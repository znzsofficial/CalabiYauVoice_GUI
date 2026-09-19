package com.nekolaska.calabiyau.feature.wiki.announcement.parser

import com.nekolaska.calabiyau.feature.wiki.announcement.model.Announcement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import util.wikiPathEncode

object AnnouncementParsers {

    private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

    // Wiki 将 SMW 属性从 时间/b站/官网 改名为 公告发布时间/公告B站发布链接/公告官网发布链接。
    // 新名优先，旧名兜底以兼容磁盘上的历史缓存。
    private val DATE_KEYS = listOf("公告发布时间", "时间")
    private val BILI_KEYS = listOf("公告B站发布链接", "b站")
    private val OFFICIAL_KEYS = listOf("公告官网发布链接", "官网")

    /**
     * 解析 ask 查询结果。线上在无数据时返回 JSON 数组（如 []）而非对象，
     * 因此接受任意 [JsonElement]：数组一律视为空结果，不再抛类型异常。
     */
    fun parseAnnouncements(results: JsonElement): List<Announcement> {
        val entries = when (results) {
            is JsonObject -> results.entries
            is JsonArray -> return emptyList()
            else -> return emptyList()
        }
        return entries.map { (title, value) ->
            val obj = value.jsonObject
            val printouts = obj["printouts"]?.jsonObject
            val fullUrl = obj["fullurl"]?.jsonPrimitive?.content
                ?: "$WIKI_BASE${title.wikiPathEncode()}"

            Announcement(
                title = title,
                date = printouts.firstText(DATE_KEYS),
                biliUrl = printouts.firstText(BILI_KEYS),
                officialUrl = printouts.firstText(OFFICIAL_KEYS),
                wikiUrl = fullUrl
            )
        }.sortedByDescending { it.date }
    }

    private fun JsonObject?.firstText(keys: List<String>): String {
        if (this == null) return ""
        for (key in keys) {
            val arr = this[key] as? JsonArray ?: continue
            val text = arr.firstOrNull()?.let { elem ->
                when (elem) {
                    is JsonPrimitive -> elem.content
                    is JsonObject -> elem["raw"]?.jsonPrimitive?.content
                        ?: elem["timestamp"]?.jsonPrimitive?.content
                        ?: ""
                    else -> ""
                }
            } ?: ""
            if (text.isNotBlank()) return text
        }
        return ""
    }
}
