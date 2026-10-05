package com.nekolaska.calabiyau.core.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiAuthHelper
import com.nekolaska.calabiyau.core.wiki.WikiUserApi
import data.ApiResult
import data.CustomUserApi
import data.CustomUserProfile
import data.ProfileComment
import data.ProfileCommentsResponse
import data.SharedJson
import data.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal data class BoardPage(
    val comments: List<ProfileComment> = emptyList(),
    val root: ProfileComment? = null,
    val nextCursor: String? = null,
    val total: Int = 0,
    val pinned: List<ProfileComment> = emptyList()
) {
    fun withRoot(updated: ProfileComment): BoardPage {
        val present = comments.any { it.id == updated.id }
        val remove = updated.deleted && updated.replyCount == 0
        return copy(
            pinned = if (remove || updated.deleted || updated.pinnedAt == null) pinned.filterNot { it.id == updated.id }
                else pinned.map { if (it.id == updated.id) updated else it },
            comments = if (remove) comments.filterNot { it.id == updated.id }
                else comments.map { if (it.id == updated.id) updated else it },
            total = (total - if (present && remove) 1 else 0).coerceAtLeast(0)
        )
    }

    fun afterDelete(comment: ProfileComment): BoardPage {
        fun ProfileComment.deletedCopy() = copy(deleted = true, content = "该留言已删除", authorBid = "", authorWikiUserId = null,
            authorName = "已删除留言", authorTag = null, authorAvatarUrl = null, replyToName = null, replyToTag = null)
        val newRoot = when {
            root?.id == comment.id -> root.deletedCopy()
            root != null && root.id == comment.rootId && !comment.deleted -> root.copy(replyCount = (root.replyCount - 1).coerceAtLeast(0))
            else -> root
        }
        val updated = comments.map {
            when {
                it.id == comment.id -> it.deletedCopy()
                it.replyToId == comment.id -> it.copy(replyToName = "已删除留言", replyToTag = null)
                else -> it
            }
        }
        val page = copy(comments = updated, root = newRoot)
        return if (root == null && comment.rootId == null) page.withRoot(comment.deletedCopy()) else page
    }

    fun afterLoadFailure(error: ApiResult.Error): BoardPage =
        if (error.invalidatesDiscussion()) BoardPage() else this
}

/** Screen-owned state: leaving the screen cancels its scope; switching threads cancels stale reads. */
internal class MessageBoardState(
    private val scope: CoroutineScope,
    // 列表缓存读写可注入（测试传内存实现）；默认走 OfflineCache
    private val readListCache: suspend () -> String? = {
        OfflineCache.getEntry(OfflineCache.Type.MESSAGE_BOARD, "public_list")?.content
    },
    private val writeListCache: suspend (String) -> Unit = {
        OfflineCache.put(OfflineCache.Type.MESSAGE_BOARD, "public_list", it)
    },
    private val preferences: BoardPreferences = AppBoardPreferences,
    private val cookies: () -> String? = WikiAuthHelper::getWikiCookies,
    private val fetchSession: suspend (String) -> ApiResult<UserSession> = { CustomUserApi.fetchSession(it) },
    private val fetchProfile: suspend (Long) -> ApiResult<CustomUserProfile?> = { CustomUserApi.fetchProfile(wikiId = it) },
    private val fetchPage: suspend (String?, Long?, Long?) -> ApiResult<ProfileCommentsResponse> = { cursor, rootId, focusId ->
        CustomUserApi.fetchComments(bid = "__public__", before = cursor, rootId = rootId, focusId = focusId)
    },
    private val post: suspend (String, PendingBoardPost, String?, String) -> ApiResult<ProfileComment> = { target, pending, cookie, guestId ->
        CustomUserApi.postComment(target, pending.content, cookie, pending.authorName, pending.requestId, guestId, pending.replyToId)
    },
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    var userInfo by mutableStateOf<WikiUserApi.UserInfo?>(null)
        private set
    var profile by mutableStateOf<CustomUserProfile?>(null)
        private set
    var identityLoaded by mutableStateOf(false)
        private set
    var nickname by mutableStateOf(preferences.nickname.orEmpty())
        private set
    var comments by mutableStateOf<List<ProfileComment>>(emptyList())
        private set
    var pinned by mutableStateOf<List<ProfileComment>>(emptyList())
        private set
    var root by mutableStateOf<ProfileComment?>(null)
        private set
    var threadId by mutableStateOf<Long?>(null)
        private set
    var replyTarget by mutableStateOf<ProfileComment?>(null)
        private set
    var nextCursor by mutableStateOf<String?>(null)
        private set
    var total by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set
    var posting by mutableStateOf(false)
        private set
    var deletingId by mutableStateOf<Long?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private val drafts = BoardDraftStore({ preferences.draftsJson }, { preferences.draftsJson = it })
    private var draftVersion by mutableIntStateOf(0)
    val replyTargetUnavailable: Boolean
        get() { draftVersion; return drafts.get(threadId).targetUnavailable }
    var draft: String
        get() { draftVersion; return if (postingAsGuest || cookies() == identityCookie) drafts.get(threadId).content else "" }
        set(value) {
            if (!postingAsGuest && (!identityLoaded || cookies() != identityCookie)) { syncIdentity(); return }
            if (value.length <= 200) {
                drafts.edit(threadId, value, replyTarget?.id, replyTarget?.displayAuthor())
                draftVersion++
            }
        }
    private var boardPage: BoardPage? = null
    private val threadPages = mutableMapOf<Long, BoardPage>()
    private var pageLoaded = false
    private var listJob: Job? = null
    private var generation = 0
    private var identityCookie: String? = null
    private var identityJob: Job? = null
    private var authGeneration = 0
    private var observedCookie: String? = null
    private var nextIdentityRetryAt = Long.MAX_VALUE
    var focusedReplyId by mutableStateOf<Long?>(null)
        private set
    var focusScrollPending by mutableStateOf(false)
        private set
    private val guestId = preferences.guestId?.takeIf { it.isNotBlank() }
        ?: UUID.randomUUID().toString().also { preferences.guestId = it }

    /** 登录用户可选择以匿名（访客）身份发言；跨会话记忆 */
    var postingAsGuest by mutableStateOf(preferences.postAsGuest)
        private set

    fun togglePostingAsGuest() {
        if (posting || drafts.get(threadId).pending != null) {
            error = "上次发送结果尚未确认，请先核对或使用原身份重试，再切换发言身份。"
            return
        }
        postingAsGuest = !postingAsGuest
        preferences.postAsGuest = postingAsGuest
        bindDraftIdentity()
        restoreReplyTarget()
        if (!postingAsGuest && !identityLoaded) refreshIdentity()
    }

    private fun bindDraftIdentity() {
        drafts.useIdentity(when {
            postingAsGuest -> "guest:$guestId"
            identityLoaded -> userInfo?.id?.let { "wiki:$it" } ?: "guest:$guestId"
            else -> null
        })
        draftVersion++
    }

    /** 上次发言身份的缓存文案，身份识别完成前先展示，避免转圈等待 */
    val cachedIdentityLabel: String?
        get() = preferences.lastIdentity?.takeIf { it.isNotBlank() }

    init {
        refresh()
    }

    fun syncIdentity() {
        if (cookies() != observedCookie || (!identityLoaded && identityJob?.isActive != true && now() >= nextIdentityRetryAt)) refreshIdentity()
    }

    private fun refreshIdentity() {
        val token = ++authGeneration
        identityJob?.cancel()
        identityLoaded = false
        nextIdentityRetryAt = Long.MAX_VALUE
        userInfo = null
        profile = null
        replyTarget = null
        bindDraftIdentity()
        val cookie = cookies()
        observedCookie = cookie
        identityJob = scope.launch {
            try {
                userInfo = null
                profile = null
                if (!cookie.isNullOrBlank()) {
                when (val result = fetchSession(cookie)) {
                    is ApiResult.Success -> {
                        if (token != authGeneration || cookies() != cookie) return@launch
                        userInfo = WikiUserApi.UserInfo(result.value.wikiUserId, result.value.bid)
                    }
                    is ApiResult.Error -> {
                        if (token == authGeneration) {
                            error = result.message
                            if (result.httpStatus != 401) nextIdentityRetryAt = now() + 20_000
                        }
                        return@launch
                    }
                }
                if (userInfo == null) { error = "请重新登录 Wiki"; return@launch }
                }
                if (token != authGeneration || cookies() != cookie) return@launch
                identityCookie = cookie
                identityLoaded = true
                bindDraftIdentity()
                preferences.lastIdentity = userInfo?.name ?: "访客"
                restoreReplyTarget()
                userInfo?.let { user ->
                    when (val result = fetchProfile(user.id)) {
                        is ApiResult.Success -> if (token == authGeneration && cookies() == cookie) profile = result.value
                        is ApiResult.Error -> Unit
                    }
                }
            } finally { if (token == authGeneration && cookies() != cookie) identityLoaded = false }
        }
    }

    fun updateNickname(value: String) {
        if (value.length <= 20) { nickname = value; preferences.nickname = value }
    }

    fun openThread(comment: ProfileComment) {
        if (posting || deletingId != null) return
        focusedReplyId = null
        focusScrollPending = false
        savePage()
        cancelRead()
        threadId = comment.rootId ?: comment.id
        val cached = threadPages[threadId]
        pageLoaded = cached != null
        restorePage(cached ?: BoardPage(root = if (comment.rootId == null) comment else null))
        restoreReplyTarget()
        load(null)
    }

    fun closeThread() {
        if (posting || deletingId != null) return
        focusedReplyId = null
        focusScrollPending = false
        savePage()
        cancelRead()
        threadId = null; root = null; replyTarget = null
        val cached = boardPage
        pageLoaded = cached != null
        restorePage(cached ?: BoardPage())
        if (cached == null) refresh()
    }

    fun selectReply(comment: ProfileComment?) {
        if (!postingAsGuest && cookies() != identityCookie) { syncIdentity(); return }
        if ((identityLoaded || postingAsGuest) && !posting && root?.deleted != true && comment?.deleted != true) {
            replyTarget = comment
            drafts.selectTarget(threadId, comment?.id, comment?.displayAuthor())
            draftVersion++
        }
    }

    private fun page() = BoardPage(comments, root, nextCursor, total, pinned)
    private fun restorePage(page: BoardPage) {
        comments = page.comments; root = page.root; nextCursor = page.nextCursor; total = page.total
        pinned = page.pinned
    }
    private fun savePage() {
        if (!pageLoaded) return
        val id = threadId
        if (id == null) boardPage = page() else threadPages[id] = page()
    }
    private fun cancelRead() {
        generation++
        listJob?.cancel()
        loading = false; error = null
    }
    private fun restoreReplyTarget() {
        val stored = drafts.get(threadId)
        replyTarget = stored.replyToId?.let { id ->
            comments.find { it.id == id } ?: ProfileComment(id = id, authorBid = "", authorName = stored.replyToName, content = "")
        }
        if ((identityLoaded || postingAsGuest) && (root?.deleted == true || replyTarget?.deleted == true)) {
            drafts.markTargetUnavailable(threadId)
            draftVersion++
        }
    }
    private fun updateCachedRoot() {
        root?.let { boardPage = boardPage?.withRoot(it) }
    }

    fun refresh() {
        if (posting || deletingId != null) return
        refreshIdentity()
        focusedReplyId = null
        focusScrollPending = false
        load(null)
    }

    fun openNotification(rootId: Long, commentId: Long) {
        if (posting || deletingId != null) return
        savePage(); cancelRead()
        threadId = rootId
        val cached = threadPages[rootId]
        pageLoaded = cached != null
        restorePage(cached ?: BoardPage())
        restoreReplyTarget()
        focusedReplyId = commentId
        focusScrollPending = true
        load(null, commentId)
    }

    fun finishFocusScroll() { focusScrollPending = false }

    fun loadMore() {
        if (loading || posting || deletingId != null) return
        nextCursor?.let { load(it) }
    }

    private fun load(cursor: String?, focusId: Long? = null) {
        listJob?.cancel()
        val token = ++generation
        val selected = threadId
        var networkSucceeded = false

        // 首屏主列表：先渲染磁盘缓存（公开数据，身份类 UI 由独立状态驱动），
        // 网络刷新在后台继续，完成后无感覆盖。缓存读取独立于 listJob（不受取消影响），
        // 仅在期间没有新数据到达时渲染。
        if (cursor == null && focusId == null && selected == null && comments.isEmpty()) {
            scope.launch {
                readListCache()?.let { cached ->
                    runCatching {
                        val data = SharedJson.decodeFromString<ProfileCommentsResponse>(cached)
                        if (token == generation && !networkSucceeded && comments.isEmpty() && data.comments.isNotEmpty()) {
                            comments = data.comments
                            root = data.root
                            pinned = data.pinnedComments
                            total = data.total
                            nextCursor = data.nextCursor
                            pageLoaded = true
                            loading = false
                        }
                    }
                }
            }
        }

        loading = true; error = null
        listJob = scope.launch {
            try {
                when (val result = fetchPage(cursor, selected, focusId)) {
                    is ApiResult.Success -> if (token == generation) {
                        val data = result.value
                        networkSucceeded = true
                        comments = if (cursor == null) data.comments else (comments + data.comments).distinctBy { it.id }
                        pageLoaded = true
                        nextCursor = data.nextCursor; total = data.total
                        root = data.root
                        pinned = data.pinnedComments
                        val target = replyTarget
                        if ((identityLoaded || postingAsGuest) && (data.root?.deleted == true || comments.any { it.id == target?.id && it.deleted } ||
                                (target != null && cursor == null && !data.hasMore && comments.none { it.id == target.id }))) {
                            drafts.markTargetUnavailable(threadId)
                            draftVersion++
                        }
                        updateCachedRoot()
                        savePage()
                        if (cursor == null && focusId == null && selected == null) {
                            writeListCache(SharedJson.encodeToString(data))
                        }
                    }
                    is ApiResult.Error -> if (token == generation) {
                        error = result.message
                        if (focusId != null && result.apiCode == "FOCUS_UNAVAILABLE") {
                            focusedReplyId = null
                            focusScrollPending = false
                            load(null)
                            return@launch
                        }
                        if (selected != null && result.invalidatesDiscussion()) {
                            restorePage(page().afterLoadFailure(result))
                            threadPages.remove(selected)
                            pageLoaded = false
                        }
                    }
                }
            } finally { if (token == generation) loading = false }
        }
    }

    fun send() {
        val content = draft.trim()
        // 匿名发帖身份即本地访客标识，无需等待在线身份识别
        if (posting || loading || deletingId != null || (!identityLoaded && !postingAsGuest) || content.isEmpty()) return
        val selected = threadId
        if (selected != null && (root == null || root?.deleted == true)) return
        val cookie = if (postingAsGuest) null else cookies()
        if (!postingAsGuest && cookie != identityCookie) { refreshIdentity(); error = "身份已变化，请确认后重新发送"; return }
        val authorName = if (cookie.isNullOrBlank()) nickname.trim().ifBlank { null } else null
        val actor = drafts.currentOwner ?: return
        val submissionActor = if (cookie.isNullOrBlank()) "guest:$guestId" else userInfo?.id?.let { "wiki:$it" } ?: return
        val targetBid = root?.targetBid ?: "__public__"
        val pending = try { drafts.prepare(selected, authorName, actor, submissionActor, targetBid) }
            catch (e: IllegalStateException) { error = e.message; return }
        posting = true; error = null
        scope.launch {
            try {
                if (!withContext(Dispatchers.IO) { preferences.flushDrafts() }) {
                    error = "无法保存待发送请求，请重试"
                    return@launch
                }
                when (val result = post(pending.targetBid ?: targetBid, pending, cookie, guestId)) {
                    is ApiResult.Success -> {
                        drafts.acknowledge(selected, pending.requestId, actor)
                        draftVersion++
                        if (postingAsGuest || (identityLoaded && identityCookie == cookie)) replyTarget = null
                        load(null)
                    }
                    is ApiResult.Error -> {
                        error = result.message
                        if (result.apiCode in setOf("IDEMPOTENT_RESULT_DELETED", "IDEMPOTENT_RESULT_HIDDEN")) {
                            drafts.acknowledge(selected, pending.requestId, actor)
                            draftVersion++
                            error = "此前的留言已发送，但现在已被删除或隐藏。"
                            return@launch
                        }
                        if (selected != null && (postingAsGuest || (cookie == identityCookie && identityLoaded)) && result.apiCode == "REPLY_TARGET_UNAVAILABLE") {
                            drafts.markTargetUnavailable(selected)
                            draftVersion++
                        }
                    }
                }
            } finally { posting = false }
        }
    }

    fun delete(comment: ProfileComment) {
        if (!identityLoaded || cookies() != identityCookie) { syncIdentity(); return }
        if (posting || loading || deletingId != null || comment.deleted) return
        val cookie = cookies() ?: return
        deletingId = comment.id; error = null
        scope.launch {
            var success = false
            try {
                when (val result = CustomUserApi.deleteComment(comment.id, cookie)) {
                    is ApiResult.Success -> {
                        success = true
                        // Commit the server-confirmed tombstone locally before attempting a read.
                        restorePage(page().afterDelete(comment))
                        if (identityLoaded && cookie == identityCookie && (root?.deleted == true || replyTarget?.id == comment.id)) {
                            drafts.markTargetUnavailable(threadId)
                            draftVersion++
                        }
                        if (threadId == null) {
                            threadPages[comment.id]?.let { threadPages[comment.id] = it.afterDelete(comment) }
                        }
                        updateCachedRoot()
                        savePage()
                    }
                    is ApiResult.Error -> error = result.message
                }
            } finally { deletingId = null }
            if (success) refresh()
        }
    }
}
