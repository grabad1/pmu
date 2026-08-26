package rs.etf.focusguard.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SensorSampleDao {

    @Insert
    suspend fun insert(sample: SensorSample)

    @Insert
    suspend fun insertAll(samples: List<SensorSample>)

    @Query("SELECT * FROM sensor_samples WHERE sessionId = :sessionId ORDER BY recordedAt ASC")
    suspend fun getBySession(sessionId: Long): List<SensorSample>

    @Query(
        "SELECT * FROM sensor_samples WHERE sessionId = :sessionId AND kind = :kind ORDER BY recordedAt ASC"
    )
    suspend fun getBySessionAndKind(sessionId: Long, kind: SensorKind): List<SensorSample>

    /**
     * Fraction of samples of [kind] that fall below [threshold] — used for "how much of the
     * session was spent in the dark". Returns null when no samples of that kind exist.
     */
    @Query(
        """
        SELECT CAST(SUM(CASE WHEN value < :threshold THEN 1 ELSE 0 END) AS REAL) / COUNT(*)
        FROM sensor_samples WHERE sessionId = :sessionId AND kind = :kind
        """
    )
    suspend fun fractionBelow(sessionId: Long, kind: SensorKind, threshold: Float): Double?

    /**
     * Fraction of samples of [kind] that exceed [threshold] — used for noise and motion.
     * Returns null when no samples of that kind exist.
     */
    @Query(
        """
        SELECT CAST(SUM(CASE WHEN value > :threshold THEN 1 ELSE 0 END) AS REAL) / COUNT(*)
        FROM sensor_samples WHERE sessionId = :sessionId AND kind = :kind
        """
    )
    suspend fun fractionAbove(sessionId: Long, kind: SensorKind, threshold: Float): Double?

    /**
     * The same two questions asked across every finished session matching a filter, so the
     * history screen can say "for maths, the light was bad 10% of the time".
     *
     * Every stored sample counts equally, which is fair because sampling is periodic: a
     * session twice as long contributes twice as many samples and so twice the weight.
     */
    @Query(
        """
        SELECT CAST(SUM(CASE WHEN ss.value < :threshold THEN 1 ELSE 0 END) AS REAL) / COUNT(*)
        FROM sensor_samples ss
        JOIN sessions s ON s.id = ss.sessionId
        WHERE ss.kind = :kind AND s.status = 'COMPLETED'
          AND s.id != :excludeSessionId
          AND (:category IS NULL OR s.category = :category COLLATE NOCASE)
          AND (:topic IS NULL OR s.topic = :topic COLLATE NOCASE)
        """
    )
    suspend fun fractionBelowFiltered(
        kind: SensorKind,
        threshold: Float,
        category: String?,
        topic: String?,
        excludeSessionId: Long = -1,
    ): Double?

    @Query(
        """
        SELECT CAST(SUM(CASE WHEN ss.value > :threshold THEN 1 ELSE 0 END) AS REAL) / COUNT(*)
        FROM sensor_samples ss
        JOIN sessions s ON s.id = ss.sessionId
        WHERE ss.kind = :kind AND s.status = 'COMPLETED'
          AND s.id != :excludeSessionId
          AND (:category IS NULL OR s.category = :category COLLATE NOCASE)
          AND (:topic IS NULL OR s.topic = :topic COLLATE NOCASE)
        """
    )
    suspend fun fractionAboveFiltered(
        kind: SensorKind,
        threshold: Float,
        category: String?,
        topic: String?,
        excludeSessionId: Long = -1,
    ): Double?
}
