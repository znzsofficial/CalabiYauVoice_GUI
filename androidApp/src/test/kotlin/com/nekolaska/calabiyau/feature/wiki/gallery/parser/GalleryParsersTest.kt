package com.nekolaska.calabiyau.feature.wiki.gallery.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GalleryParsersTest {

    @Test
    fun parsesHeadingGalleryBoxAndObtainMethod() {
        val sections = GalleryParsers.parseHtml(
            pageName = "图鉴",
            html = """
                <div class="mw-parser-output">
                  <h2><span class="mw-headline">立绘</span></h2>
                  <ul class="gallery">
                    <li class="gallerybox">
                      <a class="image" href="/klbq/%E6%96%87%E4%BB%B6:%E7%B1%B3%E9%9B%AA%E5%84%BF%E7%AB%8B%E7%BB%98.png">
                        <img alt="米雪儿立绘.png"
                             src="https://patchwiki.biligame.com/images/klbq/thumb/a/ab/x.png/300px-x.png"
                             srcset="https://patchwiki.biligame.com/images/klbq/a/ab/x.png 2x" />
                      </a>
                      <div class="gallerytext"><p><big>默认</big><br/><small>描述</small>获取方式：商城</p></div>
                    </li>
                  </ul>
                </div>
            """.trimIndent()
        )

        assertEquals(1, sections.size)
        assertEquals("立绘", sections.single().first)
        val image = sections.single().second.single()
        assertEquals("米雪儿立绘.png", image.fileName)
        assertEquals("默认", image.caption)
        assertEquals("描述", image.description)
        assertEquals("商城", image.obtainMethod)
        assertEquals("https://patchwiki.biligame.com/images/klbq/a/ab/x.png", image.directImageUrl)
    }

    @Test
    fun usesPageNameWhenThereIsNoHeading() {
        val sections = GalleryParsers.parseHtml(
            pageName = "表情",
            html = """
                <div class="mw-parser-output">
                  <a class="image" href="/klbq/文件:笑.png">
                    <img alt="笑.png" src="https://patchwiki.biligame.com/images/klbq/1/11/smile.png" />
                  </a>
                </div>
            """.trimIndent()
        )

        assertEquals("表情", sections.single().first)
        assertEquals("笑.png", sections.single().second.single().fileName)
        assertEquals("https://patchwiki.biligame.com/images/klbq/1/11/smile.png", sections.single().second.single().directImageUrl)
    }

    @Test
    fun skipsNestedGalleryboxLinksAndDeduplicatesFileNames() {
        val sections = GalleryParsers.parseHtml(
            pageName = "图鉴",
            html = """
                <div class="mw-parser-output">
                  <h3>立绘</h3>
                  <ul class="gallery">
                    <li class="gallerybox">
                      <a class="image" href="/klbq/文件:重复.png"><img alt="重复.png" src="https://x/a.png" /></a>
                    </li>
                  </ul>
                  <a class="image" href="/klbq/文件:重复.png"><img alt="重复.png" src="https://x/b.png" /></a>
                </div>
            """.trimIndent()
        )

        assertEquals(listOf("重复.png"), sections.single().second.map { it.fileName })
    }

    @Test
    fun emptyHtmlReturnsEmptyList() {
        assertTrue(GalleryParsers.parseHtml("图鉴", "<div></div>").isEmpty())
    }
}
