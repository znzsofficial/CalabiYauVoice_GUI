package com.nekolaska.calabiyau.core.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.wiki.WikiAuthHelper
import com.nekolaska.calabiyau.core.wiki.WikiUserApi
import data.ApiResult
import data.CustomUserApi
import data.CustomUserProfile
import data.ProfileComment
import data.ProfileCommentsResponse
import data.SharedJson
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
        fun ProfileComment.deletedCopy() = copy(deleted = true, content = "该留言已删除", authorBid = "",
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
    }
) {
    var userInfo by mutableStateOf<WikiUserApi.UserInfo?>(null)
        private set
    var profile by mutableStateOf<CustomUserProfile?>(null)
        private set
    var identityLoaded by mutableStateOf(false)
        private set
    var nickname by mutableStateOf(AppPrefs.anonymousNickname.orEmpty())
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
    private val drafts = BoardDraftStore({ AppPrefs.messageBoardDraftsJson }, { AppPrefs.messageBoardDraftsJson = it })
    private var draftVersion by mutableIntStateOf(0)
    val replyTargetUnavailable: Boolean
        get() { draftVersion; return drafts.get(threadId).targetUnavailable }
    var draft: String
        get() { draftVersion; return if (WikiAuthHelper.getWikiCookies() == identityCookie) drafts.get(threadId).content else "" }
        set(value) {
            if (!identityLoaded || WikiAuthHelper.getWikiCookies() != identityCookie) { syncIdentity(); return }
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
    var focusedReplyId by mutableStateOf<Long?>(null)
        private set
    var focusScrollPending by mutableStateOf(false)
        private set
    private val guestId = AppPrefs.anonymousGuestId?.takeIf { it.isNotBlank() }
        ?: UUID.randomUUID().toString().also { AppPrefs.anonymousGuestId = it }

    init {
        refresh()
    }

    fun syncIdentity() {
        if (WikiAuthHelper.getWikiCookies() != observedCookie) refreshIdentity()
    }

    private fun refreshIdentity() {
        val token = ++authGeneration
        identityJob?.cancel()
        identityLoaded = false
        userInfo = null
        profile = null
        replyTarget = null
        drafts.useIdentity(null)
        draftVersion++
        val cookie = WikiAuthHelper.getWikiCookies()
        observedCookie = cookie
        identityJob = scope.launch {
            try {
                userInfo = null
                profile = null
                if (!cookie.isNullOrBlank()) {
                when (val result = CustomUserApi.fetchSession(cookie)) {
                    is ApiResult.Success -> {
                        if (token != authGeneration || WikiAuthHelper.getWikiCookies() != cookie) return@launch
                        userInfo = WikiUserApi.UserInfo(result.value.wikiUserId, result.value.bid)
                    }
                    is ApiResult.Error -> { if (token == authGeneration) error = result.message; return@launch }
                }
                if (userInfo == null) { error = "请重新登录 Wiki"; return@launch }
                }
                if (token != authGeneration || WikiAuthHelper.getWikiCookies() != cookie) return@launch
                identityCookie = cookie
                drafts.useIdentity(userInfo?.id?.let { "wiki:$it" } ?: "guest:$guestId")
                draftVersion++
                identityLoaded = true
                restoreReplyTarget()
                userInfo?.let { user ->
                    when (val result = CustomUserApi.fetchProfile(bid = user.name, wikiId = user.id)) {
                        is ApiResult.Success -> if (token == authGeneration && WikiAuthHelper.getWikiCookies() == cookie) profile = result.value
                        is ApiResult.Error -> Unit
                    }
                }
            } finally { if (token == authGeneration && WikiAuthHelper.getWikiCookies() != cookie) identityLoaded = false }
        }
    }

    fun updateNickname(value: String) {
        if (value.length <= 20) { nickname = value; AppPrefs.anonymousNickname = value }
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
        if (WikiAuthHelper.getWikiCookies() != identityCookie) { syncIdentity(); return }
        if (identityLoaded && !posting && root?.deleted != true && comment?.deleted != true) {
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
        if (identityLoaded && (root?.deleted == true || replyTarget?.deleted == true)) {
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

        // 首屏主列表：先渲染磁盘缓存（公开数据，身份类 UI 由独立状态驱动），
        // 网络刷新在后台继续，完成后无感覆盖。缓存读取独立于 listJob（不受取消影响），
        // 仅在期间没有新数据到达时渲染。
        if (cursor == null && focusId == null && selected == null && comments.isEmpty()) {
            scope.launch {
                readListCache()?.let { cached ->
                    runCatching {
                        val data = SharedJson.decodeFromString<ProfileCommentsResponse>(cached)
                        if (token == generation && comments.isEmpty() && data.comments.isNotEmpty()) {
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
                when (val result = CustomUserApi.fetchComments(bid = "__public__", before = cursor, rootId = selected, focusId = focusId)) {
                    is ApiResult.Success -> if (token == generation) {
                        val data = result.value
                        comments = if (cursor == null) data.comments else (comments + data.comments).distinctBy { it.id }
                        pageLoaded = true
                        nextCursor = data.nextCursor; total = data.total
                        root = data.root
                        pinned = data.pinnedComments
                        val target = replyTarget
                        if (identityLoaded && (data.root?.deleted == true || comments.any { it.id == target?.id && it.deleted } ||
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
        if (posting || loading || deletingId != null || !identityLoaded || content.isEmpty()) return
        val selected = threadId
        if (selected != null && (root == null || root?.deleted == true)) return
        val cookies = WikiAuthHelper.getWikiCookies()
        if (cookies != identityCookie) { refreshIdentity(); error = "身份已变化，请确认后重新发送"; return }
        val authorName = if (cookies.isNullOrBlank()) nickname.trim().ifBlank { null } else null
        val actor = if (cookies.isNullOrBlank()) "guest:$guestId" else "wiki:${userInfo!!.id}"
        val pending = try { drafts.prepare(selected, authorName, actor) }
            catch (e: IllegalStateException) { error = e.message; return }
        posting = true; error = null
        scope.launch {
            try {
                if (!withContext(Dispatchers.IO) { AppPrefs.flushMessageBoardDrafts() }) {
                    error = "无法保存待发送请求，请重试"
                    return@launch
                }
                when (val result = CustomUserApi.postComment(root?.targetBid ?: "__public__", pending.content, cookies, pending.authorName, pending.requestId, guestId, pending.replyToId)) {
                    is ApiResult.Success -> {
                        drafts.acknowledge(selected, pending.requestId, actor)
                        draftVersion++
                        if (identityLoaded && identityCookie == cookies) replyTarget = null
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
                        if (selected != null && cookies == identityCookie && identityLoaded && result.apiCode == "REPLY_TARGET_UNAVAILABLE") {
                            drafts.markTargetUnavailable(selected)
                            draftVersion++
                        }
                    }
                }
            } finally { posting = false }
        }
    }

    fun delete(comment: ProfileComment) {
        if (!identityLoaded || WikiAuthHelper.getWikiCookies() != identityCookie) { syncIdentity(); return }
        if (posting || loading || deletingId != null || comment.deleted) return
        val cookies = WikiAuthHelper.getWikiCookies() ?: return
        deletingId = comment.id; error = null
        scope.launch {
            var success = false
            try {
                when (val result = CustomUserApi.deleteComment(comment.id, cookies)) {
                    is ApiResult.Success -> {
                        success = true
                        // Commit the server-confirmed tombstone locally before attempting a read.
                        restorePage(page().afterDelete(comment))
                        if (identityLoaded && cookies == identityCookie && (root?.deleted == true || replyTarget?.id == comment.id)) {
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
