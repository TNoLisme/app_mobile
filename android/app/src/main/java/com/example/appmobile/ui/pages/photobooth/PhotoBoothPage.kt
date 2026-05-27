package com.example.appmobile.ui.pages.photobooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgEmotionVectorIcon
import com.example.appmobile.ui.components.EgGradientPill
import com.example.appmobile.ui.components.EgSoftCard
import com.example.appmobile.ui.components.EgVectorEmojiIcon
import java.io.File

@Composable
fun PhotoBoothPage(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    vm: PhotoBoothViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val latestPhase = rememberUpdatedState(state.phase)
    val latestImageCapture = rememberUpdatedState(imageCapture)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (granted) {
            if (latestPhase.value == PhotoBoothPhase.PermissionDenied) vm.beginCapture()
        } else {
            vm.onPermissionDenied()
        }
    }

    LaunchedEffect(state.phase) {
        if ((state.phase == PhotoBoothPhase.Capturing || state.phase == PhotoBoothPhase.ReviewingShot) && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is PhotoBoothEvent.CapturePhoto -> {
                    val capture = latestImageCapture.value
                    if (capture == null) {
                        vm.onCaptureError()
                    } else {
                        captureToFile(
                            context = context,
                            imageCapture = capture,
                            outputPath = event.outputPath,
                            onSuccess = vm::onCaptureSuccess,
                            onError = { vm.onCaptureError() }
                        )
                    }
                }
            }
        }
    }

    BackHandler {
        if (state.phase in listOf(
                PhotoBoothPhase.PickingEmotions,
                PhotoBoothPhase.PickingFrame,
                PhotoBoothPhase.Preparing,
                PhotoBoothPhase.Capturing,
                PhotoBoothPhase.ReviewingShot,
                PhotoBoothPhase.PreviewFinal
            )
        ) {
            showExitConfirm = true
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EgDesign.background)
    ) {
        when (state.phase) {
            PhotoBoothPhase.Intro -> PhotoBoothIntro(onBack = onBack, onStart = vm::startPicking)
            PhotoBoothPhase.PickingEmotions -> EmotionMultiPicker(state, onBack = { vm.resetToIntro() }, vm = vm)
            PhotoBoothPhase.PickingFrame -> FramePicker(state = state, onBack = vm::backToEmotionPicker, vm = vm)
            PhotoBoothPhase.Preparing -> PreparationScreen(state = state, onBack = { vm.goToFramePicker() }, onStart = vm::beginCapture)
            PhotoBoothPhase.Capturing,
            PhotoBoothPhase.ReviewingShot -> CaptureSequenceScreen(
                state = state,
                hasCameraPermission = hasCameraPermission,
                onBack = { showExitConfirm = true },
                onImageCaptureReady = { imageCapture = it },
                onCameraError = { vm.onCaptureError() },
                onCapture = vm::requestCapture,
                onAccept = vm::acceptCurrentShot,
                onRetake = vm::retakeCurrentShot
            )
            PhotoBoothPhase.Composing -> ComposingScreen(state = state)
            PhotoBoothPhase.PreviewFinal,
            PhotoBoothPhase.Saving -> FinalPreviewScreen(
                state = state,
                onSaveGallery = vm::saveToGallery,
                onSaveAlbum = vm::saveToAlbum,
                onRestart = vm::restartSession,
                onGoHome = onGoHome
            )
            PhotoBoothPhase.PermissionDenied -> PermissionDeniedScreen(
                onBack = onBack,
                onRetry = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
            PhotoBoothPhase.Error -> ErrorFallbackScreen(
                message = state.errorMessage ?: "Photobooth chưa sẵn sàng. Con thử lại nhé.",
                onRetry = vm::restartSession,
                onBack = onBack
            )
        }

        if (showExitConfirm) {
            ConfirmExitDialog(
                onDismiss = { showExitConfirm = false },
                onExit = {
                    showExitConfirm = false
                    vm.resetToIntro()
                    onBack()
                }
            )
        }
    }
}

@Composable
private fun PhotoBoothIntro(onBack: () -> Unit, onStart: () -> Unit) {
    PhotoBoothScaffold(onBack = onBack) {
        Text("Photobooth cảm xúc", color = EgDesign.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Chọn nhiều cảm xúc, chụp từng ảnh rồi ghép thành một dải ảnh thật dễ thương.",
            color = EgDesign.textSecondary,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
        EgSoftCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MiniStripPreview()
                Text(
                    "Con có thể chọn Vui vẻ, Buồn bã, Tức giận... rồi chụp lần lượt từng khuôn mặt.",
                    color = EgDesign.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                EgGradientPill("Bắt đầu", onClick = onStart, modifier = Modifier.fillMaxWidth(), height = 46.dp, fontSize = 14)
            }
        }
    }
}

@Composable
private fun EmotionMultiPicker(state: PhotoBoothUiState, onBack: () -> Unit, vm: PhotoBoothViewModel) {
    PhotoBoothScaffold(onBack = onBack) {
        SectionHeader(
            title = "Chọn cảm xúc con muốn chụp",
            subtitle = "Chọn từ 2 đến 4 cảm xúc. Thứ tự chọn sẽ là thứ tự chụp ảnh."
        )
        EmotionPickerGrid(state = state, onToggle = vm::toggleEmotion)
        state.validationMessage?.let { FriendlyNotice(it, isError = true) }
        EgGradientPill("Tiếp tục", onClick = vm::goToFramePicker, modifier = Modifier.fillMaxWidth(), height = 46.dp, fontSize = 14)
    }
}

@Composable
private fun FramePicker(state: PhotoBoothUiState, onBack: () -> Unit, vm: PhotoBoothViewModel) {
    PhotoBoothScaffold(onBack = onBack) {
        SectionHeader(
            title = "Chọn khung photobooth",
            subtitle = "Chọn khung dễ thương cho bộ ảnh cảm xúc của con."
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoBoothCatalog.frames.forEach { frame ->
                FrameTemplateCard(
                    frame = frame,
                    selected = state.selectedFrameId == frame.id,
                    layout = state.selectedLayout,
                    onClick = { vm.selectFrame(frame.id) }
                )
            }
        }
        EgGradientPill("Tiếp tục chụp", onClick = vm::goToPreparation, modifier = Modifier.fillMaxWidth(), height = 46.dp, fontSize = 14)
    }
}

@Composable
private fun PreparationScreen(state: PhotoBoothUiState, onBack: () -> Unit, onStart: () -> Unit) {
    PhotoBoothScaffold(onBack = onBack) {
        SectionHeader(
            title = "Chuẩn bị chụp",
            subtitle = "Con sẽ chụp lần lượt từng cảm xúc theo thứ tự này."
        )
        EgSoftCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.selectedEmotionIds.forEachIndexed { index, emotionId ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberBadge(index + 1)
                        EgEmotionVectorIcon(emotionId, size = 36.dp)
                        Text(
                            PhotoBoothCatalog.emotionName(emotionId),
                            color = EgDesign.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
        EgSoftCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EgVectorEmojiIcon("camera", size = 26.dp)
                    Text("Mẹo nhỏ", color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
                TipText("Đưa khuôn mặt vào giữa khung.")
                TipText("Giữ điện thoại ổn định khi chụp.")
                TipText("Sau mỗi ảnh, con có thể dùng ảnh đó hoặc chụp lại.")
            }
        }
        EgGradientPill("Bắt đầu chụp", onClick = onStart, modifier = Modifier.fillMaxWidth(), height = 46.dp, fontSize = 14)
    }
}

@Composable
private fun CaptureSequenceScreen(
    state: PhotoBoothUiState,
    hasCameraPermission: Boolean,
    onBack: () -> Unit,
    onImageCaptureReady: (ImageCapture?) -> Unit,
    onCameraError: (Throwable) -> Unit,
    onCapture: () -> Unit,
    onAccept: () -> Unit,
    onRetake: () -> Unit
) {
    val emotionId = state.selectedEmotionIds.getOrNull(state.currentStepIndex).orEmpty()
    val frame = PhotoBoothCatalog.frame(state.selectedFrameId)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EgDesign.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = EgDesign.screenPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppBackButton(onClick = onBack)
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = EgDesign.card,
                border = BorderStroke(1.dp, EgDesign.cardBorder)
            ) {
                Text(
                    text = "Ảnh ${state.currentStepIndex + 1}/${state.selectedEmotionIds.size}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = EgDesign.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
        }
        Text("Photobooth cảm xúc", color = EgDesign.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        EgSoftCard {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EgEmotionVectorIcon(emotionId, size = 42.dp)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Con hãy làm khuôn mặt ${PhotoBoothCatalog.emotionName(emotionId)}",
                        color = EgDesign.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    Text(
                        state.friendlyMessage ?: "Đưa khuôn mặt vào giữa khung nhé.",
                        color = EgDesign.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                PhotoBoothCameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onImageCaptureReady = onImageCaptureReady,
                    onCameraError = onCameraError
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Đang xin quyền camera...", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            if (state.phase == PhotoBoothPhase.ReviewingShot && !state.currentPreviewUri.isNullOrBlank()) {
                AsyncImage(
                    model = state.currentPreviewUri,
                    contentDescription = "Ảnh vừa chụp",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            CameraFrameOverlay(frame = frame, countdown = state.countdown)
        }
        state.errorMessage?.let { FriendlyNotice(message = it, isError = true) }
        if (state.phase == PhotoBoothPhase.ReviewingShot) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinePill("Chụp lại", onClick = onRetake, modifier = Modifier.weight(1f))
                EgGradientPill("Dùng ảnh này", onClick = onAccept, modifier = Modifier.weight(1f), height = 46.dp, fontSize = 14)
            }
        } else {
            EgGradientPill(
                text = if (state.isBusy) "Đang chụp..." else "Chụp",
                onClick = onCapture,
                modifier = Modifier.fillMaxWidth(),
                height = 50.dp,
                fontSize = 15
            )
        }
    }
}

@Composable
private fun ComposingScreen(state: PhotoBoothUiState) {
    PhotoBoothPlainScreen {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EgSoftCard {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = EgDesign.primary)
                    Text("Đang ghép ảnh photobooth...", color = EgDesign.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Chờ một chút nhé.", color = EgDesign.textSecondary, fontSize = 14.sp)
                    ShotThumbnailRow(state)
                }
            }
        }
    }
}

@Composable
private fun FinalPreviewScreen(
    state: PhotoBoothUiState,
    onSaveGallery: () -> Unit,
    onSaveAlbum: () -> Unit,
    onRestart: () -> Unit,
    onGoHome: () -> Unit
) {
    PhotoBoothScaffold(onBack = onGoHome) {
        SectionHeader(
            title = "Ảnh photobooth của con",
            subtitle = "Con muốn lưu ảnh này không?"
        )
        EgSoftCard {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = state.composedPhotoUri,
                    contentDescription = "Ảnh photobooth",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 420.dp, max = 560.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(EgDesign.cardSoft),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = "Cảm xúc: " + state.selectedEmotionIds.joinToString(" · ") { PhotoBoothCatalog.emotionName(it) },
                    color = EgDesign.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
        state.friendlyMessage?.let { FriendlyNotice(message = it, isError = false) }
        EgGradientPill(
            text = if (state.isBusy) "Đang lưu..." else "Lưu vào máy",
            onClick = onSaveGallery,
            modifier = Modifier.fillMaxWidth(),
            height = 46.dp,
            fontSize = 14
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinePill("Lưu album", onClick = onSaveAlbum, modifier = Modifier.weight(1f))
            OutlinePill("Chụp lại", onClick = onRestart, modifier = Modifier.weight(1f))
        }
        OutlinePill("Về Trang chủ", onClick = onGoHome, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PermissionDeniedScreen(onBack: () -> Unit, onRetry: () -> Unit) {
    PhotoBoothScaffold(onBack = onBack) {
        EgSoftCard {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EgVectorEmojiIcon("camera", size = 52.dp)
                Text("Cần quyền camera", color = EgDesign.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "EmoGarden cần camera để con chụp ảnh photobooth. Ứng dụng không lưu ảnh nếu con chưa bấm lưu.",
                    color = EgDesign.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                EgGradientPill("Cho phép camera", onClick = onRetry, modifier = Modifier.fillMaxWidth(), height = 46.dp, fontSize = 14)
            }
        }
    }
}

@Composable
private fun ErrorFallbackScreen(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    PhotoBoothScaffold(onBack = onBack) {
        FriendlyNotice(message = message, isError = true)
        EgGradientPill("Thử lại", onClick = onRetry, modifier = Modifier.fillMaxWidth(), height = 46.dp, fontSize = 14)
    }
}

@Composable
private fun PhotoBoothScaffold(onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EgDesign.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = EgDesign.screenPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppBackButton(onClick = onBack)
        content()
    }
}

@Composable
private fun PhotoBoothPlainScreen(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EgDesign.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(EgDesign.screenPadding)
    ) {
        content()
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = EgDesign.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 29.sp)
        Text(subtitle, color = EgDesign.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun EmotionPickerGrid(state: PhotoBoothUiState, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PhotoBoothCatalog.emotions.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { emotion ->
                    val order = state.selectedEmotionIds.indexOf(emotion.id).takeIf { it >= 0 }?.plus(1)
                    EmotionSelectCard(
                        emotion = emotion,
                        order = order,
                        onClick = { onToggle(emotion.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmotionSelectCard(
    emotion: PhotoBoothEmotion,
    order: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = order != null
    Card(
        modifier = modifier
            .height(116.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) EgDesign.cardSoft else EgDesign.card),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) EgDesign.primaryDark else EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 1.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                EgEmotionVectorIcon(emotion.id, size = 42.dp)
                Text(emotion.name, color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            order?.let {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).size(26.dp),
                    shape = CircleShape,
                    color = EgDesign.primaryDark
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$it", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FrameTemplateCard(
    frame: PhotoBoothFrameTemplate,
    selected: Boolean,
    layout: PhotoBoothLayoutType,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) EgDesign.primaryDark else EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PhotoBoothFramePreview(frame = frame, layout = layout, modifier = Modifier.size(width = 92.dp, height = 128.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(frame.name, color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(frame.description, color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
            if (selected) {
                EgVectorEmojiIcon("check", size = 24.dp, tint = EgDesign.primaryDark)
            }
        }
    }
}

@Composable
private fun PhotoBoothFramePreview(frame: PhotoBoothFrameTemplate, layout: PhotoBoothLayoutType, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, EgDesign.cardBorder, RoundedCornerShape(16.dp))
    ) {
        drawRect(
            Brush.linearGradient(
                listOf(frame.primaryColor, frame.secondaryColor),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.88f),
            topLeft = Offset(size.width * 0.12f, size.height * 0.10f),
            size = Size(size.width * 0.76f, size.height * 0.06f),
            cornerRadius = CornerRadius(999f, 999f)
        )
        if (layout == PhotoBoothLayoutType.Grid2x2) {
            val cellW = size.width * 0.31f
            val cellH = size.height * 0.25f
            val lefts = listOf(size.width * 0.16f, size.width * 0.53f)
            val tops = listOf(size.height * 0.25f, size.height * 0.58f)
            tops.forEach { top ->
                lefts.forEach { left ->
                    drawRoundRect(Color.White, Offset(left, top), Size(cellW, cellH), CornerRadius(8f, 8f))
                }
            }
        } else {
            repeat(3) { index ->
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(size.width * 0.16f, size.height * (0.23f + index * 0.22f)),
                    size = Size(size.width * 0.68f, size.height * 0.16f),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
        }
        drawDecorativeDots(frame.id)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDecorativeDots(frameId: String) {
    val colors = when (frameId) {
        "starry_night" -> listOf(Color(0xFFFFE170), Color(0xFF7CC8FF))
        "flower_booth", "garden_blue" -> listOf(Color(0xFF65B37D), Color(0xFFFFD86A))
        "emotion_stickers" -> listOf(Color(0xFFFF8A35), Color(0xFF86B6FF))
        else -> listOf(Color(0xFF7CC8FF), Color(0xFFFFE28A))
    }
    listOf(
        Offset(size.width * 0.12f, size.height * 0.20f),
        Offset(size.width * 0.88f, size.height * 0.20f),
        Offset(size.width * 0.14f, size.height * 0.88f),
        Offset(size.width * 0.86f, size.height * 0.88f)
    ).forEachIndexed { index, offset ->
        drawCircle(colors[index % colors.size], radius = size.minDimension * 0.035f, center = offset)
    }
}

@Composable
private fun MiniStripPreview() {
    Canvas(modifier = Modifier.size(width = 150.dp, height = 220.dp)) {
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFFE7F7FF), Color(0xFFFFF5CA))),
            cornerRadius = CornerRadius(28f, 28f)
        )
        repeat(3) { index ->
            val top = 34f + index * 56f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(24f, top),
                size = Size(size.width - 48f, 42f),
                cornerRadius = CornerRadius(12f, 12f)
            )
            drawCircle(Color(0xFFFFD64D), 10f, Offset(42f, top + 21f))
            drawRoundRect(
                color = Color(0xFFD8ECFF),
                topLeft = Offset(62f, top + 14f),
                size = Size(56f, 8f),
                cornerRadius = CornerRadius(999f, 999f)
            )
        }
        drawRoundRect(
            color = Color(0xFF0B3A6E).copy(alpha = 0.75f),
            topLeft = Offset(36f, size.height - 32f),
            size = Size(size.width - 72f, 8f),
            cornerRadius = CornerRadius(999f, 999f)
        )
    }
}

@Composable
private fun CameraFrameOverlay(frame: PhotoBoothFrameTemplate, countdown: Int?) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val color = if (frame.id == "starry_night") Color(0xFF7CC8FF) else frame.secondaryColor
            val guide = Size(size.width * 0.68f, size.width * 0.86f)
            val topLeft = Offset((size.width - guide.width) / 2f, (size.height - guide.height) / 2f)
            drawRoundRect(
                color = color.copy(alpha = 0.78f),
                topLeft = topLeft,
                size = guide,
                cornerRadius = CornerRadius(42f, 42f),
                style = Stroke(width = 5f)
            )
            drawDecorativeDots(frame.id)
        }
        if (countdown != null) {
            Surface(
                modifier = Modifier.align(Alignment.Center).size(90.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.48f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$countdown", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun ShotThumbnailRow(state: PhotoBoothUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        state.shots.take(4).forEach { shot ->
            AsyncImage(
                model = shot.photoUri,
                contentDescription = "Ảnh ${PhotoBoothCatalog.emotionName(shot.emotionId)}",
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(EgDesign.cardSoft),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun NumberBadge(number: Int) {
    Surface(shape = CircleShape, color = EgDesign.primaryDark, modifier = Modifier.size(28.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text("$number", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun TipText(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(EgDesign.primaryDark))
        Text(text, color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun FriendlyNotice(message: String, isError: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isError) Color(0xFFFFE7E7) else EgDesign.cardSoft,
        border = BorderStroke(1.dp, if (isError) Color(0xFFFFB4B4) else EgDesign.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EgVectorEmojiIcon(if (isError) "warning" else "sparkle", size = 22.dp)
            Text(message, color = if (isError) Color(0xFFB42318) else EgDesign.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(46.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = EgDesign.card,
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                color = EgDesign.textPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConfirmExitDialog(onDismiss: () -> Unit, onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thoát Photobooth?", color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold) },
        text = { Text("Nếu thoát, các ảnh vừa chụp sẽ không được giữ lại.", color = EgDesign.textSecondary) },
        confirmButton = {
            TextButton(onClick = onExit) {
                Text("Thoát", color = Color(0xFFE5484D), fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Ở lại", color = EgDesign.primaryDark, fontWeight = FontWeight.ExtraBold)
            }
        },
        containerColor = EgDesign.card
    )
}

@Composable
private fun PhotoBoothCameraPreview(
    modifier: Modifier = Modifier,
    onImageCaptureReady: (ImageCapture?) -> Unit,
    onCameraError: (Throwable) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val readyCallback = rememberUpdatedState(onImageCaptureReady)
    val errorCallback = rememberUpdatedState(onCameraError)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                    readyCallback.value.invoke(imageCapture)
                }.onFailure { error ->
                    Log.w("PhotoBooth", "Cannot start camera", error)
                    errorCallback.value.invoke(error)
                }
            },
            mainExecutor
        )
        onDispose {
            readyCallback.value.invoke(null)
            runCatching {
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
            }
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun captureToFile(
    context: Context,
    imageCapture: ImageCapture,
    outputPath: String,
    onSuccess: (String) -> Unit,
    onError: () -> Unit
) {
    val outputFile = File(outputPath).apply { parentFile?.mkdirs() }
    val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = true }
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile)
        .setMetadata(metadata)
        .build()
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSuccess(outputPath)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.w("PhotoBooth", "Cannot capture photo", exception)
                onError()
            }
        }
    )
}
