package com.nekolaska.calabiyau.feature.wiki.announcement.parser

import com.nekolaska.calabiyau.feature.wiki.announcement.model.Announcement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

object AnnouncementParsers {

    private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

    fun parseAnnouncements(results: JsonObject): List<Announcement> {
        return results.entries.map { (title, value) ->
            val obj = value.jsonObject
            val printouts = obj["printouts"]?.jsonObject
            val fullUrl = obj["fullurl"]?.jsonPrimitive?.content
                ?: "${WIKI_BASE}${URLEncoder.encode(title, "UTF-8").replace("+", "%20")}"

            val dateStr = printouts?.get("时间")?.jsonArray?.firstOrNull()?.let { elem ->
                when (elem) {
                    is JsonPrimitive -> elem.content
                    is JsonObject -> elem["raw"]?.jsonPrimitive?.content
                        ?: elem["timestamp"]?.jsonPrimitive?.content ?: ""
                    else -> ""
                }
            } ?: ""

            val biliUrl = printouts?.get("b站")?.jsonArray
                ?.firstOrNull()?.jsonPrimitive?.content ?: ""
            val officialUrl = printouts?.get("官网")?.jsonArray
                ?.firstOrNull()?.jsonPrimitive?.content ?: ""

            Announcement(
                title = title,
                date = dateStr,
                biliUrl = biliUrl,
                officialUrl = officialUrl,
                wikiUrl = fullUrl
            )
        }.sortedByDescending { it.date }
    }
}
