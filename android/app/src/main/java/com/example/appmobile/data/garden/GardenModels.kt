package com.example.appmobile.data.garden

enum class GardenTaskType {
    DAILY_CHECK_IN,
    LEARN_EMOTION,
    PLAY_GAME,
    COMPLETE_CAMERA_CHALLENGE,
    CREATE_PHOTOBOOTH,
    SEND_REPORT,
    PRACTICE_WEAK_EMOTION
}

enum class GardenTaskPeriod {
    DAILY,
    WEEKLY
}

enum class GardenTaskStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED_NOT_CLAIMED,
    CLAIMED
}

data class EmotionPlant(
    val emotionId: String,
    val level: Int,
    val growthPoints: Int,
    val pointsToNextLevel: Int,
    val totalGrowthPoints: Int,
    val lastWateredAtMs: Long?
)

data class EmotionPlantSpecies(
    val emotionId: String,
    val speciesId: String,
    val speciesName: String,
    val emotionDisplayName: String,
    val description: String,
    val stages: List<PlantGrowthStage>
)

data class PlantGrowthStage(
    val level: Int,
    val stageName: String,
    val visualKey: String,
    val shortDescription: String
)

data class GardenInventory(
    val water: Int = 0,
    val sunlight: Int = 0,
    val seeds: Int = 0,
    val emotionStars: Int = 0
)

data class GardenReward(
    val water: Int = 0,
    val sunlight: Int = 0,
    val seeds: Int = 0,
    val emotionStars: Int = 0,
    val growthPoints: Int = 0
)

data class GardenTask(
    val id: String,
    val type: GardenTaskType,
    val period: GardenTaskPeriod,
    val title: String,
    val description: String,
    val progress: Int,
    val target: Int,
    val status: GardenTaskStatus,
    val reward: GardenReward,
    val relatedEmotionId: String?,
    val dateKey: String,
    val weekKey: String?,
    val progressKeys: List<String> = emptyList()
)

data class GardenUiState(
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val plants: List<EmotionPlant> = emptyList(),
    val inventory: GardenInventory = GardenInventory(),
    val dailyTasks: List<GardenTask> = emptyList(),
    val weeklyTasks: List<GardenTask> = emptyList(),
    val pendingRewardCount: Int = 0,
    val gardenLevel: Int = 1,
    val gardenProgressPercent: Int = 0,
    val streakDays: Int = 0,
    val message: String? = null,
    val errorMessage: String? = null,
    val selectedPlantId: String? = null
)

data class GardenHomeSummary(
    val gardenProgressPercent: Int,
    val pendingRewardCount: Int,
    val suggestedEmotionToCare: String?,
    val todayTaskCount: Int,
    val completedTodayTaskCount: Int
)

sealed class LearningEvent {
    data class EmotionLessonCompleted(val emotionId: String) : LearningEvent()
    data class GameCompleted(val gameId: String, val emotionId: String?, val score: Int?) : LearningEvent()
    data class CameraChallengeCompleted(val emotionId: String, val success: Boolean) : LearningEvent()
    data class PhotoBoothSaved(val emotionIds: List<String>) : LearningEvent()
    object ReportSentToParent : LearningEvent()
}
