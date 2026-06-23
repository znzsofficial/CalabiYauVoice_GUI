package com.nekolaska.calabiyau.feature.tools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 工具页通用执行框架：管理 busy 状态、IO 调度、错误提示。
 *
 * @param onBusyChange 忙碌状态回调
 * @param onResult 结果回调
 * @param showSnack 提示回调
 * @param errorLabel 操作名称（用于错误提示 "xxx失败：..."）
 * @param onSuccess 成功后的额外清理（可选）
 * @param block 在 IO 线程执行的核心逻辑，须返回 [ToolOutput]
 */
internal fun CoroutineScope.launchToolJob(
    onBusyChange: (Boolean) -> Unit,
    onResult: (ToolOutput) -> Unit,
    showSnack: (String) -> Unit,
    errorLabel: String,
    onSuccess: ((ToolOutput) -> Unit)? = null,
    block: suspend () -> ToolOutput
) {
    launch {
        onBusyChange(true)
        runCatching {
            withContext(Dispatchers.IO) { block() }
        }.onSuccess {
            onResult(it)
            showSnack(it.message)
            onSuccess?.invoke(it)
        }.onFailure {
            showSnack("$errorLabel：${it.message ?: "未知错误"}")
        }
        onBusyChange(false)
    }
}
