package rs.etf.focusguard.ui.elements.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import rs.etf.focusguard.R
import rs.etf.focusguard.data.room.Session
import rs.etf.focusguard.ui.elements.composables.DialogButton
import rs.etf.focusguard.ui.elements.composables.DialogButtonStyle
import rs.etf.focusguard.ui.elements.composables.FocusGuardDialog
import rs.etf.focusguard.ui.elements.composables.FormField
import rs.etf.focusguard.ui.elements.composables.FormLabel
import rs.etf.focusguard.ui.elements.theme.Border
import rs.etf.focusguard.ui.elements.theme.Card
import rs.etf.focusguard.ui.elements.theme.Red
import rs.etf.focusguard.ui.elements.theme.TextPrimary
import rs.etf.focusguard.ui.elements.theme.TextSecondary
import rs.etf.focusguard.ui.stateholders.NewSessionUiState
import rs.etf.focusguard.util.formatDate
import rs.etf.focusguard.util.formatTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSessionDialog(
    uiState: NewSessionUiState,
    existingSessions: List<Session>,
    onNameChange: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    FocusGuardDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.schedule_dialog_title),
        actions = {
            DialogButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                style = DialogButtonStyle.SECONDARY,
            )
            DialogButton(
                text = stringResource(R.string.action_schedule),
                onClick = onConfirm,
                style = DialogButtonStyle.PRIMARY,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            FormField(
                label = stringResource(R.string.field_session_name),
                value = uiState.scheduleName,
                onValueChange = onNameChange,
                placeholder = stringResource(R.string.field_schedule_name_hint),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(
                    label = stringResource(R.string.field_date),
                    value = formatDate(uiState.scheduleInstant),
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                )
                PickerField(
                    label = stringResource(R.string.field_time),
                    value = formatTime(uiState.scheduleInstant),
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                )
            }

            uiState.conflictName?.let { name ->
                Text(
                    text = stringResource(R.string.schedule_conflict, name),
                    color = Red,
                    fontSize = 12.sp,
                )
            }
            uiState.scheduleError?.let { message ->
                Text(text = message, color = Red, fontSize = 12.sp)
            }

            if (existingSessions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.schedule_existing),
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
                existingSessions.forEach { session ->
                    ExistingSessionRow(session)
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = uiState.scheduleDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        // The picker reports UTC midnight; convert back to a calendar day.
                        onDateChange(
                            java.time.Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        )
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.scheduleHour,
            initialMinute = uiState.scheduleMinute,
            is24Hour = true,
        )

        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = rs.etf.focusguard.ui.elements.theme.Surface,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        TextButton(onClick = {
                            onTimeChange(timePickerState.hour, timePickerState.minute)
                            showTimePicker = false
                        }) { Text(stringResource(R.string.action_ok)) }
                    }
                }
            }
        }
    }
}

/** Read-only field that opens a picker, standing in for the prototype's native date input. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FormLabel(label)
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.small,
            color = Card,
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = value,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun ExistingSessionRow(session: Session, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        color = Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = session.name,
                fontSize = 12.sp,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            session.scheduledAt?.let { at ->
                Text(
                    text = "${formatDate(at)} ${formatTime(at)}",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
        }
    }
}
