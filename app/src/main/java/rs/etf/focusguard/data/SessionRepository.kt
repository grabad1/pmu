package rs.etf.focusguard.data

import rs.etf.focusguard.data.room.Pause
import rs.etf.focusguard.data.room.PauseDao
import rs.etf.focusguard.data.room.PauseType
import rs.etf.focusguard.data.room.SensorKind
import rs.etf.focusguard.data.room.SensorSample
import rs.etf.focusguard.data.room.SensorSampleDao
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.data.room.SessionDao
import rs.etf.focusguard.data.room.SessionStatus
import rs.etf.focusguard.data.room.SessionWithPauses
import rs.etf.focusguard.sensors.EnvironmentThresholds
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Single entry point to stored session data. ViewModels and the session service talk to this
 * rather than to DAOs directly.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val pauseDao: PauseDao,
    private val sensorSampleDao: SensorSampleDao,
) {

    val scheduledSessions = sessionDao.getScheduledAsFlow()
    val completedSessions = sessionDao.getCompletedAsFlow()
    val completedSessionsWithPauses = sessionDao.getCompletedWithPausesAsFlow()
    val runningSession = sessionDao.getRunningAsFlow()

    /** Categories the user has actually used, for suggestions and history filtering. */
    val usedCategories = sessionDao.getCategoriesAsFlow()

    /** Topics used, optionally narrowed to one category. */
    fun usedTopics(category: String? = null) = sessionDao.getTopicsAsFlow(category)

    /** The same, but only what appears on finished sessions — see [SessionDao]. */
    val historyCategories = sessionDao.getCompletedCategoriesAsFlow()

    fun historyTopics(category: String? = null) = sessionDao.getCompletedTopicsAsFlow(category)

    /** Finished sessions narrowed by category and/or topic; nulls mean "any". */
    fun completedSessionsFiltered(category: String?, topic: String?) =
        sessionDao.getCompletedWithPausesFiltered(category, topic)

    suspend fun getSession(id: Long): Session? = sessionDao.getById(id)

    /** Used only to decide whether a fresh install has anything in it yet. */
    suspend fun countSessions(): Int = sessionDao.count()

    fun getSessionAsFlow(id: Long) = sessionDao.getByIdAsFlow(id)

    suspend fun getSessionWithPauses(id: Long): SessionWithPauses? = sessionDao.getWithPauses(id)

    suspend fun getRunningSession(): Session? = sessionDao.getRunning()

    /** Finished sessions still waiting for a score. See [SessionDao.getUnrated]. */
    suspend fun getUnratedSessions(): List<Session> = sessionDao.getUnrated()

    suspend fun insertSession(session: Session): Long = sessionDao.insert(session)

    suspend fun updateSession(session: Session) = sessionDao.update(session)

    suspend fun deleteSession(session: Session) = sessionDao.delete(session)

    /**
     * Scheduled sessions clashing with the window [start] .. [start] + [durationMinutes].
     * [excludedId] lets an existing session be rescheduled without conflicting with itself.
     */
    suspend fun findConflicts(
        start: Instant,
        durationMinutes: Int,
        excludedId: Long = -1,
    ): List<Session> = sessionDao.findScheduledOverlapping(
        start = start,
        end = start.plus(Duration.ofMinutes(durationMinutes.toLong())),
        excludedId = excludedId,
    )

    suspend fun cancelScheduled(session: Session) {
        sessionDao.update(session.copy(status = SessionStatus.CANCELLED))
    }

    suspend fun getPauses(sessionId: Long): List<Pause> = pauseDao.getBySession(sessionId)

    fun getPausesAsFlow(sessionId: Long) = pauseDao.getBySessionAsFlow(sessionId)

    suspend fun insertPause(pause: Pause): Long = pauseDao.insert(pause)

    suspend fun updatePause(pause: Pause) = pauseDao.update(pause)

    suspend fun getOpenPause(sessionId: Long): Pause? = pauseDao.getOpenPause(sessionId)

    suspend fun countPauses(sessionId: Long, type: PauseType): Int =
        pauseDao.countByType(sessionId, type)

    suspend fun insertSensorSample(sample: SensorSample) = sensorSampleDao.insert(sample)

    suspend fun insertSensorSamples(samples: List<SensorSample>) =
        sensorSampleDao.insertAll(samples)

    suspend fun getSensorSamples(sessionId: Long): List<SensorSample> =
        sensorSampleDao.getBySession(sessionId)

    /** Stores the rating produced for a finished session. */
    suspend fun saveRating(sessionId: Long, rating: SessionRating) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(
            session.copy(
                focusScore = rating.score,
                aiComment = rating.comment,
                aiAnalysis = rating.analysis,
            )
        )
    }

    suspend fun getSensorSamples(sessionId: Long, kind: SensorKind): List<SensorSample> =
        sensorSampleDao.getBySessionAndKind(sessionId, kind)

    suspend fun fractionBelow(sessionId: Long, kind: SensorKind, threshold: Float): Double =
        sensorSampleDao.fractionBelow(sessionId, kind, threshold) ?: 0.0

    suspend fun fractionAbove(sessionId: Long, kind: SensorKind, threshold: Float): Double =
        sensorSampleDao.fractionAbove(sessionId, kind, threshold) ?: 0.0

    /**
     * Averages across every finished session matching a filter. Null category and topic give
     * the user's overall baseline, which is what a single topic gets compared against.
     *
     * [excludeSessionId] leaves one session out. Rating uses it so a finished session is
     * compared against the ones *before* it rather than against an average it is itself part
     * of — with a handful of sessions, including it would flatten the very difference the
     * comparison is meant to show.
     */
    suspend fun summarise(
        category: String? = null,
        topic: String? = null,
        excludeSessionId: Long = -1,
    ): TopicSummary {
        val totals = sessionDao.aggregate(category, topic, excludeSessionId)
        if (totals.sessionCount == 0) return TopicSummary.empty(category, topic)

        val planned = pauseDao.countByTypeFiltered(
            PauseType.PLANNED, category, topic, excludeSessionId,
        )
        val unplanned = pauseDao.countByTypeFiltered(
            PauseType.UNPLANNED, category, topic, excludeSessionId,
        )

        return TopicSummary(
            category = category,
            topic = topic,
            sessionCount = totals.sessionCount,
            averageScore = totals.avgScore?.roundToInt(),
            averageFocusedSeconds = totals.avgFocusedSeconds?.roundToInt() ?: 0,
            averageGoalMinutes = totals.avgGoalMinutes?.roundToInt() ?: 0,
            averagePlannedPauses = planned.toDouble() / totals.sessionCount,
            averageUnplannedPauses = unplanned.toDouble() / totals.sessionCount,
            darkFraction = sensorSampleDao.fractionBelowFiltered(
                SensorKind.LIGHT, EnvironmentThresholds.DARK_LUX, category, topic, excludeSessionId,
            ) ?: 0.0,
            loudFraction = sensorSampleDao.fractionAboveFiltered(
                SensorKind.NOISE, EnvironmentThresholds.LOUD_DB, category, topic, excludeSessionId,
            ) ?: 0.0,
            movementFraction = sensorSampleDao.fractionAboveFiltered(
                SensorKind.MOTION, EnvironmentThresholds.MOVEMENT_MS2, category, topic,
                excludeSessionId,
            ) ?: 0.0,
            totalAwaySeconds = totals.totalAwaySeconds,
        )
    }
}
