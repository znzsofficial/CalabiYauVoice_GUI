package com.nekolaska.calabiyau.core.wiki

import kotlin.test.*

class AvatarBudgetTest {
    @Test fun wideTallAndLowHeapStayWithinBothBounds() {
        assertEquals(1, avatarSampleSize(512, 512, 16L shl 20))
        assertEquals(8, avatarSampleSize(16000, 4000, 16L shl 20))
        assertEquals(8, avatarSampleSize(4000, 16000, 16L shl 20))
        assertEquals(4, avatarSampleSize(2048, 2048, 1L shl 20))
        assertNull(avatarSampleSize(-1, 50, Long.MAX_VALUE))
    }
}
