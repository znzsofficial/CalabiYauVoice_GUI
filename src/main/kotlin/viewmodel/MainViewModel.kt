package viewmodel

import data.WikiEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class MainViewModel(
    private val scope: CoroutineScope
) {
    // =========================================================
    // 搜索状态
    // =========================================================
    private val _searchKeyword = MutableStateFlow("角色")
    val searchKeyword: StateFlow<String> = _searchKeyword.asStateFlow()

    private val _voiceOnly = MutableStateFlow(true)
    val voiceOnly: StateFlow<Boolean> = _voiceOnly.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _characterGroups = MutableStateFlow<List<WikiEngine.CharacterGroup>>(emptyList())
    val characterGroups: StateFlow<List<WikiEngine.CharacterGroup>> = _characterGroups.asStateFlow()

    // =========================================================
    // 角色选择 & 分类树状态
    // =========================================================
    private val _selectedGroup = MutableStateFlow<WikiEngine.CharacterGroup?>(null)
    val selectedGroup: StateFlow<WikiEngine.CharacterGroup?> = _selectedGroup.asStateFlow()

    private val _subCategories = MutableStateFlow<List<String>>(emptyList())
    val subCategories: StateFlow<List<String>> = _subCategories.asStateFlow()

    private val _checkedCategories = MutableStateFlow<List<String>>(emptyList())
    val checkedCategories: StateFlow<List<String>> = _checkedCategories.asStateFlow()

    private val _isScanningTree = MutableStateFlow(false)
    val isScanningTree: StateFlow<Boolean> = _isScanningTree.asStateFlow()

    // =========================================================
    // 文件弹窗状态
    // =========================================================
    private val _showFileDialog = MutableStateFlow(false)
    val showFileDialog: StateFlow<Boolean> = _showFileDialog.asStateFlow()

    private val _dialogCategoryName = MutableStateFlow("")
    val dialogCategoryName: StateFlow<String> = _dialogCategoryName.asStateFlow()

    private val _dialogFileList = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val dialogFileList: StateFlow<List<Pair<String, String>>> = _dialogFileList.asStateFlow()

    private val _dialogIsLoading = MutableStateFlow(false)
    val dialogIsLoading: StateFlow<Boolean> = _dialogIsLoading.asStateFlow()

    private val _dialogInitialSelection = MutableStateFlow<List<String>>(emptyList())
    val dialogInitialSelection: StateFlow<List<String>> = _dialogInitialSelection.asStateFlow()

    // =========================================================
    // 手动选择 & 总数缓存
    // =========================================================
    private val _manualSelectionMap = MutableStateFlow<Map<String, List<Pair<String, String>>>>(emptyMap())
    val manualSelectionMap: StateFlow<Map<String, List<Pair<String, String>>>> = _manualSelectionMap.asStateFlow()

    private val _categoryTotalCountMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val categoryTotalCountMap: StateFlow<Map<String, Int>> = _categoryTotalCountMap.asStateFlow()

    // =========================================================
    // 下载状态
    // =========================================================
    private val _savePath = MutableStateFlow("${System.getProperty("user.home")}\\角色语音")
    val savePath: StateFlow<String> = _savePath.asStateFlow()

    private val _maxConcurrencyStr = MutableStateFlow("16")
    val maxConcurrencyStr: StateFlow<String> = _maxConcurrencyStr.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _progressText = MutableStateFlow("")
    val progressText: StateFlow<String> = _progressText.asStateFlow()

    // =========================================================
    // 日志
    // =========================================================
    private val _logLines = MutableStateFlow(listOf("欢迎使用卡拉彼丘 Wiki 语音下载器。"))
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    // =========================================================
    // 内部缓存 & Job 管理
    // =========================================================
    private val categoryCache = mutableMapOf<String, List<String>>()
    private var searchJob: Job? = null
    private var scanJob: Job? = null

    // =========================================================
    // 初始化
    // =========================================================
    init {
        scope.launch {
            addLog("正在加载角色数据...")
            WikiEngine.preloadCharacterNames()
            addLog("角色数据加载完成。")
            performSearch()
        }
    }

    // =========================================================
    // 搜索
    // =========================================================
    fun onSearchKeywordChange(value: String) { _searchKeyword.value = value }

    fun onVoiceOnlyChange(value: Boolean) { _voiceOnly.value = value }

    fun performSearch() {
        if (_searchKeyword.value.isBlank()) return

        searchJob?.cancel()
        scanJob?.cancel()

        _isSearching.value = true
        _selectedGroup.value = null
        _characterGroups.value = emptyList()

        searchJob = scope.launch {
            try {
                val keyword = _searchKeyword.value
                val voiceOnly = _voiceOnly.value
                addLog("正在搜索: $keyword ${if (voiceOnly) "(仅语音)" else "(全部类型)"}...")
                val res = WikiEngine.searchAndGroupCharacters(keyword, voiceOnly)
                _characterGroups.value = res
                addLog("搜索完成，找到 ${res.size} 个角色。")
            } catch (_: CancellationException) {
                // 忽略取消
            } catch (e: Exception) {
                addLog("搜索失败: ${e.message}")
            } finally {
                _isSearching.value = false
            }
        }
    }

    // =========================================================
    // 角色选择
    // =========================================================
    fun onSelectGroup(group: WikiEngine.CharacterGroup) {
        if (_isDownloading.value) return

        scanJob?.cancel()
        _selectedGroup.value = group
        _subCategories.value = emptyList()
        _checkedCategories.value = emptyList()

        val cached = categoryCache[group.rootCategory]
        if (cached != null) {
            _subCategories.value = cached
            _checkedCategories.value = cached
        } else {
            _isScanningTree.value = true
            scanJob = scope.launch {
                try {
                    addLog("正在获取 [${group.characterName}] 的所有分类...")
                    val tree = WikiEngine.scanCategoryTree(group.rootCategory)
                    categoryCache[group.rootCategory] = tree
                    _subCategories.value = tree
                    _checkedCategories.value = tree

                    // 更新角色组的 subCategories
                    val updated = _characterGroups.value.toMutableList()
                    val index = updated.indexOf(group)
                    if (index != -1) {
                        updated[index] = group.copy(subCategories = tree)
                        _characterGroups.value = updated
                        _selectedGroup.value = updated[index]
                    }
                    addLog("获取完成，共 ${tree.size} 个分类。")
                } catch (_: CancellationException) {
                    // 忽略取消
                } catch (e: Exception) {
                    addLog("获取分类失败: ${e.message}")
                } finally {
                    _isScanningTree.value = false
                }
            }
        }
    }

    // =========================================================
    // 分类勾选
    // =========================================================
    fun setCategoryChecked(cat: String, checked: Boolean) {
        val current = _checkedCategories.value.toMutableList()
        if (checked) { if (!current.contains(cat)) current.add(cat) }
        else current.remove(cat)
        _checkedCategories.value = current
    }

    fun checkAllCategories() { _checkedCategories.value = _subCategories.value.toList() }

    fun uncheckAllCategories() { _checkedCategories.value = emptyList() }

    // =========================================================
    // 文件弹窗
    // =========================================================
    fun openFileDialog(cat: String) {
        _dialogCategoryName.value = cat
        _dialogFileList.value = emptyList()
        _dialogInitialSelection.value = emptyList()
        _showFileDialog.value = true
        _dialogIsLoading.value = true

        scope.launch {
            try {
                val files = WikiEngine.fetchFilesInCategory(cat, audioOnly = _voiceOnly.value)
                _dialogFileList.value = files
                _categoryTotalCountMap.value += (cat to files.size)

                val manual = _manualSelectionMap.value[cat]
                _dialogInitialSelection.value = manual?.map { it.second } ?: files.map { it.second }
            } catch (e: Exception) {
                addLog("加载失败: ${e.message}")
            } finally {
                _dialogIsLoading.value = false
            }
        }
    }

    fun closeFileDialog() { _showFileDialog.value = false }

    fun confirmFileDialog(selectedFiles: List<Pair<String, String>>) {
        val cat = _dialogCategoryName.value
        _showFileDialog.value = false

        val newMap = _manualSelectionMap.value.toMutableMap()
        newMap[cat] = selectedFiles
        _manualSelectionMap.value = newMap

        val newCountMap = _categoryTotalCountMap.value.toMutableMap()
        newCountMap[cat] = _dialogFileList.value.size
        _categoryTotalCountMap.value = newCountMap

        val checked = _checkedCategories.value.toMutableList()
        if (selectedFiles.isNotEmpty() && !checked.contains(cat)) {
            checked.add(cat)
            _checkedCategories.value = checked
        }
    }

    // =========================================================
    // 下载配置
    // =========================================================
    fun onSavePathChange(value: String) { _savePath.value = value }

    fun onMaxConcurrencyChange(value: String) {
        if (value.all { it.isDigit() }) _maxConcurrencyStr.value = value
    }

    // =========================================================
    // 下载
    // =========================================================
    fun startDownload() {
        val checked = _checkedCategories.value
        if (checked.isEmpty()) return

        _isDownloading.value = true
        val targetDir = File(
            _savePath.value,
            WikiEngine.sanitizeFileName(_selectedGroup.value?.characterName ?: "Unknown")
        )
        val concurrency = _maxConcurrencyStr.value.toIntOrNull() ?: 16

        scope.launch {
            try {
                addLog("开始处理下载任务...")
                val finalDownloadList = mutableListOf<Pair<String, String>>()

                for (cat in checked) {
                    val manual = _manualSelectionMap.value[cat]
                    if (manual != null) {
                        finalDownloadList.addAll(manual)
                        addLog("[${cat.replace("Category:", "")}] 使用手动选择 (${manual.size}项)")
                    } else {
                        addLog("正在扫描 [${cat.replace("Category:", "")}] ...")
                        val files = WikiEngine.fetchFilesInCategory(cat, audioOnly = _voiceOnly.value)
                        finalDownloadList.addAll(files)
                    }
                }

                val uniqueList = finalDownloadList.distinctBy { it.second }
                if (uniqueList.isEmpty()) {
                    addLog("没有文件需要下载。")
                } else {
                    addLog("共 ${uniqueList.size} 个文件，开始下载...")
                    WikiEngine.downloadSpecificFiles(
                        files = uniqueList,
                        saveDir = targetDir,
                        maxConcurrency = concurrency,
                        onLog = { addLog(it) },
                        onProgress = { current, total, name ->
                            _progress.value = current.toFloat() / total
                            _progressText.value = "$current / $total : $name"
                        }
                    )
                    addLog("全部下载完成！")
                }
            } catch (e: Exception) {
                addLog("中断: ${e.message}")
            } finally {
                _isDownloading.value = false
                _progress.value = 0f
                _progressText.value = ""
            }
        }
    }

    // =========================================================
    // 日志
    // =========================================================
    fun addLog(msg: String) {
        val current = _logLines.value.toMutableList()
        current.add(msg)
        if (current.size > 100) current.removeAt(0)
        _logLines.value = current
    }
}

