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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.appmobile.ui.components.egEmotionPastelColor
import com.example.appmobile.ui.viewmodel.HomeEmotionUi
import com.example.appmobile.ui.viewmodel.HomeMetricUi
import com.example.appmobile.ui.viewmodel.HomeRecentGameUi
import com.example.appmobile.ui.viewmodel.HomeViewModel

private val HomeBackgroundGradient = Color(0xFFEAF7FF)
private val HomePrimaryGradient = Color(0xFF62B5FF)
private val HomeCard = Color.White
private val HomeCardBorder = Color(0xFFD9E8F5)
private val HomeTextPrimary = Color(0xFF0B3A6E)
private val HomeTextSecondary = Color(0xFF6B7280)
private val HomeBlue = Color(0xFF0B5DAE)
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val loading by vm.isLoading
    val errorMessage by vm.errorMessage
    val childName by vm.childName
    val recommendedEmotion = recommendedHomeEmotion(vm.emotions)
    val learnedEmotionCount = vm.emotions.count { it.correct + it.incorrect > 0 }.coerceAtMost(6)
    val gamesPlayedCount = vm.recentGames.size
    val averageAccuracy = vm.emotions.takeIf { it.isNotEmpty() }?.map { it.accuracy }?.average()?.toInt()
    val totalEmotionAttempts = vm.emotions.sumOf { it.correct + it.incorrect }
    val strongestEmotion = vm.emotions
        .filter { it.correct + it.incorrect > 0 }
        .maxByOrNull { it.accuracy }
        ?.name
    val topGameRatio = vm.gameRatios.firstOrNull()

    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    EgCollapsibleMainScaffold(
        activeTab = EgTab.Home,
        onHome = {},
        onLearn = onNavigateToLearn,
        onGames = { onNavigateToGame("all") },
        onProfile = onNavigateToProfile,
        onSettings = onNavigateToSettings,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        errorMessage?.let { message ->
            ErrorBanner(message = message, onRetry = vm::refresh)
        }

        if (loading) {
            LoadingStrip("Đang chuẩn bị bài học cho bé...")
        }

        GreetingSection(childName = childName)

        TodayLearningCard(
            emotionName = recommendedEmotion,
            onStartLearn = onNavigateToLearn,
            onStartChallenge = { onNavigateToLevel(GameUiCatalog.GAME_CV_REQUEST) }
        )

        QuickActionRow(
            onLearn = onNavigateToLearn,
            onGames = { onNavigateToGame("all") },
            onReport = onNavigateToReport
        )

        ProgressSummaryCard(
            learnedEmotionCount = learnedEmotionCount,
            gamesPlayedCount = gamesPlayedCount,
            averageAccuracy = averageAccuracy,
            onPlay = { onNavigateToGame("all") }
        )

        HomeInsightCard(
            totalEmotionAttempts = totalEmotionAttempts,
            strongestEmotion = strongestEmotion,
            topGameRatio = topGameRatio
        )

        if (vm.improvements.isNotEmpty() || vm.gameRatios.isNotEmpty()) {
            HomeAnalyticsCard(
                improvements = vm.improvements,
                gameRatios = vm.gameRatios,
                onReport = onNavigateToReport
            )
        }

        RecommendationCard(
            emotionName = recommendedEmotion,
            hasProgress = vm.emotions.isNotEmpty(),
            onStartLearn = onNavigateToLearn
        )

        if (vm.emotions.isNotEmpty()) {
            EmotionAccuracySection(emotions = vm.emotions)
        }

        RecentGamesSection(
            games = vm.recentGames,
            onPlayNow = { onNavigateToGame("all") },
            onOpenGame = { game ->
                if (!game.id.isNullOrBlank()) {
                    onNavigateToLevel(game.id)
                } else {
                    onNavigateToGame(gameCategory(game))
                }
            }
        )

        ProgressReportCard(onNavigateToReport = onNavigateToReport)
    }
}

@Composable
private fun GreetingSection(childName: String?) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = childName?.takeIf { it.isNotBlank() }?.let { "Chào bé $it 👋" } ?: "Chào bé yêu 👋",
            color = HomeTextPrimary,
            fontSize = 22.sp,
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
    emotionName: String,
    onStartLearn: () -> Unit,
    onStartChallenge: () -> Unit
) {
    val emoji = egEmotionIcon(emotionName)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7FF)),
        border = BorderStroke(1.dp, Color(0xFFCDE7FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Hôm nay bé học gì?", color = HomeBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(emoji, fontSize = 42.sp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Cùng luyện cảm xúc ${egEmotionDisplayName(emotionName)}",
                        color = HomeTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Xem mẫu cảm xúc và thử làm khuôn mặt trước camera nhé.",
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
private fun QuickActionRow(
    onLearn: () -> Unit,
    onGames: () -> Unit,
    onReport: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickActionCard("📚", "Học cảm xúc", onLearn, Modifier.weight(1f))
        QuickActionCard("🎮", "Chơi game", onGames, Modifier.weight(1f))
        QuickActionCard("📋", "Báo cáo", onReport, Modifier.weight(1f))
    }
}

@Composable
private fun QuickActionCard(icon: String, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(74.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = HomeCard,
        border = BorderStroke(1.dp, HomeCardBorder),
        shadowElevation = 1.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(icon, fontSize = 23.sp)
            Text(text, color = HomeBlue, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun ProgressSummaryCard(
    learnedEmotionCount: Int,
    gamesPlayedCount: Int,
    averageAccuracy: Int?,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HomeRadiusCard),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(1.dp, HomeCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Tiến độ nhanh")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressMiniStat("$learnedEmotionCount/6", "Cảm xúc đã học", Modifier.weight(1f))
                ProgressMiniStat(if (gamesPlayedCount > 0) "$gamesPlayedCount" else "Chưa chơi", "Lượt chơi", Modifier.weight(1f))
                ProgressMiniStat(averageAccuracy?.let { "$it%" } ?: "Chưa có", "Độ chính xác", Modifier.weight(1f))
            }
            if (gamesPlayedCount == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Bé chơi thử một màn để xem tiến độ nhé!",
                        modifier = Modifier.weight(1f),
                        color = HomeTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    HomeActionPill("Chơi ngay", onPlay, primary = false)
                }
            }
        }
    }
}

@Composable
private fun HomeInsightCard(
    totalEmotionAttempts: Int,
    strongestEmotion: String?,
    topGameRatio: HomeMetricUi?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HomeRadiusCard),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(1.dp, HomeCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Chỉ số hôm nay")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressMiniStat(
                    if (totalEmotionAttempts > 0) totalEmotionAttempts.toString() else "Chưa có",
                    "Lượt cảm xúc",
                    Modifier.weight(1f)
                )
                ProgressMiniStat(
                    strongestEmotion?.let { egEmotionDisplayName(it) } ?: "Chưa có",
                    "Cảm xúc mạnh",
                    Modifier.weight(1f)
                )
                ProgressMiniStat(
                    topGameRatio?.name?.let(::homeMetricDisplayName) ?: "Chưa có",
                    "Game chơi nhiều",
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HomeAnalyticsCard(
    improvements: List<HomeMetricUi>,
    gameRatios: List<HomeMetricUi>,
    onReport: () -> Unit
) {
    val bestImprovement = improvements.firstOrNull()
    val topGame = gameRatios.firstOrNull()
    HomeEmptyStateCard(
        title = "Dữ liệu luyện tập",
        subtitle = when {
            bestImprovement != null && topGame != null ->
                "Tiến bộ rõ nhất: ${egEmotionDisplayName(bestImprovement.name)}. Game chơi nhiều: ${homeMetricDisplayName(topGame.name)}."
            bestImprovement != null ->
                "Tiến bộ rõ nhất: ${egEmotionDisplayName(bestImprovement.name)}."
            topGame != null ->
                "Game bé chơi nhiều nhất gần đây: ${homeMetricDisplayName(topGame.name)}."
            else ->
                "Chơi thêm vài lượt để hệ thống có chỉ số luyện tập."
        },
        icon = "📈",
        actionText = "Xem báo cáo",
        onAction = onReport
    )
}

@Composable
private fun ProgressMiniStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8FCFF),
        border = BorderStroke(1.dp, Color(0xFFE3EEF8))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                value,
                color = HomeBlue,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                color = HomeTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecommendationCard(emotionName: String, hasProgress: Boolean, onStartLearn: () -> Unit) {
    HomeEmptyStateCard(
        title = "Gợi ý cho bé",
        subtitle = if (hasProgress) {
            "Bé có thể luyện thêm cảm xúc ${egEmotionDisplayName(emotionName)} ${egEmotionIcon(emotionName)}."
        } else {
            "Bắt đầu với cảm xúc Vui vẻ 😊 để mở thống kê đầu tiên."
        },
        icon = "🌟",
        actionText = "Xem bài học",
        onAction = onStartLearn
    )
}

@Composable
private fun HomeEmptyStateCard(
    title: String,
    subtitle: String,
    icon: String,
    actionText: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HomeRadiusCard),
        color = HomeCard,
        border = BorderStroke(1.dp, HomeCardBorder),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(icon, fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = HomeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = HomeTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
            HomeActionPill(actionText, onAction, primary = false)
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
            .height(42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(HomeRadiusPill),
        color = if (primary) HomePrimaryGradient else Color.White,
        border = if (primary) null else BorderStroke(1.dp, HomeCardBorder),
        shadowElevation = if (primary) 2.dp else 1.dp
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                color = if (primary) Color.White else HomeBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

private fun recommendedHomeEmotion(emotions: List<HomeEmotionUi>): String {
    if (emotions.isEmpty()) return "happy"
    return emotions.minByOrNull { it.accuracy }?.name ?: "happy"
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
                .background(if (active) HomePrimaryGradient else Color(0xFFF8FCFF))
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
            EmptyHomeCard(
                message = "Chơi thử thách đầu tiên để mở thống kê.",
                subtitle = "Bé sẽ thấy cảm xúc nào mình làm tốt nhất.",
                icon = "📊"
            )
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
    val emotionName = egEmotionDisplayName(emotion.name)
    val emotionColor = egEmotionPastelColor(emotion.name)

    Card(
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(1.dp, HomeCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(emotionColor)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = egEmotionIcon(emotion.name), fontSize = 25.sp)
            Text(
                emotionName,
                color = HomeTextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatPercent(emotion.accuracy)}%",
                color = HomeBlue,
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
    onPlayNow: () -> Unit,
    onOpenGame: (HomeRecentGameUi) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Trò chơi đã chơi gần đây")

        if (games.isEmpty()) {
            HomeEmptyStateCard(
                title = "Bé chưa chơi game nào",
                subtitle = "Thử một trò chơi nhỏ để luyện cảm xúc nhé.",
                icon = "🎮",
                actionText = "Chơi ngay",
                onAction = onPlayNow
            )
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
private fun EmptyHomeCard(
    message: String,
    subtitle: String? = null,
    icon: String = "🎮"
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = Color(0xFFE8F7FF),
                border = BorderStroke(1.dp, Color(0xFFD7E7F3))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(icon, fontSize = 22.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = message,
                    color = HomeTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
                subtitle?.let {
                    Text(
                        text = it,
                        color = Color(0xFF7C8A9C),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
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
    return if (key.contains("cv") || key.contains("camera") || key.contains("bieucam")) "camera_game" else "click_game"
}

private fun homeMetricDisplayName(rawName: String): String {
    GameUiCatalog.gameById(rawName)?.let { return it.title }
    val key = normalizeGameKey(rawName)
    return when {
        key.contains("recognize") || key.contains("chiec") || key.contains("hop") -> "Chiếc hộp"
        key.contains("click2") || key.contains("lap") || key.contains("xuong") -> "Lắp ghép"
        key.contains("click3") || key.contains("dungcho") -> "Đúng chỗ"
        key.contains("click4") || key.contains("tham") -> "Thám tử"
        key.contains("cv2") || key.contains("thuthach") -> "Thử thách"
        key.contains("cv") || key.contains("khuonmat") -> "Khuôn mặt"
        else -> rawName.ifBlank { "Chưa có" }
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
