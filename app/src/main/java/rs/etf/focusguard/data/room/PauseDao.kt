package rs.etf.focusguard.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PauseDao {

    @Insert
    suspend fun insert(pause: Pause): Long

    @Update
    suspend fun update(pause: Pause)

    @Query("SELECT * FROM pauses WHERE sessionId = :sessionId ORDER BY startOffsetSeconds ASC")
    suspend fun getBySession(sessionId: Long): List<Pause>

    @Query("SELECT * FROM pauses WHERE sessionId = :sessionId ORDER BY startOffsetSeconds ASC")
    fun getBySessionAsFlow(sessionId: Long): Flow<List<Pause>>

    /** The pause currently in progress, if the user is paused right now. */
    @Query("SELECT * FROM pauses WHERE sessionId = :sessionId AND endedAt IS NULL LIMIT 1")
    suspend fun getOpenPause(sessionId: Long): Pause?

    @Query("SELECT COUNT(*) FROM pauses WHERE sessionId = :sessionId AND type = :type")
    suspend fun countByType(sessionId: Long, type: PauseType): Int

    /**
     * Total pauses of one type across every finished session matching a filter, for the
     * per-topic averages. Divided by the session count by the caller, so a topic with no
     * sessions cannot divide by zero here.
     */
    @Query(
        """
        SELECT COUNT(*) FROM pauses p
        JOIN sessions s ON s.id = p.sessionId
        WHERE s.status = 'COMPLETED' AND p.type = :type
          AND s.id != :excludeSessionId
          AND (:category IS NULL OR s.category = :category COLLATE NOCASE)
          AND (:topic IS NULL OR s.topic = :topic COLLATE NOCASE)
        """
    )
    suspend fun countByTypeFiltered(
        type: PauseType,
        category: String?,
        topic: String?,
        excludeSessionId: Long = -1,
    ): Int
}
