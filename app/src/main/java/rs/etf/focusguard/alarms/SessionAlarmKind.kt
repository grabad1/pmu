package rs.etf.focusguard.alarms

import java.time.Duration

/**
 * The reminders fired for a scheduled session.
 *
 * Each is a separate alarm rather than one alarm that reschedules itself, so a missed or
 * cancelled reminder cannot take the others down with it.
 */
enum class SessionAlarmKind(val leadTime: Duration) {
    ONE_HOUR_BEFORE(Duration.ofHours(1)),
    FIVE_MINUTES_BEFORE(Duration.ofMinutes(5)),
    AT_START(Duration.ZERO),
}
