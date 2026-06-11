package com.example.appmobile.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appmobile.data.garden.GardenHomeSummary
import com.example.appmobile.data.garden.GardenRepository
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.ReportPayloadDto
import com.example.appmobile.data.remote.dto.SessionHistoryItemDto
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class HomeEmotionUi(
    val emotionId: String,
    val accuracy: Int,
    val attempts: Int
)

data class HomeRecentGameUi(
    val id: String?,
    val name: String,
    val gameType: String?,
    val lastPlayed: String
)

data class HomeUiState(
    val childName: String? = null,
    val recommendedEmotionId: String = "happy",
    val learnedEmotionCount: Int = 0,
    val totalEmotionCount: Int = 6,
    val weeklyPlayCount: Int = 0,
    val weeklyAccuracy: Int? = null,
    val weakEmotionId: String? = null,
    val weakEmotionIds: List<String> = emptyList(),
    val recentGames: List<HomeRecentGameUi> = emptyList(),
    val reportSummary: ReportSummary? = null,
    val reportActionText: String = "Tạo báo cáo",
    val gardenSummary: GardenHomeSummary? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class ReportSummary(
    val sessionsCount: Int,
    val averageScore: Int?,
    val learnedEmotionCount: Int,
    val totalEmotionCount: Int = 6
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(HomeUiState(isLoading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            val context = getApplication<Application>()
            val googleDisplayName = runCatching {
                GoogleSignIn.getLastSignedInAccount(context)?.displayName
            }.getOrNull()
            val firebaseDisplayName = FirebaseAuth.getInstance().currentUser?.displayName
            val userId = FirebaseAuth.getInstance().currentUser?.uid
                ?: AppSession.currentBackendUserId()
                ?: AppSession.getBackendUserId(context)
                ?: "local-player"

            var connected = false
            var failedRequests = 0
            var childName: String? = bestHomeDisplayName(
                googleDisplayName,
                firebaseDisplayName,
                _state.value.childName
            )
            var recentGames: List<HomeRecentGameUi> = _state.value.recentGames
            var emotionStats: List<HomeEmotionUi> = emptyList()
            var weakEmotionIds: List<String> = emptyList()
            var weeklyPlayCount = 0
            var reportSummary: ReportSummary? = _state.value.reportSummary
            var reportActionText = _state.value.reportActionText
            val gardenSummary = GardenRepository(context).getHomeSummary()

            runCatching {
                NetworkClient.apiService.getUserProfile(userId)
            }.onSuccess { response ->
                connected = true
                if (response.isSuccessful) {
                    childName = bestHomeDisplayName(
                        googleDisplayName,
                        firebaseDisplayName,
                        response.body()?.name,
                        childName
                    )
                }
            }.onFailure {
                failedRequests += 1
            }

            runCatching {
                NetworkClient.apiService.getRecentGames(userId, limit = 8)
            }.onSuccess { response ->
                connected = true
                if (response.isSuccessful) {
                    recentGames = response.body()?.data.orEmpty().map { game ->
                        HomeRecentGameUi(
                            id = game.gameId,
                            name = game.name?.takeIf { it.isNotBlank() } ?: "Trò chơi",
                            gameType = game.gameType,
                            lastPlayed = formatDate(game.lastPlayed)
                        )
                    }
                }
            }.onFailure {
                failedRequests += 1
            }

            runCatching {
                NetworkClient.apiService.getEmotionAccuracy(userId)
            }.onSuccess { response ->
                connected = true
                if (response.isSuccessful) {
                    emotionStats = response.body()?.data.orEmpty().map { (rawName, stat) ->
                        val correct = stat.correct ?: 0
                        val incorrect = stat.incorrect ?: 0
                        val attempts = correct + incorrect
                        val accuracy = stat.accuracy?.toInt()?.coerceIn(0, 100)
                            ?: if (attempts > 0) ((correct * 100f) / attempts).toInt().coerceIn(0, 100) else 0
                        HomeEmotionUi(
                            emotionId = normalizeEmotionId(rawName),
                            accuracy = accuracy,
                            attempts = attempts
                        )
                    }.distinctBy { it.emotionId }
                }
            }.onFailure {
                failedRequests += 1
            }

            runCatching {
                NetworkClient.apiService.getWeakEmotions(userId, limit = 3)
            }.onSuccess { response ->
                connected = true
                if (response.isSuccessful) {
                    weakEmotionIds = response.body()?.data.orEmpty()
                        .mapNotNull { it.emotion }
                        .map(::normalizeEmotionId)
                        .filter { it in EmotionOrder }
                        .distinct()
                }
            }.onFailure {
                failedRequests += 1
            }

            runCatching {
                NetworkClient.apiService.getSessionHistory(userId = userId, skip = 0, limit = 1000)
            }.onSuccess { response ->
                connected = true
                if (response.isSuccessful) {
                    weeklyPlayCount = response.body()?.sessions.orEmpty().count(::isCurrentWeekSession)
                }
            }.onFailure {
                failedRequests += 1
            }

            runCatching {
                NetworkClient.apiService.previewReport(childUserId = userId, reportType = "weekly")
            }.onSuccess { response ->
                connected = true
                if (response.isSuccessful) {
                    val preview = response.body()?.data
                    val sessions = preview?.stats?.totalSessions ?: 0
                    val avgScore = preview?.stats?.avgScore?.toInt()?.coerceIn(0, 100)
                    val learnedFromPreview = preview?.insights?.emotionStats.orEmpty()
                        .count { (_, stat) ->
                            val attempts = stat.attempts ?: ((stat.correct ?: 0) + (stat.incorrect ?: 0))
                            attempts > 0
                        }
                    reportSummary = ReportSummary(
                        sessionsCount = sessions.coerceAtLeast(0),
                        averageScore = avgScore,
                        learnedEmotionCount = learnedFromPreview.coerceIn(0, 6),
                        totalEmotionCount = 6
                    )
                }
            }.onFailure {
                failedRequests += 1
            }

            runCatching {
                NetworkClient.apiService.getReportHistory(userId)
            }.onSuccess { response ->
                connected = true
                if (response.isSuccessful) {
                    val currentWeeklyReport = response.body()?.data.orEmpty()
                        .filter { it.reportType.equals("weekly", ignoreCase = true) }
                        .filter(::isCurrentReportPeriod)
                        .distinctByReportPeriod()
                        .maxByOrNull { it.generatedAt.orEmpty() }
                    val reportKey = currentWeeklyReport?.let(::reportPeriodKey).orEmpty()
                    val lastSentKey = getLastSentWeeklyKey(context, userId).orEmpty()
                    reportActionText = if (currentWeeklyReport != null && reportKey.isNotBlank() && reportKey != lastSentKey) {
                        "Mở báo cáo"
                    } else {
                        "Tạo báo cáo"
                    }
                }
            }.onFailure {
                failedRequests += 1
            }

            val learnedEmotionCount = emotionStats.count { it.attempts > 0 }.coerceIn(0, 6)
            val playedEmotions = emotionStats.filter { it.attempts > 0 }
            val weeklyAccuracy = playedEmotions
                .takeIf { it.isNotEmpty() }
                ?.map { it.accuracy }
                ?.average()
                ?.toInt()
                ?.coerceIn(0, 100)
            val fallbackWeeklyPlayCount = recentGames.size
            val finalWeeklyPlayCount = weeklyPlayCount.takeIf { it > 0 } ?: fallbackWeeklyPlayCount
            val recommendedEmotionId = resolveRecommendedEmotion(emotionStats)
            val weakEmotionId = resolveWeakEmotion(emotionStats, weakEmotionIds)
            val finalReportSummary = reportSummary ?: ReportSummary(
                sessionsCount = finalWeeklyPlayCount,
                averageScore = weeklyAccuracy,
                learnedEmotionCount = learnedEmotionCount,
                totalEmotionCount = 6
            )

            _state.update {
                it.copy(
                    childName = childName,
                    recommendedEmotionId = recommendedEmotionId,
                    learnedEmotionCount = learnedEmotionCount,
                    weeklyPlayCount = finalWeeklyPlayCount,
                    weeklyAccuracy = weeklyAccuracy,
                    weakEmotionId = weakEmotionId,
                    weakEmotionIds = weakEmotionIds,
                    recentGames = recentGames,
                    reportSummary = finalReportSummary,
                    reportActionText = reportActionText,
                    gardenSummary = gardenSummary,
                    isLoading = false,
                    errorMessage = if (!connected && failedRequests > 0) {
                        "Chưa tải được dữ liệu. Bé vẫn có thể học và chơi trên thiết bị này."
                    } else {
                        null
                    }
                )
            }
        }
    }

    private fun resolveRecommendedEmotion(stats: List<HomeEmotionUi>): String {
        val learnedIds = stats.filter { it.attempts > 0 }.map { it.emotionId }.toSet()
        val unlearned = EmotionOrder.firstOrNull { it !in learnedIds }
        if (unlearned != null) return unlearned

        val weakestLearned = stats
            .filter { it.attempts > 0 }
            .minByOrNull { it.accuracy }
            ?.emotionId
        return weakestLearned ?: "happy"
    }

    private fun resolveWeakEmotion(stats: List<HomeEmotionUi>, weakIds: List<String>): String? {
        weakIds.firstOrNull()?.let { return it }
        return stats
            .filter { it.attempts > 0 }
            .minByOrNull { it.accuracy }
            ?.emotionId
    }

    private fun formatDate(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value.substringBefore("T").substringBefore(" ")
    }

    private fun isCurrentWeekSession(session: SessionHistoryItemDto): Boolean {
        val date = parseDate(session.startTime ?: session.endTime) ?: return false
        val now = Calendar.getInstance()
        val startOfWeek = now.clone() as Calendar
        startOfWeek.firstDayOfWeek = Calendar.MONDAY
        startOfWeek.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        startOfWeek.set(Calendar.HOUR_OF_DAY, 0)
        startOfWeek.set(Calendar.MINUTE, 0)
        startOfWeek.set(Calendar.SECOND, 0)
        startOfWeek.set(Calendar.MILLISECOND, 0)

        val endOfWeek = startOfWeek.clone() as Calendar
        endOfWeek.add(Calendar.DAY_OF_YEAR, 7)

        return !date.before(startOfWeek) && date.before(endOfWeek)
    }

    private fun parseDate(raw: String?): Calendar? {
        if (raw.isNullOrBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                val parser = SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }
                val parsed = parser.parse(raw) ?: return@runCatching null
                Calendar.getInstance().apply { time = parsed }
            }.getOrNull()
        }
    }

    private fun List<ReportPayloadDto>.distinctByReportPeriod(): List<ReportPayloadDto> {
        return filter { !it.reportId.isNullOrBlank() }
            .groupBy(::reportPeriodKey)
            .values
            .map { group -> group.maxByOrNull { it.generatedAt.orEmpty() } ?: group.first() }
    }

    private fun reportPeriodKey(report: ReportPayloadDto): String {
        val data = parseReportData(report.data)
        val start = data?.optString("start_date").orEmpty()
        val end = data?.optString("end_date").orEmpty()
        return if (start.isNotBlank() && end.isNotBlank()) {
            "${report.reportType ?: "weekly"}_${start}_${end}"
        } else {
            report.reportId.orEmpty()
        }
    }

    private fun isCurrentReportPeriod(report: ReportPayloadDto): Boolean {
        val data = parseReportData(report.data) ?: return false
        val start = data.optString("start_date")
        val end = data.optString("end_date")
        if (start.isBlank() || end.isBlank()) return false
        val parser = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).apply {
            isLenient = false
        }
        return runCatching {
            val startDate = parser.parse(start) ?: return false
            val endDate = parser.parse(end) ?: return false
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            !today.before(startDate) && !today.after(endDate)
        }.getOrDefault(false)
    }

    private fun parseReportData(raw: String?): JSONObject? {
        return runCatching { JSONObject(raw.orEmpty()) }.getOrNull()
    }

    private fun getLastSentWeeklyKey(context: Context, userId: String): String? {
        return context.getSharedPreferences(REPORT_UI_PREF, Context.MODE_PRIVATE)
            .getString("$KEY_LAST_SENT_WEEKLY_PREFIX$userId", null)
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        private const val REPORT_UI_PREF = "report_ui_state"
        private const val KEY_LAST_SENT_WEEKLY_PREFIX = "last_sent_weekly_"
    }
}

private fun cleanHomeDisplayName(name: String?): String? {
    val normalized = name
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val withoutProviderWord = normalized.replace(Regex("(?i)\\s*\\(?google\\)?\\s*$"), "").trim()
    return if (
        withoutProviderWord.endsWith("gg", ignoreCase = true) &&
        withoutProviderWord.dropLast(2).contains(" ")
    ) {
        withoutProviderWord.dropLast(2).trim()
    } else {
        withoutProviderWord
    }
}

private fun bestHomeDisplayName(vararg names: String?): String? {
    return names
        .mapNotNull(::cleanHomeDisplayName)
        .distinct()
        .maxWithOrNull(
            compareBy<String> { name -> name.count { it.code > 127 } }
                .thenBy { name -> name.length }
        )
}

private val EmotionOrder = listOf("happy", "sad", "angry", "fear", "surprise", "disgust")

private fun normalizeEmotionId(value: String): String {
    val lower = value.trim().lowercase(Locale.ROOT)
    return when {
        "happy" in lower || "vui" in lower -> "happy"
        "sad" in lower || "buồn" in lower || "buon" in lower -> "sad"
        "angry" in lower || "tức" in lower || "tuc" in lower || "giận" in lower || "gian" in lower -> "angry"
        "fear" in lower || "sợ" in lower || "so" in lower -> "fear"
        "surprise" in lower || "ngạc" in lower || "ngac" in lower -> "surprise"
        "disgust" in lower || "ghê" in lower || "ghe" in lower -> "disgust"
        else -> lower
    }
}
