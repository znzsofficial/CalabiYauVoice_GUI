package com.nekolaska.calabiyau.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.data.GameModeApi
import com.nekolaska.calabiyau.data.GameModeApi.GameModeDetail
import kotlinx.coroutines.launch
import data.ApiResult

// ════════════════════════════════════════════════════════
//  战斗模式页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameModeScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    onOpenMapDetail: ((name: String, imageUrl: String?) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var modes by remember { mutableStateOf<List<GameModeDetail>>(emptyList()) }
    var expandedMode by remember { mutableStateOf<String?>(null) }

    fun loadData(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            errorMessage = null
            when (val result = GameModeApi.fetchAllModes(forceRefresh)) {
                is ApiResult.Success -> modes = result.value
                is ApiResult.Error -> errorMessage = result.message
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("战斗模式", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onOpenWikiUrl("https://wiki.biligame.com/klbq/%E6%88%98%E6%96%97%E6%A8%A1%E5%BC%8F")
                    }) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = "在浏览器中打开")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在加载模式信息…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            errorMessage != null && modes.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Text(errorMessage!!, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { loadData(forceRefresh = true) }) {
                            Icon(Icons.Outlined.Refresh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("重试")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
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
                            onOpenMapDetail = onOpenMapDetail
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameModeCard(
    mode: GameModeDetail,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    onOpenMapDetail: ((name: String, imageUrl: String?) -> Unit)?
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            // 标题行（可点击展开/折叠）
            Surface(
                onClick = onToggle,
                shape = RoundedCornerShape(12.dp),
                color = androidx.compose.ui.graphics.Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modeIcon(mode.name),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
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
                    Icon(
                        if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 展开内容
            if (isExpanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 获胜条件
                if (mode.winCondition.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    ModeSectionTitle(Icons.Outlined.EmojiEvents, "获胜条件")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        mode.winCondition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 模式设定
                if (mode.settings.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    ModeSectionTitle(Icons.Outlined.Settings, "模式设定")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        mode.settings,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 可用地图
                if (mode.maps.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    ModeSectionTitle(Icons.Outlined.Map, "可用地图")
                    Spacer(Modifier.height(8.dp))
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
                                        val enc = java.net.URLEncoder.encode(mapName, "UTF-8")
                                            .replace("+", "%20")
                                        onOpenWikiUrl("https://wiki.biligame.com/klbq/$enc")
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(mapName, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // 查看完整页面
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = { onOpenWikiUrl(mode.wikiUrl) },
                    shape = RoundedCornerShape(12.dp),
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
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
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
    }
}

/** 根据模式名返回对应图标 */
private fun modeIcon(name: String): ImageVector {
    return when (name) {
        "一般爆破" -> Icons.Outlined.LocalFireDepartment
        "团队乱斗" -> Icons.Outlined.Groups
        "无限团竞" -> Icons.Outlined.AllInclusive
        "极限推进" -> Icons.Outlined.RocketLaunch
        "晶源感染" -> Icons.Outlined.Coronavirus
        "极限刀战" -> Icons.Outlined.ContentCut
        "枪王乱斗" -> Icons.Outlined.GpsFixed
        "晶能冲突" -> Icons.Outlined.Bolt
        "弦区争夺" -> Icons.Outlined.Flag
        "大头乱斗" -> Icons.Outlined.Face
        else -> Icons.Outlined.SportsEsports
    }
}
