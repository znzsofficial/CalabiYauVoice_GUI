package com.nekolaska.calabiyau.feature.wiki.decoration.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DecorationCatalogsTest {
    @Test
    fun nineVerifiedDirectoriesUsePairedLuaAndComplexDirectoriesStayHtml() {
        val policies = DecorationCatalogs.policies
        assertEquals(13, policies.map { it.pageName }.distinct().size)
        val lua = policies.filter { it.source is DecorationCatalogSource.Lua }
        assertEquals(setOf("封装", "聊天气泡", "头套", "超弦体动作", "房间外观", "头像框", "极限推进模式载具外观", "休息室手办", "个性名片"), lua.map { it.pageName }.toSet())
        assertEquals(listOf(52,37,9,16,7,71,15,14,5), lua.map { (it.source as DecorationCatalogSource.Lua).minimumItems })
        assertEquals(setOf("基板", "喷漆", "勋章", "登场特效"), policies.filter { it.source == DecorationCatalogSource.Html }.map { it.pageName }.toSet())
        assertEquals("模块:获得方式/HeadFrameMobile", (DecorationCatalogs.forPage("头像框").source as DecorationCatalogSource.Lua).waysPage)
        assertEquals(false, (DecorationCatalogs.forPage("头像框").source as DecorationCatalogSource.Lua).sortById)
        assertEquals(DecorationCatalogSource.Html, DecorationCatalogs.forPage("未登记").source)
    }
}
