package com.gurhangure.taskpilot.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.gurhangure.taskpilot.MainActivity
import org.junit.Rule
import org.junit.Test

/** Exercises real activity launch and recreation without backend configuration. */
class AppStartupTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** Keeps the initial experience available after activity recreation. */
    @Test
    fun launchesAndRecreatesWithoutBackend() {
        compose.onNodeWithText("TaskPilot").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("TaskPilot").assertIsDisplayed()
        compose.onNodeWithText("Your tasks, in one place").assertIsDisplayed()
        compose.onNodeWithText("No backend connection is needed to open this preview.")
            .performScrollTo().assertIsDisplayed()
    }
}
