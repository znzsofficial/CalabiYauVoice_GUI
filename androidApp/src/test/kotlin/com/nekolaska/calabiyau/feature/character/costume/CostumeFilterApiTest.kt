package com.nekolaska.calabiyau.feature.character.costume

import data.SharedJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.nio.file.Files
import java.nio.file.Path

class CostumeFilterApiTest {

    @Test
    fun parsesGalleryCardsAndModelImage() {
        val costumes = CostumeFilterApi.parseCostumeHtml(
            """
            <div class="gallerygrid">
              <div class="gallerygrid-item klbq-skin-card" data-param1="诺诺" data-param2="2" data-param3="角色预设直购" data-param4="150" data-param5="150">
                <div class="klbq-skin-card&#95;_imagebox">
                  <img alt="角色时装图鉴 20126004.png"
                       src="https://patchwiki.biligame.com/images/klbq/thumb/f/ff/hash.png/200px-x.png"
                       srcset="https://patchwiki.biligame.com/images/klbq/f/ff/hash.png 1.5x" />
                </div>
                <div class="klbq-skin-card&#95;_captionbox">
                  <div class="klbq-skin-card&#95;_name">诺诺：晦朔-常磐</div>
                  <div class="klbq-skin-card&#95;_desc">
                    <span class="klbq-skin-card&#95;_label">简介：</span>
                    <span class="klbq-skin-card&#95;_value">踏入自然中，可以放空思维。</span>
                  </div>
                </div>
              </div>
              <div class="gallerygrid-item klbq-skin-card" data-param1="梅瑞狄斯" data-param2="5" data-param3="意识重构-阿卡西之眼" data-param4="无" data-param5="无">
                <div class="klbq-skin-card__imagebox">
                  <img alt="角色时装图鉴 20133206.png"
                       src="https://patchwiki.biligame.com/images/klbq/thumb/a/ab/icon.png/200px-icon.png"
                       srcset="https://patchwiki.biligame.com/images/klbq/a/ab/icon.png 1.5x" />
                  <img alt="梅瑞狄斯时装-阿卡西之眼.jpg"
                       src="https://patchwiki.biligame.com/images/klbq/thumb/c/cd/model.jpg/400px-model.jpg"
                       srcset="https://patchwiki.biligame.com/images/klbq/c/cd/model.jpg 1.5x" />
                </div>
                <div class="klbq-skin-card__name">梅瑞狄斯：阿卡西之眼</div>
                <div class="klbq-skin-card__desc">
                  <span class="klbq-skin-card__value">过去、现在、未来。</span>
                </div>
              </div>
            </div>
            """.trimIndent()
        )

        assertEquals(2, costumes.size)

        val first = costumes[0]
        assertEquals("诺诺：晦朔-常磐", first.name)
        assertEquals("诺诺", first.character)
        assertEquals(CostumeFilterApi.Quality.EXQUISITE, first.quality)
        assertEquals(listOf("角色预设直购"), first.sources)
        assertEquals("150", first.crystalCost)
        assertEquals("150", first.baseCost)
        assertEquals("踏入自然中，可以放空思维。", first.description)
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/f/ff/hash.png",
            first.fullImageUrl
        )
        assertNull(first.screenshotUrl)

        val legendary = costumes[1]
        assertEquals("梅瑞狄斯：阿卡西之眼", legendary.name)
        assertEquals(CostumeFilterApi.Quality.LEGENDARY, legendary.quality)
        assertEquals("", legendary.crystalCost)
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/a/ab/icon.png",
            legendary.fullImageUrl
        )
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/c/cd/model.jpg",
            legendary.screenshotUrl
        )
    }

    @Test
    fun parsesLiveWikiSnapshot() {
        val snapshot = liveWikiHtml() ?: return
        val expectedCards = Jsoup.parse(snapshot).select(".gallerygrid-item.klbq-skin-card, .klbq-skin-card").size
        val costumes = CostumeFilterApi.parseCostumeHtml(snapshot)

        assertTrue(expectedCards > 500, "expected a full costume page, got $expectedCards cards")
        assertEquals(expectedCards, costumes.size)
        assertTrue(costumes.none { it.name.endsWith("：未知") })
        assertTrue(costumes.all { it.character.isNotBlank() })
        assertTrue(costumes.all { it.fullImageUrl != null })
        assertTrue(costumes.count { it.screenshotUrl != null } > 100)
        assertTrue(costumes.any { it.quality == CostumeFilterApi.Quality.LEGENDARY })
        assertEquals("诺诺：晦朔-常磐", costumes.first().name)
        val akashi = costumes.first { it.name.contains("阿卡西之眼") }
        assertEquals("梅瑞狄斯", akashi.character)
        assertEquals(CostumeFilterApi.Quality.LEGENDARY, akashi.quality)
        assertTrue(akashi.description.isNotBlank())
        val screenshot = akashi.screenshotUrl.orEmpty()
        assertTrue(screenshot.contains(".jpg") || screenshot.contains(".png"))
    }

    @Test
    fun fallsBackToLegacyDivsortTable() {
        val costumes = CostumeFilterApi.parseCostumeHtml(
            """
            |- class="divsort" data-param1="信" data-param2="3" data-param3="活动" data-param4="无" data-param5="无"
            | <img alt="角色时装图鉴 1.png" src="https://patchwiki.biligame.com/images/klbq/1/11/a.png"/><br />信：旧表时装
            | 这是一段足够长的时装介绍文字用于回退解析
            """.trimIndent()
        )

        assertEquals("信：旧表时装", costumes.single().name)
        assertEquals("信", costumes.single().character)
        assertEquals(CostumeFilterApi.Quality.SUPERIOR, costumes.single().quality)
        assertTrue(costumes.single().description.contains("时装介绍"))
    }

    private fun liveWikiHtml(): String? {
        val candidates = listOf(
            Path.of("C:/Users/NekoLaska/.local/share/opencode/tool-output/tool_066e115bb001Mdb0AclL2VJtIC"),
            Path.of(System.getProperty("user.home"), ".local/share/opencode/tool-output/tool_066e115bb001Mdb0AclL2VJtIC")
        )
        val file = candidates.firstOrNull { Files.exists(it) } ?: return null
        val raw = Files.readString(file)
        val start = raw.indexOf('{')
        if (start < 0) return null
        val json = SharedJson.parseToJsonElement(raw.substring(start)).jsonObject
        return json["parse"]?.jsonObject?.get("text")?.jsonObject?.get("*")?.jsonPrimitive?.content
    }
}
