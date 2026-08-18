package rs.etf.focusguard.sensors

/**
 * Environment problems the app reacts to during a session.
 *
 * Only [MOVEMENT] is serious enough to interrupt with a full-screen warning, because picking
 * the phone up is the failure mode the app exists to prevent. The others are advisory and
 * appear as toasts.
 */
enum class WarningKind {
    MOVEMENT,
    BAD_LIGHT,
    LOUD_ROOM,
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

    /** How often readings are stored, so a session yields useful history without bloat. */
    const val SAMPLE_EVERY_SECONDS = 10L
}
