package rs.etf.focusguard.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A single pause within a session, planned or unplanned.
 *
 * [startOffsetSeconds] records how much focus time had accrued when the pause began, which
 * is what the prototype's pause log displays (e.g. "23:12 – 28:12"). It is not derivable
 * from [startedAt] alone, because earlier pauses shift wall-clock time relative to focus time.
 */
@Entity(
    tableName = "pauses",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId")],
)
data class Pause(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: Long,

    val type: PauseType,

    /** Focus seconds elapsed at the moment the pause started. */
    val startOffsetSeconds: Int,

    val startedAt: Instant,

    /** Null while the pause is still running. */
    val endedAt: Instant? = null,

    /** Length of the pause; still growing while [endedAt] is null. */
    val durationSeconds: Int = 0,
)
