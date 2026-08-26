package rs.etf.focusguard.sensors

/**
 * Environment problems the app reacts to during a session.
 *
 * [MOVEMENT] and [FIDGETING] are serious enough to interrupt with a full-screen warning,
 * because reaching for the phone is the failure mode the app exists to prevent. The others
 * are advisory and appear as toasts.
 *
 * The three pattern warnings — [FLICKERING_LIGHT], [RESTLESS_NOISE], [FIDGETING] — describe
 * how a condition is *behaving* rather than what it currently reads, which is why each
 * carries its own wording: "the light keeps changing" calls for a different response from
 * "it is too dark", even though both come from the same sensor.
 */
enum class WarningKind {
    MOVEMENT,
    BAD_LIGHT,
    LOUD_ROOM,
    FLICKERING_LIGHT,
    RESTLESS_NOISE,
    FIDGETING,
}

/**
 * Thresholds are gathered here rather than scattered through the monitors, so they can be
 * tuned against real-world readings without touching logic.
 *
 * Sustain windows exist because momentary spikes are normal: a hand passing over the sensor,
 * a door closing, setting the phone down. A condition has to persist to count.
 */
object EnvironmentThresholds {

    /** Below this a desk is too dim for sustained work; a dim room reads 30-80 lux. */
    const val DARK_LUX = 15f

    /**
     * Long enough to ignore a hand passing over the sensor, short enough that the warning
     * arrives while it is still worth acting on.
     */
    const val DARK_SUSTAIN_SECONDS = 10L

    /**
     * Relative loudness, not calibrated SPL. [rs.etf.focusguard.sensors.MicrophoneNoiseSource]
     * reports `20·log10(rms)` of 16-bit samples, so the scale runs from 0 for silence to about
     * 90 at full scale, and sits roughly 30 below the dB SPL a sound-level meter would show.
     *
     * 70 was set by eye and was far too high: it needs a tenth of full-scale amplitude held
     * for fifteen seconds, which is shouting into the microphone. 50 is about a tenth of that
     * pressure — a conversation beside the desk. Still a guess until it is checked against a
     * real device, since the emulator's microphone reports digital silence and cannot settle
     * it; inject a level with `DEBUG_NOISE` to exercise the path meanwhile.
     */
    const val LOUD_DB = 50f
    const val LOUD_SUSTAIN_SECONDS = 15L

    /**
     * Linear acceleration excludes gravity, so a phone lying still reads near zero. Picking
     * it up comfortably exceeds this; typing beside it does not.
     */
    const val MOVEMENT_MS2 = 2.5f

    /**
     * Zero, deliberately. Handling a phone is a burst of a few hundred milliseconds, not a
     * sustained state, so requiring it to persist would mean it never fired. The cooldown,
     * not a sustain window, is what stops repeated warnings.
     */
    const val MOVEMENT_SUSTAIN_SECONDS = 0L

    /**
     * Angular velocity, in rad/s. Turning a phone to look at it is a deliberate act, and one
     * that linear acceleration can miss: a phone rotated smoothly in the hand barely
     * accelerates, but its orientation changes a great deal.
     *
     * 1 rad/s is roughly 57°/s — a clear turn, not a nudge from a passing hand.
     */
    const val ROTATION_RAD_S = 1.0f

    /** Repeating the same warning is just noise, so each kind has a cooldown. */
    const val COOLDOWN_SECONDS = 120L

    // --- patterns, not levels ---------------------------------------------------------

    /**
     * How much brighter or darker a reading must be than the one before it to count as a
     * swing, as a fraction of the larger of the two.
     *
     * 40% is well beyond the drift of a steady room but comfortably inside what a failing
     * lamp or a passing shadow produces.
     */
    const val FLICKER_RELATIVE_CHANGE = 0.4f

    /**
     * Four swings, so switching a lamp off (one swing) or drawing a blind (two) is ignored.
     * Ambient light is an on-change sensor, so a still room contributes almost no readings
     * and cannot accumulate swings by sitting there.
     */
    const val FLICKER_SWINGS = 4
    const val FLICKER_WINDOW_SECONDS = 30L

    /**
     * Noise variability, in the same relative dB scale as [LOUD_DB].
     *
     * A fan, traffic or a distant motorway sit within a few dB of themselves; speech swings
     * far more as words start and stop. This is what separates "a room with a hum" from "a
     * room with a conversation in it", which matter very differently for concentration and
     * which a level threshold alone cannot tell apart.
     */
    const val RESTLESS_SPREAD_DB = 12f

    /** Below this the room is quiet enough that its variation is measurement noise. */
    const val RESTLESS_FLOOR_DB = 30f

    /**
     * How many times the level must cross the middle of its own range before it counts as
     * restless rather than merely different.
     *
     * Three, so a single step — a fan switched on, a window opened — is ignored however
     * large the change, while speech starting and stopping qualifies quickly.
     */
    const val RESTLESS_CROSSINGS = 3
    const val RESTLESS_WINDOW_SECONDS = 20L

    /**
     * Pick-ups within [FIDGET_WINDOW_SECONDS] before handling the phone is treated as a
     * habit rather than a moment.
     *
     * Five minutes rather than the whole session, so it describes what is happening now and
     * clears once the user settles.
     */
    const val FIDGET_EVENTS = 4
    const val FIDGET_WINDOW_SECONDS = 300L

    /**
     * Longer than the others: being told repeatedly that you keep touching your phone is
     * itself a distraction, and the point has been made.
     */
    const val FIDGET_COOLDOWN_SECONDS = 300L

    /** How often readings are stored, so a session yields useful history without bloat. */
    const val SAMPLE_EVERY_SECONDS = 10L
}
