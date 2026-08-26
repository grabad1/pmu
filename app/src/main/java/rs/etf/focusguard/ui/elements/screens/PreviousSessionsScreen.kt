package rs.etf.focusguard.ui.elements.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.etf.focusguard.R
import rs.etf.focusguard.data.TopicSummary
import rs.etf.focusguard.data.room.SessionWithPauses
import rs.etf.focusguard.ui.elements.composables.ChipRow
import rs.etf.focusguard.ui.elements.composables.DetailItem
import rs.etf.focusguard.ui.elements.composables.DetailRow
import rs.etf.focusguard.ui.elements.composables.EmptyState
import rs.etf.focusguard.ui.elements.composables.FocusCard
import rs.etf.focusguard.ui.elements.composables.ScreenHeader
import rs.etf.focusguard.ui.elements.composables.SelectableChip
import rs.etf.focusguard.ui.elements.theme.Accent
import rs.etf.focusguard.ui.elements.theme.Border
import rs.etf.focusguard.ui.elements.theme.TextPrimary
import rs.etf.focusguard.ui.elements.theme.TextSecondary
import rs.etf.focusguard.ui.elements.theme.TextTertiary
import rs.etf.focusguard.ui.stateholders.HistoryContent
import rs.etf.focusguard.ui.stateholders.PreviousSessionsViewModel
import rs.etf.focusguard.util.actualTimeColor
import rs.etf.focusguard.util.formatDate
import rs.etf.focusguard.util.formatDuration
import rs.etf.focusguard.util.formatTime
import rs.etf.focusguard.util.scoreColor
import kotlin.math.roundToInt

@Composable
fun PreviousSessionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PreviousSessionsViewModel = hiltViewModel(),
) {
    val content by viewModel.content
        .collectAsStateWithLifecycle(initialValue = HistoryContent(emptyList(), null))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val topics by viewModel.topics.collectAsStateWithLifecycle(initialValue = emptyList())
    val openDetail by viewModel.openDetail.collectAsStateWithLifecycle()
    val sessions = content.sessions

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.previous_sessions_title), onBack = onBack)

        if (categories.isNotEmpty()) {
            HistoryFilters(
                categories = categories,
                topics = topics,
                selectedCategory = uiState.categoryFilter,
                selectedTopic = uiState.topicFilter,
                onCategoryClick = viewModel::onCategoryFilter,
                onTopicClick = viewModel::onTopicFilter,
            )
        }

        if (sessions.isEmpty()) {
            EmptyState(
                icon = "\uD83D\uDCCA",
                text = if (uiState.categoryFilter == null && uiState.topicFilter == null) {
                    stringResource(R.string.previous_empty)
                } else {
                    stringResource(R.string.previous_empty_filtered)
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content.summary?.let { stats ->
                    item(key = "summary") { TopicSummaryCard(summary = stats) }
                }
                items(items = sessions, key = { it.session.id }) { item ->
                    PreviousSessionCard(
                        item = item,
                        onCardClick = { viewModel.showAnalysis(item.session.id) },
                        onPausesClick = { viewModel.showPauseLog(item.session.id) },
                    )
                }
            }
        }
    }

    uiState.pauseLogSessionId
        ?.let { id -> sessions.firstOrNull { it.session.id == id } }
        ?.let { item -> PauseLogDialog(item = item, onDismiss = viewModel::dismissDialogs) }

    uiState.analysisSessionId
        ?.let { id -> sessions.firstOrNull { it.session.id == id } }
        ?.let { item ->
            AnalysisDialog(
                item = item,
                detail = openDetail,
                onDismiss = viewModel::dismissDialogs,
            )
        }
}

/** Category chips, plus topic chips once a category is chosen. */
@Composable
private fun HistoryFilters(
    categories: List<String>,
    topics: List<String>,
    selectedCategory: String?,
    selectedTopic: String?,
    onCategoryClick: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChipRow {
            categories.forEach { category ->
                SelectableChip(
                    text = category,
                    selected = selectedCategory.equals(category, ignoreCase = true),
                    onClick = { onCategoryClick(category) },
                )
            }
        }

        // Topics only make sense once a category narrows them, and only if there are any.
        if (selectedCategory != null && topics.isNotEmpty()) {
            ChipRow {
                topics.forEach { topic ->
                    SelectableChip(
                        text = topic,
                        selected = selectedTopic.equals(topic, ignoreCase = true),
                        onClick = { onTopicClick(topic) },
                    )
                }
            }
        }
    }
}

/**
 * The averages for whatever is on screen — the answer to "how do my maths sessions go".
 *
 * Percentages are only shown when there is something to say: a run of sessions with no bad
 * light should not display "Bad light 0%" three times over.
 */
@Composable
private fun TopicSummaryCard(
    summary: TopicSummary,
    modifier: Modifier = Modifier,
) {
    FocusCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.summary_session_count,
                        summary.sessionCount,
                        summary.sessionCount,
                    ),
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
            Text(
                text = summary.averageScore?.toString() ?: "—",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = summary.averageScore?.let(::scoreColor) ?: TextSecondary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DetailRow(
                left = {
                    DetailItem(
                        label = stringResource(R.string.summary_avg_focus),
                        value = formatDuration(summary.averageFocusedSeconds),
                    )
                },
                right = {
                    DetailItem(
                        label = stringResource(R.string.summary_avg_pauses),
                        value = stringResource(
                            R.string.summary_pause_split,
                            summary.averagePlannedPauses,
                            summary.averageUnplannedPauses,
                        ),
                    )
                },
            )

            val conditions = listOfNotNull(
                summary.darkFraction.takeIf { it > 0.005 }
                    ?.let { stringResource(R.string.summary_dark, percent(it)) },
                summary.loudFraction.takeIf { it > 0.005 }
                    ?.let { stringResource(R.string.summary_loud, percent(it)) },
                summary.movementFraction.takeIf { it > 0.005 }
                    ?.let { stringResource(R.string.summary_moving, percent(it)) },
            )

            if (conditions.isNotEmpty()) {
                DetailItem(
                    label = stringResource(R.string.summary_conditions),
                    value = conditions.joinToString("   ·   "),
                )
            }
        }
    }
}

private fun percent(fraction: Double): Int = (fraction * 100).roundToInt()

@Composable
private fun PreviousSessionCard(
    item: SessionWithPauses,
    onCardClick: () -> Unit,
    onPausesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = item.session
    val goalSeconds = session.goalMinutes * 60
    val isOvertime = session.focusedSeconds > goalSeconds
    val actualTime = formatDuration(session.focusedSeconds)

    FocusCard(modifier = modifier, onClick = onCardClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                session.endedAt?.let { at ->
                    Text(
                        text = "${formatDate(at)} · ${formatTime(at)}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                }
                // Only sessions that were categorised say so; older ones stay uncluttered.
                listOfNotNull(session.category, session.topic)
                    .takeIf { it.isNotEmpty() }
                    ?.let { labels ->
                        Text(
                            text = labels.joinToString(" · "),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Accent,
                        )
                    }
            }
            Text(
                text = session.focusScore?.toString() ?: "—",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = session.focusScore?.let(::scoreColor) ?: TextSecondary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DetailRow(
                left = {
                    DetailItem(
                        label = stringResource(R.string.detail_goal_time),
                        value = stringResource(R.string.value_minutes, session.goalMinutes),
                    )
                },
                right = {
                    DetailItem(
                        label = stringResource(R.string.detail_actual_time),
                        value = if (isOvertime) {
                            stringResource(R.string.value_duration_over, actualTime)
                        } else {
                            actualTime
                        },
                        valueColor = actualTimeColor(session.focusedSeconds, goalSeconds),
                    )
                },
            )

            DetailItem(
                label = stringResource(R.string.detail_pauses),
                value = pluralStringResource(
                    R.plurals.value_pauses_tap,
                    item.pauses.size,
                    item.pauses.size,
                ),
                valueColor = Accent,
                modifier = Modifier.clickable(onClick = onPausesClick),
            )

            // Only shown when it happened: a clean session should not carry a row of zeroes.
            if (session.awaySeconds > 0) {
                DetailItem(
                    label = stringResource(R.string.detail_time_away),
                    value = formatDuration(session.awaySeconds),
                    valueColor = MaterialTheme.colorScheme.error,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp),
            color = Border,
        )
        Text(
            text = stringResource(R.string.previous_card_footer),
            fontSize = 11.sp,
            color = TextTertiary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
