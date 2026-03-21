package viewmodel

import data.UserLookupMode
import data.WikiCookieManager
import data.WikiUserApi
import data.WikiUserApi.ApiResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import util.AppPrefs

private const val MAX_RECENT_LOOKUPS = 10

internal fun normalizeLookupQuery(mode: UserLookupMode, input: String): String = when (mode) {
    UserLookupMode.BID -> input.trim()
    UserLookupMode.WIKI_ID -> input.trim().trimStart('#').trim()
}

internal fun appendRecentLookup(
    mode: UserLookupMode,
    existing: List<String>,
    newValue: String,
    maxSize: Int = MAX_RECENT_LOOKUPS
): List<String> {
    val normalized = normalizeLookupQuery(mode, newValue)
    val isValid = when (mode) {
        UserLookupMode.BID -> normalized.isNotBlank()
        UserLookupMode.WIKI_ID -> normalized.isNotBlank() && normalized.all { it.isDigit() }
    }
    if (!isValid) return existing
    return buildList {
        add(normalized)
        addAll(existing.filterNot { it == normalized }.take(maxSize - 1))
    }
}

enum class UserInfoTab { INFO, CONTRIBUTIONS, WATCHLIST, LOG }

enum class LogSortOrder { NEWEST_FIRST, OLDEST_FIRST }

enum class LookupDetailTab { SUMMARY, FILES, LOG }

sealed interface RequestState {
    data object Idle : RequestState
    data object Loading : RequestState
    data object Success : RequestState
    data class Error(val message: String) : RequestState
}

class UserInfoViewModel(private val scope: CoroutineScope) {

    // ─── Cookie 输入 ───────────────────────────────────────────────
    private val _cookieInput = MutableStateFlow(WikiCookieManager.currentCookieString)
    val cookieInput: StateFlow<String> = _cookieInput.asStateFlow()
    val cookiePreview: StateFlow<WikiCookieManager.CookieImportPreview> = _cookieInput
        .map { WikiCookieManager.previewCookieImport(it) }
        .stateIn(scope, SharingStarted.Eagerly, WikiCookieManager.previewCookieImport(_cookieInput.value))

    // ─── 用户信息 ──────────────────────────────────────────────────
    // 使用 WikiUserApi.currentUser 作为数据源
    val userInfo: StateFlow<WikiUserApi.UserInfo?> = WikiUserApi.currentUser

    private val _isLoadingInfo = MutableStateFlow(false)
    val isLoadingInfo: StateFlow<Boolean> = _isLoadingInfo.asStateFlow()

    // ─── 封禁状态 ──────────────────────────────────────────────────
    private val _blockStatus = MutableStateFlow<WikiUserApi.BlockInfo?>(null)
    val blockStatus: StateFlow<WikiUserApi.BlockInfo?> = _blockStatus.asStateFlow()

    // ─── 最后编辑时间 ─────────────────────────────────────────────
    private val _lastEditTimestamp = MutableStateFlow<String?>(null)
    val lastEditTimestamp: StateFlow<String?> = _lastEditTimestamp.asStateFlow()
    private val _userSummaryState = MutableStateFlow<RequestState>(RequestState.Idle)
    val userSummaryState: StateFlow<RequestState> = _userSummaryState.asStateFlow()

    // ─── 贡献 ──────────────────────────────────────────────────────
    private val _contributions = MutableStateFlow<List<WikiUserApi.UserContrib>>(emptyList())
    val contributions: StateFlow<List<WikiUserApi.UserContrib>> = _contributions.asStateFlow()
    private val _contributionsRequestState = MutableStateFlow<RequestState>(RequestState.Idle)
    val contributionsRequestState: StateFlow<RequestState> = _contributionsRequestState.asStateFlow()

    // ─── 监视列表 ──────────────────────────────────────────────────
    private val _watchlist = MutableStateFlow<List<WikiUserApi.WatchlistItem>>(emptyList())
    val watchlist: StateFlow<List<WikiUserApi.WatchlistItem>> = _watchlist.asStateFlow()
    private val _watchlistRequestState = MutableStateFlow<RequestState>(RequestState.Idle)
    val watchlistRequestState: StateFlow<RequestState> = _watchlistRequestState.asStateFlow()

    // ─── 操作日志 ──────────────────────────────────────────────────
    private val _logEvents = MutableStateFlow<List<WikiUserApi.LogEvent>>(emptyList())
    private val _logRequestState = MutableStateFlow<RequestState>(RequestState.Idle)
    val logRequestState: StateFlow<RequestState> = _logRequestState.asStateFlow()

    // 日志筛选 & 排序
    private val _logTypeFilter = MutableStateFlow<String?>(null)
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

    // ─── 公开用户查询 ──────────────────────────────────────────────
    private val _lookupQuery = MutableStateFlow("")
    val lookupQuery: StateFlow<String> = _lookupQuery.asStateFlow()
    private val _lookupMode = MutableStateFlow(UserLookupMode.BID)
    val lookupMode: StateFlow<UserLookupMode> = _lookupMode.asStateFlow()
    private val _recentBidLookups = MutableStateFlow(AppPrefs.recentBidLookupValues)
    private val _recentWikiIdLookups = MutableStateFlow(AppPrefs.recentWikiIdLookupValues)
    val recentLookupIds: StateFlow<List<String>> = combine(_lookupMode, _recentBidLookups, _recentWikiIdLookups) { mode, bid, wikiId ->
        when (mode) {
            UserLookupMode.BID -> bid
            UserLookupMode.WIKI_ID -> wikiId
        }
    }.stateIn(scope, SharingStarted.Eagerly, _recentWikiIdLookups.value)

    private val _lookupResult = MutableStateFlow<WikiUserApi.PublicUserInfo?>(null)
    val lookupResult: StateFlow<WikiUserApi.PublicUserInfo?> = _lookupResult.asStateFlow()

    private val _lookupBlockStatus = MutableStateFlow<WikiUserApi.BlockInfo?>(null)
    val lookupBlockStatus: StateFlow<WikiUserApi.BlockInfo?> = _lookupBlockStatus.asStateFlow()

    private val _lookupLastEdit = MutableStateFlow<String?>(null)
    val lookupLastEdit: StateFlow<String?> = _lookupLastEdit.asStateFlow()
    private val _lookupSummaryState = MutableStateFlow<RequestState>(RequestState.Idle)
    val lookupSummaryState: StateFlow<RequestState> = _lookupSummaryState.asStateFlow()

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
    private val _lookupFilesRequestState = MutableStateFlow<RequestState>(RequestState.Idle)
    val lookupFilesRequestState: StateFlow<RequestState> = _lookupFilesRequestState.asStateFlow()

    // 查询用户 - 操作日志
    private val _lookupLogEvents = MutableStateFlow<List<WikiUserApi.LogEvent>>(emptyList())
    val lookupLogEvents: StateFlow<List<WikiUserApi.LogEvent>> = _lookupLogEvents.asStateFlow()
    private val _lookupLogRequestState = MutableStateFlow<RequestState>(RequestState.Idle)
    val lookupLogRequestState: StateFlow<RequestState> = _lookupLogRequestState.asStateFlow()

    private var fetchUserInfoJob: Job? = null
    private var contributionsJob: Job? = null
    private var watchlistJob: Job? = null
    private var logEventsJob: Job? = null
    private var lookupJob: Job? = null
    private var lookupFilesJob: Job? = null
    private var lookupLogJob: Job? = null
    private var currentUserRequestToken = 0L
    private var lookupRequestToken = 0L

    init {
        // 如果已有 Cookie 且未登录，尝试自动登录
        if (WikiCookieManager.hasCookies && WikiUserApi.currentUser.value == null) {
            importAndFetch()
        }
    }

    fun onCookieInputChange(value: String) { _cookieInput.value = value }

    fun onLogTypeFilterChange(type: String?) { _logTypeFilter.value = type }

    fun onLogSortOrderChange(order: LogSortOrder) { _logSortOrder.value = order }

    fun onLookupQueryChange(q: String) { _lookupQuery.value = q }

    fun onLookupModeChange(mode: UserLookupMode) {
        if (_lookupMode.value == mode) return
        lookupRequestToken++
        lookupJob?.cancel()
        lookupFilesJob?.cancel()
        lookupLogJob?.cancel()
        _lookupMode.value = mode
        _lookupQuery.value = ""
        resetLookupState(clearQuery = false)
    }

    fun useRecentLookup(id: String) {
        _lookupQuery.value = id
        lookupUser()
    }

    fun removeRecentLookup(id: String) {
        when (_lookupMode.value) {
            UserLookupMode.BID -> {
                val next = _recentBidLookups.value.filterNot { it == id }
                _recentBidLookups.value = next
                AppPrefs.recentBidLookupValues = next
            }
            UserLookupMode.WIKI_ID -> {
                val next = _recentWikiIdLookups.value.filterNot { it == id }
                _recentWikiIdLookups.value = next
                AppPrefs.recentWikiIdLookupValues = next
            }
        }
    }

    fun clearRecentLookups() {
        when (_lookupMode.value) {
            UserLookupMode.BID -> {
                _recentBidLookups.value = emptyList()
                AppPrefs.recentBidLookupValues = emptyList()
            }
            UserLookupMode.WIKI_ID -> {
                _recentWikiIdLookups.value = emptyList()
                AppPrefs.recentWikiIdLookupValues = emptyList()
            }
        }
    }

    fun onLookupDetailTabChange(tab: LookupDetailTab) {
        _lookupDetailTab.value = tab
        val name = _lookupResult.value?.name ?: return
        when (tab) {
            LookupDetailTab.FILES -> if (_lookupFilesRequestState.value == RequestState.Idle) fetchLookupFiles(name)
            LookupDetailTab.LOG -> if (_lookupLogRequestState.value == RequestState.Idle) fetchLookupLog(name)
            else -> Unit
        }
    }

    /**
     * 导入 Cookie 并立即查询用户信息。
     */
    fun importAndFetch() {
        if (_cookieInput.value.isBlank() && !WikiCookieManager.hasCookies) return

        fetchUserInfoJob?.cancel()
        fetchUserInfoJob = scope.launch {
            _isLoadingInfo.value = true
            _statusMessage.value = ""
            // 重置本地可能缓存的状态，不直接操作 WikiUserApi，因为那是全局的
            // 不过如果是重新导入，通常意味着之前的无效化，可以在这里清理一下
            WikiUserApi.clearCurrentUser()

            // 只有当输入框有内容时才重新导入，否则只使用内存中已有的
            if (_cookieInput.value.isNotBlank()) {
                val count = WikiCookieManager.importCookies(_cookieInput.value)
                if (count == 0) {
                    _statusMessage.value = "⚠️ 未能识别到有效的 Cookie"
                    _isLoadingInfo.value = false
                    return@launch
                }
            } else if (!WikiCookieManager.hasCookies) {
                 _statusMessage.value = "⚠️ 此时无 Cookie"
                 _isLoadingInfo.value = false
                 return@launch
            }

            currentUserRequestToken++
            val token = currentUserRequestToken
            when (val userResult = WikiUserApi.fetchCurrentUserInfoResult()) {
                is ApiResult.Success -> {
                    if (token == currentUserRequestToken) {
                        WikiUserApi.updateCurrentUser(userResult.value)
                        val info = userResult.value
                        if (info != null && info.isLoggedIn) {
                            _statusMessage.value = "✅ 已登录：${info.name}"
                        } else {
                            _statusMessage.value = "⚠️ Cookie 无效，未登录"
                        }
                    }
                }
                is ApiResult.Error -> {
                    if (token == currentUserRequestToken) {
                        _statusMessage.value = "❌ ${userResult.message}"
                    }
                }
            }
            _isLoadingInfo.value = false
        }
    }

    /**
     * 清除 Cookie 并重置所有状态。
     */
    fun clearCookies() {
        currentUserRequestToken++
        lookupRequestToken++
        cancelAllJobs()
        WikiCookieManager.clearCookies()
        _cookieInput.value = ""
        resetAuthenticatedState()
        resetLookupState()
        _currentTab.value = UserInfoTab.INFO
        _logTypeFilter.value = null
        _logSortOrder.value = LogSortOrder.NEWEST_FIRST
        _statusMessage.value = "Cookie 已清除"
    }

    fun fetchUserInfo() {
        fetchUserInfoJob?.cancel()
        val requestToken = ++currentUserRequestToken
        fetchUserInfoJob = scope.launch {
            _isLoadingInfo.value = true
            _blockStatus.value = null
            _lastEditTimestamp.value = null
            _userSummaryState.value = RequestState.Idle
            try {
                when (val result = WikiUserApi.fetchCurrentUserInfoResult()) {
                    is ApiResult.Success -> {
                        if (requestToken != currentUserRequestToken) return@launch
                        val info = result.value
                        WikiUserApi.updateCurrentUser(info)
                        _statusMessage.value = when {
                            info == null -> "❌ 无法读取当前用户信息"
                            info.isLoggedIn -> "✅ 已登录：${info.name}"
                            else -> "⚠️ Cookie 无效或已过期，当前为未登录状态"
                        }
                        if (info != null && info.isLoggedIn) {
                            _userSummaryState.value = RequestState.Loading
                            val blockDeferred = async { WikiUserApi.fetchBlockStatusResult(info.name) }
                            val lastEditDeferred = async { WikiUserApi.fetchLastEditTimestampResult(info.name) }
                            val blockResult = blockDeferred.await()
                            val lastEditResult = lastEditDeferred.await()
                            if (requestToken != currentUserRequestToken) return@launch
                            val errors = mutableListOf<String>()
                            when (blockResult) {
                                is ApiResult.Success -> _blockStatus.value = blockResult.value
                                is ApiResult.Error -> errors += blockResult.message
                            }
                            when (lastEditResult) {
                                is ApiResult.Success -> _lastEditTimestamp.value = lastEditResult.value.orEmpty()
                                is ApiResult.Error -> errors += lastEditResult.message
                            }
                            _userSummaryState.value = if (errors.isEmpty()) {
                                RequestState.Success
                            } else {
                                RequestState.Error(errors.joinToString("；"))
                            }
                        } else {
                            resetAuthenticatedCollections()
                        }
                    }
                    is ApiResult.Error -> {
                        if (requestToken != currentUserRequestToken) return@launch
                        resetAuthenticatedState(resetCookieInput = false, clearStatus = false)
                        _statusMessage.value = "❌ ${result.message}"
                    }
                }
            } finally {
                if (requestToken == currentUserRequestToken) {
                    _isLoadingInfo.value = false
                }
            }
        }
    }

    fun fetchContributions() {
        val name = currentAuthenticatedUserName() ?: return
        val requestToken = currentUserRequestToken
        contributionsJob?.cancel()
        contributionsJob = scope.launch {
            _contributionsRequestState.value = RequestState.Loading
            when (val result = WikiUserApi.fetchContributionsResult(name)) {
                is ApiResult.Success -> {
                    if (requestToken != currentUserRequestToken || name != currentAuthenticatedUserName()) return@launch
                    _contributions.value = result.value
                    _contributionsRequestState.value = RequestState.Success
                }
                is ApiResult.Error -> {
                    if (requestToken != currentUserRequestToken || name != currentAuthenticatedUserName()) return@launch
                    _contributions.value = emptyList()
                    _contributionsRequestState.value = RequestState.Error(result.message)
                }
            }
        }
    }

    fun fetchWatchlist() {
        val requestToken = currentUserRequestToken
        watchlistJob?.cancel()
        watchlistJob = scope.launch {
            _watchlistRequestState.value = RequestState.Loading
            when (val result = WikiUserApi.fetchWatchlistResult()) {
                is ApiResult.Success -> {
                    if (requestToken != currentUserRequestToken) return@launch
                    _watchlist.value = result.value
                    _watchlistRequestState.value = RequestState.Success
                }
                is ApiResult.Error -> {
                    if (requestToken != currentUserRequestToken) return@launch
                    _watchlist.value = emptyList()
                    _watchlistRequestState.value = RequestState.Error(result.message)
                }
            }
        }
    }

    fun fetchLogEvents() {
        val name = currentAuthenticatedUserName() ?: return
        val requestToken = currentUserRequestToken
        logEventsJob?.cancel()
        logEventsJob = scope.launch {
            _logRequestState.value = RequestState.Loading
            when (val result = WikiUserApi.fetchUserLogEventsResult(name)) {
                is ApiResult.Success -> {
                    if (requestToken != currentUserRequestToken || name != currentAuthenticatedUserName()) return@launch
                    _logEvents.value = result.value
                    _logRequestState.value = RequestState.Success
                }
                is ApiResult.Error -> {
                    if (requestToken != currentUserRequestToken || name != currentAuthenticatedUserName()) return@launch
                    _logEvents.value = emptyList()
                    _logRequestState.value = RequestState.Error(result.message)
                }
            }
        }
    }

    fun clearCurrentLookup() {
        lookupRequestToken++
        lookupJob?.cancel()
        lookupFilesJob?.cancel()
        lookupLogJob?.cancel()
        resetLookupState(clearQuery = true)
    }

    fun lookupUser() {
        val mode = _lookupMode.value
        val q = normalizeLookupQuery(mode, _lookupQuery.value)
        if (q.isBlank()) {
            lookupRequestToken++
            lookupFilesJob?.cancel()
            lookupLogJob?.cancel()
            resetLookupState(clearQuery = false)
            _lookupError.value = when (mode) {
                UserLookupMode.BID -> "⚠️ 请输入 BID"
                UserLookupMode.WIKI_ID -> "⚠️ 请输入 WikiID"
            }
            return
        }
        if (mode == UserLookupMode.WIKI_ID && !q.all { it.isDigit() }) {
            lookupRequestToken++
            lookupFilesJob?.cancel()
            lookupLogJob?.cancel()
            resetLookupState(clearQuery = false)
            _lookupError.value = "⚠️ WikiID 必须为纯数字"
            return
        }

        lookupJob?.cancel()
        lookupFilesJob?.cancel()
        lookupLogJob?.cancel()
        val requestToken = ++lookupRequestToken
        resetLookupState(clearQuery = false)
        _lookupQuery.value = q

        lookupJob = scope.launch {
            _isLoadingLookup.value = true
            try {
                when (val result = WikiUserApi.fetchPublicUserInfoResult(q, mode)) {
                    is ApiResult.Success -> {
                        if (requestToken != lookupRequestToken) return@launch
                        val publicUser = result.value
                        _lookupResult.value = publicUser
                        if (publicUser == null) {
                            _lookupError.value = "❌ 未返回用户信息"
                        } else if (!publicUser.exists) {
                            _lookupError.value = when (mode) {
                                UserLookupMode.BID -> "⚠️ BID「$q」不存在"
                                UserLookupMode.WIKI_ID -> "⚠️ WikiID「$q」不存在"
                            }
                        } else {
                            rememberRecentLookup(q)
                            _lookupSummaryState.value = RequestState.Loading
                            val blockDeferred = async { WikiUserApi.fetchBlockStatusResult(publicUser.name) }
                            val lastEditDeferred = async { WikiUserApi.fetchLastEditTimestampResult(publicUser.name) }
                            val blockResult = blockDeferred.await()
                            val lastEditResult = lastEditDeferred.await()
                            if (requestToken != lookupRequestToken) return@launch
                            val errors = mutableListOf<String>()
                            when (blockResult) {
                                is ApiResult.Success -> _lookupBlockStatus.value = blockResult.value
                                is ApiResult.Error -> errors += blockResult.message
                            }
                            when (lastEditResult) {
                                is ApiResult.Success -> _lookupLastEdit.value = lastEditResult.value.orEmpty()
                                is ApiResult.Error -> errors += lastEditResult.message
                            }
                            _lookupSummaryState.value = if (errors.isEmpty()) {
                                RequestState.Success
                            } else {
                                RequestState.Error(errors.joinToString("；"))
                            }
                        }
                    }
                    is ApiResult.Error -> {
                        if (requestToken != lookupRequestToken) return@launch
                        _lookupError.value = "❌ ${result.message}"
                    }
                }
            } finally {
                if (requestToken == lookupRequestToken) {
                    _isLoadingLookup.value = false
                }
            }
        }
    }

    fun fetchLookupFiles(name: String) {
        val requestToken = lookupRequestToken
        lookupFilesJob?.cancel()
        lookupFilesJob = scope.launch {
            _lookupFilesRequestState.value = RequestState.Loading
            when (val result = WikiUserApi.fetchUserFilesResult(name)) {
                is ApiResult.Success -> {
                    if (requestToken != lookupRequestToken || _lookupResult.value?.name != name) return@launch
                    _lookupFiles.value = result.value
                    _lookupFilesRequestState.value = RequestState.Success
                }
                is ApiResult.Error -> {
                    if (requestToken != lookupRequestToken || _lookupResult.value?.name != name) return@launch
                    _lookupFiles.value = emptyList()
                    _lookupFilesRequestState.value = RequestState.Error(result.message)
                }
            }
        }
    }

    fun fetchLookupLog(name: String) {
        val requestToken = lookupRequestToken
        lookupLogJob?.cancel()
        lookupLogJob = scope.launch {
            _lookupLogRequestState.value = RequestState.Loading
            when (val result = WikiUserApi.fetchUserLogEventsResult(name)) {
                is ApiResult.Success -> {
                    if (requestToken != lookupRequestToken || _lookupResult.value?.name != name) return@launch
                    _lookupLogEvents.value = result.value
                    _lookupLogRequestState.value = RequestState.Success
                }
                is ApiResult.Error -> {
                    if (requestToken != lookupRequestToken || _lookupResult.value?.name != name) return@launch
                    _lookupLogEvents.value = emptyList()
                    _lookupLogRequestState.value = RequestState.Error(result.message)
                }
            }
        }
    }

    /**
     * 切换 Tab 时按需加载数据。
     */
    fun onTabSelected(tab: UserInfoTab) {
        _currentTab.value = tab
        val info = userInfo.value ?: return
        if (!info.isLoggedIn) return
        when (tab) {
            UserInfoTab.CONTRIBUTIONS -> if (_contributionsRequestState.value == RequestState.Idle) fetchContributions()
            UserInfoTab.WATCHLIST -> if (_watchlistRequestState.value == RequestState.Idle) fetchWatchlist()
            UserInfoTab.LOG -> if (_logRequestState.value == RequestState.Idle) fetchLogEvents()
            else -> Unit
        }
    }

    private fun currentAuthenticatedUserName(): String? =
        userInfo.value?.takeIf { it.isLoggedIn }?.name ?: WikiCookieManager.extractUserNameFromCookies()

    private fun rememberRecentLookup(id: String) {
        when (_lookupMode.value) {
            UserLookupMode.BID -> {
                val next = appendRecentLookup(UserLookupMode.BID, _recentBidLookups.value, id)
                _recentBidLookups.value = next
                AppPrefs.recentBidLookupValues = next
            }
            UserLookupMode.WIKI_ID -> {
                val next = appendRecentLookup(UserLookupMode.WIKI_ID, _recentWikiIdLookups.value, id)
                _recentWikiIdLookups.value = next
                AppPrefs.recentWikiIdLookupValues = next
            }
        }
    }

    private fun cancelAuthenticatedJobs() {
        listOfNotNull(fetchUserInfoJob, contributionsJob, watchlistJob, logEventsJob).forEach { it.cancel() }
    }

    private fun cancelAllJobs() {
        listOfNotNull(
            fetchUserInfoJob,
            contributionsJob,
            watchlistJob,
            logEventsJob,
            lookupJob,
            lookupFilesJob,
            lookupLogJob
        ).forEach { it.cancel() }
    }

    private fun resetAuthenticatedCollections() {
        _contributions.value = emptyList()
        _watchlist.value = emptyList()
        _logEvents.value = emptyList()
        _contributionsRequestState.value = RequestState.Idle
        _watchlistRequestState.value = RequestState.Idle
        _logRequestState.value = RequestState.Idle
    }

    private fun resetAuthenticatedState(resetCookieInput: Boolean = false, clearStatus: Boolean = false) {
        if (resetCookieInput) _cookieInput.value = ""
        WikiUserApi.clearCurrentUser()
        _blockStatus.value = null
        _lastEditTimestamp.value = null
        _userSummaryState.value = RequestState.Idle
        resetAuthenticatedCollections()
        if (clearStatus) _statusMessage.value = ""
    }

    private fun resetLookupState(clearQuery: Boolean = false) {
        if (clearQuery) _lookupQuery.value = ""
        _lookupResult.value = null
        _lookupBlockStatus.value = null
        _lookupLastEdit.value = null
        _lookupSummaryState.value = RequestState.Idle
        _lookupError.value = ""
        _lookupDetailTab.value = LookupDetailTab.SUMMARY
        _lookupFiles.value = emptyList()
        _lookupLogEvents.value = emptyList()
        _lookupFilesRequestState.value = RequestState.Idle
        _lookupLogRequestState.value = RequestState.Idle
        _isLoadingLookup.value = false
    }
}
