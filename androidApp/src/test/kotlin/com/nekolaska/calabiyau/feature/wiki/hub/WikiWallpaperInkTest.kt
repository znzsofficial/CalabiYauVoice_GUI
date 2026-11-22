package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikiWallpaperInkTest {
    @Test fun searchAndDirectoryReadabilitySurvivesBothGlassAndFallback() {
        for (colors in listOf(lightColorScheme(), darkColorScheme())) {
            // Search uses the higher surface, direct destinations and secondary pages use the lower one.
            for ((surface, glassAlpha, wallpaperAlpha) in listOf(
                Triple(colors.surfaceContainerHigh, 0.22f, 0.86f),
                Triple(colors.surfaceContainerLow, 0.25f, 0.78f),
                Triple(colors.surfaceContainerHigh, 0.22f, 0.78f),
            )) for (glass in listOf(false, true)) {
                val material = resolveHubPanelMaterial(glass, true, surface, colors.onSurface,
                    colors.onSurfaceVariant, glassAlpha, wallpaperAlpha, tintPlainGlass = false)
                if (glass) assertEquals(Color.Transparent, material.containerColor) else assertNull(material.tuning)
                val tint = material.tuning?.surfaceColor ?: material.containerColor
                for (backdrop in listOf(Color.Black, Color.White)) {
                    val background = tint.compositeOver(backdrop).luminance()
                    for (ink in listOf(colors.onSurface, colors.onSurfaceVariant)) {
                        val text = ink.luminance()
                        assertTrue((maxOf(background, text) + 0.05f) / (minOf(background, text) + 0.05f) >= 4.5f)
                    }
                }
                assertTrue(tint.alpha < 1f)
            }
        }
    }

    @Test fun withoutWallpaperUsesOriginalOpaqueOrGlassMaterials() {
        val colors = darkColorScheme()
        fun resolve(glass: Boolean, topic: Boolean) = resolveHubPanelMaterial(glass, false, colors.primaryContainer,
            colors.onPrimaryContainer, colors.onPrimaryContainer, 0.48f, 0.78f, tintPlainGlass = topic)
        assertEquals(colors.primaryContainer, resolve(false, true).containerColor)
        assertNull(resolve(true, false).tuning) // Use the existing lens/highlight profile.
        assertEquals(colors.primaryContainer.copy(alpha = 0.48f), resolve(true, true).tuning?.surfaceColor)
    }

    @Test fun extractionDirectoryKeepsBothTextRolesAndQueryButtonsReadableOverWallpaper() {
        for (colors in listOf(lightColorScheme(), darkColorScheme())) {
            for ((surface, inks, baseAlpha) in listOf(
                Triple(colors.surfaceContainerLow, listOf(colors.onSurface, colors.onSurfaceVariant), 0.30f),
                Triple(colors.surfaceContainerLow, listOf(colors.onSurface, colors.onSurfaceVariant), 0.78f),
                Triple(colors.primaryContainer, listOf(colors.onPrimaryContainer), 0.48f),
                Triple(colors.surface, listOf(colors.primary), 0.78f)
            )) {
                val tint = hubReadableTint(surface, inks.first(), baseAlpha, inks.last())
                assertTrue(tint.alpha < 1f, "Keep the wallpaper visible with the standard theme")
                for (ink in inks) for (photo in listOf(Color.White, Color.Black, ink, Color.Yellow, Color.Blue)) {
                    val bg = tint.compositeOver(photo).luminance()
                    val fg = ink.luminance()
                    assertTrue((maxOf(bg, fg) + 0.05f) / (minOf(bg, fg) + 0.05f) >= 4.5f,
                        "Insufficient contrast: $ink on $tint over $photo")
                }
            }
        }
    }

    @Test fun oppositeToneWallpaperAndInkColoredPatchesCannotHideTheTitleOrAction() {
        for ((surface, ink) in listOf(
            Color(0xffFFFBFE) to Color(0xff1C1B1F), Color(0xffFFFBFE) to Color(0xff6750A4),
            Color(0xff1C1B1F) to Color(0xffE6E1E5), Color(0xff1C1B1F) to Color(0xffD0BCFF)
        )) {
            val alpha = wallpaperInkProtection(surface, ink)
            assertTrue(alpha < 1f, "Retain the wallpaper when the theme permits it")
            for (photo in listOf(Color.White, Color.Black, ink, Color.Blue, Color.Yellow)) {
                val bg = surface.copy(alpha = alpha).compositeOver(photo).luminance()
                val fg = ink.luminance()
                assertTrue((maxOf(bg, fg) + 0.05f) / (minOf(bg, fg) + 0.05f) >= 4.5f)
            }
        }
    }

    @Test fun lowContrastThemeDoesNotMistakeTheOppositePhotoForAReadableMidtone() {
        // White on pale grey cannot be fixed by adding translucent grey over arbitrary photos.
        assertEquals(1f, wallpaperInkProtection(Color.LightGray, Color.White))
    }
}
