package com.nekolaska.calabiyau.feature.wiki.bgm.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BgmParsersTest {

    @Test
    fun enrichesCharacterTrackFromAlbumTable() {
        val page = BgmParsers.parsePage(
            """
            <div class="mw-parser-output">
              <h2><span class="mw-headline">角色音乐</span></h2>
              <h3><span class="mw-headline">米雪儿·李</span></h3>
              <div class="playerBox"
                   data-name="主题曲"
                   data-music="https://ex.com/Bgm-theme.mp3"
                   data-cover="https://ex.com/c.jpg"></div>
              <h2><span class="mw-headline">音乐专辑</span></h2>
              <h3><span class="mw-headline">OST1</span></h3>
              <a title="专辑封面-OST1.jpg" href="https://ex.com/ost1.jpg">cover</a>
              <table class="klbqtable table-hover">
                <tr><th>曲名</th><th>时长</th><th>场景</th></tr>
                <tr><td>1. 主题曲</td><td>3:00</td><td>登录</td></tr>
              </table>
            </div>
            """.trimIndent()
        )

        val track = page.tracks.single()
        assertEquals("主题曲", track.title)
        assertEquals("角色音乐", track.category)
        assertEquals("米雪儿·李", track.character)
        assertEquals("https://ex.com/Bgm-theme.mp3", track.audioUrl)
        assertEquals("https://ex.com/c.jpg", track.coverUrl)
        assertEquals("OST1", track.album)
        assertEquals("3:00", track.duration)
        assertEquals("登录", track.scene)

        val album = page.albums.single()
        assertEquals("OST1", album.title)
        assertEquals("https://ex.com/ost1.jpg", album.coverUrl)
        assertEquals("主题曲", album.tracks.single().title)
    }

    @Test
    fun cdPlayerUsesHardcodedTitlesAndPipeSeparator() {
        val page = BgmParsers.parsePage(
            """
            <div class="mw-parser-output">
              <h2><span class="mw-headline">登录音乐</span></h2>
              <div id="CDPlayer" data-music="https://ex.com/a.mp3|https://ex.com/b.mp3"></div>
            </div>
            """.trimIndent()
        )

        assertEquals(
            listOf("名为真相的幻影", "To The Beautiful"),
            page.tracks.map { it.title }
        )
        assertEquals("https://ex.com/a.mp3", page.tracks[0].audioUrl)
        assertEquals("https://ex.com/b.mp3", page.tracks[1].audioUrl)
        assertEquals("登录音乐", page.tracks[0].category)
        assertNull(page.tracks[0].character)
    }

    @Test
    fun hiddenMp3FallsBackToFileTitle() {
        val page = BgmParsers.parsePage(
            """
            <div class="mw-parser-output">
              <h2><span class="mw-headline">其他</span></h2>
              <p><a href="https://ex.com/media/BGM-Foo.mp3" title="媒体文件:BGM-Foo.mp3">audio</a></p>
            </div>
            """.trimIndent()
        )

        assertEquals("Foo", page.tracks.single().title)
        assertEquals("https://ex.com/media/BGM-Foo.mp3", page.tracks.single().audioUrl)
        assertEquals("其他", page.tracks.single().category)
    }
}
