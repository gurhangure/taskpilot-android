package com.gurhangure.taskpilot.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.feature.connection.ConnectionFeedback
import com.gurhangure.taskpilot.feature.connection.ConnectionScreen
import com.gurhangure.taskpilot.feature.connection.ConnectionUiState
import com.gurhangure.taskpilot.ui.theme.TaskPilotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercises connection form states and actions without using storage or network services. */
class ConnectionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /** Fresh settings explain emulator scope and keep submission disabled for an empty URL. */
    @Test
    fun displaysDisconnectedFirstLaunchForm() {
        render(ConnectionUiState(restoring = false))
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithText("Save & Connect").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Disconnected — not verified").performScrollTo().assertIsDisplayed()
    }

    /** HTTP requests require acknowledging the displayed risk before Test or Save becomes enabled. */
    @Test
    fun requiresHttpAcknowledgementAndForwardsTestAction() {
        val state = mutableStateOf(ConnectionUiState(url = "http://10.0.2.2:5087/", restoring = false))
        var tests = 0
        compose.setContent {
            TaskPilotTheme {
                ConnectionScreen(state.value, {}, { state.value = state.value.copy(httpAcknowledged = it) }, {}, {}, { tests++ }, {})
            }
        }
        compose.onNodeWithText("Test Connection").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("I understand the local HTTP risk").performScrollTo().performClick()
        compose.onNodeWithText("Test Connection").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, tests) }
    }

    /** Connecting feedback is visible and prevents duplicate submissions. */
    @Test
    fun displaysConnectingAndDisablesActions() {
        render(ConnectionUiState(url = "https://server.test/", restoring = false, feedback = ConnectionFeedback.Connecting))
        compose.onNodeWithText("Save & Connect").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Test Connection").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Connecting…").performScrollTo().assertIsDisplayed()
    }

    /** Successful verification shows a connected state and the separately selected server. */
    @Test
    fun displaysConnectedAndSelectedServer() {
        render(ConnectionUiState(url = "https://server.test/", savedUrl = "https://server.test/", restoring = false, feedback = ConnectionFeedback.Connected))
        compose.onNodeWithText("Connected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Selected server: https://server.test/").performScrollTo().assertIsDisplayed()
    }

    /** Connection failures provide actionable guidance while allowing another attempt. */
    @Test
    fun displaysTimeoutAndAllowsRetry() {
        render(ConnectionUiState(url = "https://server.test/", restoring = false, feedback = ConnectionFeedback.Error(ApiError.Timeout)))
        compose.onNodeWithText("The API took too long to respond. Check your connection and retry.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Test Connection").performScrollTo().assertIsEnabled()
    }

    /** Editing, saving, and returning are forwarded through the declared screen actions. */
    @Test
    fun forwardsUrlSaveAndBackActions() {
        val state = mutableStateOf(ConnectionUiState(restoring = false))
        var saves = 0
        var backs = 0
        compose.setContent {
            TaskPilotTheme {
                ConnectionScreen(state.value, { state.value = state.value.copy(url = it) }, {}, {}, { saves++ }, {}, { backs++ })
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("https://edited.test/")
        compose.onNodeWithText("Save & Connect").performScrollTo().performClick()
        compose.onNodeWithText("Back to Task List").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("https://edited.test/", state.value.url)
            assertEquals(1, saves)
            assertEquals(1, backs)
        }
    }

    /** The emulator suggestion is conditional guidance and does not select a URL on first display. */
    @Test
    fun emulatorSuggestionRequiresAnExplicitUserAction() {
        var suggestions = 0
        val state = ConnectionUiState(restoring = false)
        compose.setContent {
            TaskPilotTheme { ConnectionScreen(state, {}, {}, { suggestions++ }, {}, {}, {}) }
        }
        compose.runOnIdle { assertEquals(0, suggestions); assertEquals("", state.url) }
        compose.onNodeWithText("Running TaskPilot API locally?\n\nIf you are using the Android Emulator and running the API on port 5087, try http://10.0.2.2:5087/.\n\nOtherwise, enter your own server URL.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Use emulator address").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, suggestions) }
    }

    /** Supplies a fixed snapshot for presentation-only checks. */
    private fun render(state: ConnectionUiState) {
        compose.setContent { TaskPilotTheme { ConnectionScreen(state, {}, {}, {}, {}, {}, {}) } }
    }
}
