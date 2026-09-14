package com.nekolaska.calabiyau.feature.tools

import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * 记住一个与外部 value 保持同步的 SliderState。
 *
 * material3 alpha28 起有状态 Slider 接管值管理：
 * 外部 value 变化（如重置按钮）通过 LaunchedEffect 回写，拖动产生的新值经
 * Slider 的 onValueChange 回调向上传递。
 */
@Composable
internal fun rememberSyncedSliderState(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
): SliderState {
    val state = remember(valueRange, steps) { SliderState(value, steps, valueRange) }
    LaunchedEffect(value) {
        if (state.value != value) state.value = value
    }
    return state
}
