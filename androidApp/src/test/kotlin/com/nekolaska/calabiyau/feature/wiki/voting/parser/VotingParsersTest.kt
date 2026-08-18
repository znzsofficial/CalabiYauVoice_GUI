package com.nekolaska.calabiyau.feature.wiki.voting.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VotingParsersTest {

    @Test
    fun parsePollConfigReadsInfoAndDecodesCandidate() {
        val config = VotingParsers.parsePollConfigFromHtml(
            """
            <div class="kqp-poll-container">
              <div class="poll-info">名称：最佳角色<br/>限制票数：3<br/>结束时间：2026-01-01</div>
              <input type="checkbox" value="米雪儿&middot;李"/>
              <div class="card-content"><img src="https://x/m.png"></div>
            </div>
            """.trimIndent()
        )!!

        assertEquals("最佳角色", config.name)
        assertEquals(3, config.voteLimit)
        assertEquals("2026-01-01", config.endTime)
        assertEquals("米雪儿·李", config.candidates.single().name)
        assertEquals("https://x/m.png", config.candidates.single().imageUrl)
    }

    @Test
    fun parsePollConfigReturnsNullWithoutContainerOrCards() {
        assertNull(VotingParsers.parsePollConfigFromHtml("<div>no poll</div>"))
        assertNull(
            VotingParsers.parsePollConfigFromHtml(
                """<div class="kqp-poll-container"><div class="poll-info">名称：空</div></div>"""
            )
        )
    }

    @Test
    fun parseAjaxPollPrefersSpanVotesAndMarksUserVoted() {
        val polls = VotingParsers.parseAjaxPollElements(
            """
            <div class="ajaxpoll-container-1">
              <span class="poll-id ABC123"></span>
              <div answer="1">
                <div class="ajaxpoll-our-vote"></div>
                <div class="ajaxpoll-answer-vote"><span>42</span></div>
              </div>
              <div class="ajaxpoll-info">共有99 人投票</div>
            </div>
            <div class="ajaxpoll-container-2">
              <span class="poll-id DEF456"></span>
              <div answer="1"></div>
              <div class="ajaxpoll-info">共有7 人投票</div>
            </div>
            """.trimIndent()
        )

        assertEquals(2, polls.size)
        assertEquals("ABC123", polls[0].pollId)
        assertEquals(42, polls[0].votes)
        assertTrue(polls[0].userVoted)
        assertEquals("DEF456", polls[1].pollId)
        assertEquals(7, polls[1].votes)
        assertFalse(polls[1].userVoted)
    }
}
