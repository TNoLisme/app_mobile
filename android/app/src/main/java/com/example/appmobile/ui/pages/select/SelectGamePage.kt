package com.example.appmobile.ui.pages.select

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.catalog.GameUiItem
import com.example.appmobile.ui.components.EmoGardenBackground
import com.example.appmobile.ui.components.EmoGardenButtonGradient
import com.example.appmobile.ui.components.EmoGardenNavItem
import com.example.appmobile.ui.components.EmoGardenTopNav

private data class GameCategoryUi(
    val title: String,
    val icon: String,
    val type: String,
    val games: List<GameUiItem>
)

@Composable
fun SelectGamePage(
    type: String,
    onBack: () -> Unit,
    onOpenLevel: (String) -> Unit,
    onGoHome: () -> Unit = onBack,
    onOpenLearn: () -> Unit = {},
    onOpenProfile: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }
    var games by remember(type) { mutableStateOf(GameUiCatalog.games) }
    var isLoading by remember(type) { mutableStateOf(true) }
    val showAll = type.isBlank() || type == "all"

    LaunchedEffect(type) {
        isLoading = true
        val backendGames = runCatching {
            repository.getGames(if (showAll) null else type).map { game ->
                GameUiCatalog.gameFromBackend(
                    id = game.id,
                    title = game.name,
                    type = game.type,
                    maxLevel = game.level
                )
            }
        }.getOrDefault(emptyList())

        games = backendGames.ifEmpty {
            if (showAll) GameUiCatalog.games else GameUiCatalog.gamesByType(type)
        }
        isLoading = false
    }

    val categories = buildCategories(games, showAll, type)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EmoGardenBackground)
    ) {
        EmoGardenTopNav(
            activeItem = EmoGardenNavItem.Games,
            onHome = onGoHome,
            onLearn = onOpenLearn,
            onGames = {},
            onProfile = onOpenProfile,
            onSettings = onOpenSettings
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            GamePageHeader()

            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp, color = Color(0xFF1976D2))
                    Text("Đang tải danh sách game...", color = Color(0xFF6B7280))
                }
            }

            categories.forEach { category ->
                GameCategorySection(category = category, onOpenLevel = onOpenLevel)
            }
        }
    }
}

@Composable
private fun GamePageHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                "CHƠI GAME",
                color = Color(0xFF0B3C7D),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(3.dp)
                    .background(EmoGardenButtonGradient, CircleShape)
            )
            Text(
                "Chọn trò chơi, sau đó chọn level phù hợp để bé luyện cảm xúc.",
                color = Color(0xFF6B7280),
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun GameCategorySection(category: GameCategoryUi, onOpenLevel: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(category.icon, fontSize = 24.sp)
                Text(
                    category.title,
                    color = Color(0xFF1976D2),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(EmoGardenButtonGradient, CircleShape)
                )
            }

            if (category.games.isEmpty()) {
                Text("Chưa có game trong nhóm này.", color = Color(0xFF6B7280))
            } else {
                category.games.chunked(2).forEach { rowGames ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowGames.forEach { game ->
                            GameBlock(
                                game = game,
                                onClick = { onOpenLevel(game.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowGames.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameBlock(
    game: GameUiItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(218.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = game.imageRes),
                contentDescription = game.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    game.title,
                    color = Color(0xFF0B3C7D),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Chơi ngay!",
                    color = Color(0xFF6B7280),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .background(EmoGardenButtonGradient, CircleShape)
                            .padding(PaddingValues(vertical = 9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Chọn Level", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun buildCategories(games: List<GameUiItem>, showAll: Boolean, type: String): List<GameCategoryUi> {
    val clickGames = games.filter { it.type == "click_game" }
        .ifEmpty { if (showAll || type == "click_game") GameUiCatalog.gamesByType("click_game") else emptyList() }
    val cameraGames = games.filter { it.type == "camera_game" }
        .ifEmpty { if (showAll || type == "camera_game") GameUiCatalog.gamesByType("camera_game") else emptyList() }

    return listOfNotNull(
        GameCategoryUi("Game nhận biết", "🎮", "click_game", clickGames)
            .takeIf { showAll || type == it.type },
        GameCategoryUi("Game Biểu Cảm", "💻", "camera_game", cameraGames)
            .takeIf { showAll || type == it.type }
    )
}
