package com.nekolaska.calabiyau.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object AppShapes {
    val chipRadius = 12.dp
    val sectionIconRadius = 14.dp
    val compactCardRadius = 16.dp
    val cardRadius = 24.dp
    val sheetRadius = 28.dp
    val dialogRadius = 28.dp

    val chip: Shape
        @Composable get() = smoothCornerShape(chipRadius)

    val sectionIcon: Shape
        @Composable get() = smoothCornerShape(sectionIconRadius)

    val compactCard: Shape
        @Composable get() = smoothCornerShape(compactCardRadius)

    val card: Shape
        @Composable get() = smoothCornerShape(cardRadius)

    val sheet: Shape
        @Composable get() = smoothCornerShape(sheetRadius)

    val dialog: Shape
        @Composable get() = smoothCornerShape(dialogRadius)

    val capsule: Shape
        @Composable get() = smoothCapsuleShape()
}
