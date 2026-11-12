package com.nekolaska.calabiyau.feature.weapon.list

import data.SharedJson
import kotlinx.serialization.json.*

/** Assemble every SMW page before publishing one category cache. A failed tail keeps the old cache. */
internal object WeaponListSource {
    fun results(payload: String): JsonObject {
        val root = SharedJson.parseToJsonElement(payload).jsonObject
        require(root["error"] == null && root["errors"] == null) { "武器查询返回错误" }
        val query = root["query"] as? JsonObject ?: error("缺少武器查询结果")
        require(query["errors"] == null) { "武器查询返回错误" }
        val rows = query["results"] as? JsonObject ?: error("缺少武器列表")
        require(rows.isNotEmpty() && rows.all { (name, value) ->
            name.isNotBlank() && value is JsonObject && value["printouts"] is JsonObject
        }) { "武器列表结构不完整" }
        return rows
    }

    fun isValidPayload(payload: String): Boolean = runCatching {
        val root = SharedJson.parseToJsonElement(payload).jsonObject
        val rows = results(payload)
        // A legacy cached first page with continuation is not a complete category.
        root["query-continue-offset"] == null && WeaponListApi.parseWeapons(payload).size == rows.size
    }.getOrDefault(false)

    suspend fun fetchAll(fetchPage: suspend (offset: Int) -> String?): String? {
        val merged = linkedMapOf<String, JsonElement>()
        var offset = 0
        repeat(100) {
            val payload = fetchPage(offset) ?: return null
            val root = SharedJson.parseToJsonElement(payload).jsonObject
            val rows = results(payload)
            require(rows.keys.none { it in merged }) { "武器分页重复，拒绝发布不完整列表" }
            merged.putAll(rows)
            val next = root["query-continue-offset"] ?: return buildJsonObject {
                putJsonObject("query") { put("results", JsonObject(merged)) }
            }.toString()
            val nextOffset = (next as? JsonPrimitive)?.intOrNull
            require(nextOffset != null && nextOffset > offset) { "武器分页游标无效" }
            offset = nextOffset
        }
        error("武器分页超过限制")
    }
}
