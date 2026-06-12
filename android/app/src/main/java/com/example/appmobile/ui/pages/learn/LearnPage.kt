package com.example.appmobile.ui.pages.learn

import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.appmobile.R
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.EmotionUiItem
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.EgCollapsibleMainScaffold
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgEmotionVectorIcon
import com.example.appmobile.ui.components.EgSoftCard
import com.example.appmobile.ui.components.EgTab
import com.example.appmobile.ui.components.EgVectorEmojiIcon
import com.example.appmobile.ui.components.EgEmotionCardBackground
import com.example.appmobile.ui.components.EgEmotionCardBorder
import com.example.appmobile.ui.components.EgEmotionCardSelectedBackground
import com.example.appmobile.ui.components.EgEmotionCardSelectedBorder
import com.example.appmobile.ui.components.EgEmotionCardSelectedText
import com.example.appmobile.ui.components.EgEmotionCardText
import com.example.appmobile.ui.components.egEmotionDisplayName
import com.example.appmobile.ui.components.egEmotionKey
import com.example.appmobile.ui.components.egLearningEmotionGridItems
import com.example.appmobile.ui.components.egTactileClick
import com.example.appmobile.ui.state.AppSettingsState
import kotlinx.coroutines.delay

private data class EmotionDetailContent(
    val id: String,
    val name: String,
    val emoji: String,
    val shortDescription: String,
    val whatIsIt: List<String>,
    val signs: List<String>,
    val situations: List<String>,
    val whatToDo: List<String>,
    val sayItLikeThis: List<String>
)

private data class SituationVisualContent(
    val sceneTitle: String,
    val imageRes: Int,
    val caption: String,
    val startColor: Color,
    val endColor: Color
)

private val LearnMediaArrowsVisible = mutableStateOf(true)

@Composable
fun LearnPage(
    initialEmotionId: String? = null,
    onBack: () -> Unit,
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
    var selectedEmotionId by remember { mutableStateOf(initialEmotionId ?: GameUiCatalog.emotions.first().id) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var detailEmotionId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialEmotionId) {
        if (initialEmotionId != null) {
            selectedEmotionId = initialEmotionId
            detailEmotionId = null
        }
    }

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

    val gridEmotions = remember(emotions) { egLearningEmotionGridItems(emotions) }
    val selectedEmotion = gridEmotions.firstOrNull { it.id == selectedEmotionId }
        ?: gridEmotions.firstOrNull()
        ?: GameUiCatalog.emotions.first()

    LaunchedEffect(gridEmotions, selectedEmotionId) {
        if (gridEmotions.isNotEmpty() && gridEmotions.none { it.id == selectedEmotionId }) {
            selectedEmotionId = gridEmotions.first().id
        }
    }

    EgCollapsibleMainScaffold(
        activeTab = EgTab.Learn,
        onHome = onGoHome,
        onLearn = {},
        onGames = onOpenGames,
        onProfile = onOpenProfile,
        onSettings = onOpenSettings
    ) {
        if (isLoading) {
            LoadingStrip("Đang tải thẻ học...")
        }

        LearnMediaCarousel(
            emotion = selectedEmotion,
            pageIndex = pageIndex,
            onPrevious = { pageIndex = if (pageIndex == 0) 1 else 0 },
            onNext = { pageIndex = if (pageIndex == 0) 1 else 0 },
            onOpenDetail = { detailEmotionId = selectedEmotion.id }
        )

        EmotionRememberCard(emotion = selectedEmotion)

        EmotionGrid(
            emotions = gridEmotions,
            selectedEmotionId = selectedEmotionId,
            onSelect = { emotion ->
                selectedEmotionId = emotion.id
                pageIndex = 0
            }
        )
    }

    detailEmotionId?.let { emotionId ->
        val detailEmotion = gridEmotions.firstOrNull { it.id == emotionId } ?: selectedEmotion
        EmotionDetailBottomSheet(
            content = emotionDetailContent(detailEmotion),
            onDismiss = { detailEmotionId = null }
        )
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
private fun EmotionGrid(
    emotions: List<EmotionUiItem>,
    selectedEmotionId: String,
    onSelect: (EmotionUiItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Chọn cảm xúc khác",
            color = EgDesign.blue,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold
        )
        emotions.take(6).chunked(2).forEach { rowEmotions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowEmotions.forEach { emotion ->
                    EmotionGridItem(
                        emotion = emotion,
                        selected = emotion.id == selectedEmotionId,
                        onClick = { onSelect(emotion) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(2 - rowEmotions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EmotionRememberCard(emotion: EmotionUiItem) {
    val key = egEmotionKey(emotion)
    val notes = remember(key) { rememberTextsForEmotion(key) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = EgDesign.card,
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EgVectorEmojiIcon("bulb", size = 20.dp)
                Text(
                    "Bé cần nhớ",
                    color = EgDesign.blue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            notes.forEach { note ->
                Text(
                    text = "• $note",
                    color = EgDesign.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EmotionGridItem(
    emotion: EmotionUiItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val key = egEmotionKey(emotion)
    val isDark = AppSettingsState.activeDarkTheme.value
    
    val unselectedBgColor = if (isDark) Color(0xFF1E293B) else EgEmotionCardBackground
    val selectedBgColor = if (isDark) Color(0xFF1E293B) else EgEmotionCardSelectedBackground
    val backgroundColor = if (selected) selectedBgColor else unselectedBgColor
    
    val unselectedBorderColor = if (isDark) Color(0xFF334155) else EgEmotionCardBorder
    val selectedBorderColor = if (isDark) {
        when (key) {
            "happy" -> Color(0xFFF59E0B)
            "sad" -> Color(0xFF3B82F6)
            "angry" -> Color(0xFFEF4444)
            "fear" -> Color(0xFF8B5CF6)
            "surprise" -> Color(0xFF10B981)
            "disgust" -> Color(0xFF84CC16)
            else -> Color(0xFF3B82F6)
        }
    } else {
        EgEmotionCardSelectedBorder
    }
    
    val borderColor = if (selected) selectedBorderColor else unselectedBorderColor
    val textColor = if (isDark) Color(0xFFF8FAFC) else (if (selected) EgEmotionCardSelectedText else EgEmotionCardText)

    Surface(
        modifier = modifier
            .height(72.dp)
            .egTactileClick(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            borderColor
        ),
        shadowElevation = if (selected) 2.dp else 1.dp
    ) {
        Box(
            modifier = Modifier
                .background(backgroundColor)
                .padding(horizontal = 6.dp, vertical = 8.dp)
        ) {
            if (selected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp),
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, borderColor.copy(alpha = 0.24f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EgVectorEmojiIcon("check", size = 12.dp, tint = borderColor)
                    }
                }
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                EgEmotionVectorIcon(key, size = 34.dp)
                Text(
                    text = egEmotionDisplayName(emotion),
                    color = textColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
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
    onOpenDetail: () -> Unit
) {
    var videoControlsVisible by remember(emotion.id, pageIndex) { mutableStateOf(true) }
    var videoEndedTime by remember(emotion.id, pageIndex) { mutableStateOf<Long?>(null) }
    val autoTransitionEnabled by AppSettingsState.learnContentAutoTransitionEnabled

    LaunchedEffect(pageIndex, videoControlsVisible) {
        LearnMediaArrowsVisible.value = pageIndex != 0 || videoControlsVisible
    }

    LaunchedEffect(videoEndedTime) {
        if (videoEndedTime != null) {
            kotlinx.coroutines.delay(2000)
            onNext()
        }
    }

    LaunchedEffect(pageIndex, autoTransitionEnabled) {
        if (pageIndex == 1 && autoTransitionEnabled) {
            kotlinx.coroutines.delay(10000)
            onNext()
        }
    }
    EgSoftCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        egEmotionDisplayName(emotion),
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
                EmotionDetailButton(onClick = onOpenDetail)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(EgDesign.radiusLarge))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (pageIndex == 0) {
                    AssetVideoPlayer(
                        emotionId = egEmotionKey(emotion),
                        onControlsVisibleChange = { videoControlsVisible = it },
                        loop = !autoTransitionEnabled,
                        onVideoEnded = { videoEndedTime = System.currentTimeMillis() }
                    )
                } else {
                    SituationIllustration(emotion = emotion)
                }
                MediaArrow(text = "‹", modifier = Modifier.align(Alignment.CenterStart), onClick = onPrevious)
                MediaArrow(text = "›", modifier = Modifier.align(Alignment.CenterEnd), onClick = onNext)
            }

            if (pageIndex == 1) {
                SituationPanel(emotion = emotion)
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
private fun SituationIllustration(emotion: EmotionUiItem) {
    val visual = situationVisualForEmotion(egEmotionKey(emotion))
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(visual.startColor, visual.endColor)))
    ) {
        Image(
            painter = painterResource(id = visual.imageRes),
            contentDescription = visual.sceneTitle,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
        )
        if (AppSettingsState.activeDarkTheme.value) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.16f))
            )
        }

//        Surface(
//            modifier = Modifier
//                .align(Alignment.TopStart)
//                .padding(horizontal = 18.dp, vertical = 14.dp),
//            shape = RoundedCornerShape(999.dp),
//            color = EgDesign.card.copy(alpha = 0.88f),
//            border = BorderStroke(1.dp, EgDesign.cardBorder)
//        ) {
//            Text(
//                text = visual.sceneTitle,
//                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
//                color = EgDesign.blue,
//                fontSize = 12.sp,
//                fontWeight = FontWeight.ExtraBold,
//                maxLines = 1,
//                overflow = TextOverflow.Ellipsis
//            )
//        }
//
//        Surface(
//            modifier = Modifier
//                .align(Alignment.BottomStart)
//                .padding(horizontal = 18.dp, vertical = 14.dp)
//                .fillMaxWidth(),
//            shape = RoundedCornerShape(16.dp),
//            color = EgDesign.card.copy(alpha = 0.92f),
//            border = BorderStroke(1.dp, EgDesign.cardBorder)
//        ) {
//            Text(
//                text = visual.caption,
//                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
//                color = EgDesign.textPrimary,
//                fontSize = 13.sp,
//                lineHeight = 18.sp,
//                fontWeight = FontWeight.SemiBold,
//                maxLines = 2,
//                overflow = TextOverflow.Ellipsis
//            )
//        }
    }
}

@Composable
private fun EmotionDetailButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(32.dp)
            .egTactileClick(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = EgDesign.cardSoft,
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Chi tiết", color = EgDesign.blue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmotionDetailBottomSheet(
    content: EmotionDetailContent,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = EgDesign.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = EgDesign.cardSoft,
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EgVectorEmojiIcon(content.emoji, size = 34.dp)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = content.name,
                        color = EgDesign.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = content.shortDescription,
                        color = EgDesign.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
                Surface(
                    modifier = Modifier
                        .height(34.dp)
                        .clickable(onClick = onDismiss),
                    shape = RoundedCornerShape(999.dp),
                    color = EgDesign.cardSoft,
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text("Đóng", color = EgDesign.blue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            EmotionDetailSection("bulb", "Cảm xúc này là gì?", content.whatIsIt)
            EmotionDetailSection("eye", "Dấu hiệu dễ nhận biết", content.signs)
            EmotionDetailSection("puzzle", "Khi nào bé thường có cảm xúc này?", content.situations)
            EmotionDetailSection("sparkle", "Bé nên làm gì?", content.whatToDo)
            EmotionDetailSection("chat", "Nói thế nào cho đúng?", content.sayItLikeThis)
        }
    }
}

@Composable
private fun EmotionDetailSection(icon: String, title: String, items: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = EgDesign.cardSoft,
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EgVectorEmojiIcon(icon, size = 20.dp)
                Text(title, color = EgDesign.blue, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            items.forEach { item ->
                Text(
                    text = "• $item",
                    color = EgDesign.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MediaArrow(text: String, modifier: Modifier, onClick: () -> Unit) {
    if (!LearnMediaArrowsVisible.value) return
    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .size(34.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = EgDesign.card.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = EgDesign.blue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AssetVideoPlayer(
    emotionId: String,
    onControlsVisibleChange: (Boolean) -> Unit = {},
    allowFullscreen: Boolean = true,
    loop: Boolean = true,
    onVideoEnded: () -> Unit = {}
) {
    val context = LocalContext.current
    val assetName = "${egEmotionKey(emotionId)}.mp4"
    val assetPath = remember(assetName) { "fe/assets/videos/$assetName" }
    var isPrepared by remember(assetPath) { mutableStateOf(false) }
    var isPlaying by remember(assetPath) { mutableStateOf(false) }
    var playbackError by remember(assetPath) { mutableStateOf<String?>(null) }
    var controlsVisible by remember(assetPath) { mutableStateOf(true) }
    var showFullscreen by remember(assetPath) { mutableStateOf(false) }
    var currentTextureView by remember(assetPath) { mutableStateOf<TextureView?>(null) }
    var videoWidth by remember(assetPath) { mutableIntStateOf(0) }
    var videoHeight by remember(assetPath) { mutableIntStateOf(0) }
    val mediaPlayer = remember(assetPath) { MediaPlayer() }
    val autoPlayEnabled by AppSettingsState.learnVideoAutoplayEnabled
    val soundEnabled by AppSettingsState.learnVideoSoundEnabled

    LaunchedEffect(loop, isPrepared) {
        if (isPrepared) {
            runCatching {
                mediaPlayer.isLooping = loop
            }
        }
    }

    fun applyVideoFitTransform(textureView: TextureView) {
        val vw = textureView.width.toFloat()
        val vh = textureView.height.toFloat()
        val videoW = videoWidth.toFloat()
        val videoH = videoHeight.toFloat()
        if (vw <= 0f || vh <= 0f || videoW <= 0f || videoH <= 0f) return

        val viewRatio = vw / vh
        val videoRatio = videoW / videoH
        val scaleX: Float
        val scaleY: Float

        if (videoRatio > viewRatio) {
            scaleX = 1f
            scaleY = viewRatio / videoRatio
        } else {
            scaleX = videoRatio / viewRatio
            scaleY = 1f
        }

        val matrix = Matrix().apply {
            setScale(scaleX, scaleY, vw / 2f, vh / 2f)
        }
        textureView.setTransform(matrix)
    }

    LaunchedEffect(controlsVisible) {
        onControlsVisibleChange(controlsVisible)
    }

    LaunchedEffect(isPlaying, controlsVisible) {
        if (isPlaying && controlsVisible) {
            delay(2600)
            if (isPlaying) controlsVisible = false
        }
    }

    LaunchedEffect(showFullscreen) {
        if (showFullscreen) {
            runCatching { mediaPlayer.pause() }
            isPlaying = false
            controlsVisible = false
        }
    }

    LaunchedEffect(mediaPlayer, soundEnabled) {
        runCatching {
            val volume = if (soundEnabled) 1f else 0f
            mediaPlayer.setVolume(volume, volume)
        }
    }

    fun prepare(surface: Surface) {
        isPrepared = false
        playbackError = null
        runCatching {
            mediaPlayer.reset()
            mediaPlayer.setSurface(surface)
            mediaPlayer.isLooping = loop
            mediaPlayer.setOnCompletionListener {
                if (!loop) {
                    isPlaying = false
                    onVideoEnded()
                }
            }
            val volume = if (soundEnabled) 1f else 0f
            mediaPlayer.setVolume(volume, volume)
            mediaPlayer.setOnPreparedListener { player ->
                videoWidth = player.videoWidth
                videoHeight = player.videoHeight
                currentTextureView?.let(::applyVideoFitTransform)
                isPrepared = true
                if (autoPlayEnabled) {
                    player.start()
                    isPlaying = true
                    controlsVisible = true
                } else {
                    player.seekTo(1)
                    isPlaying = false
                    controlsVisible = true
                }
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                playbackError = "Khong mo duoc video mau"
                isPlaying = false
                true
            }
            mediaPlayer.setOnVideoSizeChangedListener { _, width, height ->
                videoWidth = width
                videoHeight = height
                currentTextureView?.let(::applyVideoFitTransform)
            }
            context.assets.openFd(assetPath).use { descriptor ->
                mediaPlayer.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
            }
            mediaPlayer.prepareAsync()
        }.onFailure {
            playbackError = "Khong mo duoc video mau"
            isPlaying = false
        }
    }

    DisposableEffect(mediaPlayer) {
        onDispose {
            runCatching { mediaPlayer.stop() }
            mediaPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = playbackError == null) { controlsVisible = !controlsVisible },
        contentAlignment = Alignment.Center
    ) {
        key(assetPath) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    TextureView(it).apply {
                        currentTextureView = this
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            private var surface: Surface? = null

                            override fun onSurfaceTextureAvailable(
                                surfaceTexture: android.graphics.SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                val newSurface = Surface(surfaceTexture)
                                surface = newSurface
                                applyVideoFitTransform(this@apply)
                                prepare(newSurface)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surfaceTexture: android.graphics.SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                applyVideoFitTransform(this@apply)
                            }

                            override fun onSurfaceTextureDestroyed(
                                surfaceTexture: android.graphics.SurfaceTexture
                            ): Boolean {
                                runCatching { mediaPlayer.pause() }
                                isPlaying = false
                                if (currentTextureView === this@apply) {
                                    currentTextureView = null
                                }
                                surface?.release()
                                surface = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(
                                surfaceTexture: android.graphics.SurfaceTexture
                            ) = Unit
                        }
                    }
                },
                update = { textureView ->
                    currentTextureView = textureView
                    applyVideoFitTransform(textureView)
                }
            )
        }

        if (playbackError != null) {
            Text(
                text = playbackError.orEmpty(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        } else {
            if (allowFullscreen && controlsVisible) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .clickable { showFullscreen = true },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EgVectorEmojiIcon("fullscreen", size = 20.dp, tint = Color.White)
                    }
                }
            }
            if (!controlsVisible && isPlaying) {
                return@Box
            }
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .align(Alignment.Center)
                    .clickable {
                        if (!isPrepared) return@clickable
                        if (isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                            controlsVisible = true
                        } else {
                            mediaPlayer.start()
                            isPlaying = true
                            controlsVisible = true
                        }
                    },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        imageVector = if (isPlaying) androidx.compose.material.icons.Icons.Rounded.Pause else androidx.compose.material.icons.Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    if (allowFullscreen && showFullscreen) {
        FullscreenVideoDialog(
            emotionId = emotionId,
            onDismiss = { showFullscreen = false }
        )
    }
}

@Composable
private fun FullscreenVideoDialog(emotionId: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AssetVideoPlayer(
                emotionId = emotionId,
                allowFullscreen = false
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(42.dp)
                    .clickable(onClick = onDismiss),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("×", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SituationPanel(emotion: EmotionUiItem) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = EgDesign.cardSoft,
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = situationForEmotion(egEmotionKey(emotion)),
                color = EgDesign.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun Dot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(if (active) 10.dp else 8.dp)
            .background(if (active) EgDesign.blue else EgDesign.cardBorder, CircleShape)
    )
}

private fun situationForEmotion(emotionId: String): String {
    return when (egEmotionKey(emotionId)) {
        "happy" -> "Lan được tặng một món quà bất ngờ nên Lan rất vui và mỉm cười."
        "sad" -> "An đánh rơi cây kem yêu thích nên An buồn và muốn khóc."
        "angry" -> "Nam bị bạn giật đồ chơi mà không xin phép nên Nam tức giận."
        "fear" -> "Mai đi lạc mẹ trong siêu thị nên Mai cảm thấy sợ hãi."
        "surprise" -> "Huy mở hộp quà và thấy món đồ chơi mình thích nên rất ngạc nhiên."
        "disgust" -> "Minh ngửi thấy mùi rác thối nên cảm thấy ghê tởm."
        else -> "Hãy quan sát khuôn mặt và cơ thể để đoán cảm xúc của bạn nhỏ."
    }
}

private fun situationVisualForEmotion(emotionId: String): SituationVisualContent {
    return when (egEmotionKey(emotionId)) {
        "happy" -> SituationVisualContent(
            sceneTitle = "Được tặng quà",
            imageRes = R.drawable.learn_scene_happy,
            caption = "Lan vui vì nhận được món quà bất ngờ.",
            startColor = Color(0xFFFFF6C7),
            endColor = Color(0xFFFFE6A3)
        )
        "sad" -> SituationVisualContent(
            sceneTitle = "Rơi cây kem",
            imageRes = R.drawable.learn_scene_sad,
            caption = "An buồn vì cây kem yêu thích bị rơi.",
            startColor = Color(0xFFE8F5FF),
            endColor = Color(0xFFCFE9FF)
        )
        "angry" -> SituationVisualContent(
            sceneTitle = "Bị giành đồ chơi",
            imageRes = R.drawable.learn_scene_angry,
            caption = "Nam tức giận khi bạn lấy đồ chơi.",
            startColor = Color(0xFFFFE0D8),
            endColor = Color(0xFFFFC5B8)
        )
        "fear" -> SituationVisualContent(
            sceneTitle = "Lạc trong siêu thị",
            imageRes = R.drawable.learn_scene_fear,
            caption = "Mai sợ hãi khi chưa nhìn thấy mẹ.",
            startColor = Color(0xFFEAF0FF),
            endColor = Color(0xFFD9E0FF)
        )
        "surprise" -> SituationVisualContent(
            sceneTitle = "Mở hộp quà",
            imageRes = R.drawable.learn_scene_surprise,
            caption = "Huy ngạc nhiên khi thấy món đồ chơi.",
            startColor = Color(0xFFFFEED8),
            endColor = Color(0xFFFFD7A8)
        )
        "disgust" -> SituationVisualContent(
            sceneTitle = "Mùi khó chịu",
            imageRes = R.drawable.learn_scene_disgust,
            caption = "Minh thấy ghê tởm khi ngửi mùi rác.",
            startColor = Color(0xFFE5F9E9),
            endColor = Color(0xFFCFF1D8)
        )
        else -> SituationVisualContent(
            sceneTitle = "Quan sát cảm xúc",
            imageRes = R.drawable.learn_scene_happy,
            caption = "Bé quan sát tình huống và gọi tên cảm xúc.",
            startColor = Color(0xFFEAF7FF),
            endColor = Color(0xFFD9F0FF)
        )
    }
}

private fun rememberTextsForEmotion(emotionId: String): List<String> {
    return when (egEmotionKey(emotionId)) {
        "happy" -> listOf(
            "Bé cảm thấy vui khi được chơi, được khen hoặc gặp người thân.",
            "Khi vui, bé có thể cười và chia sẻ niềm vui với mọi người."
        )
        "sad" -> listOf(
            "Bé có thể buồn khi mất đồ chơi hoặc phải xa người thân.",
            "Khi buồn, bé có thể nói với bố mẹ hoặc cô giáo."
        )
        "angry" -> listOf(
            "Bé có thể tức giận khi bị giành đồ chơi hoặc bị làm đau.",
            "Khi tức giận, bé hãy hít thở chậm và nói với người lớn nhé."
        )
        "fear" -> listOf(
            "Bé có thể sợ khi nghe tiếng lớn hoặc ở nơi tối.",
            "Khi sợ, bé hãy ở gần người lớn và nói điều mình lo lắng."
        )
        "surprise" -> listOf(
            "Bé có thể ngạc nhiên khi thấy điều mới lạ.",
            "Khi ngạc nhiên, bé thường mở to mắt hoặc há miệng nhẹ."
        )
        "disgust" -> listOf(
            "Bé có thể thấy ghê tởm khi ngửi mùi khó chịu hoặc thấy đồ bẩn.",
            "Bé có thể tránh ra xa và nói với người lớn."
        )
        else -> listOf(
            "Bé có thể nhìn mắt, miệng và lông mày để nhận ra cảm xúc.",
            "Khi chưa chắc, bé hãy hỏi người lớn để được giúp nhé."
        )
    }
}

private fun emotionDetailContent(emotion: EmotionUiItem): EmotionDetailContent {
    val key = egEmotionKey(emotion)
    val name = egEmotionDisplayName(emotion)
    val emoji = key
    return when (key) {
        "happy" -> EmotionDetailContent(
            id = key,
            name = name,
            emoji = emoji,
            shortDescription = "Vui vẻ là cảm xúc khi bé cảm thấy hạnh phúc, thích thú hoặc được yêu thương.",
            whatIsIt = listOf(
                "Bé cảm thấy vui khi có điều tốt đẹp xảy ra.",
                "Đây là cảm xúc tích cực và dễ nhận ra."
            ),
            signs = listOf("Cười tươi", "Ánh mắt sáng", "Giọng nói vui vẻ", "Muốn chia sẻ với người khác"),
            situations = listOf(
                "Khi được chơi trò mình thích",
                "Khi được khen",
                "Khi gặp người thân",
                "Khi nhận quà hoặc làm được điều tốt"
            ),
            whatToDo = listOf(
                "Cười và chia sẻ niềm vui với mọi người",
                "Nói điều làm mình vui",
                "Gửi lời cảm ơn nếu ai đó làm mình vui"
            ),
            sayItLikeThis = listOf(
                "“Con đang rất vui.”",
                "“Con vui vì được chơi với bạn.”",
                "“Con vui vì con làm được rồi.”"
            )
        )
        "sad" -> EmotionDetailContent(
            id = key,
            name = name,
            emoji = emoji,
            shortDescription = "Buồn bã là cảm xúc khi bé cảm thấy mất mát, thất vọng hoặc cô đơn.",
            whatIsIt = listOf(
                "Bé có thể buồn khi chuyện không như ý.",
                "Buồn là cảm xúc bình thường, ai cũng có lúc buồn."
            ),
            signs = listOf("Mặt buồn", "Ít cười", "Có thể khóc", "Muốn ngồi yên hoặc ít nói"),
            situations = listOf(
                "Khi mất đồ chơi",
                "Khi bị mắng",
                "Khi nhớ người thân",
                "Khi không được làm điều mình thích"
            ),
            whatToDo = listOf(
                "Nói với bố mẹ, cô giáo hoặc người lớn",
                "Nghỉ ngơi một chút",
                "Ôm người mình tin tưởng",
                "Chia sẻ điều làm mình buồn"
            ),
            sayItLikeThis = listOf(
                "“Con đang buồn.”",
                "“Con buồn vì mất đồ chơi.”",
                "“Con muốn nói chuyện với mẹ.”"
            )
        )
        "angry" -> EmotionDetailContent(
            id = key,
            name = name,
            emoji = emoji,
            shortDescription = "Tức giận là cảm xúc khi bé cảm thấy không hài lòng, bị làm phiền hoặc bị đối xử không công bằng.",
            whatIsIt = listOf(
                "Tức giận xuất hiện khi điều gì đó làm bé khó chịu.",
                "Đây là cảm xúc bình thường, nhưng bé cần học cách thể hiện đúng."
            ),
            signs = listOf("Nhíu mày", "Nói to hơn", "Mặt căng", "Có thể cáu gắt hoặc không muốn nói chuyện"),
            situations = listOf(
                "Khi bị giành đồ chơi",
                "Khi bị trêu chọc",
                "Khi bị làm đau",
                "Khi không được lắng nghe"
            ),
            whatToDo = listOf(
                "Hít thở chậm",
                "Dừng lại vài giây",
                "Nói với người lớn",
                "Nói rõ điều mình không thích thay vì la hét hay đánh bạn"
            ),
            sayItLikeThis = listOf(
                "“Con đang tức giận.”",
                "“Con không thích bạn làm vậy.”",
                "“Con cần giúp đỡ.”"
            )
        )
        "fear" -> EmotionDetailContent(
            id = key,
            name = name,
            emoji = emoji,
            shortDescription = "Sợ hãi là cảm xúc khi bé cảm thấy lo lắng hoặc thấy điều gì đó nguy hiểm.",
            whatIsIt = listOf(
                "Bé có thể sợ những điều lạ, tiếng động lớn hoặc nơi tối.",
                "Cảm xúc này giúp bé cẩn thận hơn."
            ),
            signs = listOf("Mắt mở to", "Ôm chặt ai đó", "Run hoặc lùi lại", "Giọng nói nhỏ hoặc gấp"),
            situations = listOf(
                "Khi ở nơi tối",
                "Khi nghe tiếng động lớn",
                "Khi xem điều đáng sợ",
                "Khi bị lạc"
            ),
            whatToDo = listOf("Đến gần người lớn", "Nói điều mình sợ", "Hít thở chậm", "Xin giúp đỡ"),
            sayItLikeThis = listOf(
                "“Con đang sợ.”",
                "“Con sợ bóng tối.”",
                "“Con muốn ở gần mẹ.”"
            )
        )
        "surprise" -> EmotionDetailContent(
            id = key,
            name = name,
            emoji = emoji,
            shortDescription = "Ngạc nhiên là cảm xúc khi bé gặp một điều bất ngờ hoặc mới lạ.",
            whatIsIt = listOf(
                "Bé ngạc nhiên khi có điều xảy ra ngoài mong đợi.",
                "Cảm xúc này có thể vui hoặc chỉ đơn giản là bất ngờ."
            ),
            signs = listOf("Mắt mở to", "Miệng há nhẹ", "Dừng lại nhìn", "Phản ứng nhanh"),
            situations = listOf(
                "Khi nhận quà bất ngờ",
                "Khi thấy điều mới lạ",
                "Khi ai đó làm bé bất ngờ",
                "Khi nghe một tin lạ"
            ),
            whatToDo = listOf("Bình tĩnh quan sát", "Hỏi người lớn nếu chưa hiểu", "Nói điều làm mình ngạc nhiên"),
            sayItLikeThis = listOf(
                "“Ồ, con ngạc nhiên quá!”",
                "“Con không nghĩ điều này sẽ xảy ra.”",
                "“Cái này làm con bất ngờ.”"
            )
        )
        "disgust" -> EmotionDetailContent(
            id = key,
            name = name,
            emoji = emoji,
            shortDescription = "Ghê tởm là cảm xúc khi bé thấy hoặc ngửi điều gì đó khó chịu.",
            whatIsIt = listOf(
                "Bé có thể thấy ghê khi gặp mùi khó chịu, đồ bẩn hoặc thứ mình không thích.",
                "Đây là cảm xúc giúp bé tránh điều không tốt cho mình."
            ),
            signs = listOf("Nhăn mũi", "Nhăn mặt", "Quay đi chỗ khác", "Tỏ ra không muốn chạm vào"),
            situations = listOf(
                "Khi ngửi mùi khó chịu",
                "Khi thấy đồ ăn hỏng",
                "Khi thấy thứ bẩn",
                "Khi thấy thứ mình rất không thích"
            ),
            whatToDo = listOf(
                "Tránh xa điều gây khó chịu",
                "Nói với người lớn",
                "Rửa tay nếu chạm vào đồ bẩn",
                "Không cố chạm hoặc ngửi tiếp"
            ),
            sayItLikeThis = listOf(
                "“Con thấy ghê.”",
                "“Mùi này làm con khó chịu.”",
                "“Con không thích cái này.”"
            )
        )
        else -> EmotionDetailContent(
            id = key,
            name = name,
            emoji = emoji,
            shortDescription = "Cảm xúc giúp bé hiểu điều đang diễn ra trong lòng mình.",
            whatIsIt = listOf("Bé có thể quan sát cơ thể và khuôn mặt để nhận ra cảm xúc."),
            signs = listOf("Mắt, miệng và lông mày thay đổi", "Giọng nói và hành động cũng có thể thay đổi"),
            situations = listOf("Khi có điều mới xảy ra", "Khi bé gặp người khác hoặc chơi cùng bạn"),
            whatToDo = listOf("Nói với người lớn điều mình cảm thấy", "Hít thở chậm nếu cảm xúc quá mạnh"),
            sayItLikeThis = listOf("“Con đang có cảm xúc này.”", "“Con muốn nói với người lớn.”")
        )
    }
}
