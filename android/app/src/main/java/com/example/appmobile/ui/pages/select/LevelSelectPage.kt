package com.example.appmobile.ui.pages.select

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.catalog.LevelUiItem
import com.example.appmobile.ui.components.GameScreenShell
import com.google.firebase.auth.FirebaseAuth

private data class LevelProgressUi(
    val level: LevelUiItem,
    val unlocked: Boolean,
    val completed: Boolean,
    val score: Int?
)

@Composable
fun LevelSelectPage(gameId: String, onBack: () -> Unit, onStartGame: (String) -> Unit) {
    val context = LocalContext.current
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "local-player" }
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
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

    GameScreenShell(contentMaxWidth = 520) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Text("←", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chọn cấp độ",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E4E8C)
                    )
                    Text(progressText, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoading) {
                Text("Đang tải tiến trình...", color = Color.Gray)
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
private fun LevelCard(state: LevelProgressUi, onStartGame: (String) -> Unit) {
    val level = state.level
    val containerColor = if (state.unlocked) Color.White else Color(0xFFF1F5F9)
    val titleColor = if (state.unlocked) Color(0xFF203864) else Color(0xFF94A3B8)
    val statusText = when {
        state.completed -> "Đã hoàn thành"
        state.unlocked -> "Có thể chơi"
        else -> "Đã khóa"
    }
    val statusColor = when {
        state.completed -> Color(0xFF2E7D32)
        state.unlocked -> Color(0xFF1E4E8C)
        else -> Color(0xFF94A3B8)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state.unlocked) { onStartGame(level.id.toString()) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (state.unlocked) 2.dp else 0.dp)
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
                    color = Color.Gray
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
                            color = Color.Gray
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
