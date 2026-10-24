package com.gurhangure.taskpilot.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gurhangure.taskpilot.R
import com.gurhangure.taskpilot.ui.components.apiErrorMessage
import com.gurhangure.taskpilot.ui.theme.TaskPilotSpacing

/** Renders editable connection settings and forwards actions without accessing storage or transport. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionScreen(
    state: ConnectionUiState,
    onUrlChanged: (String) -> Unit,
    onHttpAcknowledged: (Boolean) -> Unit,
    onEmulatorSuggestion: () -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onBack: () -> Unit,
) {
    val focus = LocalFocusManager.current
    Scaffold { insets ->
        Box(Modifier.fillMaxSize().padding(insets).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()
                    .verticalScroll(rememberScrollState()).padding(TaskPilotSpacing.ExtraLarge),
                verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Large),
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.connection_back)) }
                Text(
                    stringResource(R.string.connection_settings), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.connection_guidance), style = MaterialTheme.typography.bodyLarge)
                if (state.restoring) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.connection_restoring))
                }
                OutlinedTextField(
                    value = state.url, onValueChange = onUrlChanged,
                    label = { Text(stringResource(R.string.connection_url)) },
                    singleLine = true, enabled = !state.restoring,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    isError = state.feedback is ConnectionFeedback.Error,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.connection_emulator_guidance), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onEmulatorSuggestion, enabled = !state.restoring) {
                    Text(stringResource(R.string.connection_emulator_action))
                }
                if (state.usesHttp) {
                    Text(
                        stringResource(R.string.connection_http_warning), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    val acknowledgement = stringResource(R.string.connection_http_acknowledgement)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.httpAcknowledged, onCheckedChange = onHttpAcknowledged,
                            modifier = Modifier.semantics { contentDescription = acknowledgement },
                        )
                        Text(acknowledgement, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Small),
                    verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Small),
                ) {
                    Button(onClick = onSave, enabled = state.canSubmit) { Text(stringResource(R.string.connection_save)) }
                    OutlinedButton(onClick = onTest, enabled = state.canSubmit) { Text(stringResource(R.string.connection_test)) }
                }
                ConnectionStatus(state)
                state.savedUrl?.let { url ->
                    Text(stringResource(R.string.connection_saved, url), style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.connection_saved_note), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Announces verification progress and results, including actionable typed errors. */
@Composable
private fun ConnectionStatus(state: ConnectionUiState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Small),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val label = when (state.feedback) {
            ConnectionFeedback.Disconnected -> R.string.connection_disconnected
            ConnectionFeedback.Connecting -> R.string.connection_connecting
            ConnectionFeedback.Connected -> R.string.connection_connected
            is ConnectionFeedback.Error -> R.string.connection_error
        }
        Text(stringResource(label), style = MaterialTheme.typography.titleLarge)
        (state.feedback as? ConnectionFeedback.Error)?.let { Text(apiErrorMessage(it.error)) }
    }
}
