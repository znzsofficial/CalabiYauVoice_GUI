package com.nekolaska.calabiyau.feature.weapon.detail

import data.SharedJson
import java.io.File
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/** Optional captured-page audit. Never fetches Wiki; a fresh checkout skips without snapshots. */
class WeaponRealPageAuditTest {
    @Test fun parsesEveryCapturedWeaponAndReportsUnrepresentedSourceData() {
        val directory = System.getenv("WEAPON_AUDIT_DIR")?.let(::File)
        assumeTrue("Set WEAPON_AUDIT_DIR to an existing offline capture directory", directory?.isDirectory == true)
        val dir = directory!!
        val manifest = SharedJson.parseToJsonElement(File(dir, "manifest.json").readText(Charsets.UTF_8)).jsonObject
        val groups = manifest.getValue("groups").jsonObject
        val entries = mutableListOf<JsonObject>()
        val failures = mutableListOf<String>()
        for ((kind, names) in groups) for (name in names.jsonArray.map { it.jsonPrimitive.content }) {
            val file = File(dir, "$name.json")
            if (!file.exists()) {
                failures += "$name: capture missing"
                continue
            }
            val parse = SharedJson.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject["parse"]?.jsonObject
            if (parse == null) {
                entries += buildJsonObject { put("name", name); put("kind", kind); put("missingPage", true) }
                failures += "$name: missing page / not a parser pass"
                continue
            }
            val wikitext = parse["wikitext"]?.jsonObject?.get("*")?.jsonPrimitive?.content.orEmpty()
            val html = parse["text"]?.jsonObject?.get("*")?.jsonPrimitive?.content.orEmpty()
            val document = Jsoup.parse(html)
            val detail = WeaponDetailApi.parseWeaponWikitext(name, wikitext, renderedHtml = html)
            if (detail == null) {
                failures += "$name: weapon template not parsed"
                continue
            }
            val effectBody = document.select(".klbq-tac-effect__body").clone()
            effectBody.select(".smwttcontent, .smwtticon, script, style").remove()
            if (kind == "tactical" && effectBody.text().isNotBlank() && detail.itemEffect.isBlank()) {
                failures += "$name: rendered ITEM EFFECT lost"
            }
            if (kind == "tactical") {
                val cached = WeaponDetailApi.parseWeaponWikitext(name, wikitext)
                val normalize = { text: String -> text.replace(Regex("[\\s•]+"), "") }
                if (normalize(cached?.itemEffect.orEmpty()) != normalize(detail.itemEffect)) {
                    failures += "$name: wikitext-only effect differs from rendered effect"
                }
            }
            if (kind != "tactical" && detail.itemEffect.isNotBlank()) failures += "$name: unexpected tactical effect"
            if (detail.itemEffect.contains("{{") || detail.itemEffect.contains("[[")) failures += "$name: wiki syntax leaked"
            if (kind in setOf("primary", "secondary", "melee") && detail.damageTable.isEmpty() && detail.damageDescription.isBlank()) {
                failures += "$name: no damage rows parsed"
            }
            if (name == "静风" && detail.mechanics.flatMap { it.values }.none { it.label == "爆炸箭冷却时间" && it.value == "10秒/发" }) {
                failures += "$name: infection-mode explosive arrow explanation lost"
            }
            if (name == "雪球" && detail.mechanics.flatMap { it.values }.none { it.label == "蓄力3段" && it.value == "40" }) {
                failures += "$name: maximum charge damage lost"
            }
            entries += buildJsonObject {
                put("name", name)
                put("title", parse["title"]?.jsonPrimitive?.content.orEmpty())
                put("kind", kind)
                put("description", detail.description)
                put("itemEffect", detail.itemEffect)
                putJsonObject("cooldowns") { detail.cooldowns.forEach { (k, v) -> put(k, v) } }
                put("baseDamage", detail.baseDamage)
                put("damageDescription", detail.damageDescription)
                putJsonObject("mobileCoefficients") { detail.mobileDamageCoefficients?.let {
                    put("baseDamage", it.baseDamage); put("head", it.head); put("upper", it.upper); put("lower", it.lower)
                } }
                putJsonArray("performance") { detail.performanceData.forEach { section ->
                    add(buildJsonObject {
                        put("title", section.title)
                        put("notice", section.notice)
                        putJsonObject("values") { section.values.forEach { put(it.label, it.value) } }
                    })
                } }
                putJsonArray("mechanics") { detail.mechanics.forEach { section ->
                    add(buildJsonObject {
                        put("title", section.title)
                        putJsonObject("values") { section.values.forEach { put(it.label, it.value) } }
                    })
                } }
                putJsonArray("damage") { detail.damageTable.forEach {
                    add(buildJsonObject { put("label", it.distance); put("head", it.head); put("upper", it.upper); put("lower", it.lower) })
                } }
                putJsonArray("sourceParameters") {
                    Regex("(?m)^\\s*\\|([^=\\n]+)=").findAll(wikitext).map { it.groupValues[1].trim() }
                        .distinct().forEach { add(it) }
                }
                putJsonArray("sourceHeadings") { document.select(".mw-headline").forEach { add(it.text()) } }
                putJsonArray("tables") {
                    document.select("table.klbqtable").forEach { table ->
                        add(buildJsonObject {
                            put("caption", table.selectFirst("caption")?.text().orEmpty())
                            putJsonArray("headers") {
                                table.select("tr").take(24).forEach { row ->
                                    val cells = row.select("> th, > td")
                                    if (cells.isNotEmpty()) add(cells.first()!!.text())
                                }
                            }
                        })
                    }
                }
            }
        }
        File(dir, "parsed-audit.json").writeText(buildJsonObject {
            putJsonArray("pages") { entries.forEach(::add) }
            putJsonArray("failures") { failures.forEach { add(it) } }
        }.toString(), Charsets.UTF_8)
        assertTrue(entries.isNotEmpty(), "No page captures exercised")
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        println("Captured weapon audit: ${entries.size} entries; see ${File(dir, "parsed-audit.json")}")
    }
}
