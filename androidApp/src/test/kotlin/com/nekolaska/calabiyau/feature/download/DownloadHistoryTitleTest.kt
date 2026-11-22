package com.nekolaska.calabiyau.feature.download

import data.DownloadRecord
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadHistoryTitleTest {
    @Test
    fun fileSearchTitleWorksForStoredRecordsWithOrWithoutAPath() {
        val rows = DownloadRecord.decodeFromJson("""[
            {"name":"文件搜索 (6个)","fileCount":6,"savePath":"/download/文件搜索"},
            {"name":"文件搜索 (12个)","fileCount":12},
            {"name":"文件搜索","fileCount":3}
        ]""")
        assertEquals(listOf("文件搜索", "文件搜索", "文件搜索"), rows.map { it.historyTitle() })
    }

    @Test
    fun similarCategoryAndPortraitNamesSurviveIncludingAnExactTitleCollision() {
        val rows = listOf(
            DownloadRecord("星绘 (6个)", 6),
            DownloadRecord("文件搜索 (6个) / 默认", 6),
            DownloadRecord("文件搜索 (12个)", 6),
            DownloadRecord("文件搜索 (6个)", 6, savePath = "/download/文件搜索 (6个)"),
            DownloadRecord("文件搜索 (6个)", 6, status = "pending")
        )
        for (row in rows) assertEquals(row.name, row.historyTitle())
    }
}
