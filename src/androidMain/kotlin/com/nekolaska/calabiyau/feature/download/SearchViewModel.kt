package com.nekolaska.calabiyau.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.CharacterGroup
import data.PortraitRepository
import data.SearchMode
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * 搜索 & 分类树相关状态。
 * 管理关键词搜索、角色分组、分类文件选择对话框等。
 */
class SearchViewModel : ViewModel() {

    /** 一次性错误事件（Snackbar 消费后即消失） */
    private val _errorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    /** 最近一次搜索是否因网络问题失败 */
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _searchKeyword = MutableStateFlow("角色")
    val searchKeyword: StateFlow<String> = _searchKeyword.asStateFlow()

    private val _searchMode = MutableStateFlow(SearchMode.VOICE_ONLY)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private val _characterGroups = MutableStateFlow<List<CharacterGroup>>(emptyList())
    val characterGroups: StateFlow<List<CharacterGroup>> = _characterGroups.asStateFlow()

    private val _characterAvatars = MutableStateFlow<Map<String, String>>(emptyMap())
    val characterAvatars: StateFlow<Map<String, String>> = _characterAvatars.asStateFlow()

    // 选中的角色组
    private val _selectedGroup = MutableStateFlow<CharacterGroup?>(null)
    val selectedGroup: StateFlow<CharacterGroup?> = _selectedGroup.asStateFlow()

    private val _subCategories = MutableStateFlow<List<String>>(emptyList())
    val subCategories: StateFlow<List<String>> = _subCategories.asStateFlow()

    private val _checkedCategories = MutableStateFlow<List<String>>(emptyList())
    val checkedCategories: StateFlow<List<String>> = _checkedCategories.asStateFlow()

    private val _isScanningTree = MutableStateFlow(false)
    val isScanningTree: StateFlow<Boolean> = _isScanningTree.asStateFlow()

    // 文件搜索结果
    private val _fileSearchResults = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val fileSearchResults: StateFlow<List<Pair<String, String>>> = _fileSearchResults.asStateFlow()

    private val _fileSearchSelectedUrls = MutableStateFlow<Set<String>>(emptySet())
    val fileSearchSelectedUrls: StateFlow<Set<String>> = _fileSearchSelectedUrls.asStateFlow()

    // 分类文件选择对话框
    private val _showFileDialog = MutableStateFlow(false)
    val showFileDialog: StateFlow<Boolean> = _showFileDialog.asStateFlow()

    private val _dialogCategoryName = MutableStateFlow("")
    val dialogCategoryName: StateFlow<String> = _dialogCategoryName.asStateFlow()

    private val _dialogFileList = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val dialogFileList: StateFlow<List<Pair<String, String>>> = _dialogFileList.asStateFlow()

    private val _dialogIsLoading = MutableStateFlow(false)
    val dialogIsLoading: StateFlow<Boolean> = _dialogIsLoading.asStateFlow()

    private val _dialogSelectedUrls = MutableStateFlow<Set<String>>(emptySet())
    val dialogSelectedUrls: StateFlow<Set<String>> = _dialogSelectedUrls.asStateFlow()

    // 手动选择的文件映射 (分类名 -> 选中的文件列表)
    private val _manualSelectionMap = MutableStateFlow<Map<String, List<Pair<String, String>>>>(emptyMap())
    val manualSelectionMap: StateFlow<Map<String, List<Pair<String, String>>>> = _manualSelectionMap.asStateFlow()

    // 立绘角色列表（搜索结果）
    private val _portraitCharacters = MutableStateFlow<List<String>>(emptyList())
    val portraitCharacters: StateFlow<List<String>> = _portraitCharacters.asStateFlow()

    // 每个模式缓存的搜索关键词
    private val cachedKeywords = mutableMapOf<SearchMode, String>(
        SearchMode.VOICE_ONLY to "角色",
        SearchMode.ALL_CATEGORIES to "角色",
        SearchMode.PORTRAIT to "角色",
        SearchMode.FILE_SEARCH to ""
    )
    private val hasResultsCache = mutableSetOf<SearchMode>()
    private val cachedCharacterGroups = mutableMapOf<SearchMode, List<CharacterGroup>>()
    private val cachedCharacterAvatars = mutableMapOf<SearchMode, Map<String, String>>()
    private val cachedSelectedGroup = mutableMapOf<SearchMode, CharacterGroup?>()
    private val cachedSubCategories = mutableMapOf<SearchMode, List<String>>()
    private val cachedCheckedCategories = mutableMapOf<SearchMode, List<String>>()

    /** 供 DownloadViewModel 追加日志 */
    var onLog: ((String) -> Unit)? = null

    init {
        performSearch()
        observeKeywordForAutoSearch()
    }

    /**
     * 关键词变化后自动延迟搜索（FILE_SEARCH 模式除外——遍历分类树开销大，仍需手动触发）。
     *
     * 跳过 mode-switch 回显：onSearchModeChange 会把模式缓存的关键词同步到 _searchKeyword，
     * 若此时的值与当前模式 `cachedKeywords` 一致，则说明该发射是由模式切换触发的回显
     * （已由 performSearch() 同步处理或走缓存恢复），不应再触发一次网络请求。
     */
    @OptIn(FlowPreview::class)
    private fun observeKeywordForAutoSearch() {
        viewModelScope.launch {
            _searchKeyword
                .drop(1)  // 跳过初始 "角色" 值（已由 init 中的 performSearch() 处理）
                .distinctUntilChanged()
                .debounce(300)
                .collect { kw ->
                    if (_searchMode.value == SearchMode.FILE_SEARCH) return@collect
                    if (kw == cachedKeywords[_searchMode.value]) return@collect
                    performSearch()
                }
        }
    }

    fun onSearchKeywordChange(value: String) { _searchKeyword.value = value }

    fun onSearchModeChange(mode: SearchMode) {
        val prev = _searchMode.value
        cachedKeywords[prev] = _searchKeyword.value

        if (prev == SearchMode.VOICE_ONLY || prev == SearchMode.ALL_CATEGORIES) {
            cachedCharacterGroups[prev] = _characterGroups.value
            cachedCharacterAvatars[prev] = _characterAvatars.value
            cachedSelectedGroup[prev] = _selectedGroup.value
            cachedSubCategories[prev] = _subCategories.value
            cachedCheckedCategories[prev] = _checkedCategories.value
        }

        _searchMode.value = mode

        if (mode == SearchMode.FILE_SEARCH) {
            _searchKeyword.value = cachedKeywords[mode] ?: ""
            if (mode !in hasResultsCache) {
                _fileSearchResults.value = emptyList()
                _fileSearchSelectedUrls.value = emptySet()
                _hasSearched.value = false
            }
        } else if (mode == SearchMode.VOICE_ONLY || mode == SearchMode.ALL_CATEGORIES) {
            _searchKeyword.value = cachedKeywords[mode] ?: "角色"
            if (mode in hasResultsCache) {
                _characterGroups.value = cachedCharacterGroups[mode] ?: emptyList()
                _characterAvatars.value = cachedCharacterAvatars[mode] ?: emptyMap()
                _selectedGroup.value = cachedSelectedGroup[mode]
                _subCategories.value = cachedSubCategories[mode] ?: emptyList()
                _checkedCategories.value = cachedCheckedCategories[mode] ?: emptyList()
                _hasSearched.value = true
                return
            }
            performSearch()
        } else {
            _searchKeyword.value = cachedKeywords[mode] ?: "角色"
            if (mode in hasResultsCache) {
                _hasSearched.value = true
                return
            }
            performSearch()
        }
    }

    fun performSearch() {
        if (_isSearching.value) return
        val keyword = _searchKeyword.value.trim()
        if (keyword.isBlank()) return

        AppPrefs.addSearchHistory(keyword)
        hasResultsCache.remove(_searchMode.value)

        viewModelScope.launch {
            _isSearching.value = true
            _hasSearched.value = false

            when (_searchMode.value) {
                SearchMode.FILE_SEARCH -> {
                    _fileSearchResults.value = emptyList()
                    _fileSearchSelectedUrls.value = emptySet()
                }
                else -> {
                    _selectedGroup.value = null
                    _subCategories.value = emptyList()
                    _checkedCategories.value = emptyList()
                }
            }

            _searchError.value = null

            try {
                when (_searchMode.value) {
                    SearchMode.VOICE_ONLY -> {
                        val groups = WikiEngine.searchAndGroupCharacters(keyword, voiceOnly = true)
                        _characterGroups.value = groups
                        _characterAvatars.value = WikiEngine.fetchCharacterAvatars(groups.map { it.characterName })
                    }
                    SearchMode.ALL_CATEGORIES -> {
                        val groups = WikiEngine.searchAndGroupCharacters(keyword, voiceOnly = false)
                        _characterGroups.value = groups
                        _characterAvatars.value = WikiEngine.fetchCharacterAvatars(groups.map { it.characterName })
                    }
                    SearchMode.PORTRAIT -> {
                        onLog?.invoke("搜索立绘角色: $keyword")
                        val characters = PortraitRepository.searchCharacters(keyword)
                        _portraitCharacters.value = characters
                        _characterAvatars.value = WikiEngine.fetchCharacterAvatars(characters)
                        onLog?.invoke("找到 ${characters.size} 个角色")
                    }
                    SearchMode.FILE_SEARCH -> {
                        onLog?.invoke("开始搜索文件: $keyword")
                        val results = WikiEngine.searchFiles(
                            keyword = keyword,
                            audioOnly = false,
                            onLog = { onLog?.invoke(it) }
                        )
                        _fileSearchResults.value = results
                        onLog?.invoke("搜索完成，共找到 ${results.size} 个文件")
                    }
                }
            } catch (e: Exception) {
                val msg = "搜索失败: ${e.message}"
                _searchError.value = msg
                _errorEvent.tryEmit(msg)
                onLog?.invoke("[错误] 搜索失败: ${e.message}")
            } finally {
                _isSearching.value = false
                _hasSearched.value = true
                hasResultsCache.add(_searchMode.value)
                cachedKeywords[_searchMode.value] = keyword
            }
        }
    }

    fun onSelectGroup(group: CharacterGroup) {
        _selectedGroup.value = group
        _isScanningTree.value = true
        viewModelScope.launch {
            try {
                val cats = WikiEngine.scanCategoryTree(group.rootCategory)
                _subCategories.value = cats
                _checkedCategories.value = cats.toList()
            } catch (e: Exception) {
                _errorEvent.tryEmit("扫描分类树失败: ${e.message}")
                onLog?.invoke("[错误] 扫描分类树失败: ${e.message}")
            } finally {
                _isScanningTree.value = false
            }
        }
    }

    fun clearSelectedGroup() { _selectedGroup.value = null }

    fun setCategoryChecked(cat: String, checked: Boolean) {
        val current = _checkedCategories.value.toMutableList()
        if (checked) { if (cat !in current) current.add(cat) } else current.remove(cat)
        _checkedCategories.value = current
    }

    fun checkAllCategories() { _checkedCategories.value = _subCategories.value.toList() }
    fun uncheckAllCategories() { _checkedCategories.value = emptyList() }

    fun toggleFileSearchSelection(url: String) {
        val current = _fileSearchSelectedUrls.value.toMutableSet()
        if (url in current) current.remove(url) else current.add(url)
        _fileSearchSelectedUrls.value = current
    }

    fun selectAllFileSearchResults() {
        _fileSearchSelectedUrls.value = _fileSearchResults.value.map { it.second }.toSet()
    }

    // ── 分类文件选择对话框 ──

    fun openFileDialog(cat: String) {
        _dialogCategoryName.value = cat
        _dialogFileList.value = emptyList()
        _dialogSelectedUrls.value = emptySet()
        _showFileDialog.value = true
        _dialogIsLoading.value = true
        viewModelScope.launch {
            try {
                val files = WikiEngine.fetchFilesInCategory(cat, audioOnly = _searchMode.value == SearchMode.VOICE_ONLY)
                _dialogFileList.value = files
                val manual = _manualSelectionMap.value[cat]
                _dialogSelectedUrls.value = manual?.map { it.second }?.toSet()
                    ?: files.map { it.second }.toSet()
            } catch (e: Exception) {
                onLog?.invoke("加载分类文件失败: ${e.message}")
            } finally {
                _dialogIsLoading.value = false
            }
        }
    }

    fun closeFileDialog() { _showFileDialog.value = false }

    fun toggleDialogFileSelection(url: String) {
        val current = _dialogSelectedUrls.value.toMutableSet()
        if (url in current) current.remove(url) else current.add(url)
        _dialogSelectedUrls.value = current
    }

    fun selectAllDialogFiles() {
        _dialogSelectedUrls.value = _dialogFileList.value.map { it.second }.toSet()
    }

    fun clearDialogSelection() { _dialogSelectedUrls.value = emptySet() }

    fun confirmFileDialog() {
        val cat = _dialogCategoryName.value
        val selectedFiles = _dialogFileList.value.filter { it.second in _dialogSelectedUrls.value }
        _showFileDialog.value = false

        val newMap = _manualSelectionMap.value.toMutableMap()
        newMap[cat] = selectedFiles
        _manualSelectionMap.value = newMap

        val checked = _checkedCategories.value.toMutableList()
        if (selectedFiles.isNotEmpty() && cat !in checked) {
            checked.add(cat)
            _checkedCategories.value = checked
        }
    }
}
