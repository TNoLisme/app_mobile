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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    onOpenAlbum: () -> Unit,
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
            PhotoBoothPhase.Intro -> PhotoBoothIntro(onBack = onBack, onStart = vm::startPicking, onOpenAlbum = onOpenAlbum)
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
                onGoHome = {
                    vm.resetToIntro()
                    onGoHome()
                },
                onOpenAlbum = onOpenAlbum
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
private fun PhotoBoothIntro(onBack: () -> Unit, onStart: () -> Unit, onOpenAlbum: () -> Unit) {
    PhotoBoothScaffold(onBack = onBack) {
        Text("EmoGarden Photobooth", color = EgDesign.textPrimary, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(
            "Chọn vài cảm xúc, chụp từng biểu cảm rồi ghép thành một bộ ảnh dễ thương.",
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
                EgGradientPill("Bắt đầu", onClick = onStart, modifier = Modifier.fillMaxWidth(), height = 46.dp, fontSize = 14)
                OutlinePill("Xem album", onClick = onOpenAlbum, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun EmotionMultiPicker(state: PhotoBoothUiState, onBack: () -> Unit, vm: PhotoBoothViewModel) {
    PhotoBoothScaffold(onBack = onBack) {
        SectionHeader(
            title = "Chọn cảm xúc",
            subtitle = "Chọn từ 2 đến 4 cảm xúc. Thứ tự chọn sẽ là thứ tự chụp ảnh."
        )
        EmotionPickerGrid(state = state, onToggle = vm::toggleEmotion)
        if (state.selectedEmotionIds.size < 2) {
            Text(
                "Chọn ít nhất 2 cảm xúc để tiếp tục.",
                color = EgDesign.textSecondary,
                fontSize = 13.sp
            )
        }
        state.validationMessage?.let { FriendlyNotice(it, isError = true) }
        EgGradientPill(
            "Tiếp tục",
            onClick = vm::goToFramePicker,
            modifier = Modifier.fillMaxWidth(),
            height = 46.dp,
            fontSize = 14,
            enabled = state.selectedEmotionIds.size >= 2
        )
    }
}

@Composable
private fun FramePicker(state: PhotoBoothUiState, onBack: () -> Unit, vm: PhotoBoothViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EgDesign.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = EgDesign.screenPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppBackButton(onClick = onBack)
        SectionHeader(
            title = "Chọn khung",
            subtitle = "Chọn khung dễ thương cho bộ ảnh của con."
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                TipText("Chọn nơi đủ sáng.")
                TipText("Sau mỗi ảnh, con có thể chụp lại nếu muốn.")
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
            Text(
                "EmoGarden Photobooth",
                modifier = Modifier.weight(1f),
                color = EgDesign.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        EgSoftCard {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EgEmotionVectorIcon(emotionId, size = 42.dp)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (state.phase == PhotoBoothPhase.ReviewingShot) {
                            "Ảnh ${PhotoBoothCatalog.emotionName(emotionId)} của con"
                        } else {
                            "Làm khuôn mặt ${PhotoBoothCatalog.emotionName(emotionId)}"
                        },
                        color = EgDesign.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        if (state.phase == PhotoBoothPhase.ReviewingShot) {
                            "Đẹp lắm! Con muốn dùng ảnh này không?"
                        } else {
                            state.friendlyMessage ?: "Đưa mặt vào giữa khung nhé."
                        },
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
            if (state.phase != PhotoBoothPhase.ReviewingShot) {
                CameraFrameOverlay(frame = frame, countdown = state.countdown)
            }
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
                fontSize = 15,
                enabled = !state.isBusy
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
    onGoHome: () -> Unit,
    onOpenAlbum: () -> Unit
) {
    PhotoBoothScaffold(onBack = onGoHome) {
        SectionHeader(
            title = "Ảnh photobooth của con",
            subtitle = "Con muốn lưu ảnh này không?"
        )
        EgSoftCard {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 420.dp, max = 560.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(EgDesign.cardSoft)
                ) {
                    AsyncImage(
                        model = state.composedPhotoUri,
                        contentDescription = "Ảnh photobooth",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    PhotoBoothDownloadButton(
                        isBusy = state.isBusy,
                        isSaved = state.gallerySaved,
                        onClick = onSaveGallery,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                }
                Text(
                    text = "Cảm xúc: " + state.selectedEmotionIds.joinToString(" · ") { PhotoBoothCatalog.emotionName(it) },
                    color = EgDesign.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
        state.friendlyMessage?.let { FriendlyNotice(message = it, isError = false) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinePill(
                text = if (state.albumSaved) "Đã thêm vào album" else "Thêm vào album",
                onClick = onSaveAlbum,
                modifier = Modifier.weight(1f),
                enabled = !state.isBusy && !state.albumSaved
            )
            OutlinePill("Xem album", onClick = onOpenAlbum, modifier = Modifier.weight(1f), enabled = !state.isBusy)
        }
        OutlinePill("Chụp lại", onClick = onRestart, modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy)
        OutlinePill("Về Trang chủ", onClick = onGoHome, modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy)
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
            topLeft = Offset(4f, 4f),
            size = Size(size.width - 8f, size.height - 8f),
            cornerRadius = CornerRadius(14f, 14f),
            style = Stroke(width = 2.4f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.5f),
            topLeft = Offset(8f, 8f),
            size = Size(size.width - 16f, size.height - 16f),
            cornerRadius = CornerRadius(11f, 11f),
            style = Stroke(width = 1.2f)
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
        drawFramePreviewDecorations(frame.id)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFramePreviewDecorations(frameId: String) {
    when (frameId) {
        "garden_blue" -> {
            drawPreviewLeaf(Offset(size.width * 0.13f, size.height * 0.88f), Color(0xFF65B37D), -28f)
            drawPreviewLeaf(Offset(size.width * 0.22f, size.height * 0.84f), Color(0xFF86C99B), 34f)
            drawPreviewFlower(Offset(size.width * 0.18f, size.height * 0.86f), Color(0xFFFFD86A))
            drawPreviewLeaf(Offset(size.width * 0.86f, size.height * 0.87f), Color(0xFF65B37D), 22f)
            drawPreviewFlower(Offset(size.width * 0.82f, size.height * 0.86f), Color(0xFF7CC8FF))
        }
        "rainbow_feelings" -> {
            repeat(3) { index ->
                drawArc(
                    color = listOf(Color(0xFFFF8A8A), Color(0xFFFFD86A), Color(0xFF7CC8FF))[index],
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.56f + index * 5f, size.height * 0.77f + index * 5f),
                    size = Size(size.width * 0.32f - index * 10f, size.height * 0.18f - index * 10f),
                    style = Stroke(width = 4f)
                )
            }
            drawPreviewCloud(Offset(size.width * 0.22f, size.height * 0.86f))
            drawPreviewCloud(Offset(size.width * 0.78f, size.height * 0.87f))
        }
        "emotion_stickers" -> {
            listOf(
                Offset(size.width * 0.18f, size.height * 0.86f) to Color(0xFFFFD64D),
                Offset(size.width * 0.82f, size.height * 0.86f) to Color(0xFFFF8A35),
                Offset(size.width * 0.84f, size.height * 0.18f) to Color(0xFF86B6FF)
            ).forEach { (center, color) ->
                drawCircle(color, radius = 9f, center = center)
                drawCircle(Color(0xFF243040), radius = 1.5f, center = center + Offset(-3f, -2f))
                drawCircle(Color(0xFF243040), radius = 1.5f, center = center + Offset(3f, -2f))
                drawArc(
                    color = Color(0xFF243040),
                    startAngle = 18f,
                    sweepAngle = 144f,
                    useCenter = false,
                    topLeft = center + Offset(-4f, 1f),
                    size = Size(8f, 5f),
                    style = Stroke(width = 1.4f)
                )
            }
        }
        "flower_booth" -> {
            drawPreviewLeaf(Offset(size.width * 0.13f, size.height * 0.88f), Color(0xFF65B37D), -30f)
            drawPreviewFlower(Offset(size.width * 0.18f, size.height * 0.86f), Color(0xFF7CC8FF))
            drawPreviewFlower(Offset(size.width * 0.29f, size.height * 0.89f), Color(0xFFFFC2D5))
            drawPreviewLeaf(Offset(size.width * 0.88f, size.height * 0.88f), Color(0xFF65B37D), 30f)
            drawPreviewFlower(Offset(size.width * 0.82f, size.height * 0.86f), Color(0xFFFFD86A))
            drawPreviewFlower(Offset(size.width * 0.71f, size.height * 0.89f), Color(0xFF7CC8FF))
        }
        "starry_night" -> {
            drawCircle(Color(0xFFFFE170), radius = 9f, center = Offset(size.width * 0.82f, size.height * 0.16f))
            drawCircle(frameNightColor, radius = 9f, center = Offset(size.width * 0.86f, size.height * 0.13f))
            listOf(
                Offset(size.width * 0.18f, size.height * 0.84f),
                Offset(size.width * 0.82f, size.height * 0.86f),
                Offset(size.width * 0.18f, size.height * 0.18f)
            ).forEach { center ->
                drawPreviewStar(center)
            }
        }
        else -> {
            drawCircle(Color(0xFF7CC8FF), radius = 5f, center = Offset(size.width * 0.18f, size.height * 0.87f))
            drawCircle(Color(0xFFFFD86A), radius = 4f, center = Offset(size.width * 0.82f, size.height * 0.87f))
            drawLine(
                Color.White.copy(alpha = 0.72f),
                Offset(size.width * 0.18f, size.height * 0.91f),
                Offset(size.width * 0.82f, size.height * 0.91f),
                strokeWidth = 2f
            )
        }
    }
}

private val frameNightColor = Color(0xFF172A42)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewLeaf(center: Offset, color: Color, rotation: Float) {
    rotate(rotation, pivot = center) {
        drawOval(
            color = color,
            topLeft = center + Offset(-8f, -4f),
            size = Size(16f, 8f)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewFlower(center: Offset, color: Color) {
    repeat(5) { petal ->
        val angle = Math.toRadians((petal * 72).toDouble())
        drawCircle(
            color = color,
            radius = 3.5f,
            center = Offset(
                center.x + kotlin.math.cos(angle).toFloat() * 6f,
                center.y + kotlin.math.sin(angle).toFloat() * 6f
            )
        )
    }
    drawCircle(Color.White, radius = 2.8f, center = center)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewCloud(center: Offset) {
    drawCircle(Color.White.copy(alpha = 0.9f), radius = 6f, center = center + Offset(-6f, 0f))
    drawCircle(Color.White.copy(alpha = 0.9f), radius = 8f, center = center)
    drawCircle(Color.White.copy(alpha = 0.9f), radius = 5f, center = center + Offset(7f, 1f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewStar(center: Offset) {
    drawLine(Color(0xFFFFE170), center + Offset(-5f, 0f), center + Offset(5f, 0f), strokeWidth = 2f)
    drawLine(Color(0xFFFFE170), center + Offset(0f, -5f), center + Offset(0f, 5f), strokeWidth = 2f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDecorativeDots(frameId: String) {
    val colors = when (frameId) {
        "starry_night" -> listOf(Color(0xFFFFE170), Color(0xFF7CC8FF))
        "flower_booth", "garden_blue" -> listOf(Color(0xFF65B37D), Color(0xFFFFD86A))
        "emotion_stickers" -> listOf(Color(0xFFFF8A35), Color(0xFF86B6FF))
        else -> listOf(Color(0xFF7CC8FF), Color(0xFFFFE28A))
    }
    listOf(
        Offset(size.width * 0.14f, size.height * 0.88f),
        Offset(size.width * 0.86f, size.height * 0.88f)
    ).forEachIndexed { index, offset ->
        drawCircle(colors[index % colors.size], radius = size.minDimension * 0.035f, center = offset)
    }
}

@Composable
private fun MiniStripPreview() {
    Column(
        modifier = Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFE7F7FF), Color(0xFFE9FAEF))))
            .border(2.dp, Color.White, RoundedCornerShape(20.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "EmoGarden",
            color = Color(0xFF0B3A6E),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp
        )
        MiniStripFrame("Vui vẻ", Color(0xFFFFD86A))
        MiniStripFrame("Buồn bã", Color(0xFF86B6FF))
        MiniStripFrame("Tức giận", Color(0xFFFF9B7A))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(Color(0xFF65B37D), Color(0xFF7CC8FF), Color(0xFFFFD86A)).forEach { color ->
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            }
        }
    }
}

@Composable
private fun MiniStripFrame(label: String, accent: Color) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .width(32.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent.copy(alpha = 0.34f))
            )
        }
        Text(label, color = Color(0xFF0B3A6E), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
internal fun PhotoBoothDownloadButton(
    isBusy: Boolean,
    isSaved: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(38.dp)
            .semantics {
                contentDescription = if (isSaved) "Đã tải ảnh xuống" else "Tải ảnh xuống"
            }
            .clickable(enabled = !isBusy && !isSaved, onClick = onClick),
        shape = CircleShape,
        color = if (isSaved) Color(0xFFF0FAF4).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, if (isSaved) Color(0xFF65B37D) else EgDesign.cardBorder),
        shadowElevation = 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(17.dp), color = EgDesign.primaryDark, strokeWidth = 2.dp)
            } else {
                EgVectorEmojiIcon(
                    if (isSaved) "check" else "download",
                    size = 18.dp,
                    tint = if (isSaved) Color(0xFF3A9B63) else EgDesign.primaryDark
                )
            }
        }
    }
}

@Composable
private fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(
        modifier = modifier
            .height(46.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (enabled) EgDesign.card else EgDesign.cardSoft,
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                color = if (enabled) EgDesign.textPrimary else EgDesign.textSecondary,
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
