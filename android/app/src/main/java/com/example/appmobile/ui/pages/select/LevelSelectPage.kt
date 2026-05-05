package com.example.appmobile.ui.pages.select

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

@Composable
fun LevelSelectPage(gameId: String, onBack: () -> Unit, onStartGame: (String) -> Unit) {
    val context = LocalContext.current
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }
    var levels by remember(gameId) { mutableStateOf(GameUiCatalog.levelsForGame(gameId)) }
    var isLoading by remember(gameId) { mutableStateOf(true) }

    LaunchedEffect(gameId) {
        isLoading = true
        val maxLevel = runCatching {
            repository.getGames()
                .firstOrNull { it.id == gameId }
                ?.level
                ?: 0
        }.getOrDefault(0)

        levels = GameUiCatalog.levelsForMaxLevel(maxLevel)
            .ifEmpty { GameUiCatalog.levelsForGame(gameId) }
        isLoading = false
    }

    GameScreenShell(contentMaxWidth = 500) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Text("←", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Chọn cấp độ",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E4E8C)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                Text("Đang tải cấp độ...", color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                levels.forEach { level ->
                    LevelCard(level, onStartGame)
                }
            }
        }
    }
}

@Composable
private fun LevelCard(level: LevelUiItem, onStartGame: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStartGame(level.id.toString()) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color(level.colorHex), MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = level.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = level.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Text("▶", color = Color.LightGray)
        }
    }
}
