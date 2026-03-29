package com.nekolaska.calabiyau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
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
    val sheetState = rememberModalBottomSheetState()

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
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 头像 + 用户名
            Row(
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
