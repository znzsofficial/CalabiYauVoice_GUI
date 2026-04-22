package com.nekolaska.calabiyau.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import data.PortraitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import portrait.CharacterPortraitCatalog
import portrait.PortraitCostume

/**
 * 立绘浏览相关状态。
 * 管理角色立绘目录、服装选择等。
 */
class PortraitViewModel : ViewModel() {

    private val _selectedPortraitCharacter = MutableStateFlow<String?>(null)
    val selectedPortraitCharacter: StateFlow<String?> = _selectedPortraitCharacter.asStateFlow()

    private val _portraitCatalog = MutableStateFlow<CharacterPortraitCatalog?>(null)
    val portraitCatalog: StateFlow<CharacterPortraitCatalog?> = _portraitCatalog.asStateFlow()

    private val _isLoadingPortrait = MutableStateFlow(false)
    val isLoadingPortrait: StateFlow<Boolean> = _isLoadingPortrait.asStateFlow()

    private val _selectedPortraitCostume = MutableStateFlow<PortraitCostume?>(null)
    val selectedPortraitCostume: StateFlow<PortraitCostume?> = _selectedPortraitCostume.asStateFlow()

    fun onSelectPortraitCharacter(characterName: String) {
        _selectedPortraitCharacter.value = characterName
        _portraitCatalog.value = null
        _selectedPortraitCostume.value = null
        _isLoadingPortrait.value = true
        viewModelScope.launch {
            try {
                val catalog = PortraitRepository.loadCharacterPortraitCatalog(characterName)
                _portraitCatalog.value = catalog
            } catch (_: Exception) {
                _portraitCatalog.value = CharacterPortraitCatalog(characterName, emptyList())
            } finally {
                _isLoadingPortrait.value = false
            }
        }
    }

    fun clearSelectedPortraitCharacter() {
        _selectedPortraitCharacter.value = null
        _portraitCatalog.value = null
        _selectedPortraitCostume.value = null
    }

    fun selectPortraitCostume(costume: PortraitCostume) {
        _selectedPortraitCostume.value = costume
    }
}
