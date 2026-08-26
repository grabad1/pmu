package rs.etf.focusguard.data

import rs.etf.focusguard.data.room.InterruptionCount
import rs.etf.focusguard.data.room.Pause
import rs.etf.focusguard.data.room.SensorKind
import rs.etf.focusguard.data.room.SensorSample
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.sensors.EnvironmentThresholds

/**
 * Everything recorded about one finished session, for the detail view.
 *
 * Loaded on demand rather than carried in the history list: a session holds hundreds of
 * sensor samples, and reading them for every card on screen to show three of them would be
 * wasteful. It is only needed once a session is actually opened.
 */
data class SessionDetail(
    val session: Session,
    val pauses: List<Pause>,
    val samples: List<SensorSample>,
    val interruptions: List<InterruptionCount>,
) {

    fun samplesOf(kind: SensorKind): List<SensorSample> = samples.filter { it.kind == kind }

    /** Share of readings of [kind] that breached [threshold], 0..1. */
    private fun shareAbove(kind: SensorKind, threshold: Float): Double {
        val ofKind = samplesOf(kind)
        if (ofKind.isEmpty()) return 0.0
        return ofKind.count { it.value > threshold }.toDouble() / ofKind.size
    }

    private fun shareBelow(kind: SensorKind, threshold: Float): Double {
        val ofKind = samplesOf(kind)
        if (ofKind.isEmpty()) return 0.0
        return ofKind.count { it.value < threshold }.toDouble() / ofKind.size
    }

    val darkShare: Double get() = shareBelow(SensorKind.LIGHT, EnvironmentThresholds.DARK_LUX)
    val loudShare: Double get() = shareAbove(SensorKind.NOISE, EnvironmentThresholds.LOUD_DB)
    val movingShare: Double
        get() = shareAbove(SensorKind.MOTION, EnvironmentThresholds.MOVEMENT_MS2)

    /** Most pick-ups counted in any five-minute stretch of the session. */
    val peakPickUps: Int
        get() = samplesOf(SensorKind.MOTION_EVENTS).maxOfOrNull { it.value }?.toInt() ?: 0

    val interruptionCount: Int get() = interruptions.sumOf { it.total }

    /** True when there is enough history to draw anything worth looking at. */
    val hasCharts: Boolean get() = samples.size >= MINIMUM_SAMPLES

    private companion object {
        /** Two samples cannot make a line; a handful cannot make a shape worth reading. */
        const val MINIMUM_SAMPLES = 6
    }
}
