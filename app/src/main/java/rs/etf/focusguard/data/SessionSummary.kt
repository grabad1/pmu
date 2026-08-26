package rs.etf.focusguard.data

import rs.etf.focusguard.data.room.PauseType
import rs.etf.focusguard.data.room.InterruptionCount
import rs.etf.focusguard.data.room.SensorKind
import rs.etf.focusguard.data.room.SensorSample
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.data.room.SessionWithPauses
import rs.etf.focusguard.sensors.EnvironmentThresholds

/**
 * Everything known about a finished session, reduced to the facts a rating depends on.
 *
 * Built once and used both for local scoring and as the basis of the AI prompt, so the two
 * can never disagree about what actually happened.
 */
data class SessionSummary(
    val name: String,
    val goalMinutes: Int,
    val focusedSeconds: Int,
    val plannedPauseCount: Int,
    val plannedPausesTaken: Int,
    val unplannedPauseCount: Int,
    val unplannedPauseSeconds: Int,
    /** Seconds spent in another app while the session was supposed to be focusing. */
    val awaySeconds: Int,
    /** 0..1 fractions of stored samples that breached each threshold. */
    val darkFraction: Double,
    val loudFraction: Double,
    val movementFraction: Double,
    /** Calls and notifications from other apps, busiest app first. Never scored. */
    val interruptions: List<InterruptionCount> = emptyList(),
) {
    /** For display and prompts; the score is computed from seconds. */
    val focusedMinutes: Int get() = focusedSeconds / 60

    /**
     * Human wording that never rounds a real session down to nothing — "0 minutes" for a
     * 52-second session led the model to conclude no focus had happened at all.
     */
    val focusedDescription: String get() = describeSeconds(focusedSeconds)

    private val goalSeconds: Int get() = goalMinutes * 60

    val goalReached: Boolean get() = focusedSeconds >= goalSeconds

    /**
     * Measured in seconds rather than whole minutes: 44 seconds of a one-minute goal is most
     * of the way there, but integer minutes would score it as nothing at all.
     */
    val goalCompletion: Double
        get() = if (goalSeconds <= 0) 1.0 else focusedSeconds.toDouble() / goalSeconds

    val unplannedPauseMinutes: Int get() = unplannedPauseSeconds / 60

    /**
     * How much of the focus time was actually spent in another app, 0..1.
     *
     * Measured against focus time rather than desk time, because that is what it displaced:
     * the timer kept running while the user was on Instagram, so the session claims focus
     * that did not happen.
     */
    val awayShare: Double
        get() = if (focusedSeconds <= 0) 0.0 else awaySeconds.toDouble() / focusedSeconds

    val awayDescription: String get() = describeSeconds(awaySeconds)

    /** How much of the time at the desk was lost to unplanned breaks, 0..1. */
    val unplannedShare: Double
        get() {
            val total = focusedSeconds + unplannedPauseSeconds
            return if (total <= 0) 0.0 else unplannedPauseSeconds.toDouble() / total
        }

    /**
     * Roughly one break per 20 minutes of goal is sensible. Beyond that the session is being
     * chopped up, however deliberately.
     */
    val reasonablePauseCount: Int get() = maxOf(1, goalMinutes / 20)

    val excessPlannedPauses: Int
        get() = (plannedPauseCount - reasonablePauseCount).coerceAtLeast(0)

    val interruptionCount: Int get() = interruptions.sumOf { it.total }

    val callCount: Int get() = interruptions.sumOf { it.calls }

    /** The app responsible for the most interruptions, when one clearly stands out. */
    val worstInterrupter: InterruptionCount?
        get() = interruptions.maxByOrNull { it.total }?.takeIf { it.total >= 2 }

    /**
     * A suggestion, never a penalty.
     *
     * Interruptions are deliberately kept out of scoring: an incoming message is not a choice
     * the user made, and the rubric already forgives a short interruption. What is useful is
     * naming the culprit, because muting one app is a change someone can actually make.
     *
     * Silent below three, since being interrupted once or twice in a session is simply life.
     */
    val interruptionAdvice: String?
        get() {
            if (interruptionCount < 3) return null

            val worst = worstInterrupter
            return if (worst != null && worst.total * 2 >= interruptionCount) {
                "${worst.appLabel} interrupted you ${worst.total} times — consider muting it " +
                    "during your next session."
            } else {
                "You were interrupted $interruptionCount times by ${interruptions.size} " +
                    "different apps. Do Not Disturb would keep the next session clear."
            }
        }

    companion object {

        /**
         * "45 seconds", "1 minute", "3 minutes 12 seconds" — always in words the model and the
         * user can read, never rounded down to nothing, and never "1 minutes".
         */
        private fun describeSeconds(totalSeconds: Int): String {
            val safe = totalSeconds.coerceAtLeast(0)
            val minutes = safe / 60
            val seconds = safe % 60

            fun plural(value: Int, unit: String) =
                "$value $unit" + if (value == 1) "" else "s"

            return when {
                minutes == 0 -> plural(seconds, "second")
                seconds == 0 -> plural(minutes, "minute")
                else -> "${plural(minutes, "minute")} ${plural(seconds, "second")}"
            }
        }

        fun from(
            item: SessionWithPauses,
            samples: List<SensorSample>,
            interruptions: List<InterruptionCount> = emptyList(),
        ): SessionSummary {
            val session: Session = item.session
            val unplanned = item.unplannedPauses

            return SessionSummary(
                name = session.name,
                goalMinutes = session.goalMinutes,
                focusedSeconds = session.focusedSeconds,
                plannedPauseCount = session.plannedPauseCount,
                plannedPausesTaken = item.pauses.count { it.type == PauseType.PLANNED },
                unplannedPauseCount = unplanned.size,
                unplannedPauseSeconds = unplanned.sumOf { it.durationSeconds },
                awaySeconds = session.awaySeconds,
                interruptions = interruptions,
                darkFraction = samples.fractionOf(SensorKind.LIGHT) {
                    it < EnvironmentThresholds.DARK_LUX
                },
                loudFraction = samples.fractionOf(SensorKind.NOISE) {
                    it > EnvironmentThresholds.LOUD_DB
                },
                movementFraction = samples.fractionOf(SensorKind.MOTION) {
                    it > EnvironmentThresholds.MOVEMENT_MS2
                },
            )
        }

        private fun List<SensorSample>.fractionOf(
            kind: SensorKind,
            predicate: (Float) -> Boolean,
        ): Double {
            val ofKind = filter { it.kind == kind }
            if (ofKind.isEmpty()) return 0.0
            return ofKind.count { predicate(it.value) }.toDouble() / ofKind.size
        }
    }
}
