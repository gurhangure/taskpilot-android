package com.gurhangure.taskpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gurhangure.taskpilot.data.repository.ConnectionRepository
import com.gurhangure.taskpilot.feature.connection.ConnectionScreen
import com.gurhangure.taskpilot.feature.connection.ConnectionViewModel
import com.gurhangure.taskpilot.feature.home.HomeScreen
import com.gurhangure.taskpilot.feature.home.HomeViewModel
import com.gurhangure.taskpilot.ui.theme.TaskPilotTheme

/** Hosts the single-activity Compose application. */
class MainActivity : ComponentActivity() {
    /** Installs dark system bars and observes home state only while the UI is active. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val connections = (application as TaskPilotApplication).connections
        setContent { TaskPilotTheme { TaskPilotContent(connections) } }
    }
}

/** Coordinates two screens with saveable navigation and native Back handling. */
@Composable
private fun TaskPilotContent(connections: ConnectionRepository) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val home: HomeViewModel = viewModel(factory = HomeViewModel.factory(connections))
    val homeState by home.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    if (settingsOpen) {
        val settings: ConnectionViewModel = viewModel(factory = ConnectionViewModel.factory(connections))
        val settingsState by settings.uiState.collectAsStateWithLifecycle()
        DisposableEffect(settings) { onDispose { settings.cancelPending() } }
        ConnectionScreen(
            state = settingsState,
            onUrlChanged = settings::editUrl,
            onHttpAcknowledged = settings::acknowledgeHttp,
            onEmulatorSuggestion = settings::useEmulatorSuggestion,
            onSave = settings::saveAndConnect,
            onTest = settings::testConnection,
            onBack = { settingsOpen = false },
        )
    } else {
        HomeScreen(
            state = homeState, onRefresh = home::refresh, onRetry = home::retry,
            onFilterSelected = home::selectFilter, onOpenSettings = { settingsOpen = true },
        )
    }
}
