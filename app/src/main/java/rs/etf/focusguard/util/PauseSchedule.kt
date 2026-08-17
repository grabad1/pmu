package rs.etf.focusguard.util

/**
 * Focus-time offsets, in seconds, at which planned pauses should trigger.
 *
 * Pauses are spaced at `goal / (count + 1)`, so a 60-minute goal with 6 pauses breaks every
 * ~8.5 minutes rather than every 10. Dividing by `count` instead would place the final pause
 * exactly at the goal, where it is useless.
 *
 * Offsets are measured in *focus* seconds, not wall-clock seconds, so pause time does not
 * push later pauses further out.
 */
fun plannedPauseOffsetsSeconds(goalMinutes: Int, pauseCount: Int): List<Int> {
    if (pauseCount <= 0 || goalMinutes <= 0) return emptyList()

    val goalSeconds = goalMinutes * 60
    val interval = goalSeconds.toDouble() / (pauseCount + 1)

    return (1..pauseCount)
        .map { index -> Math.floor(interval * index).toInt() }
        .filter { it in 1 until goalSeconds }
        .distinct()
}
