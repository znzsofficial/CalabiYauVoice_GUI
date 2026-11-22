package com.nekolaska.calabiyau.feature.download

import data.DownloadRecord
import java.io.File

internal const val FILE_SEARCH_NAME = "文件搜索"

internal fun DownloadRecord.historyTitle(): String {
    // The old file-search writer used this exact title/count pair for successful batches.
    if (status != "success" || fileCount <= 0 || name != "$FILE_SEARCH_NAME (${fileCount}个)") return name
    // Category records use the character name as their directory, even when it looks like this title.
    return if (savePath.isEmpty() || File(savePath).name == FILE_SEARCH_NAME) FILE_SEARCH_NAME else name
}
