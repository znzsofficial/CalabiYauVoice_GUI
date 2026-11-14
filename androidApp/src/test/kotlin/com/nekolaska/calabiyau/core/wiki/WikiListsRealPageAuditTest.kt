package com.nekolaska.calabiyau.core.wiki

import com.nekolaska.calabiyau.feature.character.list.CharacterListApi
import com.nekolaska.calabiyau.feature.character.list.CharacterListParser
import com.nekolaska.calabiyau.feature.weapon.list.WeaponListApi
import com.nekolaska.calabiyau.feature.weapon.list.WeaponListSource
import com.nekolaska.calabiyau.feature.wiki.game.parser.GameModeParsers
import com.nekolaska.calabiyau.feature.wiki.map.parser.MapListParsers
import data.SharedJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

/** Full November 14 responses, never network access. See docs/wiki-list-resilience.md. */
class WikiListsRealPageAuditTest {
    private fun directory(): File {
        val path = System.getenv("WIKI_LIST_AUDIT_DIR")
        assumeTrue("Set WIKI_LIST_AUDIT_DIR to the complete capture directory", !path.isNullOrBlank())
        return File(requireNotNull(path)).also { assertTrue(it.isDirectory, "Missing audit directory: $it") }
    }

    private fun read(directory: File, stem: String): String {
        val file = File(directory, "$stem.json")
        assertTrue(file.isFile, "Missing captured response: $file")
        return file.readText(Charsets.UTF_8)
    }

    @Test fun navigationRevision119887KeepsEveryDeclaredModeAndMap() {
        val root = SharedJson.parseToJsonElement(read(directory(), "navigation")).jsonObject
        val parse = root.getValue("parse").jsonObject
        assertEquals("119887", parse.getValue("revid").jsonPrimitive.content)
        val text = parse.getValue("wikitext").jsonObject.getValue("*").jsonPrimitive.content
        val demolition = listOf("404基地", "88区", "风曳镇", "欧拉港口", "空间实验室", "柯西街区", "科斯迷特", "奥卡努斯", "莱布伦城")
        val expected = mapOf(
            "一般爆破" to demolition,
            "团队乱斗" to demolition,
            "无限团竞" to listOf("莱特园区", "2号仓库", "花明弓道馆", "鹿鹿潮玩区", "水下发电厂", "码头小镇"),
            "极限推进" to listOf("离境区", "亚克萨工厂", "簌雪东区", "泰芙绿洲", "厄瑞洞窟", "熔岩工厂"),
            "晶源感染" to listOf("迦纳古城", "洛希街区", "极地研究所", "欧拉夜港", "猩红废墟", "虚空残岸"),
            "极限刀战" to listOf("莱特园区", "2号仓库", "码头小镇", "花明弓道馆"),
            "弦区争夺" to listOf("88区", "风曳镇", "欧拉港口"),
            "大头乱斗" to listOf("88区", "风曳镇", "空间实验室"),
            "枪王乱斗" to listOf("404基地", "88区", "风曳镇", "柯西街区", "莱布伦城"),
            "晶能冲突" to listOf("狄拉克矿区"),
            "弦溯战线" to listOf("维里昂裂隙")
        )
        // Four other application modes are genuinely absent from this navbox, not inferred aliases.
        assertEquals(expected, GameModeParsers.parseModeMapMapping(text))
    }

    @Test fun fourIndividualFactionTemplatesKeepAll29ArticleIdentitiesAndArtwork() {
        val directory = directory()
        val expected = linkedMapOf(
            "欧泊" to listOf("米雪儿·李", "信", "心夏", "伊薇特", "芙拉薇娅", "忧雾", "蕾欧娜", "千代"),
            "剪刀手" to listOf("明", "拉薇", "梅瑞狄斯", "香奈美", "令", "艾卡", "珐格兰丝", "玛拉", "诺诺"),
            "乌尔比诺" to listOf("星绘", "奥黛丽·格罗夫", "白墨", "玛德蕾娜·利里", "绯莎", "加拉蒂亚·利里", "汐"),
            "晶源体" to listOf("爆裂魔怪", "刺镰魔怪", "冥荆皇女", "血荆皇女", "莉莉丝")
        )
        assertEquals(CharacterListApi.FACTIONS, expected.keys.toList())
        val names = expected.flatMap { (faction, identities) ->
            val payload = read(directory, "faction-$faction")
            assertTrue(CharacterListParser.isValidPayload(payload), faction)
            val characters = CharacterListParser.parse(assertNotNull(wikiListHtml(payload)))
            assertEquals(identities, characters.map { it.name }, faction)
            assertTrue(characters.all { wikiArticleTitle(it.wikiUrl) == it.name }, faction)
            assertTrue(characters.all { it.imageUrl.startsWith("https://patchwiki.biligame.com/") && "/thumb/" !in it.imageUrl }, faction)
            characters.map { it.name }
        }
        assertEquals(29, names.distinct().size)
    }

    @Test fun representativeMapTemplatesKeepExactIdentitiesIncludingModeAbsentFromNavbox() {
        val directory = directory()
        val expected = mapOf(
            "一般爆破" to listOf("莱布伦城", "奥卡努斯", "科斯迷特", "柯西街区", "空间实验室", "风曳镇", "88区", "欧拉港口", "404基地"),
            "个人乱斗" to listOf("空间实验室", "风曳镇", "88区", "欧拉港口", "404基地"),
            "弦溯战线" to listOf("维里昂裂隙")
        )
        expected.forEach { (mode, identities) ->
            val payload = read(directory, "map-$mode")
            assertTrue(MapListParsers.isValidPayload(payload), mode)
            val maps = MapListParsers.parseMapsFromHtml(assertNotNull(wikiListHtml(payload)))
            assertEquals(identities, maps.map { it.name }, mode)
            assertTrue(maps.all { wikiArticleTitle(it.wikiUrl) == it.name }, mode)
            assertTrue(maps.all { it.imageUrl.startsWith("https://patchwiki.biligame.com/") && "/thumb/" !in it.imageUrl }, mode)
        }
    }

    @Test fun allFourAskChainsReplayThroughProductionAssemblerAndParser() = runBlocking {
        val directory = directory()
        val expected = mapOf(
            "主武器" to setOf("北极星", "卫冕", "夜镰", "审判官", "幻霜", "彩绘", "影袭", "枫鸣", "校准仪", "欺诈师", "潮音", "独舞", "破晓", "空境", "绝对执行", "绽放", "自由意志", "警探", "谢幕曲", "逆焰", "隼", "雨晦", "静风", "鸣火", "齿锋"),
            "近战武器" to setOf("大剑", "忍锋", "战镰"),
            "副武器" to setOf("小蜜蜂", "焚焰者", "重焰", "雪鸮"),
            "战术道具" to setOf("减速雷", "拦截者", "治疗雷", "烟雾弹", "破片手雷", "警报器", "闪光弹", "防弹屏障", "雪球", "风场雷")
        )
        assertEquals(WeaponListApi.WeaponCategory.entries.map { it.smwCategory }.toSet(), expected.keys)
        expected.forEach { (category, identities) ->
            val offsets = mutableListOf<Int>()
            val assembled = assertNotNull(WeaponListSource.fetchAll { offset ->
                offsets += offset
                read(directory, "weapon-$category-$offset")
            })
            // All four live responses terminate at offset 0; synthetic continuation tests remain separate.
            assertEquals(listOf(0), offsets, category)
            assertTrue(WeaponListSource.isValidPayload(assembled), category)
            val weapons = WeaponListApi.parseWeapons(assembled)
            assertEquals(identities, weapons.map { it.name }.toSet(), category)
            assertEquals(identities.size, weapons.size, category)
            assertTrue(weapons.all { wikiArticleTitle(it.wikiUrl) == it.name }, category)
            assertTrue(weapons.all { it.imageUrl == null }, category)
            if (category == "主武器") {
                val quietWind = weapons.single { it.name == "静风" }
                assertEquals(19, quietWind.users.size)
                assertEquals("精确射手步枪", quietWind.type)
                assertEquals("", quietWind.description) // The upstream ask printout is empty, not a parse failure.
                assertEquals(listOf("星绘"), weapons.single { it.name == "北极星" }.users)
            }
            if (category == "副武器") {
                assertTrue(weapons.all { it.description.isNotBlank() && it.users.isEmpty() && it.type.isEmpty() })
            }
        }
    }
}
