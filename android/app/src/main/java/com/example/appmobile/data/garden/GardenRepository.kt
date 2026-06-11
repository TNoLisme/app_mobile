package com.example.appmobile.data.garden

import android.content.Context
import com.example.appmobile.data.local.AppSession
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class GardenRepository(private val context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PrefName, Context.MODE_PRIVATE)

    @Synchronized
    fun getGardenState(message: String? = null): GardenUiState {
        val snapshot = loadSnapshot().ensureCurrentTasks()
        saveSnapshot(snapshot)
        return snapshot.toUiState(message = message)
    }

    @Synchronized
    fun getHomeSummary(): GardenHomeSummary {
        val state = getGardenState()
        val suggested = state.plants
            .filter { it.level < MaxPlantLevel }
            .minWithOrNull(compareBy<EmotionPlant> { it.level }.thenBy { it.growthPoints })
            ?.emotionId
        val completedToday = state.dailyTasks.count {
            it.status == GardenTaskStatus.COMPLETED_NOT_CLAIMED || it.status == GardenTaskStatus.CLAIMED
        }
        return GardenHomeSummary(
            gardenProgressPercent = state.gardenProgressPercent,
            pendingRewardCount = state.pendingRewardCount,
            suggestedEmotionToCare = suggested,
            todayTaskCount = state.dailyTasks.size,
            completedTodayTaskCount = completedToday
        )
    }

    @Synchronized
    fun resetGarden(): GardenUiState {
        val snapshot = GardenSnapshot(
            plants = EmotionIds.map(::initialPlant),
            inventory = GardenInventory(),
            tasks = emptyList(),
            eventKeys = emptySet(),
            lastCheckInDate = null,
            streakDays = 0
        ).ensureCurrentTasks()
        saveSnapshot(snapshot)
        return snapshot.toUiState("Vườn cảm xúc đã được đặt lại.")
    }

    @Synchronized
    fun checkIn(): GardenUiState {
        val today = todayKey()
        val snapshot = loadSnapshot().ensureCurrentTasks()
        val taskId = dailyTaskId(GardenTaskType.DAILY_CHECK_IN, today)
        val task = snapshot.tasks.firstOrNull { it.id == taskId }
            ?: return snapshot.toUiState("Hôm nay mình chăm vườn nhé!")
        if (task.status == GardenTaskStatus.CLAIMED) {
            return snapshot.toUiState("Hôm nay con đã nhận giọt nước rồi.")
        }

        val completedTask = task.copy(progress = 1, status = GardenTaskStatus.COMPLETED_NOT_CLAIMED)
        val prepared = snapshot.copy(tasks = snapshot.tasks.replaceTask(completedTask))
        val claimed = prepared.claimTaskInternal(taskId)
        val nextStreak = when {
            snapshot.lastCheckInDate == today -> snapshot.streakDays
            snapshot.lastCheckInDate == yesterdayKey() -> (snapshot.streakDays + 1).coerceAtLeast(1)
            else -> 1
        }
        val updated = claimed.copy(lastCheckInDate = today, streakDays = nextStreak)
        saveSnapshot(updated)
        return updated.toUiState("Con nhận được 1 giọt nước. Vườn cảm xúc vui hơn rồi!")
    }

    @Synchronized
    fun claimTaskReward(taskId: String): GardenUiState {
        val snapshot = loadSnapshot().ensureCurrentTasks()
        val task = snapshot.tasks.firstOrNull { it.id == taskId }
            ?: return snapshot.toUiState("Chưa tìm thấy nhiệm vụ này.")
        if (task.status == GardenTaskStatus.CLAIMED) {
            return snapshot.toUiState("Phần thưởng này đã nhận rồi.")
        }
        if (task.status != GardenTaskStatus.COMPLETED_NOT_CLAIMED) {
            return snapshot.toUiState("Con hoàn thành nhiệm vụ rồi quay lại nhận thưởng nhé.")
        }

        val updated = snapshot.claimTaskInternal(taskId)
        saveSnapshot(updated)
        return updated.toUiState("Đã nhận thưởng: ${task.reward.asText()}.")
    }

    @Synchronized
    fun waterPlant(emotionId: String): GardenUiState {
        val normalized = normalizeEmotionId(emotionId)
        val snapshot = loadSnapshot().ensureCurrentTasks()
        val plant = snapshot.plants.firstOrNull { it.emotionId == normalized }
            ?: return snapshot.toUiState("Loài thực vật cảm xúc này chưa sẵn sàng.")
        val speciesName = plantSpeciesName(normalized)
        if (plant.level >= MaxPlantLevel) {
            return snapshot.toUiState("$speciesName đã trưởng thành rồi!")
        }
        if (snapshot.inventory.water <= 0) {
            return snapshot.toUiState("Con hãy hoàn thành nhiệm vụ để nhận thêm giọt nước nhé.")
        }

        val updatedPlant = plant.addGrowth(1, caredAt = System.currentTimeMillis())
        val updated = snapshot.copy(
            inventory = snapshot.inventory.copy(water = (snapshot.inventory.water - 1).coerceAtLeast(0)),
            plants = snapshot.plants.replacePlant(updatedPlant)
        )
        saveSnapshot(updated)
        val levelUp = updatedPlant.level > plant.level
        val message = if (levelUp) {
            "$speciesName đã lên Level ${updatedPlant.level} rồi!"
        } else {
            "$speciesName lớn thêm rồi!"
        }
        return updated.toUiState(message)
    }

    @Synchronized
    fun sunPlant(emotionId: String): GardenUiState {
        val normalized = normalizeEmotionId(emotionId)
        val snapshot = loadSnapshot().ensureCurrentTasks()
        val plant = snapshot.plants.firstOrNull { it.emotionId == normalized }
            ?: return snapshot.toUiState("Loài thực vật cảm xúc này chưa sẵn sàng.")
        val speciesName = plantSpeciesName(normalized)
        if (plant.level >= MaxPlantLevel) {
            return snapshot.toUiState("$speciesName đã trưởng thành rồi!")
        }
        if (snapshot.inventory.sunlight <= 0) {
            return snapshot.toUiState("Con hãy hoàn thành nhiệm vụ để nhận thêm ánh nắng nhé.")
        }

        val updatedPlant = plant.addGrowth(SunlightGrowthBoost, caredAt = System.currentTimeMillis())
        val updated = snapshot.copy(
            inventory = snapshot.inventory.copy(sunlight = (snapshot.inventory.sunlight - 1).coerceAtLeast(0)),
            plants = snapshot.plants.replacePlant(updatedPlant)
        )
        saveSnapshot(updated)
        val levelUp = updatedPlant.level > plant.level
        val message = if (levelUp) {
            "Ánh nắng giúp $speciesName lên Level ${updatedPlant.level} rồi!"
        } else {
            "$speciesName nhận thêm ánh nắng và lớn nhanh hơn rồi!"
        }
        return updated.toUiState(message)
    }

    @Synchronized
    fun onLearningEvent(event: LearningEvent) {
        val snapshot = loadSnapshot().ensureCurrentTasks()
        val updated = when (event) {
            is LearningEvent.EmotionLessonCompleted -> recordEmotionLesson(snapshot, event.emotionId)
            is LearningEvent.GameCompleted -> recordGameCompleted(snapshot, event.gameId, event.emotionId)
            is LearningEvent.CameraChallengeCompleted -> {
                if (event.success) recordCameraChallenge(snapshot, event.emotionId) else snapshot
            }
            is LearningEvent.PhotoBoothSaved -> recordPhotoBooth(snapshot, event.emotionIds)
            LearningEvent.ReportSentToParent -> recordReportSent(snapshot)
        }
        saveSnapshot(updated.ensureCurrentTasks())
    }

    private fun recordEmotionLesson(snapshot: GardenSnapshot, emotionId: String): GardenSnapshot {
        val emotion = normalizeEmotionId(emotionId)
        val eventKey = "lesson_completed:v2:${todayKey()}:$emotion"
        if (eventKey in snapshot.eventKeys) return snapshot
        return snapshot
            .addEventKey(eventKey)
            .addGrowth(emotion, 2)
            .advanceTask(GardenTaskType.LEARN_EMOTION, GardenTaskPeriod.DAILY, progressKey = eventKey)
            .advanceTask(GardenTaskType.LEARN_EMOTION, GardenTaskPeriod.WEEKLY, progressKey = "lesson_completed:v2:${weekKey()}:$emotion")
    }

    private fun recordGameCompleted(snapshot: GardenSnapshot, gameId: String, emotionId: String?): GardenSnapshot {
        val emotion = emotionId?.let(::normalizeEmotionId)
        val safeGameId = gameId.ifBlank { "game" }
        val eventKey = "game_completed:v2:${todayKey()}:$safeGameId:${System.currentTimeMillis()}"
        if (eventKey in snapshot.eventKeys) return snapshot
        var next = snapshot
            .addEventKey(eventKey)
            .advanceTask(GardenTaskType.PLAY_GAME, GardenTaskPeriod.DAILY, progressKey = eventKey)
            .advanceTask(GardenTaskType.PLAY_GAME, GardenTaskPeriod.WEEKLY, progressKey = eventKey)
        if (emotion != null) next = next.addGrowth(emotion, 2)
        return next
    }

    private fun recordCameraChallenge(snapshot: GardenSnapshot, emotionId: String): GardenSnapshot {
        val emotion = normalizeEmotionId(emotionId)
        val eventKey = "camera:${todayKey()}:$emotion"
        if (eventKey in snapshot.eventKeys) return snapshot
        return snapshot
            .addEventKey(eventKey)
            .addGrowth(emotion, 3)
            .advanceTask(GardenTaskType.COMPLETE_CAMERA_CHALLENGE, GardenTaskPeriod.DAILY, progressKey = "camera")
            .advanceTask(GardenTaskType.PLAY_GAME, GardenTaskPeriod.DAILY, progressKey = "game_completed:v2:$eventKey")
            .advanceTask(GardenTaskType.PLAY_GAME, GardenTaskPeriod.WEEKLY, progressKey = "game_completed:v2:$eventKey")
    }

    private fun recordPhotoBooth(snapshot: GardenSnapshot, emotionIds: List<String>): GardenSnapshot {
        val eventKey = "photobooth:${todayKey()}"
        if (eventKey in snapshot.eventKeys) return snapshot
        return emotionIds.map(::normalizeEmotionId).distinct().fold(
            snapshot.addEventKey(eventKey)
        ) { current, emotion -> current.addGrowth(emotion, 1) }
            .advanceTask(GardenTaskType.CREATE_PHOTOBOOTH, GardenTaskPeriod.DAILY, progressKey = "photobooth")
    }

    private fun recordReportSent(snapshot: GardenSnapshot): GardenSnapshot {
        val eventKey = "report:${weekKey()}"
        if (eventKey in snapshot.eventKeys) return snapshot
        return snapshot
            .addEventKey(eventKey)
            .advanceTask(GardenTaskType.SEND_REPORT, GardenTaskPeriod.WEEKLY, progressKey = "report")
    }

    private fun loadSnapshot(): GardenSnapshot {
        val raw = preferences.getString(stateKey(), null)
        val parsed = raw?.let { runCatching { parseSnapshot(JSONObject(it)) }.getOrNull() }
        return parsed ?: GardenSnapshot(
            plants = EmotionIds.map(::initialPlant),
            inventory = GardenInventory(),
            tasks = emptyList(),
            eventKeys = emptySet(),
            lastCheckInDate = null,
            streakDays = 0
        )
    }

    private fun saveSnapshot(snapshot: GardenSnapshot) {
        preferences.edit().putString(stateKey(), snapshot.toJson().toString()).apply()
    }

    private fun stateKey(): String = "garden_state_${currentUserId()}"

    private fun currentUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid
            ?: AppSession.currentBackendUserId()
            ?: AppSession.getBackendUserId(context.applicationContext)
            ?: "local-player"
    }

    private fun GardenSnapshot.ensureCurrentTasks(): GardenSnapshot {
        val today = todayKey()
        val week = weekKey()
        val activeTasks = tasks.filter { task ->
            (task.period == GardenTaskPeriod.DAILY && task.dateKey == today) ||
                (task.period == GardenTaskPeriod.WEEKLY && task.weekKey == week)
        }
        val byId = activeTasks.associateBy { it.id }
        val generated = dailyTasks(today) + weeklyTasks(today, week)
        val merged = generated.map { task ->
            (byId[task.id] ?: task).resetLegacyAutoCompletedTask()
        }
        return copy(
            plants = EmotionIds.map { emotion -> plants.firstOrNull { it.emotionId == emotion } ?: initialPlant(emotion) },
            tasks = merged,
            eventKeys = eventKeys.filterNot { it.isBlank() }.toSet()
        )
    }

    private fun GardenTask.resetLegacyAutoCompletedTask(): GardenTask {
        if (status == GardenTaskStatus.NOT_STARTED) return this
        val usesOldCompletionKey = when (type) {
            GardenTaskType.LEARN_EMOTION -> progressKeys.isEmpty() || progressKeys.any { !it.startsWith("lesson_completed:v2:") }
            GardenTaskType.PLAY_GAME -> progressKeys.isEmpty() || progressKeys.any { !it.startsWith("game_completed:v2:") }
            else -> false
        }
        if (!usesOldCompletionKey) return this
        return copy(
            progress = 0,
            status = GardenTaskStatus.NOT_STARTED,
            progressKeys = emptyList()
        )
    }

    private fun GardenSnapshot.claimTaskInternal(taskId: String): GardenSnapshot {
        val task = tasks.firstOrNull { it.id == taskId } ?: return this
        if (task.status != GardenTaskStatus.COMPLETED_NOT_CLAIMED) return this
        var updated = copy(
            inventory = inventory.copy(
                water = inventory.water + task.reward.water,
                sunlight = inventory.sunlight + task.reward.sunlight,
                seeds = inventory.seeds + task.reward.seeds,
                emotionStars = inventory.emotionStars + task.reward.emotionStars
            ),
            tasks = tasks.replaceTask(task.copy(status = GardenTaskStatus.CLAIMED))
        )
        val relatedEmotion = task.relatedEmotionId
        if (!relatedEmotion.isNullOrBlank() && task.reward.growthPoints > 0) {
            updated = updated.addGrowth(relatedEmotion, task.reward.growthPoints)
        }
        return updated
    }

    private fun GardenSnapshot.advanceTask(
        type: GardenTaskType,
        period: GardenTaskPeriod,
        progressKey: String
    ): GardenSnapshot {
        val key = if (period == GardenTaskPeriod.DAILY) todayKey() else weekKey()
        val targetTask = tasks.firstOrNull {
            it.type == type &&
                it.period == period &&
                ((period == GardenTaskPeriod.DAILY && it.dateKey == key) ||
                    (period == GardenTaskPeriod.WEEKLY && it.weekKey == key))
        } ?: return this
        if (targetTask.status == GardenTaskStatus.CLAIMED || progressKey in targetTask.progressKeys) return this

        val nextKeys = (targetTask.progressKeys + progressKey).distinct()
        val nextProgress = nextKeys.size.coerceAtMost(targetTask.target)
        val nextStatus = if (nextProgress >= targetTask.target) {
            GardenTaskStatus.COMPLETED_NOT_CLAIMED
        } else {
            GardenTaskStatus.IN_PROGRESS
        }
        return copy(
            tasks = tasks.replaceTask(
                targetTask.copy(
                    progress = nextProgress,
                    status = nextStatus,
                    progressKeys = nextKeys
                )
            )
        )
    }

    private fun GardenSnapshot.addGrowth(emotionId: String, points: Int): GardenSnapshot {
        val normalized = normalizeEmotionId(emotionId)
        val plant = plants.firstOrNull { it.emotionId == normalized } ?: initialPlant(normalized)
        return copy(plants = plants.replacePlant(plant.addGrowth(points)))
    }

    private fun GardenSnapshot.addEventKey(key: String): GardenSnapshot = copy(eventKeys = eventKeys + key)

    private fun EmotionPlant.addGrowth(points: Int, caredAt: Long? = lastWateredAtMs): EmotionPlant {
        if (level >= MaxPlantLevel || points <= 0) {
            return copy(lastWateredAtMs = caredAt)
        }
        var nextLevel = level
        var nextGrowth = growthPoints + points
        var nextTotal = totalGrowthPoints + points
        while (nextLevel < MaxPlantLevel && nextGrowth >= pointsRequiredForLevel(nextLevel)) {
            nextGrowth -= pointsRequiredForLevel(nextLevel)
            nextLevel += 1
        }
        if (nextLevel >= MaxPlantLevel) nextGrowth = 0
        return copy(
            level = nextLevel,
            growthPoints = nextGrowth.coerceAtLeast(0),
            pointsToNextLevel = if (nextLevel >= MaxPlantLevel) 0 else pointsRequiredForLevel(nextLevel),
            totalGrowthPoints = nextTotal.coerceAtLeast(totalGrowthPoints),
            lastWateredAtMs = caredAt
        )
    }

    private fun GardenSnapshot.toUiState(message: String? = null): GardenUiState {
        val levelSum = plants.sumOf { it.level }
        val totalMaxGrowth = EmotionIds.size * TotalGrowthPerPlant
        val currentGrowth = plants.sumOf { it.totalGrowthPoints.coerceIn(0, TotalGrowthPerPlant) }
        val rawProgressPercent = ((currentGrowth.toFloat() / totalMaxGrowth.toFloat()) * 100f)
            .roundToInt()
            .coerceIn(0, 100)
        val progressPercent = if (currentGrowth > 0 && rawProgressPercent == 0) 1 else rawProgressPercent
        return GardenUiState(
            isLoading = false,
            plants = plants.sortedBy { EmotionIds.indexOf(it.emotionId).takeIf { index -> index >= 0 } ?: 99 },
            inventory = inventory,
            dailyTasks = tasks.filter { it.period == GardenTaskPeriod.DAILY }.sortedWith(taskDisplayComparator()),
            weeklyTasks = tasks.filter { it.period == GardenTaskPeriod.WEEKLY }.sortedWith(taskDisplayComparator()),
            pendingRewardCount = tasks.count { it.status == GardenTaskStatus.COMPLETED_NOT_CLAIMED },
            gardenLevel = (levelSum / 6) + 1,
            gardenProgressPercent = progressPercent,
            streakDays = streakDays,
            message = message
        )
    }

    private fun initialPlant(emotionId: String) = EmotionPlant(
        emotionId = emotionId,
        level = 0,
        growthPoints = 0,
        pointsToNextLevel = pointsRequiredForLevel(0),
        totalGrowthPoints = 0,
        lastWateredAtMs = null
    )

    private fun dailyTasks(dateKey: String): List<GardenTask> {
        return listOf(
            GardenTask(
                id = dailyTaskId(GardenTaskType.DAILY_CHECK_IN, dateKey),
                type = GardenTaskType.DAILY_CHECK_IN,
                period = GardenTaskPeriod.DAILY,
                title = "Điểm danh chăm vườn",
                description = "Vào vườn hôm nay để tưới cây.",
                progress = 0,
                target = 1,
                status = GardenTaskStatus.NOT_STARTED,
                reward = GardenReward(water = 1),
                relatedEmotionId = null,
                dateKey = dateKey,
                weekKey = null
            ),
            GardenTask(
                id = dailyTaskId(GardenTaskType.LEARN_EMOTION, dateKey),
                type = GardenTaskType.LEARN_EMOTION,
                period = GardenTaskPeriod.DAILY,
                title = "Học 1 cảm xúc",
                description = "Học một bài cảm xúc bất kỳ.",
                progress = 0,
                target = 1,
                status = GardenTaskStatus.NOT_STARTED,
                reward = GardenReward(water = 2),
                relatedEmotionId = null,
                dateKey = dateKey,
                weekKey = null
            ),
            GardenTask(
                id = dailyTaskId(GardenTaskType.PLAY_GAME, dateKey),
                type = GardenTaskType.PLAY_GAME,
                period = GardenTaskPeriod.DAILY,
                title = "Chơi 1 trò chơi",
                description = "Chơi một trò chơi cảm xúc.",
                progress = 0,
                target = 1,
                status = GardenTaskStatus.NOT_STARTED,
                reward = GardenReward(water = 2),
                relatedEmotionId = null,
                dateKey = dateKey,
                weekKey = null
            ),
            GardenTask(
                id = dailyTaskId(GardenTaskType.COMPLETE_CAMERA_CHALLENGE, dateKey),
                type = GardenTaskType.COMPLETE_CAMERA_CHALLENGE,
                period = GardenTaskPeriod.DAILY,
                title = "Thể hiện cảm xúc",
                description = "Hoàn thành một thử thách camera.",
                progress = 0,
                target = 1,
                status = GardenTaskStatus.NOT_STARTED,
                reward = GardenReward(water = 3),
                relatedEmotionId = null,
                dateKey = dateKey,
                weekKey = null
            ),
            GardenTask(
                id = dailyTaskId(GardenTaskType.CREATE_PHOTOBOOTH, dateKey),
                type = GardenTaskType.CREATE_PHOTOBOOTH,
                period = GardenTaskPeriod.DAILY,
                title = "Chụp 1 bộ Photobooth",
                description = "Chụp một bộ ảnh cảm xúc.",
                progress = 0,
                target = 1,
                status = GardenTaskStatus.NOT_STARTED,
                reward = GardenReward(sunlight = 1),
                relatedEmotionId = null,
                dateKey = dateKey,
                weekKey = null
            )
        )
    }

    private fun weeklyTasks(dateKey: String, weekKey: String): List<GardenTask> {
        return listOf(
            GardenTask(
                id = weeklyTaskId(GardenTaskType.LEARN_EMOTION, weekKey),
                type = GardenTaskType.LEARN_EMOTION,
                period = GardenTaskPeriod.WEEKLY,
                title = "Học 3 cảm xúc khác nhau",
                description = "Mỗi cảm xúc khác nhau giúp vườn đa dạng hơn.",
                progress = 0,
                target = 3,
                status = GardenTaskStatus.NOT_STARTED,
                reward = GardenReward(sunlight = 3),
                relatedEmotionId = null,
                dateKey = dateKey,
                weekKey = weekKey
            ),
            GardenTask(
                id = weeklyTaskId(GardenTaskType.PLAY_GAME, weekKey),
                type = GardenTaskType.PLAY_GAME,
                period = GardenTaskPeriod.WEEKLY,
                title = "Chơi 5 lượt game",
                description = "Mỗi lượt chơi là một lần chăm vườn nhẹ nhàng.",
                progress = 0,
                target = 5,
                status = GardenTaskStatus.NOT_STARTED,
                reward = GardenReward(water = 5),
                relatedEmotionId = null,
                dateKey = dateKey,
                weekKey = weekKey
            ),
            GardenTask(
                id = weeklyTaskId(GardenTaskType.SEND_REPORT, weekKey),
                type = GardenTaskType.SEND_REPORT,
                period = GardenTaskPeriod.WEEKLY,
                title = "Gửi báo cáo cho bố mẹ",
                description = "Chia sẻ tiến bộ tuần này với bố mẹ.",
                progress = 0,
                target = 1,
                status = GardenTaskStatus.NOT_STARTED,
                reward = GardenReward(sunlight = 1),
                relatedEmotionId = null,
                dateKey = dateKey,
                weekKey = weekKey
            )
        )
    }

    private fun GardenReward.asText(): String {
        val parts = buildList {
            if (water > 0) add("$water giọt nước")
            if (sunlight > 0) add("$sunlight ánh nắng")
            if (seeds > 0) add("$seeds hạt giống")
            if (emotionStars > 0) add("$emotionStars sao cảm xúc")
            if (growthPoints > 0) add("$growthPoints điểm lớn lên")
        }
        return parts.joinToString(", ").ifBlank { "một chút năng lượng cho vườn" }
    }

    private fun parseSnapshot(json: JSONObject): GardenSnapshot {
        return GardenSnapshot(
            plants = (json.optJSONArray("plants") ?: json.optJSONArray("flowers")).toPlantList(),
            inventory = json.optJSONObject("inventory").toInventory(),
            tasks = json.optJSONArray("tasks").toTaskList(),
            eventKeys = json.optJSONArray("event_keys").toStringSet(),
            lastCheckInDate = json.optString("last_check_in_date").takeIf { it.isNotBlank() },
            streakDays = json.optInt("streak_days", 0).coerceAtLeast(0)
        )
    }

    private fun GardenSnapshot.toJson(): JSONObject {
        return JSONObject().apply {
            put("plants", JSONArray().also { array -> plants.forEach { array.put(it.toJson()) } })
            put("inventory", inventory.toJson())
            put("tasks", JSONArray().also { array -> tasks.forEach { array.put(it.toJson()) } })
            put("event_keys", JSONArray().also { array -> eventKeys.forEach { array.put(it) } })
            put("last_check_in_date", lastCheckInDate.orEmpty())
            put("streak_days", streakDays)
        }
    }

    private fun EmotionPlant.toJson() = JSONObject().apply {
        put("emotion_id", emotionId)
        put("level", level)
        put("growth_points", growthPoints)
        put("points_to_next_level", pointsToNextLevel)
        put("total_growth_points", totalGrowthPoints)
        put("last_watered_at_ms", lastWateredAtMs ?: 0L)
    }

    private fun GardenInventory.toJson() = JSONObject().apply {
        put("water", water)
        put("sunlight", sunlight)
        put("seeds", seeds)
        put("emotion_stars", emotionStars)
    }

    private fun GardenTask.toJson() = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("period", period.name)
        put("title", title)
        put("description", description)
        put("progress", progress)
        put("target", target)
        put("status", status.name)
        put("reward", reward.toJson())
        put("related_emotion_id", relatedEmotionId.orEmpty())
        put("date_key", dateKey)
        put("week_key", weekKey.orEmpty())
        put("progress_keys", JSONArray().also { array -> progressKeys.forEach { array.put(it) } })
    }

    private fun GardenReward.toJson() = JSONObject().apply {
        put("water", water)
        put("sunlight", sunlight)
        put("seeds", seeds)
        put("emotion_stars", emotionStars)
        put("growth_points", growthPoints)
    }

    private fun JSONArray?.toPlantList(): List<EmotionPlant> {
        if (this == null) return EmotionIds.map(::initialPlant)
        return (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let { json ->
                val emotion = normalizeEmotionId(json.optString("emotion_id"))
                val level = json.optInt("level", 0).coerceIn(0, MaxPlantLevel)
                EmotionPlant(
                    emotionId = emotion,
                    level = level,
                    growthPoints = json.optInt("growth_points", 0).coerceAtLeast(0),
                    pointsToNextLevel = if (level >= MaxPlantLevel) 0 else pointsRequiredForLevel(level),
                    totalGrowthPoints = json.optInt("total_growth_points", 0).coerceAtLeast(0),
                    lastWateredAtMs = json.optLong("last_watered_at_ms", 0L).takeIf { it > 0L }
                )
            }
        }.ifEmpty { EmotionIds.map(::initialPlant) }
    }

    private fun JSONObject?.toInventory(): GardenInventory {
        if (this == null) return GardenInventory()
        return GardenInventory(
            water = optInt("water", 0).coerceAtLeast(0),
            sunlight = optInt("sunlight", 0).coerceAtLeast(0),
            seeds = optInt("seeds", 0).coerceAtLeast(0),
            emotionStars = optInt("emotion_stars", 0).coerceAtLeast(0)
        )
    }

    private fun JSONArray?.toTaskList(): List<GardenTask> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let { json ->
                val type = runCatching { GardenTaskType.valueOf(json.optString("type")) }.getOrNull() ?: return@let null
                val period = runCatching { GardenTaskPeriod.valueOf(json.optString("period")) }.getOrNull() ?: return@let null
                val status = runCatching { GardenTaskStatus.valueOf(json.optString("status")) }.getOrDefault(GardenTaskStatus.NOT_STARTED)
                GardenTask(
                    id = json.optString("id"),
                    type = type,
                    period = period,
                    title = json.optString("title"),
                    description = json.optString("description"),
                    progress = json.optInt("progress", 0).coerceAtLeast(0),
                    target = json.optInt("target", 1).coerceAtLeast(1),
                    status = status,
                    reward = json.optJSONObject("reward").toReward(),
                    relatedEmotionId = json.optString("related_emotion_id").takeIf { it.isNotBlank() },
                    dateKey = json.optString("date_key"),
                    weekKey = json.optString("week_key").takeIf { it.isNotBlank() },
                    progressKeys = json.optJSONArray("progress_keys").toStringList()
                )
            }
        }
    }

    private fun JSONObject?.toReward(): GardenReward {
        if (this == null) return GardenReward()
        return GardenReward(
            water = optInt("water", 0).coerceAtLeast(0),
            sunlight = optInt("sunlight", 0).coerceAtLeast(0),
            seeds = optInt("seeds", 0).coerceAtLeast(0),
            emotionStars = optInt("emotion_stars", 0).coerceAtLeast(0),
            growthPoints = optInt("growth_points", 0).coerceAtLeast(0)
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { value -> value.isNotBlank() } }
    }

    private fun JSONArray?.toStringSet(): Set<String> = toStringList().toSet()

    private fun List<GardenTask>.replaceTask(task: GardenTask): List<GardenTask> = map { if (it.id == task.id) task else it }

    private fun List<EmotionPlant>.replacePlant(plant: EmotionPlant): List<EmotionPlant> {
        val replaced = map { if (it.emotionId == plant.emotionId) plant else it }
        return if (replaced.any { it.emotionId == plant.emotionId }) replaced else replaced + plant
    }

    private fun dailyTaskId(type: GardenTaskType, dateKey: String): String = "daily_${type.name.lowercase(Locale.US)}_$dateKey"

    private fun weeklyTaskId(type: GardenTaskType, weekKey: String): String = "weekly_${type.name.lowercase(Locale.US)}_$weekKey"

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)

    private fun yesterdayKey(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }

    private fun weekKey(): String {
        val calendar = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
        }
        val year = calendar.get(Calendar.YEAR)
        val week = calendar.get(Calendar.WEEK_OF_YEAR)
        return "%04d-W%02d".format(Locale.US, year, week)
    }

    private data class GardenSnapshot(
        val plants: List<EmotionPlant>,
        val inventory: GardenInventory,
        val tasks: List<GardenTask>,
        val eventKeys: Set<String>,
        val lastCheckInDate: String?,
        val streakDays: Int
    )

    companion object {
        private const val PrefName = "emotion_garden_state"
        private const val MaxPlantLevel = 5
        private const val TotalGrowthPerPlant = 80
        private const val SunlightGrowthBoost = 3
        val EmotionIds = listOf("happy", "sad", "angry", "fear", "surprise", "disgust")

        fun pointsRequiredForLevel(level: Int): Int {
            return when (level.coerceIn(0, MaxPlantLevel)) {
                0 -> 5
                1 -> 10
                2 -> 15
                3 -> 20
                4 -> 30
                else -> 0
            }
        }

        fun normalizeEmotionId(value: String): String {
            val lower = value.trim().lowercase(Locale.ROOT)
            return when {
                "happy" in lower || "vui" in lower -> "happy"
                "sad" in lower || "buon" in lower || "buồn" in lower -> "sad"
                "angry" in lower || "tuc" in lower || "tức" in lower || "gian" in lower || "giận" in lower -> "angry"
                "fear" in lower || "so" in lower || "sợ" in lower -> "fear"
                "surprise" in lower || "ngac" in lower || "ngạc" in lower -> "surprise"
                "disgust" in lower || "ghe" in lower || "ghê" in lower -> "disgust"
                else -> lower.takeIf { it in EmotionIds } ?: "happy"
            }
        }

        fun emotionName(emotionId: String): String {
            return when (normalizeEmotionId(emotionId)) {
                "happy" -> "Vui vẻ"
                "sad" -> "Buồn bã"
                "angry" -> "Tức giận"
                "fear" -> "Sợ hãi"
                "surprise" -> "Ngạc nhiên"
                "disgust" -> "Ghê tởm"
                else -> emotionId
            }
        }

        fun plantSpecies(emotionId: String): EmotionPlantSpecies {
            val emotion = normalizeEmotionId(emotionId)
            return PlantSpeciesByEmotion[emotion] ?: PlantSpeciesByEmotion.getValue("happy")
        }

        fun plantSpeciesName(emotionId: String): String = plantSpecies(emotionId).speciesName

        fun plantStageName(emotionId: String, level: Int): String {
            val species = plantSpecies(emotionId)
            val normalizedLevel = level.coerceIn(0, MaxPlantLevel)
            return species.stages.firstOrNull { it.level == normalizedLevel }?.stageName
                ?: species.stages.last().stageName
        }

        private val PlantSpeciesByEmotion = mapOf(
            "happy" to EmotionPlantSpecies(
                emotionId = "happy",
                speciesId = "sunflower",
                speciesName = "Hoa hướng dương",
                emotionDisplayName = "Vui vẻ",
                description = "Hoa hướng dương sáng, ấm áp và luôn hướng về điều tích cực.",
                stages = listOf(
                    PlantGrowthStage(0, "Hạt hướng dương", "sunflower_seed", "Một hạt nhỏ đang chờ nảy mầm."),
                    PlantGrowthStage(1, "Mầm hướng dương", "sunflower_sprout", "Mầm xanh nhỏ nhú lên khỏi đất."),
                    PlantGrowthStage(2, "Cây hướng dương non", "sunflower_young", "Thân nhỏ với hai chiếc lá xanh."),
                    PlantGrowthStage(3, "Cây hướng dương đang lớn", "sunflower_growing", "Thân cao hơn và nhiều lá hơn."),
                    PlantGrowthStage(4, "Nụ hướng dương", "sunflower_bud", "Nụ vàng nhỏ chuẩn bị nở."),
                    PlantGrowthStage(5, "Hoa hướng dương nở", "sunflower_bloom", "Bông hướng dương vàng nở rõ.")
                )
            ),
            "sad" to EmotionPlantSpecies(
                emotionId = "sad",
                speciesId = "blue_willow",
                speciesName = "Cây liễu xanh",
                emotionDisplayName = "Buồn bã",
                description = "Cây liễu xanh có tán lá rủ mềm, dịu và bình yên.",
                stages = listOf(
                    PlantGrowthStage(0, "Hạt liễu xanh", "willow_seed", "Hạt xanh nhạt nhỏ nằm trên đất."),
                    PlantGrowthStage(1, "Mầm liễu", "willow_sprout", "Mầm xanh dịu bắt đầu lớn."),
                    PlantGrowthStage(2, "Cây liễu non", "willow_young", "Thân nhỏ với vài lá mềm."),
                    PlantGrowthStage(3, "Cây liễu đang lớn", "willow_growing", "Lá bắt đầu rủ nhẹ."),
                    PlantGrowthStage(4, "Cây liễu rủ lá", "willow_drooping", "Tán lá xanh rủ xuống rõ hơn."),
                    PlantGrowthStage(5, "Cây liễu trưởng thành", "willow_mature", "Cây liễu trưởng thành với tán lá rủ mềm.")
                )
            ),
            "angry" to EmotionPlantSpecies(
                emotionId = "angry",
                speciesId = "chili_plant",
                speciesName = "Cây ớt đỏ",
                emotionDisplayName = "Tức giận",
                description = "Cây ớt đỏ nhỏ nhắn, mạnh mẽ và đầy năng lượng.",
                stages = listOf(
                    PlantGrowthStage(0, "Hạt ớt đỏ", "chili_seed", "Hạt nhỏ màu cam đỏ."),
                    PlantGrowthStage(1, "Mầm ớt", "chili_sprout", "Mầm nhỏ hơi xanh cam."),
                    PlantGrowthStage(2, "Cây ớt non", "chili_young", "Thân nhỏ với vài lá xanh."),
                    PlantGrowthStage(3, "Cây ớt đang lớn", "chili_growing", "Cây cao hơn và nhiều lá hơn."),
                    PlantGrowthStage(4, "Cây ớt ra quả", "chili_fruiting", "Những quả ớt nhỏ bắt đầu xuất hiện."),
                    PlantGrowthStage(5, "Cây ớt đỏ trưởng thành", "chili_mature", "Cây ớt có vài quả ớt đỏ dễ thương.")
                )
            ),
            "fear" to EmotionPlantSpecies(
                emotionId = "fear",
                speciesId = "shy_plant",
                speciesName = "Cây xấu hổ",
                emotionDisplayName = "Sợ hãi",
                description = "Cây xấu hổ khép lá khi cần được che chở.",
                stages = listOf(
                    PlantGrowthStage(0, "Hạt cây xấu hổ", "shy_seed", "Hạt tím xanh nhỏ trên đất."),
                    PlantGrowthStage(1, "Mầm xấu hổ", "shy_sprout", "Mầm nhỏ hơi rụt rè."),
                    PlantGrowthStage(2, "Cây xấu hổ non", "shy_young", "Cây nhỏ có vài cặp lá."),
                    PlantGrowthStage(3, "Cây xấu hổ đang lớn", "shy_growing", "Nhiều lá xếp đối xứng hơn."),
                    PlantGrowthStage(4, "Cây xấu hổ khép lá", "shy_closing", "Lá bắt đầu khép nhẹ."),
                    PlantGrowthStage(5, "Cây xấu hổ trưởng thành", "shy_mature", "Cây xấu hổ trưởng thành với nhiều lá nhỏ.")
                )
            ),
            "surprise" to EmotionPlantSpecies(
                emotionId = "surprise",
                speciesId = "surprise_tulip",
                speciesName = "Hoa tulip bất ngờ",
                emotionDisplayName = "Ngạc nhiên",
                description = "Tulip bung mở nhẹ như một điều bất ngờ dễ thương.",
                stages = listOf(
                    PlantGrowthStage(0, "Hạt/củ tulip", "tulip_seed", "Củ tulip nhỏ đang ngủ yên."),
                    PlantGrowthStage(1, "Mầm tulip", "tulip_sprout", "Mầm tulip bật lên khỏi đất."),
                    PlantGrowthStage(2, "Cây tulip non", "tulip_young", "Lá dài nhỏ bắt đầu rõ hơn."),
                    PlantGrowthStage(3, "Tulip đang lớn", "tulip_growing", "Thân và lá vươn cao hơn."),
                    PlantGrowthStage(4, "Nụ tulip", "tulip_bud", "Nụ tulip chuẩn bị nở."),
                    PlantGrowthStage(5, "Hoa tulip nở", "tulip_bloom", "Tulip nở bung nhẹ.")
                )
            ),
            "disgust" to EmotionPlantSpecies(
                emotionId = "disgust",
                speciesId = "pitcher_plant",
                speciesName = "Cây nắp ấm",
                emotionDisplayName = "Ghê tởm",
                description = "Cây nắp ấm có dáng lạ nhưng vẫn thật đáng yêu.",
                stages = listOf(
                    PlantGrowthStage(0, "Hạt nắp ấm", "pitcher_seed", "Hạt xanh nhỏ nằm trên đất."),
                    PlantGrowthStage(1, "Mầm nắp ấm", "pitcher_sprout", "Mầm xanh bắt đầu nhú lên."),
                    PlantGrowthStage(2, "Cây nắp ấm non", "pitcher_young", "Cây nhỏ có lá xanh."),
                    PlantGrowthStage(3, "Cây nắp ấm đang lớn", "pitcher_growing", "Lá dài và xoắn nhẹ rõ hơn."),
                    PlantGrowthStage(4, "Cây nắp ấm ra ấm nhỏ", "pitcher_small", "Một chiếc ấm nhỏ xuất hiện."),
                    PlantGrowthStage(5, "Cây nắp ấm trưởng thành", "pitcher_mature", "Cây nắp ấm cute với vài chiếc ấm nhỏ.")
                )
            )
        )
    }

    private fun taskDisplayComparator(): Comparator<GardenTask> {
        return compareBy<GardenTask> { taskStatusRank(it.status) }
            .thenBy { taskTypeRank(it.type) }
    }

    private fun taskStatusRank(status: GardenTaskStatus): Int {
        return when (status) {
            GardenTaskStatus.COMPLETED_NOT_CLAIMED -> 0
            GardenTaskStatus.IN_PROGRESS -> 1
            GardenTaskStatus.NOT_STARTED -> 2
            GardenTaskStatus.CLAIMED -> 3
        }
    }

    private fun taskTypeRank(type: GardenTaskType): Int {
        return when (type) {
            GardenTaskType.DAILY_CHECK_IN -> 0
            GardenTaskType.LEARN_EMOTION -> 1
            GardenTaskType.PLAY_GAME -> 2
            GardenTaskType.COMPLETE_CAMERA_CHALLENGE -> 3
            GardenTaskType.CREATE_PHOTOBOOTH -> 4
            GardenTaskType.PRACTICE_WEAK_EMOTION -> 5
            GardenTaskType.SEND_REPORT -> 6
        }
    }
}
