package com.gurhangure.taskpilot.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.gurhangure.taskpilot.MainActivity
import org.junit.Rule
import org.junit.Test

/** Exercises real activity launch, retry, and recreation with CI's unconfigured endpoint. */
class AppStartupTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** Startup and recreation preserve an actionable task-list screen without requiring a backend. */
    @Test
    fun launchesAndRecreatesWithoutBackend() {
        compose.onNodeWithText("TaskPilot").assertIsDisplayed()
        assertConfigurationGuidance()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Retry"))
        compose.onNodeWithText("Retry").performClick()
        assertConfigurationGuidance()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("TaskPilot"))
        compose.onNodeWithText("TaskPilot").assertIsDisplayed()
        assertConfigurationGuidance()
    }

    /** Verifies configuration failures stay visible and actionable after screen lifecycle changes. */
    private fun assertConfigurationGuidance() {
        val message = "API connection not configured. Set the backend address in the build configuration, then reopen the app."
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(message))
        compose.onNodeWithText(message).assertIsDisplayed()
    }
}
