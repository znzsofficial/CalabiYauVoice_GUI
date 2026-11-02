package com.nekolaska.calabiyau.feature.weapon.detail

import com.nekolaska.calabiyau.feature.weapon.detail.parser.WeaponSupplementaryDataParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeaponSupplementaryDataParsingTest {
    @Test fun renderedStatAliasesAndBlankDamageOverridesDoNotDuplicateOrEraseData() {
        val sections = WeaponSupplementaryDataParser.performance("""
            <h2><span class="mw-headline">武器详细数据</span></h2><table class="klbqtable">
            <tr><th>移动时散布增量</th><td>4.0</td></tr><tr><th>静止时散布衰减</th><td>20</td></tr></table>
        """.trimIndent(), mapOf("移动散布增量" to "4.0", "静止散布衰减" to "20"))
        assertEquals(2, sections.sumOf { it.values.size })
        val detail = WeaponDetailApi.parseWeaponWikitext("测试步枪", """
            {{武器|类型=自动步枪|基础伤害=25|10米头部=37|10米上肢=25|10米下肢=17}}
            {{武器详细数据|基础伤害=|10米头部=}}
        """.trimIndent())
        assertNotNull(detail)
        assertEquals("25", detail.baseDamage)
        assertEquals("37", detail.damageTable.single().head)
    }
    @Test fun flamethrowerDamageExplanationIsNotMistakenForAMissingDamageTable() {
        val detail = WeaponDetailApi.parseWeaponWikitext("焚焰者", """
            {{武器-副武器|获得方式=账号等级8级解锁|弹匣容量=30|弦化伤害=135|射速=300
            |伤害说明=对 3D 状态造成 15 点，对弦化状态造成 20 点。开火启动延迟为 0.3 秒；射程为 7.5 米。{{#info:隐藏注释|note}}}}
        """.trimIndent())
        assertNotNull(detail)
        assertEquals("对 3D 状态造成 15 点，对弦化状态造成 20 点。开火启动延迟为 0.3 秒；射程为 7.5 米。", detail.damageDescription)
        assertTrue(detail.damageTable.isEmpty()) // No invented distance/body rows.
        assertEquals("300", detail.fireRate)
    }

    @Test fun mobileCoefficientTemplateParametersNeverReusePcValues() {
        val detail = WeaponDetailApi.parseWeaponWikitext("北极星", """
            {{武器|类型=自动步枪|基础伤害=25.0|头部倍率=1.5|基础伤害移动端=26.0
            |头部倍率移动端=1.4|上肢倍率移动端=1.0|下肢倍率移动端=0.7}}
        """.trimIndent())
        assertNotNull(detail)
        assertEquals("25.0", detail.baseDamage)
        assertEquals("1.5", detail.headMultiplier)
        val mobile = assertNotNull(detail.mobileDamageCoefficients)
        assertEquals("26.0", mobile.baseDamage)
        assertEquals("1.4", mobile.head)
    }

    @Test fun mobileCoefficientTableIsIsolatedAndMissingMobileIsNotInvented() {
        val detail = WeaponDetailApi.parseWeaponWikitext("副武器", "{{武器-副武器|获得方式=自动获得}}", renderedHtml = """
            <table class="klbqtable"><tr><th colspan="6">武器部位伤害系数</th></tr>
            <tr><th>基础伤害</th><td>8.5</td></tr><tr><th>头部</th><td>1.0</td><th>上肢</th><td>1.0</td><th>下肢</th><td>1.0</td></tr></table>
            <table class="klbqtable"><tr><th colspan="6">武器部位伤害系数（移动端）</th></tr>
            <tr><th>基础伤害</th><td>7.0</td></tr><tr><th>头部</th><td>1.25</td><th>上肢</th><td>1.0</td><th>下肢</th><td>0.7</td></tr></table>
        """.trimIndent())
        assertNotNull(detail)
        assertEquals("8.5", detail.baseDamage)
        assertEquals("7.0", detail.mobileDamageCoefficients?.baseDamage)
        assertEquals("1.25", detail.mobileDamageCoefficients?.head)
        val pcOnly = WeaponDetailApi.parseWeaponWikitext("步枪", "{{武器|类型=自动步枪|基础伤害=30}}")
        assertNotNull(pcOnly)
        assertNull(pcOnly.mobileDamageCoefficients)
    }
    @Test fun snowballChargeLevelsStayUnderItemDataNotTheEffectOrHistory() {
        val detail = WeaponDetailApi.parseWeaponWikitext("雪球", "{{武器-战术道具|效果=冰冻时间为5秒}}", renderedHtml = """
            <h2><span class="mw-headline">道具数据</span></h2>
            <dl><dt>伤害：</dt></dl><ul><li>不蓄力：10</li><li>蓄力1段：20</li><li>蓄力2段：30</li><li>蓄力3段：40</li></ul>
            <h2><span class="mw-headline">更新改动历史</span></h2><ul><li>蓄力3段：999</li></ul>
        """.trimIndent())
        assertNotNull(detail)
        assertEquals("冰冻时间为5秒", detail.itemEffect)
        assertEquals(listOf("10", "20", "30", "40"), detail.mechanics.single().values.map { it.value })
    }
    @Test fun detailedTemplateFallbackKeepsPerformanceButNotDamageRows() {
        val detail = WeaponDetailApi.parseWeaponWikitext("北极星", """
            {{武器|类型=自动步枪|基础伤害=25}}
            {{武器详细数据|单发间隔=0.09|快速换弹=2.25|空仓换弹=2.45|最小散布=0.3|最大散布=1.95
            |水平后坐力上限=8.5|静止散布衰减=|10米头部=37}}
        """.trimIndent())
        assertNotNull(detail)
        val stats = detail.performanceData.flatMap { it.values }.associate { it.label to it.value }
        assertEquals("0.09", stats["单发间隔"])
        assertEquals("2.45", stats["空仓换弹"])
        assertEquals("8.5", stats["水平后坐力上限"])
        assertTrue("静止散布衰减" !in stats && "10米头部" !in stats)
    }

    @Test fun renderedUnitsWinAndTablesDoNotLeakFromPrototypeOrHistory() {
        val sections = WeaponSupplementaryDataParser.performance("""
            <h2><span class="mw-headline">武器详细数据</span></h2>
            <div><table class="klbqtable"><caption>换弹</caption>
            <tr><th>快速换弹时间</th><td>2.25 秒</td></tr>
            <tr><th>空仓换弹时间</th><td>2.45 秒</td></tr></table></div>
            <h2><span class="mw-headline">武器原型</span></h2>
            <table class="klbqtable"><tr><th>射速</th><td>9999</td></tr></table>
            <h2><span class="mw-headline">更新改动历史</span></h2>
            <table class="klbqtable"><tr><th>快速换弹</th><td>旧数据</td></tr></table>
        """.trimIndent(), mapOf("快速换弹" to "2.25", "空仓换弹" to "2.45"))
        assertEquals(1, sections.size)
        assertEquals(listOf("2.25 秒", "2.45 秒"), sections.single().values.map { it.value })
    }

    @Test fun mechanicsPreserveSeparateModeContextAndAvoidAugmentationHistory() {
        val sections = WeaponSupplementaryDataParser.mechanics("""
            <h2><span class="mw-headline">武器</span></h2>
            <div class="hero"><ul><li>武器菜单：不应收录</li></ul></div>
            <ul><li>弹速：510米/秒</li><li>爆炸箭冷却时间：35秒/发</li></ul>
            <dl><dt>晶源感染模式：</dt></dl>
            <ul><li>爆炸箭冷却时间：10秒/发</li><li>爆炸箭伤害：105+450~225</li></ul>
            <h2><span class="mw-headline">弦能增幅网络</span></h2>
            <ul><li>爆炸箭冷却时间：升级值</li></ul>
            <h2><span class="mw-headline">更新改动历史</span></h2>
            <ul><li>爆炸箭冷却时间：12秒/发</li></ul>
        """.trimIndent())
        assertEquals(listOf("武器机制", "晶源感染模式"), sections.map { it.title })
        assertEquals("35秒/发", sections[0].values[1].value)
        assertEquals("10秒/发", sections[1].values[0].value)
        assertTrue(sections.flatMap { it.values }.none { it.value.contains("不应") || it.value.contains("升级值") || it.value == "12秒/发" })
    }

    @Test fun keepsAiDerivedDetailExplicitlyMarkedEvenInTemplateFallback() {
        val sections = WeaponSupplementaryDataParser.performance("""
            <h2><span class="mw-headline">武器详细数据</span></h2>
            <div class="alert">以下内容使用 AI 分析游戏数据获得，仅供参考</div>
            <table class="klbqtable"><tr><th>单发间隔</th><td>不可靠值</td></tr></table>
        """.trimIndent(), mapOf("最大散布" to "2.0"))
        assertEquals(2, sections.size)
        assertTrue(sections.all { it.notice.contains("AI") && it.notice.contains("仅供参考") })
    }

    @Test fun chargeAndMobileScoresRemainIndependent() {
        val detail = WeaponDetailApi.parseWeaponWikitext("谢幕曲", """
            {{武器|类型=狙击步枪|蓄力速度=48|蓄力速度移动端=60|瞄准速度=42|瞄准速度移动端=38
            |弦化伤害=67|弦化伤害移动端=60}}
        """.trimIndent())
        assertNotNull(detail)
        assertEquals("48", detail.extraStats["蓄力速度"])
        assertEquals("60", detail.extraStats["蓄力速度移动端"])
        assertEquals("38", detail.extraStats["瞄准速度移动端"])
        assertEquals("60", detail.extraStats["弦化伤害移动端"])
        assertEquals("67", detail.stringDamage)
    }
}
