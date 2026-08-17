package rs.etf.focusguard.ui.stateholders

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.parcelize.Parcelize
import kotlinx.coroutines.launch
import rs.etf.focusguard.data.SessionRepository
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.data.room.SessionStatus
import rs.etf.focusguard.util.atTimeToInstant
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Numeric fields are held as strings so the user can clear a field while typing without it
 * snapping back to a default. They are parsed only when the form is read.
 */
@Parcelize
data class NewSessionUiState(
    val name: String = "",
    val goalMinutes: String = "45",
    val pauseCount: String = "2",
    val pauseMinutes: String = "5",
    val isScheduleDialogOpen: Boolean = false,
    val scheduleName: String = "",
    val scheduleDateEpochDay: Long? = null,
    val scheduleHour: Int = 9,
    val scheduleMinute: Int = 0,
    val scheduleError: String? = null,
    val conflictName: String? = null,
) : Parcelable {

    val goalMinutesOrDefault: Int get() = goalMinutes.toIntOrNull()?.coerceAtLeast(1) ?: 25
    val pauseCountOrZero: Int get() = pauseCount.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val pauseMinutesOrZero: Int get() = pauseMinutes.toIntOrNull()?.coerceAtLeast(0) ?: 0

    val totalPauseMinutes: Int get() = pauseCountOrZero * pauseMinutesOrZero
    val plannedTotalMinutes: Int get() = goalMinutesOrDefault + totalPauseMinutes

    val scheduleDate: LocalDate
        get() = scheduleDateEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()

    val scheduleTime: LocalTime get() = LocalTime.of(scheduleHour, scheduleMinute)

    val scheduleInstant: Instant get() = scheduleDate.atTimeToInstant(scheduleTime)
}

@HiltViewModel
class NewSessionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    companion object {
        private const val UI_STATE_KEY = "uiState"
    }

    val uiState = savedStateHandle.getStateFlow(UI_STATE_KEY, NewSessionUiState())

    val scheduledSessions = sessionRepository.scheduledSessions

    private fun update(transform: (NewSessionUiState) -> NewSessionUiState) {
        savedStateHandle[UI_STATE_KEY] = transform(uiState.value)
    }

    fun onNameChange(value: String) = update { it.copy(name = value) }

    fun onGoalMinutesChange(value: String) = update { it.copy(goalMinutes = value.digitsOnly()) }

    fun onPauseCountChange(value: String) = update { it.copy(pauseCount = value.digitsOnly()) }

    fun onPauseMinutesChange(value: String) = update { it.copy(pauseMinutes = value.digitsOnly()) }

    fun openScheduleDialog() = update {
        it.copy(
            isScheduleDialogOpen = true,
            scheduleName = it.name,
            scheduleDateEpochDay = it.scheduleDateEpochDay ?: LocalDate.now().toEpochDay(),
            scheduleError = null,
            conflictName = null,
        )
    }

    fun closeScheduleDialog() = update {
        it.copy(isScheduleDialogOpen = false, scheduleError = null, conflictName = null)
    }

    fun onScheduleNameChange(value: String) = update { it.copy(scheduleName = value) }

    fun onScheduleDateChange(epochDay: Long) = update {
        it.copy(scheduleDateEpochDay = epochDay, scheduleError = null, conflictName = null)
    }

    fun onScheduleTimeChange(hour: Int, minute: Int) = update {
        it.copy(scheduleHour = hour, scheduleMinute = minute, scheduleError = null, conflictName = null)
    }

    /**
     * Persists the configured session as RUNNING and hands its id back so the caller can
     * start the service and navigate. The session row must exist before the service starts,
     * since the service looks it up by id.
     */
    fun startSession(onStarted: (Long) -> Unit) {
        val state = uiState.value
        viewModelScope.launch {
            val id = sessionRepository.insertSession(
                Session(
                    name = state.name.trim().ifBlank { "New Session" },
                    goalMinutes = state.goalMinutesOrDefault,
                    plannedPauseCount = state.pauseCountOrZero,
                    plannedPauseMinutes = state.pauseMinutesOrZero,
                    status = SessionStatus.RUNNING,
                    startedAt = Instant.now(),
                )
            )
            onStarted(id)
        }
    }

    /**
     * Validates and stores a scheduled session. [onScheduled] runs only on success, so the
     * caller can navigate away and show confirmation.
     */
    fun schedule(onScheduled: (String) -> Unit) {
        val state = uiState.value
        val name = state.scheduleName.trim()

        if (name.isEmpty()) {
            update { it.copy(scheduleError = "Please enter a session name.", conflictName = null) }
            return
        }

        val start = state.scheduleInstant
        if (start.isBefore(Instant.now())) {
            update { it.copy(scheduleError = "That time is already in the past.", conflictName = null) }
            return
        }

        viewModelScope.launch {
            val conflict = sessionRepository
                .findConflicts(start = start, durationMinutes = state.plannedTotalMinutes)
                .firstOrNull()

            if (conflict != null) {
                update { it.copy(scheduleError = null, conflictName = conflict.name) }
                return@launch
            }

            sessionRepository.insertSession(
                Session(
                    name = name,
                    goalMinutes = state.goalMinutesOrDefault,
                    plannedPauseCount = state.pauseCountOrZero,
                    plannedPauseMinutes = state.pauseMinutesOrZero,
                    status = SessionStatus.SCHEDULED,
                    scheduledAt = start,
                )
            )
            update { it.copy(isScheduleDialogOpen = false, scheduleError = null, conflictName = null) }
            onScheduled(name)
        }
    }
}

private fun String.digitsOnly(): String = filter(Char::isDigit).take(4)
