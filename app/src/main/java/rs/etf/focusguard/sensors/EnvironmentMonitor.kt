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
        // Patterns are measured over their own window, so they need no further sustain: by
        // the time the rule is true it has already been true for the length of the window.
        WarningKind.FLICKERING_LIGHT to ConditionTracker(
            sustainSeconds = 0L,
            cooldownSeconds = EnvironmentThresholds.COOLDOWN_SECONDS,
        ),
        WarningKind.RESTLESS_NOISE to ConditionTracker(
            sustainSeconds = 0L,
            cooldownSeconds = EnvironmentThresholds.COOLDOWN_SECONDS,
        ),
        WarningKind.FIDGETING to ConditionTracker(
            sustainSeconds = 0L,
            cooldownSeconds = EnvironmentThresholds.FIDGET_COOLDOWN_SECONDS,
        ),
    )

    /** Recent readings, for the rules that judge behaviour rather than level. */
    private val lightWindow = ReadingWindow(EnvironmentThresholds.FLICKER_WINDOW_SECONDS)
    private val noiseWindow = ReadingWindow(EnvironmentThresholds.RESTLESS_WINDOW_SECONDS)
    private val movementEvents = ReadingWindow(EnvironmentThresholds.FIDGET_WINDOW_SECONDS)

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
        lightWindow.clear()
        noiseWindow.clear()
        movementEvents.clear()
        startEvaluating()
    }

    fun stop() {
        evaluationJob?.cancel()
        evaluationJob = null
        latest.clear()
        peakSinceEvaluation.clear()
        peakSinceSample.clear()
        lightWindow.clear()
        noiseWindow.clear()
        movementEvents.clear()
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

        // Patterns need every reading, not one a second: a lamp flickering twice between
        // ticks is invisible to anything that only looks at the latest value.
        val nowSeconds = System.nanoTime() / 1_000_000_000L
        when (kind) {
            SensorKind.LIGHT -> lightWindow.add(nowSeconds, value)
            SensorKind.NOISE -> noiseWindow.add(nowSeconds, value)
            else -> Unit
        }
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
                val focusing = evaluateConditions()
                if (focusing && ticks % (EnvironmentThresholds.SAMPLE_EVERY_SECONDS) == 0L) {
                    persistLatest()
                }
            }
        }
    }

    /**
     * @return true when the session is actually focusing, so this tick's readings count.
     */
    private fun evaluateConditions(): Boolean {
        val nowSeconds = System.nanoTime() / 1_000_000_000L

        // Pauses are legitimate phone time, so nothing is judged while paused — and readings
        // taken during one are discarded rather than merely skipped. A peak kept across a
        // pause is judged the instant focus resumes: that is how a movement warning once
        // fired 174 ms after a three-minute break, from a phone that had been put down
        // before the break even started.
        val state = sessionEngine.state.value
        if (state == null || state.isPaused) {
            peakSinceEvaluation.clear()
            peakSinceSample.clear()
            // Windows are emptied too: a conversation during a break must not still be
            // "recent" when focus resumes, for the same reason peaks are discarded.
            lightWindow.clear()
            noiseWindow.clear()
            // Pick-ups are deliberately *not* cleared. Handling the phone during a break is
            // fine and never warns, but someone who picks it up four times either side of a
            // two-minute break is still fidgeting.
            movementEvents.prune(nowSeconds)
            trackers.values.forEach { it.update(violating = false, nowSeconds = nowSeconds) }
            return false
        }

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
            if (moved || turned) movementEvents.add(nowSeconds, 1f)
            raiseMovement(moved || turned, nowSeconds, reported)
        }

        evaluatePatterns(nowSeconds)
        return true
    }

    /**
     * Chooses between the two ways of saying the phone was handled.
     *
     * One pick-up and a habit of picking it up are the same event seen at different scales,
     * so they must not both fire: whichever applies is raised, and the other is marked as
     * warned so it cannot follow a second later saying much the same thing.
     */
    private fun raiseMovement(violating: Boolean, nowSeconds: Long, value: Float) {
        movementEvents.prune(nowSeconds)
        val fidgeting = violating && PatternDetectors.isFidgeting(movementEvents)

        if (fidgeting && trackers.getValue(WarningKind.FIDGETING).update(true, nowSeconds)) {
            trackers.getValue(WarningKind.MOVEMENT).markWarned(nowSeconds)
            Log.d(LOG_TAG, "EnvironmentMonitor warning=FIDGETING events=${movementEvents.size}")
            _warnings.tryEmit(WarningKind.FIDGETING)
            return
        }

        // Not fidgeting, or fidgeting is still in cooldown: fall back to the plain warning.
        if (raiseIf(WarningKind.MOVEMENT, violating, nowSeconds, value) && fidgeting) {
            trackers.getValue(WarningKind.FIDGETING).markWarned(nowSeconds)
        }
    }

    /**
     * Rules about how a condition is behaving rather than what it currently reads. These are
     * judged every tick from their own windows, which is why they need no sustain of their
     * own — the window *is* the sustain.
     */
    private fun evaluatePatterns(nowSeconds: Long) {
        lightWindow.prune(nowSeconds)
        noiseWindow.prune(nowSeconds)
        movementEvents.prune(nowSeconds)

        val swings = lightWindow.swings(EnvironmentThresholds.FLICKER_RELATIVE_CHANGE)
        val spread = noiseWindow.spread()

        // Kept for the history and the graphs whether or not they warranted a warning: a
        // session spent in a slightly restless room is worth being able to see afterwards.
        latest[SensorKind.LIGHT_VARIABILITY] = swings.toFloat()
        latest[SensorKind.NOISE_VARIABILITY] = spread
        latest[SensorKind.MOTION_EVENTS] = movementEvents.size.toFloat()

        raiseIf(
            WarningKind.FLICKERING_LIGHT,
            PatternDetectors.isFlickering(lightWindow),
            nowSeconds,
            swings.toFloat(),
        )
        raiseIf(
            WarningKind.RESTLESS_NOISE,
            PatternDetectors.isRestless(noiseWindow),
            nowSeconds,
            spread,
        )
    }

    private fun raiseIf(
        kind: WarningKind,
        violating: Boolean,
        nowSeconds: Long,
        value: Float,
    ): Boolean {
        if (!trackers.getValue(kind).update(violating, nowSeconds)) return false

        Log.d(LOG_TAG, "EnvironmentMonitor warning=$kind value=${value.roundToInt()}")
        _warnings.tryEmit(kind)
        return true
    }

    /**
     * Writes one sample per sensor. Only ever called while focusing, so stored history covers
     * focus time alone — the rating divides by the number of samples, and counting a break
     * during which the phone was legitimately picked up would score the user down for it.
     */
    private suspend fun persistLatest() {
        val sessionId = sessionEngine.state.value?.sessionId ?: return
        if (latest.isEmpty()) return

        val now = Instant.now()
        val samples = latest.map { (kind, value) ->
            // Spiky signals are stored at their peak so the history shows that the phone was
            // moved, rather than whatever happened to be true at the instant of the write.
            // The derived measures are already summaries of a window, so they are stored as
            // computed.
            val stored = when (kind) {
                SensorKind.LIGHT,
                SensorKind.LIGHT_VARIABILITY,
                SensorKind.NOISE_VARIABILITY,
                SensorKind.MOTION_EVENTS -> value

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
