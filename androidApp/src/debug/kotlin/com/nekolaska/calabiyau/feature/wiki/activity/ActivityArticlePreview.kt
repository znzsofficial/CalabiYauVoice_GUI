package com.nekolaska.calabiyau.feature.wiki.activity

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock.*
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityEntry

// Excerpts from the 120799 HTML, with the same source column titles and reward pairings.
private val event = ActivityEntry("欢迎光临卡邦尼！", "2026年11月24日 06:00", "2026年12月29日 05:59",
    "活动期间完成每周挑战获取邦尼特大汉堡玩具及卡邦尼狂欢券，即可解锁奖励及活动剧情。")
private val excerpt = listOf<ActivityDetailBlock>(
    Heading("活动规则"), Paragraph("活动期间，引航者可以完成【邦尼到家！】中的任务，获取【邦尼特大汉堡玩具】，活动任务每周刷新；"),
    Heading("每周任务"), TableRow(listOf("任务", "获得邦尼特大汉堡玩具"), true),
    TableRow(listOf("累计游戏30分钟（好友组队时共享进度）", "100"), false),
    Heading("奖励"), TableRow(listOf("场景", "奖励"), true),
    TableRow(listOf("累计获得100个邦尼特大汉堡玩具", "活动凭录 ×500"), false),
    TableRow(listOf("累计获得800个邦尼特大汉堡玩具", "聊天气泡：邦邦快送"), false),
    TableRow(listOf("累计获得1600个邦尼特大汉堡玩具", "喷漆：鹅了么"), false),
    Heading("邦尼秘闻"), Heading("汐【打工吧！引航者大人！】"),
    Paragraph("今日特供：着急忙慌，些微甜酸的风味，为您定制的专属套餐。"),
    Paragraph("Here’s punny！——\n随着引航者推开卡邦尼汉堡店的大门，迎宾铃声揭开了这次狂欢季的序幕。\n“没时间了没时间了没时间了，上吧上吧上吧！”\n活力四射的身影在一秒内冲出员工室，一头撞进了收银台，伴随着一声不知道身体哪个部位磕到柜台的巨响。"),
    Heading("诺诺【天才败北事件簿】"), Paragraph("敬请到店就餐，充满真心的服务能带来最愉悦的体验。"),
    Paragraph("卡邦尼狂欢季的最后一天让普雷顿老区的商业街重新热闹了起来。\n随着城市建设的迁移，很多商户去了更繁华的地段，这里留着的都是些没什么朝气的老店，携着特色狂欢日开放在这里的卡邦尼快闪店简直就是一条活蹦乱跳的鲶鱼。")
)

@Preview(widthDp = 320, heightDp = 540, fontScale = 1.5f, locale = "zh-rCN")
@Preview(widthDp = 480, heightDp = 540, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "zh-rCN")
@Composable
internal fun ActivityArticlePreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            val state = rememberLoadState(initial = excerpt, initiallyLoaded = true, reloadOnEnter = false,
                cachedFetch = { error("Preview") }, fetch = { error("Preview") })
            val reward = ActivityArticleIndex(excerpt, hasIntro = true).headings.first { it.value == "奖励" }
            ActivityArticle(event, state, rememberLazyListState(reward.index), isCurrent = { true }, onOpenWiki = {})
        }
    }
}
