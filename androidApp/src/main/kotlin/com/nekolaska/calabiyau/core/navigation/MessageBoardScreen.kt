package com.nekolaska.calabiyau.core.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.core.wiki.CustomProfileAvatar
import com.nekolaska.calabiyau.core.wiki.WikiAuthHelper
import com.nekolaska.calabiyau.core.wiki.WikiUserApi
import data.ApiResult
import data.CustomUserApi
import data.CustomUserProfile
import data.ProfileComment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 公共留言板的目标标识（站点级，非个人档案） */
private const val PUBLIC_BOARD_TARGET = "__public__"

/** 留言分页大小 */
private const val BOARD_PAGE_SIZE = 20

/**
 * 公共留言板（侧栏入口）。
 * 所有人可读；登录用户带身份署名，未登录以访客身份匿名发言（可填昵称，按 IP 限流）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageBoardScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var userInfo by remember { mutableStateOf<WikiUserApi.UserInfo?>(null) }
    var customProfile by remember { mutableStateOf<CustomUserProfile?>(null) }
    var identityLoaded by remember { mutableStateOf(false) }

    var comments by remember { mutableStateOf<List<ProfileComment>>(emptyList()) }
    var commentsTotal by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(1) }
    var isLoadingComments by remember { mutableStateOf(false) }
    var commentsError by remember { mutableStateOf<String?>(null) }

    var nickname by remember { mutableStateOf(AppPrefs.anonymousNickname.orEmpty()) }
    var commentInput by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    // 作者资料详情弹窗
    var profileSheetBid by remember { mutableStateOf<String?>(null) }

    // 每次重组时按最新总数计算
    val totalPages = if (commentsTotal == 0) 1 else (commentsTotal + BOARD_PAGE_SIZE - 1) / BOARD_PAGE_SIZE
    val isAnonymous = userInfo == null

    fun loadInitial() {
        isLoadingComments = true
        commentsError = null
        // 身份识别与留言拉取互不依赖，并行请求，先到先渲染
        scope.launch {
            when (val user = WikiUserApi.fetchCurrentUserInfo()) {
                is ApiResult.Success -> userInfo = user.value?.takeIf { it.isLoggedIn }
                is ApiResult.Error -> userInfo = null
            }
            if (userInfo != null) {
                when (val profile = CustomUserApi.fetchProfile(bid = userInfo!!.name, wikiId = userInfo!!.id)) {
                    is ApiResult.Success -> if (profile.value != null) customProfile = profile.value
                    is ApiResult.Error -> Unit
                }
            }
            identityLoaded = true
        }
        scope.launch {
            when (val result = CustomUserApi.fetchComments(bid = PUBLIC_BOARD_TARGET, page = 1, size = BOARD_PAGE_SIZE)) {
                is ApiResult.Success -> {
                    comments = result.value.comments
                    commentsTotal = result.value.total
                    currentPage = 1
                }
                is ApiResult.Error -> commentsError = result.message
            }
            isLoadingComments = false
        }
    }

    fun loadMore() {
        if (isLoadingComments || currentPage >= totalPages) return
        val nextPage = currentPage + 1
        isLoadingComments = true
        commentsError = null
        scope.launch {
            when (val result = CustomUserApi.fetchComments(bid = PUBLIC_BOARD_TARGET, page = nextPage, size = BOARD_PAGE_SIZE)) {
                is ApiResult.Success -> {
                    // 发帖本地插入与他人中途发帖会造成 offset 漂移，去重防 LazyColumn key 冲突
                    comments = (comments + result.value.comments).distinctBy { it.id }
                    currentPage = nextPage
                }
                is ApiResult.Error -> commentsError = result.message
            }
            isLoadingComments = false
        }
    }

    fun sendComment() {
        val content = commentInput.trim()
        if (content.isEmpty()) return
        val cookies = WikiAuthHelper.getWikiCookies()
        scope.launch {
            isPosting = true
            actionError = null
            when (val result = CustomUserApi.postComment(
                targetBid = PUBLIC_BOARD_TARGET,
                content = content,
                wikiCookie = cookies,
                authorName = nickname.trim().ifBlank { null }
            )) {
                is ApiResult.Success -> {
                    commentInput = ""
                    // 本地插到列表头部，免整页刷新、滚动位置不跳
                    comments = listOf(result.value) + comments
                    commentsTotal += 1
                }
                is ApiResult.Error -> actionError = result.message
            }
            isPosting = false
        }
    }

    fun deleteComment(comment: ProfileComment) {
        val cookies = WikiAuthHelper.getWikiCookies() ?: return
        scope.launch {
            when (val result = CustomUserApi.deleteComment(comment.id, cookies)) {
                is ApiResult.Success -> {
                    comments = comments.filterNot { it.id == comment.id }
                    commentsTotal = (commentsTotal - 1).coerceAtLeast(0)
                }
                is ApiResult.Error -> actionError = result.message
            }
        }
    }

    LaunchedEffect(Unit) { loadInitial() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("留言板", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { loadInitial() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 身份卡：登录显示档案身份，未登录显示访客昵称输入
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                shape = smoothCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                val boardMe = userInfo
                if (identityLoaded && boardMe != null) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CustomProfileAvatar(
                            profile = customProfile,
                            fallbackText = boardMe.name,
                            modifier = Modifier.size(40.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = customProfile?.customName?.takeIf { it.isNotBlank() } ?: boardMe.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "以该身份发言",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (identityLoaded) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("访", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = {
                                if (it.length <= 20) {
                                    nickname = it
                                    AppPrefs.anonymousNickname = it
                                }
                            },
                            placeholder = { Text("访客昵称（可选）") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 留言列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (comments.isEmpty() && !isLoadingComments) {
                    item {
                        Text(
                            text = commentsError ?: "还没有留言，来抢沙发吧",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                items(comments, key = { it.id }) { comment ->
                    val myBid = userInfo?.name
                    MessageBoardCommentItem(
                        comment = comment,
                        deletable = myBid != null && comment.authorBid == myBid,
                        onDelete = { deleteComment(comment) },
                        onAuthorClick = {
                            if (comment.authorBid != "anon") profileSheetBid = comment.authorBid
                        }
                    )
                }
                if (currentPage < totalPages) {
                    item {
                        TextButton(
                            onClick = { loadMore() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoadingComments
                        ) { Text(if (isLoadingComments) "加载中…" else "加载更多（共 $commentsTotal 条）") }                    }
                }
                if (isLoadingComments && comments.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }

            if (actionError != null || commentsError != null) {
                Text(
                    text = actionError ?: commentsError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // 输入行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { if (it.length <= 200) commentInput = it },
                    placeholder = { Text("写下你的留言…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    enabled = !isPosting
                )
                FilledTonalButton(
                    onClick = { sendComment() },
                    enabled = !isPosting && commentInput.isNotBlank(),
                    modifier = Modifier.height(52.dp)
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    // 作者资料详情弹窗
    profileSheetBid?.let { sheetBid ->
        ProfileDetailSheet(bid = sheetBid, onDismiss = { profileSheetBid = null })
    }
}

/** 作者资料详情弹窗：展示该用户的完整自定义档案与获赞数 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDetailSheet(
    bid: String,
    onDismiss: () -> Unit
) {
    var profile by remember { mutableStateOf<CustomUserProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var likeCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(bid) {
        loading = true
        when (val result = CustomUserApi.fetchProfile(bid = bid)) {
            is ApiResult.Success -> profile = result.value
            is ApiResult.Error -> Unit
        }
        when (val result = CustomUserApi.fetchLikes(bid = bid, wikiCookie = WikiAuthHelper.getWikiCookies())) {
            is ApiResult.Success -> likeCount = result.value.count
            is ApiResult.Error -> Unit
        }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = smoothCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CustomProfileAvatar(
                profile = profile,
                fallbackText = bid,
                modifier = Modifier.size(64.dp),
                textStyle = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile?.customName?.takeIf { it.isNotBlank() } ?: bid,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detailBadge = profile?.badge
                if (!detailBadge.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = smoothCornerShape(6.dp)
                    ) {
                        Text(
                            text = detailBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = "BID：$bid · WikiID：${profile?.wikiUserId ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val detailBio = profile?.bio
            if (!detailBio.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = detailBio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = smoothCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "获赞 $likeCount",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            if (loading) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun MessageBoardCommentItem(
    comment: ProfileComment,
    deletable: Boolean,
    onDelete: () -> Unit,
    onAuthorClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CustomProfileAvatar(
                profile = CustomUserProfile(
                    bid = comment.authorBid,
                    customName = comment.authorName,
                    avatarUrl = comment.authorAvatarUrl
                ),
                fallbackText = comment.authorBid,
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onAuthorClick)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.authorName?.takeIf { it.isNotBlank() } ?: comment.authorBid
                        .let { name ->
                            // 匿名留言追加稳定访客编号，如「访客#042」
                            val tag = comment.authorTag
                                ?.takeIf { comment.authorBid == "anon" && it.isNotBlank() }
                                ?.let { "#$it" } ?: ""
                            "$name$tag"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable(onClick = onAuthorClick)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatBoardTime(comment.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (deletable) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除留言",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onDelete)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = comment.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatBoardTime(epochSeconds: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))
