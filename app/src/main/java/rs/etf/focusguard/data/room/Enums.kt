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

    /**
     * Derived measures of how a condition is *behaving*, rather than what it reads.
     *
     * Stored as extra kinds in the same table rather than as new columns or a new table, for
     * three reasons: `kind` is persisted as its own name in a TEXT column, so adding values
     * needs no migration at all; every existing query, fraction and graph path works on them
     * unchanged; and they are conceptually the same thing as the rest — a number sampled from
     * the environment at a moment in time.
     *
     * They cannot be recomputed later from the stored raw samples, which is precisely why
     * they are stored: a reading every ten seconds cannot show that a lamp flickered four
     * times in between.
     */

    /** Number of large swings in ambient light over the recent window. */
    LIGHT_VARIABILITY,

    /** Loudest minus quietest reading over the recent window, in the same dB scale. */
    NOISE_VARIABILITY,

    /** Pick-ups detected in the last few minutes. */
    MOTION_EVENTS,
}

/**
 * What interrupted the user from outside the app.
 *
 * These are recorded but deliberately never scored — an incoming call is not a choice the
 * user made, and the rubric already forgives short interruptions. They exist to produce
 * advice ("this app interrupted you six times"), not a penalty.
 */
enum class InterruptionKind {
    /** An incoming phone call, ringing or answered. */
    CALL,

    /** A notification posted by another app. */
    NOTIFICATION,
}
