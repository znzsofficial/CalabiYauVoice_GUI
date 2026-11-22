package com.nekolaska.calabiyau.core.preferences

// Keep existing choices (-1 = wallpaper, 0 = system, otherwise an ARGB color) readable.
// The picker emits opaque colors only; reserve transparent white for the opaque-white choice.
private const val EXPLICIT_WHITE = 0x00FFFFFF

internal fun explicitSeedArgb(choice: Int): Int? = when (choice) {
    AppPrefs.SEED_WALLPAPER, 0 -> null
    EXPLICIT_WHITE -> 0xFFFFFFFF.toInt()
    else -> choice
}

internal fun encodeCustomSeed(argb: Int): Int = if (argb == 0xFFFFFFFF.toInt()) EXPLICIT_WHITE else argb
