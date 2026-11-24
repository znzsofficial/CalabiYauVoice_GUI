package com.nekolaska.calabiyau.feature.weapon.detail

import com.nekolaska.calabiyau.feature.weapon.detail.parser.WeaponPublishedSections
import java.io.File
import org.jsoup.Jsoup
import org.junit.Assume.assumeNotNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WeaponDefaultAugmentTest {
    // 影袭 120675: {{弦能增幅网络3/武器}} omits 模式; the rendered IDs retain its default.
    private val id = "mc_collapse-爆破模式Parts1Group1Change"
    private val html = """<h2>弦能增幅网络</h2><div class="row growth3"><div class="upgrade-group">
        <div class="upgrade-top"><span class="upgrade-name">输出</span></div><div class="upgrade-options">
        <div class="upgrade-row-weapon" data-target="#$id"><span class="upgrade-text">基础伤害提升</span><span class="upgrade-cost"><img alt="图标-能量点.png">150</span></div>
        <div class="panel-collapse" id="$id"><ul><li>10米头部伤害：34<span>→36</span>（+2）</li><li>50米下肢伤害：12<span>→12</span></li></ul></div>
        </div></div></div>"""
    private val declaration = mapOf("Parts1组1属性变化" to "{{ul|…}}")

    @Test fun onlyAnOmittedParameterUsesTheWikiDefault() {
        val parser = WeaponPublishedSections(Jsoup.parse(html))
        val mode = parser.augmentations(sequenceOf(declaration)).single()
        assertEquals("爆破模式", mode.mode)
        val option = mode.groups.single().options.single()
        assertEquals(150, option.energyCost)
        assertEquals("10米头部伤害：34→36（+2）\n50米下肢伤害：12→12", option.changes)
        for (explicit in listOf("", "极限推进模式")) {
            assertTrue(parser.augmentations(sequenceOf(declaration + ("模式" to explicit))).isEmpty())
        }
        assertTrue(parser.augmentations(sequenceOf(declaration, declaration + ("模式" to "爆破模式"))).isEmpty())
    }

    @Test fun readsAllThreeNovember23RevisionsWhenCaptured() {
        val path = System.getenv("WIKI_ACTIVITY_NOV24_AUDIT_DIR")
        assumeNotNull(path)
        for ((name, damage) in mapOf("影袭" to "25→30（+5）", "雨晦" to "15→18（+3）", "破晓" to "140→160（+20）")) {
            fun read(ext: String) = File(path!!, "$name.$ext").readText(Charsets.UTF_8)
            val detail = assertNotNull(WeaponDetailApi.parseWeaponWikitext(name, read("txt"), renderedHtml = read("html")))
            val mode = detail.augmentations.single()
            assertEquals("爆破模式", mode.mode)
            assertEquals(listOf(2, 2, 2, 2), mode.groups.map { it.options.size })
            val options = mode.groups.flatMap { it.options }
            assertTrue(options.all { it.energyCost == 150 })
            assertTrue("50米头部伤害：$damage" in options.single { it.title == "爆头伤害提升" }.changes.lines())
        }
    }
}
