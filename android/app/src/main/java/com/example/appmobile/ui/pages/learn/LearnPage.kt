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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgGradientPill
import com.example.appmobile.ui.components.EgHeroCard
import com.example.appmobile.ui.components.EgSegmentedTabs
import com.example.appmobile.ui.components.EgSoftCard
import com.example.appmobile.ui.components.EgTab
import com.example.appmobile.ui.components.EgTopActions

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
            .background(EgDesign.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = EgDesign.screenPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EgTopActions(onProfile = onOpenProfile, onSettings = onOpenSettings)
        EgSegmentedTabs(
            activeTab = EgTab.Learn,
            onHome = onGoHome,
            onLearn = {},
            onGames = onOpenGames
        )

        EgHeroCard(
            title = "HỌC CẢM XÚC",
            description = "Chọn một cảm xúc, xem video mẫu rồi đọc tình huống minh họa."
        )

        if (isLoading) {
            LoadingStrip("Đang tải thẻ học...")
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
        Spacer(modifier = Modifier.height(10.dp))
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
                shape = RoundedCornerShape(EgDesign.pillRadius),
                color = Color.Transparent,
                border = BorderStroke(1.dp, if (selected) EgDesign.blue else EgDesign.cardBorder),
                shadowElevation = if (selected) 2.dp else 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .background(if (selected) EgDesign.primaryGradient else Brush.linearGradient(listOf(emotionPillColor(emotion.id), EgDesign.card)))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(emotionIcon(emotion.id), fontSize = 18.sp)
                    Text(
                        emotion.name,
                        color = if (selected) Color.White else EgDesign.blue,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
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
    EgSoftCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${emotionIcon(emotion.id)} ${emotion.name}",
                        color = EgDesign.blue,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (pageIndex == 0) "Video mẫu" else "Tình huống minh họa",
                        color = EgDesign.textSecondary,
                        fontSize = 13.sp
                    )
                }
                TextButton(onClick = onSelectDetail) {
                    Text("Chi tiết", color = EgDesign.blue, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp))
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
            .size(40.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = EgDesign.blue, fontSize = 30.sp, fontWeight = FontWeight.Bold)
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8FBFF),
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = situationForEmotion(emotion.id),
                color = EgDesign.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp
            )
            EgGradientPill(
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
            .background(if (active) EgDesign.blue else Color(0xFFD1D5DB), CircleShape)
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
        "happy" -> Color(0xFFFFF7CC)
        "sad" -> Color(0xFFE0F2FE)
        "angry" -> Color(0xFFFFE4E6)
        "fear" -> Color(0xFFEDE9FE)
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
        lower.contains("sad") || lower.contains("buồn") || lower.contains("buon") || lower.contains("buá") -> "sad"
        lower.contains("angry") || lower.contains("tức") || lower.contains("tuc") || lower.contains("tá") -> "angry"
        lower.contains("fear") || lower.contains("sợ") || lower.contains("so") || lower.contains("sá") -> "fear"
        lower.contains("surprise") || lower.contains("ngạc") || lower.contains("ngac") || lower.contains("ngá") -> "surprise"
        lower.contains("disgust") || lower.contains("ghê") || lower.contains("ghe") || lower.contains("ghã") -> "disgust"
        else -> lower
    }
}
