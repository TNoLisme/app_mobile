package com.example.appmobile.ui.pages.learn

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.viewinterop.AndroidView
import com.example.appmobile.R
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.EmotionUiItem
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.EmoGardenBackground
import com.example.appmobile.ui.components.EmoGardenNavItem
import com.example.appmobile.ui.components.EmoGardenTopNav
import com.example.appmobile.ui.components.GradientActionButton

@Composable
fun LearnPage(
    onBack: () -> Unit,
    onSelectEmotion: (String) -> Unit,
    onGoHome: () -> Unit = onBack,
    onOpenGames: () -> Unit = {},
    onOpenProfile: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }
    var emotions by remember { mutableStateOf(GameUiCatalog.emotions) }
    var selectedEmotionId by remember { mutableStateOf(GameUiCatalog.emotions.first().id) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        val backendEmotions = runCatching {
            repository.getEmotionConcepts().map { concept ->
                GameUiCatalog.emotionFromBackend(
                    id = concept.id,
                    title = concept.name,
                    description = concept.desc
                )
            }
        }.getOrDefault(emptyList())

        emotions = backendEmotions.ifEmpty { GameUiCatalog.emotions }
        selectedEmotionId = emotions.firstOrNull()?.id ?: selectedEmotionId
        isLoading = false
    }

    val selectedEmotion = emotions.firstOrNull { it.id == selectedEmotionId }
        ?: GameUiCatalog.emotions.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EmoGardenBackground)
    ) {
        EmoGardenTopNav(
            activeItem = EmoGardenNavItem.Learn,
            onHome = onGoHome,
            onLearn = {},
            onGames = onOpenGames,
            onProfile = onOpenProfile,
            onSettings = onOpenSettings
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LearnHeader()

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF1976D2))
                    Text("Đang tải thẻ học...", color = Color(0xFF6B7280))
                }
            }

            EmotionPillRow(
                emotions = emotions,
                selectedEmotionId = selectedEmotionId,
                onSelect = { emotion ->
                    selectedEmotionId = emotion.id
                    pageIndex = 0
                }
            )

            LearnMediaCarousel(
                emotion = selectedEmotion,
                pageIndex = pageIndex,
                onPrevious = { pageIndex = if (pageIndex == 0) 1 else 0 },
                onNext = { pageIndex = if (pageIndex == 0) 1 else 0 },
                onSelectDetail = { onSelectEmotion(selectedEmotion.id) }
            )
        }
    }
}

@Composable
private fun LearnHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = "HỌC CẢM XÚC",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0B3C7D)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .height(3.dp)
                    .background(Color(0xFF4FACFE), CircleShape)
            )
            Text(
                text = "Chọn một cảm xúc, xem video mẫu rồi đọc tình huống minh họa.",
                color = Color(0xFF6B7280),
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun EmotionPillRow(
    emotions: List<EmotionUiItem>,
    selectedEmotionId: String,
    onSelect: (EmotionUiItem) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(emotions) { emotion ->
            val selected = emotion.id == selectedEmotionId
            Surface(
                modifier = Modifier.clickable { onSelect(emotion) },
                shape = CircleShape,
                color = emotionPillColor(emotion.id),
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF1976D2) else Color.White.copy(alpha = 0.6f)),
                shadowElevation = if (selected) 7.dp else 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(emotion.emoji.ifBlank { emotionIcon(emotion.id) }, fontSize = 20.sp)
                    Text(
                        emotion.name,
                        color = Color(0xFF0B3C7D),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LearnMediaCarousel(
    emotion: EmotionUiItem,
    pageIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDetail: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${emotion.emoji.ifBlank { emotionIcon(emotion.id) }} ${emotion.name}",
                        color = Color(0xFF0B3C7D),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(if (pageIndex == 0) "Video mẫu" else "Tình huống minh họa", color = Color(0xFF6B7280))
                }
                TextButton(onClick = onSelectDetail) { Text("Chi tiết") }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (pageIndex == 0) {
                    AssetVideoPlayer(emotionId = emotion.id)
                } else {
                    Image(
                        painter = painterResource(id = rememberEmotionImageResource(emotion.id)),
                        contentDescription = emotion.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                MediaArrow(text = "‹", modifier = Modifier.align(Alignment.CenterStart), onClick = onPrevious)
                MediaArrow(text = "›", modifier = Modifier.align(Alignment.CenterEnd), onClick = onNext)
            }

            if (pageIndex == 1) {
                SituationPanel(emotion = emotion, onSelectDetail = onSelectDetail)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Dot(active = pageIndex == 0)
                Spacer(modifier = Modifier.size(8.dp))
                Dot(active = pageIndex == 1)
            }
        }
    }
}

@Composable
private fun MediaArrow(text: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .size(42.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.92f),
        shadowElevation = 5.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = Color(0xFF1976D2), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AssetVideoPlayer(emotionId: String) {
    val context = LocalContext.current
    val assetName = "${normalizeEmotionId(emotionId)}.mp4"
    val uri = remember(assetName) {
        Uri.parse("file:///android_asset/fe/assets/videos/$assetName")
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            VideoView(it).apply {
                val controller = MediaController(context)
                controller.setAnchorView(this)
                setMediaController(controller)
                setVideoURI(uri)
                setOnPreparedListener { player ->
                    player.isLooping = true
                    seekTo(1)
                }
            }
        },
        update = { view ->
            view.setVideoURI(uri)
            view.seekTo(1)
        }
    )
}

@Composable
private fun SituationPanel(emotion: EmotionUiItem, onSelectDetail: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFF8FAFC)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = situationForEmotion(emotion.id),
                color = Color(0xFF111827),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 23.sp
            )
            GradientActionButton(
                text = "Xem dấu hiệu nhận biết",
                onClick = onSelectDetail,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Dot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(if (active) 10.dp else 8.dp)
            .background(if (active) Color(0xFF1976D2) else Color(0xFFD1D5DB), CircleShape)
    )
}

@Composable
private fun rememberEmotionImageResource(emotionId: String): Int {
    val context = LocalContext.current
    val resourceName = when (normalizeEmotionId(emotionId)) {
        "happy" -> "happy_1"
        "sad" -> "sad_1"
        "angry" -> "angry_1"
        "fear" -> "fear_1"
        "surprise" -> "surprise_1"
        "disgust" -> "disgust_1"
        else -> null
    }

    if (resourceName != null) {
        val id = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        if (id != 0) return id
    }

    return R.drawable.logo_emo
}

private fun emotionPillColor(emotionId: String): Color {
    return when (normalizeEmotionId(emotionId)) {
        "happy" -> Color(0xFFFFF2B3)
        "sad" -> Color(0xFFD9F1FF)
        "angry" -> Color(0xFFFFE1E6)
        "fear" -> Color(0xFFEEE9FF)
        "surprise" -> Color(0xFFFFEDD5)
        "disgust" -> Color(0xFFDCFCE7)
        else -> Color(0xFFF4F4F5)
    }
}

private fun situationForEmotion(emotionId: String): String {
    return when (normalizeEmotionId(emotionId)) {
        "happy" -> "Lan được tặng một món quà bất ngờ nên Lan rất vui và mỉm cười."
        "sad" -> "An đánh rơi cây kem yêu thích nên An buồn và muốn khóc."
        "angry" -> "Nam bị bạn giật đồ chơi mà không xin phép nên Nam tức giận."
        "fear" -> "Mai đi lạc mẹ trong siêu thị nên Mai cảm thấy sợ hãi."
        "surprise" -> "Huy mở hộp quà và thấy món đồ chơi mình thích nên rất ngạc nhiên."
        "disgust" -> "Minh ngửi thấy mùi rác thối nên cảm thấy ghê tởm."
        else -> "Hãy quan sát khuôn mặt và cơ thể để đoán cảm xúc của bạn nhỏ."
    }
}

private fun emotionIcon(emotionId: String): String {
    return when (normalizeEmotionId(emotionId)) {
        "happy" -> "😊"
        "sad" -> "😢"
        "angry" -> "😠"
        "fear" -> "😨"
        "surprise" -> "😲"
        "disgust" -> "🤢"
        else -> "🙂"
    }
}

private fun normalizeEmotionId(value: String): String {
    val lower = value.trim().lowercase()
    return when {
        lower.contains("happy") || lower.contains("vui") -> "happy"
        lower.contains("sad") || lower.contains("buồn") || lower.contains("buon") -> "sad"
        lower.contains("angry") || lower.contains("tức") || lower.contains("tuc") -> "angry"
        lower.contains("fear") || lower.contains("sợ") || lower.contains("so") -> "fear"
        lower.contains("surprise") || lower.contains("ngạc") || lower.contains("ngac") -> "surprise"
        lower.contains("disgust") || lower.contains("ghê") || lower.contains("ghe") -> "disgust"
        else -> lower
    }
}
