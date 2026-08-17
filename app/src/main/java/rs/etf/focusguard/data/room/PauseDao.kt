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
}
