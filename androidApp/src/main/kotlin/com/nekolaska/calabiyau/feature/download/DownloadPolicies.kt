package com.nekolaska.calabiyau.feature.download

import kotlinx.coroutines.CancellationException

internal const val RESUME_RECORD_SAVE_WARNING =
    "无法保存恢复记录，本次下载仍会继续；关闭应用后不能继续原任务"
internal const val RESUME_RECORD_CLEAR_WARNING = "文件已保存，但无法清除恢复记录"

/** The error snackbar replaces whatever is showing, so the warning has to travel with it. */
internal fun downloadFailureMessage(warnings: List<String>, detail: String): String {
    val failure = "下载失败: $detail"
    return if (warnings.isEmpty()) failure else (warnings.distinct() + failure).joinToString("\n")
}

internal data class CategoryFileBatch(
    val category: String,
    val files: List<Pair<String, String>>
)

internal data class CategoryFileResolution(
    val batches: List<CategoryFileBatch>,
    val failures: List<String>
)

/** Resume bookkeeping is optional. It must not reject a download or turn finished files into a failure. */
internal suspend fun runResourceDownload(
    request: ResourceDownloadRequest,
    persist: suspend (String?) -> Boolean,
    download: suspend (ResourceDownloadRequest) -> Unit,
    warn: (String) -> Unit
): Boolean {
    if (!persist(request.encode())) warn(RESUME_RECORD_SAVE_WARNING)
    download(request)
    val cleared = persist(null)
    if (!cleared) warn(RESUME_RECORD_CLEAR_WARNING)
    return cleared
}

internal fun categoryDisplayName(category: String): String =
    category.removePrefix("Category:").removePrefix("分类:")

/**
 * A failed category query is not an empty category. Successful queries stay downloadable.
 * Cancellation is never converted into a category failure.
 */
internal suspend fun resolveCategoryDownloads(
    categories: List<String>,
    manualSelectionMap: Map<String, List<Pair<String, String>>>,
    fetch: suspend (String) -> List<Pair<String, String>>
): CategoryFileResolution {
    val batches = mutableListOf<CategoryFileBatch>()
    val failures = mutableListOf<String>()
    for (category in categories.distinct()) {
        val files = if (category in manualSelectionMap) {
            manualSelectionMap.getValue(category)
        } else {
            try {
                fetch(category)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += "${categoryDisplayName(category)}: ${e.message ?: e::class.simpleName.orEmpty()}"
                continue
            }
        }
        val unique = files.distinctBy { it.second }
        if (unique.isNotEmpty()) batches += CategoryFileBatch(category, unique)
    }
    return CategoryFileResolution(batches, failures)
}

internal fun incompleteDownloadMessage(downloadError: String?, fetchFailures: List<String>): String {
    val fetchSummary = fetchFailures.takeIf { it.isNotEmpty() }
        ?.let { "${it.size} 个分类获取失败: ${it.joinToString("; ")}" }
    return listOfNotNull(downloadError?.takeIf { it.isNotBlank() }, fetchSummary)
        .joinToString("；")
        .ifBlank { "下载未完成" }
}
