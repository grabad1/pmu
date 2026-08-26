package rs.etf.focusguard.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One app's share of the interruptions in a session, for the interruptions tab and advice. */
data class InterruptionCount(
    val packageName: String,
    val appLabel: String,
    val calls: Int,
    val notifications: Int,
) {
    val total: Int get() = calls + notifications
}

@Dao
interface InterruptionDao {

    @Insert
    suspend fun insert(interruption: Interruption): Long

    @Query("SELECT * FROM interruptions WHERE sessionId = :sessionId ORDER BY occurredAt ASC")
    suspend fun getBySession(sessionId: Long): List<Interruption>

    @Query("SELECT * FROM interruptions WHERE sessionId = :sessionId ORDER BY occurredAt ASC")
    fun getBySessionAsFlow(sessionId: Long): Flow<List<Interruption>>

    @Query("SELECT COUNT(*) FROM interruptions WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: Long): Int

    /**
     * Interruptions grouped by the app that caused them, busiest first — the shape the
     * "consider muting this" advice needs.
     */
    @Query(
        """
        SELECT packageName,
               MAX(appLabel) AS appLabel,
               SUM(CASE WHEN kind = 'CALL' THEN 1 ELSE 0 END) AS calls,
               SUM(CASE WHEN kind = 'NOTIFICATION' THEN 1 ELSE 0 END) AS notifications
        FROM interruptions
        WHERE sessionId = :sessionId
        GROUP BY packageName
        ORDER BY COUNT(*) DESC, appLabel COLLATE NOCASE ASC
        """
    )
    suspend fun countsByApp(sessionId: Long): List<InterruptionCount>
}
