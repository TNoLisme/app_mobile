package com.example.appmobile.ui.pages.select

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.catalog.GameUiItem
import com.example.appmobile.ui.components.GameScreenShell

@Composable
fun SelectGamePage(
    type: String,
    onBack: () -> Unit,
    onOpenLevel: (String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }
    var filteredGames by remember(type) { mutableStateOf(GameUiCatalog.gamesByType(type)) }
    var isLoading by remember(type) { mutableStateOf(true) }
    val pageTitle = if (type == "camera_game") "Gương soi thông minh" else "Trò chơi nhận diện"

    LaunchedEffect(type) {
        isLoading = true
        val backendGames = runCatching {
            repository.getGames(type).map { game ->
                GameUiCatalog.gameFromBackend(
                    id = game.id,
                    title = game.name,
                    type = game.type,
                    maxLevel = game.level
                )
            }
        }.getOrDefault(emptyList())

        filteredGames = backendGames.ifEmpty { GameUiCatalog.gamesByType(type) }
        isLoading = false
    }

    GameScreenShell(contentMaxWidth = 600) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Text("←", style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    pageTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E4E8C)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            if (isLoading) {
                Text("Đang tải dữ liệu...", color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                filteredGames.forEach { game ->
                    GameListItem(game = game, onOpenLevel = onOpenLevel)
                }
            }
        }
    }
}

@Composable
private fun GameListItem(game: GameUiItem, onOpenLevel: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenLevel(game.id) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(70.dp),
                shape = MaterialTheme.shapes.large,
                color = Color(0xFFF0F7FF)
            ) {
                Image(
                    painter = painterResource(id = game.imageRes),
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(game.title, fontWeight = FontWeight.Bold, color = Color(0xFF203864))
                Text(game.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text("▶", color = Color.LightGray)
        }
    }
}
