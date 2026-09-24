package com.nekolaska.calabiyau.feature.wiki

import com.nekolaska.calabiyau.feature.wiki.achievement.parser.AchievementParsers
import com.nekolaska.calabiyau.feature.wiki.activity.parser.ActivityParsers
import com.nekolaska.calabiyau.feature.wiki.bgm.parser.BgmParsers
import com.nekolaska.calabiyau.feature.wiki.collaboration.parser.CollaborationParsers
import com.nekolaska.calabiyau.feature.wiki.history.parser.GameHistoryParsers
import com.nekolaska.calabiyau.feature.wiki.imprint.parser.ImprintParsers
import com.nekolaska.calabiyau.feature.wiki.item.parser.ItemCatalogParsers
import com.nekolaska.calabiyau.feature.wiki.map.parser.MapListParsers
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
 * Real-page golden tests: production HTML captured 2026-09-18 from wiki.biligame.com.
 * 快照文件（fixtures/pages 目录下的 HTML）保留在本地但不入 git（避免仓库语言统计被 HTML 淹没）：
 * 文件存在时逐页跑 parser 断言；缺失时（如新 clone / CI）整组跳过。
 *
 * Refresh: re-fetch each page via action=parse&page=<名>&prop=text and replace the file.
 */
class WikiRealPageGoldenTest {

    private fun pageOrNull(name: String): String? {
        val path = java.nio.file.Paths.get("src/test/resources/fixtures/pages", name)
        if (!java.nio.file.Files.exists(path)) return null
        return java.nio.file.Files.readString(path).removePrefix("\uFEFF")
    }

    private fun page(name: String): String {
        val path = java.nio.file.Paths.get("src/test/resources/fixtures/pages", name)
        org.junit.Assume.assumeTrue("page fixture $name not present locally", java.nio.file.Files.exists(path))
        return java.nio.file.Files.readString(path).removePrefix("\uFEFF")
    }

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
        // 分开断言两条解析路径，任一回归都能定位（真实页面两个区块都有）
        assertTrue(history.any { it.entries.isNotEmpty() }, "history entries empty — entry parsing drifted")
        assertTrue(history.any { it.description != null }, "history descriptions empty — description parsing drifted")
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
        // 分开断言时间轴与具体事件两条路径（真实页面两个区块都有）
        assertTrue(c.timelineYears.isNotEmpty(), "collab timeline empty — timeline parsing drifted")
        assertTrue(c.events.isNotEmpty(), "collab events empty — event parsing drifted")
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

    // 梗百科：MemeParsersTest（结构+值锚点）与 LiveWikiSnapshotTest 的本地快照
    // （真实梗百科页，官方 issues 与编辑条目分别断言）已覆盖，不再保留弱化的小夹具用例。

    @Test
    fun oath() {
        val oath = OathParsers.parseHtml(page("oath.html"))
        assertTrue(oath.levels.isNotEmpty(), "oath levels empty")
    }

    @Test
    fun playerLevel() {
        val pl = PlayerLevelParsers.parseHtml(page("playerlevel.html"))
        // 分开断言等级经验与奖励两条路径（真实页面两个区块都有）
        assertTrue(pl.levels.isNotEmpty(), "player levels empty — level parsing drifted")
        assertTrue(pl.rewards.isNotEmpty(), "player rewards empty — reward parsing drifted")
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
        // 战斗模式页：锚定模式入口结构（模式详情走 GameModeParsers 单测）
        val html = page("modes.html")
        assertTrue(html.contains("mw-parser-output"), "modes page structure drifted")
        // 每个模式子页链接形如 /klbq/战斗模式/一般爆破
        val modeLinks = Regex("href=\"/klbq/%E6%88%98%E6%96%97%E6%A8%A1%E5%BC%8F/").findAll(html).count()
        assertTrue(modeLinks >= 10, "mode links=$modeLinks — modes page lost its mode entries")
    }

    @Test
    fun mapListMode() {
        val html = pageOrNull("map_list_mode.html") ?: return
        val maps = MapListParsers.parseMapsFromHtml(html)
        assertTrue(maps.isNotEmpty(), "map cards empty")
        assertTrue(maps.all { it.name.isNotBlank() && it.wikiUrl.startsWith("https://wiki.biligame.com/klbq/") })
        assertTrue(maps.all { it.imageUrl.startsWith("http") }, "image urls missing")
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
