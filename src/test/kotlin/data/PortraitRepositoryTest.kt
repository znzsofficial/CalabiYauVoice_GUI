package data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PortraitRepositoryTest {
    @Test
    fun `buildPortraitCatalog groups real wiki preview and illustration names by costume`() {
        val catalog = buildPortraitCatalog(
            characterName = "艾卡",
            files = listOf(
                "艾卡-课后魔王立绘.png" to "https://example.com/illust.png",
                "艾卡时装-课后魔王.jpg" to "https://example.com/front.jpg",
                "艾卡时装-课后魔王_背面.jpg" to "https://example.com/back.jpg",
                "艾卡-初始立绘.png" to "https://example.com/default.png"
            )
        )

        assertEquals(2, catalog.costumes.size)
        assertEquals("默认时装", catalog.costumes.first().name)
        val costume = catalog.costumes.last()
        assertEquals("课后魔王", costume.name)
        assertEquals("https://example.com/front.jpg", costume.frontPreview?.url)
        assertEquals("https://example.com/back.jpg", costume.backPreview?.url)
        assertEquals("https://example.com/illust.png", costume.illustration?.url)
    }

    @Test
    fun `buildPortraitCatalog merges default illustration into the unique preview only default costume`() {
        val catalog = buildPortraitCatalog(
            characterName = "艾卡",
            files = listOf(
                "艾卡-初始立绘.png" to "https://example.com/default.png",
                "艾卡时装-火舞者.jpg" to "https://example.com/front.jpg",
                "艾卡时装-火舞者_背面.jpg" to "https://example.com/back.jpg"
            )
        )

        assertEquals(1, catalog.costumes.size)
        val defaultCostume = catalog.costumes.single()
        assertEquals("火舞者", defaultCostume.name)
        assertEquals("https://example.com/default.png", defaultCostume.illustration?.url)
        assertEquals("https://example.com/front.jpg", defaultCostume.frontPreview?.url)
        assertEquals("https://example.com/back.jpg", defaultCostume.backPreview?.url)
    }

    @Test
    fun `buildPortraitCatalog ignores unrelated images without portrait patterns`() {
        val catalog = buildPortraitCatalog(
            characterName = "米雪儿·李",
            files = listOf(
                "米雪儿·李 头像.png" to "https://example.com/avatar.png",
                "米雪儿·李 站内横幅.jpg" to "https://example.com/banner.jpg"
            )
        )

        assertEquals(0, catalog.costumes.size)
    }

    @Test
    fun `buildPortraitCatalog treats initial illustrations as default costume`() {
        val catalog = buildPortraitCatalog(
            characterName = "奥黛丽格罗夫",
            files = listOf(
                "奥黛丽格罗夫-初始立绘.png" to "https://example.com/portrait.png"
            )
        )

        assertEquals(1, catalog.costumes.size)
        assertEquals("默认时装", catalog.costumes.single().name)
        assertNotNull(catalog.costumes.single().illustration)
    }

    @Test
    fun `buildPortraitCatalog supports long character names with 时装 prefix`() {
        val catalog = buildPortraitCatalog(
            characterName = "奥黛丽格罗夫",
            files = listOf(
                "奥黛丽格罗夫-黯月遗狩立绘.png" to "https://example.com/illust.png",
                "奥黛丽格罗夫时装-黯月遗狩.jpg" to "https://example.com/front.jpg",
                "奥黛丽格罗夫时装-黯月遗狩 背面.jpg" to "https://example.com/back.jpg"
            )
        )

        val costume = catalog.costumes.single()
        assertEquals("黯月遗狩", costume.name)
        assertEquals("https://example.com/front.jpg", costume.frontPreview?.url)
        assertEquals("https://example.com/back.jpg", costume.backPreview?.url)
    }

    @Test
    fun `remapPortraitFilesToOfficialNames prefers official wiki role names when prefixes are compatible`() {
        val remapped = remapPortraitFilesToOfficialNames(
            rawGrouped = mapOf(
                "奥黛丽格罗夫" to listOf("奥黛丽格罗夫-初始立绘.png" to "u1"),
                "艾卡" to listOf("艾卡-初始立绘.png" to "u2")
            ),
            officialNames = listOf("艾卡", "奥黛丽格罗夫")
        )

        assertEquals(listOf("艾卡", "奥黛丽格罗夫"), remapped.keys.toList())
        assertEquals("u1", remapped["奥黛丽格罗夫"]?.single()?.second)
    }

    @Test
    fun `extractPortraitCharacterName supports compact crystal body file names`() {
        assertEquals("爆裂魔怪", extractPortraitCharacterName("文件:爆裂魔怪立绘.png"))
        assertEquals("刺镰魔怪", extractPortraitCharacterName("文件:刺镰魔怪背面.jpg"))
        assertEquals("冥荆皇女", extractPortraitCharacterName("文件:冥荆皇女预览图.jpg"))
        assertEquals("血荆皇女", extractPortraitCharacterName("文件:血荆皇女正面.jpg"))
    }

    @Test
    fun `buildPortraitCatalog supports compact crystal body role names without separators`() {
        val catalog = buildPortraitCatalog(
            characterName = "爆裂魔怪",
            files = listOf(
                "爆裂魔怪立绘.png" to "https://example.com/illust.png",
                "爆裂魔怪.jpg" to "https://example.com/front.jpg",
                "爆裂魔怪背面.jpg" to "https://example.com/back.jpg"
            )
        )

        assertEquals(1, catalog.costumes.size)
        val costume = catalog.costumes.single()
        assertEquals("默认时装", costume.name)
        assertEquals("https://example.com/illust.png", costume.illustration?.url)
        assertEquals("https://example.com/front.jpg", costume.frontPreview?.url)
        assertEquals("https://example.com/back.jpg", costume.backPreview?.url)
    }

    @Test
    fun `remapPortraitFilesToOfficialNames drops unmatched skill and english groups`() {
        val remapped = remapPortraitFilesToOfficialNames(
            rawGrouped = mapOf(
                "艾卡" to listOf("艾卡-初始立绘.png" to "u1"),
                "艾卡技能1" to listOf("艾卡技能1.png" to "u2"),
                "SKILLICON" to listOf("SKILLICON.png" to "u3")
            ),
            officialNames = listOf("艾卡")
        )

        assertEquals(listOf("艾卡"), remapped.keys.toList())
        assertEquals("u1", remapped["艾卡"]?.single()?.second)
    }
}
