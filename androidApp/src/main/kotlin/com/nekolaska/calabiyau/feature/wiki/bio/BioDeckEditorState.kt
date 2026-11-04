package com.nekolaska.calabiyau.feature.wiki.bio

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import com.nekolaska.calabiyau.feature.wiki.bio.model.*
import com.nekolaska.calabiyau.feature.wiki.bio.parser.BioDeckShareCodecs

internal const val MAX_PER_RARITY = 20
internal val DECK_RARITY_THRESHOLDS = mapOf(
    "超弦体" to mapOf("完美" to 10, "卓越" to 15, "精致" to 15),
    "晶源体" to mapOf("完美" to 8, "卓越" to 11, "精致" to 11)
)

/** Small saved draft, independent of network and card artwork. No writes occur during restoration. */
internal class BioDeckEditorState {
    var deckName by mutableStateOf("")
    var author by mutableStateOf("")
    var intro by mutableStateOf("")
    var shareCodeInput by mutableStateOf("")
    var statusText by mutableStateOf("")
    var selectedFaction by mutableStateOf("")
        private set
    val selectableIds = mutableStateListOf<String>()

    fun selectFaction(faction: String) {
        if (selectedFaction == faction) return
        selectedFaction = faction
        selectableIds.clear()
        statusText = ""
    }

    fun reconcileFactions(factions: List<String>) {
        if (factions.isNotEmpty() && selectedFaction !in factions) selectFaction(factions.first())
    }

    fun selection(cards: List<DeckCardOption>): List<String> =
        (selectableIds + cards.filter { it.isDefault }.map { it.cardId }).filter { it.isNotBlank() }.distinct()

    fun rarityCounts(cards: List<DeckCardOption>): Map<String, Int> {
        val byId = cards.associateBy { it.cardId }
        return selection(cards).mapNotNull { byId[it]?.let { card -> normalizeDeckQuality(card.quality) } }.groupingBy { it }.eachCount()
    }

    fun importCode(cards: List<DeckCardOption>) {
        require(selectedFaction.isNotBlank()) { "请先选择阵营" }
        val name = BioDeckShareCodecs.extractDeckNameFromShareInput(shareCodeInput)
        val (ids, _) = BioDeckShareCodecs.decodeShareCode(shareCodeInput, selectedFaction,
            cards.filter { it.cardId.isNotBlank() && it.index >= 0 }.associate { it.index to it.cardId })
        val byId = cards.associateBy { it.cardId }
        selectableIds.clear()
        selectableIds.addAll(ids.filter { byId[it]?.isDefault == false }.distinct())
        if (deckName.isBlank() && !name.isNullOrBlank()) deckName = name
        statusText = "已导入 ${selectableIds.size} 张卡牌"
    }

    fun toggle(card: DeckCardOption, cards: List<DeckCardOption>): String? {
        if (card.isDefault || card.cardId.isBlank() || cards.none { it.cardId == card.cardId }) return "卡牌不可选"
        if (card.cardId in selectableIds) { selectableIds.remove(card.cardId); return null }
        val quality = normalizeDeckQuality(card.quality)
        if (quality in listOf("完美", "卓越", "精致") && (rarityCounts(cards)[quality] ?: 0) >= MAX_PER_RARITY)
            return "$quality 已达到上限（$MAX_PER_RARITY）"
        selectableIds.add(card.cardId)
        return null
    }

    fun payload(cards: List<DeckCardOption>, loggedIn: Boolean, allowMissingShareCode: Boolean): SubmitDeckPayload {
        require(loggedIn) { "请先登录 Wiki" }
        require(deckName.isNotBlank() && selectedFaction.isNotBlank()) { "请填写卡组名称并选择阵营" }
        val byId = cards.associateBy { it.cardId }
        val selected = selection(cards)
        require(selected.isNotEmpty()) { "至少需要选择 1 张卡牌" }
        require(selected.all { it in byId }) { "部分已选卡牌已失效，请重新选择" }
        val counts = rarityCounts(cards)
        val thresholds = DECK_RARITY_THRESHOLDS[selectedFaction].orEmpty()
        require(listOf("完美", "卓越", "精致").all { (counts[it] ?: 0) in (thresholds[it] ?: 0)..MAX_PER_RARITY }) {
            "稀有度条件不满足，无法发布"
        }
        require(allowMissingShareCode || selected.none { byId.getValue(it).index < 0 }) {
            "请确认仅发布卡牌列表，或等待 Wiki 补充分享码索引"
        }
        return SubmitDeckPayload(deckName.trim(), author.trim(), intro.trim(), selectedFaction, selected, allowMissingShareCode)
    }

    fun savedValues(): List<String> = listOf("1", deckName, author, intro, shareCodeInput, selectedFaction) + selectableIds

    companion object {
        fun restore(values: List<String>): BioDeckEditorState? {
            if (values.size < 6 || values.first() != "1") return null
            return BioDeckEditorState().apply {
                deckName = values[1]; author = values[2]; intro = values[3]; shareCodeInput = values[4]
                selectedFaction = values[5]; selectableIds.addAll(values.drop(6).distinct())
            }
        }
        val Saver = listSaver<BioDeckEditorState, String>(save = { it.savedValues() }, restore = ::restore)
    }
}
