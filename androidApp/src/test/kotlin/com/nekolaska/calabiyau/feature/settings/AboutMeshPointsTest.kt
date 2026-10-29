package com.nekolaska.calabiyau.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AboutMeshPointsTest {
    @Test
    fun cornersStayPinnedWhileInteriorPointsDrift() {
        assertTrue(meshPointsStayOrdered(0f, 0f, 0f))
        assertTrue(meshPointsStayOrdered(-0.07f, 0.08f, -0.05f))
        assertTrue(meshPointsStayOrdered(0.09f, -0.06f, 0.11f))
        assertTrue(meshPointsStayOrdered(0.2f, -0.2f, 0.2f))
    }

    @Test
    fun bottomEdgeWashesIntoThePageBackground() {
        val scheme = lightColorScheme(primary = Color(0xFF6750A4), background = Color(0xFFFFFBFE))
        val colors = aboutMeshColors(scheme)

        assertEquals(16, colors.size)
        assertEquals(scheme.background, colors[12])
        assertEquals(scheme.background, colors[15])
        assertTrue(colors[5] != scheme.background)
    }

    @Test
    fun darkSchemeStillAnchorsTheLowerEdge() {
        val scheme = darkColorScheme()
        val colors = aboutMeshColors(scheme)
        assertEquals(scheme.background, colors[12])
        assertEquals(scheme.background, colors[15])
    }
}
