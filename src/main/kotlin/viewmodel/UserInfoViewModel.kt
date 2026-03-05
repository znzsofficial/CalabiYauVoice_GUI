package viewmodel

import data.WikiCookieManager
import data.WikiUserApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class UserInfoTab { INFO, CONTRIBUTIONS, WATCHLIST, LOG }

class UserInfoViewModel(private val scope: CoroutineScope) {

    // ─── Cookie 输入 ───────────────────────────────────────────────
    private val _cookieInput = MutableStateFlow(WikiCookieManager.currentCookieString)
    val cookieInput: StateFlow<String> = _cookieInput.asStateFlow()

    // ─── 用户信息 ──────────────────────────────────────────────────
    private val _userInfo = MutableStateFlow<WikiUserApi.UserInfo?>(null)
    val userInfo: StateFlow<WikiUserApi.UserInfo?> = _userInfo.asStateFlow()

    private val _isLoadingInfo = MutableStateFlow(false)
    val isLoadingInfo: StateFlow<Boolean> = _isLoadingInfo.asStateFlow()

    // ─── 贡献 ──────────────────────────────────────────────────────
    private val _contributions = MutableStateFlow<List<WikiUserApi.UserContrib>>(emptyList())
    val contributions: StateFlow<List<WikiUserApi.UserContrib>> = _contributions.asStateFlow()

    private val _isLoadingContrib = MutableStateFlow(false)
    val isLoadingContrib: StateFlow<Boolean> = _isLoadingContrib.asStateFlow()

    // ─── 监视列表 ──────────────────────────────────────────────────
    private val _watchlist = MutableStateFlow<List<WikiUserApi.WatchlistItem>>(emptyList())
    val watchlist: StateFlow<List<WikiUserApi.WatchlistItem>> = _watchlist.asStateFlow()

    private val _isLoadingWatch = MutableStateFlow(false)
    val isLoadingWatch: StateFlow<Boolean> = _isLoadingWatch.asStateFlow()

    // ─── 操作日志 ──────────────────────────────────────────────────
    private val _logEvents = MutableStateFlow<List<WikiUserApi.LogEvent>>(emptyList())
    val logEvents: StateFlow<List<WikiUserApi.LogEvent>> = _logEvents.asStateFlow()

    private val _isLoadingLog = MutableStateFlow(false)
    val isLoadingLog: StateFlow<Boolean> = _isLoadingLog.asStateFlow()

    // ─── 当前 Tab ─────────────────────────────────────────────────
    private val _currentTab = MutableStateFlow(UserInfoTab.INFO)
    val currentTab: StateFlow<UserInfoTab> = _currentTab.asStateFlow()

    // ─── 通用状态消息 ──────────────────────────────────────────────
    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // ─── Cookie 已导入标记 ─────────────────────────────────────────
    val hasCookies: StateFlow<Boolean> = _userInfo
        .map { WikiCookieManager.hasCookies }
        .stateIn(scope, SharingStarted.Eagerly, WikiCookieManager.hasCookies)

    // ──────────────────────────────────────────────────────────────

    fun onCookieInputChange(value: String) { _cookieInput.value = value }

    fun onTabChange(tab: UserInfoTab) { _currentTab.value = tab }

    /**
     * 导入 Cookie 并立即查询用户信息。
     */
    fun importAndFetch() {
        val count = WikiCookieManager.importCookies(_cookieInput.value)
        if (count == 0) {
            _statusMessage.value = "⚠️ 未能解析到有效的 Cookie，请检查格式"
            return
        }
        _statusMessage.value = "✅ 已导入 $count 个 Cookie，正在查询用户信息…"
        fetchUserInfo()
    }

    /**
     * 清除 Cookie 并重置所有状态。
     */
    fun clearCookies() {
        WikiCookieManager.clearCookies()
        _cookieInput.value = ""
        _userInfo.value = null
        _contributions.value = emptyList()
        _watchlist.value = emptyList()
        _logEvents.value = emptyList()
        _statusMessage.value = "Cookie 已清除"
    }

    fun fetchUserInfo() {
        scope.launch {
            _isLoadingInfo.value = true
            try {
                val info = WikiUserApi.fetchCurrentUserInfo()
                _userInfo.value = info
                _statusMessage.value = when {
                    info == null -> "❌ 请求失败，请检查网络连接"
                    info.isLoggedIn -> "✅ 已登录：${info.name}"
                    else -> "⚠️ Cookie 无效或已过期，当前为未登录状态"
                }
            } finally {
                _isLoadingInfo.value = false
            }
        }
    }

    fun fetchContributions() {
        val name = _userInfo.value?.name ?: WikiCookieManager.extractUserNameFromCookies() ?: return
        scope.launch {
            _isLoadingContrib.value = true
            try {
                _contributions.value = WikiUserApi.fetchContributions(name)
            } finally {
                _isLoadingContrib.value = false
            }
        }
    }

    fun fetchWatchlist() {
        scope.launch {
            _isLoadingWatch.value = true
            try {
                _watchlist.value = WikiUserApi.fetchWatchlist()
            } finally {
                _isLoadingWatch.value = false
            }
        }
    }

    fun fetchLogEvents() {
        val name = _userInfo.value?.name ?: WikiCookieManager.extractUserNameFromCookies() ?: return
        scope.launch {
            _isLoadingLog.value = true
            try {
                _logEvents.value = WikiUserApi.fetchUserLogEvents(name)
            } finally {
                _isLoadingLog.value = false
            }
        }
    }

    /**
     * 切换 Tab 时按需加载数据。
     */
    fun onTabSelected(tab: UserInfoTab) {
        _currentTab.value = tab
        val info = _userInfo.value ?: return
        if (!info.isLoggedIn) return
        when (tab) {
            UserInfoTab.CONTRIBUTIONS -> if (_contributions.value.isEmpty()) fetchContributions()
            UserInfoTab.WATCHLIST -> if (_watchlist.value.isEmpty()) fetchWatchlist()
            UserInfoTab.LOG -> if (_logEvents.value.isEmpty()) fetchLogEvents()
            else -> Unit
        }
    }
}

