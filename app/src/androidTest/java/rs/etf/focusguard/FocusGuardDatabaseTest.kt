package rs.etf.focusguard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import rs.etf.focusguard.data.room.FocusGuardDatabase
import rs.etf.focusguard.data.room.Pause
import rs.etf.focusguard.data.room.PauseType
import rs.etf.focusguard.data.room.SensorKind
import rs.etf.focusguard.data.room.SensorSample
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.data.room.SessionStatus
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class FocusGuardDatabaseTest {

    private lateinit var database: FocusGuardDatabase

    private val now: Instant = Instant.parse("2026-05-16T09:00:00Z")

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FocusGuardDatabase::class.java,
        ).build()
    }

    @After
    fun closeDatabase() = database.close()

    private fun session(
        name: String = "Deep Work Block",
        status: SessionStatus = SessionStatus.COMPLETED,
        goalMinutes: Int = 60,
        pauseCount: Int = 2,
        pauseMinutes: Int = 5,
        scheduledAt: Instant? = null,
        endedAt: Instant? = null,
    ) = Session(
        name = name,
        goalMinutes = goalMinutes,
        plannedPauseCount = pauseCount,
        plannedPauseMinutes = pauseMinutes,
        status = status,
        scheduledAt = scheduledAt,
        startedAt = now,
        endedAt = endedAt,
    )

    @Test
    fun sessionRoundTripsThroughConverters() = runBlocking {
        val id = database.sessionDao().insert(session(endedAt = now.plusSeconds(3600)))

        val loaded = database.sessionDao().getById(id)

        assertNotNull(loaded)
        assertEquals("Deep Work Block", loaded!!.name)
        assertEquals(SessionStatus.COMPLETED, loaded.status)
        assertEquals(now, loaded.startedAt)
        assertEquals(now.plusSeconds(3600), loaded.endedAt)
        // 60 goal + 2 pauses x 5 min
        assertEquals(70, loaded.plannedTotalMinutes)
    }

    @Test
    fun completedAndScheduledListsStaySeparate() = runBlocking {
        database.sessionDao().insert(session(name = "Finished", status = SessionStatus.COMPLETED))
        database.sessionDao().insert(
            session(
                name = "Upcoming",
                status = SessionStatus.SCHEDULED,
                scheduledAt = now.plus(Duration.ofDays(1)),
            )
        )

        val completed = database.sessionDao().getCompletedAsFlow().first()
        val scheduled = database.sessionDao().getScheduledAsFlow().first()

        assertEquals(listOf("Finished"), completed.map { it.name })
        assertEquals(listOf("Upcoming"), scheduled.map { it.name })
    }

    @Test
    fun pausesLoadWithTheirSessionAndSplitByType() = runBlocking {
        val sessionId = database.sessionDao().insert(session())

        database.pauseDao().insert(
            Pause(
                sessionId = sessionId,
                type = PauseType.PLANNED,
                startOffsetSeconds = 1200,
                startedAt = now.plusSeconds(1200),
                endedAt = now.plusSeconds(1500),
                durationSeconds = 300,
            )
        )
        database.pauseDao().insert(
            Pause(
                sessionId = sessionId,
                type = PauseType.UNPLANNED,
                startOffsetSeconds = 2400,
                startedAt = now.plusSeconds(2700),
                endedAt = now.plusSeconds(2880),
                durationSeconds = 180,
            )
        )

        val withPauses = database.sessionDao().getWithPauses(sessionId)

        assertNotNull(withPauses)
        assertEquals(2, withPauses!!.pauses.size)
        assertEquals(1, withPauses.plannedPauses.size)
        assertEquals(1, withPauses.unplannedPauses.size)
        assertEquals(
            1,
            database.pauseDao().countByType(sessionId, PauseType.UNPLANNED),
        )
    }

    @Test
    fun openPauseIsTheOneWithoutAnEnd() = runBlocking {
        val sessionId = database.sessionDao().insert(session())
        database.pauseDao().insert(
            Pause(
                sessionId = sessionId,
                type = PauseType.PLANNED,
                startOffsetSeconds = 600,
                startedAt = now.plusSeconds(600),
                endedAt = now.plusSeconds(900),
                durationSeconds = 300,
            )
        )

        assertNull(database.pauseDao().getOpenPause(sessionId))

        database.pauseDao().insert(
            Pause(
                sessionId = sessionId,
                type = PauseType.UNPLANNED,
                startOffsetSeconds = 1800,
                startedAt = now.plusSeconds(2100),
            )
        )

        val open = database.pauseDao().getOpenPause(sessionId)
        assertNotNull(open)
        assertEquals(PauseType.UNPLANNED, open!!.type)
    }

    @Test
    fun deletingASessionCascadesToPausesAndSamples() = runBlocking {
        val sessionId = database.sessionDao().insert(session())
        database.pauseDao().insert(
            Pause(
                sessionId = sessionId,
                type = PauseType.PLANNED,
                startOffsetSeconds = 600,
                startedAt = now.plusSeconds(600),
            )
        )
        database.sensorSampleDao().insert(
            SensorSample(sessionId = sessionId, kind = SensorKind.LIGHT, value = 12f, recordedAt = now)
        )

        database.sessionDao().delete(database.sessionDao().getById(sessionId)!!)

        assertTrue(database.pauseDao().getBySession(sessionId).isEmpty())
        assertTrue(database.sensorSampleDao().getBySession(sessionId).isEmpty())
    }

    @Test
    fun overlappingScheduledSessionsAreDetected() = runBlocking {
        // 09:00, 60 min goal + 2 x 5 min pauses = occupies 09:00-10:10
        database.sessionDao().insert(
            session(name = "Morning Focus", status = SessionStatus.SCHEDULED, scheduledAt = now)
        )

        val overlapping = database.sessionDao().findScheduledOverlapping(
            start = now.plus(Duration.ofMinutes(30)),
            end = now.plus(Duration.ofMinutes(90)),
        )
        assertEquals(listOf("Morning Focus"), overlapping.map { it.name })

        val touchingTheEnd = database.sessionDao().findScheduledOverlapping(
            start = now.plus(Duration.ofMinutes(70)),
            end = now.plus(Duration.ofMinutes(130)),
        )
        assertTrue(touchingTheEnd.isEmpty())

        val before = database.sessionDao().findScheduledOverlapping(
            start = now.minus(Duration.ofMinutes(60)),
            end = now,
        )
        assertTrue(before.isEmpty())
    }

    @Test
    fun sensorFractionsSummariseConditions() = runBlocking {
        val sessionId = database.sessionDao().insert(session())
        // 3 dark readings out of 4
        listOf(2f, 4f, 6f, 300f).forEach { lux ->
            database.sensorSampleDao().insert(
                SensorSample(
                    sessionId = sessionId,
                    kind = SensorKind.LIGHT,
                    value = lux,
                    recordedAt = now,
                )
            )
        }

        val dark = database.sensorSampleDao().fractionBelow(sessionId, SensorKind.LIGHT, 10f)

        assertNotNull(dark)
        assertEquals(0.75, dark!!, 0.0001)
        // No noise samples were recorded at all.
        assertNull(database.sensorSampleDao().fractionAbove(sessionId, SensorKind.NOISE, 60f))
    }

    @Test
    fun onlyTheRunningSessionIsReturnedAsRunning() = runBlocking {
        database.sessionDao().insert(session(name = "Old", status = SessionStatus.COMPLETED))
        database.sessionDao().insert(session(name = "Live", status = SessionStatus.RUNNING))

        val running = database.sessionDao().getRunning()

        assertNotNull(running)
        assertEquals("Live", running!!.name)
    }
}
