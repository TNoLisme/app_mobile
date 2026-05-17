package com.example.appmobile.ui.pages.select

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.catalog.LevelUiItem
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.GameScreenShell
import com.example.appmobile.ui.components.egEmotionDisplayName
import com.example.appmobile.ui.components.egEmotionIcon
import com.example.appmobile.ui.components.egEmotionKey
import com.example.appmobile.ui.components.egEmotionRouteValue
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val CvRequestHoldSeconds = 5
private const val CvStoryCheckpointPref = "cv_story_checkpoint"
private const val CvStoryCheckpointTtlMs = 24L * 60L * 60L * 1000L

private data class LevelProgressUi(
    val level: LevelUiItem,
    val unlocked: Boolean,
    val completed: Boolean,
    val score: Int?,
    val available: Boolean = true,
    val resumable: Boolean = false,
    val resumeProgressText: String? = null
)

private val LevelCardMinHeight = 126.dp

private data class CvEmotionChoiceUi(
    val id: String,
    val displayName: String,
    val routeValue: String,
    val emoji: String,
    val progress: Float
)

private data class CvStoryResumePreview(
    val answeredCount: Int,
    val totalCount: Int
)

@Composable
fun LevelSelectPage(
    gameId: String,
    onBack: () -> Unit,
    onStartGame: (String) -> Unit,
    onOpenAssistant: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val userId = remember {
        FirebaseAuth.getInstance().currentUser?.uid
            ?: AppSession.getBackendUserId(context)
            ?: AppSession.currentBackendUserId()
            ?: "local-player"
    }
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }

    if (gameId == GameUiCatalog.GAME_CV_REQUEST) {
        CvEmotionSelectPage(
            userId = userId,
            repository = repository,
            onBack = onBack,
            onStartGame = onStartGame
        )
        return
    }

    var levelStates by remember(gameId) {
        mutableStateOf(GameUiCatalog.levelsForGame(gameId).mapIndexed { index, level ->
            LevelProgressUi(level = level, unlocked = index == 0, completed = false, score = null)
        })
    }
    val hasCachedProgress = remember(gameId, userId) {
        if (gameId == GameUiCatalog.GAME_CV_STORY) {
            repository.peekCvCompletedLevels(userId) != null
        } else {
            repository.peekGameProgress(gameId = gameId, userId = userId) != null
        }
    }
    var isLoading by remember(gameId, userId) { mutableStateOf(false) }
    var progressText by remember(gameId) {
        mutableStateOf(
            if (gameId == GameUiCatalog.GAME_CV_STORY) {
                "Mỗi cấp độ có 5 tình huống cảm xúc."
            } else {
                "Cấp độ 1 đang mở"
            }
        )
    }

    suspend fun loadLevelProgress(forceRefresh: Boolean, showLoading: Boolean) {
        if (showLoading) {
            isLoading = true
        }

        if (gameId == GameUiCatalog.GAME_CV_STORY) {
            val levels = GameUiCatalog.levelsForGame(gameId)
            val completedLevels = repository.getCvCompletedLevels(userId = userId, forceRefresh = forceRefresh)
            val backendLevelsById = completedLevels?.levels.orEmpty().associateBy { it.level }
            val backendCurrentLevel = completedLevels?.currentLevel?.coerceIn(1, levels.size.coerceAtLeast(1)) ?: 1
            val resumeByLevel = levels.associate { level ->
                level.id to loadCvStoryResumePreview(
                    context = context,
                    userId = userId,
                    gameId = gameId,
                    level = level.id
                )
            }

            levelStates = levels.map { level ->
                val backendLevel = backendLevelsById[level.id]
                val resume = resumeByLevel[level.id]
                val unlocked = (backendLevel?.unlocked ?: (level.id <= backendCurrentLevel)) || resume != null
                val completed = backendLevel?.completed ?: (level.id < backendCurrentLevel)
                val score = backendLevel?.score
                LevelProgressUi(
                    level = level,
                    unlocked = unlocked,
                    completed = completed,
                    score = score,
                    available = true,
                    resumable = resume != null,
                    resumeProgressText = resume?.let {
                        "${it.answeredCount.coerceAtLeast(0)}/${it.totalCount.coerceAtLeast(1)} màn"
                    }
                )
            }
            progressText = "Mỗi cấp độ có 5 tình huống cảm xúc."
            isLoading = false
            return
        }

        val catalogMaxLevel = GameUiCatalog.gameById(gameId)?.maxLevel ?: 0
        val backendMaxLevel = runCatching {
            repository.getGames()
                .firstOrNull { it.id == gameId }
                ?.level
                ?: 0
        }.getOrDefault(0)
        val maxLevel = maxOf(backendMaxLevel, catalogMaxLevel)

        val levels = GameUiCatalog.levelsForMaxLevel(maxLevel)
            .ifEmpty { GameUiCatalog.levelsForGame(gameId) }

        val isClickGame = GameUiCatalog.isClickGame(gameId)
        val availabilityByLevel = if (isClickGame) {
            levels.associate { level ->
                level.id to (repository.getContentForLevel(gameId, level.id).size >= 5)
            }
        } else {
            levels.associate { it.id to true }
        }

        val progress = repository.getGameProgress(gameId, userId, forceRefresh = forceRefresh)
        val progressLevel = progress?.level ?: 0
        val progressScore = progress?.score ?: 0
        val completedCurrent = !isClickGame && progressLevel > 0 && progressScore >= passThreshold(gameId, progressLevel)
        val unlockedLevel = if (isClickGame) {
            (progress?.level ?: 1).coerceIn(1, levels.size.coerceAtLeast(1))
        } else {
            when {
                progressLevel <= 0 -> 1
                completedCurrent -> progressLevel + 1
                else -> progressLevel
            }.coerceIn(1, levels.size.coerceAtLeast(1))
        }

        levelStates = levels.map { level ->
            val score = if (level.id == progressLevel) progressScore else null
            val available = availabilityByLevel[level.id] ?: true
            val completed = if (isClickGame) level.id < unlockedLevel else level.id < unlockedLevel || (level.id == progressLevel && completedCurrent)
            LevelProgressUi(
                level = level,
                unlocked = level.id <= unlockedLevel && available,
                completed = completed,
                score = score,
                available = available
            )
        }
        progressText = if (gameId == GameUiCatalog.GAME_CV_STORY) {
            "Mỗi cấp độ có 5 tình huống cảm xúc."
        } else {
            "Cấp độ $unlockedLevel đang mở"
        }
        isLoading = false
    }

    LaunchedEffect(gameId, userId) {
        loadLevelProgress(forceRefresh = false, showLoading = !hasCachedProgress)
    }

    DisposableEffect(lifecycleOwner, gameId, userId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    loadLevelProgress(forceRefresh = true, showLoading = false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    GameScreenShell(
        contentMaxWidth = 520,
        onOpenAssistant = if (gameId == GameUiCatalog.GAME_CV_STORY) null else onOpenAssistant,
        scrollEnabled = true,
        bottomSpacerHeight = if (gameId == GameUiCatalog.GAME_CV_STORY) 0.dp else 96.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppBackButton(onClick = onBack); if (false) {
                    Text("←", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chọn cấp độ",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = EgDesign.textPrimary
                    )
                    Text(progressText, style = MaterialTheme.typography.bodySmall, color = EgDesign.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (isLoading) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFEAF7FF),
                        border = BorderStroke(1.dp, EgDesign.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = EgDesign.primary
                            )
                            Text(
                                text = "Đang cập nhật tiến trình...",
                                color = EgDesign.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                levelStates.forEach { state ->
                    LevelCard(state = state, onStartGame = onStartGame)
                }
            }
        }
    }
}

@Composable
private fun CvEmotionSelectPage(
    userId: String,
    repository: GameRepository,
    onBack: () -> Unit,
    onStartGame: (String) -> Unit
) {
    var scores by remember(userId) { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var selectedEmotionId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    fun refreshScores(showLoading: Boolean) {
        scope.launch {
            if (showLoading) isLoading = true
            if (scores.isEmpty()) {
                scores = repository.peekCvEmotionScores(userId)
            }
            val backendScores = repository.getCvEmotionScores(
                userId = userId,
                forceRefresh = !showLoading
            )
            scores = backendScores
            isLoading = false
        }
    }

    LaunchedEffect(userId) {
        refreshScores(showLoading = true)
    }

    DisposableEffect(lifecycleOwner, userId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshScores(showLoading = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val choices = remember(scores) {
        listOf("happy", "sad", "surprise", "angry", "fear", "disgust").map { id ->
            CvEmotionChoiceUi(
                id = id,
                displayName = cvChallengeEmotionLabel(id),
                routeValue = egEmotionRouteValue(id),
                emoji = egEmotionIcon(id),
                progress = cvEmotionProgress(scores, id)
            )
        }
    }
    val selectedChoice = choices.firstOrNull { it.id == selectedEmotionId }
    val startButtonText = "Bắt đầu thử thách"

    GameScreenShell(contentMaxWidth = 560, onOpenAssistant = null) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CvEmotionSelectHeader(onBack = onBack)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = EgDesign.card,
                border = BorderStroke(1.dp, EgDesign.cardBorder),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLoading) Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFEAF7FF),
                            border = BorderStroke(1.dp, EgDesign.cardBorder)
                        ) {
                            Text(
                                "Đang cập nhật tiến trình...",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = EgDesign.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    choices.chunked(3).forEach { rowChoices ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowChoices.forEach { choice ->
                                CvEmotionChoiceCard(
                                    choice = choice,
                                    selected = choice.id == selectedEmotionId,
                                    onClick = { selectedEmotionId = choice.id },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    SelectedEmotionMissionCard(choice = selectedChoice)

                    Button(
                        onClick = {
                            selectedChoice?.let { choice ->
                                onStartGame("1?emotion=${Uri.encode(choice.routeValue)}")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = selectedChoice != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color(0xFFE2E8F0)
                        ),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (selectedChoice != null) EgDesign.primary else Color(0xFFE2E8F0),
                                    RoundedCornerShape(999.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = startButtonText,
                                color = if (selectedChoice != null) Color.White else Color(0xFF94A3B8),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CvEmotionSelectHeader(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppBackButton(onClick = onBack)
        }
        Text(
            text = "Thử thách cảm xúc",
            modifier = Modifier.fillMaxWidth(),
            color = EgDesign.textPrimary,
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Chọn cảm xúc, rồi làm khuôn mặt giống vậy trước camera nhé.",
            modifier = Modifier.fillMaxWidth(),
            color = EgDesign.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CvEmotionSelectTopBar(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppBackButton(onClick = onBack)
            if (false) Surface(
                modifier = Modifier.clickable(onClick = onBack),
                shape = RoundedCornerShape(999.dp),
                color = Color.White,
                border = BorderStroke(1.dp, EgDesign.cardBorder),
                shadowElevation = 1.dp
            ) {
                Text(
                    text = "← Quay lại",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = EgDesign.blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Text(
            text = "🎮 Thử thách cảm xúc 🎮",
            modifier = Modifier.fillMaxWidth(),
            color = EgDesign.textPrimary,
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Chọn một cảm xúc để chơi",
            modifier = Modifier.fillMaxWidth(),
            color = EgDesign.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SelectedEmotionMissionCard(choice: CvEmotionChoiceUi?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Nhiệm vụ của bé",
                color = if (choice != null) EgDesign.textPrimary else EgDesign.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = choice?.let { cvChallengeEmotionMissionCompact(it.id, it.emoji) }
                    ?: "Chọn một cảm xúc để xem nhiệm vụ của bé.",
                color = EgDesign.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun CvEmotionChoiceCard(
    choice: CvEmotionChoiceUi,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = choice.progress.coerceIn(0f, 100f)
    val statusText = if (progress > 0f) "${progress.toInt()}%" else "Chưa luyện"
    Card(
        modifier = modifier
            .height(118.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFEAF7FF) else Color.White
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) EgDesign.primaryDark else Color(0xFFDCEBFA)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 3.dp else 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (selected) Color(0xFFEAF7FF) else Color.White)
                .padding(8.dp)
        ) {
            if (selected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp),
                    shape = CircleShape,
                    color = EgDesign.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(choice.emoji, fontSize = 32.sp)
                Box(
                    modifier = Modifier.height(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = choice.displayName,
                        color = EgDesign.textPrimary,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = statusText,
                    color = if (selected) EgDesign.blue else EgDesign.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

private fun cvChallengeEmotionLabel(id: String): String {
    return when (egEmotionKey(id)) {
        "happy" -> "Vui vẻ"
        "sad" -> "Buồn bã"
        "surprise" -> "Ngạc nhiên"
        "angry" -> "Tức giận"
        "fear" -> "Sợ hãi"
        "disgust" -> "Ghê tởm"
        else -> egEmotionDisplayName(id)
    }
}

private fun cvChallengeEmotionMission(id: String): String {
    return when (egEmotionKey(id)) {
        "happy" -> "Hãy cười thật tươi trước camera trong ${CvRequestHoldSeconds} giây."
        "sad" -> "Hãy làm khuôn mặt buồn trước camera trong ${CvRequestHoldSeconds} giây."
        "surprise" -> "Hãy mở mắt to và làm vẻ ngạc nhiên trong ${CvRequestHoldSeconds} giây."
        "angry" -> "Hãy nhíu mày như đang tức giận trong ${CvRequestHoldSeconds} giây."
        "fear" -> "Hãy làm khuôn mặt sợ hãi trước camera trong ${CvRequestHoldSeconds} giây."
        "disgust" -> "Hãy nhăn mũi như không thích mùi gì đó trong ${CvRequestHoldSeconds} giây."
        else -> "Hãy làm đúng biểu cảm trước camera trong ${CvRequestHoldSeconds} giây."
    }
}

private fun cvChallengeEmotionMissionCompact(id: String, emoji: String): String {
    return when (egEmotionKey(id)) {
        "happy" -> "Hãy làm khuôn mặt vui vẻ trong ${CvRequestHoldSeconds} giây $emoji"
        "sad" -> "Hãy làm khuôn mặt buồn trong ${CvRequestHoldSeconds} giây $emoji"
        "surprise" -> "Hãy làm khuôn mặt ngạc nhiên trong ${CvRequestHoldSeconds} giây $emoji"
        "angry" -> "Hãy làm khuôn mặt tức giận trong ${CvRequestHoldSeconds} giây $emoji"
        "fear" -> "Hãy làm khuôn mặt sợ hãi trong ${CvRequestHoldSeconds} giây $emoji"
        "disgust" -> "Hãy làm khuôn mặt ghê tởm trong ${CvRequestHoldSeconds} giây $emoji"
        else -> "Hãy làm khuôn mặt giống cảm xúc đã chọn trong ${CvRequestHoldSeconds} giây $emoji"
    }
}

@Composable
private fun LevelLoadingCard() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(5) { index ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = LevelCardMinHeight),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, EgDesign.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFFD9E6F2), MaterialTheme.shapes.small)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(18.dp)
                                .fillMaxWidth(0.35f)
                                .background(Color(0xFFEAF2F9), RoundedCornerShape(8.dp))
                        )
                        Box(
                            modifier = Modifier
                                .height(14.dp)
                                .fillMaxWidth(0.22f)
                                .background(Color(0xFFF1F6FB), RoundedCornerShape(7.dp))
                        )
                        if (index == 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = EgDesign.primary
                                )
                                Text(
                                    text = "Đang tải tiến trình...",
                                    color = EgDesign.textSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .height(22.dp)
                                    .fillMaxWidth(0.28f)
                                    .background(Color(0xFFF1F6FB), RoundedCornerShape(11.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .height(14.dp)
                                    .fillMaxWidth(0.62f)
                                    .background(Color(0xFFF1F6FB), RoundedCornerShape(7.dp))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .width(86.dp)
                            .background(Color(0xFFEAF2F9), RoundedCornerShape(18.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelCard(state: LevelProgressUi, onStartGame: (String) -> Unit) {
    val level = state.level
    val containerColor = if (state.unlocked) EgDesign.card else Color(0xFFF1F7FC)
    val titleColor = if (state.unlocked) EgDesign.textPrimary else Color(0xFF94A3B8)
    val statusText = when {
        !state.available -> "Đang cập nhật"
        state.resumable -> "Đang chơi dở"
        state.completed -> "Đã hoàn thành"
        state.unlocked -> "Có thể chơi"
        else -> "Đã khóa"
    }
    val statusColor = when {
        !state.available -> Color(0xFFB7791F)
        state.resumable -> Color(0xFF0369A1)
        state.completed -> Color(0xFF2E7D32)
        state.unlocked -> EgDesign.blue
        else -> Color(0xFF94A3B8)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LevelCardMinHeight)
            .clickable(enabled = state.unlocked) { onStartGame(level.id.toString()) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (state.unlocked) 1.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        if (state.unlocked) Color(level.colorHex) else Color(0xFFCBD5E1),
                        MaterialTheme.shapes.small
                    )
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = level.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor
                )
                Text(
                    text = level.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = EgDesign.textSecondary
                )
                Surface(shape = MaterialTheme.shapes.medium, color = statusColor.copy(alpha = 0.12f)) {
                    Text(
                        statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val progressText = when {
                    state.resumable && !state.resumeProgressText.isNullOrBlank() -> "Tiến trình: ${state.resumeProgressText}"
                    state.score != null -> "Điểm gần nhất: ${state.score}/100"
                    else -> null
                }
                progressText?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = EgDesign.textSecondary
                    )
                }
                if (!state.unlocked && state.available) {
                    Text(
                        text = "Hoàn thành cấp độ ${level.id - 1} để mở khóa.",
                        style = MaterialTheme.typography.labelSmall,
                        color = EgDesign.textSecondary
                    )
                }
            }
            if (state.unlocked) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = EgDesign.primary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = when {
                            state.resumable -> "Chơi tiếp"
                            state.completed -> "Chơi lại"
                            else -> "Bắt đầu"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = EgDesign.blue,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text("🔒", color = Color(0xFFCBD5E1))
            }
        }
    }
}

private fun cvStoryCheckpointKey(userId: String, gameId: String, level: Int): String {
    return "$userId::$gameId::$level"
}

private fun loadCvStoryResumePreview(
    context: android.content.Context,
    userId: String,
    gameId: String,
    level: Int
): CvStoryResumePreview? {
    val preferences = context.getSharedPreferences(CvStoryCheckpointPref, android.content.Context.MODE_PRIVATE)
    val key = cvStoryCheckpointKey(userId, gameId, level)
    val raw = preferences.getString(key, null) ?: return null
    return runCatching {
        val root = JSONObject(raw)
        val savedAtMs = root.optLong("saved_at_ms", 0L)
        if (savedAtMs <= 0L || System.currentTimeMillis() - savedAtMs > CvStoryCheckpointTtlMs) {
            preferences.edit().remove(key).apply()
            return null
        }
        val questions = root.optJSONArray("questions")?.length() ?: 0
        val results = root.optJSONArray("results")?.length() ?: 0
        if (questions <= 0 || results >= questions) {
            preferences.edit().remove(key).apply()
            return null
        }
        CvStoryResumePreview(
            answeredCount = results.coerceAtLeast(0),
            totalCount = questions.coerceAtLeast(1)
        )
    }.getOrNull()
}

private fun passThreshold(gameId: String, level: Int): Int {
    val gameType = GameUiCatalog.gameById(gameId)?.type
    if (gameType != "camera_game") return 30

    return when (level) {
        1 -> 40
        2 -> 50
        3 -> 60
        4 -> 70
        5 -> 80
        else -> 90
    }
}

private fun cvEmotionProgress(scores: Map<String, Float>, emotionId: String): Float {
    val targetKey = egEmotionKey(emotionId)
    return scores.entries
        .firstOrNull { (key, _) -> egEmotionKey(key) == targetKey }
        ?.value
        ?.coerceIn(0f, 100f)
        ?: 0f
}
