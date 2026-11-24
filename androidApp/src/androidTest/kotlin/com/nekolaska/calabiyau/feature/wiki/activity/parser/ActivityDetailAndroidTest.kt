package com.nekolaska.calabiyau.feature.wiki.activity.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Must run on ART: desktop java.util.regex accepts a bare `}` that Android ICU rejects. */
@RunWith(AndroidJUnit4::class)
class ActivityDetailAndroidTest {
    @Test fun firstReadParsesTheRewardsTableAndRejectsAMissingRow() {
        val title = "欢迎光临卡邦尼！"
        val source = """== 奖励 ==
            |{| class="klbqtable"
            |! 场景 !! 奖励
            ||-
            || 800 个 || 邦邦快送
            ||-
            || 1600 个 || 鹅了么
            ||}""".trimMargin().replace("\n", "\r\n")
        val lastRow = "<tr><td>1600 个</td><td>鹅了么</td></tr>"
        val html = """<div class="mw-parser-output"><div class="klbq-event-header">
            <b class="klbq-event-header__title">$title</b></div><h2>奖励</h2>
            <table class="klbqtable"><tr><th>场景</th><th>奖励</th></tr>
            <tr><td>800 个</td><td>邦邦快送</td></tr>$lastRow</table></div>"""

        assertEquals(listOf(Heading("奖励"), TableRow(listOf("场景", "奖励"), true),
            TableRow(listOf("800 个", "邦邦快送"), false), TableRow(listOf("1600 个", "鹅了么"), false)),
            ActivityDetailParser.parse(html, title, source))
        assertTrue(ActivityDetailParser.parse(html.replace(lastRow, ""), title, source).isEmpty())
    }
}
