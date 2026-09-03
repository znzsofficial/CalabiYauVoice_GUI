package com.nekolaska.calabiyau.feature.wiki.activity.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivityParsersTest {

    @Test
    fun extractFirstFileTitleFromDetailLinks() {
        assertEquals(
            "文件:活动封面.png",
            ActivityParsers.extractFirstFileTitle(
                """<p><a href="/klbq/活动页">活动</a><a href="/klbq/文件:活动封面.png">图</a></p>"""
            )
        )
        assertNull(ActivityParsers.extractFirstFileTitle("""<a href="/klbq/活动页">活动</a>"""))
    }
}
