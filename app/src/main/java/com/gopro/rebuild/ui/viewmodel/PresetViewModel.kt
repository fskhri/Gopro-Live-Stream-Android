package com.gopro.rebuild.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gopro.rebuild.camera.PresetGroup
import com.gopro.rebuild.wifi.GoProWifiApi
import com.gopro.rebuild.wifi.PresetData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PresetViewModel @Inject constructor(
    private val wifiApi: GoProWifiApi
) : ViewModel() {

    private val _presetGroups = MutableStateFlow<Map<PresetGroup, List<PresetData>>>(emptyMap())
    val presetGroups: StateFlow<Map<PresetGroup, List<PresetData>>> = _presetGroups.asStateFlow()

    private val _currentPreset = MutableStateFlow<Int?>(null)
    val currentPreset: StateFlow<Int?> = _currentPreset.asStateFlow()

    private val _selectedGroup = MutableStateFlow(PresetGroup.VIDEO)
    val selectedGroup: StateFlow<PresetGroup> = _selectedGroup.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadPresets() {
        viewModelScope.launch {
            _isLoading.value = true

            // Get current preset from state
            wifiApi.getState().onSuccess { state ->
                _currentPreset.value = state.status["97"]
            }

            // Get all presets
            wifiApi.getPresets().onSuccess { presetList ->
                val groupMap = mutableMapOf<PresetGroup, List<PresetData>>()

                presetList.presetGroupArray.forEach { group ->
                    val presetGroup = PresetGroup.fromId(group.id)
                    groupMap[presetGroup] = group.presetArray
                }

                _presetGroups.value = groupMap
            }

            _isLoading.value = false
        }
    }

    fun selectGroup(group: PresetGroup) {
        _selectedGroup.value = group
    }

    fun selectPreset(presetId: Int) {
        viewModelScope.launch {
            wifiApi.setPreset(presetId).onSuccess {
                _currentPreset.value = presetId
            }
        }
    }
}
