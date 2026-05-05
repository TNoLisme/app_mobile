package com.example.appmobile.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appmobile.data.remote.NetworkClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

data class HomeEmotionUi(
    val name: String,
    val accuracy: Double,
    val correct: Int,
    val incorrect: Int
)

data class HomeRecentGameUi(
    val name: String,
    val lastPlayed: String
)

class HomeViewModel : ViewModel() {
    val childName = mutableStateOf<String?>(null)
    val emotions = mutableStateListOf<HomeEmotionUi>()
    val recentGames = mutableStateListOf<HomeRecentGameUi>()
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "local-player"
            try {
                val profileResponse = NetworkClient.apiService.getUserProfile(userId)
                if (profileResponse.isSuccessful) {
                    childName.value = profileResponse.body()?.name
                }

                val gamesResponse = NetworkClient.apiService.getRecentGames(userId)
                recentGames.clear()
                if (gamesResponse.isSuccessful) {
                    gamesResponse.body()?.data.orEmpty()
                        .map {
                            HomeRecentGameUi(
                                name = it.name ?: "Game",
                                lastPlayed = formatDate(it.lastPlayed)
                            )
                        }
                        .let(recentGames::addAll)
                }

                val accuracyResponse = NetworkClient.apiService.getEmotionAccuracy(userId)
                emotions.clear()
                if (accuracyResponse.isSuccessful) {
                    accuracyResponse.body()?.data.orEmpty()
                        .map { (name, stat) ->
                            HomeEmotionUi(
                                name = name,
                                accuracy = (stat.accuracy ?: 0f).toDouble(),
                                correct = stat.correct ?: 0,
                                incorrect = stat.incorrect ?: 0
                            )
                        }
                        .sortedByDescending { it.accuracy }
                        .let(emotions::addAll)
                }
            } catch (e: Exception) {
                errorMessage.value = "Chưa kết nối được backend."
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun formatDate(value: String?): String {
        if (value.isNullOrBlank()) return "---"
        return value.substringBefore("T").substringBefore(" ")
    }
}
