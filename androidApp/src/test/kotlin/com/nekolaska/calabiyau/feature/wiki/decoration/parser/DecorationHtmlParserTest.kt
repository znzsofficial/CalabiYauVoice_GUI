package com.nekolaska.calabiyau.feature.wiki.decoration.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecorationHtmlParserTest {
    private fun fixture(name: String): String = requireNotNull(
        javaClass.getResource("/fixtures/decoration/$name.html")
    ).readText(Charsets.UTF_8)

    @Test
    fun modernNamecardsKeepRealIdentityDescriptionAndOriginalImages() {
        val items = DecorationHtmlParser.parse(fixture("namecards")).single().items
        assertEquals(listOf(34000001, 34000002), items.map { it.id })
        assertEquals(listOf("序列证", "弦辉名笺"), items.map { it.name })
        assertEquals(listOf(2, 5), items.map { it.quality })
        assertEquals("这是我的序列证，官方亲自认证。", items.first().description)
        assertEquals("默认", items.first().source)
        assertEquals("赫尔墨斯商城累充奖励", items.last().source)
        assertEquals("个性名片 34000001.png", items.first().image.fileName)
        assertEquals("https://patchwiki.biligame.com/images/klbq/3/38/3uw9ts747aknlplvgxvrd8aa96c3ngn.png", items.first().image.originalUrl)
        assertTrue(items.all { it.icon == it.image && it.image.originalUrl.isNotBlank() })
    }

    @Test
    fun loungeFiguresKeepRealIdsQualityAndDifferentAcquisitionSources() {
        val items = DecorationHtmlParser.parse(fixture("lounge-figures")).single().items
        assertEquals(listOf(70001, 70012, 70014), items.map { it.id })
        assertEquals(listOf("星绘-星佑手办", "喵萌元气", "肥肥鲨绯绯莎"), items.map { it.name })
        assertEquals(listOf(3, 4, 4), items.map { it.quality })
        assertEquals(listOf("挑战赛S7赛季大师段位奖励", "米雪儿喵萌元气Ver.Q版手办特典奖励", "【梦海纪念礼】活动奖励"), items.map { it.source })
        assertTrue(items[1].description.endsWith("这份携手的情谊，永远不会在记忆里褪色。"))
        assertEquals("https://patchwiki.biligame.com/images/klbq/b/b2/aw0sh788f35teitsjbmgcs3dsred74t.png", items.last().image.originalUrl)
    }

    @Test
    fun legacyGalleryAndRoomKeepGroupsFilesTextAndExtraPreviews() {
        val sections = DecorationHtmlParser.parse(fixture("legacy-gallery-room"))
        assertEquals(listOf("头像", "房间"), sections.map { it.title })
        val avatar = sections[0].items.single()
        assertEquals(101, avatar.id)
        assertEquals("猫头", avatar.name)
        assertEquals(4, avatar.quality)
        assertEquals("旧版描述", avatar.description)
        assertEquals("avatar_101_icon.png", avatar.icon.fileName)
        assertEquals("avatar_101_icon.png", avatar.image.fileName)
        assertEquals("商城\n活动", avatar.source)
        assertEquals("", avatar.icon.originalUrl, "A thumbnail is not an original")

        val room = sections[1].items.single()
        assertEquals(202, room.id)
        assertEquals("房间", room.name)
        assertEquals("房间描述", room.description)
        assertEquals("UI", room.extraPreviews.single().label)
        assertEquals("room_202_ui.png", room.extraPreviews.single().image.fileName)
        assertEquals("https://example.com/room-ui.png", room.extraPreviews.single().image.originalUrl)
    }

    @Test
    fun onlySourceProvidedImageFilesAreUsed() {
        val items = DecorationHtmlParser.parse(fixture("legacy-gallery-room")
            .replace("https://example.com/thumb/avatar.png/100px-avatar.png", "//example.com/avatar-icon.png"))
            .first().items
        assertEquals("https://example.com/avatar-icon.png", items.single().icon.originalUrl)
        assertEquals(items.single().icon, items.single().image)
    }

    @Test
    fun lazyAndSrcsetOriginalsAreAcceptedButThumbnailOnlyFilesStayUnresolved() {
        val html = fixture("namecards")
            .replace("src=\"https://patchwiki", "data-src=\"//patchwiki")
        assertTrue(DecorationHtmlParser.parse(html).single().items.all {
            it.icon.originalUrl.startsWith("https://patchwiki")
        })
        val withSrcset = fixture("legacy-gallery-room").replace(
            "src=\"https://example.com/thumb/avatar.png/100px-avatar.png\"",
            "src=\"data:image/gif;base64,placeholder\" srcset=\"/thumb/a.png/100px-a.png 1x, /original.png 2x\""
        )
        assertEquals("https://wiki.biligame.com/original.png", DecorationHtmlParser.parse(withSrcset).first().items.single().icon.originalUrl)
    }

    @Test
    fun missingIdCannotBeReplacedByAnInventedSequenceNumber() {
        val sections = DecorationHtmlParser.parse("""
            <div class="mw-parser-output"><div class="gallerygrid">
              <div class="gallerygrid-item"><img alt="no-id.png" /><span data-quality="1">无</span></div>
            </div></div>
        """.trimIndent())
        assertTrue(sections.isEmpty())
    }

    @Test
    fun modernRoomsVehiclesAndHandWrittenMedalsKeepTheirDistinctFields() {
        val groups = DecorationHtmlParser.parse(fixture("modern-contract")).associateBy { it.title }
        val room = groups.getValue("房间").items.single()
        assertEquals(0, room.quality)
        assertEquals(listOf("好友列表", "按钮样式"), room.extraPreviews.map { it.label })
        assertTrue(room.description.contains("\n匹配房间"))
        assertEquals("默认", room.source)
        val vehicles = groups.getValue("载具").items
        assertEquals("默认", vehicles[0].source)
        assertEquals("", vehicles[0].specialDescription)
        assertEquals("一番赏", vehicles[1].source)
        assertEquals("载具附近每2秒恢复1生命值", vehicles[1].specialDescription)
        assertEquals("移动端", vehicles[1].platform)
        val medals = groups.getValue("勋章").items
        assertEquals(listOf("荣誉之王", "八面玲珑"), medals.map { it.name })
        assertEquals(listOf(-1, -1), medals.map { it.quality })
        assertEquals("荣誉成就S级奖励", medals[0].source)
        assertEquals(33500294, medals[1].id)
        assertEquals("", medals[1].source, "A gallery caption is not an acquisition source")
    }

    @Test
    fun unuploadedActionPreviewKeepsDeclaredFileWithoutAnImageUrl() {
        // Reduced November 14 structure: Wiki renders a red upload link when this GIF is absent.
        val raw = DecorationHtmlParser.parse("""
            <div class="mw-parser-output"><div class="gallerygrid-item">
              <div class="gallerygrid-imagebox"><img alt="超弦体动作图标 21999010.png" /></div>
              <span data-quality="5">光之轮舞</span>
              <div class="wiki-lemma-content-data" style="display:none">
                <a class="new" title="文件:光之轮舞.gif">文件:光之轮舞.gif</a>
              </div>
            </div></div>
        """.trimIndent()).single().items.single()
        assertEquals("光之轮舞.gif", raw.extraPreviews.single().image.fileName)
        assertEquals("", raw.extraPreviews.single().image.originalUrl)
        assertEquals("动作预览", raw.extraPreviews.single().label)
    }
}
