package com.nekolaska.calabiyau.feature.wiki

import com.nekolaska.calabiyau.feature.wiki.achievement.parser.AchievementParsers
import com.nekolaska.calabiyau.feature.wiki.activity.parser.ActivityParsers
import com.nekolaska.calabiyau.feature.wiki.announcement.parser.AnnouncementParsers
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
import com.nekolaska.calabiyau.feature.weapon.detail.WeaponDetailApi
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.net.HttpURLConnection
import java.net.URI

class LiveWikiSnapshotTest {

    /** Run with LIVE_WIKI_TEST=1 to validate current production HTML, not fixtures. */
    @Test
    fun fetchesAndParsesCurrentWikiHtmlWhenEnabled() {
        org.junit.Assume.assumeTrue("Enable LIVE_WIKI_TEST=1 to fetch production HTML", System.getenv("LIVE_WIKI_TEST") == "1")
        val pages = listOf(
            "items.html" to "功能道具筛选表",
            "tips.html" to "游戏Tips",
            "history.html" to "游戏历史",
            "achievements.html" to "成就",
            "activity.html" to "活动",
            "bgm.html" to "BGM",
            "collab.html" to "联动",
            "imprints.html" to "印迹",
            "meow.html" to "喵言喵语",
            "meme.html" to "梗百科",
            "oath.html" to "誓约",
            "playerlevel.html" to "玩家等级",
            "story.html" to "剧情故事",
            "submission.html" to "投稿作品",
            "friends.html" to "好友",
            "modes.html" to "战斗模式"
        ).map { (file, page) -> file to "/${encoded(page)}" }
        Files.createDirectories(Paths.get("build/live-wiki"))
        pages.forEach { (name, path) ->
            val html = fetchHtml("https://wiki.biligame.com/klbq$path")
            assertTrue(html.contains("<html", ignoreCase = true), "$name did not return HTML")
            assertTrue(html.contains("mw-content-text") || html.contains("mw-parser-output"), "$name has no MediaWiki content")
            Files.writeString(Path.of("build/live-wiki", name), html)
        }
        val items = Files.readString(Path.of("build/live-wiki/items.html")).let(ItemCatalogParsers::parseItems)
        assertTrue(items.isNotEmpty(), "live items parser returned no rows")
        val tips = Files.readString(Path.of("build/live-wiki/tips.html")).let(GameTipsParsers::parseSections)
        assertTrue(tips.isNotEmpty(), "live tips parser returned no sections")
        val history = Files.readString(Path.of("build/live-wiki/history.html")).let(GameHistoryParsers::parseSections)
        assertTrue(history.isNotEmpty(), "live history parser returned no sections")
    }

    /** Weapon skins are rendered from a Lua module through action=parse, not a normal page. */
    @Test
    fun fetchesAndParsesWeaponSkinModuleWhenEnabled() {
        org.junit.Assume.assumeTrue("Enable LIVE_WIKI_TEST=1 to fetch production HTML", System.getenv("LIVE_WIKI_TEST") == "1")
        Files.createDirectories(Paths.get("build/live-wiki"))
        val url = "https://wiki.biligame.com/klbq/api.php?action=parse" +
            "&text=${encoded("{{#invoke:武器|武器外观筛选}}")}&prop=text&format=json"
        val body = fetchBody(url, json = true)
        Files.writeString(Path.of("build/live-wiki/weapon_skins.json"), body)
        val json = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
        val html = json["parse"]?.jsonObject?.get("text")?.jsonObject?.get("*")
            ?.jsonPrimitive?.content
            ?: error("parse response missing text")
        assertTrue(WeaponSkinFilterApi.parseWeaponSkinHtml(html).isNotEmpty(), "weapon skin parser returned no rows")
    }

    /** Live end-to-end: announcements ask API, activity page, and weapon detail (静风) with renamed templates. */
    @Test
    fun fetchesAndParsesAnnouncementActivityWeaponWhenEnabled() {
        org.junit.Assume.assumeTrue("Enable LIVE_WIKI_TEST=1 to fetch production HTML", System.getenv("LIVE_WIKI_TEST") == "1")
        Files.createDirectories(Paths.get("build/live-wiki"))

        val askBody = fetchBody(
            "https://wiki.biligame.com/klbq/api.php?action=ask" +
                "&query=${encoded("[[分类:公告资讯]]|?公告发布时间|?公告B站发布链接|?公告官网发布链接|sort=公告发布时间|order=desc|limit=20")}" +
                "&format=json",
            json = true
        )
        val results = kotlinx.serialization.json.Json.parseToJsonElement(askBody)
            .jsonObject["query"]?.jsonObject?.get("results")
            ?: kotlinx.serialization.json.buildJsonObject { }
        val announcements = AnnouncementParsers.parseAnnouncements(results)
        assertTrue(announcements.isNotEmpty(), "live announcements returned no rows")
        assertTrue(announcements.first().date.isNotBlank(), "live announcement date blank")

        val activityHtml = fetchHtml("https://wiki.biligame.com/klbq/${encoded("活动")}")
        val activities = ActivityParsers.parseActivities(activityHtml)
        assertTrue(activities.isNotEmpty(), "live activities returned no rows")
        assertTrue(activities.any { it.entry.title.isNotBlank() && it.entry.startTime.isNotBlank() },
            "live activity rows missing title/time")

        val weaponBody = fetchBody(
            "https://wiki.biligame.com/klbq/api.php?action=parse" +
                "&page=${encoded("静风")}&prop=wikitext%7Ctext&format=json",
            json = true
        )
        val wjson = kotlinx.serialization.json.Json.parseToJsonElement(weaponBody).jsonObject["parse"]?.jsonObject
            ?: error("weapon parse missing")
        val wikitext = wjson["wikitext"]?.jsonObject?.get("*")?.jsonPrimitive?.content ?: error("missing wikitext")
        val renderedHtml = wjson["text"]?.jsonObject?.get("*")?.jsonPrimitive?.content ?: error("missing html")
        val detail = WeaponDetailApi.parseWeaponWikitext("静风", wikitext, renderedHtml = renderedHtml)
        assertNotNull(detail, "weapon detail parse failed")
        assertTrue(detail.damageTable.isNotEmpty(), "live weapon damage table empty")
        assertTrue(detail.baseDamage.isNotBlank(), "live weapon baseDamage blank")
        assertTrue(detail.user.contains("、") || detail.user.isNotBlank(), "live weapon user blank")

        // 其余三类模板的代表武器：新代主武器 / 近战 / 副武器（战术道具冷却依赖登录态外接口，不在此覆盖）
        for (weapon in listOf("北极星", "大剑", "小蜜蜂")) {
            val body = fetchBody(
                "https://wiki.biligame.com/klbq/api.php?action=parse" +
                    "&page=${encoded(weapon)}&prop=wikitext%7Ctext&format=json",
                json = true
            )
            val p = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["parse"]?.jsonObject
                ?: error("$weapon parse missing")
            val wt = p["wikitext"]?.jsonObject?.get("*")?.jsonPrimitive?.content ?: error("$weapon wikitext missing")
            val html2 = p["text"]?.jsonObject?.get("*")?.jsonPrimitive?.content ?: ""
            val d = WeaponDetailApi.parseWeaponWikitext(weapon, wt, renderedHtml = html2)
            assertNotNull(d, "$weapon detail parse failed")
            // 近战模板没有 类型 参数，不做统一断言；只要求产出可用数据
            assertTrue(
                d.damageTable.isNotEmpty() || d.stringDamage.isNotBlank() || d.description.isNotBlank() || d.obtainMethod.isNotBlank(),
                "$weapon produced no usable data"
            )
        }
    }

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

    private fun encoded(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8)

    private fun fetchBody(url: String, json: Boolean = false): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "CalabiYauVoice-live-test/1.0")
        connection.setRequestProperty(
            "Accept",
            if (json) "application/json" else "text/html,application/xhtml+xml"
        )
        connection.setRequestProperty("Referer", "https://wiki.biligame.com/klbq/")
        try {
            assertTrue(connection.responseCode in 200..299, "HTTP ${connection.responseCode} for $url")
            val expected = if (json) "application/json" else "text/html"
            assertTrue(connection.contentType?.contains(expected, ignoreCase = true) == true,
                "Unexpected content type ${connection.contentType} for $url")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchHtml(url: String): String = fetchBody(url)
}
