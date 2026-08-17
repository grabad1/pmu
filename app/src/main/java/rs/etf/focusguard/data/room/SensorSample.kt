package rs.etf.focusguard.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A periodic environment reading taken while a session runs.
 *
 * Raw samples are stored rather than pre-computed verdicts so the warning thresholds can be
 * retuned later without invalidating history, and so the AI rating can reason about how long
 * conditions were bad rather than merely that they were.
 */
@Entity(
    tableName = "sensor_samples",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId"), Index("sessionId", "kind")],
)
data class SensorSample(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: Long,

    val kind: SensorKind,

    /** Lux for LIGHT, dB for NOISE, m/s² for MOTION. */
    val value: Float,

    val recordedAt: Instant,
)
