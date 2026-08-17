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

    /** Most recent reading per sensor, evaluated and sampled on a timer. */
    private val latest = java.util.concurrent.ConcurrentHashMap<SensorKind, Float>()

    private var evaluationJob: Job? = null

    fun start() {
        trackers.values.forEach(ConditionTracker::reset)
        latest.clear()
        startEvaluating()
    }

    fun stop() {
        evaluationJob?.cancel()
        evaluationJob = null
        latest.clear()
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

        latest[SensorKind.LIGHT]?.let { lux ->
            raiseIf(
                WarningKind.BAD_LIGHT,
                lux < EnvironmentThresholds.DARK_LUX,
                nowSeconds,
                lux,
            )
        }
        latest[SensorKind.NOISE]?.let { db ->
            raiseIf(
                WarningKind.LOUD_ROOM,
                db > EnvironmentThresholds.LOUD_DB,
                nowSeconds,
                db,
            )
        }
        latest[SensorKind.MOTION]?.let { ms2 ->
            raiseIf(
                WarningKind.MOVEMENT,
                ms2 > EnvironmentThresholds.MOVEMENT_MS2,
                nowSeconds,
                ms2,
            )
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
            SensorSample(sessionId = sessionId, kind = kind, value = value, recordedAt = now)
        }
        repository.insertSensorSamples(samples)
    }

    private companion object {
        const val EVALUATE_EVERY_MILLIS = 1_000L
    }
}
