package com.nekolaska.calabiyau.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AboutMeshPointsTest {
    @Test
    fun cornersStayPinnedWhileInteriorPointsDrift() {
        // Cartesian product also catches meshes at mismatched phases, not just matching endpoints.
        for (a in listOf(-0.07f, 0f, 0.09f, -10f, 10f)) {
            for (b in listOf(-0.06f, 0f, 0.08f, -10f, 10f)) {
                for (c in listOf(-0.05f, 0f, 0.11f, -10f, 10f)) {
                    val points = List(4) { row ->
                        List(4) { column -> aboutMeshPoint(row, column, a, b, c) }
                    }
                    assertEquals(Offset.Zero, points[0][0])
                    assertEquals(Offset(1f, 0f), points[0][3])
                    assertEquals(Offset(0f, 1f), points[3][0])
                    assertEquals(Offset(1f, 1f), points[3][3])
                    for (row in 0..3) {
                        for (column in 0..3) {
                            val point = points[row][column]
                            assertTrue(point.x.isFinite() && point.x in 0f..1f)
                            assertTrue(point.y.isFinite() && point.y in 0f..1f)
                            if (column < 3) assertTrue(point.x < points[row][column + 1].x)
                            if (row < 3) assertTrue(point.y < points[row + 1][column].y)
                        }
                    }
                }
            }
        }
        assertTrue(aboutMeshPoint(1, 1, -0.07f, 0.08f, -0.05f) != aboutMeshPoint(1, 1, 0f, 0f, 0f))
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
