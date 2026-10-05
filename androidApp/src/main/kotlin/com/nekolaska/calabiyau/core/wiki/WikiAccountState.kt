package com.nekolaska.calabiyau.core.wiki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import data.ApiResult
import data.CustomUserApi
import data.CustomUserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Account-scoped drawer state. An old response may never populate a new account. */
internal class WikiAccountState(
    private val scope: CoroutineScope,
    private val cookies: () -> String? = WikiAuthHelper::getWikiCookies,
    private val fetchUser: suspend (String) -> ApiResult<WikiUserApi.UserInfo?> = { WikiUserApi.fetchCurrentUserInfo(it) },
    private val fetchProfile: suspend (Long) -> ApiResult<CustomUserProfile?> = { CustomUserApi.fetchProfile(wikiId = it) },
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    var userInfo by mutableStateOf<WikiUserApi.UserInfo?>(null)
        private set
    var profile by mutableStateOf<CustomUserProfile?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var profileLoaded by mutableStateOf(false)
        private set
    var revision by mutableIntStateOf(0)
        private set
    private var initialized = false
    private var accountCookie: String? = null
    private var job: Job? = null
    private var profileRevision = 0
    private var nextRetryAt = Long.MAX_VALUE

    fun syncAccount() {
        val cookie = cookies()
        if (initialized && cookie == accountCookie &&
            (cookie.isNullOrBlank() || userInfo != null || loading || now() < nextRetryAt)) return
        initialized = true
        accountCookie = cookie
        val token = ++revision
        val profileToken = ++profileRevision
        job?.cancel()
        userInfo = null; profile = null; profileLoaded = false; loading = false
        nextRetryAt = Long.MAX_VALUE
        if (cookie.isNullOrBlank()) return
        loading = true
        job = scope.launch {
            try {
                val result = fetchUser(cookie)
                if (!isCurrent(token, cookie)) return@launch
                if (result is ApiResult.Error) { nextRetryAt = now() + 20_000; return@launch }
                val user = (result as? ApiResult.Success)?.value?.takeIf { it.isLoggedIn } ?: return@launch
                userInfo = user
                val custom = fetchProfile(user.id)
                if (isCurrent(token, cookie) && profileToken == profileRevision && custom is ApiResult.Success) {
                    profile = custom.value; profileLoaded = true
                }
            } finally { if (token == revision && profileToken == profileRevision) loading = false }
        }
    }

    fun refreshProfile() {
        syncAccount()
        val user = userInfo ?: return
        val cookie = accountCookie
        val token = revision
        val profileToken = ++profileRevision
        loading = true
        scope.launch {
            try {
                val result = fetchProfile(user.id)
                if (isCurrent(token, cookie) && profileToken == profileRevision && result is ApiResult.Success) {
                    profile = result.value; profileLoaded = true
                }
            } finally { if (token == revision && profileToken == profileRevision) loading = false }
        }
    }

    fun acceptSavedProfile(saved: CustomUserProfile): Boolean {
        syncAccount()
        if (saved.wikiUserId == null || saved.wikiUserId != userInfo?.id) return false
        profileRevision++
        profile = saved
        profileLoaded = true; loading = false
        return true
    }

    private fun isCurrent(token: Int, cookie: String?) = token == revision && cookie == cookies()
}
