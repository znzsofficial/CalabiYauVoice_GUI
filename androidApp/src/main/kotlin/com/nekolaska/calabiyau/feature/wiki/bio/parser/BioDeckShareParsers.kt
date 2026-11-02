package com.nekolaska.calabiyau.feature.wiki.bio.parser

import com.nekolaska.calabiyau.feature.wiki.bio.model.DeckCardOption
import com.nekolaska.calabiyau.feature.wiki.bio.model.PcCard
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

object BioDeckShareParsers {

    /** The PC catalog can precede the official share index table. Add verified card IDs,
     * never infer a bit position from card order, filename or Lua table keys. */
    fun completeFromPcCatalog(
        shareCards: Map<String, List<DeckCardOption>>, pcCards: List<PcCard>
    ): Map<String, List<DeckCardOption>> {
        val allKnownIds = shareCards.values.flatten().map { it.cardId }.toSet()
        return shareCards.mapValues { (faction, options) ->
            val missing = pcCards.filter {
                it.faction == faction && it.cardId.isNotBlank() && it.cardId !in allKnownIds &&
                    it.rarity in 2..4 && !it.category.contains("天赋") && it.name.isNotBlank() && it.name != "未知卡牌"
            }.distinctBy { it.cardId }.map {
                DeckCardOption(it.name, it.cardId, when (it.rarity) { 4 -> "完美"; 3 -> "卓越"; else -> "精致" },
                    isDefault = it.defaultTag == "默认", index = -1)
            }
            options + missing
        }
    }

    /** Fail closed instead of publishing a different, partially resolved selection. */
    fun resolveSelectedCards(ids: List<String>, options: List<DeckCardOption>): List<DeckCardOption> {
        require(ids.isNotEmpty()) { "至少需要选择 1 张卡牌" }
        val byId = options.filter { it.cardId.isNotBlank() }.associateBy { it.cardId }
        val cards = ids.distinct().map { id ->
            requireNotNull(byId[id]) { "卡牌数据不完整：未知卡牌 $id，请刷新卡牌数据" }
        }
        require(cards.all { it.index in -1 until 4096 }) { "卡牌索引无效，请刷新卡牌数据" }
        val indexed = cards.filter { it.index >= 0 }
        require(indexed.map { it.index }.distinct().size == indexed.size) { "卡牌索引冲突，请刷新卡牌数据" }
        // Share-code validation also bounds indexes before callers issue any mutation.
        return cards
    }

    /** Empty code is allowed only with explicit consent to publish a card-list-only deck. */
    fun publicationShareCode(cards: List<DeckCardOption>, faction: String, allowMissingShareCode: Boolean): String {
        require(cards.isNotEmpty()) { "卡牌不能为空" }
        require(faction in setOf("超弦体", "晶源体")) { "无效阵营" }
        val knownIndexes = cards.filter { it.index >= 0 }.map { it.index }
        require(knownIndexes.distinct().size == knownIndexes.size) { "卡牌索引冲突" }
        require(cards.all { it.index in -1 until 4096 }) { "卡牌索引无效" }
        if (cards.any { it.index == -1 }) {
            require(allowMissingShareCode) { "所选卡牌尚无 Wiki 分享码索引，请选择仅发布卡牌列表或等待卡表更新" }
            return ""
        }
        return BioDeckShareCodecs.encodeShareCode(knownIndexes, faction)
    }

    fun parseFactionCards(arr: JsonArray): List<DeckCardOption> {
        return arr.mapNotNull { element ->
            when (element) {
                is JsonObject -> {
                    val name = element["name"]?.jsonPrimitive?.content.orEmpty().trim()
                    if (name.isBlank()) return@mapNotNull null
                    DeckCardOption(
                        name = name,
                        cardId = element["cardid"]?.jsonPrimitive?.content.orEmpty().trim(),
                        quality = element["quality"]?.jsonPrimitive?.content.orEmpty().trim(),
                        isDefault = element["default"]?.let { boolOf(it) } ?: false,
                        index = element["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
                    )
                }
                else -> null
            }
        }
    }

    private fun boolOf(el: JsonElement): Boolean {
        return el.jsonPrimitive.content.equals("true", ignoreCase = true)
    }
}
