package rs.etf.focusguard.sensors

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rs.etf.focusguard.LOG_TAG
import rs.etf.focusguard.data.SessionEngine
import rs.etf.focusguard.data.SessionRepository
import rs.etf.focusguard.data.room.SensorKind
import rs.etf.focusguard.data.room.SensorSample
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Turns raw sensor readings into warnings and stored history.
 *
 * Readings arrive on the sensor callback thread and can be frequent, so [onReading] stays
 * cheap: it updates a tracker and records the latest value. Persistence happens on a timer
 * rather than per reading, and monitoring only runs while the user is actually focusing —
 * moving the phone during a pause is expected, not a failure.
 */
@Singleton
class EnvironmentMonitor @Inject constructor(
    private val repository: SessionRepository,
    private val sessionEngine: SessionEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _warnings = MutableSharedFlow<WarningKind>(extraBufferCapacity = 8)
    val warnings = _warnings.asSharedFlow()

    private val trackers = mapOf(
        WarningKind.BAD_LIGHT to ConditionTracker(
            sustainSeconds = EnvironmentThresholds.DARK_SUSTAIN_SECONDS,
            cooldownSeconds = EnvironmentThresholds.COOLDOWN_SECONDS,
        ),
        WarningKind.LOUD_ROOM to ConditionTracker(
            sustainSeconds = EnvironmentThresholds.LOUD_SUSTAIN_SECONDS,
            cooldownSeconds = EnvironmentThresholds.COOLDOWN_SECONDS,
        ),
        WarningKind.MOVEMENT to ConditionTracker(
            sustainSeconds = EnvironmentThresholds.MOVEMENT_SUSTAIN_SECONDS,
            cooldownSeconds = EnvironmentThresholds.COOLDOWN_SECONDS,
        ),
    )

    /** Most recent reading per sensor, used for conditions that change slowly. */
    private val latest = java.util.concurrent.ConcurrentHashMap<SensorKind, Float>()

    /**
     * Highest reading seen since the last evaluation.
     *
     * Movement and noise are transient: picking up a phone is a burst of a few hundred
     * milliseconds, which a once-a-second look at the latest value would step straight over.
     * Keeping the peak means no spike is missed regardless of tick alignment.
     */
    private val peakSinceEvaluation = java.util.concurrent.ConcurrentHashMap<SensorKind, Float>()

    /** Highest reading since the last write to Room, so stored history shows spikes too. */
    private val peakSinceSample = java.util.concurrent.ConcurrentHashMap<SensorKind, Float>()

    private var evaluationJob: Job? = null

    fun start() {
        trackers.values.forEach(ConditionTracker::reset)
        latest.clear()
        peakSinceEvaluation.clear()
        peakSinceSample.clear()
        startEvaluating()
    }

    fun stop() {
        evaluationJob?.cancel()
        evaluationJob = null
        latest.clear()
        peakSinceEvaluation.clear()
        peakSinceSample.clear()
    }

    /**
     * Called from sensor callbacks. Deliberately does nothing but record the value: judging
     * conditions here would tie warning timing to sensor rates, which differ wildly and, for
     * ambient light, stop entirely once a reading settles.
     */
    fun onReading(kind: SensorKind, value: Float) {
        // SQLite has no NaN: Android binds it as NULL, which violates the non-null column and
        // crashes the insert. Rejecting non-finite readings at the door keeps bad data from
        // ever reaching storage or the thresholds.
        if (!value.isFinite()) {
            Log.w(LOG_TAG, "Discarding non-finite $kind reading: $value")
            return
        }
        latest[kind] = value
        peakSinceEvaluation.merge(kind, value, ::maxOf)
        peakSinceSample.merge(kind, value, ::maxOf)
    }

    /**
     * Conditions are judged on a steady cadence rather than per reading.
     *
     * `TYPE_LIGHT` is an on-change sensor: once the room is dark it reports once and then
     * goes quiet, so a sustain window driven by readings alone would never elapse. Ticking
     * on a clock makes "dark for 20 seconds" mean what it says for every sensor.
     */
    private fun startEvaluating() {
        evaluationJob?.cancel()
        evaluationJob = scope.launch {
            var ticks = 0L
            while (isActive) {
                delay(EVALUATE_EVERY_MILLIS)
                ticks++
                evaluateConditions()
                if (ticks % (EnvironmentThresholds.SAMPLE_EVERY_SECONDS) == 0L) {
                    persistLatest()
                }
            }
        }
    }

    private fun evaluateConditions() {
        // Pauses are legitimate phone time, so nothing is judged while paused.
        val state = sessionEngine.state.value
        if (state == null || state.isPaused) return

        val nowSeconds = System.nanoTime() / 1_000_000_000L

        // "Too dark" is a floor, so the newest reading is what matters; a peak would hide it.
        latest[SensorKind.LIGHT]?.let { lux ->
            raiseIf(WarningKind.BAD_LIGHT, lux < EnvironmentThresholds.DARK_LUX, nowSeconds, lux)
        }

        // Noise and movement are ceilings, and both are spiky, so the peak is the honest value.
        peakSinceEvaluation.remove(SensorKind.NOISE)?.let { db ->
            raiseIf(WarningKind.LOUD_ROOM, db > EnvironmentThresholds.LOUD_DB, nowSeconds, db)
        }

        // Acceleration and rotation are two symptoms of one problem — the phone is in someone's
        // hand — so they share a warning and its cooldown rather than firing twice.
        val motionPeak = peakSinceEvaluation.remove(SensorKind.MOTION)
        val rotationPeak = peakSinceEvaluation.remove(SensorKind.ROTATION)

        val moved = (motionPeak ?: 0f) > EnvironmentThresholds.MOVEMENT_MS2
        val turned = (rotationPeak ?: 0f) > EnvironmentThresholds.ROTATION_RAD_S

        if (motionPeak != null || rotationPeak != null) {
            val reported = if (turned && !moved) rotationPeak ?: 0f else motionPeak ?: 0f
            raiseIf(WarningKind.MOVEMENT, moved || turned, nowSeconds, reported)
        }
    }

    private fun raiseIf(kind: WarningKind, violating: Boolean, nowSeconds: Long, value: Float) {
        if (!trackers.getValue(kind).update(violating, nowSeconds)) return

        Log.d(LOG_TAG, "EnvironmentMonitor warning=$kind value=${value.roundToInt()}")
        _warnings.tryEmit(kind)
    }

    private suspend fun persistLatest() {
        val sessionId = sessionEngine.state.value?.sessionId ?: return
        if (latest.isEmpty()) return

        val now = Instant.now()
        val samples = latest.map { (kind, value) ->
            // Spiky signals are stored at their peak so the history shows that the phone was
            // moved, rather than whatever happened to be true at the instant of the write.
            val stored = when (kind) {
                SensorKind.LIGHT -> value
                SensorKind.NOISE, SensorKind.MOTION, SensorKind.ROTATION ->
                    peakSinceSample[kind] ?: value
            }
            SensorSample(sessionId = sessionId, kind = kind, value = stored, recordedAt = now)
        }
        peakSinceSample.clear()
        repository.insertSensorSamples(samples)
    }

    private companion object {
        const val EVALUATE_EVERY_MILLIS = 1_000L
    }
}
