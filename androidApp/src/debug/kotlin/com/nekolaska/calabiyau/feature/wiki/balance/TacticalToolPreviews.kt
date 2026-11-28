package com.nekolaska.calabiyau.feature.wiki.balance

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceMetric
import com.nekolaska.calabiyau.feature.wiki.balance.model.CharacterMeta
import com.nekolaska.calabiyau.feature.wiki.balance.model.HeroBalanceData
import com.nekolaska.calabiyau.feature.wiki.gamesettings.PcSettingsListHeader
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingField
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsBlock
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsSection
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsView
import com.nekolaska.calabiyau.feature.wiki.gamesettings.pcSettingsItems

@Preview(name = "320dp · 大字号", widthDp = 320, heightDp = 760, fontScale = 1.5f, locale = "zh-rCN")
@Preview(name = "480dp · 深色", widthDp = 480, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "zh-rCN")
@Composable
internal fun BalanceTacticalLayoutPreview() {
    val current1 = HeroBalanceData(101, "米雪儿·李", 52.84, 18.40, 1.24, 3420.0, 108.5)
    val base1 = HeroBalanceData(101, "米雪儿·李", 51.40, 16.90, 1.18, 3290.0, 104.2)
    val current2 = HeroBalanceData(104, "明", 51.12, 22.15, 1.31, 3680.0, 112.0)
    val base2 = HeroBalanceData(104, "明", 51.90, 21.80, 1.29, 3610.0, 110.8)
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                FilterSummaryBar(
                    mode = "排位爆破",
                    season = "第9赛季",
                    map = "全部地图",
                    rankNames = listOf("无限超弦", "奇点"),
                    compareSeason = "第8赛季",
                    onOpen = {}
                )
                SideAndSortBar(
                    showAttackers = true,
                    sortField = BalanceMetric.WIN_RATE,
                    sortOrder = BalanceSortOrder.DESC,
                    scoreAvailable = true,
                    onSideToggle = {},
                    onSortChange = {}
                )
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        BalanceHeroCard(
                            rank = 1,
                            hero = current1,
                            compareHero = base1,
                            meta = CharacterMeta("101", "米雪儿·李", "守护", 1, ""),
                            positionName = "守护",
                            highlightField = BalanceMetric.WIN_RATE,
                            hasCompare = true,
                            scoreAvailable = true,
                            compareScoreAvailable = true
                        )
                    }
                    item {
                        BalanceHeroCard(
                            rank = 4,
                            hero = current2,
                            compareHero = base2,
                            meta = CharacterMeta("104", "明", "决斗", 2, ""),
                            positionName = "决斗",
                            highlightField = BalanceMetric.WIN_RATE,
                            hasCompare = true,
                            scoreAvailable = true,
                            compareScoreAvailable = true
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "320dp · 大字号", widthDp = 320, heightDp = 760, fontScale = 1.5f, locale = "zh-rCN")
@Preview(name = "480dp · 深色", widthDp = 480, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "zh-rCN")
@Composable
internal fun PcGameSettingsTacticalLayoutPreview() {
    val sections = listOf(
        PcSettingsSection(
            id = "graphics",
            title = "画面与渲染",
            view = PcSettingsView.RECOMMENDATIONS,
            blocks = listOf(
                PcSettingsBlock.Setting(
                    id = "s1",
                    subsection = "竞技帧率优先",
                    name = "抗锯齿与超分辨率",
                    fields = listOf(
                        PcSettingField("推荐", "DLSS 质量 / TSR 高（渲染比例 85%）"),
                        PcSettingField("说明", "兼顾远距离轮廓清晰度与团战帧率稳定性")
                    ),
                    recommendationCategory = "竞技帧率优先",
                    isQuickReference = true
                ),
                PcSettingsBlock.Setting(
                    id = "s2",
                    subsection = "竞技帧率优先",
                    name = "阴影质量",
                    fields = listOf(
                        PcSettingField("默认", "高"),
                        PcSettingField("作用", "调为低可显著降低 GPU 负载，不影响人物模型判定")
                    )
                ),
                PcSettingsBlock.Reference(
                    id = "r1",
                    subsection = "渲染比例对照",
                    nameLabel = "超分档位",
                    name = "DLSS / FSR 质量档",
                    fields = listOf(
                        PcSettingField("内部渲染比例", "66.7%"),
                        PcSettingField("1080p 实际基底", "1280 × 720")
                    )
                )
            )
        )
    )
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    PcSettingsListHeader(
                        view = PcSettingsView.RECOMMENDATIONS,
                        category = "画面与渲染",
                        countLabel = "2 个设置",
                        onReset = {}
                    )
                }
                pcSettingsItems(sections, query = "", onSelect = {})
            }
        }
    }
}
