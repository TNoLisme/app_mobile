package com.example.appmobile.ui.pages.select

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.appmobile.ui.components.EgCollapsibleMainScaffold
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgGradientPill
import com.example.appmobile.ui.components.EgSoftCard
import com.example.appmobile.ui.components.EgTab

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

    EgCollapsibleMainScaffold(
        activeTab = EgTab.Games,
        onHome = onGoHome,
        onLearn = onOpenLearn,
        onGames = {},
        onProfile = onOpenProfile,
        onSettings = onOpenSettings
    ) {
        categories.forEach { category ->
            GameCategorySection(category = category, onOpenLevel = onOpenLevel)
        }

        if (isLoading) {
            LoadingStrip("Đang tải danh sách game...")
        }
    }
}

@Composable
private fun LoadingStrip(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = EgDesign.card,
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = EgDesign.blue)
            Text(message, color = EgDesign.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun GameCategorySection(category: GameCategoryUi, onOpenLevel: (String) -> Unit) {
    EgSoftCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(category.icon, fontSize = 23.sp)
                Text(
                    category.title,
                    color = EgDesign.blue,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (category.games.isEmpty()) {
                Text("Chưa có game trong nhóm này.", color = EgDesign.textSecondary, fontSize = 14.sp)
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
            .height(248.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = game.imageRes),
                contentDescription = displayGameTitle(game),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 9.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    displayGameTitle(game),
                    color = EgDesign.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    displayGameDescription(game),
                    color = EgDesign.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.weight(1f))
                EgGradientPill(
                    text = displayGameActionText(game),
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    height = 40.dp,
                    fontSize = 12
                )
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
        GameCategoryUi("Trò chơi luyện cảm xúc", "🎮", "click_game", clickGames)
            .takeIf { showAll || type == it.type },
        GameCategoryUi("Trò chơi biểu cảm", "💻", "camera_game", cameraGames)
            .takeIf { showAll || type == it.type }
    )
}

private fun displayGameTitle(game: GameUiItem): String {
    return when (game.id) {
        GameUiCatalog.GAME_EMOTION_MATCH -> "Cảm xúc đúng chỗ"
        GameUiCatalog.GAME_RECOGNIZE_EMOTION -> "Chiếc hộp cảm xúc"
        GameUiCatalog.GAME_DETECTIVE -> "Thám tử cảm xúc"
        GameUiCatalog.GAME_FACE_ASSEMBLY -> "Xưởng lắp ghép"
        GameUiCatalog.GAME_CV_STORY -> "Câu chuyện khuôn mặt"
        GameUiCatalog.GAME_CV_REQUEST -> "Thử thách biểu cảm"
        else -> game.title
    }
}

private fun displayGameDescription(game: GameUiItem): String {
    return when (game.id) {
        GameUiCatalog.GAME_EMOTION_MATCH -> "Đặt cảm xúc vào đúng ngữ cảnh"
        GameUiCatalog.GAME_RECOGNIZE_EMOTION -> "Chọn cảm xúc đúng qua hình ảnh"
        GameUiCatalog.GAME_DETECTIVE -> "Tìm cảm xúc ẩn giấu trong tình huống"
        GameUiCatalog.GAME_FACE_ASSEMBLY -> "Ghép các bộ phận khuôn mặt"
        GameUiCatalog.GAME_CV_STORY -> "Bắt chước biểu cảm của nhân vật"
        GameUiCatalog.GAME_CV_REQUEST -> "Thể hiện biểu cảm theo yêu cầu"
        else -> game.description.ifBlank { "Chơi ngay!" }
    }
}

private fun displayGameActionText(game: GameUiItem): String {
    return when (game.id) {
        GameUiCatalog.GAME_CV_REQUEST -> "Chơi ngay"
        else -> "Chọn cấp độ"
    }
}
