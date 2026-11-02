package com.nekolaska.calabiyau.feature.weapon.detail

import data.SharedJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Small, tracked effect excerpts from all ten tactical pages captured 2026-11-02. */
@RunWith(Parameterized::class)
class TacticalCapturedEffectsTest(private val name: String, private val effect: String, private val anchors: List<String>) {
    @Test fun wikitextOnlyCachePreservesTheGameplayFactsWithoutMarkup() {
        val detail = WeaponDetailApi.parseWeaponWikitext(name, "{{武器-战术道具|武器名称=$name|效果=$effect}}")
        assertNotNull(detail)
        anchors.forEach { assertTrue(detail.itemEffect.contains(it), "$name lost $it: ${detail.itemEffect}") }
        assertFalse(detail.itemEffect.contains("{{"), detail.itemEffect)
        assertFalse(detail.itemEffect.contains("'''"), detail.itemEffect)
        assertFalse(detail.itemEffect.contains("#info"), detail.itemEffect)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun pages(): Collection<Array<Any>> {
            val fixture = checkNotNull(TacticalCapturedEffectsTest::class.java.getResource("/fixtures/weapons/tactical_effects_20261102.json"))
            return SharedJson.parseToJsonElement(fixture.readText(Charsets.UTF_8)).jsonArray.map {
                val row = it.jsonObject
                arrayOf(row.getValue("name").jsonPrimitive.content, row.getValue("effect").jsonPrimitive.content,
                    row.getValue("anchors").jsonArray.map { anchor -> anchor.jsonPrimitive.content })
            }
        }
    }
}
