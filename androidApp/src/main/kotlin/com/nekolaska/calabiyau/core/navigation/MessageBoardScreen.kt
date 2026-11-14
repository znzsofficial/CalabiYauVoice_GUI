package com.nekolaska.calabiyau.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.rememberPlainTextClipboardCopier
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
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

@Composable
internal fun MessageBoardScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { MessageBoardState(scope) }
    val notifications = remember(scope) { BoardNotificationsState(scope) }
    val showSnack = rememberSnackbarLauncher()
    val haptics = LocalHapticFeedback.current
    val copyCommentText = rememberPlainTextClipboardCopier { showSnack("已复制留言内容") }
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
    var profileAuthor by remember { mutableStateOf<ProfileComment?>(null) }
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
                    BackNavButton(onClick = back, enabled = !busy)
                },
                actions = {
                    if (state.userInfo != null) IconButton(onClick = {
                        showNotifications = true; notifications.refresh()
                    }, enabled = !busy) {
                        BadgedBox(badge = {
                            if (notifications.unread > 0) {
                                Badge { Text(if (notifications.unread > 99) "99+" else notifications.unread.toString()) }
                            }
                        }) {
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            key(state.threadId) {
                val displayComments = if (thread) state.comments
                else state.comments.filterNot { row -> state.pinned.any { it.id == row.id } }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = AppSpacing.large),
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
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        items(state.pinned, key = { "pinned-${it.id}" }) { comment ->
                            CommentItem(
                                comment = comment,
                                isPinned = true,
                                canDelete = !busy && !state.loading && state.userInfo?.let { user ->
                                    comment.isOwnedBy(user.id, user.name)
                                } == true,
                                onDelete = { deleteTarget = comment },
                                onAuthor = { profileAuthor = comment },
                                onCardClick = { if (!busy) state.openThread(comment) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    copyCommentText("留言内容", comment.content)
                                },
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
                                        onClick = { state.openThread(comment) },
                                        enabled = !busy
                                    )
                                }
                            )
                        }
                        item(key = "regular-heading") {
                            Text(
                                "最新留言",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                    if (thread) {
                        state.root?.let { root ->
                            item(key = "root") {
                                CommentItem(
                                    comment = root,
                                    isRootThread = true,
                                    canDelete = !busy && !state.loading && !root.deleted && state.userInfo?.let { user ->
                                        root.isOwnedBy(user.id, user.name)
                                    } == true,
                                    onDelete = { deleteTarget = root },
                                    onAuthor = { profileAuthor = root },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        copyCommentText("留言内容", root.content)
                                    }
                                )
                            }
                            item(key = "heading") {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "回复 · ${root.replyCount} · 最新在前",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (state.focusedReplyId != null) {
                                        TextButton(
                                            onClick = state::refresh,
                                            enabled = !busy && !state.loading,
                                            contentPadding = PaddingValues(horizontal = 0.dp)
                                        ) { Text("已定位通知回复 · 查看最新回复") }
                                    }
                                }
                            }
                        }
                    }
                    items(displayComments, key = { it.id }) { comment ->
                        CommentItem(
                            comment = comment,
                            isThreadReply = thread,
                            canDelete = !busy && !state.loading && !comment.deleted && state.userInfo?.let { user ->
                                comment.isOwnedBy(user.id, user.name)
                            } == true,
                            onDelete = { deleteTarget = comment },
                            onAuthor = { profileAuthor = comment },
                            onCardClick = if (!thread) {
                                { if (!busy) state.openThread(comment) }
                            } else null,
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                copyCommentText("留言内容", comment.content)
                            },
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
                                        onClick = { state.openThread(comment) },
                                        enabled = !busy
                                    )
                                }
                            } else if (!comment.deleted && state.root?.deleted == false) {
                                {
                                    ReplyPill(
                                        icon = null,
                                        text = "回复",
                                        onClick = { state.selectReply(comment) },
                                        enabled = !busy
                                    )
                                }
                            } else null
                        )
                    }
                    if (state.loading) {
                        if (displayComments.isEmpty() && (!thread || state.root == null)) {
                            items(4, key = { "skeleton-$it" }) {
                                CommentItemSkeleton()
                            }
                        } else {
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
                                onClick = state::loadMore,
                                enabled = !state.loading && !busy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = AppShapes.chip
                            ) {
                                Text(if (thread) "加载更多回复" else "加载更多留言")
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn() + scaleIn(initialScale = 0.96f),
                exit = fadeOut() + scaleOut(targetScale = 0.96f)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = AppShapes.chip,
                    modifier = Modifier
                        .padding(horizontal = AppSpacing.screen, vertical = AppSpacing.small)
                        .let { mod ->
                            if (thread && state.root?.deleted == true) mod.navigationBarsPadding() else mod
                        }
                ) {
                    Text(
                        state.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (thread && state.root?.deleted == true) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = AppShapes.chip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.screen, vertical = AppSpacing.small)
                        .navigationBarsPadding()
                ) {
                    Text(
                        "主留言已删除，讨论仅可查看。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            } else {
                BoardComposerPanel(
                    state = state,
                    thread = thread,
                    busy = busy
                )
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
    profileAuthor?.let { author ->
        ProfileDetailSheet(author.authorBid, author.authorWikiUserId) { profileAuthor = null }
    }
    if (showNotifications) {
        NotificationsSheet(
            notifications,
            onDismiss = { showNotifications = false },
            onOpen = { rootId, commentId ->
                if (showNotifications) {
                    showNotifications = false
                    state.openNotification(rootId, commentId)
                }
            }
        )
    }
}

@Composable
private fun BoardComposerPanel(
    state: MessageBoardState,
    thread: Boolean,
    busy: Boolean
) {
    var editingNickname by remember { mutableStateOf(false) }
    val user = state.userInfo
    val showGuestNickname = state.postingAsGuest || (state.identityLoaded && user == null)
    LaunchedEffect(showGuestNickname) {
        if (!showGuestNickname) editingNickname = false
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = smoothCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 紧凑身份栏（主列表与讨论详情共用，默认不占屏）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    state.postingAsGuest -> {
                        Surface(
                            onClick = { editingNickname = !editingNickname },
                            shape = AppShapes.capsule,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "匿名 · ${state.nickname.ifBlank { "设置访客昵称" }}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "修改昵称",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = state::togglePostingAsGuest,
                            enabled = !state.posting,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.heightIn(min = 30.dp)
                        ) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("切实名", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    user != null -> {
                        CustomProfileAvatar(state.profile, user.name, Modifier.size(22.dp))
                        Text(
                            text = state.profile?.displayName(user.name) ?: user.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = AppShapes.chip,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "实名",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = state::togglePostingAsGuest,
                            enabled = !state.posting,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.heightIn(min = 30.dp)
                        ) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("切匿名", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    !state.identityLoaded -> {
                        val cached = state.cachedIdentityLabel
                        if (cached != null) {
                            Icon(
                                Icons.Outlined.History,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "上次：$cached · 刷新中",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text(
                                "识别身份中…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        TextButton(
                            onClick = state::togglePostingAsGuest,
                            enabled = !state.posting,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.heightIn(min = 30.dp)
                        ) {
                            Text("匿名发言", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    else -> {
                        Surface(
                            onClick = { editingNickname = !editingNickname },
                            shape = AppShapes.capsule,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "访客 · ${state.nickname.ifBlank { "设置昵称（可选）" }}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "修改昵称",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }

                // 字数计数移至身份行末尾，避免在输入框内遮挡末字
                Text(
                    "${state.draft.length}/200",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.draft.length >= 180) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = showGuestNickname && editingNickname,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                BoardNicknameField(state)
            }

            if (thread) {
                if (state.replyTargetUnavailable) {
                    Text(
                        "原回复目标已不可用，请先核对上次是否发送成功。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (state.replyTarget != null || state.replyTargetUnavailable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.replyTarget?.let { "回复 @${it.displayAuthor()}" } ?: "回复主留言",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (state.replyTarget != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { state.selectReply(null) },
                            enabled = !busy,
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
                    enabled = !state.posting && (state.identityLoaded || state.postingAsGuest),
                    shape = smoothCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                FilledIconButton(
                    onClick = state::send,
                    enabled = !busy && !state.loading && (state.identityLoaded || state.postingAsGuest) && !state.replyTargetUnavailable && state.draft.isNotBlank() && (!thread || state.root != null),
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape
                ) {
                    if (state.posting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (thread) "发送回复" else "发送留言"
                        )
                    }
                }
            }
        }
    }
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
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
        ) {
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
                    onClick = { state.markRead() },
                    enabled = !state.loading && state.unread > 0,
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
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
            // 先留出顶部间距再定 8dp 尺寸，避免圆点被内边距压扁
            Box(Modifier.padding(top = 6.dp).size(8.dp)) {
                if (!item.read) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxSize()
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
private fun BoardNicknameField(state: MessageBoardState) {
    OutlinedTextField(
        state.nickname,
        state::updateNickname,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("访客昵称（可选）", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
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
private fun CommentItemSkeleton() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShimmerBox(Modifier.size(38.dp), shape = CircleShape)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShimmerBox(Modifier.width(92.dp).height(14.dp))
                    ShimmerBox(Modifier.width(56.dp).height(11.dp))
                }
            }
            ShimmerBox(Modifier.fillMaxWidth(0.92f).height(14.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.68f).height(14.dp))
            ShimmerBox(Modifier.width(84.dp).height(24.dp), shape = AppShapes.chip)
        }
    }
}

@Composable
private fun CommentItem(
    comment: ProfileComment,
    isPinned: Boolean = false,
    isRootThread: Boolean = false,
    isThreadReply: Boolean = false,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onAuthor: () -> Unit,
    onCardClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    if (comment.deleted) {
        // 已删除留言收成紧凑提示条，避免在长讨论中占一整张大卡片
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.chip,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "该留言已删除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    relativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val authorClickable = !comment.isGuest
    val containerColor = when {
        isPinned -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
        isRootThread -> MaterialTheme.colorScheme.surfaceContainer
        isThreadReply -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val border = when {
        isPinned -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
        isRootThread -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        else -> null
    }
    val shape = smoothCornerShape(16.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .let { mod ->
                if (onCardClick != null || onLongClick != null) {
                    mod.combinedClickable(
                        onClick = onCardClick ?: {},
                        onLongClick = onLongClick
                    )
                } else mod
            },
        shape = shape,
        color = containerColor,
        border = border
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (comment.pinnedAt != null) {
                            Surface(
                                shape = AppShapes.chip,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.PushPin,
                                        contentDescription = "置顶",
                                        modifier = Modifier.size(11.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        "置顶",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        relativeTime(comment.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = if (comment.rootId == null) "删除留言" else "删除回复",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (comment.replyToId != null) {
                Surface(
                    shape = AppShapes.chip,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ) {
                    Text(
                        "回复 @${comment.replyToName.orEmpty()}${
                            comment.replyToTag?.let { "#$it" }.orEmpty()
                        }",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (action != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    action()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDetailSheet(bid: String, wikiId: Long? = null, onDismiss: () -> Unit) {
    var profile by remember(bid) { mutableStateOf<CustomUserProfile?>(null) }
    var loading by remember(bid) { mutableStateOf(true) }
    var likes by remember(bid) { mutableStateOf<Int?>(null) }
    var error by remember(bid) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(bid, wikiId, retry) {
        loading = true; error = null
        try {
            when (val result = CustomUserApi.fetchProfile(bid = bid, wikiId = wikiId)) {
                is ApiResult.Success -> profile = result.value
                is ApiResult.Error -> error = result.message
            }
            when (val result = CustomUserApi.fetchLikes(bid = profile?.bid ?: bid)) {
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
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (loading && profile == null) {
                // 首载骨架：避免空白面板+小转圈的空洞感
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                ShimmerBox(Modifier.width(140.dp).height(22.dp))
                ShimmerBox(Modifier.width(90.dp).height(14.dp))
                Spacer(Modifier.height(4.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(14.dp))
                ShimmerBox(Modifier.width(180.dp).height(14.dp))
            } else {
                Box(
                    Modifier
                        .size(72.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                        .padding(2.dp)
                ) {
                    CustomProfileAvatar(profile, bid, Modifier.fillMaxSize())
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        profile?.displayName(bid) ?: bid,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                }
                // 统计行：获赞与 WikiID 合并，替代两个孤立元素
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    likes?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                " $it",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    profile?.wikiUserId?.let {
                        Text("·", color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "WikiID $it",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                profile?.bio?.takeIf { it.isNotBlank() }?.let {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // 技术标识弱化到底部
                Text(
                    "BID $bid",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
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
