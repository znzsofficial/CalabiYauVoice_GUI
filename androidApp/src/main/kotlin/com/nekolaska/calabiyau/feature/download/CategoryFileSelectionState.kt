package com.nekolaska.calabiyau.feature.download

import data.SearchMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One request belongs to one dialog/category/mode; late responses cannot replace another dialog. */
internal class CategoryFileSelectionState(
    private val scope: CoroutineScope,
    private val fetch: suspend (String, Boolean) -> List<Pair<String, String>>
) {
    private val _visible = MutableStateFlow(false)
    val visible = _visible.asStateFlow()
    private val _category = MutableStateFlow("")
    val category = _category.asStateFlow()
    private val _files = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val files = _files.asStateFlow()
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected = _selected.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var mode: SearchMode? = null
    private var version = 0
    private var job: Job? = null

    fun open(category: String, mode: SearchMode, manual: List<Pair<String, String>>?) {
        close()
        val token = version
        this.mode = mode
        _category.value = category
        _visible.value = true; _loading.value = true; _error.value = null
        val manualUrls = manual?.map { it.second }?.toSet()
        job = scope.launch {
            try {
                val rows = fetch(category, mode == SearchMode.VOICE_ONLY).distinctBy { it.second }
                if (token != version) return@launch
                _files.value = rows
                _selected.value = rows.map { it.second }.filter { manualUrls == null || it in manualUrls }.toSet()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token == version) _error.value = e.message ?: "加载分类文件失败" }
            finally { if (token == version) _loading.value = false }
        }
    }

    fun close() {
        version++; job?.cancel(); job = null; mode = null
        _visible.value = false; _loading.value = false; _error.value = null
        _files.value = emptyList(); _selected.value = emptySet()
    }

    fun toggle(url: String) {
        if (_loading.value || _error.value != null || _files.value.none { it.second == url }) return
        _selected.value = if (url in _selected.value) _selected.value - url else _selected.value + url
    }
    fun selectAll() { if (!_loading.value && _error.value == null) _selected.value = _files.value.map { it.second }.toSet() }
    fun clear() { _selected.value = emptySet() }
    fun invert() { if (!_loading.value && _error.value == null) _selected.value = _files.value.map { it.second }.toSet() - _selected.value }

    fun confirm(currentMode: SearchMode): Pair<String, List<Pair<String, String>>>? {
        if (!_visible.value || _loading.value || _error.value != null || mode != currentMode) return null
        val result = _category.value to _files.value.filter { it.second in _selected.value }
        close()
        return result
    }
}
