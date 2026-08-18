package com.nekolaska.calabiyau.core.ui

import androidx.compose.ui.graphics.Color

fun catalogQualityLabel(level: Int): String = when (level) {
    1 -> "初始"
    2 -> "精致"
    3 -> "卓越"
    4 -> "完美"
    5 -> "传说"
    6 -> "私服"
    8 -> "臻藏"
    else -> "未知"
}

fun catalogQualityColor(level: Int): Color = when (level) {
    1 -> Color(0xFF94A3B8)
    2 -> Color(0xFF3B82F6)
    3 -> Color(0xFFA855F7)
    4 -> Color(0xFFF59E0B)
    5 -> Color(0xFFEF4444)
    6, 8 -> Color(0xFFFF6B2C)
    else -> Color(0xFF9CA3AF)
}
