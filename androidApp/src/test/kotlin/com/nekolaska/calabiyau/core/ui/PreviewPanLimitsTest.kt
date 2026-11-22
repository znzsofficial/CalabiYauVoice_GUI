package com.nekolaska.calabiyau.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewPanLimitsTest {
    @Test fun wideTimelineAndTallPortraitCanOnlyPanWhereTheEnlargedImageOverflows() {
        val phone = IntSize(300, 600)
        assertEquals(Offset(225f, 0f), zoomPanLimits(phone, IntSize(1200, 100), 2.5f))
        assertEquals(Offset(0f, 450f), zoomPanLimits(phone, IntSize(100, 1200), 2.5f))
        assertEquals(Offset.Zero, zoomPanLimits(phone, IntSize(1200, 100), 1f))
    }

    @Test fun resizingFromLandscapeToSplitScreenReducesTheLegalOffset() {
        val square = IntSize(400, 400)
        assertEquals(Offset(300f, 600f), zoomPanLimits(IntSize(1200, 600), square, 3f))
        assertEquals(Offset(200f, 0f), zoomPanLimits(IntSize(200, 600), square, 3f))
    }

    @Test fun dominantDragAtAnEdgeIsLeftForTheParentPager() {
        val rightEdge = Offset(225f, 0f)
        assertFalse(imageCanPan(rightEdge, rightEdge, Offset(12f, 1f)))
        assertFalse(imageCanPan(Offset.Zero, Offset(1f, 0f), Offset(1f, 12f)))
        assertTrue(imageCanPan(rightEdge, Offset(210f, 0f), Offset(-6f, 1f)))
    }
}
