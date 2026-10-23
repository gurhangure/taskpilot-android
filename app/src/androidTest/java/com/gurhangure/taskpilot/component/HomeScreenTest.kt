package com.gurhangure.taskpilot.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.gurhangure.taskpilot.feature.home.HomeScreen
import com.gurhangure.taskpilot.feature.home.HomeUiState
import com.gurhangure.taskpilot.ui.theme.TaskPilotTheme
import org.junit.Rule
import org.junit.Test

/** Exercises the standalone screen independently of the production activity. */
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /** Keeps the introduction and connection guidance reachable at twice normal text size. */
    @Test
    fun placeholderRemainsReadableAtLargeFontScale() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                TaskPilotTheme { HomeScreen(HomeUiState()) }
            }
        }
        compose.onNodeWithText("TaskPilot").assertIsDisplayed()
        compose.onNodeWithText("Your tasks, in one place").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No backend connection is needed to open this preview.")
            .performScrollTo().assertIsDisplayed()
    }
}
