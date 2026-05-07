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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.appmobile.ui.components.egEmotionPastelColor
import com.example.appmobile.ui.components.egEmotionRouteValue
import com.google.firebase.auth.FirebaseAuth

private data class LevelProgressUi(
    val level: LevelUiItem,
    val unlocked: Boolean,
    val completed: Boolean,
    val score: Int?
)

private data class CvEmotionChoiceUi(
    val id: String,
    val displayName: String,
    val routeValue: String,
    val emoji: String,
    val progress: Float
)

@Composable
fun LevelSelectPage(
    gameId: String,
    onBack: () -> Unit,
    onStartGame: (String) -> Unit,
    onOpenAssistant: () -> Unit = {}
) {
    val context = LocalContext.current
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: AppSession.currentBackendUserId() ?: "local-player" }
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
    var isLoading by remember(gameId) { mutableStateOf(true) }
    var progressText by remember(gameId) { mutableStateOf("Level 1 đang mở") }

    LaunchedEffect(gameId, userId) {
        isLoading = true

        val maxLevel = runCatching {
            repository.getGames()
                .firstOrNull { it.id == gameId }
                ?.level
                ?: 0
        }.getOrDefault(0)

        val levels = GameUiCatalog.levelsForMaxLevel(maxLevel)
            .ifEmpty { GameUiCatalog.levelsForGame(gameId) }

        val progress = repository.getGameProgress(gameId, userId)
        val progressLevel = progress?.level ?: 0
        val progressScore = progress?.score ?: 0
        val completedCurrent = progressLevel > 0 && progressScore >= passThreshold(gameId, progressLevel)
        val unlockedLevel = when {
            progressLevel <= 0 -> 1
            completedCurrent -> progressLevel + 1
            else -> progressLevel
        }.coerceIn(1, levels.size.coerceAtLeast(1))

        levelStates = levels.map { level ->
            val score = if (level.id == progressLevel) progressScore else null
            val completed = level.id < unlockedLevel || (level.id == progressLevel && completedCurrent)
            LevelProgressUi(
                level = level,
                unlocked = level.id <= unlockedLevel,
                completed = completed,
                score = score
            )
        }
        progressText = "Level $unlockedLevel đang mở"
        isLoading = false
    }

    GameScreenShell(contentMaxWidth = 520, onOpenAssistant = onOpenAssistant) {
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

            if (isLoading) {
                Text("Đang tải tiến trình...", color = EgDesign.textSecondary)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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

    LaunchedEffect(userId) {
        isLoading = true
        scores = repository.getCvEmotionScores(userId)
        isLoading = false
    }

    val choices = remember(scores) {
        listOf("happy", "sad", "surprise", "angry", "fear", "disgust").map { id ->
            CvEmotionChoiceUi(
                id = id,
                displayName = egEmotionDisplayName(id),
                routeValue = egEmotionRouteValue(id),
                emoji = egEmotionIcon(id),
                progress = cvEmotionProgress(scores, id)
            )
        }
    }
    val selectedChoice = choices.firstOrNull { it.id == selectedEmotionId }
    val startButtonText = if (selectedChoice != null) "🚀 Bắt đầu" else "Chọn cảm xúc để bắt đầu"
    val selectedStatusText = selectedChoice?.let { "✨ Bạn đã chọn cảm xúc ${it.displayName} ✨" }
        ?: "Hãy chọn một cảm xúc để bắt đầu"

    GameScreenShell(contentMaxWidth = 560, onOpenAssistant = null) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                    if (false && isLoading) Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoading) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFFEAF7FF),
                                border = BorderStroke(1.dp, EgDesign.cardBorder)
                            ) {
                                Text(
                                    "Đang tải...",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = EgDesign.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
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

                    Text(
                        text = selectedStatusText,
                        color = if (selectedChoice != null) EgDesign.blue else EgDesign.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
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
private fun CvEmotionChoiceCard(
    choice: CvEmotionChoiceUi,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = choice.progress.coerceIn(0f, 100f)
    Card(
        modifier = modifier
            .height(118.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        border = BorderStroke(2.dp, if (selected) EgDesign.primaryDark else EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(egEmotionPastelColor(choice.id))
                .padding(8.dp)
        ) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd),
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, EgDesign.cardBorder)
            ) {
                Text(
                    text = "${progress.toInt()}%",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    color = EgDesign.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(choice.emoji, fontSize = 31.sp)
                Text(
                    text = choice.displayName,
                    color = EgDesign.textPrimary,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
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
        state.completed -> "Đã hoàn thành"
        state.unlocked -> "Có thể chơi"
        else -> "Đã khóa"
    }
    val statusColor = when {
        state.completed -> Color(0xFF2E7D32)
        state.unlocked -> EgDesign.blue
        else -> Color(0xFF94A3B8)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.medium, color = statusColor.copy(alpha = 0.12f)) {
                        Text(
                            statusText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    state.score?.let { score ->
                        Text(
                            "Điểm gần nhất: $score/100",
                            style = MaterialTheme.typography.labelSmall,
                            color = EgDesign.textSecondary
                        )
                    }
                }
            }
            Text(
                text = when {
                    state.completed -> "✓"
                    state.unlocked -> "▶"
                    else -> "🔒"
                },
                color = if (state.unlocked) Color(0xFF94A3B8) else Color(0xFFCBD5E1)
            )
        }
    }
}

private fun passThreshold(gameId: String, level: Int): Int {
    val gameType = GameUiCatalog.gameById(gameId)?.type
    if (gameType != "camera_game") return 70

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
