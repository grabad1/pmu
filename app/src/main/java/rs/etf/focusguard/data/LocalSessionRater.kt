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
 * Used when no API key is configured, when the request fails, and as a sanity check on the
 * AI: a session always ends up rated, so a flat network cannot cost the user their history.
 *
 * The weighting reflects the app's opinion of what focus means — reaching the goal matters
 * most, unplanned breaks are the clearest sign of lost concentration, and the environment
 * shapes both.
 */
object LocalSessionRater {

    fun rate(summary: SessionSummary): SessionRating {
        val score = score(summary)
        return SessionRating(
            score = score,
            comment = comment(summary, score),
            analysis = analysis(summary),
        )
    }

    fun score(summary: SessionSummary): Int {
        // Reaching the goal is worth 60 of the 100 points, and overtime is not extra credit:
        // the goal is the goal, and beating it is recognised in the wording instead.
        val goalPoints = (summary.goalCompletion.coerceAtMost(1.0) * 60).roundToInt()

        // Each unplanned pause costs, with the first hurting most; a run of them is the
        // signature of a session that never settled.
        val unplannedPenalty = when (summary.unplannedPauseCount) {
            0 -> 0
            1 -> 8
            2 -> 15
            else -> 15 + (summary.unplannedPauseCount - 2) * 5
        }.coerceAtMost(25)

        // Taking the breaks that were planned is discipline, not weakness.
        val disciplinePoints = if (summary.plannedPauseCount == 0) {
            10
        } else {
            val ratio = summary.plannedPausesTaken.toDouble() / summary.plannedPauseCount
            (ratio.coerceIn(0.0, 1.0) * 10).roundToInt()
        }

        val environmentPoints = (
            (1 - summary.darkFraction) * 10 +
                (1 - summary.loudFraction) * 10 +
                (1 - summary.movementFraction) * 10
            ).roundToInt()

        return (goalPoints + disciplinePoints + environmentPoints - unplannedPenalty)
            .coerceIn(0, 100)
    }

    private fun comment(summary: SessionSummary, score: Int): String = when {
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
            "You focused for ${summary.focusedDescription} of ${summary.goalMinutes} planned " +
                "minutes"
        )
        append(
            if (summary.goalReached) ", meeting your goal. " else ", short of your goal. "
        )

        if (summary.plannedPauseCount > 0) {
            append(
                "You took ${summary.plannedPausesTaken} of ${summary.plannedPauseCount} " +
                    "planned pauses. "
            )
        }

        if (summary.unplannedPauseCount > 0) {
            append(
                "There ${if (summary.unplannedPauseCount == 1) "was" else "were"} " +
                    "${summary.unplannedPauseCount} unplanned " +
                    "${if (summary.unplannedPauseCount == 1) "pause" else "pauses"} " +
                    "totalling ${summary.unplannedPauseMinutes} minutes, which is the " +
                    "clearest sign of concentration breaking. "
            )
        } else {
            append("No unplanned pauses were recorded. ")
        }

        val environment = buildList {
            if (summary.darkFraction > 0.3) add("the room was dark for much of the session")
            if (summary.loudFraction > 0.3) add("noise was a recurring problem")
            if (summary.movementFraction > 0.2) add("the phone was handled repeatedly")
        }
        if (environment.isNotEmpty()) {
            append("Environment: ${environment.joinToString(", ")}.")
        } else {
            append("Your working conditions stayed comfortable throughout.")
        }
    }
}
