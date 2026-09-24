package com.nekolaska.calabiyau.feature.character.detail

import com.nekolaska.calabiyau.feature.character.detail.CharacterDetailApi.CharacterDetail
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Live-audit: runs the full character parser against real action=parse
 * captures (wikitext + rendered HTML). Files live under build/char-audit/
 * (local-only, like the real-page golden fixtures) — the whole test skips
 * when they are absent (fresh clone / CI).
 *
 * Capture: api.php?action=parse&page=<名>&prop=wikitext|text (2026-09-23).
 */
class CharacterDetailLiveAuditTest {

    private fun captureOrNull(name: String): Pair<String, String>? {
        val path = java.nio.file.Paths.get("build/char-audit", "$name.json")
        if (!java.nio.file.Files.exists(path)) return null
        val json = SharedJson.parseToJsonElement(
            java.nio.file.Files.readString(path).removePrefix("\uFEFF")
        ).jsonObject["parse"]!!.jsonObject
        val wikitext = json["wikitext"]!!.jsonPrimitive.content
        val html = json["text"]!!.jsonPrimitive.content
        return wikitext to html
    }

    private fun audit(name: String, block: (CharacterDetail) -> Unit) {
        val (wikitext, html) = captureOrNull(name) ?: return
        val detail = CharacterDetailApi.parseCharacterWikitext(name, wikitext, html)
        assertNotNull(detail, "$name: parseCharacterWikitext returned null")
        block(detail)
    }

    @Test
    fun auditSuperstringMichelle() = audit("米雪儿·李") { d ->
        assertEquals("米雪儿·李", d.name)
        assertEquals("欧泊", d.faction)
        assertTrue(d.role in setOf("决斗", "守护", "支援", "突击", "先锋", "控场"), "role=${d.role}")
        assertTrue(d.summary.isNotBlank(), "summary blank")
        assertTrue(d.quote.isNotBlank(), "quote blank")
        assertTrue(d.weaponName.isNotBlank(), "weaponName blank")
        assertTrue(d.cnVoiceActor.isNotBlank(), "cn voice actor blank: '${d.cnVoiceActor}'")
        assertEquals(4, d.skills.size, "skills=${d.skills.map { it.slot to it.name }}")
        d.skills.forEach { assertTrue(it.description.isNotBlank(), "skill ${it.slot} desc blank") }
        // 技能名应来自渲染页（如 喵喵卫士），而非 Q/P/X/E 占位
        assertTrue(d.skills.none { it.name in setOf("技能1", "技能2", "技能3", "技能4") },
            "skill names still placeholders: ${d.skills.map { it.name }}")
        // 数值组：喵喵卫士应有 模式/冷却时间 等数值组
        assertTrue(d.skills.any { it.valueGroups.isNotEmpty() },
            "no value groups parsed: ${d.skills.map { it.slot to it.valueGroups.size }}")
        assertTrue(d.augmentationModes.isNotEmpty(), "augmentation modes empty")
        // 线上确认：显式双模式（爆破/极限推进）+ 页面声明"完全一致"克隆出的弦区争夺模式
        assertEquals(
            listOf("爆破模式", "极限推进模式", "弦区争夺模式"),
            d.augmentationModes.map { it.mode },
            "modes=${d.augmentationModes.map { it.mode }}"
        )
        d.augmentationModes.forEach { mode ->
            assertTrue(mode.entries.isNotEmpty(), "mode ${mode.mode} has no entries")
        }
        assertTrue(d.stories.isNotEmpty(), "stories empty")
        // 线上确认：1 个角色故事 + 3 个相关剧情条目
        assertEquals(
            listOf("米雪儿·李：《超级玩家》", "玩玩闹闹沐春大聚餐", "同沐春风共此时", "甜梦游乐园"),
            d.stories.map { it.title },
            "stories=${d.stories.map { it.title }}"
        )
        assertTrue(d.subPages.isNotEmpty(), "subPages empty")
    }

    @Test
    fun auditSuperstringXilai() = audit("汐") { d ->
        assertEquals("乌尔比诺", d.faction)
        assertTrue(d.role in setOf("决斗", "守护", "支援", "突击", "先锋", "控场"), "role=${d.role}")
        assertEquals(4, d.skills.size, "skills=${d.skills.map { it.slot to it.name }}")
        assertTrue(d.skills.any { it.valueGroups.isNotEmpty() },
            "no value groups: ${d.skills.map { it.slot to it.valueGroups.size }}")
        // 线上确认：显式双模式 + 弦区争夺模式（完全一致克隆）
        assertEquals(
            listOf("爆破模式", "极限推进模式", "弦区争夺模式"),
            d.augmentationModes.map { it.mode },
            "modes=${d.augmentationModes.map { it.mode }}"
        )
        d.augmentationModes.forEach { mode ->
            assertTrue(mode.entries.isNotEmpty(), "mode ${mode.mode} has no entries")
        }
        assertTrue(d.stories.isNotEmpty(), "stories empty")
    }

    @Test
    fun auditCrystalLilith() = audit("莉莉丝") { d ->
        assertEquals("晶源体", d.faction)
        assertEquals("晶源体", d.role)
        assertEquals(2, d.skills.size, "skills=${d.skills.map { it.slot to it.name }}")
        assertEquals(setOf("Q", "X"), d.skills.map { it.slot }.toSet())
        d.skills.forEach { assertTrue(it.description.isNotBlank(), "crystal skill ${it.slot} desc blank") }
        // 晶源体声优：中文/日文拆分
        assertTrue(d.cnVoiceActor.isNotBlank() || d.jpVoiceActor.isNotBlank(),
            "voice actors blank: cn='${d.cnVoiceActor}' jp='${d.jpVoiceActor}'")
    }
}
