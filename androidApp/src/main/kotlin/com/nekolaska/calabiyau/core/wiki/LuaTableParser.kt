package com.nekolaska.calabiyau.core.wiki

/**
 * 受限 Lua table parser。
 *
 * 仅解析本项目当前 Wiki 模块数据所需的安全子集：
 * - return { ... }
 * - 数组表
 * - key = value / ["key"] = value 形式对象
 * - 双引号、单引号和 [[...]] 字符串
 * - 整数、布尔值、nil
 * - 单行注释和块注释
 */
internal object LuaTableParser {

    sealed interface LuaValue {
        data class LuaString(val value: String) : LuaValue
        data class LuaNumber(val value: Int) : LuaValue
        data class LuaBoolean(val value: Boolean) : LuaValue
        data class LuaArray(val values: List<LuaValue>) : LuaValue
        data class LuaObject(val fields: Map<String, LuaValue>) : LuaValue
        data object LuaNil : LuaValue
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
                '"', '\'' -> LuaValue.LuaString(parseQuotedString())
                '[' -> LuaValue.LuaString(parseLongString())
                '-', in '0'..'9' -> LuaValue.LuaNumber(parseNumber())
                else -> parseKeywordValue()
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

                when {
                    peek() == '[' && !startsLongString() -> {
                    val entry = parseObjectEntry()
                    fields.putAll(entry.fields)
                    hasObjectEntries = true
                    }
                    isIdentifierStart(peek()) && looksLikeBareKeyEntry() -> {
                        val entry = parseBareObjectEntry()
                        fields.putAll(entry.fields)
                        hasObjectEntries = true
                    }
                    else -> values += parseValue()
                }

                skipWhitespace()
                if (peek() == ',' || peek() == ';') {
                    index++
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
            val key = when (peek()) {
                '"', '\'' -> parseQuotedString()
                else -> parseNumber().toString()
            }
            expect(']')
            skipWhitespace()
            expect('=')
            val value = parseValue()
            return LuaValue.LuaObject(mapOf(key to value))
        }

        private fun parseBareObjectEntry(): LuaValue.LuaObject {
            val key = parseIdentifier()
            skipWhitespace()
            expect('=')
            val value = parseValue()
            return LuaValue.LuaObject(mapOf(key to value))
        }

        private fun parseQuotedString(): String {
            val quote = peek()
            expect(quote)
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
                                '\'' -> '\''
                                '\\' -> '\\'
                                else -> escaped
                            }
                        )
                    }
                    quote -> return result.toString()
                    else -> result.append(ch)
                }
            }
            error("Unterminated Lua string")
        }

        private fun parseLongString(): String {
            if (!startsLongString()) error("Expected Lua long string at position $index")
            index += 2
            val end = input.indexOf("]]", startIndex = index)
            if (end < 0) error("Unterminated Lua long string")
            return input.substring(index, end).also { index = end + 2 }
        }

        private fun parseNumber(): Int {
            val start = index
            if (peek() == '-') index++
            while (index < input.length && input[index].isDigit()) index++
            return input.substring(start, index).toInt()
        }

        private fun parseKeywordValue(): LuaValue {
            val identifier = parseIdentifier()
            return when (identifier) {
                "true" -> LuaValue.LuaBoolean(true)
                "false" -> LuaValue.LuaBoolean(false)
                "nil" -> LuaValue.LuaNil
                else -> error("Unsupported Lua identifier '$identifier' at position $index")
            }
        }

        private fun parseIdentifier(): String {
            skipWhitespace()
            if (!isIdentifierStart(peek())) error("Expected identifier at position $index")
            val start = index++
            while (index < input.length && isIdentifierPart(input[index])) index++
            return input.substring(start, index)
        }

        private fun looksLikeBareKeyEntry(): Boolean {
            val saved = index
            return try {
                parseIdentifier()
                skipWhitespace()
                peek() == '='
            } finally {
                index = saved
            }
        }

        private fun startsLongString(): Boolean = input.startsWith("[[", index)

        private fun isIdentifierStart(ch: Char): Boolean = ch == '_' || ch.isLetter()

        private fun isIdentifierPart(ch: Char): Boolean = ch == '_' || ch.isLetterOrDigit()

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
            while (index < input.length) {
                when {
                    input[index].isWhitespace() -> index++
                    input.startsWith("--[[", index) -> {
                        val end = input.indexOf("]]", startIndex = index + 4)
                        index = if (end >= 0) end + 2 else input.length
                    }
                    input.startsWith("--", index) -> {
                        val end = input.indexOf('\n', startIndex = index + 2)
                        index = if (end >= 0) end + 1 else input.length
                    }
                    else -> return
                }
            }
        }
    }
}
