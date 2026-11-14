package com.nekolaska.calabiyau.feature.wiki.decoration.parser

import com.nekolaska.calabiyau.core.wiki.LuaTableParser
import com.nekolaska.calabiyau.core.wiki.LuaTableParser.LuaValue
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationWaysResult

internal object DecorationWaysParser {
    fun parse(wikitext: String): DecorationWaysResult {
        val issues = mutableListOf<String>()
        val ways = linkedMapOf<Int, String>()
        try {
            val fields = (LuaTableParser.parseReturnTable(wikitext) as? LuaValue.LuaObject)?.fields
                ?: return DecorationWaysResult(emptyMap(), listOf("途径表必须以数值 ID 为键"))
            fields.forEach { (key, value) ->
                val id = key.toIntOrNull()?.takeIf { it > 0 }
                if (id == null) issues += "途径 ID 无效: $key" else {
                    try {
                        val text = readWay(value)
                        if (id in ways && ways[id] != text) issues += "途径 ID 冲突: $id"
                        else ways[id] = text
                    } catch (e: IllegalArgumentException) {
                        issues += "$id: ${e.message}"
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            issues += "途径表无效: ${e.message}"
        } catch (e: IllegalStateException) {
            issues += "途径表无效: ${e.message}"
        }
        return DecorationWaysResult(ways, issues)
    }

    private fun readWay(value: LuaValue): String = when (value) {
        is LuaValue.LuaString -> DecorationText.read(value.value)
        is LuaValue.LuaArray -> value.values.map {
            require(it is LuaValue.LuaString) { "途径数组含非字符串" }
            DecorationText.read(it.value)
        }.filter(String::isNotBlank).joinToString("\n")
        is LuaValue.LuaObject -> {
            require(value.fields.keys.all { it == "get" || it == "remark" }) { "未知途径对象结构" }
            val get = requireNotNull(value.fields["get"]) { "途径对象缺少 get" }
            require(get is LuaValue.LuaString || get is LuaValue.LuaArray) { "get 类型错误" }
            val remark = value.fields["remark"]
            require(remark == null || remark is LuaValue.LuaString) { "remark 类型错误" }
            listOf(readWay(get), remark?.value?.let(DecorationText::read)
                ?.takeIf(String::isNotBlank)?.let { "备注：$it" }.orEmpty()).filter(String::isNotBlank).joinToString("\n")
        }
        else -> throw IllegalArgumentException("途径记录类型错误")
    }
}
