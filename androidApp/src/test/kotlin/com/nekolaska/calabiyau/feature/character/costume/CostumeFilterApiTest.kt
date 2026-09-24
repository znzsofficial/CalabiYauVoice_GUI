package com.nekolaska.calabiyau.feature.character.costume

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    /**
     * 线上真实页面的全量校验由 LiveWikiSnapshotTest.fetchesAndParsesCostumeFilterWhenEnabled
     * （LIVE_WIKI_TEST=1 门禁）承担——真实响应约 2.6MB，不适合作为 tracked 夹具入库，
     * 此前读取一次性 tool-output 缓存的死测试已移除。
     */
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
}
