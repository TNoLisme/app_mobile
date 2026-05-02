package com.example.appmobile.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// models removed: keep minimal state placeholders
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    val user = mutableStateOf<Any?>(null)
    val emotions = mutableStateListOf<String>()
    val isLoading = mutableStateOf(false)

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            isLoading.value = true
            // intentionally leave user and emotion accuracy empty for now
            user.value = null
            emotions.clear()
            isLoading.value = false
        }
    }
}
