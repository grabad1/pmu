package rs.etf.focusguard.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One interruption from outside the app during a session — a call, or a notification from
 * another app.
 *
 * Only the source app and the moment are stored. Notification titles and text are
 * deliberately *not* recorded: the feature exists to count interruptions and name the app
 * responsible, and keeping message contents would be a far larger privacy cost for no gain.
 */
@Entity(
    tableName = "interruptions",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId"), Index("sessionId", "packageName")],
)
data class Interruption(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: Long,

    val kind: InterruptionKind,

    /** Source app, e.g. `com.whatsapp`. For a call, the dialer that reported it. */
    val packageName: String,

    /** Human-readable app name resolved at capture time, so history survives an uninstall. */
    val appLabel: String,

    val occurredAt: Instant,
)
