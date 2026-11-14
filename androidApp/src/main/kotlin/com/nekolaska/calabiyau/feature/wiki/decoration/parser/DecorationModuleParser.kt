package com.nekolaska.calabiyau.feature.wiki.decoration.parser

import com.nekolaska.calabiyau.core.wiki.LuaTableParser
import com.nekolaska.calabiyau.core.wiki.LuaTableParser.LuaValue
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationItemsResult
import com.nekolaska.calabiyau.feature.wiki.decoration.model.LuaDecorationItem

/** Strict item table parsing; zero quality is real data (e.g. default room 93001). */
internal object DecorationModuleParser {
    fun parse(wikitext: String): DecorationItemsResult {
        val issues = mutableListOf<String>()
        val items = linkedMapOf<Int, LuaDecorationItem>()
        try {
            val rows = (LuaTableParser.parseReturnTable(wikitext) as? LuaValue.LuaArray)?.values
                ?: return DecorationItemsResult(emptyList(), listOf("条目表必须为数组"))
            rows.forEachIndexed { index, value ->
                val fields = (value as? LuaValue.LuaObject)?.fields
                val id = (fields?.get("id") as? LuaValue.LuaNumber)?.value
                val name = (fields?.get("name") as? LuaValue.LuaString)?.value
                val quality = (fields?.get("quality") as? LuaValue.LuaNumber)?.value
                val description = (fields?.get("desc") as? LuaValue.LuaString)?.value
                if (id == null || id <= 0 || name.isNullOrBlank() || quality == null || quality < 0 || description == null) {
                    issues += "条目[$index] 缺少有效 id/name/quality/desc"
                    return@forEachIndexed
                }
                val special = fields["spdesc"]
                val file = fields["file"]
                if (special != null && special !is LuaValue.LuaString) issues += "$id: spdesc 类型错误"
                if (file != null && file !is LuaValue.LuaString) issues += "$id: file 类型错误"
                val readableName = DecorationText.read(name)
                if (readableName.isBlank()) issues += "$id: 名称只有空标记"
                val row = LuaDecorationItem(id, readableName, quality, DecorationText.read(description),
                    (special as? LuaValue.LuaString)?.value?.let(DecorationText::read),
                    (file as? LuaValue.LuaString)?.value?.trim(), name)
                val previous = items[id]
                if (previous != null && previous != row) issues += "条目 ID 冲突: $id"
                else items[id] = row
            }
        } catch (e: IllegalArgumentException) {
            issues += "条目表无效: ${e.message}"
        } catch (e: IllegalStateException) {
            issues += "条目表无效: ${e.message}"
        }
        if (items.isEmpty()) issues += "条目表为空"
        return DecorationItemsResult(items.values.toList(), issues)
    }
}
