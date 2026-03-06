package ui.screens

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
import data.WikiUserApi
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.PeopleError
import io.github.composefluent.icons.regular.Person
import io.github.composefluent.icons.regular.Search
import io.github.composefluent.surface.Card
import viewmodel.LogSortOrder
import viewmodel.LookupDetailTab
import viewmodel.UserInfoTab
import viewmodel.UserInfoViewModel


@OptIn(ExperimentalFluentApi::class)
@Composable
fun UserInfoContent(
    viewModel: UserInfoViewModel,
    modifier: Modifier = Modifier
) {
    val cookieInput by viewModel.cookieInput.collectAsState()
    val userInfo by viewModel.userInfo.collectAsState()
    val isLoadingInfo by viewModel.isLoadingInfo.collectAsState()
    val blockStatus by viewModel.blockStatus.collectAsState()
    val lastEditTimestamp by viewModel.lastEditTimestamp.collectAsState()
    val contributions by viewModel.contributions.collectAsState()
    val isLoadingContrib by viewModel.isLoadingContrib.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val isLoadingWatch by viewModel.isLoadingWatch.collectAsState()
    val logEvents by viewModel.logEvents.collectAsState()
    val isLoadingLog by viewModel.isLoadingLog.collectAsState()
    val logTypeFilter by viewModel.logTypeFilter.collectAsState()
    val logSortOrder by viewModel.logSortOrder.collectAsState()
    val availableLogTypes by viewModel.availableLogTypes.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    // 公开用户查询
    val lookupQuery by viewModel.lookupQuery.collectAsState()
    val lookupResult by viewModel.lookupResult.collectAsState()
    val lookupBlockStatus by viewModel.lookupBlockStatus.collectAsState()
    val lookupLastEdit by viewModel.lookupLastEdit.collectAsState()
    val isLoadingLookup by viewModel.isLoadingLookup.collectAsState()
    val lookupError by viewModel.lookupError.collectAsState()
    val lookupDetailTab by viewModel.lookupDetailTab.collectAsState()
    val lookupFiles by viewModel.lookupFiles.collectAsState()
    val isLoadingLookupFiles by viewModel.isLoadingLookupFiles.collectAsState()
    val lookupLogEvents by viewModel.lookupLogEvents.collectAsState()
    val isLoadingLookupLog by viewModel.isLoadingLookupLog.collectAsState()

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

        // ── 查询任意用户 ───────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("查询用户", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "输入用户数字 ID 查询用户的公开信息",
                    fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = lookupQuery,
                        onValueChange = { viewModel.onLookupQueryChange(it) },
                        modifier = Modifier.weight(1f).onKeyEvent { keyEvent ->
                            if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                                viewModel.lookupUser(); true
                            } else false
                        },
                        singleLine = true,
                        placeholder = { Text("用户 ID（如 5205017）") }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.lookupUser() },
                        disabled = isLoadingLookup || lookupQuery.isBlank()
                    ) {
                        if (isLoadingLookup) ProgressRing(size = 16.dp)
                        else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("查询")
                        }
                    }
                }
                if (lookupError.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(lookupError, fontSize = 12.sp, color = Color(0xFFE57373))
                }
                val lr = lookupResult
                if (lr != null && lr.exists) {
                    Spacer(Modifier.height(12.dp))
                    PublicUserInfoCard(
                        user = lr,
                        blockStatus = lookupBlockStatus,
                        lastEdit = lookupLastEdit,
                        detailTab = lookupDetailTab,
                        onDetailTabChange = { viewModel.onLookupDetailTabChange(it) },
                        files = lookupFiles,
                        isLoadingFiles = isLoadingLookupFiles,
                        onRefreshFiles = { viewModel.fetchLookupFiles(lr.name) },
                        logEvents = lookupLogEvents,
                        isLoadingLog = isLoadingLookupLog,
                        onRefreshLog = { viewModel.fetchLookupLog(lr.name) }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
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
                    UserInfoTab.INFO -> InfoTabContent(info, blockStatus, lastEditTimestamp)
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

                    UserInfoTab.LOG -> LogTabContent(
                        isLoading = isLoadingLog,
                        logEvents = logEvents,
                        logTypeFilter = logTypeFilter,
                        logSortOrder = logSortOrder,
                        availableLogTypes = availableLogTypes,
                        onFilterChange = { viewModel.onLogTypeFilterChange(it) },
                        onSortOrderChange = { viewModel.onLogSortOrderChange(it) },
                        onRefresh = { viewModel.fetchLogEvents() }
                    )
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

// ── 封禁警告横幅（共用）──────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun BlockBanner(blockStatus: WikiUserApi.BlockInfo, iconSize: Int = 18, titleSize: Int = 13) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFE57373).copy(alpha = 0.15f))
            .border(1.dp, Color(0xFFE57373).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.PeopleError, contentDescription = null,
            modifier = Modifier.size(iconSize.dp), tint = Color(0xFFE57373)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                "该账号已被封禁", fontWeight = FontWeight.SemiBold,
                fontSize = titleSize.sp, color = Color(0xFFE57373)
            )
            Text(
                buildString {
                    append("封禁者：${blockStatus.by}")
                    when {
                        blockStatus.expiry == "infinity" -> append("  永久封禁")
                        blockStatus.expiry.isNotBlank() -> append("  到期：${WikiUserApi.formatTimestamp(blockStatus.expiry)}")
                    }
                    if (blockStatus.reason.isNotBlank()) append("\n原因：${blockStatus.reason}")
                },
                fontSize = 11.sp, color = Color(0xFFE57373).copy(alpha = 0.8f)
            )
        }
    }
}

// ── 基本信息 Tab ─────────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun InfoTabContent(
    info: WikiUserApi.UserInfo,
    blockStatus: WikiUserApi.BlockInfo?,
    lastEditTimestamp: String?
) {
    val listState = rememberLazyListState()
    val adapter = rememberScrollbarAdapter(listState)
    ScrollbarContainer(adapter = adapter, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 封禁状态警告（如被封禁）
            if (blockStatus != null) {
                item { BlockBanner(blockStatus) }
            }
            // 基础字段
            item {
                InfoSection(title = "账号信息") {
                    InfoRow("用户名", info.name)
                    InfoRow("用户 ID", info.id.toString())
                    if (info.email.isNotBlank()) InfoRow("邮箱", info.email)
                    if (info.realName.isNotBlank()) InfoRow("真实姓名", info.realName)
                    InfoRow(
                        "注册时间",
                        WikiUserApi.formatTimestamp(info.registrationDate).takeIf { it != "-" } ?: "未知")
                    InfoRow("编辑次数", info.editCount.toString())
                    InfoRow(
                        "最后编辑",
                        when {
                            lastEditTimestamp == null -> "加载中…"
                            lastEditTimestamp.isBlank() -> "无编辑记录"
                            else -> WikiUserApi.formatTimestamp(lastEditTimestamp)
                        }
                    )
                    InfoRow("封禁状态", if (blockStatus == null) "正常" else "已封禁")
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
            // 特殊权限
            val notableRights = info.rights.filter {
                it in setOf(
                    "upload", "delete", "undelete", "protect", "block",
                    "rollback", "patrol", "siteadmin", "editinterface", "oversight",
                    "import", "move", "apihighlimits"
                )
            }
            if (notableRights.isNotEmpty()) {
                item {
                    InfoSection(title = "特殊权限（${notableRights.size}）") {
                        notableRights.forEach { right ->
                            InfoRow(label = right, value = "✓")
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
            Text(
                title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = FluentTheme.colors.text.text.secondary
            )
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
                if (contrib.isMinor) {
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

// ── 操作日志 Tab（含筛选和排序）────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun LogTabContent(
    isLoading: Boolean,
    logEvents: List<WikiUserApi.LogEvent>,
    logTypeFilter: String?,
    logSortOrder: LogSortOrder,
    availableLogTypes: List<String>,
    onFilterChange: (String?) -> Unit,
    onSortOrderChange: (LogSortOrder) -> Unit,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        // 工具栏：筛选 + 排序 + 刷新
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 筛选下拉
            var filterExpanded by remember { mutableStateOf(false) }
            val allTypes = listOf(null) + availableLogTypes
            val filterLabel = logTypeFilter?.let { WikiUserApi.logTypeLabel(it, "") } ?: "全部类型"
            Box {
                Button(
                    onClick = { filterExpanded = true },
                    modifier = Modifier.height(28.dp)
                ) { Text(filterLabel, fontSize = 12.sp) }
                DropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false }
                ) {
                    allTypes.forEach { type ->
                        DropdownMenuItem(onClick = {
                            onFilterChange(type)
                            filterExpanded = false
                        }) {
                            Text(type?.let { WikiUserApi.logTypeLabel(it, "") } ?: "全部类型", fontSize = 12.sp)
                        }
                    }
                }
            }
            // 排序切换
            Button(
                onClick = {
                    onSortOrderChange(
                        if (logSortOrder == LogSortOrder.NEWEST_FIRST) LogSortOrder.OLDEST_FIRST
                        else LogSortOrder.NEWEST_FIRST
                    )
                },
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    if (logSortOrder == LogSortOrder.NEWEST_FIRST) "最新在前" else "最早在前",
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onRefresh, modifier = Modifier.height(28.dp)) {
                Text("刷新", fontSize = 12.sp)
            }
        }
        when {
            isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProgressRing(size = 36.dp)
            }

            logEvents.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无操作日志", color = FluentTheme.colors.text.text.secondary, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRefresh) { Text("刷新") }
                }
            }

            else -> {
                val listState = rememberLazyListState()
                val adapter = rememberScrollbarAdapter(listState)
                ScrollbarContainer(adapter = adapter, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logEvents) { event -> LogEventItem(event) }
                    }
                }
            }
        }
    }
}

// ── 公开用户信息卡片 ─────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun PublicUserInfoCard(
    user: WikiUserApi.PublicUserInfo,
    blockStatus: WikiUserApi.BlockInfo?,
    lastEdit: String?,
    detailTab: LookupDetailTab,
    onDetailTabChange: (LookupDetailTab) -> Unit,
    files: List<WikiUserApi.UserFile>,
    isLoadingFiles: Boolean,
    onRefreshFiles: () -> Unit,
    logEvents: List<WikiUserApi.LogEvent>,
    isLoadingLog: Boolean,
    onRefreshLog: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 封禁警告
        if (blockStatus != null) {
            BlockBanner(blockStatus, iconSize = 16, titleSize = 12)
        }
        // 用户摘要行
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(FluentTheme.colors.control.secondary)
                .border(1.dp, FluentTheme.colors.stroke.card.default, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(FluentTheme.colors.fillAccent.default.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person, contentDescription = null,
                    modifier = Modifier.size(20.dp), tint = Color.White
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val badge = user.groups.filter { it != "*" && it != "user" }
                    .joinToString(" · ").ifBlank { "普通用户" }
                Text(badge, fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("编辑次数", fontSize = 10.sp, color = FluentTheme.colors.text.text.secondary)
                Text(
                    user.editCount.toString(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    color = FluentTheme.colors.fillAccent.default
                )
            }
        }
        // 详情芯片行
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (user.registrationDate.isNotBlank()) {
                InfoChip(label = "注册时间", value = WikiUserApi.formatTimestamp(user.registrationDate))
            }
            InfoChip(
                label = "最后编辑",
                value = when {
                    lastEdit == null -> "加载中…"
                    lastEdit.isBlank() -> "无记录"
                    else -> WikiUserApi.formatTimestamp(lastEdit)
                }
            )
            InfoChip(
                label = "封禁状态", value = if (blockStatus == null) "正常" else "已封禁",
                valueColor = if (blockStatus == null) null else Color(0xFFE57373)
            )
        }
        // 详情子 Tab
        val detailTabs = listOf(
            LookupDetailTab.SUMMARY to "概览",
            LookupDetailTab.FILES to "上传文件",
            LookupDetailTab.LOG to "操作日志"
        )
        SegmentedControl(modifier = Modifier.fillMaxWidth()) {
            detailTabs.forEachIndexed { index, (tab, label) ->
                val pos = when (index) {
                    0 -> SegmentedItemPosition.Start
                    detailTabs.size - 1 -> SegmentedItemPosition.End
                    else -> SegmentedItemPosition.Center
                }
                SegmentedButton(
                    checked = detailTab == tab,
                    onCheckedChanged = { onDetailTabChange(tab) },
                    position = pos,
                    modifier = Modifier.weight(1f),
                    text = { Text(label, fontSize = 12.sp) }
                )
            }
        }
        // 子 Tab 内容（固定高度避免 Card 撑开太高）
        Box(Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 240.dp)) {
            when (detailTab) {
                LookupDetailTab.SUMMARY -> {
                    val userGroups = user.groups.filter { it != "*" }
                    if (userGroups.isNotEmpty()) {
                        val listState = rememberLazyListState()
                        val adapter = rememberScrollbarAdapter(listState)
                        ScrollbarContainer(adapter = adapter, modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(userGroups) { group ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            groupLabel(group), fontSize = 12.sp,
                                            modifier = Modifier.width(100.dp),
                                            color = FluentTheme.colors.text.text.secondary
                                        )
                                        Text(group, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("无特殊用户组", fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
                        }
                    }
                }

                LookupDetailTab.FILES -> LookupListTab(
                    isLoading = isLoadingFiles,
                    isEmpty = files.isEmpty(),
                    emptyText = "暂无上传文件",
                    onRefresh = onRefreshFiles
                ) {
                    items(files) { UserFileItem(it) }
                }

                LookupDetailTab.LOG -> LookupListTab(
                    isLoading = isLoadingLog,
                    isEmpty = logEvents.isEmpty(),
                    emptyText = "暂无操作日志",
                    onRefresh = onRefreshLog
                ) {
                    items(logEvents) { LogEventItem(it) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun LookupListTab(
    isLoading: Boolean,
    isEmpty: Boolean,
    emptyText: String,
    onRefresh: () -> Unit,
    itemContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ProgressRing(size = 28.dp)
        }

        isEmpty -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(emptyText, fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
            Spacer(Modifier.height(6.dp))
            Button(onClick = onRefresh, modifier = Modifier.height(26.dp)) { Text("加载", fontSize = 11.sp) }
        }

        else -> {
            val listState = rememberLazyListState()
            val adapter = rememberScrollbarAdapter(listState)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onRefresh, modifier = Modifier.height(24.dp)) {
                        Text("刷新", fontSize = 11.sp)
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

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun UserFileItem(file: WikiUserApi.UserFile) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(FluentTheme.colors.control.secondary)
            .border(1.dp, FluentTheme.colors.stroke.card.default, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // MIME 类型徽章
        val mimeColor = when {
            file.mime.startsWith("image/") -> Color(0xFF2196F3)
            file.mime.startsWith("audio/") -> Color(0xFF9C27B0)
            file.mime.startsWith("video/") -> Color(0xFFFF9800)
            else -> Color.Gray
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(mimeColor.copy(alpha = 0.15f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
                .widthIn(min = 36.dp),
            contentAlignment = Alignment.Center
        ) {
            val mimeLabel = when {
                file.mime.startsWith("image/") -> file.mime.removePrefix("image/").uppercase()
                file.mime.startsWith("audio/") -> file.mime.removePrefix("audio/").uppercase()
                file.mime.startsWith("video/") -> file.mime.removePrefix("video/").uppercase()
                else -> file.mime.substringAfterLast("/").uppercase().take(6)
            }
            Text(mimeLabel, fontSize = 10.sp, color = mimeColor)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.name.ifBlank { file.title.removePrefix("File:").removePrefix("文件:") },
                fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1
            )
            if (file.size > 0) {
                val sizeStr = when {
                    file.size >= 1_048_576 -> "%.1f MB".format(file.size / 1_048_576.0)
                    file.size >= 1024 -> "%.1f KB".format(file.size / 1024.0)
                    else -> "${file.size} B"
                }
                Text(sizeStr, fontSize = 10.sp, color = FluentTheme.colors.text.text.secondary)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            WikiUserApi.formatTimestamp(file.timestamp),
            fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary
        )
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun InfoChip(label: String, value: String, valueColor: Color? = null) {
    Column(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(FluentTheme.colors.control.secondary)
            .border(1.dp, FluentTheme.colors.stroke.card.default, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 10.sp, color = FluentTheme.colors.text.text.secondary)
        Text(
            value, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = valueColor ?: FluentTheme.colors.text.text.primary
        )
    }
}

// ── 辅助映射 ─────────────────────────────────────────────────────


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


