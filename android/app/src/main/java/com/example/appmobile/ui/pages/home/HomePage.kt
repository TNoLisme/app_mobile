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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.appmobile.ui.theme.SoftWhite
import com.example.appmobile.ui.viewmodel.HomeEmotionUi
import com.example.appmobile.ui.viewmodel.HomeMetricUi
import com.example.appmobile.ui.viewmodel.HomeRecentGameUi
import com.example.appmobile.ui.viewmodel.HomeViewModel
import kotlin.math.abs

@Composable
fun HomePage(
    onLogout: () -> Unit,
    onNavigateToGame: (String) -> Unit,
    onNavigateToLearn: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToAssistant: () -> Unit = {},
    onNavigateToLevel: (String) -> Unit = {},
    vm: HomeViewModel = viewModel()
) {
    val childName by vm.childName
    val loading by vm.isLoading
    val errorMessage by vm.errorMessage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(SoftWhite, Color(0xFFE3F2FD), Color(0xFFBBDEFB))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        HomeTopBar(
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToAssistant = onNavigateToAssistant,
            onLogout = onLogout
        )

        HomeHero(
            childName = childName?.takeIf { it.isNotBlank() } ?: "bé yêu",
            onNavigateToLearn = onNavigateToLearn,
            onNavigateToGame = { onNavigateToGame("click_game") }
        )

        errorMessage?.let { message ->
            ErrorBanner(message = message, onRetry = vm::refresh)
        }

        if (loading) {
            LoadingCard("Đang tải dữ liệu trang chủ...")
        }

        SectionTitle("Tỉ lệ đúng của các cảm xúc")
        EmotionAccuracySection(emotions = vm.emotions)

        SectionTitle("Trò chơi đã chơi gần đây")
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

        SectionTitle("Khám phá cảm xúc cùng bạn nhỏ")
        LearningActions(
            onNavigateToLearn = onNavigateToLearn,
            onNavigateToGame = { onNavigateToGame("click_game") }
        )

        SectionTitle("Hành trình cảm xúc của bạn nhỏ")
        StatsSection(
            emotions = vm.emotions,
            improvements = vm.improvements,
            gameRatios = vm.gameRatios
        )

        SectionTitle("Báo cáo và tiến bộ")
        ReportCard(onClick = onNavigateToReport)
    }
}

@Composable
private fun HomeTopBar(
    onNavigateToProfile: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onLogout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Emo Garden",
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Emo Garden",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0B3C7D),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("Trang chủ", color = Color.Gray, fontSize = 13.sp)
                }
                TextButton(onClick = onLogout) { Text("Thoát", color = Color(0xFFC62828)) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onNavigateToProfile, modifier = Modifier.weight(1f)) {
                    Text("Hồ sơ")
                }
                OutlinedButton(onClick = onNavigateToAssistant, modifier = Modifier.weight(1f)) {
                    Text("Trợ lý")
                }
            }
        }
    }
}

@Composable
private fun HomeHero(
    childName: String,
    onNavigateToLearn: () -> Unit,
    onNavigateToGame: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color.White, Color(0xFFEAF6FF), Color(0xFFD6ECFF))
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.index_image),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(MaterialTheme.shapes.extraLarge),
                contentScale = ContentScale.Crop
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Chào $childName!",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0B3C7D)
                )
                Text(
                    text = "Hôm nay mình cùng học, chơi và theo dõi cảm xúc nhé.",
                    color = Color(0xFF52616F),
                    lineHeight = 20.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onNavigateToLearn, modifier = Modifier.weight(1f)) { Text("Bắt đầu học") }
                OutlinedButton(onClick = onNavigateToGame, modifier = Modifier.weight(1f)) { Text("Vào chơi") }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color(0xFF0B3C7D)
    )
}

@Composable
private fun EmotionAccuracySection(emotions: List<HomeEmotionUi>) {
    if (emotions.isEmpty()) {
        EmptyHomeCard("Chưa có dữ liệu về độ chính xác. Bé hãy chơi vài câu hỏi để có thống kê nhé.")
        return
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(emotions) { index, emotion ->
            EmotionAccuracyCard(rank = index + 1, emotion = emotion)
        }
    }
}

@Composable
private fun EmotionAccuracyCard(rank: Int, emotion: HomeEmotionUi) {
    val total = emotion.correct + emotion.incorrect
    Card(
        modifier = Modifier
            .width(180.dp)
            .height(190.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF4FACFE), Color(0xFF00C6FF))))
                .padding(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.24f),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = "#$rank",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = emotionIcon(emotion.name), fontSize = 44.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = emotion.name,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatPercent(emotion.accuracy)}%",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 30.sp
                )
                Text(
                    text = "Độ chính xác (${emotion.correct}/$total)",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
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
    if (games.isEmpty()) {
        EmptyHomeCard("Chưa có trò chơi nào được chơi gần đây.")
        return
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(games) { _, game ->
            RecentGameCard(game = game, onClick = { onOpenGame(game) })
        }
    }
}

@Composable
private fun RecentGameCard(game: HomeRecentGameUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(190.dp)
            .height(178.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            Image(
                painter = painterResource(id = gameImageRes(game)),
                contentDescription = game.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = game.name,
                    color = Color(0xFF0B3C7D),
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = "Lần chơi: ${game.lastPlayed}", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LearningActions(
    onNavigateToLearn: () -> Unit,
    onNavigateToGame: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LearningActionCard(
            icon = "😊",
            title = "Học 6 cảm xúc cơ bản",
            description = "Nhận biết vui, buồn, sợ, tức giận, ngạc nhiên và ghê tởm qua video, ảnh và tình huống.",
            buttonText = "Bắt đầu học",
            onClick = onNavigateToLearn
        )
        LearningActionCard(
            icon = "🎮",
            title = "Chơi 6 trò chơi cảm xúc",
            description = "Rèn luyện cảm xúc qua các trò chơi nhận diện, ghép mặt, tình huống và camera.",
            buttonText = "Vào chơi",
            onClick = onNavigateToGame
        )
    }
}

@Composable
private fun LearningActionCard(
    icon: String,
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(2.dp, Color(0xFF1976D2)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = Color(0xFFE3F2FD), modifier = Modifier.size(54.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = icon, fontSize = 30.sp)
                    }
                }
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0B3C7D),
                    fontSize = 18.sp
                )
            }
            Text(description, color = Color(0xFF52616F), fontSize = 13.sp, lineHeight = 18.sp)
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun StatsSection(
    emotions: List<HomeEmotionUi>,
    improvements: List<HomeMetricUi>,
    gameRatios: List<HomeMetricUi>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(
            icon = "😊",
            title = "Tỉ lệ đúng của 6 cảm xúc",
            metrics = emotions.map { HomeMetricUi(it.name, it.accuracy) },
            emptyMessage = "Chưa có dữ liệu độ chính xác.",
            allowNegative = false
        )
        MetricCard(
            icon = "📈",
            title = "Tỉ lệ cải thiện của 6 cảm xúc",
            metrics = improvements,
            emptyMessage = "Chưa đủ dữ liệu để tính cải thiện.",
            allowNegative = true
        )
        MetricCard(
            icon = "🎮",
            title = "Tỉ lệ chơi của các trò chơi",
            metrics = gameRatios,
            emptyMessage = "Chưa có dữ liệu tỉ lệ chơi.",
            allowNegative = false
        )
    }
}

@Composable
private fun MetricCard(
    icon: String,
    title: String,
    metrics: List<HomeMetricUi>,
    emptyMessage: String,
    allowNegative: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(2.dp, Color(0xFF1976D2)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(icon, fontSize = 34.sp)
                Text(title, color = Color(0xFF0B3C7D), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }

            if (metrics.isEmpty()) {
                Text(emptyMessage, color = Color.Gray)
            } else {
                val maxValue = metrics.maxOf { if (allowNegative) abs(it.value) else it.value.coerceAtLeast(0.0) }
                    .coerceAtLeast(1.0)
                metrics.take(6).forEach { metric ->
                    MetricRow(
                        metric = metric,
                        progress = if (allowNegative) abs(metric.value) / maxValue else metric.value / 100.0,
                        positiveColor = if (allowNegative && metric.value < 0) Color(0xFFE57373) else Color(0xFF4FACFE)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(metric: HomeMetricUi, progress: Double, positiveColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(metric.name, color = Color(0xFF1F2937), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("${signedPrefix(metric.value)}${formatPercent(abs(metric.value))}%", color = Color(0xFF0B3C7D), fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(Color(0xFFE5E7EB))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.toFloat().coerceIn(0.02f, 1f))
                    .clip(CircleShape)
                    .background(positiveColor)
            )
        }
    }
}

@Composable
private fun ReportCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 118.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("📋", fontSize = 42.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text("Báo cáo tiến bộ", color = Color(0xFF1B5E20), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text("Xem tổng kết luyện tập và gợi ý cảm xúc cần ôn thêm.", color = Color(0xFF52616F))
            }
        }
    }
}

@Composable
private fun LoadingCard(message: String) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(message, color = Color.Gray)
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = Color(0xFFFFEBEE), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, color = Color(0xFFC62828), modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Thử lại") }
        }
    }
}

@Composable
private fun EmptyHomeCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            color = Color.Gray
        )
    }
}

private fun gameImageRes(game: HomeRecentGameUi): Int {
    val key = normalizeGameKey(game.gameType ?: game.name)
    return when {
        game.id == GameUiCatalog.GAME_RECOGNIZE_EMOTION || key.contains("recognize") || key.contains("chiec") -> R.drawable.recognize_emotion
        game.id == GameUiCatalog.GAME_FACE_ASSEMBLY || key.contains("click2") || key.contains("lap") || key.contains("xuong") -> R.drawable.game_click_2
        game.id == GameUiCatalog.GAME_EMOTION_MATCH || key.contains("click3") || key.contains("dungcho") || key.contains("ai") -> R.drawable.game_click_3
        game.id == GameUiCatalog.GAME_DETECTIVE || key.contains("click4") || key.contains("thám") || key.contains("tham") -> R.drawable.game_click_4
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

private fun signedPrefix(value: Double): String = when {
    value > 0 -> "+"
    value < 0 -> "-"
    else -> ""
}
