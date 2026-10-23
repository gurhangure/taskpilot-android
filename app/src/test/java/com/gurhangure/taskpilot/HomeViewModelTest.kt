package com.gurhangure.taskpilot

import com.gurhangure.taskpilot.feature.home.HomeViewModel
import org.junit.Assert.assertFalse
import org.junit.Test

/** Checks startup state independently of Android rendering. */
class HomeViewModelTest {
    /** Does not claim tasks were loaded before the task feature exists. */
    @Test
    fun startsWithAnUnavailableTaskList() {
        assertFalse(HomeViewModel().uiState.value.isTaskListAvailable)
    }
}
