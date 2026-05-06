package com.example.appmobile.ui.pages.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appmobile.R
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.EmoGardenBackground
import com.example.appmobile.ui.components.EmoGardenNavItem
import com.example.appmobile.ui.components.EmoGardenTopNav
import com.example.appmobile.ui.viewmodel.HomeEmotionUi
import com.example.appmobile.ui.viewmodel.HomeRecentGameUi
import com.example.appmobile.ui.viewmodel.HomeViewModel

@Composable
fun HomePage(
    onNavigateToGame: (String) -> Unit,
    onNavigateToLearn: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToLevel: (String) -> Unit = {},
    vm: HomeViewModel = viewModel()
) {
    val loading by vm.isLoading
    val errorMessage by vm.errorMessage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EmoGardenBackground)
    ) {
        EmoGardenTopNav(
            activeItem = EmoGardenNavItem.Home,
            onHome = {},
            onLearn = onNavigateToLearn,
            onGames = { onNavigateToGame("all") },
            onProfile = onNavigateToProfile,
            onSettings = onNavigateToSettings
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            errorMessage?.let { message ->
                ErrorBanner(message = message, onRetry = vm::refresh)
            }

            if (loading) {
                LoadingStrip("Đang tải dữ liệu trang chủ...")
            }

            EmotionAccuracySection(emotions = vm.emotions)

            RecentGamesSection(
                games = vm.recentGames,
                onOpenGame = { game ->
                    if (!game.id.isNullOrBlank()) {
                        onNavigateToLevel(game.id)
                    } else {
                        onNavigateToGame(gameCategory(game))
                    }
                }
            )

            ReportSection(onNavigateToReport = onNavigateToReport)
        }
    }
}

@Composable
private fun EmotionAccuracySection(emotions: List<HomeEmotionUi>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle("Tỉ lệ đúng của các cảm xúc", center = true)

        if (emotions.isEmpty()) {
            EmptyHomeCard("Chưa có dữ liệu độ chính xác. Bé hãy chơi vài câu hỏi để có thống kê nhé.")
            return
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            emotions.take(6).chunked(3).forEach { rowEmotions ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowEmotions.forEach { emotion ->
                        EmotionScoreCard(
                            emotion = emotion,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowEmotions.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmotionScoreCard(emotion: HomeEmotionUi, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF9CCFE4), Color(0xFF478FEE))
                    )
                )
                .padding(9.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = emotionIcon(emotion.name), fontSize = 25.sp)
                Text(
                    emotion.name,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatPercent(emotion.accuracy)}%",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp
                )
            }
        }
    }
}

@Composable
private fun RecentGamesSection(
    games: List<HomeRecentGameUi>,
    onOpenGame: (HomeRecentGameUi) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Trò chơi đã chơi gần đây")

        if (games.isEmpty()) {
            EmptyHomeCard("Chưa có trò chơi nào được chơi gần đây.")
            return
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(games.take(6)) { game ->
                RecentGameCard(game = game, onClick = { onOpenGame(game) })
            }
        }
    }
}

@Composable
private fun RecentGameCard(game: HomeRecentGameUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(142.dp)
            .height(132.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = gameImageRes(game)),
                contentDescription = game.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                text = game.name,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                color = Color(0xFF0B3C7D),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReportSection(onNavigateToReport: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 118.dp)
            .clickable(onClick = onNavigateToReport),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("📋", fontSize = 42.sp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Báo cáo tiến bộ", color = Color(0xFF0B3C7D), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text("Xem tổng kết luyện tập và gợi ý cảm xúc cần ôn thêm.", color = Color(0xFF52616F))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, center: Boolean = false) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 20.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color(0xFF0B3C7D),
        textAlign = if (center) TextAlign.Center else TextAlign.Start
    )
}

@Composable
private fun LoadingStrip(message: String) {
    Surface(shape = RoundedCornerShape(22.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF1976D2))
            Text(message, color = Color(0xFF6B7280))
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFFFEBEE), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, color = Color(0xFFC62828), modifier = Modifier.weight(1f), fontSize = 13.sp)
            TextButton(onClick = onRetry) { Text("Thử lại", color = Color(0xFF1976D2)) }
        }
    }
}

@Composable
private fun EmptyHomeCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = Color(0xFF6B7280),
            lineHeight = 20.sp
        )
    }
}

private fun gameImageRes(game: HomeRecentGameUi): Int {
    val key = normalizeGameKey(game.gameType ?: game.name)
    return when {
        game.id == GameUiCatalog.GAME_RECOGNIZE_EMOTION || key.contains("recognize") || key.contains("chiec") -> R.drawable.recognize_emotion
        game.id == GameUiCatalog.GAME_FACE_ASSEMBLY || key.contains("click2") || key.contains("lap") || key.contains("xuong") -> R.drawable.game_click_2
        game.id == GameUiCatalog.GAME_EMOTION_MATCH || key.contains("click3") || key.contains("dungcho") || key.contains("ai") -> R.drawable.game_click_3
        game.id == GameUiCatalog.GAME_DETECTIVE || key.contains("click4") || key.contains("tham") -> R.drawable.game_click_4
        game.id == GameUiCatalog.GAME_CV_REQUEST || key.contains("cv2") || key.contains("thu") -> R.drawable.game_cv_2
        game.id == GameUiCatalog.GAME_CV_STORY || key.contains("cv") -> R.drawable.game_cv
        else -> R.drawable.logo_emo
    }
}

private fun gameCategory(game: HomeRecentGameUi): String {
    val key = normalizeGameKey("${game.gameType.orEmpty()} ${game.name}")
    return if (key.contains("cv") || key.contains("camera") || key.contains("bieucam")) "camera_game" else "click_game"
}

private fun normalizeGameKey(value: String): String {
    return value
        .trim()
        .lowercase()
        .replace(" ", "")
        .replace("_", "")
        .replace("-", "")
}

private fun emotionIcon(name: String): String {
    val key = name.lowercase()
    return when {
        key.contains("vui") || key.contains("happy") -> "😊"
        key.contains("buồn") || key.contains("buon") || key.contains("sad") -> "😢"
        key.contains("tức") || key.contains("tuc") || key.contains("angry") -> "😠"
        key.contains("sợ") || key.contains("so") || key.contains("fear") -> "😨"
        key.contains("ngạc") || key.contains("ngac") || key.contains("surprise") -> "😲"
        key.contains("ghê") || key.contains("ghe") || key.contains("disgust") -> "🤢"
        else -> "🙂"
    }
}

private fun formatPercent(value: Double): String = String.format("%.1f", value)
