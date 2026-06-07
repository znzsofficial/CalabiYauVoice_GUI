package com.nekolaska.calabiyau.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object AppShapes {
    val chip: Shape
        @Composable get() = smoothCornerShape(12.dp)

    val sectionIcon: Shape
        @Composable get() = smoothCornerShape(14.dp)

    val compactCard: Shape
        @Composable get() = smoothCornerShape(16.dp)

    val card: Shape
        @Composable get() = smoothCornerShape(24.dp)

    val sheet: Shape
        @Composable get() = smoothCornerShape(28.dp)

    val dialog: Shape
        @Composable get() = smoothCornerShape(28.dp)

    val capsule: Shape
        @Composable get() = smoothCapsuleShape()
}
