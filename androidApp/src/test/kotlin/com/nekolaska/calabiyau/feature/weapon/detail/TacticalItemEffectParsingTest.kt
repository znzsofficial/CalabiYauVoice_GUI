package com.nekolaska.calabiyau.feature.weapon.detail

import com.nekolaska.calabiyau.feature.weapon.detail.parser.TacticalItemEffectParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TacticalItemEffectParsingTest {
    @Test fun cachedFlashbangListAndSpoilerTemplatesRetainTheirVisibleText() {
        val detail = WeaponDetailApi.parseWeaponWikitext("闪光弹", """
            {{武器-战术道具|效果={{ul|投出后1.6秒引爆。|影响40米范围。|{{黑幕|以摄像机视角为准。}}}}}}
        """.trimIndent())
        assertNotNull(detail)
        assertEquals("• 投出后1.6秒引爆。\n• 影响40米范围。\n• 以摄像机视角为准。", detail.itemEffect)
    }
    @Test fun parsesCapturedItemEffectSeparatelyFromTheIntroduction() {
        val html = checkNotNull(javaClass.getResource("/fixtures/weapons/tactical_item_effect.html"))
            .readText(Charsets.UTF_8)
        val detail = WeaponDetailApi.parseWeaponWikitext(
            "烟雾弹", """{{武器-战术道具|武器介绍=简短介绍|效果=旧数据}}""", renderedHtml = html
        )
        assertNotNull(detail)
        assertEquals("简短介绍", detail.description)
        assertEquals(
            "装备时间 0.6s，拉环时间 0.3s，投掷时间 0.7s，初速 33 m/s\n" +
                "在投出5s或碰撞三次后激活，释放一片阻挡视野的烟雾。\n" +
                "持续时间为8秒，影响半径约5米。\n烟雾效果为空心，边缘不会阻碍摄像机视野。",
            detail.itemEffect
        )
        assertFalse(detail.itemEffect.contains("ITEM EFFECT"))
        assertFalse(detail.itemEffect.contains("瞬爆"))
        assertTrue(detail.cooldowns.isEmpty()) // Parsing must not invoke the network.
    }

    @Test fun fallsBackToTemplateEffectWithoutLeakingWikiSyntax() {
        val detail = WeaponDetailApi.parseWeaponWikitext("测试道具", """
            {{武器-战术道具
            |获得方式=自动获得
            |效果=第一行{{#info:隐藏注释{{黑幕|嵌套说明}}|note}}<br />进入[[烟雾|烟雾区域]]后'''生效'''。
            * 影响范围：5米
            * 持续时间：8秒
            |武器图片=武器-测试.png
            }}
        """.trimIndent())
        assertNotNull(detail)
        assertEquals("第一行\n进入烟雾区域后生效。\n• 影响范围：5米\n• 持续时间：8秒", detail.itemEffect)
        assertEquals("自动获得", detail.obtainMethod)
    }

    @Test fun preservesRenderedParagraphsListsAndEntities() {
        val effect = TacticalItemEffectParser.parse("""
            <div class="klbq-tac-effect__body"><p>投掷后生效。</p>
            <ul><li>范围 &lt; 5m</li><li>持续时间：8秒</li></ul><p>补充说明。</p></div>
        """.trimIndent(), "")
        assertEquals("投掷后生效。\n• 范围 < 5m\n• 持续时间：8秒\n补充说明。", effect)
    }

    @Test fun emptyEffectDoesNotBecomeTheWholePageOrAnError() {
        val detail = WeaponDetailApi.parseWeaponWikitext(
            "测试道具", "{{武器-战术道具|武器介绍=仅介绍|效果=}}",
            renderedHtml = "<div class='klbq-tac-effect__label'>ITEM EFFECT</div><h2>武器外观</h2><p>其它内容</p>"
        )
        assertNotNull(detail)
        assertEquals("", detail.itemEffect)
        assertEquals("仅介绍", detail.description)
    }

    @Test fun blankRenderedEffectFallsBackToOldCachedTemplate() {
        assertEquals("效果内容", TacticalItemEffectParser.parse(
            "<div class='klbq-tac-effect__body'><span class='smwttcontent'>隐藏提示</span></div>", "效果内容"
        ))
    }

    @Test fun ordinaryWeaponIsNotAssignedATacticalEffect() {
        val detail = WeaponDetailApi.parseWeaponWikitext("测试步枪", "{{武器|类型=自动步枪|效果=非战术字段}}",
            renderedHtml = "<div class='klbq-tac-effect__body'>不应显示</div>")
        assertNotNull(detail)
        assertEquals("", detail.itemEffect)
    }

    @Test fun genericTemplateWithTacticalTypeAlsoReadsEffect() {
        val detail = WeaponDetailApi.parseWeaponWikitext("测试道具", "{{武器|武器种类=战术道具|效果=释放烟雾}}")
        assertNotNull(detail)
        assertEquals("释放烟雾", detail.itemEffect)
    }

    @Test fun reusesEmbeddedCooldownTableWithoutMixingItIntoTheEffect() {
        val detail = WeaponDetailApi.parseWeaponWikitext("烟雾弹", "{{武器-战术道具|效果=释放烟雾}}", renderedHtml = """
            <div class="klbq-tac-effect__body">持续8秒。</div>
            <table class="klbqtable"><caption>极限推进模式</caption>
            <tr><th>道具</th><th>冷却时间/秒</th></tr><tr><td>烟雾弹</td><td>45</td></tr>
            <tr><td>其它道具</td><td>20</td></tr></table>
        """.trimIndent())
        assertNotNull(detail)
        assertEquals("持续8秒。", detail.itemEffect)
        assertEquals(mapOf("极限推进模式" to 45), detail.cooldowns)
    }
}
