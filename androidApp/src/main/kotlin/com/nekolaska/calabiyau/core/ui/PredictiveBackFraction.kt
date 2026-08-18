package com.nekolaska.calabiyau.core.ui

internal fun predictiveBackFraction(progress: Float): Float {
    val eased = progress.coerceIn(0f, 1f)
    return eased * eased * (3f - 2f * eased)
}
