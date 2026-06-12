package com.example.appmobile.ui.pages.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appmobile.R
import com.example.appmobile.data.garden.GardenHomeSummary
import com.example.appmobile.data.garden.GardenRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.EgCollapsibleMainScaffold
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgTab
import com.example.appmobile.ui.components.EgEmotionVectorIcon
import com.example.appmobile.ui.components.EgVectorEmojiIcon
import com.example.appmobile.ui.components.egTactileClick
import com.example.appmobile.ui.viewmodel.HomeRecentGameUi
import com.example.appmobile.ui.viewmodel.HomeViewModel
import kotlin.math.min

private val HomeCard: Color get() = EgDesign.card
private val HomeCardSoft: Color get() = EgDesign.cardSoft
private val HomeCardBorder: Color get() = EgDesign.cardBorder
private val HomeTextPrimary: Color get() = EgDesign.textPrimary
private val HomeTextSecondary: Color get() = EgDesign.textSecondary
private val HomeBlue: Color get() = EgDesign.primary

@Composable
fun HomePage(
    onNavigateToGame: (String) -> Unit,
    onNavigateToLearn: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToLevel: (String) -> Unit = {},
    onNavigateToPhotoBooth: () -> Unit = {},
    onNavigateToGarden: () -> Unit = {},
    onNavigateToLearnEmotion: ((String) -> Unit)? = null,
    onStartEmotionChallenge: ((String) -> Unit)? = null,
    vm: HomeViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by vm.state.collectAsState()

    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val startLearningAction = {
        if (onNavigateToLearnEmotion != null) onNavigateToLearnEmotion(state.recommendedEmotionId) else onNavigateToLearn()
    }

    EgCollapsibleMainScaffold(
        activeTab = EgTab.Home,
        onHome = {},
        onLearn = onNavigateToLearn,
        onGames = { onNavigateToGame("all") },
        onProfile = onNavigateToProfile,
        onSettings = onNavigateToSettings,
        topBar = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.errorMessage?.let { message ->
                    ErrorBanner(message = message, onRetry = vm::refresh)
                }
                GreetingSection(childName = state.childName)
            }
        }
    ) {

        TodayLearningCard(
            emotionId = state.recommendedEmotionId,
            onStartLearn = startLearningAction,
            recentGames = state.recentGames,
            onPlayNow = { onNavigateToGame("all") },
            onOpenRecentGame = { game ->
                if (!game.id.isNullOrBlank()) {
                    onNavigateToLevel(game.id)
                } else {
                    onNavigateToGame(gameCategory(game))
                }
            }
        )

        EmotionGardenCtaCard(
            summary = state.gardenSummary,
            onOpenGarden = onNavigateToGarden
        )

        ReportCtaCard(
            actionText = state.reportActionText,
            onOpenReport = onNavigateToReport
        )

        PhotoBoothCtaCard(onStart = onNavigateToPhotoBooth)
    }
}

@Composable
private fun EmotionGardenCtaCard(summary: GardenHomeSummary?, onOpenGarden: () -> Unit) {
    val pending = summary?.pendingRewardCount ?: 0
    val suggested = summary?.suggestedEmotionToCare?.let { "${GardenRepository.plantSpeciesName(it)} cần được chăm thêm." }
        ?: "Hôm nay mình chăm vườn cảm xúc nhé!"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EgDesign.radiusLarge),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(1.dp, HomeCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiniEmotionGarden(progress = summary?.gardenProgressPercent ?: 0)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Vườn cảm xúc",
                        color = HomeTextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    if (pending > 0) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, Color(0xFF86D39E))
                        ) {
                            Text(
                                text = "$pending",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                color = Color(0xFF15803D),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
                Text(
                    text = suggested,
                    color = HomeTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            HomeActionPill("Chăm vườn", onOpenGarden, primary = true)
        }
    }
}

@Composable
private fun MiniEmotionGarden(progress: Int) {
    Canvas(
        modifier = Modifier
            .size(width = 54.dp, height = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE8F8EE))
            .padding(4.dp)
    ) {
        val s = min(size.width, size.height)
        drawOval(Color(0xFF8ED49B), Offset(s * 0.08f, s * 0.70f), Size(s * 0.88f, s * 0.20f))
        val colors = listOf(Color(0xFFFFD64D), Color(0xFF6CB8FF), Color(0xFFFF8A55))
        colors.forEachIndexed { index, color ->
            val x = s * (0.25f + index * 0.25f)
            val y = s * (0.66f - (progress.coerceIn(0, 100) / 100f) * 0.20f)
            drawLine(Color(0xFF45A162), Offset(x, s * 0.76f), Offset(x, y), s * 0.045f)
            drawCircle(color, s * 0.10f, Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.55f), s * 0.03f, Offset(x - s * 0.03f, y - s * 0.03f))
        }
    }
}

@Composable
private fun PhotoBoothCtaCard(onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EgDesign.radiusLarge),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(1.dp, HomeCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiniPhotoBoothStrip()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "EmoGarden Photobooth",
                    color = HomeTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
                Text(
                    text = "Chọn nhiều cảm xúc rồi chụp thành một dải ảnh thật dễ thương.",
                    color = HomeTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            HomeActionPill("Bắt đầu chụp", onStart, primary = true)
        }
    }
}

@Composable
private fun MiniPhotoBoothStrip() {
    Column(
        modifier = Modifier
            .width(38.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(
            Color(0xFFE7F7FF),
            Color(0xFFFFF1C7),
            Color(0xFFE8F8EE)
        ).forEach { color ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(HomeBlue)
                )
            }
        }
    }
}

@Composable
private fun GreetingSection(childName: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = childName?.takeIf { it.isNotBlank() }?.let { "Chào bé $it" } ?: "Chào bé yêu",
            color = HomeTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Hôm nay mình cùng học cảm xúc nhé!",
            color = HomeTextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TodayLearningCard(
    emotionId: String,
    onStartLearn: () -> Unit,
    recentGames: List<HomeRecentGameUi>,
    onPlayNow: () -> Unit,
    onOpenRecentGame: (HomeRecentGameUi) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EgDesign.radiusXLarge),
        colors = CardDefaults.cardColors(containerColor = HomeCardSoft),
        border = BorderStroke(1.dp, HomeCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Hôm nay bé học gì?",
                color = HomeTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EgEmotionVectorIcon(emotionId, size = 42.dp)
                HomeActionPill("Bắt đầu học", onStartLearn, Modifier.weight(1f), primary = true)
            }
            RecentGamesInsideTodayCard(
                games = recentGames,
                onPlayNow = onPlayNow,
                onOpenGame = onOpenRecentGame
            )
        }
    }
}

@Composable
private fun ReportCtaCard(
    actionText: String,
    onOpenReport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EgDesign.radiusLarge),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(1.dp, HomeCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EgVectorEmojiIcon("report", size = 30.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Báo cáo của bé",
                    color = HomeTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
                Text(
                    text = "Xem thành tích tuần này và gửi cho bố mẹ.",
                    color = HomeTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            HomeActionPill(actionText, onOpenReport, primary = true)
        }
    }
}

@Composable
private fun RecentGamesInsideTodayCard(
    games: List<HomeRecentGameUi>,
    onPlayNow: () -> Unit,
    onOpenGame: (HomeRecentGameUi) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = HomeCard,
        border = BorderStroke(1.dp, HomeCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EgVectorEmojiIcon("gamepad", size = 20.dp)
                Text(
                    text = "Trò chơi gần đây",
                    color = HomeTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
            }
            if (games.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Bé chưa chơi game nào gần đây.",
                        color = HomeTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    HomeActionPill("Chơi ngay", onPlayNow, primary = true)
                }
            } else {
                games.take(2).forEach { game ->
                    RecentGameCompactRow(game = game, onClick = { onOpenGame(game) })
                }
            }
        }
    }
}

@Composable
private fun RecentGameCompactRow(game: HomeRecentGameUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HomeCardSoft)
            .egTactileClick(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Image(
            painter = painterResource(id = gameImageRes(game)),
            contentDescription = game.name,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = game.name,
                color = HomeTextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
            )
            Text(
                text = if (game.lastPlayed.isNotBlank()) "Chơi gần đây" else "Tiếp tục",
                color = HomeTextSecondary,
                fontSize = 11.sp
            )
        }
        EgVectorEmojiIcon("next", size = 18.dp, tint = HomeTextSecondary)
    }
}

@Composable
private fun HomeActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .egTactileClick(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (primary) HomeBlue else HomeCard,
        border = if (primary) null else BorderStroke(1.dp, HomeCardBorder),
        shadowElevation = if (primary) 2.dp else 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (primary) Color.White else HomeTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
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
    return if (key.contains("cv") || key.contains("camera") || key.contains("bieucam")) {
        "camera_game"
    } else {
        "click_game"
    }
}

private fun normalizeGameKey(value: String): String {
    return value
        .trim()
        .lowercase()
        .replace(" ", "")
        .replace("_", "")
        .replace("-", "")
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = HomeCardSoft,
        border = BorderStroke(1.dp, HomeCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EgVectorEmojiIcon("warning", size = 16.dp)
            Text(
                text = message,
                color = HomeTextPrimary,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
            )
            TextButton(onClick = onRetry) {
                Text("Thử lại", color = HomeBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

