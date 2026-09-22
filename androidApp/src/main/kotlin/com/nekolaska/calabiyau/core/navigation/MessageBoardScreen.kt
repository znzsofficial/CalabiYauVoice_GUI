package com.nekolaska.calabiyau.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.core.wiki.CustomProfileAvatar
import data.ApiResult
import data.CustomUserApi
import data.CustomUserProfile
import data.ProfileComment
import data.ReplyNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageBoardScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { MessageBoardState(scope) }
    val notifications = remember(scope) { BoardNotificationsState(scope) }
    var showNotifications by remember { mutableStateOf(false) }
    LaunchedEffect(state, notifications) {
        while (true) {
            state.syncIdentity()
            notifications.syncAccount()
            kotlinx.coroutines.delay(250.milliseconds)
        }
    }
    LaunchedEffect(state.userInfo?.id) {
        if (state.userInfo != null) notifications.refresh()
    }
    var profileBid by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ProfileComment?>(null) }
    val thread = state.threadId != null
    val busy = state.posting || state.deletingId != null
    val back = { if (thread) state.closeThread() else onBack() }
    BackHandler(enabled = thread || busy) { if (!busy) state.closeThread() }
    val listPositions = remember { mutableMapOf<Long, LazyListState>() }
    val listState = listPositions.getOrPut(state.threadId ?: 0L) { LazyListState() }
    LaunchedEffect(state.focusedReplyId, state.loading, state.focusScrollPending) {
        val index = state.comments.indexOfFirst { it.id == state.focusedReplyId }
        if (state.focusScrollPending && !state.loading && state.root != null && index >= 0) {
            listState.scrollToItem(index + 2)
            state.finishFocusScroll()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (thread) "讨论详情" else "留言板",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = back, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.userInfo != null) IconButton(onClick = {
                        showNotifications = true; notifications.refresh()
                    }, enabled = !busy) {
                        BadgedBox(badge = { if (notifications.unread > 0) Badge { Text(if (notifications.unread > 99) "99+" else notifications.unread.toString()) } }) {
                            Icon(
                                Icons.Outlined.Notifications,
                                contentDescription = "回复通知，${notifications.unread} 条未读"
                            )
                        }
                    }
                    IconButton(
                        onClick = { state.refresh(); if (state.userInfo != null) notifications.refresh() },
                        enabled = !busy && !state.loading
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)) {
            if (!thread) BoardIdentity(state)
            key(state.threadId) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!thread && state.pinned.isNotEmpty()) {
                        item(key = "pinned-heading") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.PushPin,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "置顶留言",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        items(state.pinned, key = { "pinned-${it.id}" }) { comment ->
                            CommentItem(
                                comment,
                                canDelete = !busy && !state.loading && state.userInfo?.let { user ->
                                    comment.authorWikiUserId?.let { it == user.id }
                                        ?: (comment.authorBid == user.name)
                                } == true,
                                onDelete = { deleteTarget = comment },
                                onAuthor = { profileBid = comment.authorBid },
                                action = {
                                    ReplyPill(
                                        icon = {
                                            Icon(
                                                Icons.Outlined.Forum,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        text = if (comment.replyCount > 0) "查看讨论 · ${comment.replyCount} 条回复" else "查看讨论",
                                        onClick = { state.openThread(comment) }, enabled = !busy
                                    )
                                })
                        }
                        item(key = "regular-heading") {
                            Text(
                                "最新留言",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                    if (thread) {
                        state.root?.let { root ->
                            item(key = "root") {
                                CommentItem(
                                    root,
                                    canDelete = !busy && !state.loading && !root.deleted && state.userInfo?.let { user ->
                                        root.authorWikiUserId?.let { it == user.id }
                                            ?: (root.authorBid == user.name)
                                    } == true,
                                    onDelete = { deleteTarget = root },
                                    onAuthor = { profileBid = root.authorBid })
                            }
                            item(key = "heading") {
                                Column {
                                    Text(
                                        "回复 · ${root.replyCount} · 最新在前",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (state.focusedReplyId != null) TextButton(
                                        onClick = state::refresh, enabled = !busy && !state.loading,
                                        contentPadding = PaddingValues(horizontal = 0.dp)
                                    ) { Text("已定位通知回复 · 查看最新回复") }
                                }
                            }
                        }
                    }
                    items(
                        if (thread) state.comments else state.comments.filterNot { row -> state.pinned.any { it.id == row.id } },
                        key = { it.id }) { comment ->
                        CommentItem(
                            comment,
                            canDelete = !busy && !state.loading && !comment.deleted && state.userInfo?.let { user ->
                                comment.authorWikiUserId?.let { it == user.id }
                                    ?: (comment.authorBid == user.name)
                            } == true,
                            onDelete = { deleteTarget = comment },
                            onAuthor = { profileBid = comment.authorBid },
                            action = if (!thread) {
                                {
                                    ReplyPill(
                                        icon = {
                                            Icon(
                                                Icons.Outlined.Forum,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        text = if (comment.replyCount > 0) "${comment.replyCount} 条回复" else "回复",
                                        onClick = { state.openThread(comment) }, enabled = !busy
                                    )
                                }
                            } else if (!comment.deleted && state.root?.deleted == false) {
                                {
                                    ReplyPill(
                                        icon = null, text = "回复",
                                        onClick = { state.selectReply(comment) }, enabled = !busy
                                    )
                                }
                            } else null
                        )
                    }
                    if (state.loading) {
                        item(key = "loading") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }
                    } else if (state.comments.isEmpty() && state.error == null) {
                        item(key = "empty") {
                            BoardEmpty(
                                if (thread) "还没有回复" else "还没有留言",
                                if (thread) "写下你的想法，开始讨论。" else "分享你的想法吧。"
                            )
                        }
                    }
                    if (state.nextCursor != null) {
                        item(key = "more") {
                            TextButton(
                                onClick = state::loadMore, enabled = !state.loading && !busy,
                                modifier = Modifier.fillMaxWidth(), shape = smoothCornerShape(12.dp)
                            ) {
                                Text(if (thread) "加载更多回复" else "加载更多留言")
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = state.error != null, enter = fadeIn() + scaleIn(initialScale = 0.96f),
                exit = fadeOut() + scaleOut(targetScale = 0.96f)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = smoothCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        state.error.orEmpty(), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            if (thread && state.root?.deleted == true) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = smoothCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        "主留言已删除，讨论仅可查看。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = smoothCornerShape(20.dp),
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .imePadding()
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (thread) {
                            if (state.replyTargetUnavailable) Text(
                                "原回复目标已不可用，请先核对上次是否发送成功。",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(state.replyTarget?.let { "回复 @${it.displayAuthor()}" }
                                    ?: "回复主留言",
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (state.replyTarget != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (state.replyTarget != null || state.replyTargetUnavailable) TextButton(
                                    onClick = { state.selectReply(null) }, enabled = !busy,
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.heightIn(min = 32.dp)
                                ) {
                                    Text(
                                        if (state.replyTargetUnavailable) "改为回复主留言" else "取消",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.draft,
                                onValueChange = { state.draft = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(if (thread) "写下你的回复…" else "写下你的留言…") },
                                maxLines = 4,
                                enabled = !state.posting && state.identityLoaded,
                                shape = smoothCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                trailingIcon = {
                                    Text(
                                        "${state.draft.length}/200",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            )
                            FilledIconButton(
                                onClick = state::send,
                                enabled = !busy && !state.loading && state.identityLoaded && !state.replyTargetUnavailable && state.draft.isNotBlank() && (!thread || state.root != null),
                                modifier = Modifier.size(52.dp), shape = CircleShape
                            ) {
                                if (state.posting) CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                else Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = if (thread) "发送回复" else "发送留言"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { comment ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
            title = { Text("删除${if (comment.rootId == null) "留言" else "回复"}？") },
            text = { Text(if (comment.rootId == null) "删除后无法恢复。已有回复会保留，讨论将停止接收新回复。" else "删除后无法恢复，引用这条回复的位置将显示已删除。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null; state.delete(comment)
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
    profileBid?.let { bid -> ProfileDetailSheet(bid) { profileBid = null } }
    if (showNotifications) NotificationsSheet(
        notifications,
        onDismiss = { showNotifications = false },
        onOpen = { rootId, commentId ->
            if (showNotifications) {
                showNotifications = false
                state.openNotification(rootId, commentId)
            }
        })
}

@Composable
private fun ReplyPill(
    icon: (@Composable () -> Unit)?,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    TextButton(
        onClick = onClick, enabled = enabled, shape = smoothCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.heightIn(min = 32.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    ) {
        icon?.invoke()
        if (icon != null) Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BoardEmpty(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Outlined.Forum,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .alpha(0.5f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsSheet(
    state: BoardNotificationsState,
    onDismiss: () -> Unit,
    onOpen: (Long, Long) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "回复通知",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { state.markRead() }, enabled = !state.loading && state.unread > 0,
                    shape = smoothCornerShape(10.dp)
                ) { Text("全部已读") }
                IconButton(
                    onClick = state::refresh,
                    enabled = !state.loading
                ) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新通知") }
            }
            AnimatedVisibility(visible = state.stale, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "刷新未完成，未读数及列表为上次成功获取的结果。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            AnimatedVisibility(visible = state.error != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            LazyColumn(
                Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 24.dp
                )
            ) {
                items(state.items, key = { it.id }) { item ->
                    NotificationItem(item, enabled = !state.loading) {
                        if (state.canOpenCurrentAccount() && item.status == "available") onOpen(
                            item.rootId,
                            item.commentId
                        )
                        if (!item.read) state.markRead(item)
                    }
                }
                if (state.loading) item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp) }
                }
                if (!state.loading && state.items.isEmpty() && state.error == null) item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .alpha(0.5f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("还没有回复通知", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "有人回复你的留言后，会显示在这里。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (state.nextCursor != null) item {
                    TextButton(
                        onClick = state::more,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = smoothCornerShape(12.dp)
                    ) { Text("加载更多") }
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(item: ReplyNotification, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (item.read) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = smoothCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier
                .size(8.dp)
                .padding(top = 6.dp)) {
                if (!item.read) Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (item.status == "available") "${item.displayAuthor()} 回复了你" else "回复已不可用",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (item.read) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    when (item.status) {
                        "available" -> item.content.orEmpty()
                        "hidden" -> "该回复或讨论已隐藏"
                        "deleted" -> "该回复或讨论中的关联留言已删除"
                        else -> "对应讨论已不存在"
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    (if (item.read) "已读 · " else "未读 · ") + relativeTime(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BoardIdentity(state: MessageBoardState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = smoothCornerShape(16.dp),
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val user = state.userInfo
            when {
                // 登录用户主动选择匿名发言
                user != null && state.postingAsGuest -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Outlined.VisibilityOff, contentDescription = null,
                            modifier = Modifier.size(36.dp).padding(6.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(Modifier.weight(1f)) {
                            Text("匿名（访客）", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "以访客身份发言，不关联 Wiki 账号",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = state::togglePostingAsGuest, enabled = !state.posting) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = "切回登录身份")
                        }
                    }
                    BoardNicknameField(state)
                }

                user != null -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CustomProfileAvatar(state.profile, user.name, Modifier.size(36.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.profile?.displayName(user.name) ?: user.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "以该身份发言", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = state::togglePostingAsGuest, enabled = !state.posting) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = "切换为匿名发言")
                        }
                    }
                }

                !state.identityLoaded -> {
                    val cached = state.cachedIdentityLabel
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (cached != null) {
                            Icon(
                                Icons.Outlined.History, contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "上次以 $cached 身份发言 · 正在刷新…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("正在识别发言身份…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                else -> BoardNicknameField(state)
            }
        }
    }
}

@Composable
private fun BoardNicknameField(state: MessageBoardState) {
    OutlinedTextField(
        state.nickname,
        state::updateNickname,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("访客昵称（可选）") },
        singleLine = true,
        enabled = !state.posting,
        shape = smoothCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun CommentItem(
    comment: ProfileComment,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onAuthor: () -> Unit,
    action: (@Composable () -> Unit)? = null
) {
    val authorClickable = !comment.deleted && comment.authorBid != "anon"
    Card(
        Modifier.fillMaxWidth(), shape = smoothCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (comment.deleted) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CustomProfileAvatar(
                    CustomUserProfile(
                        comment.authorBid,
                        customName = comment.authorName,
                        avatarUrl = comment.authorAvatarUrl
                    ),
                    comment.displayAuthor(),
                    Modifier
                        .size(38.dp)
                        .clickable(enabled = authorClickable, onClick = onAuthor)
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(enabled = authorClickable, onClick = onAuthor)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            comment.displayAuthor(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (comment.pinnedAt != null) Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = "置顶",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        relativeTime(comment.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canDelete) IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = if (comment.rootId == null) "删除留言" else "删除回复",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!comment.deleted && comment.replyToId != null) {
                Text(
                    "回复 @${comment.replyToName.orEmpty()}${
                        comment.replyToTag?.let { "#$it" }.orEmpty()
                    }",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                if (comment.deleted) "该留言已删除" else comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (comment.deleted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                action?.invoke() ?: Spacer(Modifier.height(1.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDetailSheet(bid: String, onDismiss: () -> Unit) {
    var profile by remember(bid) { mutableStateOf<CustomUserProfile?>(null) }
    var loading by remember(bid) { mutableStateOf(true) }
    var likes by remember(bid) { mutableStateOf<Int?>(null) }
    var error by remember(bid) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(bid, retry) {
        loading = true; error = null
        try {
            when (val result = CustomUserApi.fetchProfile(bid = bid)) {
                is ApiResult.Success -> profile = result.value
                is ApiResult.Error -> error = result.message
            }
            when (val result = CustomUserApi.fetchLikes(bid = bid)) {
                is ApiResult.Success -> likes = result.value.count
                is ApiResult.Error -> error = result.message
            }
        } finally {
            loading = false
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CustomProfileAvatar(profile, bid, Modifier.size(64.dp))
            Text(profile?.displayName(bid) ?: bid, style = MaterialTheme.typography.titleLarge)
            profile?.badge?.takeIf { it.isNotBlank() }?.let {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = smoothCornerShape(8.dp)
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                "BID：$bid · WikiID：${profile?.wikiUserId ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            profile?.bio?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            likes?.let {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = smoothCornerShape(10.dp)
                ) {
                    Text(
                        "获赞 $it",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { retry++ }, enabled = !loading) { Text("重试") }
            }
        }
    }
}

private fun relativeTime(epochSeconds: Long): String {
    val diff = System.currentTimeMillis() / 1000 - epochSeconds
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60} 分钟前"
        diff < 86400 -> "${diff / 3600} 小时前"
        diff < 172800 -> "昨天 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(
            Date(
                epochSeconds * 1000
            )
        )

        diff < 31536000 -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(
            Date(
                epochSeconds * 1000
            )
        )

        else -> SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Date(epochSeconds * 1000))
    }
}
