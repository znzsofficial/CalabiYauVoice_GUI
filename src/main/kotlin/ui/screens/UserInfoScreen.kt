package ui.screens

import LocalAppStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.mayakapps.compose.windowstyler.WindowBackdrop
import com.mayakapps.compose.windowstyler.WindowStyle
import data.WikiUserApi
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*
import io.github.composefluent.darkColors
import io.github.composefluent.lightColors
import io.github.composefluent.surface.Card
import jna.windows.structure.isWindows11OrLater
import ui.components.WindowsWindowFrame
import ui.components.rememberWindowsWindowFrameState
import util.findSkiaLayer
import viewmodel.UserInfoTab
import viewmodel.UserInfoViewModel

@OptIn(ExperimentalFluentApi::class, ExperimentalLayoutApi::class)
@Composable
fun UserInfoWindow(onCloseRequest: () -> Unit) {
    val darkModeState = LocalAppStore.current.darkMode
    val windowState = rememberWindowState(
        width = 760.dp,
        height = 620.dp,
        position = WindowPosition(Alignment.Center)
    )

    Window(
        onCloseRequest = onCloseRequest,
        title = "用户信息",
        state = windowState,
        onKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                onCloseRequest(); true
            } else false
        }
    ) {
        val darkMode = darkModeState.value
        val windowFrameState = rememberWindowsWindowFrameState(window)
        val skiaLayerExists = remember { window.findSkiaLayer() != null }
        val isWin11 = remember { isWindows11OrLater() }

        if (skiaLayerExists && isWin11) {
            LaunchedEffect(Unit) { window.findSkiaLayer()?.transparency = true }
            WindowStyle(isDarkTheme = darkMode, backdropType = WindowBackdrop.Tabbed)
        }

        FluentTheme(colors = if (darkMode) darkColors() else lightColors(), useAcrylicPopup = true) {
            WindowsWindowFrame(
                title = "用户信息",
                onCloseRequest = onCloseRequest,
                state = windowState,
                frameState = windowFrameState,
                isDarkTheme = darkMode,
                captionBarHeight = 36.dp
            ) { windowInset, _ ->
                val coroutineScope = rememberCoroutineScope()
                val viewModel = remember { UserInfoViewModel(coroutineScope) }

                UserInfoContent(
                    viewModel = viewModel,
                    modifier = Modifier
                        .windowInsetsPadding(windowFrameState.paddingInset)
                        .windowInsetsPadding(windowInset)
                )
            }
        }
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun UserInfoContent(
    viewModel: UserInfoViewModel,
    modifier: Modifier = Modifier
) {
    val cookieInput by viewModel.cookieInput.collectAsState()
    val userInfo by viewModel.userInfo.collectAsState()
    val isLoadingInfo by viewModel.isLoadingInfo.collectAsState()
    val contributions by viewModel.contributions.collectAsState()
    val isLoadingContrib by viewModel.isLoadingContrib.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val isLoadingWatch by viewModel.isLoadingWatch.collectAsState()
    val logEvents by viewModel.logEvents.collectAsState()
    val isLoadingLog by viewModel.isLoadingLog.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    Column(modifier.fillMaxSize().padding(16.dp)) {

        // ── Cookie 输入区 ──────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("导入 Cookie", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "从浏览器开发者工具 Network 面板中复制请求的 Cookie 字段，粘贴到下方输入框",
                    fontSize = 12.sp,
                    color = FluentTheme.colors.text.text.secondary
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    TextField(
                        value = cookieInput,
                        onValueChange = { viewModel.onCookieInputChange(it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("gamecenter_wiki_UserID=…; gamecenter_wiki__session=…; SESSDATA=…") },
                        header = { Text("Cookie", fontSize = 12.sp) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.importAndFetch() },
                        disabled = isLoadingInfo
                    ) {
                        if (isLoadingInfo) ProgressRing(size = 16.dp)
                        else Text("导入并查询")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.clearCookies() }
                    ) { Text("清除") }
                }
                if (statusMessage.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        statusMessage,
                        fontSize = 12.sp,
                        color = when {
                            statusMessage.startsWith("✅") -> Color(0xFF4CAF50)
                            statusMessage.startsWith("❌") || statusMessage.startsWith("⚠️") -> Color(0xFFE57373)
                            else -> FluentTheme.colors.text.text.secondary
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 用户摘要横幅（仅已登录时显示）────────────────────────
        val info = userInfo
        if (info != null && info.isLoggedIn) {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 头像占位圆圈
                    Box(
                        Modifier.size(44.dp)
                            .clip(CircleShape)
                            .background(FluentTheme.colors.fillAccent.default),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            info.name.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        val badge = buildString {
                            val userGroups = info.groups.filter { it != "*" && it != "user" }
                            if (userGroups.isNotEmpty()) append(userGroups.joinToString(" · "))
                            else append("普通用户")
                        }
                        Text(badge, fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("编辑次数", fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary)
                        Text(
                            info.editCount.toString(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            color = FluentTheme.colors.fillAccent.default
                        )
                    }
                    if (info.registrationDate.isNotBlank()) {
                        Spacer(Modifier.width(24.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("注册时间", fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary)
                            Text(
                                WikiUserApi.formatTimestamp(info.registrationDate),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── Tab 切换 ───────────────────────────────────────────────
        if (info != null && info.isLoggedIn) {
            val tabs = listOf(
                UserInfoTab.INFO to "基本信息",
                UserInfoTab.CONTRIBUTIONS to "编辑贡献",
                UserInfoTab.WATCHLIST to "监视列表",
                UserInfoTab.LOG to "操作日志"
            )
            val tabCount = tabs.size
            SegmentedControl(modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, (tab, label) ->
                    val pos = when (index) {
                        0 -> SegmentedItemPosition.Start
                        tabCount - 1 -> SegmentedItemPosition.End
                        else -> SegmentedItemPosition.Center
                    }
                    SegmentedButton(
                        checked = currentTab == tab,
                        onCheckedChanged = { viewModel.onTabSelected(tab) },
                        position = pos,
                        modifier = Modifier.weight(1f),
                        text = { Text(label, fontSize = 13.sp) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // ── Tab 内容 ───────────────────────────────────────────
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (currentTab) {
                    UserInfoTab.INFO -> InfoTabContent(info)
                    UserInfoTab.CONTRIBUTIONS -> ListTabContent(
                        isLoading = isLoadingContrib,
                        isEmpty = contributions.isEmpty(),
                        emptyText = "暂无贡献记录",
                        onRefresh = { viewModel.fetchContributions() }
                    ) {
                        items(contributions) { contrib ->
                            ContribItem(contrib)
                        }
                    }
                    UserInfoTab.WATCHLIST -> ListTabContent(
                        isLoading = isLoadingWatch,
                        isEmpty = watchlist.isEmpty(),
                        emptyText = "监视列表为空",
                        onRefresh = { viewModel.fetchWatchlist() }
                    ) {
                        items(watchlist) { item ->
                            WatchlistItem(item)
                        }
                    }
                    UserInfoTab.LOG -> ListTabContent(
                        isLoading = isLoadingLog,
                        isEmpty = logEvents.isEmpty(),
                        emptyText = "暂无操作日志",
                        onRefresh = { viewModel.fetchLogEvents() }
                    ) {
                        items(logEvents) { event ->
                            LogEventItem(event)
                        }
                    }
                }
            }
        } else if (info != null && !info.isLoggedIn) {
            // 未登录提示
            Card(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Cookie 无效或已过期", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "当前处于未登录状态，请更新 Cookie 后重试",
                            fontSize = 13.sp,
                            color = FluentTheme.colors.text.text.secondary
                        )
                    }
                }
            }
        } else {
            // 尚未查询
            Card(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "请在上方输入 Cookie 后点击「导入并查询」",
                        fontSize = 13.sp,
                        color = FluentTheme.colors.text.text.secondary
                    )
                }
            }
        }
    }
}

// ── 基本信息 Tab ─────────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun InfoTabContent(info: WikiUserApi.UserInfo) {
    val listState = rememberLazyListState()
    val adapter = rememberScrollbarAdapter(listState)
    ScrollbarContainer(adapter = adapter, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 基础字段
            item {
                InfoSection(title = "账号信息") {
                    InfoRow("用户名", info.name)
                    InfoRow("用户 ID", info.id.toString())
                    if (info.email.isNotBlank()) InfoRow("邮箱", info.email)
                    if (info.realName.isNotBlank()) InfoRow("真实姓名", info.realName)
                    InfoRow("注册时间", WikiUserApi.formatTimestamp(info.registrationDate).takeIf { it != "-" } ?: "未知")
                    InfoRow("编辑次数", info.editCount.toString())
                }
            }
            // 用户组
            val userGroups = info.groups.filter { it != "*" }
            if (userGroups.isNotEmpty()) {
                item {
                    InfoSection(title = "用户组（${userGroups.size}）") {
                        userGroups.forEach { group ->
                            InfoRow(
                                label = groupLabel(group),
                                value = group
                            )
                        }
                    }
                }
            }
            // 权限（可读权限，过滤掉隐式的基础权限）
            val highlightRights = info.rights.filter { it in NOTABLE_RIGHTS }
            if (highlightRights.isNotEmpty()) {
                item {
                    InfoSection(title = "特殊权限（${highlightRights.size}）") {
                        highlightRights.forEach { right ->
                            InfoRow(label = rightLabel(right), value = right)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = FluentTheme.colors.text.text.secondary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = FluentTheme.colors.text.text.secondary,
            modifier = Modifier.width(110.dp)
        )
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

// ── 通用列表 Tab ─────────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun ListTabContent(
    isLoading: Boolean,
    isEmpty: Boolean,
    emptyText: String,
    onRefresh: () -> Unit,
    itemContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ProgressRing(size = 36.dp)
        }
        isEmpty -> {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(emptyText, color = FluentTheme.colors.text.text.secondary, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRefresh) { Text("刷新") }
            }
        }
        else -> {
            val listState = rememberLazyListState()
            val adapter = rememberScrollbarAdapter(listState)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onRefresh, modifier = Modifier.height(28.dp)) {
                        Text("刷新", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                ScrollbarContainer(adapter = adapter, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        content = itemContent
                    )
                }
            }
        }
    }
}

// ── 贡献条目 ─────────────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun ContribItem(contrib: WikiUserApi.UserContrib) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(FluentTheme.colors.control.secondary)
            .border(1.dp, FluentTheme.colors.stroke.card.default, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    contrib.title.replace("File:", "").replace("文件:", ""),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                if (contrib.minor) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(FluentTheme.colors.fillAccent.default.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("小编辑", fontSize = 10.sp, color = FluentTheme.colors.fillAccent.default)
                    }
                }
            }
            if (contrib.comment.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    contrib.comment,
                    fontSize = 11.sp,
                    color = FluentTheme.colors.text.text.secondary,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            val diffStr = if (contrib.sizeDiff >= 0) "+${contrib.sizeDiff}" else "${contrib.sizeDiff}"
            val diffColor = if (contrib.sizeDiff >= 0) Color(0xFF4CAF50) else Color(0xFFE57373)
            Text(diffStr, fontSize = 12.sp, color = diffColor, fontWeight = FontWeight.Medium)
            Text(
                WikiUserApi.formatTimestamp(contrib.timestamp),
                fontSize = 11.sp,
                color = FluentTheme.colors.text.text.secondary
            )
        }
    }
}

// ── 监视列表条目 ─────────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun WatchlistItem(item: WikiUserApi.WatchlistItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(FluentTheme.colors.control.secondary)
            .border(1.dp, FluentTheme.colors.stroke.card.default, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 类型徽章
        val typeColor = when (item.type) {
            "edit" -> Color(0xFF2196F3)
            "new" -> Color(0xFF4CAF50)
            "log" -> Color(0xFFFF9800)
            else -> Color.Gray
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(typeColor.copy(alpha = 0.15f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
                .widthIn(min = 38.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(WikiUserApi.watchTypeLabel(item.type), fontSize = 11.sp, color = typeColor)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            if (item.comment.isNotBlank()) {
                Text(
                    item.comment,
                    fontSize = 11.sp,
                    color = FluentTheme.colors.text.text.secondary,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(item.user, fontSize = 11.sp, color = FluentTheme.colors.fillAccent.default)
            Text(
                WikiUserApi.formatTimestamp(item.timestamp),
                fontSize = 11.sp,
                color = FluentTheme.colors.text.text.secondary
            )
        }
    }
}

// ── 操作日志条目 ─────────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun LogEventItem(event: WikiUserApi.LogEvent) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(FluentTheme.colors.control.secondary)
            .border(1.dp, FluentTheme.colors.stroke.card.default, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val typeColor = when (event.type) {
            "upload" -> Color(0xFF2196F3)
            "delete" -> Color(0xFFE57373)
            "protect" -> Color(0xFF9C27B0)
            "block" -> Color(0xFFF44336)
            "move" -> Color(0xFF009688)
            else -> Color.Gray
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(typeColor.copy(alpha = 0.15f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
                .widthIn(min = 42.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(WikiUserApi.logTypeLabel(event.type, event.action), fontSize = 11.sp, color = typeColor)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                event.title.replace("File:", "").replace("文件:", ""),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            if (event.comment.isNotBlank()) {
                Text(
                    event.comment,
                    fontSize = 11.sp,
                    color = FluentTheme.colors.text.text.secondary,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            WikiUserApi.formatTimestamp(event.timestamp),
            fontSize = 11.sp,
            color = FluentTheme.colors.text.text.secondary
        )
    }
}

// ── 辅助映射 ─────────────────────────────────────────────────────

private val NOTABLE_RIGHTS = setOf(
    "upload", "upload_by_url", "delete", "undelete", "deletedhistory", "deletedtext",
    "protect", "block", "createaccount", "rollback", "patrol", "autopatrol",
    "editprotected", "editsemiprotected", "suppressredirect", "move", "move-rootuserpages",
    "import", "importupload", "siteadmin", "editinterface", "editusercss", "edituserjs",
    "suppressionlog", "hideuser", "oversight", "apihighlimits"
)

private fun groupLabel(group: String) = when (group) {
    "sysop" -> "管理员"
    "bureaucrat" -> "行政员"
    "autoconfirmed" -> "自动确认用户"
    "confirmed" -> "确认用户"
    "bot" -> "机器人"
    "suppress" -> "监督员"
    "checkuser" -> "核查员"
    "interface-admin" -> "界面管理员"
    "rollbacker" -> "回退员"
    "patroller" -> "巡查员"
    "uploader" -> "上传者"
    "reviewer" -> "审核员"
    "editor" -> "编辑员"
    else -> group
}

private fun rightLabel(right: String) = when (right) {
    "upload" -> "上传文件"
    "upload_by_url" -> "通过 URL 上传"
    "delete" -> "删除页面"
    "undelete" -> "恢复删除"
    "protect" -> "保护页面"
    "block" -> "封禁用户"
    "rollback" -> "快速回退"
    "patrol" -> "巡查编辑"
    "autopatrol" -> "自动巡查"
    "editprotected" -> "编辑受保护页面"
    "move" -> "移动页面"
    "import" -> "导入页面"
    "apihighlimits" -> "API 高级限额"
    "siteadmin" -> "站点管理"
    "editinterface" -> "编辑界面"
    "suppressionlog" -> "隐藏日志"
    "hideuser" -> "隐藏用户"
    "oversight" -> "监督"
    else -> right
}

