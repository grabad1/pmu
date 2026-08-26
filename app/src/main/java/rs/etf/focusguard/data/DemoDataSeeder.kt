package rs.etf.focusguard.data

import android.util.Log
import rs.etf.focusguard.BuildConfig
import rs.etf.focusguard.LOG_TAG
import rs.etf.focusguard.alarms.SessionAlarmScheduler
import rs.etf.focusguard.data.room.Pause
import rs.etf.focusguard.data.room.PauseType
import rs.etf.focusguard.data.room.SensorKind
import rs.etf.focusguard.data.room.SensorSample
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.data.room.SessionStatus
import rs.etf.focusguard.util.atTimeToInstant
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Fills an empty database with a few realistic sessions, so the app has something to show
 * without having to sit through five focus sessions first.
 *
 * Three deliberate limits:
 *
 * - **Only when the database is completely empty.** Never merges with, tops up or overwrites
 *   real sessions — the user's own history is the point of the app, and demo rows mixed into
 *   it would quietly corrupt every average on the history screen.
 * - **Debug builds only**, so a release build can never ship invented history.
 * - **Every seeded session already carries a score**, which keeps `rateOutstandingSessions()`
 *   from firing five Gemini calls the first time the app opens — the free tier allows twenty
 *   in a rolling window, and spending a quarter of it on fake data would be careless.
 *
 * The data is generated from a fixed random seed, so the demo looks the same every time.
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val repository: SessionRepository,
    private val alarmScheduler: SessionAlarmScheduler,
) {

    suspend fun seedIfEmpty() {
        if (!BuildConfig.DEBUG) return
        if (repository.countSessions() > 0) return

        Log.d(LOG_TAG, "Empty database; seeding demo sessions")
        finishedSessions().forEach { seedFinished(it) }
        upcomingSessions().forEach { seedScheduled(it) }
        Log.d(LOG_TAG, "Seeded 5 finished and 3 scheduled sessions")
    }

    private suspend fun seedFinished(demo: DemoSession) {
        val endedAt = demo.endedAt
        val startedAt = endedAt.minusSeconds(
            (demo.focusedSeconds + demo.pauses.sumOf { it.durationSeconds }).toLong()
        )

        val id = repository.insertSession(
            Session(
                name = demo.name,
                category = demo.category,
                topic = demo.topic,
                goalMinutes = demo.goalMinutes,
                plannedPauseCount = demo.plannedPauseCount,
                plannedPauseMinutes = demo.plannedPauseMinutes,
                status = SessionStatus.COMPLETED,
                startedAt = startedAt,
                endedAt = endedAt,
                focusedSeconds = demo.focusedSeconds,
                awaySeconds = demo.awaySeconds,
                focusScore = demo.score,
                aiComment = demo.comment,
                aiAnalysis = demo.analysis,
            )
        )

        demo.pauses.forEach { pause ->
            val pauseStart = startedAt.plusSeconds(pause.startOffsetSeconds.toLong())
            repository.insertPause(
                Pause(
                    sessionId = id,
                    type = pause.type,
                    startOffsetSeconds = pause.startOffsetSeconds,
                    startedAt = pauseStart,
                    endedAt = pauseStart.plusSeconds(pause.durationSeconds.toLong()),
                    durationSeconds = pause.durationSeconds,
                )
            )
        }

        repository.insertSensorSamples(samplesFor(id, startedAt, demo))
    }

    /**
     * Readings every ten seconds, matching how the real monitor stores them, so the history
     * percentages and anything drawn from samples later have believable material.
     */
    private fun samplesFor(
        sessionId: Long,
        startedAt: Instant,
        demo: DemoSession,
    ): List<SensorSample> {
        val random = Random(demo.name.hashCode())
        val sampleCount = (demo.focusedSeconds / SAMPLE_INTERVAL_SECONDS).coerceIn(6, 120)
        val samples = mutableListOf<SensorSample>()

        repeat(sampleCount) { index ->
            val at = startedAt.plusSeconds((index * SAMPLE_INTERVAL_SECONDS).toLong())
            val bad = index.toDouble() / sampleCount

            val lux = if (bad < demo.darkShare) random.nextDouble(2.0, 12.0)
            else random.nextDouble(120.0, 400.0)
            val decibels = if (bad < demo.loudShare) random.nextDouble(52.0, 68.0)
            else random.nextDouble(18.0, 42.0)
            val motion = if (bad < demo.movingShare) random.nextDouble(2.8, 6.0)
            else random.nextDouble(0.0, 1.2)

            samples += SensorSample(sessionId = sessionId, kind = SensorKind.LIGHT, value = lux.toFloat(), recordedAt = at)
            samples += SensorSample(sessionId = sessionId, kind = SensorKind.NOISE, value = decibels.toFloat(), recordedAt = at)
            samples += SensorSample(sessionId = sessionId, kind = SensorKind.MOTION, value = motion.toFloat(), recordedAt = at)
        }
        return samples
    }

    private suspend fun seedScheduled(demo: DemoScheduled) {
        val session = Session(
            name = demo.name,
            category = demo.category,
            topic = demo.topic,
            goalMinutes = demo.goalMinutes,
            plannedPauseCount = demo.plannedPauseCount,
            plannedPauseMinutes = demo.plannedPauseMinutes,
            status = SessionStatus.SCHEDULED,
            scheduledAt = demo.at,
        )
        val id = repository.insertSession(session)
        // Booked against the stored id, exactly as the scheduling screen does, so the seeded
        // sessions genuinely remind rather than merely looking like they would.
        alarmScheduler.schedule(session.copy(id = id))
    }

    private fun finishedSessions(): List<DemoSession> {
        val today = LocalDate.now()

        return listOf(
            DemoSession(
                name = "Linear Algebra",
                category = "Studying",
                topic = "Math",
                goalMinutes = 45,
                focusedSeconds = 47 * 60 + 20,
                plannedPauseCount = 2,
                plannedPauseMinutes = 5,
                endedAt = today.minusDays(1).atTimeToInstant(LocalTime.of(18, 42)),
                pauses = listOf(
                    DemoPause(PauseType.PLANNED, 15 * 60, 5 * 60),
                    DemoPause(PauseType.PLANNED, 30 * 60, 5 * 60),
                ),
                score = 88,
                comment = "Strong session — you passed your goal without a single unplanned break.",
                analysis = "You focused for 47 minutes against a 45-minute goal and took both " +
                    "planned breaks, which is exactly how this is meant to go. Conditions were " +
                    "clean throughout. Keep the phone where it was.",
                darkShare = 0.0,
                loudShare = 0.05,
                movingShare = 0.02,
            ),
            DemoSession(
                name = "Operating Systems",
                category = "Studying",
                topic = "Operating Systems",
                goalMinutes = 60,
                focusedSeconds = 52 * 60,
                plannedPauseCount = 3,
                plannedPauseMinutes = 5,
                awaySeconds = 95,
                endedAt = today.minusDays(2).atTimeToInstant(LocalTime.of(20, 15)),
                pauses = listOf(
                    DemoPause(PauseType.PLANNED, 15 * 60, 5 * 60),
                    DemoPause(PauseType.PLANNED, 30 * 60, 5 * 60),
                    DemoPause(PauseType.UNPLANNED, 41 * 60, 4 * 60 + 30),
                ),
                score = 68,
                comment = "Good work, but the room was noisy and you left the app twice.",
                analysis = "You reached 52 of 60 minutes. One unplanned break and a minute and " +
                    "a half in another app cost you more than the noise did. Leaving the timer " +
                    "running while you are elsewhere is the single thing to change.",
                darkShare = 0.0,
                loudShare = 0.4,
                movingShare = 0.1,
            ),
            DemoSession(
                name = "Math Problem Set",
                category = "Studying",
                topic = "Math",
                goalMinutes = 30,
                focusedSeconds = 17 * 60 + 40,
                plannedPauseCount = 1,
                plannedPauseMinutes = 5,
                awaySeconds = 200,
                endedAt = today.minusDays(4).atTimeToInstant(LocalTime.of(22, 5)),
                pauses = listOf(
                    DemoPause(PauseType.UNPLANNED, 6 * 60, 3 * 60),
                    DemoPause(PauseType.UNPLANNED, 12 * 60, 6 * 60),
                ),
                score = 41,
                comment = "This one got away from you — two long unplanned breaks and a dark room.",
                analysis = "You stopped at 18 of 30 minutes, and over three of those minutes " +
                    "were spent in another app. Working this late in a dark room is not helping " +
                    "either. Try the same set earlier in the day with the lamp on.",
                darkShare = 0.75,
                loudShare = 0.1,
                movingShare = 0.35,
            ),
            DemoSession(
                name = "Inbox Zero",
                category = "Work",
                topic = "Email",
                goalMinutes = 25,
                focusedSeconds = 25 * 60 + 10,
                plannedPauseCount = 1,
                plannedPauseMinutes = 5,
                endedAt = today.minusDays(5).atTimeToInstant(LocalTime.of(9, 30)),
                pauses = listOf(DemoPause(PauseType.PLANNED, 12 * 60, 5 * 60)),
                score = 81,
                comment = "Goal met with one planned break — a tidy session.",
                analysis = "Twenty-five minutes of focus against a twenty-five minute goal, one " +
                    "planned break taken, and the phone left alone. Nothing to fix here.",
                darkShare = 0.0,
                loudShare = 0.15,
                movingShare = 0.05,
            ),
            DemoSession(
                name = "Evening Stretch",
                category = "Yoga",
                topic = null,
                goalMinutes = 20,
                focusedSeconds = 21 * 60,
                plannedPauseCount = 0,
                plannedPauseMinutes = 0,
                endedAt = today.minusDays(6).atTimeToInstant(LocalTime.of(21, 10)),
                pauses = emptyList(),
                score = 90,
                comment = "Straight through, no breaks, no distractions.",
                analysis = "You went slightly past the goal with no pauses at all. The light was " +
                    "low, but for yoga in the evening that is the point rather than a problem.",
                darkShare = 0.6,
                loudShare = 0.0,
                movingShare = 0.15,
            ),
        )
    }

    /** Three upcoming sessions on the next two days, spaced so none of them conflict. */
    private fun upcomingSessions(): List<DemoScheduled> {
        val tomorrow = LocalDate.now().plusDays(1)
        val dayAfter = LocalDate.now().plusDays(2)

        return listOf(
            DemoScheduled(
                name = "Compilers Revision",
                category = "Studying",
                topic = "Compilers",
                goalMinutes = 60,
                plannedPauseCount = 2,
                plannedPauseMinutes = 5,
                at = tomorrow.atTimeToInstant(LocalTime.of(9, 0)),
            ),
            DemoScheduled(
                name = "Math Practice",
                category = "Studying",
                topic = "Math",
                goalMinutes = 45,
                plannedPauseCount = 1,
                plannedPauseMinutes = 10,
                at = tomorrow.atTimeToInstant(LocalTime.of(14, 30)),
            ),
            DemoScheduled(
                name = "Morning Yoga",
                category = "Yoga",
                topic = null,
                goalMinutes = 20,
                plannedPauseCount = 0,
                plannedPauseMinutes = 0,
                at = dayAfter.atTimeToInstant(LocalTime.of(8, 0)),
            ),
        )
    }

    private companion object {
        const val SAMPLE_INTERVAL_SECONDS = 10
    }
}

private data class DemoPause(
    val type: PauseType,
    val startOffsetSeconds: Int,
    val durationSeconds: Int,
)

private data class DemoSession(
    val name: String,
    val category: String?,
    val topic: String?,
    val goalMinutes: Int,
    val focusedSeconds: Int,
    val plannedPauseCount: Int,
    val plannedPauseMinutes: Int,
    val endedAt: Instant,
    val pauses: List<DemoPause>,
    val score: Int,
    val comment: String,
    val analysis: String,
    val darkShare: Double,
    val loudShare: Double,
    val movingShare: Double,
    val awaySeconds: Int = 0,
)

private data class DemoScheduled(
    val name: String,
    val category: String?,
    val topic: String?,
    val goalMinutes: Int,
    val plannedPauseCount: Int,
    val plannedPauseMinutes: Int,
    val at: Instant,
)
