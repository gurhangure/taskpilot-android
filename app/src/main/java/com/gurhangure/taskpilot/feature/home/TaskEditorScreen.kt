package com.gurhangure.taskpilot.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gurhangure.taskpilot.R
import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.ui.components.apiErrorMessage
import com.gurhangure.taskpilot.ui.theme.TaskPilotSpacing

/** Shares accessible, scrollable create/edit fields without performing persistence or network work. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(
    draft: TaskDraft,
    busy: Boolean,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val fields = (draft.error as? ApiError.Validation)?.fields.orEmpty()
    Scaffold { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState())
                .padding(TaskPilotSpacing.ExtraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Large),
        ) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Large),
            ) {
                Text(stringResource(if (draft.original == null) R.string.task_create else R.string.task_edit),
                    style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                draft.original?.let {
                    Text(stringResource(if (it.isCompleted) R.string.tasks_completed else R.string.tasks_active))
                }
                OutlinedTextField(
                    value = draft.title, onValueChange = onTitleChanged, enabled = !busy,
                    label = { Text(stringResource(R.string.task_title)) }, singleLine = true,
                    isError = fields.keys.any { it.equals("Title", ignoreCase = true) },
                    supportingText = { Text(fieldMessage(fields, "Title") ?: stringResource(R.string.task_title_help)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.description, onValueChange = onDescriptionChanged, enabled = !busy,
                    label = { Text(stringResource(R.string.task_description)) }, minLines = 3,
                    isError = fields.keys.any { it.equals("Description", ignoreCase = true) },
                    supportingText = { Text(fieldMessage(fields, "Description") ?: stringResource(R.string.task_description_help)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                draft.error?.let { MutationError(it) }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.task_saving))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Small),
                    verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Small),
                ) {
                    Button(onClick = { focus.clearFocus(); onSave() }, enabled = !busy) { Text(stringResource(R.string.task_save)) }
                    OutlinedButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.task_cancel)) }
                    if (draft.original != null) {
                        OutlinedButton(onClick = onDelete, enabled = !busy) { Text(stringResource(R.string.task_delete)) }
                    }
                }
            }
        }
    }
}

/** Displays field errors supplied by local validation or ASP.NET ValidationProblemDetails. */
private fun fieldMessage(fields: Map<String, List<String>>, name: String): String? =
    fields.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.joinToString("\n")

/** Explains mutation failures without incorrectly treating a missing task as an endpoint problem. */
@Composable
internal fun MutationError(error: ApiError) {
    Text(
        when (error) {
            ApiError.NotFound -> stringResource(R.string.task_missing)
            is ApiError.Validation -> stringResource(R.string.task_validation)
            else -> apiErrorMessage(error)
        },
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
