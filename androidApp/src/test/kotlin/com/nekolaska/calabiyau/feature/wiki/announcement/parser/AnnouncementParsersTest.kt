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
    fun dateObjectPrefersTimestampAndStripsCalendarPrefix() {
        // 公告发布时间是 Date 类型：SMW 返回 {timestamp, raw}，
        // raw 带日历模型前缀（"1/2026/9/16"），应显示为 "2026/9/16"
        val results = SharedJson.parseToJsonElement(
            """
            {
              "公告A": {"printouts": {"公告发布时间": [{"timestamp":"1789516800","raw":"1/2026/9/16"}]}},
              "公告B": {"printouts": {"公告发布时间": [{"raw":"1/2026/9/14"}]}}
            }
            """.trimIndent()
        ).jsonObject

        val byTitle = { t: String ->
            AnnouncementParsers.parseAnnouncements(results).first { it.title == t }
        }
        val a = byTitle("公告A")
        assertEquals("2026/9/16", a.date)
        assertEquals(1789516800L, a.sortTimestamp)
        // 无 timestamp 时回退 raw 并剥离日历前缀
        val b = byTitle("公告B")
        assertEquals("2026/9/14", b.date)
        assertEquals(0L, b.sortTimestamp)
    }

    @Test
    fun sortsByTimestampBeforeFallingBackToDateText() {
        // 文本排序会把 9月排在 10月后（'9'>'1'）；timestamp 存在时必须按数值排
        val results = SharedJson.parseToJsonElement(
            """
            {
              "十月公告": {"printouts": {"公告发布时间": [{"timestamp":"1790000000","raw":"1/2026/10/1"}]}},
              "九月公告": {"printouts": {"公告发布时间": [{"timestamp":"1789516800","raw":"1/2026/9/16"}]}}
            }
            """.trimIndent()
        ).jsonObject

        val items = AnnouncementParsers.parseAnnouncements(results)
        assertEquals(listOf("十月公告", "九月公告"), items.map { it.title })
    }

    @Test
    fun emptyArrayResultsYieldNoCrash() {
        // SMW 无结果时 results 是 JSON 数组（如 []），不得抛 JsonObject 类型异常
        val empty = SharedJson.parseToJsonElement("[]")
        assertTrue(AnnouncementParsers.parseAnnouncements(empty).isEmpty())
    }
}
