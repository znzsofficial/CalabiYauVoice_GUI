package com.nekolaska.calabiyau.feature.settings

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AboutTextProtectionTest {
    private val themes = listOf(
        lightColorScheme(),
        darkColorScheme(),
        darkColorScheme(background = Color.Black, surface = Color.Black, surfaceContainerLow = Color.Black),
        lightColorScheme(
            primary = Color(0xFFFF00CC), secondary = Color.Cyan, tertiary = Color.Yellow,
            surface = Color(0xFFFFE0F6), onSurface = Color(0xFF311025)
        ),
        darkColorScheme(
            primary = Color(0xFF00FF88), secondary = Color.Yellow, tertiary = Color.Magenta,
            surface = Color(0xFF102A20), onSurface = Color(0xFFDCFFEE)
        ),
        // Deliberately poor custom/reduced-contrast input must still be readable.
        lightColorScheme(
            primary = Color.Yellow, surface = Color(0xFF777777), onSurface = Color(0xFF888888),
            surfaceContainerLow = Color(0xFF777777), onSurfaceVariant = Color(0xFF999999),
            secondaryContainer = Color.Yellow, onSecondaryContainer = Color.White
        )
    )

    @Test
    fun allTextRolesMeetAAInLightDarkAmoledAndCustomThemes() {
        for (scheme in themes) {
            for ((background, foreground) in textRoles(scheme)) {
                val protected = aboutTextProtection(background, foreground)
                assertEquals(background.copy(alpha = 1f), protected.background)
                assertEquals(1f, protected.foreground.alpha)
                assertTrue(contrast(protected.background, protected.foreground) >= 4.5f)
                if (contrast(background, foreground) >= 4.5f) {
                    assertEquals(foreground, protected.foreground, "Already readable theme roles stay unchanged")
                }
            }
        }
    }

    @Test
    fun opaqueProtectionMakesContrastIndependentOfEveryMeshColor() {
        for (scheme in themes) {
            for ((background, foreground) in textRoles(scheme)) {
                val protected = aboutTextProtection(background, foreground)
                val meshColors = aboutMeshColors(scheme) + listOf(Color.Black, Color.White, scheme.primary)
                for (meshColor in meshColors) {
                    val actualBackground = protected.background.compositeOver(meshColor)
                    assertEquals(protected.background, actualBackground)
                    assertTrue(contrast(actualBackground, protected.foreground) >= 4.5f)
                }
            }
        }
    }

    @Test
    fun protectionHandlesTranslucentInputsAndIntermediateThemeColors() {
        for (step in 0..255) {
            val gray = Color(step / 255f, step / 255f, step / 255f, 0.1f)
            val protected = aboutTextProtection(gray, gray)
            assertEquals(1f, protected.background.alpha)
            assertEquals(1f, protected.foreground.alpha)
            assertTrue(contrast(protected.background, protected.foreground) >= 4.5f)
            val scheme = lightColorScheme()
            val dark = darkColorScheme()
            val animated = aboutTextProtection(
                lerp(scheme.surface, dark.surface, step / 255f),
                lerp(scheme.onSurface, dark.onSurface, step / 255f)
            )
            assertTrue(contrast(animated.background, animated.foreground) >= 4.5f)
        }
    }

    private fun textRoles(scheme: ColorScheme): List<Pair<Color, Color>> = listOf(
        scheme.background to scheme.onSurface,
        scheme.background to scheme.primary,
        scheme.background to scheme.onSurfaceVariant,
        scheme.surface to scheme.onSurface,
        scheme.surface to scheme.onSurfaceVariant,
        scheme.surfaceContainerLow to scheme.onSurface,
        scheme.surfaceContainerLow to scheme.primary,
        scheme.surfaceContainerLow to scheme.onSurfaceVariant,
        scheme.surfaceContainerHigh to scheme.onSurface,
        scheme.surfaceContainerHigh to scheme.onSurfaceVariant,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.primary to Color.White
    )

    private fun contrast(background: Color, foreground: Color): Float {
        val a = background.luminance()
        val b = foreground.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
