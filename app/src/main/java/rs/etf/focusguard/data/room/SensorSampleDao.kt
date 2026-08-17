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
}
