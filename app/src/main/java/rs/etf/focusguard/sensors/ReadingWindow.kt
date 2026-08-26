package rs.etf.focusguard.sensors

/**
 * A short rolling window of recent readings, used to judge *patterns* rather than levels.
 *
 * Everything before this phase asked a single question of a single reading — "is it below
 * fifteen lux", "is it above fifty decibels". That cannot see a lamp that keeps flickering,
 * a room where someone is talking, or a phone picked up ten times in five minutes, because
 * each individual reading looks unremarkable.
 *
 * Deliberately free of Android and of clocks: time is passed in, so the rules can be
 * unit-tested exhaustively instead of by sitting in front of a device waiting for a lamp to
 * flicker. Readings arrive on sensor callback threads, so access is synchronised.
 */
class ReadingWindow(private val windowSeconds: Long) {

    private val times = ArrayDeque<Long>()
    private val values = ArrayDeque<Float>()

    fun add(nowSeconds: Long, value: Float) = synchronized(this) {
        times.addLast(nowSeconds)
        values.addLast(value)
        pruneLocked(nowSeconds)
    }

    /** Drops readings that have fallen out of the window without adding a new one. */
    fun prune(nowSeconds: Long) = synchronized(this) { pruneLocked(nowSeconds) }

    fun clear() = synchronized(this) {
        times.clear()
        values.clear()
    }

    val size: Int get() = synchronized(this) { values.size }

    /** Highest minus lowest reading in the window. */
    fun spread(): Float = synchronized(this) {
        if (values.isEmpty()) return 0f
        (values.max() - values.min())
    }

    fun max(): Float = synchronized(this) { values.maxOrNull() ?: 0f }

    /**
     * How many times consecutive readings cross the midpoint between the quietest and the
     * loudest in the window.
     *
     * This is what separates speech from a change of scene. Someone switching a fan on
     * produces a large spread but crosses once and then stays put; a conversation crosses
     * repeatedly as words start and stop.
     */
    fun midpointCrossings(): Int = synchronized(this) {
        if (values.size < 2) return 0

        val min = values.min()
        val max = values.max()
        if (max == min) return 0
        val midpoint = (min + max) / 2f

        var count = 0
        var previous: Float? = null
        for (value in values) {
            val last = previous
            if (last != null && (last > midpoint) != (value > midpoint)) count++
            previous = value
        }
        count
    }

    /**
     * How many times consecutive readings changed by more than [relativeChange] of the
     * larger of the two.
     *
     * Relative rather than absolute because light is not linear: 400 lux dropping to 300 is
     * nothing, while 12 dropping to 3 is the room going dark. The `coerceAtLeast(1f)` keeps
     * near-zero readings from dividing into a meaningless enormous ratio.
     */
    fun swings(relativeChange: Float): Int = synchronized(this) {
        if (values.size < 2) return 0

        var count = 0
        var previous: Float? = null
        for (value in values) {
            val last = previous
            if (last != null) {
                val reference = maxOf(last, value).coerceAtLeast(1f)
                if (kotlin.math.abs(value - last) / reference > relativeChange) count++
            }
            previous = value
        }
        count
    }

    private fun pruneLocked(nowSeconds: Long) {
        while (times.isNotEmpty() && nowSeconds - times.first() > windowSeconds) {
            times.removeFirst()
            values.removeFirst()
        }
    }
}

/**
 * The pattern rules themselves, kept beside the window they read so that the thresholds and
 * the reasoning stay in one place.
 */
object PatternDetectors {

    /**
     * A light that will not sit still — a failing lamp, a flickering tube, or a screen-lit
     * room with someone moving past the sensor.
     *
     * Judged on the number of large swings rather than on brightness, so a room that is
     * simply dim does not qualify and a room that is bright but strobing does. Turning a
     * lamp off is a single swing and correctly ignored.
     */
    fun isFlickering(light: ReadingWindow): Boolean =
        light.swings(EnvironmentThresholds.FLICKER_RELATIVE_CHANGE) >=
            EnvironmentThresholds.FLICKER_SWINGS

    /**
     * Noise that keeps changing, which is what speech does — as opposed to a fan or traffic,
     * which sit at a steady level and are far easier to work through.
     *
     * Three conditions, each earning its place:
     *
     * - a **floor**, because a silent room technically has a small spread, and without it a
     *   quiet library would eventually qualify on rounding noise alone;
     * - a **spread**, because that is what "keeps changing" means;
     * - and **crossings**, because spread alone cannot tell speech from a change of scene.
     *   Testing caught exactly that: switching from silence to a steady 40 dB produced a
     *   spread of 26 and was reported as voices, when in fact the room had simply become
     *   uniformly louder. A step change crosses the midpoint once; a conversation crosses it
     *   again and again.
     */
    fun isRestless(noise: ReadingWindow): Boolean =
        noise.max() > EnvironmentThresholds.RESTLESS_FLOOR_DB &&
            noise.spread() > EnvironmentThresholds.RESTLESS_SPREAD_DB &&
            noise.midpointCrossings() >= EnvironmentThresholds.RESTLESS_CROSSINGS

    /**
     * Repeated handling of the phone within a few minutes.
     *
     * One pick-up is a moment of weakness; four in five minutes is a habit, and worth saying
     * differently. [movementEvents] holds one entry per detected pick-up rather than raw
     * readings, so the count is of events and not of samples.
     */
    fun isFidgeting(movementEvents: ReadingWindow): Boolean =
        movementEvents.size >= EnvironmentThresholds.FIDGET_EVENTS
}
