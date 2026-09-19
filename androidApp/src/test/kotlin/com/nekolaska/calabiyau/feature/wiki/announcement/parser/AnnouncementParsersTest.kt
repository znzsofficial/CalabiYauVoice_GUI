package com.nekolaska.calabiyau.feature.wiki.announcement.parser

import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnouncementParsersTest {

    @Test
    fun parsesPrintoutsAndSortsByDate() {
        val results = SharedJson.parseToJsonElement(
            """
            {
              "旧公告": {
                "fullurl": "https://wiki.biligame.com/klbq/旧公告",
                "printouts": {
                  "时间": [{"raw": "2025-01-01"}],
                  "b站": ["https://b23.tv/old"],
                  "官网": ["https://official/old"]
                }
              },
              "新公告": {
                "printouts": {
                  "时间": ["2026-08-01"],
                  "b站": ["https://b23.tv/new"],
                  "官网": []
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val items = AnnouncementParsers.parseAnnouncements(results)
        assertEquals(listOf("新公告", "旧公告"), items.map { it.title })
        assertEquals("2026-08-01", items[0].date)
        assertEquals("https://b23.tv/new", items[0].biliUrl)
        assertEquals("", items[0].officialUrl)
        assertTrue(
            items[0].wikiUrl.contains("%E6%96%B0%E5%85%AC%E5%91%8A") ||
                items[0].wikiUrl.endsWith("新公告")
        )
        assertEquals("https://wiki.biligame.com/klbq/旧公告", items[1].wikiUrl)
    }

    @Test
    fun parsesRenamedPrintoutKeys() {
        // 2026-09 Wiki 将 SMW 属性改名为 公告发布时间/公告B站发布链接/公告官网发布链接
        val results = SharedJson.parseToJsonElement(
            """
            {
              "新公告": {
                "printouts": {
                  "公告发布时间": ["2026-09-10"],
                  "公告B站发布链接": ["https://b23.tv/new2"],
                  "公告官网发布链接": []
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val items = AnnouncementParsers.parseAnnouncements(results)
        assertEquals("2026-09-10", items.single().date)
        assertEquals("https://b23.tv/new2", items.single().biliUrl)
        assertEquals("", items.single().officialUrl)
    }

    @Test
    fun emptyArrayResultsYieldNoCrash() {
        // SMW 无结果时 results 是 JSON 数组（如 []），不得抛 JsonObject 类型异常
        val empty = SharedJson.parseToJsonElement("[]")
        assertTrue(AnnouncementParsers.parseAnnouncements(empty).isEmpty())
    }
}
