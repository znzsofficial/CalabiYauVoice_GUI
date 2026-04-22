package com.nekolaska.calabiyau.feature.wiki.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.LoadingState
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape

// ════════════════════════════════════════════════════════
//  Wiki 导航页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

/** 根据分区标题返回对应图标 */
private fun sectionIcon(title: String): ImageVector = when {
    title.contains("首页") -> Icons.Outlined.Home
    title.contains("角色") -> Icons.Outlined.People
    title.contains("武器") -> Icons.Outlined.GpsFixed
    title.contains("地图") -> Icons.Outlined.Map
    title.contains("玩法") -> Icons.Outlined.SportsEsports
    title.contains("其他") -> Icons.Outlined.MoreHoriz
    else -> Icons.AutoMirrored.Outlined.Article
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationMenuScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    embedded: Boolean = false
) {
    val state =
        rememberLoadState(emptyList<NavigationMenuApi.NavSection>()) { force ->
            NavigationMenuApi.fetchNavigationSections(force)
        }
    val sections = state.data
    var expandedSections by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 默认展开所有分区（首次成功加载后）
    LaunchedEffect(sections) {
        if (expandedSections.isEmpty() && sections.isNotEmpty()) {
            expandedSections = sections.map { it.title }.toSet()
        }
    }

    Scaffold(
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = {
                        Text("Wiki 导航", fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        BackNavButton(onClick = onBack)
                    },
                    actions = {
                        // 全部展开/折叠切换
                        if (sections.isNotEmpty()) {
                            val allExpanded = expandedSections.size == sections.size
                            IconButton(onClick = {
                                expandedSections = if (allExpanded) emptySet()
                                else sections.map { it.title }.toSet()
                            }) {
                                Icon(
                                    if (allExpanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                                    contentDescription = if (allExpanded) "全部折叠" else "全部展开"
                                )
                            }
                        }
                        IconButton(onClick = { state.reload(forceRefresh = true) }, enabled = !state.isLoading) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> LoadingState("正在加载 Wiki 导航…", mod) }
        ) {
            if (sections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无可用导航数据", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sections, key = { it.title }) { section ->
                        NavSectionCard(
                            section = section,
                            isExpanded = section.title in expandedSections,
                            onToggle = {
                                expandedSections = if (section.title in expandedSections) {
                                    expandedSections - section.title
                                } else {
                                    expandedSections + section.title
                                }
                            },
                            onOpenWikiUrl = onOpenWikiUrl
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  分区卡片
// ────────────────────────────────────────────

@Composable
private fun NavSectionCard(
    section: NavigationMenuApi.NavSection,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val sectionShape = smoothCornerShape(24.dp)
    val icon = sectionIcon(section.title)

    Card(
        shape = sectionShape,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 分区标题行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(sectionShape)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 分区图标
                Surface(
                    shape = smoothCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${section.items.size} 个条目",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "折叠" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 展开内容
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    section.items.forEach { item ->
                        NavNodeRow(
                            item = item,
                            level = 0,
                            onOpenWikiUrl = onOpenWikiUrl
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  导航条目行（递归支持子级）
// ────────────────────────────────────────────

@Composable
private fun NavNodeRow(
    item: NavigationMenuApi.NavItem,
    level: Int,
    onOpenWikiUrl: (String) -> Unit
) {
    val indent = (level * 16).dp
    val hasChildren = item.children.isNotEmpty()
    val isClickable = item.url != null
    val itemShape = smoothCornerShape(14.dp)

    Column(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = indent),
            shape = itemShape,
            color = if (isClickable) MaterialTheme.colorScheme.surface
                    else Color.Transparent,
            onClick = { item.url?.let(onOpenWikiUrl) },
            enabled = isClickable
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 层级指示圆点
                if (level > 0) {
                    Surface(
                        modifier = Modifier.size(6.dp),
                        shape = RoundedCornerShape(50),
                        color = if (isClickable) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.outlineVariant
                    ) {}
                    Spacer(Modifier.width(10.dp))
                }

                Text(
                    text = item.title,
                    style = when (level) {
                        0 if hasChildren -> MaterialTheme.typography.titleSmall
                        0 -> MaterialTheme.typography.bodyLarge
                        else -> MaterialTheme.typography.bodyMedium
                    },
                    fontWeight = if (level == 0 && hasChildren) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isClickable) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isClickable) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 递归渲染子级
        if (hasChildren) {
            item.children.forEach { child ->
                NavNodeRow(item = child, level = level + 1, onOpenWikiUrl = onOpenWikiUrl)
            }
        }
    }
}
