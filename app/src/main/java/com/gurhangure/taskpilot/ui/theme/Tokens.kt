package com.gurhangure.taskpilot.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Spacing on the shared four-point grid, expressed in density-independent units. */
object TaskPilotSpacing {
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val ExtraLarge = 24.dp
    val Section = 32.dp
}

/** Shared radii for controls, cards, panels, and filter chips. */
object TaskPilotCorners {
    val Card = 12.dp
    val Panel = 16.dp
    val Pill = RoundedCornerShape(percent = 50)
}

val TaskPilotShapes = Shapes(
    small = RoundedCornerShape(TaskPilotCorners.Card),
    medium = RoundedCornerShape(TaskPilotCorners.Card),
    large = RoundedCornerShape(TaskPilotCorners.Panel),
)
