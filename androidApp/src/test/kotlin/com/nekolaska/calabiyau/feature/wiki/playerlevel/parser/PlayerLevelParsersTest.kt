package com.nekolaska.calabiyau.feature.wiki.playerlevel.parser

import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PLAYER_LEVEL_PAGE_URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerLevelParsersTest {

    @Test
    fun prefersWikitextLevelsAndParsesRewardTable() {
        val page = PlayerLevelParsers.parseHtml(
            html = """
                <div class="mw-parser-output">
                  <h2><span id="等级奖励" class="mw-headline">等级奖励</span></h2>
                  <p>达到指定等级可领取奖励。</p>
                  <table class="klbqtable">
                    <tr><th>等级</th><th>道具</th><th>武器</th></tr>
                    <tr>
                      <td>10级</td>
                      <td>
                        <span class="items-icon items-quality-4">
                          <span class="items-icon-img" data-count="5">
                            <img src="https://patchwiki.biligame.com/images/klbq/thumb/a/ab/coin.png/70px-coin.png"/>
                          </span>
                          <span class="items-icon-text">金币</span>
                        </span>
                      </td>
                      <td><a href="/klbq/警探">警探</a></td>
                    </tr>
                    <tr><td colspan="3">备注：仅限一次</td></tr>
                  </table>
                </div>
            """.trimIndent(),
            wikitext = """
                ==等级经验值==
                ! 1
                | 0
                | [[文件:头像框1.png|50px]]
                ! 2
                | 1,200
                ! 999
                | 1
                ==等级奖励==
                [[分类:系统]]
            """.trimIndent()
        )

        assertEquals("玩家等级", page.title)
        assertEquals(PLAYER_LEVEL_PAGE_URL, page.wikiUrl)
        assertEquals("达到指定等级可领取奖励。", page.intro)
        assertEquals(listOf(1, 2), page.levels.map { it.level })
        assertEquals(0, page.levels[0].requiredExp)
        assertEquals(
            "https://wiki.biligame.com/klbq/Special:Redirect/file/%E5%A4%B4%E5%83%8F%E6%A1%861.png",
            page.levels[0].frameImageUrl
        )
        assertEquals(1200, page.levels[1].requiredExp)
        assertEquals(page.levels[0].frameImageUrl, page.levels[1].frameImageUrl)

        val reward = page.rewards.single()
        assertEquals(10, reward.level)
        assertEquals("金币", reward.items.single().name)
        assertEquals(5, reward.items.single().count)
        assertEquals(4, reward.items.single().quality)
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/a/ab/coin.png",
            reward.items.single().iconUrl
        )
        assertEquals("警探", reward.weapons.single().name)
        assertEquals("https://wiki.biligame.com/klbq/警探", reward.weapons.single().wikiUrl)
        assertEquals("备注：仅限一次", page.note)
    }

    @Test
    fun fallsBackToHtmlLevelsWhenWikitextMissing() {
        val page = PlayerLevelParsers.parseHtml(
            """
            <div class="mw-parser-output">
              <h2><span id="等级经验值" class="mw-headline">等级经验值</span></h2>
              <table class="klbqtable">
                <tr><th>等级</th><th>经验</th><th>头像框</th></tr>
                <tr>
                  <th>3</th>
                  <td>2,000</td>
                  <td><img src="https://patchwiki.biligame.com/images/klbq/thumb/1/11/frame.png/50px-frame.png"/></td>
                </tr>
                <tr><th>0</th><td>1</td><td></td></tr>
              </table>
            </div>
            """.trimIndent()
        )

        assertEquals(listOf(3), page.levels.map { it.level })
        assertEquals(2000, page.levels.single().requiredExp)
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/1/11/frame.png",
            page.levels.single().frameImageUrl
        )
        assertNull(page.note)
    }
}
