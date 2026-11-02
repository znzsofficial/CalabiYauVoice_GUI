package com.nekolaska.calabiyau.feature.wiki.bio.parser

import java.math.BigInteger
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8

object BioDeckShareCodecs {

    private const val PREFIX_BASE = "000B"
    private const val HEX_LEN = 25
    // Safety ceiling, not a fixed card-table size: leave room for future official cards.
    private const val MAX_CARD_BITS = 4096
    private const val MAX_HEX_LEN = MAX_CARD_BITS / 4
    private const val MAX_BASE64_LEN = ((10 + MAX_HEX_LEN + 2) / 3) * 4
    private val PAYLOAD = Regex("000B\\|(0002|0003)\\|[0-9A-Fa-f]{1,$MAX_HEX_LEN}")
    private val BASE64_BODY = Regex("[A-Za-z0-9+/]+={0,2}")

    private val FACTION_CODE = mapOf(
        "超弦体" to "0002",
        "晶源体" to "0003"
    )

    private val CODE_FACTION = FACTION_CODE.entries.associate { (k, v) -> v to k }

    fun encodeShareCode(cardIndexes: List<Int>, faction: String): String {
        val factionCode = FACTION_CODE[faction]
            ?: throw IllegalArgumentException("无效阵营: $faction")
        if (cardIndexes.isEmpty()) {
            throw IllegalArgumentException("卡牌不能为空")
        }
        require(cardIndexes.all { it in 0 until MAX_CARD_BITS }) { "卡牌索引无效或超出支持范围" }

        val mask = cardIndexes.fold(BigInteger.ZERO) { acc, idx ->
            acc.setBit(idx)
        }

        val requiredLen = maxOf((mask.bitLength() + 3) / 4, HEX_LEN)
        val hex = mask.toString(16).uppercase().padStart(requiredLen, '0')
        val raw = "$PREFIX_BASE|$factionCode|$hex"
        val base64 = raw.encodeUtf8().base64()
        return "$base64**"
    }

    /**
     * @return Pair(解析出的 cardId 列表, 分享码中自带的阵营)
     */
    fun decodeShareCode(
        rawInput: String,
        expectedFaction: String,
        indexToCardId: Map<Int, String>
    ): Pair<List<String>, String> {
        val parts = validatedPayload(rawInput).split('|')
        val faction = CODE_FACTION.getValue(parts[1])
        if (faction != expectedFaction) {
            throw IllegalArgumentException("阵营不匹配：分享码是“$faction”，当前为“$expectedFaction”")
        }

        val mask = BigInteger(parts[2], 16)
        val ids = buildList {
            var bit = 0
            while (bit < mask.bitLength()) {
                if (mask.testBit(bit)) {
                    val id = indexToCardId[bit]
                    require(!id.isNullOrBlank()) { "分享码包含未知卡牌索引：$bit，请刷新卡牌数据" }
                    add(id)
                }
                bit++
            }
        }
        return ids to faction
    }

    /** Static display and import use the same protocol validation; never invent a code. */
    fun normalizeShareCode(rawInput: String): String = runCatching {
        val payload = validatedPayload(rawInput)
        payload.encodeUtf8().base64() + "**"
    }.getOrDefault("")

    private fun validatedPayload(rawInput: String): String {
        val code = extractActualShareCode(rawInput)
        require(code.isNotBlank() && code.length <= MAX_BASE64_LEN) { "分享码为空或过长" }
        // Okio works on API 25 and local JVM tests (java.util.Base64 needs API 26).
        // Its decoder is permissive, so validate alphabet, padding and canonical bits here.
        require(BASE64_BODY.matches(code)) { "分享码 Base64 字符无效" }
        val bytes = requireNotNull(code.decodeBase64()) { "分享码 Base64 格式不正确" }
        val canonical = bytes.base64()
        require(code == canonical || code == canonical.trimEnd('=')) {
            "分享码 Base64 格式不正确"
        }
        val payload = bytes.utf8()
        require(PAYLOAD.matches(payload)) { "分享码版本、阵营或卡牌位图无效" }
        return payload
    }

    fun extractDeckNameFromShareInput(rawInput: String): String? {
        val parts = rawInput.split('｜', '|').map { it.trim() }.filter { it.isNotBlank() }
        return if (parts.size >= 3) parts[parts.size - 2] else null
    }

    fun extractActualShareCode(rawInput: String): String {
        val cleaned = rawInput.trim()
        if (cleaned.isBlank()) return ""
        // 真实页面源码里分享码尾部带 MediaWiki 粗体残留 "**"
        if ('｜' !in cleaned && '|' !in cleaned) return cleaned.removeSuffix("**")
        val parts = cleaned.split('｜', '|').map { it.trim() }.filter { it.isNotBlank() }
        return parts.lastOrNull().orEmpty().removeSuffix("**")
    }
}
