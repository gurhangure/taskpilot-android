package com.gurhangure.taskpilot.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gurhangure.taskpilot.R
import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.ui.components.apiErrorMessage
import com.gurhangure.taskpilot.ui.theme.TaskPilotColors
import com.gurhangure.taskpilot.ui.theme.TaskPilotSpacing
import com.gurhangure.taskpilot.ui.theme.TaskPilotTheme

/** Renders list state and forwards actions without accessing repositories or network configuration. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onFilterSelected: (TaskFilter) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onCreate: () -> Unit = {},
    onEdit: (Task) -> Unit = {},
    onToggle: (Task) -> Unit = {},
    onRequestDelete: (Task) -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onCancelDelete: () -> Unit = {},
    onTitleChanged: (String) -> Unit = {},
    onDescriptionChanged: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onCancelEditor: () -> Unit = {},
    onSearchChanged: (String) -> Unit = {},
) {
    BackHandler(enabled = state.editor != null || state.deleteTask != null) {
        if (state.deleteTask != null) onCancelDelete() else onCancelEditor()
    }
    state.deleteTask?.let { task ->
        DeleteTaskDialog(task, state.isMutating, state.mutationError, onConfirmDelete, onCancelDelete)
    }
    state.editor?.let { draft ->
        TaskEditorScreen(draft, state.isMutating, onTitleChanged, onDescriptionChanged, onSave, onCancelEditor,
            onDelete = { draft.original?.let(onRequestDelete) })
        return
    }
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        floatingActionButton = {
            if (state.endpointAvailable && !state.isMutating) {
                FloatingActionButton(onClick = onCreate) { Text(stringResource(R.string.task_create), Modifier.padding(TaskPilotSpacing.Large)) }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = TaskPilotSpacing.ExtraLarge, end = TaskPilotSpacing.ExtraLarge,
                    top = TaskPilotSpacing.ExtraLarge, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Large),
            ) {
                item(key = "header") { TaskListHeader(state.isLoading || state.isMutating, onRefresh, onOpenSettings) }
                if (state.content != HomeContent.ConnectionRequired) {
                    item(key = "search") { TaskSearchField(state.searchQuery, onSearchChanged) }
                    item(key = "statistics") { TaskSummary(state.statistics) }
                    item(key = "filters") { TaskFilters(state.filter, onFilterSelected) }
                }
                if (state.isLoading) {
                    item(key = "progress") { LoadingTasks(state.isRefreshing) }
                }
                state.refreshError?.let { error ->
                    item(key = "refresh_error") { TaskError(error, onRetry, state.isLoading || state.isMutating, isRefresh = true) }
                }
                state.mutationError?.let { error ->
                    item(key = "mutation_error") { MutationError(error) }
                }
                when (val content = state.content) {
                    HomeContent.Loading -> Unit
                    HomeContent.ConnectionRequired -> item(key = "connection_required") {
                        ConnectionRequired(onOpenSettings)
                    }
                    HomeContent.Empty -> item(key = "empty") {
                        TaskMessage(stringResource(R.string.tasks_empty_title), stringResource(R.string.tasks_empty_body))
                    }
                    is HomeContent.Error -> item(key = "error") { TaskError(content.error, onRetry, state.isLoading || state.isMutating) }
                    is HomeContent.Success -> {
                        val tasks = state.visibleTasks
                        if (tasks.isEmpty()) {
                            item(key = "filter_empty") {
                                TaskMessage(
                                    stringResource(R.string.tasks_filter_empty_title),
                                    stringResource(when {
                                        state.searchQuery.isNotBlank() || state.filter == TaskFilter.All -> R.string.tasks_search_no_matches
                                        state.filter == TaskFilter.Active -> R.string.tasks_no_active
                                        else -> R.string.tasks_no_completed
                                    }),
                                )
                            }
                        }
                        items(tasks, key = { it.id.toString() }) { task ->
                            TaskCard(task, state.endpointAvailable && !state.isMutating,
                                state.isMutating && state.pendingTaskId == task.id, onEdit, onToggle, onRequestDelete)
                        }
                    }
                }
            }
        }
    }
}

/** Provides labelled local search and clearing; the IME action dismisses the keyboard without fetching. */
@Composable
private fun TaskSearchField(query: String, onChanged: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query, onValueChange = onChanged,
        label = { Text(stringResource(R.string.tasks_search)) },
        placeholder = { Text(stringResource(R.string.tasks_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        trailingIcon = {
            if (query.isNotEmpty()) {
                val clearLabel = stringResource(R.string.tasks_clear_search)
                TextButton(onClick = { onChanged("") }, modifier = Modifier.semantics { contentDescription = clearLabel }) {
                    Text(stringResource(R.string.tasks_clear))
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Shows product identity and a manual refresh action that disables during an active request. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskListHeader(isLoading: Boolean, onRefresh: () -> Unit, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Small)) {
        Text(
            stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Small),
        ) {
            Text(
                stringResource(R.string.tasks_heading), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = TaskPilotSpacing.Medium).semantics { heading() },
            )
            OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.connection_settings)) }
            OutlinedButton(onClick = onRefresh, enabled = !isLoading) { Text(stringResource(R.string.tasks_refresh)) }
        }
    }
}

/** Presents counts from the whole list, allowing metrics to wrap on narrow or large-font layouts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskSummary(statistics: TaskStatistics?) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Small),
    ) {
        TaskMetric(stringResource(R.string.tasks_total), statistics?.total)
        TaskMetric(stringResource(R.string.tasks_active), statistics?.active)
        TaskMetric(stringResource(R.string.tasks_completed), statistics?.completed)
    }
}

/** Keeps a count and its label together as a single accessibility announcement. */
@Composable
private fun TaskMetric(label: String, count: Int?) {
    val value = count?.toString() ?: stringResource(R.string.tasks_unknown_count)
    val description = stringResource(R.string.tasks_metric_description, label, value)
    Surface(
        modifier = Modifier.widthIn(min = 100.dp).semantics(mergeDescendants = true) {
            contentDescription = description
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(TaskPilotSpacing.Large), verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.ExtraSmall)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Uses wrapping Material chips with native selected semantics for local filters. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskFilters(selected: TaskFilter, onSelected: (TaskFilter) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.ExtraSmall),
    ) {
        TaskFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = {
                    Text(stringResource(when (filter) {
                        TaskFilter.All -> R.string.tasks_all
                        TaskFilter.Active -> R.string.tasks_active
                        TaskFilter.Completed -> R.string.tasks_completed
                    }))
                },
            )
        }
    }
}

/** Announces initial loading or refresh while keeping progress visible in the scrollable content. */
@Composable
private fun LoadingTasks(isRefreshing: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Small)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(if (isRefreshing) R.string.tasks_refreshing else R.string.tasks_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Presents server-confirmed task status with accessible completion, edit, and delete actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(task: Task, enabled: Boolean, busy: Boolean, onEdit: (Task) -> Unit, onToggle: (Task) -> Unit, onDelete: (Task) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            Modifier.padding(TaskPilotSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Small),
        ) {
            Text(
                task.title, style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            task.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    description, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(if (task.isCompleted) R.string.tasks_completed else R.string.tasks_active),
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.isCompleted) TaskPilotColors.Success else MaterialTheme.colorScheme.primary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Small)) {
                val toggleLabel = stringResource(if (task.isCompleted) R.string.task_reopen_label else R.string.task_complete_label, task.title)
                Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle(task) }, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = toggleLabel })
                val editLabel = stringResource(R.string.task_edit_label, task.title)
                OutlinedButton(onClick = { onEdit(task) }, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = editLabel }) { Text(stringResource(R.string.task_edit)) }
                val deleteLabel = stringResource(R.string.task_delete_label, task.title)
                OutlinedButton(onClick = { onDelete(task) }, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = deleteLabel }) { Text(stringResource(R.string.task_delete)) }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.task_updating))
            }
        }
    }
}

/** Requires explicit confirmation and retains failure feedback without closing the dialog. */
@Composable
private fun DeleteTaskDialog(task: Task, busy: Boolean, error: ApiError?, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text(stringResource(R.string.task_delete_heading)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Small),
            ) {
                Text(stringResource(R.string.task_delete_body, task.title))
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.task_deleting))
                }
                error?.let { MutationError(it) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy) { Text(stringResource(R.string.task_delete)) } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.task_cancel)) } },
    )
}

/** Explains a successfully loaded empty collection or a filter with no matches. */
@Composable
private fun TaskMessage(title: String, body: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(TaskPilotSpacing.ExtraLarge), verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Medium)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Shows actionable API guidance and retry without displaying raw server diagnostics. */
@Composable
private fun TaskError(error: ApiError, onRetry: () -> Unit, isLoading: Boolean, isRefresh: Boolean = false) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(TaskPilotSpacing.ExtraLarge), verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Medium)) {
            Text(
                stringResource(if (isRefresh) R.string.tasks_refresh_error else R.string.tasks_error_title),
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { heading() },
            )
            Text(apiErrorMessage(error), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRetry, enabled = !isLoading) { Text(stringResource(R.string.tasks_retry)) }
        }
    }
}

/** Previews the successfully loaded empty state with the shared dark theme. */
@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    TaskPilotTheme { HomeScreen(HomeUiState(HomeContent.Empty), {}, {}, {}) }
}

/** Invites first-time users to choose a server without presenting a failed network request. */
@Composable
private fun ConnectionRequired(onConnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TaskPilotSpacing.Large)) {
        TaskMessage(stringResource(R.string.connection_required_title), stringResource(R.string.connection_required_body))
        Button(onClick = onConnect) { Text(stringResource(R.string.connection_open)) }
    }
}
