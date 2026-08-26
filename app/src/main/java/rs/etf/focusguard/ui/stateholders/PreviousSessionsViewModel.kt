package rs.etf.focusguard.ui.stateholders

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import rs.etf.focusguard.data.SessionDetail
import rs.etf.focusguard.data.SessionRepository
import rs.etf.focusguard.data.TopicSummary
import rs.etf.focusguard.data.room.SessionWithPauses
import javax.inject.Inject

/** The finished sessions on screen and the averages that describe them, always in step. */
data class HistoryContent(
    val sessions: List<SessionWithPauses>,
    val summary: TopicSummary?,
)

/** Which detail modal is showing, and how the list is currently narrowed. */
@Parcelize
data class PreviousSessionsUiState(
    val pauseLogSessionId: Long? = null,
    val analysisSessionId: Long? = null,
    val categoryFilter: String? = null,
    val topicFilter: String? = null,
) : Parcelable

@HiltViewModel
class PreviousSessionsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    companion object {
        private const val UI_STATE_KEY = "uiState"
    }

    val uiState = savedStateHandle.getStateFlow(UI_STATE_KEY, PreviousSessionsUiState())

    private val filters = uiState
        .map { it.categoryFilter to it.topicFilter }
        .distinctUntilChanged()

    /**
     * The list and its averages as one value, deliberately.
     *
     * As two separate flows the averages arrived a frame after the list, and because
     * LazyColumn keeps its keyed first item anchored while scrolling, the summary was
     * prepended *above* the viewport and stayed invisible until the user scrolled up. Emitting
     * both together means the list is built with the summary already in it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val content: Flow<HistoryContent> = filters.flatMapLatest { (category, topic) ->
        sessionRepository.completedSessionsFiltered(category, topic).map { sessions ->
            HistoryContent(
                sessions = sessions,
                summary = sessionRepository.summarise(category, topic).takeIf { !it.isEmpty },
            )
        }
    }

    val categories = sessionRepository.historyCategories

    @OptIn(ExperimentalCoroutinesApi::class)
    val topics: Flow<List<String>> = uiState
        .map { it.categoryFilter }
        .distinctUntilChanged()
        .flatMapLatest { category -> sessionRepository.historyTopics(category) }

    fun showPauseLog(sessionId: Long) = update { it.copy(pauseLogSessionId = sessionId) }

    fun showAnalysis(sessionId: Long) {
        update { it.copy(analysisSessionId = sessionId) }
        loadDetail(sessionId)
    }

    fun dismissDialogs() {
        update { it.copy(pauseLogSessionId = null, analysisSessionId = null) }
        _openDetail.value = null
    }

    /**
     * The full history of the session currently open, loaded when it is opened.
     *
     * Kept out of the list itself: a session holds hundreds of sensor samples, and reading
     * them for every card on screen in order to draw three of them would be waste.
     */
    private val _openDetail = MutableStateFlow<SessionDetail?>(null)
    val openDetail = _openDetail.asStateFlow()

    private fun loadDetail(sessionId: Long) {
        _openDetail.value = null
        viewModelScope.launch {
            val detail = sessionRepository.getSessionDetail(sessionId)
            // Ignore a load that finished after the user moved on to another session.
            if (uiState.value.analysisSessionId == sessionId) _openDetail.value = detail
        }
    }

    /**
     * Selecting a category clears the topic, because a topic only means anything inside its
     * own category — keeping "Math" selected after switching to "Yoga" would show nothing.
     */
    fun onCategoryFilter(category: String?) = update {
        if (it.categoryFilter.equals(category, ignoreCase = true)) {
            it.copy(categoryFilter = null, topicFilter = null)
        } else {
            it.copy(categoryFilter = category, topicFilter = null)
        }
    }

    fun onTopicFilter(topic: String?) = update {
        if (it.topicFilter.equals(topic, ignoreCase = true)) {
            it.copy(topicFilter = null)
        } else {
            it.copy(topicFilter = topic)
        }
    }

    private fun update(transform: (PreviousSessionsUiState) -> PreviousSessionsUiState) {
        savedStateHandle[UI_STATE_KEY] = transform(uiState.value)
    }
}
