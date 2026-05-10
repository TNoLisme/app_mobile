package com.example.appmobile.ui.pages.learn

import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.example.appmobile.R
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.EmotionUiItem
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.EgCollapsibleMainScaffold
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgGradientPill
import com.example.appmobile.ui.components.EgSoftCard
import com.example.appmobile.ui.components.EgTab
import com.example.appmobile.ui.components.egEmotionDisplayName
import com.example.appmobile.ui.components.egEmotionIcon
import com.example.appmobile.ui.components.egEmotionKey
import com.example.appmobile.ui.components.egEmotionPastelColor
import com.example.appmobile.ui.components.egLearningEmotionGridItems
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.delay

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
            onSelectDetail = { onSelectEmotion(selectedEmotion.id) }
        )

        EmotionGrid(
            emotions = gridEmotions,
            selectedEmotionId = selectedEmotionId,
            onSelect = { emotion ->
                selectedEmotionId = emotion.id
                pageIndex = 0
            }
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
        emotions.take(6).chunked(3).forEach { rowEmotions ->
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
                repeat(3 - rowEmotions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
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
    Surface(
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, if (selected) EgDesign.primaryDark else EgDesign.cardBorder),
        shadowElevation = if (selected) 2.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .background(if (selected) EgDesign.primary else egEmotionPastelColor(key))
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(egEmotionIcon(key), fontSize = 20.sp, lineHeight = 22.sp)
            Text(
                text = egEmotionDisplayName(emotion),
                color = if (selected) Color.White else EgDesign.textPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
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
                        "${egEmotionIcon(egEmotionKey(emotion))} ${egEmotionDisplayName(emotion)}",
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
                    AssetVideoPlayer(emotionId = egEmotionKey(emotion))
                } else {
                    Image(
                        painter = painterResource(id = rememberEmotionImageResource(egEmotionKey(emotion))),
                        contentDescription = egEmotionDisplayName(emotion),
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
    val key = egEmotionKey(emotionId)
    val assetPath = "fe/assets/videos/$key.mp4"

    var videoPath by remember(assetPath) { mutableStateOf<String?>(null) }
    var errorMessage by remember(assetPath) { mutableStateOf<String?>(null) }
    var isPrepared by remember(assetPath) { mutableStateOf(false) }
    var isPlaying by remember(assetPath) { mutableStateOf(false) }
    var durationMs by remember(assetPath) { mutableIntStateOf(0) }
    var positionMs by remember(assetPath) { mutableIntStateOf(0) }
    val videoViewRef = remember(assetPath) { mutableStateOf<VideoView?>(null) }

    fun playVideo() {
        val view = videoViewRef.value ?: return
        if (durationMs > 0 && positionMs >= durationMs - 300) {
            view.seekTo(0)
            positionMs = 0
        }
        view.requestFocus()
        view.start()
        isPlaying = true
    }

    fun pauseVideo() {
        videoViewRef.value?.pause()
        isPlaying = false
    }

    LaunchedEffect(assetPath) {
        videoPath = null
        errorMessage = null
        isPrepared = false
        isPlaying = false
        durationMs = 0
        positionMs = 0
        runCatching {
            val cachedFile = File(context.cacheDir, "learn_video_$key.mp4")
            if (!cachedFile.exists() || cachedFile.length() == 0L) {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(cachedFile).use { output -> input.copyTo(output) }
                }
            }
            cachedFile.absolutePath
        }.onSuccess { resolvedPath ->
            videoPath = resolvedPath
        }.onFailure {
            errorMessage = "Không tìm thấy video mẫu."
        }
    }

    DisposableEffect(assetPath) {
        onDispose {
            videoViewRef.value?.stopPlayback()
            videoViewRef.value = null
            isPlaying = false
        }
    }

    LaunchedEffect(videoPath) {
        while (videoPath != null) {
            videoViewRef.value?.let { view ->
                val duration = view.duration
                if (duration > 0) durationMs = duration
                if (isPrepared) {
                    positionMs = view.currentPosition.coerceAtLeast(0)
                    isPlaying = view.isPlaying
                }
            }
            delay(250)
        }
    }

    if (videoPath == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f),
            factory = { viewContext ->
                VideoView(viewContext).apply {
                    videoViewRef.value = this
                    tag = videoPath
                    setOnPreparedListener { player ->
                        player.isLooping = true
                        durationMs = player.duration.coerceAtLeast(0)
                        isPrepared = true
                        requestFocus()
                        start()
                        isPlaying = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        positionMs = durationMs
                    }
                    setOnErrorListener { _, _, _ ->
                        errorMessage = "Không phát được video mẫu."
                        isPlaying = false
                        true
                    }
                    setVideoPath(videoPath)
                }
            },
            update = { view ->
                val targetPath = videoPath
                if (targetPath != null && view.tag != targetPath) {
                    view.tag = targetPath
                    isPrepared = false
                    isPlaying = false
                    positionMs = 0
                    view.setVideoPath(targetPath)
                }
            }
        )

        if (!isPrepared && errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier.zIndex(2f),
                color = Color.White
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage.orEmpty(),
                modifier = Modifier
                    .zIndex(3f)
                    .padding(horizontal = 16.dp),
                color = Color.White,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        VideoControlsOverlay(
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            onPlayPause = {
                if (isPlaying) pauseVideo() else playVideo()
            },
            onSeek = { nextPosition ->
                positionMs = nextPosition
                videoViewRef.value?.seekTo(nextPosition)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(2f)
        )
    }
}
@Composable
private fun VideoControlsOverlay(
    positionMs: Int,
    durationMs: Int,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        color = Color.Black.copy(alpha = 0.48f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f))
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isPlaying) "II" else "\u25B6",
                    color = EgDesign.blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = "${formatVideoTime(positionMs)} / ${formatVideoTime(durationMs)}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(74.dp),
                textAlign = TextAlign.Center
            )
            CompactSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = onSeek,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompactSeekBar(
    positionMs: Int,
    durationMs: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1)
    var widthPx by remember { mutableIntStateOf(0) }
    val progress = positionMs.coerceIn(0, safeDuration).toFloat() / safeDuration.toFloat()

    fun positionFromOffset(x: Float): Int {
        val width = widthPx.coerceAtLeast(1).toFloat()
        return ((x.coerceIn(0f, width) / width) * safeDuration).toInt()
    }

    Box(
        modifier = modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(safeDuration, widthPx) {
                detectTapGestures { offset -> onSeek(positionFromOffset(offset.x)) }
            }
            .pointerInput(safeDuration, widthPx) {
                detectDragGestures(
                    onDragStart = { offset -> onSeek(positionFromOffset(offset.x)) },
                    onDrag = { change, _ -> onSeek(positionFromOffset(change.position.x)) }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.35f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(EgDesign.primary)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                .height(24.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

private fun formatVideoTime(milliseconds: Int): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
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
                text = situationForEmotion(egEmotionKey(emotion)),
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
    val resourceName = when (egEmotionKey(emotionId)) {
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
