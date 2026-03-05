package viewmodel

import data.WikiCookieManager
import data.WikiUserApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class UserInfoTab { INFO, CONTRIBUTIONS, WATCHLIST, LOG }

/** 操作日志排序方式 */
enum class LogSortOrder { NEWEST_FIRST, OLDEST_FIRST }

/** 查询用户详情子 Tab */
enum class LookupDetailTab { SUMMARY, FILES, LOG }

class UserInfoViewModel(private val scope: CoroutineScope) {

    // ─── Cookie 输入 ───────────────────────────────────────────────
    private val _cookieInput = MutableStateFlow(WikiCookieManager.currentCookieString)
    val cookieInput: StateFlow<String> = _cookieInput.asStateFlow()

    // ─── 用户信息 ──────────────────────────────────────────────────
    private val _userInfo = MutableStateFlow<WikiUserApi.UserInfo?>(null)
    val userInfo: StateFlow<WikiUserApi.UserInfo?> = _userInfo.asStateFlow()

    private val _isLoadingInfo = MutableStateFlow(false)
    val isLoadingInfo: StateFlow<Boolean> = _isLoadingInfo.asStateFlow()

    // ─── 封禁状态 ──────────────────────────────────────────────────
    private val _blockStatus = MutableStateFlow<WikiUserApi.BlockInfo?>(null)
    val blockStatus: StateFlow<WikiUserApi.BlockInfo?> = _blockStatus.asStateFlow()

    // ─── 最后编辑时间 ─────────────────────────────────────────────
    private val _lastEditTimestamp = MutableStateFlow<String?>(null)
    val lastEditTimestamp: StateFlow<String?> = _lastEditTimestamp.asStateFlow()

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

    private val _isLoadingLog = MutableStateFlow(false)
    val isLoadingLog: StateFlow<Boolean> = _isLoadingLog.asStateFlow()

    // 日志筛选 & 排序
    private val _logTypeFilter = MutableStateFlow<String?>(null)   // null = 全部
    val logTypeFilter: StateFlow<String?> = _logTypeFilter.asStateFlow()

    private val _logSortOrder = MutableStateFlow(LogSortOrder.NEWEST_FIRST)
    val logSortOrder: StateFlow<LogSortOrder> = _logSortOrder.asStateFlow()

    val logEvents: StateFlow<List<WikiUserApi.LogEvent>> = combine(
        _logEvents, _logTypeFilter, _logSortOrder
    ) { events, filter, order ->
        val filtered = if (filter == null) events else events.filter { it.type == filter }
        if (order == LogSortOrder.OLDEST_FIRST) filtered.reversed() else filtered
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val availableLogTypes: StateFlow<List<String>> = _logEvents
        .map { events -> events.map { it.type }.distinct().sorted() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

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

    // ─── 公开用户查询 ──────────────────────────────────────────────
    private val _lookupQuery = MutableStateFlow("")
    val lookupQuery: StateFlow<String> = _lookupQuery.asStateFlow()

    private val _lookupResult = MutableStateFlow<WikiUserApi.PublicUserInfo?>(null)
    val lookupResult: StateFlow<WikiUserApi.PublicUserInfo?> = _lookupResult.asStateFlow()

    private val _lookupBlockStatus = MutableStateFlow<WikiUserApi.BlockInfo?>(null)
    val lookupBlockStatus: StateFlow<WikiUserApi.BlockInfo?> = _lookupBlockStatus.asStateFlow()

    private val _lookupLastEdit = MutableStateFlow<String?>(null)
    val lookupLastEdit: StateFlow<String?> = _lookupLastEdit.asStateFlow()

    private val _isLoadingLookup = MutableStateFlow(false)
    val isLoadingLookup: StateFlow<Boolean> = _isLoadingLookup.asStateFlow()

    private val _lookupError = MutableStateFlow("")
    val lookupError: StateFlow<String> = _lookupError.asStateFlow()

    // 查询用户详情子 Tab
    private val _lookupDetailTab = MutableStateFlow(LookupDetailTab.SUMMARY)
    val lookupDetailTab: StateFlow<LookupDetailTab> = _lookupDetailTab.asStateFlow()


    // 查询用户 - 文件
    private val _lookupFiles = MutableStateFlow<List<WikiUserApi.UserFile>>(emptyList())
    val lookupFiles: StateFlow<List<WikiUserApi.UserFile>> = _lookupFiles.asStateFlow()
    private val _isLoadingLookupFiles = MutableStateFlow(false)
    val isLoadingLookupFiles: StateFlow<Boolean> = _isLoadingLookupFiles.asStateFlow()

    // 查询用户 - 操作日志
    private val _lookupLogEvents = MutableStateFlow<List<WikiUserApi.LogEvent>>(emptyList())
    val lookupLogEvents: StateFlow<List<WikiUserApi.LogEvent>> = _lookupLogEvents.asStateFlow()
    private val _isLoadingLookupLog = MutableStateFlow(false)
    val isLoadingLookupLog: StateFlow<Boolean> = _isLoadingLookupLog.asStateFlow()

    // ──────────────────────────────────────────────────────────────

    fun onCookieInputChange(value: String) { _cookieInput.value = value }


    fun onLogTypeFilterChange(type: String?) { _logTypeFilter.value = type }

    fun onLogSortOrderChange(order: LogSortOrder) { _logSortOrder.value = order }

    fun onLookupQueryChange(q: String) { _lookupQuery.value = q }

    fun onLookupDetailTabChange(tab: LookupDetailTab) {
        _lookupDetailTab.value = tab
        val name = _lookupResult.value?.name ?: return
        when (tab) {
            LookupDetailTab.FILES -> if (_lookupFiles.value.isEmpty()) fetchLookupFiles(name)
            LookupDetailTab.LOG -> if (_lookupLogEvents.value.isEmpty()) fetchLookupLog(name)
            else -> Unit
        }
    }

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
        _blockStatus.value = null
        _lastEditTimestamp.value = null
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
                if (info != null && info.isLoggedIn) {
                    // 并行加载封禁状态和最后编辑时间
                    launch {
                        _blockStatus.value = WikiUserApi.fetchBlockStatus(info.name)
                    }
                    launch {
                        _lastEditTimestamp.value = WikiUserApi.fetchLastEditTimestamp(info.name)
                    }
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

    fun lookupUser() {
        val q = _lookupQuery.value.trim().trimStart('#')
        if (q.isBlank()) return
        if (!q.all { it.isDigit() }) {
            _lookupError.value = "⚠️ 请输入数字用户 ID"
            return
        }
        _lookupResult.value = null
        _lookupBlockStatus.value = null
        _lookupLastEdit.value = null
        _lookupError.value = ""
        _lookupDetailTab.value = LookupDetailTab.SUMMARY
        _lookupFiles.value = emptyList()
        _lookupLogEvents.value = emptyList()
        scope.launch {
            _isLoadingLookup.value = true
            try {
                val result = WikiUserApi.fetchPublicUserInfo(q)
                _lookupResult.value = result
                if (result == null) {
                    _lookupError.value = "❌ 请求失败，请检查网络连接"
                } else if (!result.exists) {
                    _lookupError.value = "⚠️ 用户 ID「$q」不存在"
                } else {
                    launch { _lookupBlockStatus.value = WikiUserApi.fetchBlockStatus(result.name) }
                    launch { _lookupLastEdit.value = WikiUserApi.fetchLastEditTimestamp(result.name) }
                }
            } finally {
                _isLoadingLookup.value = false
            }
        }
    }

    fun fetchLookupFiles(name: String) {
        scope.launch {
            _isLoadingLookupFiles.value = true
            try { _lookupFiles.value = WikiUserApi.fetchUserFiles(name) }
            finally { _isLoadingLookupFiles.value = false }
        }
    }

    fun fetchLookupLog(name: String) {
        scope.launch {
            _isLoadingLookupLog.value = true
            try { _lookupLogEvents.value = WikiUserApi.fetchUserLogEvents(name) }
            finally { _isLoadingLookupLog.value = false }
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

