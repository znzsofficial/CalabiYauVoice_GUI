package com.nekolaska.calabiyau.core.preferences

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThemeSeedChoiceTest {
    @Test fun pickerRestoresOpaqueColorsWithoutConfusingWhiteWithWallpaper() {
        assertNull(explicitSeedArgb(-1)) // Existing users following the wallpaper.
        assertNull(explicitSeedArgb(0))
        for (color in listOf(0xFF217A59.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())) {
            assertEquals(color, explicitSeedArgb(encodeCustomSeed(color)))
        }
        assertEquals(0xFF217A59.toInt(), explicitSeedArgb(0xFF217A59.toInt())) // Already saved by 2.2.5.
    }
}
