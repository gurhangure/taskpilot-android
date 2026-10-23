package com.gurhangure.taskpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
        setContent {
            TaskPilotTheme {
                val homeViewModel: HomeViewModel = viewModel()
                val state by homeViewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(state)
            }
        }
    }
}
