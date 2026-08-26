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

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int

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

    /**
     * Finished sessions that never got a score.
     *
     * Rating happens in a coroutine after the session is stored, so a process death inside
     * that window leaves the row finished but unrated, and nothing would otherwise go back
     * for it. Oldest first, so the longest-waiting session is rescued first.
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE status = 'COMPLETED' AND focusScore IS NULL
        ORDER BY endedAt ASC
        """
    )
    suspend fun getUnrated(): List<Session>

    /**
     * Categories and topics the user has actually used, for the form's suggestions and the
     * history filter. Compared case-insensitively so "Math" and "math" are one topic.
     *
     * The form offers everything ever used, including on sessions merely scheduled — having
     * planned a "Compilers" session is exactly when you want it suggested again. The history
     * filter uses the COMPLETED variants below, because offering a filter that can only ever
     * produce an empty list is a dead end.
     */
    @Query(
        """
        SELECT DISTINCT category FROM sessions
        WHERE category IS NOT NULL AND TRIM(category) != ''
        ORDER BY category COLLATE NOCASE ASC
        """
    )
    fun getCategoriesAsFlow(): Flow<List<String>>

    @Query(
        """
        SELECT DISTINCT topic FROM sessions
        WHERE topic IS NOT NULL AND TRIM(topic) != ''
          AND (:category IS NULL OR category = :category COLLATE NOCASE)
        ORDER BY topic COLLATE NOCASE ASC
        """
    )
    fun getTopicsAsFlow(category: String?): Flow<List<String>>

    @Query(
        """
        SELECT DISTINCT category FROM sessions
        WHERE status = 'COMPLETED' AND category IS NOT NULL AND TRIM(category) != ''
        ORDER BY category COLLATE NOCASE ASC
        """
    )
    fun getCompletedCategoriesAsFlow(): Flow<List<String>>

    @Query(
        """
        SELECT DISTINCT topic FROM sessions
        WHERE status = 'COMPLETED' AND topic IS NOT NULL AND TRIM(topic) != ''
          AND (:category IS NULL OR category = :category COLLATE NOCASE)
        ORDER BY topic COLLATE NOCASE ASC
        """
    )
    fun getCompletedTopicsAsFlow(category: String?): Flow<List<String>>

    /**
     * Finished sessions narrowed to a category and/or topic. A null filter means "any",
     * which keeps one query serving the unfiltered list as well.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM sessions
        WHERE status = 'COMPLETED'
          AND (:category IS NULL OR category = :category COLLATE NOCASE)
          AND (:topic IS NULL OR topic = :topic COLLATE NOCASE)
        ORDER BY endedAt DESC
        """
    )
    fun getCompletedWithPausesFiltered(
        category: String?,
        topic: String?,
    ): Flow<List<SessionWithPauses>>

    /**
     * Session-level averages for a filter — the "how do my maths sessions go" numbers.
     * Sensor and pause averages are gathered separately and assembled in the repository,
     * because one query doing all of it would be unreadable and hard to trust.
     */
    @Query(
        """
        SELECT COUNT(*) AS sessionCount,
               AVG(focusScore) AS avgScore,
               AVG(focusedSeconds) AS avgFocusedSeconds,
               AVG(goalMinutes) AS avgGoalMinutes,
               COALESCE(SUM(focusedSeconds), 0) AS totalFocusedSeconds,
               COALESCE(SUM(awaySeconds), 0) AS totalAwaySeconds
        FROM sessions
        WHERE status = 'COMPLETED'
          AND id != :excludeSessionId
          AND (:category IS NULL OR category = :category COLLATE NOCASE)
          AND (:topic IS NULL OR topic = :topic COLLATE NOCASE)
        """
    )
    suspend fun aggregate(
        category: String?,
        topic: String?,
        excludeSessionId: Long = -1,
    ): SessionAggregate
}

/** Raw session-level averages behind a topic summary. */
data class SessionAggregate(
    val sessionCount: Int,
    val avgScore: Double?,
    val avgFocusedSeconds: Double?,
    val avgGoalMinutes: Double?,
    val totalFocusedSeconds: Int,
    val totalAwaySeconds: Int,
)
