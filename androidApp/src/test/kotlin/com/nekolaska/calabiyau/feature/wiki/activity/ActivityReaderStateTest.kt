package com.nekolaska.calabiyau.feature.wiki.activity

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock.*
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityEntry
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

class ActivityReaderStateTest {
    private class NoUiApplier : AbstractApplier<Unit>(Unit) {
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    @Test fun wikiHandoffKeepsReadingPositionAndRejectsCallbacksFromThePreviousWindow() {
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(NoUiApplier(), recomposer)
        val event = ActivityEntry("欢迎光临卡邦尼！", "2026年11月24日 06:00", "2026年12月29日 05:59", "", detailPageTitle = "欢迎光临卡邦尼！")
        val selection = mutableStateOf<ActivityEntry?>(event)
        lateinit var scroll: LazyListState
        var window: ActivitySheetVisit? = null
        var covered = false
        fun render(showHub: Boolean) {
            window = null
            composition.setContent {
                selection.value?.let { entry ->
                    key(entry.detailPageTitle) {
                        ActivityReaderWindow(isActive = { showHub && !covered && selection.value === entry }) { position, visit ->
                            scroll = position
                            window = visit
                        }
                    }
                }
            }
        }
        try {
            render(true)
            scroll.requestScrollToItem(28, 17)
            val outgoing = assertNotNull(window)
            outgoing.leave { covered = true }
            outgoing.leave { selection.value = null } // late dismiss, even before recomposition
            render(false)
            assertNull(window)
            covered = false
            render(true)
            assertEquals(event, selection.value)
            assertEquals(28, scroll.firstVisibleItemIndex)
            assertEquals(17, scroll.firstVisibleItemScrollOffset)
            outgoing.leave { fail("Previous modal reopened Wiki after return") }

            // A drawer or another route can also cover the reader without a Wiki handoff.
            val interrupted = assertNotNull(window)
            covered = true
            interrupted.leave { fail("Dismiss from underneath the overlay") }
            render(false)
            covered = false
            render(true)
            interrupted.leave { fail("Callback from a disposed window") }
            assertNotNull(window).leave { selection.value = null }
            render(true)
            assertNull(window)
            selection.value = event
            render(true)
            assertEquals(0, scroll.firstVisibleItemIndex, "An explicitly dismissed article opens fresh")
            assertEquals(0, scroll.firstVisibleItemScrollOffset)
        } finally {
            composition.dispose()
            recomposer.close()
        }
    }

    @Test fun restoredReaderHasItsOwnWikiLinkEvenBeforeTheIndexLoads() {
        val event = ActivityEntry("欢迎光临卡邦尼！", "2026年11月24日 06:00", "2026年12月29日 05:59", "活动期间完成每周挑战获取邦尼特大汉堡玩具及卡邦尼狂欢券，即可解锁奖励及活动剧情。",
            wikiUrl = "https://wiki.biligame.com/klbq/欢迎光临卡邦尼！", detailPageTitle = "欢迎光临卡邦尼！")
        with(selectedActivitySaver) {
            val saved = assertNotNull(SaverScope { true }.save(event))
            assertEquals(event, restore(saved))
        }
    }

    @Test fun contentsJumpToExactBlocksAndRewardRowsUseTheirOwnColumnNames() {
        val taskHeader = listOf("任务", "获得邦尼特大汉堡玩具")
        val rewardHeader = listOf("场景", "奖励")
        val blocks = listOf(Heading("每周任务"), TableRow(taskHeader, true), TableRow(listOf("累计登录1天", "50"), false),
            Heading("奖励"), TableRow(rewardHeader, true), TableRow(listOf("累计获得800个邦尼特大汉堡玩具", "聊天气泡：邦邦快送"), false),
            Heading("邦尼秘闻"), Heading("汐【打工吧！引航者大人！】"), Paragraph("今日特供：着急忙慌，些微甜酸的风味，为您定制的专属套餐。"))
        for (hasIntro in listOf(false, true)) {
            val index = ActivityArticleIndex(blocks, hasIntro)
            val lazyItems = (if (hasIntro) listOf(Paragraph("简介")) else emptyList()) + blocks
            assertEquals(blocks.filterIsInstance<Heading>(), index.headings.map { lazyItems[it.index] })
            assertEquals(mapOf(2 to taskHeader, 5 to rewardHeader), index.rowLabels)
        }
    }

    @Test fun repeatedAnniversaryHeadingsRemainSeparateJumpTargets() {
        val blocks = listOf(Heading("踏浪疾行"), Heading("签到奖励"), Paragraph("第一个活动"),
            Heading("海滨竞速俱乐部"), Heading("签到奖励"), Paragraph("第二个活动"))
        val index = ActivityArticleIndex(blocks, hasIntro = true)
        assertEquals(listOf("踏浪疾行", "签到奖励（1/2）", "海滨竞速俱乐部", "签到奖励（2/2）"), index.contentsLabels)
        assertEquals(listOf(1, 2, 4, 5), index.headings.map { it.index })
    }
}
