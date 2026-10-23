package com.gurhangure.taskpilot.feature.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Describes the foundation screen without pretending that tasks have been loaded. */
data class HomeUiState(val isTaskListAvailable: Boolean = false)

/** Owns immutable home state; task loading and user actions arrive with the task feature. */
class HomeViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()
}
