package com.nekolaska.calabiyau.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.data.NavigationMenuApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationMenuScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    embedded: Boolean = false
) {
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sections by remember { mutableStateOf<List<NavigationMenuApi.NavSection>>(emptyList()) }
    var expandedSections by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun loadNavigation() {
        scope.launch {
            isLoading = true
            errorMessage = null
            when (val result = NavigationMenuApi.fetchNavigationSections()) {
                is NavigationMenuApi.ApiResult.Success -> {
                    sections = result.value
                    // 默认只展开"首页"
                    expandedSections = setOf("首页")
                }
                is NavigationMenuApi.ApiResult.Error -> {
                    sections = emptyList()
                    errorMessage = result.message
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadNavigation() }

    Scaffold(
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = {
                        Text("导航", fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { loadNavigation() }, enabled = !isLoading) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("正在加载 Wiki 导航…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                errorMessage != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            FilledTonalButton(onClick = { loadNavigation() }) {
                                Text("重试")
                            }
                        }
                    }
                }

                sections.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无可用导航数据", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sections, key = { it.title }) { section ->
                            val isExpanded = section.title in expandedSections

                            ElevatedCard(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedSections = if (isExpanded) {
                                                    expandedSections - section.title
                                                } else {
                                                    expandedSections + section.title
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = section.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = if (isExpanded) "折叠" else "展开"
                                        )
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
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
                    }
                }
            }
        }
    }
}

@Composable
private fun NavNodeRow(
    item: NavigationMenuApi.NavItem,
    level: Int,
    onOpenWikiUrl: (String) -> Unit
) {
    val indent = (12 + level * 14).dp

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = item.url != null) {
                    item.url?.let(onOpenWikiUrl)
                }
                .padding(start = indent, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title,
                style = if (level == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = if (item.url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (item.url != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item.children.forEach { child ->
            NavNodeRow(item = child, level = level + 1, onOpenWikiUrl = onOpenWikiUrl)
        }
    }
}
