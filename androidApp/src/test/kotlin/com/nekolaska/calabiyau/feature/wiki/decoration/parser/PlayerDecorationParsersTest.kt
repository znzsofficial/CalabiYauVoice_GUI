package com.nekolaska.calabiyau.feature.wiki.decoration.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerDecorationParsersTest {

    @Test
    fun parseModuleDataKeepsIdAndJoinsSources() {
        val data = PlayerDecorationParsers.parseModuleData(
            """
            return {
              { id = 101, name = "猫头", quality = 4, desc = "第一行<br />第二行",
                spdesc = [[特殊<br>说明]], get = { "商城", "活动" } },
              { name = "无id" },
              { id = 102, name = "空来源", quality = 1, desc = "", get = {} }
            }
            """.trimIndent()
        )

        assertEquals(setOf(101, 102), data.keys)
        val cat = data.getValue(101)
        assertEquals("猫头", cat.name)
        assertEquals(4, cat.quality)
        assertEquals("第一行\n第二行", cat.description)
        assertEquals("特殊\n说明", cat.specialDescription)
        assertEquals("商城、活动", cat.source)
        assertEquals("", data.getValue(102).source)
    }

    @Test
    fun parseHtmlExtractsGalleryAndRoomGridItems() {
        val sections = PlayerDecorationParsers.parseHtml(
            """
            <div class="mw-parser-output">
              <h2><span class="mw-headline">头像</span></h2>
              <div class="gallerygrid">
                <div class="gallerygrid-item">
                  <img alt="avatar_101_icon.png" />
                  <span data-quality="4">猫头</span>
                  <small>描述</small>
                  <ul><li>商城</li><li>活动</li></ul>
                </div>
              </div>
              <h2><span class="mw-headline">房间</span></h2>
              <div class="gallerygrid">
                <div class="gallerygrid-item roomgrid-item">
                  <div class="roomgrid-image-bg"><img alt="room_202.png" /></div>
                  <div class="roomgrid-player-decoration"><span data-quality="3">房间</span></div>
                  <div class="roomgrid-desc">房间描述</div>
                  <ul><li>活动</li></ul>
                  <div class="roomgrid-ui-item">
                    <span class="ui-label">UI</span>
                    <img alt="room_202_ui.png" />
                  </div>
                </div>
              </div>
            </div>
            """.trimIndent()
        )

        assertEquals(listOf("头像", "房间"), sections.map { it.first })
        val avatar = sections[0].second.single()
        assertEquals(101, avatar.id)
        assertEquals("猫头", avatar.name)
        assertEquals(4, avatar.quality)
        assertEquals("avatar_101_icon.png", avatar.iconFile)
        assertEquals("avatar_101.png", avatar.imgFile)
        assertEquals("商城、活动", avatar.source)

        val room = sections[1].second.single()
        assertEquals(202, room.id)
        assertEquals("房间", room.name)
        assertEquals(listOf("UI" to "room_202_ui.png"), room.extraPreviewFiles)
        assertEquals(
            setOf("avatar_101_icon.png", "avatar_101.png", "room_202.png", "room_202_ui.png"),
            PlayerDecorationParsers.extractFileNames(sections)
        )
    }

    @Test
    fun parseHtmlSkipsItemsWithoutId() {
        val sections = PlayerDecorationParsers.parseHtml(
            """
            <div class="mw-parser-output">
              <div class="gallerygrid">
                <div class="gallerygrid-item"><img alt="no-id.png" /><span data-quality="1">无</span></div>
              </div>
            </div>
            """.trimIndent()
        )
        assertTrue(sections.isEmpty())
    }
}
