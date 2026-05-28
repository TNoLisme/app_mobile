package com.example.appmobile.ui.pages.garden

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appmobile.data.garden.GardenRepository
import com.example.appmobile.data.garden.GardenUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GardenViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GardenRepository(application.applicationContext)
    private val _state = MutableStateFlow(GardenUiState(isLoading = true))
    val state: StateFlow<GardenUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val selected = _state.value.selectedPlantId
            _state.value = repository.getGardenState().copy(selectedPlantId = selected)
        }
    }

    fun checkIn() {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            _state.value = repository.checkIn().copy(isBusy = false)
        }
    }

    fun claimReward(taskId: String) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            _state.value = repository.claimTaskReward(taskId).copy(isBusy = false)
        }
    }

    fun waterPlant(emotionId: String) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            _state.value = repository.waterPlant(emotionId).copy(isBusy = false, selectedPlantId = emotionId)
        }
    }

    fun sunPlant(emotionId: String) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            _state.value = repository.sunPlant(emotionId).copy(isBusy = false, selectedPlantId = emotionId)
        }
    }

    fun openPlant(emotionId: String) {
        _state.update { it.copy(selectedPlantId = emotionId) }
    }

    fun closePlant() {
        _state.update { it.copy(selectedPlantId = null) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, errorMessage = null) }
    }
}
