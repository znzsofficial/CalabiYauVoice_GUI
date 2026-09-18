package com.nekolaska.calabiyau.feature.character.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Uses markup captured from the live 米雪儿·李 page (2026-09-18): the 弦能增幅网络Tabs /
 * 弦能增幅网络3 wikitext templates and the rendered .upgrade-row[data-target] labels.
 */
class AugmentationParsingTest {

    private val wikitext = """
        {{弦能增幅网络Tabs
        |爆破模式=
        {{弦能增幅网络3
        |模式=爆破模式
        |主动技能组1属性变化=伤害：10点/秒{{Color|gold|→15点/秒}}<br />射程：7米{{Color|gold|→10.5米}}
        |主动技能组2属性变化=上限：2个{{Color|gold|→3个}}
        |护甲组1属性变化=护甲回复值：1点/秒{{Color||→80点}}
        |护甲组2属性变化=护甲回复值：1点/秒<br />护甲值上限：80点{{Color|gold|→100点}}（+20）
        |护甲值=80
        |护甲值移动端=45
        |移动速度=55
        }}
        |极限推进模式=
        {{弦能增幅网络3
        |模式=极限推进模式
        |主动技能组1属性变化=伤害：10点/秒{{Color|gold|→15点/秒}}
        |护甲值=80
        }}
        }}弦能增幅网络【爆破模式】【无限团竞模式】完全一致
    """.trimIndent()

    private val renderedHtml = """
        <div class="upgrade-row radius5 upgrade-row-skill" data-target="#mc_collapse-%E7%88%86%E7%A0%B4%E6%A8%A1%E5%BC%8F%E4%B8%BB%E5%8A%A8%E6%8A%80%E8%83%BDGroup1Change" data-toggle="collapse">
          <div class="upgrade-text">技能一：伤害与射程提升</div>
        </div>
        <div class="upgrade-row radius5 upgrade-row-defense" data-target="#mc_collapse-%E7%88%86%E7%A0%B4%E6%A8%A1%E5%BC%8F%E6%8A%A4%E7%94%B2Group2Change" data-toggle="collapse">
          <div class="upgrade-text">护甲上限提升至100点</div>
        </div>
    """.trimIndent()

    @Test
    fun parsesModesEntriesAndRenderedLabels() {
        val modes = CharacterDetailApi.parseAugmentationModes(wikitext, renderedHtml)

        val blast = modes.firstOrNull { it.mode == "爆破模式" }
        assertNotNull(blast)
        assertEquals(4, blast.entries.size)
        // 元数据参数（护甲值/护甲值移动端/移动速度等）不得泄漏为"其他"条目
        assertTrue(blast.entries.none { it.group == "其他" })

        val skill1 = blast.entries.first { it.group == "主动技能" }
        // Color templates stripped, <br> kept as line breaks
        assertEquals("伤害：10点/秒→15点/秒\n射程：7米→10.5米", skill1.value)
        // Rendered .upgrade-text label wins over the derived title
        assertEquals("技能一：伤害与射程提升", skill1.option)

        val armor2 = blast.entries.first { it.option == "护甲上限提升至100点" }
        assertEquals("护甲", armor2.group)
        assertTrue(armor2.value.contains("→100点"))

        // Empty first Color param must not swallow the value
        val armor1 = blast.entries.first { it.value.contains("→80点") }
        assertEquals("护甲", armor1.group)
    }

    @Test
    fun equivalentModeClauseClonesSourceEntries() {
        val modes = CharacterDetailApi.parseAugmentationModes(wikitext, renderedHtml)
        val cloned = modes.firstOrNull { it.mode == "无限团竞模式" }
        assertNotNull(cloned)
        val source = modes.first { it.mode == "爆破模式" }
        assertEquals(source.entries, cloned.entries)
    }

    @Test
    fun missingTabsTemplateYieldsNothing() {
        assertTrue(CharacterDetailApi.parseAugmentationModes("no templates here", renderedHtml).isEmpty())
    }
}
