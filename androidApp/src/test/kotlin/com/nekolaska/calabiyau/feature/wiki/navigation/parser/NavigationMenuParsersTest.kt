package com.nekolaska.calabiyau.feature.wiki.navigation.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavigationMenuParsersTest {

    @Test
    fun keepsWhitelistedRootsAndBuildsTree() {
        val sections = NavigationMenuParsers.parseSidebar(
            """
            * 首页
            * 角色
            ** 米雪儿·李|米雪儿
            *** 时装
            * 忽略
            ** 不该出现
            * 武器
            ** https://example.com/gun|外部枪
            """.trimIndent()
        )

        assertEquals(listOf("首页", "角色", "武器"), sections.map { it.title })
        assertTrue(sections[0].items.isEmpty())

        val michelle = sections[1].items.single()
        assertEquals("米雪儿", michelle.title)
        assertEquals(
            "https://wiki.biligame.com/klbq/%E7%B1%B3%E9%9B%AA%E5%84%BF%C2%B7%E6%9D%8E",
            michelle.url
        )
        assertEquals("时装", michelle.children.single().title)

        assertEquals("外部枪", sections[2].items.single().title)
        assertEquals("https://example.com/gun", sections[2].items.single().url)
    }
}
