package com.nekolaska.calabiyau.core.wiki

/**
 * 受限 Lua table parser。
 *
 * 仅解析本项目当前 Wiki 模块数据所需的安全子集：
 * - return { ... }
 * - 数组表
 * - ["key"] = value 形式对象
 * - 字符串
 * - 整数
 */
internal object LuaTableParser {

    sealed interface LuaValue {
        data class LuaString(val value: String) : LuaValue
        data class LuaNumber(val value: Int) : LuaValue
        data class LuaArray(val values: List<LuaValue>) : LuaValue
        data class LuaObject(val fields: Map<String, LuaValue>) : LuaValue
    }

    fun parseReturnArray(input: String): List<LuaValue> {
        return (Parser(input).parseReturnValue() as? LuaValue.LuaArray)?.values ?: emptyList()
    }

    private class Parser(private val input: String) {
        private var index = 0

        fun parseReturnValue(): LuaValue {
            skipWhitespace()
            expectKeyword("return")
            skipWhitespace()
            return parseValue()
        }

        private fun parseValue(): LuaValue {
            skipWhitespace()
            return when (peek()) {
                '{' -> parseArrayOrObject()
                '"' -> LuaValue.LuaString(parseString())
                '-', in '0'..'9' -> LuaValue.LuaNumber(parseNumber())
                else -> error("Unsupported Lua value at position $index")
            }
        }

        private fun parseArrayOrObject(): LuaValue {
            expect('{')
            skipWhitespace()
            val values = mutableListOf<LuaValue>()
            val fields = linkedMapOf<String, LuaValue>()
            var hasObjectEntries = false

            while (true) {
                skipWhitespace()
                if (peek() == '}') {
                    expect('}')
                    break
                }

                if (peek() == '[') {
                    val entry = parseObjectEntry()
                    fields.putAll(entry.fields)
                    hasObjectEntries = true
                } else {
                    values += parseValue()
                }

                skipWhitespace()
                if (peek() == ',') {
                    expect(',')
                }
            }

            return if (hasObjectEntries) {
                LuaValue.LuaObject(fields)
            } else {
                LuaValue.LuaArray(values)
            }
        }

        private fun parseObjectEntry(): LuaValue.LuaObject {
            expect('[')
            val key = parseString()
            expect(']')
            skipWhitespace()
            expect('=')
            val value = parseValue()
            return LuaValue.LuaObject(mapOf(key to value))
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (index < input.length) {
                when (val ch = input[index++]) {
                    '\\' -> {
                        if (index >= input.length) break
                        val escaped = input[index++]
                        result.append(
                            when (escaped) {
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                '"' -> '"'
                                '\\' -> '\\'
                                else -> escaped
                            }
                        )
                    }
                    '"' -> return result.toString()
                    else -> result.append(ch)
                }
            }
            error("Unterminated Lua string")
        }

        private fun parseNumber(): Int {
            val start = index
            if (peek() == '-') index++
            while (index < input.length && input[index].isDigit()) index++
            return input.substring(start, index).toInt()
        }

        private fun expectKeyword(keyword: String) {
            skipWhitespace()
            if (!input.startsWith(keyword, index)) error("Expected keyword $keyword at position $index")
            index += keyword.length
        }

        private fun expect(ch: Char) {
            skipWhitespace()
            if (peek() != ch) error("Expected '$ch' at position $index")
            index++
        }

        private fun peek(): Char {
            skipWhitespace()
            return input.getOrElse(index) { '\u0000' }
        }

        private fun skipWhitespace() {
            while (index < input.length && input[index].isWhitespace()) index++
        }
    }
}
