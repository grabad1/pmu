package rs.etf.focusguard.data.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    @Delete
    suspend fun delete(session: Session)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): Session?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun getByIdAsFlow(id: Long): Flow<Session?>

    @Query("SELECT * FROM sessions WHERE status = 'SCHEDULED' ORDER BY scheduledAt ASC")
    fun getScheduledAsFlow(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' ORDER BY endedAt DESC")
    fun getCompletedAsFlow(): Flow<List<Session>>

    /**
     * Scheduled sessions whose planned window overlaps [start]..[end]. Used to reject a new
     * scheduled session that would clash with an existing one.
     *
     * The stored end of a session is derived rather than persisted: goal time plus all
     * planned pause time, in milliseconds.
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE status = 'SCHEDULED'
          AND :excludedId != id
          AND scheduledAt IS NOT NULL
          AND scheduledAt < :end
          AND (scheduledAt + (goalMinutes + plannedPauseCount * plannedPauseMinutes) * 60000) > :start
        ORDER BY scheduledAt ASC
        """
    )
    suspend fun findScheduledOverlapping(
        start: Instant,
        end: Instant,
        excludedId: Long = -1,
    ): List<Session>

    /** At most one session may be RUNNING at a time. */
    @Query("SELECT * FROM sessions WHERE status = 'RUNNING' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getRunning(): Session?

    @Query("SELECT * FROM sessions WHERE status = 'RUNNING' ORDER BY startedAt DESC LIMIT 1")
    fun getRunningAsFlow(): Flow<Session?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getWithPauses(id: Long): SessionWithPauses?

    @Transaction
    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' ORDER BY endedAt DESC")
    fun getCompletedWithPausesAsFlow(): Flow<List<SessionWithPauses>>
}
