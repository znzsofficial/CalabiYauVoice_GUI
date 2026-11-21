package com.nekolaska.calabiyau.feature.tools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.currentCoroutineContext

/**
 * 工具页任务回调集合，避免每个任务重复传递同一组三个回调。
 */
internal data class ToolJobCallbacks(
    val onBusyChange: (Boolean) -> Unit,
    val onResult: (ToolOutput) -> Unit,
    val showSnack: (String) -> Unit
)

internal data class ToolPreview<T>(val data: T? = null, val error: String? = null)

internal suspend fun <T : Any> loadToolPreview(read: () -> T?): ToolPreview<T> = try {
    val data = withContext(Dispatchers.IO + toolTaskJob.asContextElement(currentCoroutineContext()[Job])) {
        checkToolCancellation()
        read().also { checkToolCancellation() }
    }
    if (data == null) ToolPreview(error = "无法读取图片") else ToolPreview(data = data)
} catch (e: CancellationException) { throw e }
catch (e: Exception) { ToolPreview(error = e.message ?: "预览生成失败") }

/**
 * 工具页通用执行框架：管理 busy 状态、IO 调度、错误提示。
 *
 * @param callbacks 工具页任务回调集合
 * @param errorLabel 操作名称（用于错误提示 "xxx失败：..."）
 * @param onSuccess 成功后的额外清理（可选）
 * @param block 在 IO 线程执行的核心逻辑，须返回 [ToolOutput]
 */
internal fun CoroutineScope.launchToolJob(
    callbacks: ToolJobCallbacks,
    errorLabel: String,
    onSuccess: ((ToolOutput) -> Unit)? = null,
    onFailure: ((Exception) -> Unit)? = null,
    block: suspend () -> ToolOutput
): Job = launch {
    callbacks.onBusyChange(true)
    try {
        val output = withContext(Dispatchers.IO + toolTaskJob.asContextElement(currentCoroutineContext()[Job])) {
            checkToolCancellation()
            block().also { checkToolCancellation() }
        }
        callbacks.onResult(output)
        callbacks.showSnack(output.message)
        onSuccess?.invoke(output)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure?.invoke(e)
        callbacks.showSnack("$errorLabel：${e.message ?: "未知错误"}")
    } finally {
        callbacks.onBusyChange(false)
    }
}
