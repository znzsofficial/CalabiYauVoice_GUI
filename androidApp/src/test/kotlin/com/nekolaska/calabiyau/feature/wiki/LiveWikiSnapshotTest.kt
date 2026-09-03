package com.nekolaska.calabiyau.feature.wiki

import com.nekolaska.calabiyau.feature.wiki.achievement.parser.AchievementParsers
import com.nekolaska.calabiyau.feature.wiki.bgm.parser.BgmParsers
import com.nekolaska.calabiyau.feature.wiki.collaboration.parser.CollaborationParsers
import com.nekolaska.calabiyau.feature.wiki.game.model.ModeEntry
import com.nekolaska.calabiyau.feature.wiki.game.parser.GameModeParsers
import com.nekolaska.calabiyau.feature.wiki.history.parser.GameHistoryParsers
import com.nekolaska.calabiyau.feature.wiki.imprint.parser.ImprintParsers
import com.nekolaska.calabiyau.feature.wiki.item.parser.ItemCatalogParsers
import com.nekolaska.calabiyau.feature.wiki.map.parser.MapListParsers
import com.nekolaska.calabiyau.feature.wiki.meme.parser.MemeParsers
import com.nekolaska.calabiyau.feature.wiki.meow.parser.MeowLanguageParsers
import com.nekolaska.calabiyau.feature.wiki.oath.parser.OathParsers
import com.nekolaska.calabiyau.feature.wiki.playerlevel.parser.PlayerLevelParsers
import com.nekolaska.calabiyau.feature.wiki.story.parser.StoryParsers
import com.nekolaska.calabiyau.feature.wiki.stringer.parser.StringerPushCardParsers
import com.nekolaska.calabiyau.feature.wiki.stringer.parser.StringerTalentParsers
import com.nekolaska.calabiyau.feature.wiki.tips.parser.GameTipsParsers
import com.nekolaska.calabiyau.feature.weapon.skin.WeaponSkinFilterApi
import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path

class LiveWikiSnapshotTest {

    @Test
    fun parsesLocalLiveSnapshotsWhenPresent() {
        val items = snapshot("items.html")?.let(ItemCatalogParsers::parseItems)
        if (items != null) {
            assertTrue(items.size > 20, "items=${items.size}")
            assertTrue(items.all { it.name.isNotBlank() })
        }

        val achievements = snapshot("achievements.html")?.let(AchievementParsers::parseHtml)
        if (achievements != null) {
            val count = achievements.sections.sumOf { it.achievements.size }
            assertTrue(count > 20, "achievements=$count")
            assertTrue(achievements.sections.none { it.category == "目录" })
        }

        val imprints = snapshot("imprints.html")?.let(ImprintParsers::parseHtml)
        if (imprints != null) {
            val count = imprints.sections.sumOf { it.imprints.size }
            assertTrue(count > 10, "imprints=$count")
            assertTrue(imprints.sections.none { it.character == "目录" })
        }

        val tips = snapshot("tips.html")?.let(GameTipsParsers::parseSections)
        if (tips != null) {
            assertTrue(tips.isNotEmpty())
            assertTrue(tips.all { it.tips.isNotEmpty() })
        }

        val meow = snapshot("meow.html")?.let(MeowLanguageParsers::parseSections)
        if (meow != null) {
            assertTrue(meow.isNotEmpty())
            assertTrue(meow.any { it.groups.isNotEmpty() })
        }

        val meme = snapshot("meme.html")?.let(MemeParsers::parsePage)
        if (meme != null) {
            assertTrue(meme.officialIssues.isNotEmpty() || meme.editorEntries.isNotEmpty())
        }

        val story = snapshot("story.html")?.let(StoryParsers::parseSections)
        if (story != null) {
            assertTrue(story.any { it.entries.isNotEmpty() })
        }

        val history = snapshot("history.html")?.let(GameHistoryParsers::parseSections)
        if (history != null) {
            assertTrue(history.any { it.entries.isNotEmpty() || it.description != null })
        }

        val collab = snapshot("collab.html")?.let(CollaborationParsers::parsePage)
        if (collab != null) {
            assertTrue(collab.timelineYears.isNotEmpty() || collab.events.isNotEmpty())
        }

        val bgm = snapshot("bgm.html")?.let(BgmParsers::parsePage)
        if (bgm != null) {
            assertTrue(bgm.tracks.isNotEmpty())
        }

        val oath = snapshot("oath.html")?.let(OathParsers::parseHtml)
        if (oath != null) {
            assertTrue(oath.levels.isNotEmpty())
        }

        val playerLevel = snapshot("playerlevel.html")?.let { PlayerLevelParsers.parseHtml(it) }
        if (playerLevel != null) {
            assertTrue(playerLevel.levels.isNotEmpty() || playerLevel.rewards.isNotEmpty())
        }

        val talents = snapshot("stringer_talent.html")?.let(StringerTalentParsers::parseHtml)
        if (talents != null) {
            assertTrue(talents.sections.isNotEmpty())
            assertTrue(talents.sections.all { it.title in setOf("机能", "生存", "续航", "输出") })
        }

        val cards = snapshot("stringer_cards.html")?.let(StringerPushCardParsers::parseHtml)
        if (cards != null) {
            assertTrue(cards.cards.isNotEmpty())
        }

        val skins = snapshot("weapon_skins.html")?.let { WeaponSkinFilterApi.parseWeaponSkinHtml(it) }
        if (skins != null) {
            assertTrue(skins.size > 20, "skins=${skins.size}")
            assertTrue(skins.none { it.name.endsWith("：未知") })
        }

        val maps = snapshot("maps.html")?.let(MapListParsers::parseMapsFromHtml)
        if (maps != null) {
            assertTrue(maps.isNotEmpty())
            assertTrue(maps.all { it.name.isNotBlank() })
        }

        snapshot("modes_ffa.html")?.let { wikitext ->
            val detail = GameModeParsers.parseModeWikitext(ModeEntry("个人乱斗", "战斗模式/个人乱斗"), wikitext, emptyList())
            assertTrue("分类" !in detail.settings)
            assertTrue(detail.summary.isBlank() || !detail.summary.startsWith("|"))
        }
        snapshot("modes_infection.html")?.let { wikitext ->
            val detail = GameModeParsers.parseModeWikitext(ModeEntry("晶源感染", "战斗模式/晶源感染"), wikitext, emptyList())
            assertTrue(detail.summary.isNotBlank())
            assertTrue("|" !in detail.summary)
            assertTrue("分类" !in detail.settings)
        }
        snapshot("modes_hyper.html")?.let { wikitext ->
            val detail = GameModeParsers.parseModeWikitext(ModeEntry("超弦推进", "战斗模式/超弦推进"), wikitext, emptyList())
            assertTrue(detail.summary.isNotBlank())
            assertTrue("class=info" !in detail.summary)
        }
    }

    private fun snapshot(name: String): String? {
        val path = Path.of("C:/Users/NEKOLA~1/AppData/Local/Temp/opencode/live-wiki", name)
        if (!Files.exists(path)) return null
        val text = Files.readString(path)
        return text.takeIf { it.isNotBlank() }
    }
}
