package com.nekolaska.calabiyau.feature.settings

import androidx.compose.runtime.saveable.listSaver
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.feature.wiki.hub.QuickEntry
import com.nekolaska.calabiyau.feature.wiki.hub.allQuickEntries
import com.nekolaska.calabiyau.feature.wiki.hub.defaultQuickEntryIds
import com.nekolaska.calabiyau.feature.wiki.hub.quickEntryById
import com.nekolaska.calabiyau.feature.wiki.hub.resolveQuickEntries

/** Only these two values are saved to preferences. Selection, search and scrolling are transient. */
internal data class QuickEntryDraft(val ids: List<String>, val layout: Int) {
    fun choose(slot: Int, candidate: String): QuickEntryDraft {
        if (slot !in ids.indices || candidate !in quickEntryById || ids[slot] == candidate) return this
        val other = ids.indexOf(candidate)
        val next = ids.toMutableList()
        if (other >= 0) next[other] = ids[slot]
        next[slot] = candidate
        return copy(ids = next)
    }

    /** Move rather than swap: all the intervening entries keep their relative order. */
    fun move(from: Int, to: Int): QuickEntryDraft {
        if (from !in ids.indices || to !in ids.indices || from == to) return this
        return copy(ids = ids.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** A draft can survive while the other editor saves. Keep untouched fields from the latest save. */
    fun resolveSave(original: QuickEntryDraft, latest: QuickEntryDraft, overwrite: Boolean = false): QuickEntryDraft? {
        if (!overwrite && ids != original.ids && latest.ids != original.ids && ids != latest.ids) return null
        return QuickEntryDraft(if (ids == original.ids) latest.ids else ids,
            if (layout == original.layout) latest.layout else layout)
    }

    companion object {
        val default get() = QuickEntryDraft(defaultQuickEntryIds, AppPrefs.HOME_QUICK_LAYOUT_GRID)

        fun fromHome(storedIds: List<String>, layout: Int) = QuickEntryDraft(
            storedIds.resolveQuickEntries().map { it.id }, layout
        )

        val Saver = listSaver<QuickEntryDraft, String>(
            save = { listOf(it.layout.toString()) + it.ids },
            restore = { fromHome(it.drop(1), it.first().toInt()) }
        )
    }
}

internal data class QuickEntryCandidate(val entry: QuickEntry, val title: String, val aliases: List<String>)

private data class CandidateText(val id: String, val title: String, val aliases: String = "")

// The home registry owns available IDs, routes, icons and short labels. This index only describes
// how a player finds them in the editor; new home entries remain available without metadata.
private val candidateGroups by lazy {
    val sections = linkedMapOf(
        "对战资料" to listOf(
            CandidateText("characters", "角色", "超弦体 晶源体 人物"),
            CandidateText("weapons", "武器一览", "枪械"),
            CandidateText("maps", "地图", "点位 场景"),
            CandidateText("balance_data", "平衡数据", "胜率 选取率 KD")
        ),
        "玩法与养成" to listOf(
            CandidateText("game_modes", "玩法与养成", "模式"),
            CandidateText("extraction_hub", "弦溯战线", "搜打撤 撤离"),
            CandidateText("bio_cards", "晶源感染卡牌", "生化 卡组"),
            CandidateText("oath", "誓约", "好感 礼物"),
            CandidateText("imprints", "印迹")
        ),
        "外观与道具" to listOf(
            CandidateText("costumes", "角色时装", "皮肤 服装"),
            CandidateText("weapon_skins", "武器外观", "武器皮肤 枪械皮肤"),
            CandidateText("decorations", "玩家装饰", "名片 喷漆 勋章"),
            CandidateText("items", "PC 道具图鉴", "功能道具 物品 端游道具"),
            CandidateText("mobile_items", "移动端道具图鉴", "手游道具 手机道具")
        ),
        "资讯与社区" to listOf(
            CandidateText("announcements", "公告资讯", "更新 新闻"),
            CandidateText("activities", "游戏活动", "限时活动"),
            CandidateText("voting", "投票", "票选"),
            CandidateText("collaborations", "联动", "合作"),
            CandidateText("memes", "梗百科", "社区梗")
        ),
        "影音与导航" to listOf(
            CandidateText("wallpapers", "壁纸", "图片 视觉图"),
            CandidateText("bgm", "原声音乐 BGM", "OST 专辑 歌曲"),
            CandidateText("extension_hub", "游戏延伸", "实用指南 剧情 历史"),
            CandidateText("navigation", "完整导航", "分类 目录")
        )
    )
    val groups = sections.mapValuesTo(linkedMapOf()) { (_, texts) ->
        texts.mapNotNull { text ->
            quickEntryById[text.id]?.let { QuickEntryCandidate(it, text.title, text.aliases.split(' ')) }
        }
    }
    val indexed = groups.values.flatten().mapTo(mutableSetOf()) { it.entry.id }
    val additional = allQuickEntries.filterNot { it.id in indexed }
    if (additional.isNotEmpty()) groups["更多入口"] = additional.map { QuickEntryCandidate(it, it.label, emptyList()) }
    groups
}

internal fun quickEntryCandidates(query: String): Map<String, List<QuickEntryCandidate>> {
    val needle = query.filterNot(Char::isWhitespace)
    if (needle.isEmpty()) return candidateGroups
    return candidateGroups.mapValues { (_, entries) ->
        entries.filter { candidate ->
            (candidate.aliases + candidate.title + candidate.entry.label).any {
                it.filterNot(Char::isWhitespace).contains(needle, ignoreCase = true)
            }
        }
    }.filterValues { it.isNotEmpty() }
}
