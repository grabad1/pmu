package rs.etf.focusguard.data

/**
 * How a group of finished sessions has gone on average — everything, one category, or one
 * topic within it.
 *
 * This is what makes a topic worth recording: on its own, "Math, 62" says little, but "your
 * maths sessions average 62 and the light is bad a fifth of the time" is something the user
 * can act on. It also gives the AI a baseline to compare a single session against.
 */
data class TopicSummary(
    val category: String?,
    val topic: String?,
    val sessionCount: Int,
    val averageScore: Int?,
    val averageFocusedSeconds: Int,
    val averageGoalMinutes: Int,
    val averagePlannedPauses: Double,
    val averageUnplannedPauses: Double,
    val darkFraction: Double,
    val loudFraction: Double,
    val movementFraction: Double,
    val totalAwaySeconds: Int,
) {

    val isEmpty: Boolean get() = sessionCount == 0

    /** "Math", "Studying" or "All sessions" — whichever the filter narrowed to. */
    val label: String get() = topic ?: category ?: "All sessions"

    /** Share of the goal actually reached on average, 0..1. */
    val averageGoalCompletion: Double
        get() {
            val goalSeconds = averageGoalMinutes * 60
            return if (goalSeconds <= 0) 0.0 else averageFocusedSeconds.toDouble() / goalSeconds
        }

    companion object {
        fun empty(category: String?, topic: String?) = TopicSummary(
            category = category,
            topic = topic,
            sessionCount = 0,
            averageScore = null,
            averageFocusedSeconds = 0,
            averageGoalMinutes = 0,
            averagePlannedPauses = 0.0,
            averageUnplannedPauses = 0.0,
            darkFraction = 0.0,
            loudFraction = 0.0,
            movementFraction = 0.0,
            totalAwaySeconds = 0,
        )
    }
}
