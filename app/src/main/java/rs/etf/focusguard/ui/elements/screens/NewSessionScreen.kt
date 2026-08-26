package rs.etf.focusguard.ui.elements.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.etf.focusguard.R
import rs.etf.focusguard.ui.elements.composables.ChipRow
import rs.etf.focusguard.ui.elements.composables.FormField
import rs.etf.focusguard.ui.elements.composables.FormLabel
import rs.etf.focusguard.ui.elements.composables.PrimaryButton
import rs.etf.focusguard.ui.elements.composables.ScreenHeader
import rs.etf.focusguard.ui.elements.composables.SecondaryButton
import rs.etf.focusguard.ui.elements.composables.SelectableChip
import rs.etf.focusguard.ui.elements.theme.Accent
import rs.etf.focusguard.ui.elements.theme.AccentDim
import rs.etf.focusguard.ui.elements.theme.TextSecondary
import rs.etf.focusguard.ui.stateholders.NewSessionViewModel

@Composable
fun NewSessionScreen(
    onBack: () -> Unit,
    onStart: (Long) -> Unit,
    onScheduled: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewSessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scheduled by viewModel.scheduledSessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by viewModel.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val topicSuggestions by viewModel.topicSuggestions
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.new_session_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FormField(
                label = stringResource(R.string.field_session_name),
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                placeholder = stringResource(R.string.field_session_name_hint),
            )

            CategoryPicker(
                categories = categories,
                selected = uiState.category,
                isAddingOwn = uiState.isAddingCategory,
                customValue = uiState.customCategory,
                onSelect = viewModel::onCategorySelect,
                onAddOwn = viewModel::onAddOwnCategory,
                onCustomChange = viewModel::onCustomCategoryChange,
            )

            TopicField(
                value = uiState.topic,
                suggestions = topicSuggestions,
                onValueChange = viewModel::onTopicChange,
            )

            FormField(
                label = stringResource(R.string.field_goal_time),
                value = uiState.goalMinutes,
                onValueChange = viewModel::onGoalMinutesChange,
                numeric = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField(
                    label = stringResource(R.string.field_pauses),
                    value = uiState.pauseCount,
                    onValueChange = viewModel::onPauseCountChange,
                    numeric = true,
                    modifier = Modifier.weight(1f),
                )
                FormField(
                    label = stringResource(R.string.field_pause_duration),
                    value = uiState.pauseMinutes,
                    onValueChange = viewModel::onPauseMinutesChange,
                    numeric = true,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f),
                )
            }

            TimePreview(
                goalMinutes = uiState.goalMinutesOrDefault,
                pauseMinutes = uiState.totalPauseMinutes,
                totalMinutes = uiState.plannedTotalMinutes,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton(
                text = stringResource(R.string.action_schedule),
                onClick = viewModel::openScheduleDialog,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.action_start),
                onClick = { viewModel.startSession(onStart) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (uiState.isScheduleDialogOpen) {
        ScheduleSessionDialog(
            uiState = uiState,
            existingSessions = scheduled,
            onNameChange = viewModel::onScheduleNameChange,
            onDateChange = viewModel::onScheduleDateChange,
            onTimeChange = viewModel::onScheduleTimeChange,
            onConfirm = { viewModel.schedule(onScheduled) },
            onDismiss = viewModel::closeScheduleDialog,
        )
    }
}

/**
 * Category chips: the presets, anything the user has invented before, and a way to invent
 * another. Choosing nothing is allowed — a session without a category is still a session.
 */
@Composable
private fun CategoryPicker(
    categories: List<String>,
    selected: String?,
    isAddingOwn: Boolean,
    customValue: String,
    onSelect: (String) -> Unit,
    onAddOwn: () -> Unit,
    onCustomChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FormLabel(stringResource(R.string.field_category))

        ChipRow {
            categories.forEach { category ->
                SelectableChip(
                    text = category,
                    selected = !isAddingOwn && selected.equals(category, ignoreCase = true),
                    onClick = { onSelect(category) },
                )
            }
            SelectableChip(
                text = stringResource(R.string.category_add_own),
                selected = isAddingOwn,
                onClick = onAddOwn,
            )
        }

        if (isAddingOwn) {
            FormField(
                label = stringResource(R.string.field_custom_category),
                value = customValue,
                onValueChange = onCustomChange,
                placeholder = stringResource(R.string.field_custom_category_hint),
            )
        }
    }
}

/**
 * Free-text topic with one-tap suggestions drawn from what has been used before, so "Math"
 * stays one topic instead of becoming three spellings that never group together.
 */
@Composable
private fun TopicField(
    value: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FormField(
            label = stringResource(R.string.field_topic),
            value = value,
            onValueChange = onValueChange,
            placeholder = stringResource(R.string.field_topic_hint),
        )

        if (suggestions.isNotEmpty()) {
            Text(
                text = stringResource(R.string.topic_suggestions),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            ChipRow {
                suggestions.forEach { suggestion ->
                    SelectableChip(
                        text = suggestion,
                        selected = value.trim().equals(suggestion, ignoreCase = true),
                        onClick = { onValueChange(suggestion) },
                    )
                }
            }
        }
    }
}

/** `.time-preview` — restates the arithmetic so the total is never a surprise. */
@Composable
private fun TimePreview(
    goalMinutes: Int,
    pauseMinutes: Int,
    totalMinutes: Int,
    modifier: Modifier = Modifier,
) {
    val accent = SpanStyle(color = Accent, fontSize = 12.sp)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = AccentDim,
    ) {
        Text(
            text = buildAnnotatedString {
                append("Goal: ")
                withStyle(accent) { append("$goalMinutes min") }
                append("   +   Pauses: ")
                withStyle(accent) { append("$pauseMinutes min") }
                append("   =   ")
                withStyle(accent) { append("$totalMinutes min total") }
            },
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        )
    }
}
