package rs.etf.focusguard.data.room

/** Lifecycle of a session row. */
enum class SessionStatus {
    /** Created for a future date/time; not started yet. */
    SCHEDULED,

    /** Currently being timed by the foreground service. */
    RUNNING,

    /** Finished normally and eligible for AI rating. */
    COMPLETED,

    /** Scheduled but dismissed, or abandoned before any focus time accrued. */
    CANCELLED,
}

/**
 * Planned pauses come from the session configuration; unplanned pauses are taken by the
 * user mid-session. They are stored on the same table but must stay distinguishable,
 * because unplanned pauses lower the AI score while planned ones do not.
 */
enum class PauseType {
    PLANNED,
    UNPLANNED,
}

/** Which physical sensor produced a sample. */
enum class SensorKind {
    /** Ambient light, in lux. */
    LIGHT,

    /** Microphone loudness, in dB relative to the quietest measurable level. */
    NOISE,

    /** Linear acceleration magnitude, in m/s². */
    MOTION,

    /** Angular velocity magnitude, in rad/s. Turning a phone means holding it. */
    ROTATION,
}
