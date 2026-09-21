package com.nekolaska.calabiyau.core.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nekolaska.calabiyau.core.wiki.WikiAuthHelper
import data.ApiResult
import data.CustomUserApi
import data.ReplyNotification
import data.ReplyNotificationsResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class BoardNotificationsState(
    private val scope: CoroutineScope,
    private val cookies: () -> String? = WikiAuthHelper::getWikiCookies,
    private val fetch: suspend (String, String?) -> ApiResult<ReplyNotificationsResponse> = CustomUserApi::fetchNotifications,
    private val read: suspend (String, Long?, Long?) -> ApiResult<ReplyNotificationsResponse> = CustomUserApi::readNotifications
) {
    var items by mutableStateOf<List<ReplyNotification>>(emptyList())
        private set
    var unread by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var nextCursor by mutableStateOf<String?>(null)
        private set
    private var latestId: Long? = null
    private var loadedCookie: String? = null
    private var accountCookie: String? = null
    private var generation = 0
    var stale by mutableStateOf(false)
        private set

    fun refresh() = load(null)
    fun syncAccount() {
        val current = cookies()
        if (current != accountCookie) {
            accountCookie = current
            generation++
            clear()
            loading = false
            error = null
        }
    }
    fun canOpenCurrentAccount(): Boolean = loadedCookie != null && loadedCookie == cookies()
    fun more() { nextCursor?.let(::load) }
    private fun load(before: String?) {
        syncAccount()
        val token = generation
        val cookie = cookies()
        if (cookie.isNullOrBlank()) { clear(); error = "请先登录 Wiki 查看回复通知"; return }
        if (loadedCookie != null && loadedCookie != cookie) {
            clear()
            if (loading) { error = "登录身份已变化，请稍后刷新"; return }
            load(null)
            return
        }
        if (loading) return
        loading = true; error = null
        scope.launch {
            try {
                val result = fetch(cookie, before)
                if (token != generation) return@launch
                if (cookies() != cookie) { clear(); error = "登录身份已变化，请刷新"; return@launch }
                when (result) {
                    is ApiResult.Success -> {
                        loadedCookie = cookie
                        stale = false
                        items = if (before == null) result.value.notifications else (items + result.value.notifications).distinctBy { it.id }
                        unread = result.value.unreadCount; nextCursor = result.value.nextCursor
                        // Freeze the read-all boundary at the first page so notifications
                        // arriving while paging are not marked read without being displayed.
                        if (before == null) latestId = result.value.latestId
                    }
                    is ApiResult.Error -> {
                        if (result.invalidatesNotificationSession()) clear() else stale = true
                        error = result.message
                    }
                }
            } finally { if (token == generation) loading = false }
        }
    }

    fun markRead(item: ReplyNotification? = null, onSuccess: () -> Unit = {}) {
        syncAccount()
        val token = generation
        val cookie = cookies()
        if (cookie.isNullOrBlank()) { clear(); error = "请先登录 Wiki 查看回复通知"; return }
        if (loadedCookie != null && loadedCookie != cookie) { clear(); error = "登录身份已变化，请刷新"; return }
        if (loading) return
        val boundary = latestId
        if (item == null && boundary == null) return
        loading = true; error = null
        scope.launch {
            try {
                val result = read(cookie, item?.id, if (item == null) boundary else null)
                if (token != generation) return@launch
                if (cookies() != cookie) { clear(); error = "登录身份已变化，请刷新"; return@launch }
                when (result) {
                    is ApiResult.Success -> {
                        items = items.map { if (it.id == item?.id || (item == null && it.id <= boundary!!)) it.copy(read = true) else it }
                        unread = result.value.unreadCount
                        onSuccess()
                    }
                    is ApiResult.Error -> {
                        if (result.invalidatesNotificationSession()) clear() else stale = true
                        error = result.message
                    }
                }
            } finally { if (token == generation) loading = false }
        }
    }

    private fun clear() {
        items = emptyList(); unread = 0; nextCursor = null; latestId = null
        loadedCookie = null; stale = false
    }
}
