package com.nekolaska.calabiyau.feature.wiki.decoration.source

import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogPolicy
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogSource
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationLuaSnapshot
import com.nekolaska.calabiyau.feature.wiki.decoration.parser.DecorationLuaCatalogParser
import data.SharedJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Validates before OfflineCache publishes; bad pairs cannot replace a previously usable disk snapshot. */
internal object DecorationLuaCachePayload {
    fun encode(items: String, ways: String): String = buildJsonObject {
        put("items", items)
        put("ways", ways)
    }.toString()

    fun decode(payload: String): DecorationLuaSnapshot? = runCatching {
        val root = SharedJson.parseToJsonElement(payload) as? JsonObject ?: return null
        DecorationLuaSnapshot(root.string("items") ?: return null, root.string("ways") ?: return null)
    }.getOrNull()

    fun isValid(policy: DecorationCatalogPolicy, payload: String): Boolean {
        val lua = policy.source as? DecorationCatalogSource.Lua ?: return false
        val pair = decode(payload) ?: return false
        val parsed = DecorationLuaCatalogParser.parse(lua, pair.itemsText, pair.waysText)
        if (!parsed.isComplete) WikiParseLogger.warnMalformed(
            "DecorationLuaCache:${policy.pageName}", parsed.issues.joinToString("; "), pair.itemsText
        )
        return parsed.isComplete
    }

    fun moduleText(response: String): String? = runCatching {
        val root = SharedJson.parseToJsonElement(response) as? JsonObject ?: return null
        if ("error" in root) return null
        val parse = root["parse"] as? JsonObject ?: return null
        (parse["wikitext"] as? JsonObject)?.string("*") ?: parse.string("wikitext")
    }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
