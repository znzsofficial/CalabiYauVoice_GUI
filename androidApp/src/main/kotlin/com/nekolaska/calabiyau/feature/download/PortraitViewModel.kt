package com.nekolaska.calabiyau.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import data.PortraitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import portrait.CharacterPortraitCatalog
import portrait.PortraitCostume
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 立绘浏览相关状态。
 * 管理角色立绘目录、服装选择等。
 */
class PortraitViewModel : ViewModel() {

    private var portraitLoadJob: Job? = null
    private var portraitLoadVersion = 0L

    private val _selectedPortraitCharacter = MutableStateFlow<String?>(null)
    val selectedPortraitCharacter: StateFlow<String?> = _selectedPortraitCharacter.asStateFlow()

    private val _portraitCatalog = MutableStateFlow<CharacterPortraitCatalog?>(null)
    val portraitCatalog: StateFlow<CharacterPortraitCatalog?> = _portraitCatalog.asStateFlow()

    private val _isLoadingPortrait = MutableStateFlow(false)
    val isLoadingPortrait: StateFlow<Boolean> = _isLoadingPortrait.asStateFlow()

    private val _selectedPortraitCostume = MutableStateFlow<PortraitCostume?>(null)
    val selectedPortraitCostume: StateFlow<PortraitCostume?> = _selectedPortraitCostume.asStateFlow()

    fun onSelectPortraitCharacter(characterName: String) {
        portraitLoadJob?.cancel()
        val loadVersion = ++portraitLoadVersion
        _selectedPortraitCharacter.value = characterName
        _portraitCatalog.value = null
        _selectedPortraitCostume.value = null
        _isLoadingPortrait.value = true
        portraitLoadJob = viewModelScope.launch {
            try {
                val catalog = PortraitRepository.loadCharacterPortraitCatalog(characterName)
                currentCoroutineContext().ensureActive()
                if (loadVersion != portraitLoadVersion) return@launch
                _portraitCatalog.value = catalog
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (loadVersion == portraitLoadVersion) {
                    _portraitCatalog.value = CharacterPortraitCatalog(characterName, emptyList())
                }
            } finally {
                if (loadVersion == portraitLoadVersion) {
                    _isLoadingPortrait.value = false
                    portraitLoadJob = null
                }
            }
        }
    }

    fun clearSelectedPortraitCharacter() {
        portraitLoadJob?.cancel()
        portraitLoadVersion++
        portraitLoadJob = null
        _selectedPortraitCharacter.value = null
        _portraitCatalog.value = null
        _selectedPortraitCostume.value = null
        _isLoadingPortrait.value = false
    }

    fun selectPortraitCostume(costume: PortraitCostume) {
        _selectedPortraitCostume.value = costume
    }
}
