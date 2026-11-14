package com.nekolaska.calabiyau.feature.wiki.decoration.source

import com.nekolaska.calabiyau.feature.wiki.decoration.parser.DecorationHtmlParser
import data.SharedJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Decoration-specific publication boundary: a parse envelope alone is not a usable directory. */
internal object DecorationHtmlCachePayload {
    fun html(pageName: String, payload: String): String? = runCatching {
        val root = SharedJson.parseToJsonElement(payload) as? JsonObject ?: return null
        if ("error" in root) return null
        val parse = root["parse"] as? JsonObject ?: return null
        if (parse.string("title") != pageName) return null
        val html = (parse["text"] as? JsonObject)?.string("*") ?: return null
        html.takeIf { DecorationHtmlParser.parseResult(it).isComplete }
    }.getOrNull()

    fun isValid(pageName: String, payload: String): Boolean = html(pageName, payload) != null

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
