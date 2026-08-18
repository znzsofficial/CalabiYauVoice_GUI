package com.nekolaska.calabiyau.feature.wiki.game.parser

import com.nekolaska.calabiyau.feature.wiki.game.model.ModeEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameModeParsersTest {

    @Test
    fun compilesOnClassLoad() {
        assertTrue(GameModeParsers.parseModeMapMapping("").isEmpty())
    }

    @Test
    fun mapsModesFromGroupedLists() {
        val mapping = GameModeParsers.parseModeMapMapping(
            """
            |group1=[[战斗模式/一般爆破|一般爆破]][[战斗模式/排位爆破|排位爆破]]
            |list1=[[404基地]]、[[88区]]
            |group2=[[战斗模式/个人乱斗|个人乱斗]]
            |list2=[[莱特园区]]
            |group3=[[战斗模式/缺失|缺失]]
            """.trimIndent()
        )

        assertEquals(listOf("404基地", "88区"), mapping["一般爆破"])
        assertEquals(listOf("404基地", "88区"), mapping["排位爆破"])
        assertEquals(listOf("莱特园区"), mapping["个人乱斗"])
        assertTrue("缺失" !in mapping)
    }

    @Test
    fun mapsIndentedGroupsAndIgnoresStyleKeys() {
        val mapping = GameModeParsers.parseModeMapMapping(
            """
            |group1style=color: #4b77dc;
            |group1=[[战斗模式/一般爆破|一般爆破]]
            |list1=[[404基地]]
              |group2=[[战斗模式/个人乱斗|个人乱斗]]
              |list2=[[莱特园区]] • [[码头小镇]]
            """.trimIndent()
        )

        assertEquals(listOf("404基地"), mapping["一般爆破"])
        assertEquals(listOf("莱特园区", "码头小镇"), mapping["个人乱斗"])
    }

    @Test
    fun skipsTemplateParamsAndCategoriesForSummary() {
        val infection = parse(
            "晶源感染",
            """
            {{面包屑|战斗模式}}
            {{页顶导航
            |晶源战备=晶源战备
            |战斗模式/晶源感染=模式介绍
            }}
            [[文件:公告.png|x500px|center]]<br />
            本模式开局无固定阵营。

            ==获胜条件==
            * 超弦体方存活到结束
            """
        )
        val hyperstring = parse(
            "超弦推进",
            """
            {{面包屑|战斗模式}}{{#widget:CardSelectGallery}}
            {{信息
            |class=info
            |text=移动端独有内容。
            }}
            超弦推进由极限推进升级而来。

            ==模式设定==
            * 卡片强化
            """
        )
        val ffa = parse(
            "个人乱斗",
            """
            {{面包屑|战斗模式}}
            ==玩法教学==
            <gallery>
            文件:教学.png|个人乱斗等同于单兵作战
            </gallery>
            ==获胜条件==
            * 率先完成20击杀
            [[分类:战斗模式]]
            """
        )

        assertEquals("本模式开局无固定阵营。", infection.summary)
        assertEquals("超弦推进由极限推进升级而来。", hyperstring.summary)
        assertEquals("", ffa.summary)
    }

    @Test
    fun cleansSettingsWithoutTrailingMarkup() {
        val detail = parse(
            "个人乱斗",
            """
            {{面包屑|战斗模式}}
            ==获胜条件==
            * 率先完成20击杀目标即获胜。
            ==模式设定==
            * 模式地图：{{#ask:[[分类:地图]][[支持模式::个人乱斗]]}}
            * 每局15名玩家
            ** 可使用主动技能
            * 仅能使用枪械部分弦能增幅网络
            :[[文件:个人乱斗弦能增幅网络.jpg|600px]]
            {{卡拉彼丘}}
            [[分类:战斗模式]][[分类:移动端内容]]
            """
        )

        assertEquals("• 率先完成20击杀目标即获胜。", detail.winCondition)
        assertEquals(
            """
            • 每局15名玩家
            • 可使用主动技能
            • 仅能使用枪械部分弦能增幅网络
            """.trimIndent(),
            detail.settings
        )
    }

    @Test
    fun unwrapsLinksKeysAndDropsStruckText() {
        val detail = parse(
            "极限刀战",
            """
            {{面包屑|战斗模式}}
            6V6团队竞技，仅可使用近战武器
            ==模式设定==
            * 模式地图：[[莱特园区]]、[[2号仓库]]
            * 仅可使用近战武器，武器可在战斗途中使用{{按键|B}}切换。
            * 可使用{{按键|R}}【格挡】抵御敌人的重击。
            * <s>旧冷却时间1秒。</s>冷却时间为3秒。
            * 更多详见[[战斗模式/排位赛#排位爆破对局补充|排位爆破对局补充]]
            """
        )

        assertEquals("6V6团队竞技，仅可使用近战武器", detail.summary)
        assertEquals(
            """
            • 模式地图：莱特园区、2号仓库
            • 仅可使用近战武器，武器可在战斗途中使用B切换。
            • 可使用R【格挡】抵御敌人的重击。
            • 冷却时间为3秒。
            • 更多详见排位爆破对局补充
            """.trimIndent(),
            detail.settings
        )
    }

    @Test
    fun keepsPassedMapsAndEncodesWikiUrl() {
        val detail = GameModeParsers.parseModeWikitext(
            mode = ModeEntry("排位爆破", "战斗模式/排位爆破"),
            wikitext = "{{面包屑|战斗模式}}\n5V5攻守对阵爆破模式",
            maps = listOf("404基地", "奥卡努斯")
        )

        assertEquals("排位爆破", detail.name)
        assertEquals(listOf("404基地", "奥卡努斯"), detail.maps)
        assertEquals(
            "https://wiki.biligame.com/klbq/%E6%88%98%E6%96%97%E6%A8%A1%E5%BC%8F%2F%E6%8E%92%E4%BD%8D%E7%88%86%E7%A0%B4",
            detail.wikiUrl
        )
    }

    private fun parse(name: String, wikitext: String) =
        GameModeParsers.parseModeWikitext(ModeEntry(name, "战斗模式/$name"), wikitext.trimIndent(), emptyList())
}
