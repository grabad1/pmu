package rs.etf.focusguard.data

import rs.etf.focusguard.data.room.PauseType
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
    /** 0..1 fractions of stored samples that breached each threshold. */
    val darkFraction: Double,
    val loudFraction: Double,
    val movementFraction: Double,
) {
    /** For display and prompts; the score is computed from seconds. */
    val focusedMinutes: Int get() = focusedSeconds / 60

    /**
     * Human wording that never rounds a real session down to nothing — "0 minutes" for a
     * 52-second session led the model to conclude no focus had happened at all.
     */
    val focusedDescription: String
        get() = when {
            focusedSeconds < 60 -> "$focusedSeconds seconds"
            focusedSeconds % 60 == 0 -> "${focusedSeconds / 60} minutes"
            else -> "${focusedSeconds / 60} minutes ${focusedSeconds % 60} seconds"
        }

    private val goalSeconds: Int get() = goalMinutes * 60

    val goalReached: Boolean get() = focusedSeconds >= goalSeconds

    /**
     * Measured in seconds rather than whole minutes: 44 seconds of a one-minute goal is most
     * of the way there, but integer minutes would score it as nothing at all.
     */
    val goalCompletion: Double
        get() = if (goalSeconds <= 0) 1.0 else focusedSeconds.toDouble() / goalSeconds

    val unplannedPauseMinutes: Int get() = unplannedPauseSeconds / 60

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

    companion object {

        fun from(item: SessionWithPauses, samples: List<SensorSample>): SessionSummary {
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
