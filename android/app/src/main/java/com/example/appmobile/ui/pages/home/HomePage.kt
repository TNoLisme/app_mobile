package com.example.appmobile.ui.pages.home

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.appmobile.ui.viewmodel.HomeEmotionUi
import com.example.appmobile.ui.viewmodel.HomeRecentGameUi
import com.example.appmobile.ui.viewmodel.HomeViewModel

private val HomeBackgroundGradient = Brush.verticalGradient(
    listOf(
        Color(0xFFF4FBFF),
        Color(0xFFE6F5FD),
        Color(0xFFD9EEF9)
    )
)
private val HomePrimaryGradient = Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFF2563EB)))
private val HomeSoftGradient = Brush.linearGradient(listOf(Color(0xFF7DD3FC), Color(0xFF3B82F6)))
private val HomeCard = Color.White.copy(alpha = 0.96f)
private val HomeCardBorder = Color(0xFFDDEAF5)
private val HomeTextPrimary = Color(0xFF1F2937)
private val HomeTextSecondary = Color(0xFF6B7280)
private val HomeBlue = Color(0xFF0B66C3)
private val HomeRadiusCard = 18.dp
private val HomeRadiusPill = 999.dp
private val HomeScreenPadding = 16.dp

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
            .background(HomeBackgroundGradient)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HomeScreenPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeHeader(
            onProfile = onNavigateToProfile,
            onSettings = onNavigateToSettings
        )

        SegmentedTabs(
            onHome = {},
            onLearn = onNavigateToLearn,
            onGames = { onNavigateToGame("all") }
        )

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

        ProgressReportCard(onNavigateToReport = onNavigateToReport)
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun HomeHeader(
    onProfile: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderIconButton(icon = "👤", onClick = onProfile)
        Spacer(modifier = Modifier.weight(1f))
        HeaderIconButton(icon = "⚙️", onClick = onSettings)
    }
}

@Composable
private fun HeaderIconButton(icon: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = HomeCard,
        border = BorderStroke(1.dp, HomeCardBorder),
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = icon, fontSize = 19.sp)
        }
    }
}

@Composable
private fun SegmentedTabs(
    onHome: () -> Unit,
    onLearn: () -> Unit,
    onGames: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SegmentedTab(text = "Trang chủ", active = true, onClick = onHome, modifier = Modifier.weight(1f))
        SegmentedTab(text = "Học", active = false, onClick = onLearn, modifier = Modifier.weight(1f))
        SegmentedTab(text = "Chơi game", active = false, onClick = onGames, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SegmentedTab(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(HomeRadiusPill),
        color = Color.Transparent,
        border = if (active) null else BorderStroke(1.dp, HomeCardBorder),
        shadowElevation = if (active) 2.dp else 1.dp
    ) {
        Box(
            modifier = Modifier
                .background(if (active) HomePrimaryGradient else Brush.linearGradient(listOf(HomeCard, Color(0xFFF8FBFF))))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (active) Color.White else HomeBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmotionAccuracySection(emotions: List<HomeEmotionUi>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionTitle("Tỉ lệ đúng của các cảm xúc")

        if (emotions.isEmpty()) {
            EmptyHomeCard("Chưa có dữ liệu độ chính xác. Bé hãy chơi vài câu hỏi để có thống kê nhé.")
            return
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            emotions.take(6).chunked(3).forEach { rowEmotions ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowEmotions.forEach { emotion ->
                        EmotionStatCard(
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
private fun EmotionStatCard(emotion: HomeEmotionUi, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(106.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HomeSoftGradient)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = emotionIcon(emotion.name), fontSize = 25.sp)
            Text(
                emotion.name,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatPercent(emotion.accuracy)}%",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 21.sp,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun RecentGamesSection(
    games: List<HomeRecentGameUi>,
    onOpenGame: (HomeRecentGameUi) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            .width(148.dp)
            .height(132.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(1.dp, HomeCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = gameImageRes(game)),
                contentDescription = game.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = game.name,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                color = HomeTextPrimary,
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
private fun ProgressReportCard(onNavigateToReport: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .clickable(onClick = onNavigateToReport),
        shape = RoundedCornerShape(HomeRadiusCard),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(1.dp, HomeCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = Color(0xFFE8F7FF),
                border = BorderStroke(1.dp, Color(0xFFCDE7FA))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("📋", fontSize = 27.sp)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Báo cáo tiến bộ",
                    color = HomeBlue,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
                Text(
                    "Xem tổng kết luyện tập và gợi ý cảm xúc cần ôn thêm.",
                    color = HomeTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
            }
            Text("›", color = HomeBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold,
        color = HomeBlue,
        textAlign = TextAlign.Start
    )
}

@Composable
private fun LoadingStrip(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = HomeCard,
        border = BorderStroke(1.dp, HomeCardBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = HomeBlue)
            Text(message, color = HomeTextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFF8E7),
        border = BorderStroke(1.dp, Color(0xFFF9D26A)),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚠️", fontSize = 15.sp)
            Text(
                message,
                color = Color(0xFF8A4B00),
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onRetry, modifier = Modifier.height(32.dp)) {
                Text("Thử lại", color = HomeBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyHomeCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HomeRadiusCard),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(1.dp, HomeCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = HomeTextSecondary,
            fontSize = 14.sp,
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
