package com.nekolaska.calabiyau.feature.wiki.meme.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class MemeParsersTest {

    @Test
    fun parsesOfficialTabsAndEditorEntries() {
        val page = MemeParsers.parsePage(
            """
            <div class="mw-parser-output">
              <h2><span class="mw-headline">官方编写</span></h2>
              <ul class="tab-nav"><li><a>第一期</a></li><li><a>第二期</a></li></ul>
              <div class="tab-content">
                <img src="https://x/thumb/a/ab/1.png/300px-1.png"/>
                <img src="https://x/2.png"/>
              </div>
              <h2><span class="mw-headline">编辑编写</span></h2>
              <dl><dt>弦化笑话：</dt></dl>
              <p>贴墙一时爽。</p>
              <ul><li>掉下来火葬场。</li></ul>
            </div>
            """.trimIndent()
        )

        assertEquals(listOf("第一期", "第二期"), page.officialIssues.map { it.title })
        assertEquals("https://x/a/ab/1.png", page.officialIssues[0].imageUrl)
        val entry = page.editorEntries.single()
        assertEquals("弦化笑话", entry.title)
        assertEquals("贴墙一时爽。\n掉下来火葬场。", entry.description)
    }
}
