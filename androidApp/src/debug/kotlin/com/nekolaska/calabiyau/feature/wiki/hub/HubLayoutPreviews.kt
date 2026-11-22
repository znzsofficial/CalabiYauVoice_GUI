package com.nekolaska.calabiyau.feature.wiki.hub

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.feature.wiki.map.api.MapListApi
import com.nekolaska.calabiyau.feature.wiki.map.model.GameModeData
import com.nekolaska.calabiyau.feature.wiki.map.model.MapInfo
import data.ApiResult
import data.ErrorKind

// Exercise the production layouts without starting Hub requests, preferences, or platform glass effects.
@Preview(name = "320dp · 大字号", widthDp = 320, heightDp = 850, fontScale = 1.5f, locale = "zh-rCN")
@Preview(name = "600dp · 深色", widthDp = 600, heightDp = 680, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "zh-rCN")
annotation class HubLayoutSizes

@Preview(name = "壁纸顶栏 · 展开", widthDp = 360, heightDp = 220, locale = "zh-rCN")
@Composable
internal fun HomeTopBarExpandedPreview() = WallpaperTopBarPreview(collapsed = false)

@Preview(name = "壁纸顶栏 · 折叠", widthDp = 360, heightDp = 220, locale = "zh-rCN")
@Composable
internal fun HomeTopBarCollapsedPreview() = WallpaperTopBarPreview(collapsed = true)

@Composable
private fun WallpaperTopBarPreview(collapsed: Boolean) = PreviewTheme {
    val limit = with(LocalDensity.current) {
        (TopAppBarDefaults.LargeAppBarCollapsedHeight - TopAppBarDefaults.LargeAppBarExpandedHeight).toPx()
    }
    val state = rememberTopAppBarState(initialHeightOffsetLimit = limit, initialHeightOffset = if (collapsed) limit else 0f)
    CompositionLocalProvider(LocalHasWallpaper provides true) {
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Color(0xFFE5B457))
            drawRect(Color(0xFF527AAC), size = Size(size.width / 2, size.height))
        }) {
            WikiHomeTopBar(TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state), showDrawerButton = true, onOpenDrawer = {})
        }
    }
}

@HubLayoutSizes
@Composable
internal fun HomeDirectoryPreview() = PreviewTheme {
    val modes = MapListApi.GAME_MODES.map { (title, template) -> GameModeData(title, template, emptyList()) }
    Column(Modifier.padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
        HomePreviewCard(true, {}, emptyBackdrop(), onOpenAll = {}) { MapModePicker(modes, 0, true, {}) }
        androidx.compose.foundation.layout.BoxWithConstraints {
            HomeCategoryDirectory({}, emptyBackdrop(), homeLinkColumns(maxWidth, LocalDensity.current.fontScale))
        }
    }
}

@HubLayoutSizes
@Composable
internal fun ExtractionDirectoryPreview() = PreviewTheme {
    ExtractionHubScreen({}, {}, {}, emptyBackdrop())
}

@Preview(name = "地图一览 · 缺图与长名称", widthDp = 364, heightDp = 500, fontScale = 2f, locale = "zh-rCN")
@Composable
internal fun MapGridCaptionPreview() = PreviewTheme {
    // Use a named preview group rather than asserting that historical map names belong to the same game mode.
    val examples = listOf("科斯玛能源中心", "空间实验室").map { MapInfo(it, "", "") }
    val modes = listOf(GameModeData("示例地图", "示例地图", examples))
    MapListFullScreen({}, { _, _ -> }, modes, isLoading = false, selectedTab = 0, onTabChanged = {})
}

@Preview(name = "地图一览 · 无数据", widthDp = 360, heightDp = 500, locale = "zh-rCN")
@Composable
internal fun MapDataMissingPreview() = PreviewTheme {
    MapListFullScreen({}, { _, _ -> }, emptyList(), isLoading = false,
        error = ApiResult.Error("未找到地图数据", kind = ErrorKind.NOT_FOUND), selectedTab = 0, onTabChanged = {})
}

@Preview(name = "地图一览 · 360dp", widthDp = 360, heightDp = 500, locale = "zh-rCN")
@Preview(name = "地图一览 · 364dp", widthDp = 364, heightDp = 500, locale = "zh-rCN")
@Composable
internal fun MapModeBrowsePreview() = PreviewTheme {
    val modes = MapListApi.GAME_MODES.map { (title, template) ->
        val names = if (template == "一般爆破") listOf("莱布伦城", "奥卡努斯", "科斯迷特", "柯西街区") else emptyList()
        GameModeData(title, template, names.map { MapInfo(it, "", "") })
    }
    MapListFullScreen({}, { _, _ -> }, modes, isLoading = false, selectedTab = 0, onTabChanged = {})
}

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(content = content)
    }
}
