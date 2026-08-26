package rs.etf.focusguard.ui.stateholders

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.parcelize.Parcelize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import rs.etf.focusguard.data.SessionRepository
import rs.etf.focusguard.alarms.SessionAlarmScheduler
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.data.room.SessionStatus
import rs.etf.focusguard.util.PRESET_CATEGORIES
import rs.etf.focusguard.util.atTimeToInstant
import rs.etf.focusguard.util.normaliseLabel
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
    val category: String? = null,
    val topic: String = "",
    val isAddingCategory: Boolean = false,
    val customCategory: String = "",
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

    /**
     * The category to store: whatever was typed if the user is adding their own, otherwise
     * the selected chip. Blank counts as not set, so an abandoned custom field cannot store
     * an empty category that would then reappear as a suggestion.
     */
    val effectiveCategory: String?
        get() = if (isAddingCategory) normaliseLabel(customCategory) else normaliseLabel(category)

    val effectiveTopic: String? get() = normaliseLabel(topic)
}

@HiltViewModel
class NewSessionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val alarmScheduler: SessionAlarmScheduler,
) : ViewModel() {

    companion object {
        private const val UI_STATE_KEY = "uiState"
    }

    val uiState = savedStateHandle.getStateFlow(UI_STATE_KEY, NewSessionUiState())

    val scheduledSessions = sessionRepository.scheduledSessions

    /** Preset categories plus any the user has invented before, without duplicates. */
    val categories: Flow<List<String>> = sessionRepository.usedCategories.map { used ->
        val known = PRESET_CATEGORIES.toMutableList()
        used.forEach { category ->
            if (known.none { it.equals(category, ignoreCase = true) }) known += category
        }
        known
    }

    /**
     * Topics already used in the chosen category, offered as one-tap suggestions.
     *
     * Driven by the selected category so the suggestions stay relevant: the topics under
     * "Studying" are not the ones under "Yoga".
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val topicSuggestions: Flow<List<String>> = uiState
        .map { it.effectiveCategory }
        .distinctUntilChanged()
        .flatMapLatest { category -> sessionRepository.usedTopics(category) }

    private fun update(transform: (NewSessionUiState) -> NewSessionUiState) {
        savedStateHandle[UI_STATE_KEY] = transform(uiState.value)
    }

    fun onNameChange(value: String) = update { it.copy(name = value) }

    /** Tapping the selected category again clears it — categorising stays optional. */
    fun onCategorySelect(value: String) = update {
        if (it.category.equals(value, ignoreCase = true) && !it.isAddingCategory) {
            it.copy(category = null)
        } else {
            it.copy(category = value, isAddingCategory = false)
        }
    }

    fun onAddOwnCategory() = update {
        it.copy(isAddingCategory = !it.isAddingCategory, category = null)
    }

    fun onCustomCategoryChange(value: String) = update { it.copy(customCategory = value) }

    fun onTopicChange(value: String) = update { it.copy(topic = value) }

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
                    category = state.effectiveCategory,
                    topic = state.effectiveTopic,
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

            val scheduled = Session(
                name = name,
                category = state.effectiveCategory,
                topic = state.effectiveTopic,
                goalMinutes = state.goalMinutesOrDefault,
                plannedPauseCount = state.pauseCountOrZero,
                plannedPauseMinutes = state.pauseMinutesOrZero,
                status = SessionStatus.SCHEDULED,
                scheduledAt = start,
            )
            val id = sessionRepository.insertSession(scheduled)

            // Alarms are booked against the stored id, so this must follow the insert.
            alarmScheduler.schedule(scheduled.copy(id = id))

            update { it.copy(isScheduleDialogOpen = false, scheduleError = null, conflictName = null) }
            onScheduled(name)
        }
    }
}

private fun String.digitsOnly(): String = filter(Char::isDigit).take(4)
