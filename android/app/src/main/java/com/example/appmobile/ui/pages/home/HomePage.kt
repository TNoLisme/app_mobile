package com.example.appmobile.ui.pages.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.EgCollapsibleMainScaffold
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgTab
import com.example.appmobile.ui.components.egEmotionDisplayName
import com.example.appmobile.ui.components.egEmotionIcon
import com.example.appmobile.ui.viewmodel.HomeRecentGameUi
import com.example.appmobile.ui.viewmodel.ReportSummary
import com.example.appmobile.ui.viewmodel.HomeUiState
import com.example.appmobile.ui.viewmodel.HomeViewModel

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

    val suggestedEmotionId = state.weakEmotionId ?: state.recommendedEmotionId
    val startLearningAction = {
        if (onNavigateToLearnEmotion != null) onNavigateToLearnEmotion(state.recommendedEmotionId) else onNavigateToLearn()
    }
    val suggestionAction = {
        if (onNavigateToLearnEmotion != null) onNavigateToLearnEmotion(suggestedEmotionId) else onNavigateToLearn()
    }
    val challengeAction = {
        if (onStartEmotionChallenge != null) {
            onStartEmotionChallenge(state.recommendedEmotionId)
        } else {
            onNavigateToLevel(GameUiCatalog.GAME_CV_REQUEST)
        }
    }

    EgCollapsibleMainScaffold(
        activeTab = EgTab.Home,
        onHome = {},
        onLearn = onNavigateToLearn,
        onGames = { onNavigateToGame("all") },
        onProfile = onNavigateToProfile,
        onSettings = onNavigateToSettings
    ) {
        state.errorMessage?.let { message ->
            ErrorBanner(message = message, onRetry = vm::refresh)
        }

        if (state.isLoading) {
            LoadingStrip("Đang chuẩn bị bài học cho bé...")
        }

        GreetingSection(childName = state.childName)

        TodayLearningCard(
            emotionId = state.recommendedEmotionId,
            onStartLearn = startLearningAction,
            onStartChallenge = challengeAction
        )

        ReportCtaCard(
            reportSummary = state.reportSummary,
            actionText = state.reportActionText,
            onOpenReport = onNavigateToReport
        )

        TodaySuggestionCard(
            state = state,
            onOpenSuggestion = suggestionAction
        )

        RecentGamesSection(
            games = state.recentGames,
            onPlayNow = { onNavigateToGame("all") },
            onViewAll = { onNavigateToGame("all") },
            onOpenGame = { game ->
                if (!game.id.isNullOrBlank()) {
                    onNavigateToLevel(game.id)
                } else {
                    onNavigateToGame(gameCategory(game))
                }
            }
        )
    }
}

@Composable
private fun GreetingSection(childName: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = childName?.takeIf { it.isNotBlank() }?.let { "Chào bé $it 👋" } ?: "Chào bé yêu 👋",
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
    onStartChallenge: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(egEmotionIcon(emotionId), fontSize = 38.sp)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Cùng luyện cảm xúc ${egEmotionDisplayName(emotionId)} ${egEmotionIcon(emotionId)}",
                        color = HomeTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Xem mẫu cảm xúc rồi thử làm khuôn mặt trước camera nhé.",
                        color = HomeTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeActionPill("Bắt đầu học", onStartLearn, Modifier.weight(1f), primary = true)
                HomeActionPill("Chơi thử thách", onStartChallenge, Modifier.weight(1f), primary = false)
            }
        }
    }
}

@Composable
private fun ReportCtaCard(
    reportSummary: ReportSummary?,
    actionText: String,
    onOpenReport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(1.dp, HomeCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("📋", fontSize = 28.sp)
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
                reportSummary?.let { summary ->
                    val scoreText = summary.averageScore?.let { "$it/100" } ?: "Chưa có điểm"
                    Text(
                        text = "${summary.sessionsCount} lượt luyện · $scoreText · ${summary.learnedEmotionCount}/${summary.totalEmotionCount} cảm xúc",
                        color = HomeTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HomeActionPill(actionText, onOpenReport, primary = true)
        }
    }
}

@Composable
private fun TodaySuggestionCard(state: HomeUiState, onOpenSuggestion: () -> Unit) {
    val message = when {
        state.weakEmotionId != null -> {
            "Bé có thể luyện thêm cảm xúc ${egEmotionDisplayName(state.weakEmotionId)} ${egEmotionIcon(state.weakEmotionId)}"
        }
        state.learnedEmotionCount == 0 -> {
            "Bắt đầu với cảm xúc Vui vẻ 😊"
        }
        else -> {
            "Bé đang làm tốt, hãy thử một thử thách mới nhé!"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(1.dp, HomeCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("✨", fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Gợi ý hôm nay",
                    color = HomeTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Text(
                    text = message,
                    color = HomeTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            HomeActionPill("Xem bài học", onOpenSuggestion, primary = false)
        }
    }
}

@Composable
private fun RecentGamesSection(
    games: List<HomeRecentGameUi>,
    onPlayNow: () -> Unit,
    onViewAll: () -> Unit,
    onOpenGame: (HomeRecentGameUi) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Trò chơi gần đây", modifier = Modifier.weight(1f))
            if (games.size > 2) {
                TextButton(onClick = onViewAll) {
                    Text("Xem tất cả", color = HomeBlue, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (games.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = HomeCard),
                border = BorderStroke(1.dp, HomeCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Bé chưa chơi game nào gần đây.",
                        color = HomeTextSecondary,
                        fontSize = 14.sp
                    )
                    HomeActionPill("Chơi ngay", onPlayNow, primary = true)
                }
            }
            return
        }

        games.take(2).forEach { game ->
            RecentGameRowCard(game = game, onClick = { onOpenGame(game) })
        }
    }
}

@Composable
private fun RecentGameRowCard(game: HomeRecentGameUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(1.dp, HomeCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = gameImageRes(game)),
                contentDescription = game.name,
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = game.name,
                    color = HomeTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (game.lastPlayed.isNotBlank()) "Chơi gần đây" else "Tiếp tục",
                    color = HomeTextSecondary,
                    fontSize = 12.sp
                )
            }
            Text(">", color = HomeTextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
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
            .clickable(onClick = onClick),
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

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier,
        color = HomeTextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
private fun LoadingStrip(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = HomeCard,
        border = BorderStroke(1.dp, HomeCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = HomeBlue
            )
            Text(message, color = HomeTextSecondary, fontSize = 13.sp)
        }
    }
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
            Text("⚠️", fontSize = 15.sp)
            Text(
                text = message,
                color = HomeTextPrimary,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onRetry) {
                Text("Thử lại", color = HomeBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
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
