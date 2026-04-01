package com.nekolaska.calabiyau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.MainViewModel
import com.nekolaska.calabiyau.data.WikiUserApi
import kotlinx.coroutines.launch

/** 侧栏导航目的地 */
enum class DrawerDestination {
    DOWNLOADER,        // 资源下载 (主页)
    WIKI,              // Wiki 浏览器
    FILE_MANAGER,      // 文件管理
    DOWNLOAD_HISTORY,  // 下载历史
    SETTINGS           // 设置
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentDestination by remember { mutableStateOf(DrawerDestination.DOWNLOADER) }

    // 返回键：侧栏打开时先关闭侧栏；在 Wiki/设置页面时返回主页
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }
    BackHandler(enabled = currentDestination != DrawerDestination.DOWNLOADER && !drawerState.isOpen) {
        currentDestination = DrawerDestination.DOWNLOADER
    }

    // 切换页面时停止音频播放
    LaunchedEffect(currentDestination) {
        AudioPlayerManager.stop()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentDestination != DrawerDestination.WIKI,
        drawerContent = {
            AppDrawerContent(
                currentDestination = currentDestination,
                onDestinationSelected = { dest ->
                    currentDestination = dest
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        // 根据侧栏选中项切换页面
        when (currentDestination) {
            DrawerDestination.DOWNLOADER -> {
                DownloaderScreen(
                    viewModel = viewModel,
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    onOpenFileManager = { currentDestination = DrawerDestination.FILE_MANAGER }
                )
            }
            DrawerDestination.WIKI -> {
                WikiWebViewScreen(
                    onExitWiki = { currentDestination = DrawerDestination.DOWNLOADER }
                )
            }
            DrawerDestination.FILE_MANAGER -> {
                FileManagerScreen(
                    rootPath = com.nekolaska.calabiyau.data.AppPrefs.savePath,
                    onBack = { currentDestination = DrawerDestination.DOWNLOADER }
                )
            }
            DrawerDestination.DOWNLOAD_HISTORY -> {
                DownloadHistoryScreen(
                    viewModel = viewModel,
                    onBack = { currentDestination = DrawerDestination.DOWNLOADER }
                )
            }
            DrawerDestination.SETTINGS -> {
                SettingsScreen(onBack = { currentDestination = DrawerDestination.DOWNLOADER })
            }
        }
    }
}

// ─────────────────────── 侧栏内容 ───────────────────────

@Composable
private fun AppDrawerContent(
    currentDestination: DrawerDestination,
    onDestinationSelected: (DrawerDestination) -> Unit
) {
    // ── Wiki 用户信息状态（提升到 ModalDrawerSheet 外，底部弹窗也能访问） ──
    val hasLoginCookie = remember { mutableStateOf(hasWikiLoginCookie()) }
    var wikiUserInfo by remember { mutableStateOf<WikiUserApi.UserInfo?>(null) }
    var isLoadingUserInfo by remember { mutableStateOf(false) }
    var showUserInfoSheet by remember { mutableStateOf(false) }
    val wikiCoroutineScope = rememberCoroutineScope()

    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(300.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // 标题
        Text(
            text = "卡拉彼丘",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
        )

        // 每次侧栏显示时刷新登录状态
        LaunchedEffect(currentDestination) {
            hasLoginCookie.value = hasWikiLoginCookie()
            if (hasLoginCookie.value && wikiUserInfo == null && !isLoadingUserInfo) {
                isLoadingUserInfo = true
                wikiCoroutineScope.launch {
                    when (val result = WikiUserApi.fetchCurrentUserInfo()) {
                        is WikiUserApi.ApiResult.Success -> {
                            val info = result.value
                            if (info != null && info.isLoggedIn) {
                                wikiUserInfo = info
                            }
                        }
                        is WikiUserApi.ApiResult.Error -> { /* 忽略错误 */ }
                    }
                    isLoadingUserInfo = false
                }
            }
            if (!hasLoginCookie.value) {
                wikiUserInfo = null
                showUserInfoSheet = false
            }
        }

        if (wikiUserInfo != null) {
            WikiUserInfoCard(
                userInfo = wikiUserInfo!!,
                onClick = { showUserInfoSheet = true },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            )
        } else if (isLoadingUserInfo) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 28.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "正在获取用户信息…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp))

        Spacer(Modifier.height(8.dp))

        // 1. 资源下载
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Download, contentDescription = null) },
            label = { Text("资源下载") },
            selected = currentDestination == DrawerDestination.DOWNLOADER,
            onClick = { onDestinationSelected(DrawerDestination.DOWNLOADER) },
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = RoundedCornerShape(28.dp)
        )

        Spacer(Modifier.height(4.dp))

        // 2. Wiki 浏览器
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
            label = { Text("Wiki") },
            badge = {
                if (hasLoginCookie.value) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "已登录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = "未登录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            selected = currentDestination == DrawerDestination.WIKI,
            onClick = { onDestinationSelected(DrawerDestination.WIKI) },
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = RoundedCornerShape(28.dp)
        )

        Spacer(Modifier.height(4.dp))

        // 3. 文件管理
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
            label = { Text("文件管理") },
            selected = currentDestination == DrawerDestination.FILE_MANAGER,
            onClick = { onDestinationSelected(DrawerDestination.FILE_MANAGER) },
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = RoundedCornerShape(28.dp)
        )

        Spacer(Modifier.height(4.dp))

        // 4. 下载历史
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.History, contentDescription = null) },
            label = { Text("下载历史") },
            selected = currentDestination == DrawerDestination.DOWNLOAD_HISTORY,
            onClick = { onDestinationSelected(DrawerDestination.DOWNLOAD_HISTORY) },
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = RoundedCornerShape(28.dp)
        )

        Spacer(Modifier.height(4.dp))

        // 5. 设置
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            label = { Text("设置") },
            selected = currentDestination == DrawerDestination.SETTINGS,
            onClick = { onDestinationSelected(DrawerDestination.SETTINGS) },
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = RoundedCornerShape(28.dp)
        )

        Spacer(Modifier.weight(1f))

        // 底部版本信息
        Text(
            text = "v2.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
        )
    }

    // ── 用户信息底部弹窗 ──
    if (showUserInfoSheet && wikiUserInfo != null) {
        WikiUserInfoBottomSheet(
            userInfo = wikiUserInfo!!,
            onDismiss = { showUserInfoSheet = false }
        )
    }
}

// ─────────────────────── Wiki 用户信息卡片（侧栏摘要） ───────────────────────

@Composable
private fun WikiUserInfoCard(
    userInfo: WikiUserApi.UserInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 头像圆圈（首字母）
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = userInfo.name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userInfo.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "编辑 ${userInfo.editCount} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─────────────────────── Wiki 用户信息底部弹窗 ───────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WikiUserInfoBottomSheet(
    userInfo: WikiUserApi.UserInfo,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("个人信息", "我的编辑", "监视列表")

    // 编辑贡献状态
    var contributions by remember { mutableStateOf<List<WikiUserApi.UserContrib>>(emptyList()) }
    var isLoadingContribs by remember { mutableStateOf(false) }
    var contribError by remember { mutableStateOf<String?>(null) }

    // 监视列表状态
    var watchlist by remember { mutableStateOf<List<WikiUserApi.WatchlistItem>>(emptyList()) }
    var isLoadingWatchlist by remember { mutableStateOf(false) }
    var watchlistError by remember { mutableStateOf<String?>(null) }

    val activityScope = rememberCoroutineScope()

    // 切到编辑贡献 Tab 时自动加载
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && contributions.isEmpty() && !isLoadingContribs) {
            isLoadingContribs = true
            contribError = null
            activityScope.launch {
                when (val result = WikiUserApi.fetchContributions(userInfo.name, limit = 30)) {
                    is WikiUserApi.ApiResult.Success -> contributions = result.value
                    is WikiUserApi.ApiResult.Error -> contribError = result.message
                }
                isLoadingContribs = false
            }
        }
        if (selectedTab == 2 && watchlist.isEmpty() && !isLoadingWatchlist) {
            isLoadingWatchlist = true
            watchlistError = null
            activityScope.launch {
                when (val result = WikiUserApi.fetchWatchlist(limit = 30)) {
                    is WikiUserApi.ApiResult.Success -> watchlist = result.value
                    is WikiUserApi.ApiResult.Error -> watchlistError = result.message
                }
                isLoadingWatchlist = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // 头像 + 用户名（始终可见）
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = userInfo.name.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Column {
                    Text(
                        text = userInfo.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Wiki ID: ${userInfo.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tab 栏
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, maxLines = 1) }
                    )
                }
            }

            // Tab 内容
            when (selectedTab) {
                0 -> UserInfoTabContent(userInfo = userInfo)
                1 -> ActivityListContent(
                    items = contributions.map { contrib ->
                        ActivityItem(
                            id = contrib.revId,
                            title = contrib.title,
                            summary = contrib.comment.ifBlank { null },
                            timestamp = contrib.timestamp,
                            badge = buildString {
                                if (contrib.isNewPage) append("新建 ")
                                if (contrib.isMinor) append("小编辑 ")
                                val diff = contrib.sizeDiff
                                if (diff > 0) append("+$diff") else if (diff < 0) append("$diff")
                            }.trim().ifBlank { null },
                            badgePositive = contrib.sizeDiff >= 0
                        )
                    },
                    isLoading = isLoadingContribs,
                    error = contribError,
                    emptyIcon = Icons.Outlined.Edit,
                    emptyText = "暂无编辑记录",
                    loadingText = "正在加载编辑记录…",
                    onRetry = {
                        isLoadingContribs = true
                        contribError = null
                        activityScope.launch {
                            when (val result = WikiUserApi.fetchContributions(userInfo.name, limit = 30)) {
                                is WikiUserApi.ApiResult.Success -> contributions = result.value
                                is WikiUserApi.ApiResult.Error -> contribError = result.message
                            }
                            isLoadingContribs = false
                        }
                    }
                )
                2 -> ActivityListContent(
                    items = watchlist.map { wl ->
                        ActivityItem(
                            id = wl.revId,
                            title = wl.title,
                            summary = wl.comment.ifBlank { null },
                            timestamp = wl.timestamp,
                            badge = WikiUserApi.watchTypeLabel(wl.type),
                            badgePositive = true,
                            subtitle = wl.user.ifBlank { null }
                        )
                    },
                    isLoading = isLoadingWatchlist,
                    error = watchlistError,
                    emptyIcon = Icons.Outlined.Visibility,
                    emptyText = "监视列表为空",
                    loadingText = "正在加载监视列表…",
                    onRetry = {
                        isLoadingWatchlist = true
                        watchlistError = null
                        activityScope.launch {
                            when (val result = WikiUserApi.fetchWatchlist(limit = 30)) {
                                is WikiUserApi.ApiResult.Success -> watchlist = result.value
                                is WikiUserApi.ApiResult.Error -> watchlistError = result.message
                            }
                            isLoadingWatchlist = false
                        }
                    }
                )
            }
        }
    }
}

// ─────────────────────── 个人信息 Tab ───────────────────────

@Composable
private fun UserInfoTabContent(userInfo: WikiUserApi.UserInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 用户组标签
        if (userInfo.displayGroups.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                userInfo.displayGroups.forEach { group ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = WikiUserApi.groupLabel(group),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 详细信息
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UserDetailRow(label = "编辑次数", value = "${userInfo.editCount}")

            if (userInfo.registrationDate.isNotBlank()) {
                UserDetailRow(
                    label = "注册时间",
                    value = WikiUserApi.formatTimestamp(userInfo.registrationDate)
                )
            }

            if (userInfo.email.isNotBlank()) {
                UserDetailRow(label = "邮箱", value = userInfo.email)
            }

            if (userInfo.realName.isNotBlank()) {
                UserDetailRow(label = "真实姓名", value = userInfo.realName)
            }
        }
    }
}

// ─────────────────────── 通用活动列表数据 ───────────────────────

private data class ActivityItem(
    val id: Long,
    val title: String,
    val summary: String? = null,
    val timestamp: String = "",
    val badge: String? = null,
    val badgePositive: Boolean = true,
    val subtitle: String? = null         // 操作者（监视列表用）
)

// ─────────────────────── 通用活动列表 Tab ───────────────────────

@Composable
private fun ActivityListContent(
    items: List<ActivityItem>,
    isLoading: Boolean,
    error: String?,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyText: String,
    loadingText: String,
    onRetry: () -> Unit
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    Text(
                        text = loadingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        error != null -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onRetry) {
                        Text("重试")
                    }
                }
            }
        }
        items.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = emptyIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ActivityItemCard(item)
                }
            }
        }
    }
}

// ─────────────────────── 单条活动卡片 ───────────────────────

@Composable
private fun ActivityItemCard(item: ActivityItem) {
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
    val time = if (item.timestamp.isNotBlank()) WikiUserApi.formatTimestamp(item.timestamp) else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // 页面标题 + 变更标签
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!item.badge.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.badgePositive)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = item.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.badgePositive)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 摘要/评论
            if (!item.summary.isNullOrBlank()) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 底部：操作者 + 时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!item.subtitle.isNullOrBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (time.isNotBlank()) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun UserDetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
