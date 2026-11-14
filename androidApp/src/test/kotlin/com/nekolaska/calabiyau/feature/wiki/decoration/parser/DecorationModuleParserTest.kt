package com.nekolaska.calabiyau.feature.wiki.decoration.parser

import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogSource
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecorationModuleParserTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/decoration/$name.lua")).readText(Charsets.UTF_8)

    @Test
    fun completeNamecardPairKeepsFiveRealIdsAndVerifiedFileRules() {
        val policy = DecorationCatalogs.forPage("个性名片").source as DecorationCatalogSource.Lua
        val result = DecorationLuaCatalogParser.parse(policy, fixture("profile-card-data"), fixture("profile-card-ways"))
        assertTrue(result.isComplete, result.issues.toString())
        assertEquals((34000001..34000005).toList(), result.sections.single().items.map { it.id })
        assertEquals("个性名片_34000001.png", result.sections.single().items.first().image.fileName)
        assertEquals("默认", result.sections.single().items.first().source)
        assertEquals("失落音律 随星重鸣", result.sections.single().items.last().source)
    }

    @Test
    fun zeroQualityAndRoomPreviewsFollowCapturedRenderer() {
        val policy = (DecorationCatalogs.forPage("房间外观").source as DecorationCatalogSource.Lua).copy(minimumItems = 1)
        val result = DecorationLuaCatalogParser.parse(policy, """return {
          {id=93001,name="弦流识域",quality=0,desc="自此间，链接无限。<br /><br />匹配房间背景和个人名片样式。装配后，其他玩家加入你的房间也会可见。"}
        }""", "return {[93001]={'默认'}}")
        assertTrue(result.isComplete)
        val item = result.sections.single().items.single()
        assertEquals(0, item.quality)
        assertEquals(listOf("房间外观_93001_FriendTitleBg.png", "房间外观_93001_BtnBg.png"), item.extraPreviews.map { it.image.fileName })
        assertTrue(item.description.contains('\n'))
    }

    @Test
    fun actionPreviewAndFrameFileAreSourceRulesNotInventedCdnUrls() {
        val action = (DecorationCatalogs.forPage("超弦体动作").source as DecorationCatalogSource.Lua).copy(minimumItems = 1)
        val item = DecorationLuaCatalogParser.parse(action,
            "return {{id=21101001,name='米雪儿-你好',quality=2,desc='元气满满的搜查官，闪亮登场！'}}",
            "return {[21101001]={'意识重构-猎虎裁恶'}}").sections.single().items.single()
        assertEquals("超弦体动作图标_21101001.png", item.icon.fileName)
        assertEquals("米雪儿-你好.gif", item.extraPreviews.single().image.fileName)
        val frame = (DecorationCatalogs.forPage("封装").source as DecorationCatalogSource.Lua).copy(minimumItems = 1)
        val parsed = DecorationLuaCatalogParser.parse(frame,
            "return {{id=32000001,name='K-01标准封装',quality=2,file='封装_32000001_icon01.png',desc='可装配勋章数：1'}}",
            "return {[32000001]={'默认'}}")
        assertTrue(parsed.isComplete)
        assertEquals("封装_32000001_icon01.png", parsed.sections.single().items.single().image.fileName)
    }

    @Test
    fun conflictsMissingWaysPartialRowsAndTruncatedCatalogAreRejected() {
        val policy = DecorationCatalogs.forPage("个性名片").source as DecorationCatalogSource.Lua
        val items = fixture("profile-card-data")
        val ways = fixture("profile-card-ways")
        val invalid = listOf(
            items to ways.replace("[34000005]", "[99999999]"),
            items.replace("quality=5", "quality=nil") to ways,
            "return {{id=34000001,name='序列证',quality=2,desc='a'}}" to ways,
            items.dropLast(2) to ways,
            items.replace("34000002", "34000001") to ways
        )
        invalid.forEach { (data, acquisition) ->
            assertTrue(!DecorationLuaCatalogParser.parse(policy, data, acquisition).isComplete)
        }
    }
}
