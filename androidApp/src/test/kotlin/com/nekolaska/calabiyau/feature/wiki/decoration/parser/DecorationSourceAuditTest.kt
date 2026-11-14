package com.nekolaska.calabiyau.feature.wiki.decoration.parser

import com.nekolaska.calabiyau.feature.wiki.decoration.api.DecorationAssembler
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogSource
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogs
import java.io.File
import java.net.URLDecoder
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** All existing captures, offline only. Fresh clones skip; explicitly configured incomplete directories fail. */
class DecorationSourceAuditTest {
    private fun directory(): File {
        val path = System.getenv("DECORATION_SOURCE_AUDIT_DIR")
        assumeTrue("Set DECORATION_SOURCE_AUDIT_DIR to the November source captures", !path.isNullOrBlank())
        return File(requireNotNull(path)).also { assertTrue(it.isDirectory) }
    }
    private fun read(dir: File, title: String): String = File(dir, title.replace(":", "__").replace("/", "__") + ".txt")
        .also { assertTrue(it.isFile, "Missing source: $it") }.readText(Charsets.UTF_8)
    private fun html(dir: File, title: String): String {
        val local = File(dir, "$title.html")
        val file = if (local.isFile) local else File(dir.parentFile, "player-decoration-audit/$title.html")
        assertTrue(file.isFile, "Missing HTML: $file")
        return file.readText(Charsets.UTF_8)
    }
    private fun normalized(text: String) = text.replace(Regex("\\s+"), "")
    private fun fileIdentity(text: String) = text.replace('_', ' ')

    @Test fun allNineLuaDirectoriesMatchTheirRenderedHtmlIncludingWaysAndPreviews() {
        val dir = directory()
        var total = 0
        DecorationCatalogs.policies.forEach { policy ->
            val lua = policy.source as? DecorationCatalogSource.Lua ?: return@forEach
            val result = DecorationLuaCatalogParser.parse(lua, read(dir,lua.modulePage), read(dir,lua.waysPage))
            assertTrue(result.isComplete, "${policy.pageName}: ${result.issues}")
            val expected = DecorationHtmlParser.parse(html(dir,policy.pageName)).flatMap { it.items }
            val actual = result.sections.flatMap { it.items }
            assertEquals(lua.minimumItems, actual.size, policy.pageName)
            assertEquals(expected.map { it.id }, actual.map { it.id }, "${policy.pageName}: source ordering")
            expected.zip(actual).forEach { (h,l) ->
                val label="${policy.pageName}/${l.id}"
                assertEquals(h.name,l.name,"$label name")
                assertEquals(h.quality,l.quality,"$label quality")
                assertEquals(normalized(h.description),normalized(l.description),"$label description")
                assertEquals(normalized(h.source),normalized(l.source),"$label acquisition")
                assertEquals(normalized(h.specialDescription),normalized(l.specialDescription),"$label effect")
                assertEquals(fileIdentity(h.icon.fileName),fileIdentity(l.icon.fileName),"$label image identity")
                assertEquals(h.extraPreviews.map { it.label to fileIdentity(it.image.fileName) },
                    l.extraPreviews.map { it.label to fileIdentity(it.image.fileName) },"$label previews")
            }
            total += actual.size
            println("${policy.pageName}: ${actual.size} Lua/HTML identities, text, ways and previews matched")
        }
        assertEquals(226,total)
    }

    @Test fun complexDirectoriesKeepAllSourceCardsWithoutEnumeratingUnmatchedLuaRows() {
        val dir = directory()
        for (page in listOf("基板","喷漆","勋章")) {
            val sourceHtml=html(dir,page)
            val document=Jsoup.parse(sourceHtml)
            val cards=document.select(".mw-parser-output .gallerygrid-item, .mw-parser-output li.gallerybox")
            assertEquals(mapOf("基板" to 492,"喷漆" to 673,"勋章" to 398).getValue(page),cards.size,"$page source baseline")
            val expectedFiles=cards.map { card ->
                val image=requireNotNull(card.selectFirst("img"))
                image.attr("alt").takeIf { it.contains('.') } ?: URLDecoder.decode(
                    requireNotNull(image.parents().firstOrNull { it.tagName()=="a" }).attr("href"),"UTF-8"
                ).substringAfter("文件:")
            }.map(::fileIdentity).toSet()
            val raw=DecorationHtmlParser.parse(sourceHtml)
            val items=DecorationAssembler.assemble(raw,DecorationCatalogs.forPage(page)).flatMap { it.items }
            assertEquals(expectedFiles,items.map { fileIdentity(it.fileName) }.toSet(),"$page missing source artwork identities")
            assertEquals(items.size,items.map { it.stableKey }.distinct().size,"$page duplicate display keys")
            assertTrue(items.none { it.name=="未知" })
            if(page=="基板") assertTrue(items.none { it.id==31000002 || it.id==31001279 })
            if(page=="喷漆") {
                assertEquals(setOf("默契考验","马踏飞弦"),items.filter { it.id==30002237 }.map { it.name }.toSet())
                assertEquals(setOf("赏春荧","甜蜜心愿"),items.filter { it.id==30002250 }.map { it.name }.toSet())
            }
            if(page=="勋章") {
                assertTrue(items.any { it.name=="荣誉之王" && it.quality==-1 })
                assertTrue(items.any { it.name=="八面玲珑" })
            }
            println("$page: ${cards.size} source cards -> ${items.size} group-preserving, uniquely keyed entries")
        }
    }

    @Test fun capturedTablesParseSafelyAndConflictingBaseplateWaysAreDiagnosed() {
        val dir=directory()
        val files=dir.listFiles().orEmpty().filter { it.name.endsWith("Data.txt") && it.name.startsWith("模块__玩家装饰") }
        assertEquals(13,files.size)
        files.forEach { file ->
            val parsed=DecorationModuleParser.parse(file.readText(Charsets.UTF_8))
            assertTrue(parsed.issues.isEmpty(),"${file.name}: ${parsed.issues}")
            assertTrue(parsed.items.isNotEmpty())
        }
        val ways=dir.listFiles().orEmpty().filter { it.name.startsWith("模块__获得方式__") && it.name.endsWith(".txt") }
        assertEquals(13,ways.size)
        ways.forEach { file ->
            val parsed=DecorationWaysParser.parse(file.readText(Charsets.UTF_8))
            if (file.name == "模块__获得方式__IDCard.txt") {
                // The real source repeats 31001242/31001243 as both 未实装 and 特别行动26SP3.
                // Baseplates stay HTML-owned; do not weaken the primary-source validator to hide this conflict.
                assertTrue(parsed.issues.any { "Conflicting Lua key: 31001242" in it }, parsed.issues.toString())
                assertTrue(parsed.ways.isEmpty())
            } else {
                assertTrue(parsed.issues.isEmpty(),"${file.name}: ${parsed.issues}")
                assertTrue(parsed.ways.isNotEmpty())
            }
        }
    }
}
