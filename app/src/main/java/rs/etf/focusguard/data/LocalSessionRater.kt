package rs.etf.focusguard.data

import kotlin.math.roundToInt

/** A rating for a finished session, from either the AI or the local fallback. */
data class SessionRating(
    val score: Int,
    val comment: String,
    val analysis: String,
)

/**
 * Scores a session without any network call.
 *
 * Used when no API key is configured and when the request fails, but also serves as the
 * app's definition of a good session: the AI prompt describes these same rules, so the two
 * cannot drift apart.
 *
 * The model, in short:
 *  - reaching the goal is the largest single factor;
 *  - a very short session cannot score top marks however clean it was, because a few focused
 *    minutes is not the habit the app exists to build;
 *  - planned breaks are fine, but chopping a session into many of them is not;
 *  - unplanned breaks cost in proportion to the time they actually consumed, so a brief
 *    interruption is forgiven;
 *  - handling the phone matters far more than noise, and noise more than dim light;
 *  - a problem present only briefly is ignored entirely.
 */
object LocalSessionRater {

    // Points available.
    private const val GOAL_POINTS = 60.0
    private const val DISCIPLINE_POINTS = 15.0
    private const val MOVEMENT_POINTS = 12.0
    private const val NOISE_POINTS = 8.0
    private const val LIGHT_POINTS = 5.0

    // Penalties.
    private const val MAX_UNPLANNED_PENALTY = 25.0
    private const val EXCESS_PAUSE_PENALTY = 5.0
    private const val MAX_EXCESS_PAUSE_PENALTY = 15.0
    private const val MAX_AWAY_PENALTY = 20.0

    /** A problem present for less than this share of the session is not held against anyone. */
    private const val ENVIRONMENT_GRACE = 0.2

    /** Unplanned breaks shorter than this are treated as life, not lost focus. */
    private const val UNPLANNED_GRACE_SECONDS = 120

    /**
     * Time in another app below this is not worth mentioning — glancing at a message that
     * arrived is not the same as scrolling for ten minutes.
     */
    private const val AWAY_GRACE_SECONDS = 30

    fun rate(summary: SessionSummary): SessionRating {
        val score = score(summary)
        return SessionRating(
            score = score,
            comment = comment(summary, score),
            analysis = analysis(summary),
        )
    }

    fun score(summary: SessionSummary): Int {
        val goal = GOAL_POINTS * summary.goalCompletion.coerceAtMost(1.0)

        // Taking the breaks that were planned is discipline. Planning none is not a failing.
        val discipline = if (summary.plannedPauseCount == 0) {
            DISCIPLINE_POINTS
        } else {
            val ratio = summary.plannedPausesTaken.toDouble() / summary.plannedPauseCount
            DISCIPLINE_POINTS * ratio.coerceIn(0.0, 1.0)
        }

        val environment =
            MOVEMENT_POINTS * (1 - graced(summary.movementFraction)) +
                NOISE_POINTS * (1 - graced(summary.loudFraction)) +
                LIGHT_POINTS * (1 - graced(summary.darkFraction))

        val raw = goal + discipline + environment -
            unplannedPenalty(summary) -
            excessPausePenalty(summary) -
            awayPenalty(summary)

        // However clean it was, a very short session is not evidence of sustained focus.
        return raw.coerceIn(0.0, ceilingForLength(summary).toDouble()).roundToInt()
    }

    /**
     * Time in another app while the timer ran is the worst kind of lost focus, because unlike
     * a pause the session went on claiming it. Scales with the share of focus time it
     * displaced, after a short grace period for a glance at a message.
     */
    private fun awayPenalty(summary: SessionSummary): Double {
        if (summary.awaySeconds <= AWAY_GRACE_SECONDS) return 0.0
        return (summary.awayShare * 60).coerceAtMost(MAX_AWAY_PENALTY)
    }

    /** Nothing below the grace threshold counts; above it the excess scales to a full penalty. */
    private fun graced(fraction: Double): Double =
        ((fraction - ENVIRONMENT_GRACE) / (1 - ENVIRONMENT_GRACE)).coerceIn(0.0, 1.0)

    /**
     * Proportional to the share of desk time lost, after a short grace period: two minutes on
     * a phone call in the middle of an hour is not a collapse of concentration. A count term
     * catches sessions broken into many small pieces, which is worse than one longer break.
     */
    private fun unplannedPenalty(summary: SessionSummary): Double {
        if (summary.unplannedPauseSeconds <= UNPLANNED_GRACE_SECONDS) return 0.0

        val proportional = summary.unplannedShare * 50
        val fragmentation = (summary.unplannedPauseCount - 2).coerceAtLeast(0) * 3.0
        return (proportional + fragmentation).coerceAtMost(MAX_UNPLANNED_PENALTY)
    }

    /** Planned breaks are fine until there are more of them than the goal length warrants. */
    private fun excessPausePenalty(summary: SessionSummary): Double =
        (summary.excessPlannedPauses * EXCESS_PAUSE_PENALTY)
            .coerceAtMost(MAX_EXCESS_PAUSE_PENALTY)

    /**
     * A ceiling rather than a deduction, so a four-minute session cannot reach 100 however
     * spotless it was, while a genuinely long session is never capped.
     */
    private fun ceilingForLength(summary: SessionSummary): Int = when {
        summary.focusedMinutes < 5 -> 60
        summary.focusedMinutes < 10 -> 75
        summary.focusedMinutes < 20 -> 90
        else -> 100
    }

    private fun comment(summary: SessionSummary, score: Int): String = when {
        summary.focusedMinutes < 5 -> "Far too short to build any real focus."

        summary.awaySeconds > AWAY_GRACE_SECONDS && summary.awayShare > 0.25 ->
            "You spent much of this session in another app."

        summary.excessPlannedPauses > 0 && score < 75 ->
            "Broken into too many breaks to build momentum."

        summary.goalCompletion > 1.0 && summary.unplannedPauseCount == 0 ->
            "Exceptional — you went past your goal."

        score >= 85 -> "Strong, steady session."
        score >= 70 -> "Good focus with minor distractions."
        score >= 50 -> "Reasonable, but the interruptions added up."
        score >= 30 -> "Difficult session — frequent distractions."
        else -> "This one got away from you."
    }

    private fun analysis(summary: SessionSummary): String = buildString {
        append(
            "You focused for ${summary.focusedDescription} of ${summary.goalMinutes} " +
                "planned minutes"
        )
        append(if (summary.goalReached) ", meeting your goal. " else ", short of your goal. ")

        if (summary.focusedMinutes < 10) {
            append(
                "That is a very short block — aim for at least 20 to 25 minutes before " +
                    "stopping, since sustained focus is the whole point. "
            )
        }

        if (summary.excessPlannedPauses > 0) {
            append(
                "You planned ${summary.plannedPauseCount} breaks for a " +
                    "${summary.goalMinutes}-minute goal, which is more than it needs; about " +
                    "${summary.reasonablePauseCount} would keep the momentum. "
            )
        }

        when {
            summary.unplannedPauseSeconds == 0 -> append("No unplanned pauses were recorded. ")
            summary.unplannedPauseSeconds <= UNPLANNED_GRACE_SECONDS ->
                append("One brief unplanned interruption is nothing to worry about. ")

            else -> append(
                "Unplanned pauses took ${summary.unplannedPauseMinutes} minutes, about " +
                    "${(summary.unplannedShare * 100).roundToInt()}% of your time at the desk, " +
                    "which is where the focus went. "
            )
        }

        val problems = buildList {
            if (graced(summary.movementFraction) > 0) add("the phone was handled repeatedly")
            if (graced(summary.loudFraction) > 0) add("the room stayed noisy")
            if (graced(summary.darkFraction) > 0) add("the light was poor for a long stretch")
        }
        append(
            if (problems.isEmpty()) "Your working conditions held up well."
            else "Worth fixing: ${problems.joinToString(", ")}."
        )

        if (summary.awaySeconds > AWAY_GRACE_SECONDS) {
            append(
                " You also spent ${summary.awayDescription} in another app while the timer " +
                    "was running — that is ${(summary.awayShare * 100).roundToInt()}% of the " +
                    "focus this session claims, and the clearest thing to change next time."
            )
        }
    }
}
