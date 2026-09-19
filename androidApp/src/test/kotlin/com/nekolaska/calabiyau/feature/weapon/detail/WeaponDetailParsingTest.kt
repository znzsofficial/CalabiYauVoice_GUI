package com.nekolaska.calabiyau.feature.weapon.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Uses structure captured from the live 静风 page (2026-09-19):
 * {{武器伤害}} 模板与章节已改名 {{武器详细数据}} / ==武器详细数据==，
 * 距离伤害与基础伤害参数移入主 {{武器}} 模板。
 */
class WeaponDetailParsingTest {

    private val wikitext = """
        {{武器
        |使用者=米雪儿·李、信、心夏、芙拉薇娅
        |类型=精确射手步枪
        |10米头部=53
        |10米上肢=38
        |10米下肢=26
        |20米头部=50
        |20米上肢=35
        |20米下肢=25
        |基础伤害=38.0
        |头部倍率=1.4
        |上肢倍率=1.0
        |下肢倍率=0.7
        }}
        {{武器详细数据
        |单发间隔=0.153846 秒/发
        |最小散布=0.3
        }}
        ==武器详细数据==
        <table class="klbqtable">
          <caption>未开启弦化时的移速</caption>
          <tr><th>移动状态</th><td>6米/秒</td></tr>
        </table>
    """.trimIndent()

    @Test
    fun parsesRenamedDetailTemplateAndMainTemplateDistances() {
        val detail = WeaponDetailApi.parseWeaponWikitext(
            "静风",
            wikitext,
            renderedHtml = wikitext.substringAfter("==武器详细数据==")
        )

        assertNotNull(detail)
        assertEquals("精确射手步枪", detail.type)
        // 多名使用者原样保留
        assertTrue(detail.user.contains("米雪儿·李") && detail.user.contains("芙拉薇娅"))
        // 距离伤害回退到主模板参数
        val d10 = detail.damageTable.first { it.distance == "10米" }
        assertEquals("53", d10.head)
        assertEquals("38", d10.upper)
        assertEquals("26", d10.lower)
        assertEquals("50", detail.damageTable.first { it.distance == "20米" }.head)
        // 基础伤害回退到主模板参数
        assertEquals("38.0", detail.baseDamage)
        assertEquals("1.4", detail.headMultiplier)
    }

    @Test
    fun parsesRenamedDamageSectionFromHtml() {
        val detail = WeaponDetailApi.parseWeaponWikitext(
            "静风",
            wikitext,
            renderedHtml = """
                <h2><span class="mw-headline" id="武器详细数据">武器详细数据</span></h2>
                <table class="klbqtable">
                  <caption>未开启弦化时的移速</caption>
                  <tr><th>移动状态</th><td>6米/秒</td></tr>
                  <tr><th>弦化状态</th><td>5米/秒</td></tr>
                </table>
            """.trimIndent()
        )

        assertNotNull(detail)
        // 章节改名后 key-value 表仍会进入伤害/数据表（distance 带 caption 前缀是既有行为）
        assertTrue(
            detail.damageTable.any { it.head == "6米/秒" },
            "damageTable=${detail.damageTable}"
        )
    }

    @Test
    fun legacyDamageTemplateStillWinsWhenPresent() {
        val legacy = """
            {{武器
            |使用者=星绘
            |类型=自动步枪
            |基础伤害=30
            }}
            {{武器伤害
            |10米头部=40
            |10米上肢=30
            |10米下肢=25
            |基础伤害=31.0
            |头部倍率=1.5
            }}
        """.trimIndent()

        val detail = WeaponDetailApi.parseWeaponWikitext("某武器", legacy)
        assertNotNull(detail)
        // 旧 {{武器伤害}} 存在时其参数优先
        assertEquals("40", detail.damageTable.first { it.distance == "10米" }.head)
        assertEquals("31.0", detail.baseDamage)
    }
}
