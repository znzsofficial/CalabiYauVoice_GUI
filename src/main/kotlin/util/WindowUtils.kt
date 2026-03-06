package util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 创建非 Win11 系统（或未选择 backdrop 时）的背景渐变。
 * 调用方应用 remember(darkMode) 缓存。
 */
fun getNonWin11BackgroundGradient(isDarkMode: Boolean): Brush =
    if (isDarkMode) {
        Brush.linearGradient(
            colors = listOf(Color(0xff1A212C), Color(0xff2C343C)),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xffCCD7E8), Color(0xffDAE9F7)),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }

