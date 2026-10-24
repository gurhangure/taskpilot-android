package com.gurhangure.taskpilot.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.gurhangure.taskpilot.MainActivity
import com.gurhangure.taskpilot.TaskPilotApplication
import org.junit.Rule
import org.junit.Test

/** Exercises real first-launch navigation, validation, and recreation with no external backend. */
class AppStartupTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** A fresh installation can configure in-app and return safely after invalid input or recreation. */
    @Test
    fun launchesAndRecreatesWithoutBackend() {
        compose.waitUntil(timeoutMillis = 5_000) {
            (compose.activity.application as TaskPilotApplication).connections.initialized.value
        }
        compose.onNodeWithText("TaskPilot").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Connect to Server"))
        compose.onNodeWithText("Connect to Server").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("not a URL")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("Save & Connect").performScrollTo().performClick()
        compose.onNodeWithText("Connection failed").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Connection failed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back to Task List").performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Connect to Server"))
        compose.onNodeWithText("Connect to Server").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("TaskPilot"))
        compose.onNodeWithText("TaskPilot").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Connect to Server"))
        compose.onNodeWithText("Connect to Server").assertIsDisplayed()
    }
}
