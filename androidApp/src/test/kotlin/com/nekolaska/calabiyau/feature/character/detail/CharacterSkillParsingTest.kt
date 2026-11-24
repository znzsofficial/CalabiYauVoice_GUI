package com.nekolaska.calabiyau.feature.character.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CharacterSkillParsingTest {
    @Test fun renderedSkillIconUsesLiteralCharacterNameAndStopsAtTheNextHeading() {
        val name = "A[1]+"
        val html = """
            <h2><span id="角色技能">角色技能</span></h2>
            <div><img alt="A11技能1.png"><b>干扰项</b><p>不应识别</p></div>
            <section><div><img alt="a[1]+技能1.WEBP"><b>技能名称</b><p>第一行</p><p>第二行</p></div></section>
            <h3>更新记录</h3><div><img alt="A[1]+技能2.png"><b>其它标题</b><p>其它段落</p></div>
        """.trimIndent()
        val detail = assertNotNull(CharacterDetailApi.parseCharacterWikitext(name, "{{超弦体}}{{角色技能}}", html))
        val skill = detail.skills.single()
        assertEquals("Q", skill.slot)
        assertEquals("技能名称", skill.name)
        assertEquals("第一行\n第二行", skill.description)
    }
}
