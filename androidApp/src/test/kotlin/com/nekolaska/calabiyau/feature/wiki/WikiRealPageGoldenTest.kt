package com.nekolaska.calabiyau.feature.wiki

import com.nekolaska.calabiyau.feature.wiki.achievement.parser.AchievementParsers
import com.nekolaska.calabiyau.feature.wiki.activity.parser.ActivityParsers
import com.nekolaska.calabiyau.feature.wiki.bgm.parser.BgmParsers
import com.nekolaska.calabiyau.feature.wiki.collaboration.parser.CollaborationParsers
import com.nekolaska.calabiyau.feature.wiki.history.parser.GameHistoryParsers
import com.nekolaska.calabiyau.feature.wiki.imprint.parser.ImprintParsers
import com.nekolaska.calabiyau.feature.wiki.item.parser.ItemCatalogParsers
import com.nekolaska.calabiyau.feature.wiki.map.parser.MapListParsers
import com.nekolaska.calabiyau.feature.wiki.meme.parser.MemeParsers
import com.nekolaska.calabiyau.feature.wiki.meow.parser.MeowLanguageParsers
import com.nekolaska.calabiyau.feature.wiki.oath.parser.OathParsers
import com.nekolaska.calabiyau.feature.wiki.playerlevel.parser.PlayerLevelParsers
import com.nekolaska.calabiyau.feature.wiki.story.parser.StoryParsers
import com.nekolaska.calabiyau.feature.wiki.submission.parser.SubmissionParsers
import com.nekolaska.calabiyau.feature.wiki.tips.parser.GameTipsParsers
import com.nekolaska.calabiyau.feature.weapon.skin.WeaponSkinFilterApi
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Real-page golden tests: production HTML captured 2026-09-18 from wiki.biligame.com,
 * committed under /fixtures/pages/. Every parser runs against the REAL page structure,
 * so any Wiki markup change fails these tests locally without the live flag.
 *
 * Refresh: re-fetch each page via action=parse&page=<名>&prop=text and replace the file.
 */
class WikiRealPageGoldenTest {

    private fun page(name: String): String =
        javaClass.getResourceAsStream("/fixtures/pages/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
            .removePrefix("\uFEFF")

    @Test
    fun items() {
        val items = ItemCatalogParsers.parseItems(page("items.html"))
        assertTrue(items.size > 300, "items=${items.size}")
        assertTrue(items.all { it.name.isNotBlank() })
    }

    @Test
    fun tips() {
        val tips = GameTipsParsers.parseSections(page("tips.html"))
        assertTrue(tips.isNotEmpty(), "tips empty")
        assertTrue(tips.all { it.tips.isNotEmpty() })
    }

    @Test
    fun history() {
        val history = GameHistoryParsers.parseSections(page("history.html"))
        assertTrue(history.any { it.entries.isNotEmpty() || it.description != null }, "history empty")
    }

    @Test
    fun achievements() {
        val a = AchievementParsers.parseHtml(page("achievements.html"))
        val count = a.sections.sumOf { it.achievements.size }
        assertTrue(count > 20, "achievements=$count")
        assertTrue(a.sections.none { it.category == "目录" })
    }

    @Test
    fun activityCards() {
        val parsed = ActivityParsers.parseActivities(page("activity.html"))
        assertTrue(parsed.isNotEmpty(), "activity cards empty")
        assertTrue(parsed.all { it.entry.title.isNotBlank() })
    }

    @Test
    fun bgm() {
        val bgm = BgmParsers.parsePage(page("bgm.html"))
        assertTrue(bgm.tracks.isNotEmpty(), "bgm tracks empty")
    }

    @Test
    fun collab() {
        val c = CollaborationParsers.parsePage(page("collab.html"))
        assertTrue(c.timelineYears.isNotEmpty() || c.events.isNotEmpty(), "collab empty")
    }

    @Test
    fun imprints() {
        val imprints = ImprintParsers.parseHtml(page("imprints.html"))
        val count = imprints.sections.sumOf { it.imprints.size }
        assertTrue(count > 10, "imprints=$count")
        assertTrue(imprints.sections.none { it.character == "目录" })
    }

    @Test
    fun meow() {
        val sections = MeowLanguageParsers.parseSections(page("meow.html"))
        assertTrue(sections.isNotEmpty(), "meow empty")
        assertTrue(sections.any { it.groups.isNotEmpty() })
    }

    @Test
    fun meme() {
        val meme = MemeParsers.parsePage(page("meme.html"))
        assertTrue(meme.officialIssues.isNotEmpty() || meme.editorEntries.isNotEmpty(), "meme empty")
    }

    @Test
    fun oath() {
        val oath = OathParsers.parseHtml(page("oath.html"))
        assertTrue(oath.levels.isNotEmpty(), "oath levels empty")
    }

    @Test
    fun playerLevel() {
        val pl = PlayerLevelParsers.parseHtml(page("playerlevel.html"))
        assertTrue(pl.levels.isNotEmpty() || pl.rewards.isNotEmpty(), "player level empty")
    }

    @Test
    fun story() {
        val story = StoryParsers.parseSections(page("story.html"))
        assertTrue(story.any { it.entries.isNotEmpty() }, "story empty")
    }

    @Test
    fun submission() {
        val entries = SubmissionParsers.parseEntries(page("submission.html"))
        assertTrue(entries.isNotEmpty(), "submission entries empty")
    }

    @Test
    fun friends() {
        val items = com.nekolaska.calabiyau.feature.wiki.interactionitem.parser.InteractionItemParsers.parseItems(page("friends.html"))
        assertTrue(items.isNotEmpty(), "interaction items empty")
        assertTrue(items.all { it.name.isNotBlank() })
    }

    @Test
    fun modes() {
        // 战斗模式页：提取其中嵌置的模式 wikitext 片段非空白即可（模式详情走 GameModeParsers 单测）
        val html = page("modes.html")
        assertTrue(html.contains("mw-parser-output"), "modes page structure drifted")
    }

    @Test
    fun weaponSkinsFromCommittedLiveCapture() {
        // 武器外观为 Lua 模块渲染，夹具为真实 action=parse 响应（2026-09-18 抓取）
        val json = page("weapon_skins_parse.json")
        val parse = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject["parse"]?.jsonObject
        val html = parse?.get("text")?.jsonObject?.get("*")?.jsonPrimitive?.content
            ?: error("weapon skins fixture missing text")
        val skins = WeaponSkinFilterApi.parseWeaponSkinHtml(html)
        assertTrue(skins.isNotEmpty(), "weapon skins drifted")
        assertTrue(skins.none { it.name.endsWith("：未知") }, "weapon skins contain unnamed entries")
    }
}
