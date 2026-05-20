package com.nekolaska.calabiyau.feature.wiki.bio.parser

import android.util.Base64
import java.math.BigInteger

object BioDeckShareCodecs {

    private const val PREFIX_BASE = "000B"
    private const val HEX_LEN = 25

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

        val mask = cardIndexes.fold(BigInteger.ZERO) { acc, idx ->
            if (idx < 0) acc else acc.setBit(idx)
        }

        val requiredLen = maxOf((mask.bitLength() + 3) / 4, HEX_LEN)
        val hex = mask.toString(16).uppercase().padStart(requiredLen, '0')
        val raw = "$PREFIX_BASE|$factionCode|$hex"
        val base64 = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
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
        val code = extractActualShareCode(rawInput)
        if (code.isBlank()) throw IllegalArgumentException("分享码为空")

        val b64 = code.removeSuffix("**")
        val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
        val parts = decoded.split("|")
        if (parts.size < 3) throw IllegalArgumentException("分享码格式不正确")

        val faction = CODE_FACTION[parts[1]]
            ?: throw IllegalArgumentException("分享码阵营无效")
        if (faction != expectedFaction) {
            throw IllegalArgumentException("阵营不匹配：分享码是“$faction”，当前为“$expectedFaction”")
        }

        val hex = parts[2].trim()
        if (hex.isBlank()) return emptyList<String>() to faction

        val mask = BigInteger(hex, 16)
        val ids = buildList {
            var bit = 0
            while (bit < mask.bitLength()) {
                if (mask.testBit(bit)) {
                    indexToCardId[bit]?.let { add(it) }
                }
                bit++
            }
        }
        return ids to faction
    }

    fun extractDeckNameFromShareInput(rawInput: String): String? {
        val parts = rawInput.split('｜', '|').map { it.trim() }.filter { it.isNotBlank() }
        return if (parts.size >= 3) parts[parts.size - 2] else null
    }

    fun extractActualShareCode(rawInput: String): String {
        val cleaned = rawInput.trim()
        if (cleaned.isBlank()) return ""
        if ('｜' !in cleaned && '|' !in cleaned) return cleaned
        val parts = cleaned.split('｜', '|').map { it.trim() }.filter { it.isNotBlank() }
        return parts.lastOrNull().orEmpty()
    }
}
