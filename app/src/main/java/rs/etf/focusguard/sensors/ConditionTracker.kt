package rs.etf.focusguard.sensors

/**
 * Decides whether a stream of readings for one condition warrants a warning.
 *
 * Deliberately free of Android and of clocks: time is passed in, so the debounce and cooldown
 * rules can be unit-tested exhaustively rather than by waiting in front of a device.
 *
 * @param sustainSeconds how long the condition must hold before it counts
 * @param cooldownSeconds minimum gap between two warnings of this kind
 */
class ConditionTracker(
    private val sustainSeconds: Long,
    private val cooldownSeconds: Long,
) {
    private var violatingSince: Long? = null
    private var lastWarnedAt: Long? = null

    /**
     * @param violating whether the latest reading breaches the threshold
     * @param nowSeconds monotonic time in seconds
     * @return true when a warning should be raised for this reading
     */
    fun update(violating: Boolean, nowSeconds: Long): Boolean {
        if (!violating) {
            violatingSince = null
            return false
        }

        val since = violatingSince ?: nowSeconds.also { violatingSince = it }
        if (nowSeconds - since < sustainSeconds) return false

        val last = lastWarnedAt
        if (last != null && nowSeconds - last < cooldownSeconds) return false

        lastWarnedAt = nowSeconds
        // Restart the sustain window so a continuing problem re-warns only after the
        // cooldown, rather than on every reading once the window has elapsed.
        violatingSince = nowSeconds
        return true
    }

    /** Clears history, e.g. when a new session begins. */
    fun reset() {
        violatingSince = null
        lastWarnedAt = null
    }

    /**
     * Starts this condition's cooldown without raising anything.
     *
     * Used when a different warning has just said much the same thing: one pick-up and a
     * habit of picking the phone up are the same event at different scales, and hearing
     * about both a second apart is worse than hearing about either.
     */
    fun markWarned(nowSeconds: Long) {
        lastWarnedAt = nowSeconds
        violatingSince = nowSeconds
    }
}
