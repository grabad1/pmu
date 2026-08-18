package rs.etf.focusguard.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One focus session — scheduled, running or finished.
 *
 * [focusedSeconds] counts only time actually spent focusing: pause time is excluded, so it
 * can be compared directly against [goalMinutes]. Exceeding the goal is a success state,
 * so this value is deliberately allowed to grow past it.
 */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    /** Target focus time in minutes, excluding planned pauses. */
    val goalMinutes: Int,

    /** How many planned pauses to insert during the session. */
    val plannedPauseCount: Int,

    /** Length of each planned pause, in minutes. */
    val plannedPauseMinutes: Int,

    val status: SessionStatus,

    /** Set for sessions created through the scheduling flow; null for ad-hoc ones. */
    val scheduledAt: Instant? = null,

    val startedAt: Instant? = null,

    val endedAt: Instant? = null,

    /** Accumulated focus time, excluding all pauses. */
    val focusedSeconds: Int = 0,

    /**
     * Seconds spent in another app while the session was focusing.
     *
     * Time during a break is not counted: a break is time the user is entitled to spend
     * wherever they like. This is the one distraction no sensor can see, so it is measured
     * directly and fed to the rating.
     */
    val awaySeconds: Int = 0,

    /** AI rating, 0..100. Null until the session has been analysed. */
    val focusScore: Int? = null,

    /** One-line AI verdict. */
    val aiComment: String? = null,

    /** Longer AI analysis paragraph. */
    val aiAnalysis: String? = null,
) {
    /** Wall-clock time the session is expected to occupy, including planned pauses. */
    val plannedTotalMinutes: Int
        get() = goalMinutes + plannedPauseCount * plannedPauseMinutes
}
