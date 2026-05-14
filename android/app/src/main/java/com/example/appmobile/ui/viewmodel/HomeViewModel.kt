package com.example.appmobile.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appmobile.data.local.AppSession
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
    val id: String?,
    val name: String,
    val gameType: String?,
    val lastPlayed: String
)

data class HomeMetricUi(
    val name: String,
    val value: Double
)

class HomeViewModel : ViewModel() {
    val childName = mutableStateOf<String?>(null)
    val emotions = mutableStateListOf<HomeEmotionUi>()
    val recentGames = mutableStateListOf<HomeRecentGameUi>()
    val improvements = mutableStateListOf<HomeMetricUi>()
    val gameRatios = mutableStateListOf<HomeMetricUi>()
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: AppSession.currentBackendUserId() ?: "local-player"
            var connected = false
            var failedRequests = 0

            runCatching {
                NetworkClient.apiService.getUserProfile(userId)
            }.onSuccess { profileResponse ->
                connected = true
                if (profileResponse.isSuccessful) {
                    childName.value = profileResponse.body()?.name
                }
            }.onFailure {
                failedRequests += 1
            }

            runCatching {
                NetworkClient.apiService.getRecentGames(userId)
            }.onSuccess { gamesResponse ->
                connected = true
                if (gamesResponse.isSuccessful) {
                    recentGames.clear()
                    gamesResponse.body()?.data.orEmpty()
                        .map {
                            HomeRecentGameUi(
                                id = it.gameId,
                                name = it.name ?: "Game",
                                gameType = it.gameType,
                                lastPlayed = formatDate(it.lastPlayed)
                            )
                        }
                        .let(recentGames::addAll)
                }
            }.onFailure {
                failedRequests += 1
            }

            runCatching {
                NetworkClient.apiService.getEmotionAccuracy(userId)
            }.onSuccess { accuracyResponse ->
                connected = true
                if (accuracyResponse.isSuccessful) {
                    emotions.clear()
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
            }.onFailure {
                failedRequests += 1
            }

            runCatching {
                NetworkClient.apiService.getEmotionImprovement(userId)
            }.onSuccess { improvementResponse ->
                connected = true
                if (improvementResponse.isSuccessful) {
                    improvements.clear()
                    improvementResponse.body()?.data.orEmpty()
                        .map { (name, value) -> HomeMetricUi(name = name, value = value.toDouble()) }
                        .sortedByDescending { it.value }
                        .let(improvements::addAll)
                }
            }.onFailure {
                failedRequests += 1
            }

            runCatching {
                NetworkClient.apiService.getGamePlayRatio(userId)
            }.onSuccess { gameRatioResponse ->
                connected = true
                if (gameRatioResponse.isSuccessful) {
                    gameRatios.clear()
                    gameRatioResponse.body()?.data.orEmpty()
                        .map { (name, value) -> HomeMetricUi(name = name, value = value.toDouble()) }
                        .sortedByDescending { it.value }
                        .let(gameRatios::addAll)
                }
            }.onFailure {
                failedRequests += 1
            }

            if (!connected && failedRequests > 0) {
                errorMessage.value = "Chưa tải được dữ liệu. Bé vẫn có thể học và chơi trên thiết bị này."
            }

            isLoading.value = false
        }
    }

    private fun formatDate(value: String?): String {
        if (value.isNullOrBlank()) return "---"
        return value.substringBefore("T").substringBefore(" ")
    }
}
