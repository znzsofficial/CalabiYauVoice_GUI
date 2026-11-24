package com.nekolaska.calabiyau.feature.wiki.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nekolaska.calabiyau.feature.wiki.game.api.GameModeApi
import com.nekolaska.calabiyau.feature.wiki.game.model.GameModeDetail
import com.nekolaska.calabiyau.feature.wiki.game.model.ModeEntry
import com.nekolaska.calabiyau.feature.wiki.extraction.model.extractionArticleUrl
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import util.wikiPathEncode
import androidx.compose.runtime.saveable.rememberSaveable

// ════════════════════════════════════════════════════════
//  战斗模式页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

@Composable
fun GameModeScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    onOpenMapDetail: ((name: String, imageUrl: String?) -> Unit)? = null,
    focusedMode: ModeEntry? = null,
    onOpenExtractionMode: (() -> Unit)? = null
) {
    val state = rememberLoadState(
        initial = emptyList<GameModeDetail>(),
        key = focusedMode,
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { GameModeApi.fetchModes(focusedMode, cacheOnly = true) },
        fetch = { force -> GameModeApi.fetchModes(focusedMode, forceRefresh = force, allowMemoryCache = false) }
    )
    var expandedMode by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (focusedMode == null) "战斗模式" else "模式介绍", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
                },
                actions = {
                    if (focusedMode != null) RefreshActionButton(onClick = { state.reload(forceRefresh = true) }, enabled = !state.isLoading)
                    OpenWikiActionButton(
                        wikiUrl = extractionArticleUrl(focusedMode?.pageName ?: "战斗模式"),
                        onOpenWikiUrl = onOpenWikiUrl,
                        contentDescription = "查看 Wiki 原文"
                    )
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            loading = { mod -> if (focusedMode != null) ModeReaderSkeleton(mod) else GameModeSkeleton(mod) }
        ) { modes ->
            if (focusedMode != null) {
                modes.firstOrNull { it.name == focusedMode.displayName }?.let { mode ->
                    ModeReader(mode, onMap = { name ->
                        if (onOpenMapDetail != null) onOpenMapDetail(name, null)
                        else onOpenWikiUrl("https://wiki.biligame.com/klbq/${name.wikiPathEncode()}")
                    }, onOpenLink = { url ->
                        val map = mode.maps.firstOrNull { extractionArticleUrl(it) == url }
                        if (map != null && onOpenMapDetail != null) onOpenMapDetail(map, null) else onOpenWikiUrl(url)
                    })
                }
                return@ApiResourceContent
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(modes, key = { it.name }) { mode ->
                    GameModeCard(
                        mode = mode,
                        isExpanded = expandedMode == mode.name,
                        onToggle = {
                            expandedMode = if (expandedMode == mode.name) null else mode.name
                        },
                        onOpenWikiUrl = onOpenWikiUrl,
                        onOpenMapDetail = onOpenMapDetail,
                        onReadMode = onOpenExtractionMode.takeIf { mode.name == GameModeApi.EXTRACTION_MODE.displayName }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun GameModeCard(
    mode: GameModeDetail,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    onOpenMapDetail: ((name: String, imageUrl: String?) -> Unit)?,
    onReadMode: (() -> Unit)?
) {
    Card(
        shape = smoothCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 标题行（可点击展开/折叠）
            Surface(
                onClick = onToggle,
                shape = smoothCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = smoothCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            modeIcon(mode.name),
                            contentDescription = null,
                            modifier = Modifier.padding(9.dp).size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            mode.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (mode.summary.isNotBlank()) {
                            Text(
                                mode.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Surface(shape = smoothCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Icon(
                            if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.padding(6.dp).size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 展开内容
            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 获胜条件
                if (mode.winCondition.isNotBlank()) {
                    ModeSectionTitle(Icons.Outlined.EmojiEvents, "获胜条件")
                    InfoTextPanel(mode.winCondition)
                }

                // 模式设定
                if (mode.settings.isNotBlank()) {
                    ModeSectionTitle(Icons.Outlined.Settings, "模式设定")
                    InfoTextPanel(mode.settings)
                }

                // 可用地图
                if (mode.maps.isNotEmpty()) {
                    ModeSectionTitle(Icons.Outlined.Map, "可用地图")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        mode.maps.forEach { mapName ->
                            FilledTonalButton(
                                onClick = {
                                    if (onOpenMapDetail != null) {
                                        onOpenMapDetail(mapName, null)
                                    } else {
                                        val enc = mapName.wikiPathEncode()
                                        onOpenWikiUrl("https://wiki.biligame.com/klbq/$enc")
                                    }
                                },
                                shape = smoothCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(mapName, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // 查看完整页面
                Surface(
                    onClick = onReadMode ?: { onOpenWikiUrl(mode.wikiUrl) },
                    shape = smoothCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Article, null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "查看完整说明",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = smoothCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(icon, null, Modifier.padding(6.dp).size(17.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun InfoTextPanel(text: String) {
    Surface(
        shape = smoothCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )
    }
}

/** 根据模式名返回对应图标 */
private fun modeIcon(name: String): ImageVector {
    return when (name) {
        "一般爆破" -> Icons.Outlined.LocalFireDepartment
        "团队乱斗" -> Icons.Outlined.Groups
        "排位爆破" -> Icons.Outlined.EmojiEvents
        "个人乱斗" -> Icons.Outlined.Person
        "无限团竞" -> Icons.Outlined.AllInclusive
        "极限推进" -> Icons.Outlined.RocketLaunch
        "超弦推进" -> Icons.Outlined.PhoneAndroid
        "晶源感染" -> Icons.Outlined.Coronavirus
        "极限刀战" -> Icons.Outlined.ContentCut
        "枪王乱斗" -> Icons.Outlined.GpsFixed
        "晶能冲突" -> Icons.Outlined.Bolt
        "弦区争夺" -> Icons.Outlined.Flag
        "大头乱斗" -> Icons.Outlined.Face
        "炸弹派对" -> Icons.Outlined.Whatshot
        "弦溯战线" -> Icons.Outlined.Explore
        else -> Icons.Outlined.SportsEsports
    }
}
