package com.example.appmobile.ui.pages.photobooth

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appmobile.data.garden.GardenRepository
import com.example.appmobile.data.garden.LearningEvent
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoBoothViewModel(application: Application) : AndroidViewModel(application) {
    private val gardenRepository = GardenRepository(application.applicationContext)
    private val albumRepository = PhotoBoothAlbumRepository(application.applicationContext)
    private val _state = MutableStateFlow(PhotoBoothUiState())
    val state: StateFlow<PhotoBoothUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PhotoBoothEvent>()
    val events: SharedFlow<PhotoBoothEvent> = _events.asSharedFlow()

    private var countdownJob: Job? = null
    private var gardenEventSent = false

    fun startPicking() {
        _state.update {
            it.copy(
                phase = PhotoBoothPhase.PickingEmotions,
                validationMessage = null,
                friendlyMessage = null,
                errorMessage = null
            )
        }
    }

    fun toggleEmotion(emotionId: String) {
        _state.update { current ->
            val selected = current.selectedEmotionIds
            val next = when {
                emotionId in selected -> selected.filterNot { it == emotionId }
                selected.size >= MaxEmotionCount -> selected
                else -> selected + emotionId
            }
            current.copy(
                selectedEmotionIds = next,
                selectedLayout = PhotoBoothCatalog.layoutFor(next.size),
                validationMessage = if (emotionId !in selected && selected.size >= MaxEmotionCount) {
                    "Con chỉ nên chọn tối đa 4 cảm xúc cho một bộ ảnh nhé."
                } else {
                    null
                }
            )
        }
    }

    fun goToFramePicker() {
        _state.update { current ->
            if (current.selectedEmotionIds.size < MinEmotionCount) {
                current.copy(validationMessage = "Con hãy chọn ít nhất 2 cảm xúc nhé.")
            } else {
                current.copy(
                    phase = PhotoBoothPhase.PickingFrame,
                    selectedLayout = PhotoBoothCatalog.layoutFor(current.selectedEmotionIds.size),
                    validationMessage = null,
                    friendlyMessage = null
                )
            }
        }
    }

    fun selectFrame(frameId: String) {
        _state.update { it.copy(selectedFrameId = frameId) }
    }

    fun goToPreparation() {
        _state.update { it.copy(phase = PhotoBoothPhase.Preparing, validationMessage = null) }
    }

    fun backToEmotionPicker() {
        _state.update { it.copy(phase = PhotoBoothPhase.PickingEmotions, validationMessage = null) }
    }

    fun beginCapture() {
        countdownJob?.cancel()
        clearTemporaryShots()
        gardenEventSent = false
        _state.update {
            it.copy(
                phase = PhotoBoothPhase.Capturing,
                currentStepIndex = 0,
                shots = emptyList(),
                currentPreviewUri = null,
                composedPhotoUri = null,
                countdown = null,
                friendlyMessage = "Đưa khuôn mặt vào giữa khung nhé.",
                errorMessage = null,
                isBusy = false,
                gallerySaved = false,
                albumSaved = false
            )
        }
    }

    fun requestCapture() {
        val current = _state.value
        if (current.phase != PhotoBoothPhase.Capturing || current.isBusy) return
        val emotionId = current.selectedEmotionIds.getOrNull(current.currentStepIndex) ?: return
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (value in 3 downTo 1) {
                _state.update { it.copy(countdown = value, isBusy = true, friendlyMessage = "Chuẩn bị chụp...") }
                delay(650L)
            }
            val outputFile = createTempShotFile(emotionId)
            _events.emit(PhotoBoothEvent.CapturePhoto(outputFile.absolutePath))
        }
    }

    fun onCaptureSuccess(outputPath: String) {
        _state.update {
            it.copy(
                phase = PhotoBoothPhase.ReviewingShot,
                currentPreviewUri = Uri.fromFile(File(outputPath)).toString(),
                countdown = null,
                isBusy = false,
                friendlyMessage = "Đẹp lắm! Con có muốn dùng ảnh này không?",
                errorMessage = null
            )
        }
    }

    fun onCaptureError() {
        _state.update {
            it.copy(
                phase = PhotoBoothPhase.Capturing,
                countdown = null,
                isBusy = false,
                friendlyMessage = null,
                errorMessage = "Chưa chụp được ảnh. Con thử lại nhé."
            )
        }
    }

    fun acceptCurrentShot() {
        val current = _state.value
        val uri = current.currentPreviewUri ?: return
        val emotionId = current.selectedEmotionIds.getOrNull(current.currentStepIndex) ?: return
        val updatedShots = current.shots
            .filterNot { it.emotionId == emotionId }
            .plus(PhotoBoothShot(emotionId = emotionId, photoUri = uri))

        if (current.currentStepIndex >= current.selectedEmotionIds.lastIndex) {
            _state.update {
                it.copy(
                    shots = updatedShots,
                    currentPreviewUri = null,
                    phase = PhotoBoothPhase.Composing,
                    friendlyMessage = "Đang ghép ảnh photobooth..."
                )
            }
            composeFinalPhoto(updatedShots)
        } else {
            val nextIndex = current.currentStepIndex + 1
            val nextName = PhotoBoothCatalog.emotionName(current.selectedEmotionIds[nextIndex])
            _state.update {
                it.copy(
                    shots = updatedShots,
                    currentStepIndex = nextIndex,
                    currentPreviewUri = null,
                    phase = PhotoBoothPhase.Capturing,
                    friendlyMessage = "Tiếp theo là $nextName.",
                    errorMessage = null
                )
            }
        }
    }

    fun retakeCurrentShot() {
        deleteFile(_state.value.currentPreviewUri)
        _state.update {
            it.copy(
                phase = PhotoBoothPhase.Capturing,
                currentPreviewUri = null,
                countdown = null,
                friendlyMessage = "Con thử chụp lại ảnh này nhé.",
                errorMessage = null,
                isBusy = false
            )
        }
    }

    fun onPermissionDenied() {
        _state.update {
            it.copy(
                phase = PhotoBoothPhase.PermissionDenied,
                isBusy = false,
                countdown = null,
                errorMessage = "EmoGarden cần quyền camera để chụp photobooth."
            )
        }
    }

    fun saveToGallery() {
        val current = _state.value
        if (current.isBusy || current.gallerySaved) return
        val uri = current.composedPhotoUri ?: return
        val selectedEmotions = current.selectedEmotionIds
        viewModelScope.launch {
            _state.update { it.copy(phase = PhotoBoothPhase.Saving, isBusy = true, friendlyMessage = "Đang lưu ảnh vào máy...") }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    PhotoBoothGallerySaver.save(getApplication<Application>().applicationContext, uri)
                }
            }
            _state.update {
                it.copy(
                    phase = PhotoBoothPhase.PreviewFinal,
                    isBusy = false,
                    gallerySaved = it.gallerySaved || result.isSuccess,
                    friendlyMessage = if (result.isSuccess) "Đã lưu ảnh photobooth vào máy." else "Chưa lưu được ảnh. Con thử lại nhé."
                )
            }
            if (result.isSuccess) {
                recordGardenEventOnce(selectedEmotions)
            }
        }
    }

    fun saveToAlbum() {
        val current = _state.value
        if (current.isBusy || current.albumSaved) return
        val uri = current.composedPhotoUri ?: return
        val selectedEmotions = current.selectedEmotionIds
        viewModelScope.launch {
            _state.update { it.copy(phase = PhotoBoothPhase.Saving, isBusy = true, friendlyMessage = "Đang lưu vào album...") }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    saveFinalPhotoToAppAlbum(
                        uriString = uri,
                        emotionIds = selectedEmotions,
                        frameId = current.selectedFrameId,
                        layoutType = current.selectedLayout
                    )
                }
            }
            _state.update {
                it.copy(
                    phase = PhotoBoothPhase.PreviewFinal,
                    isBusy = false,
                    albumSaved = it.albumSaved || result.isSuccess,
                    friendlyMessage = if (result.isSuccess) "Đã thêm vào album photobooth." else "Chưa thêm được vào album. Con thử lại nhé."
                )
            }
            if (result.isSuccess) {
                recordGardenEventOnce(selectedEmotions)
            }
        }
    }

    fun restartSession() {
        countdownJob?.cancel()
        clearSessionFiles()
        gardenEventSent = false
        _state.update {
            PhotoBoothUiState(
                phase = PhotoBoothPhase.PickingEmotions,
                selectedFrameId = it.selectedFrameId
            )
        }
    }

    fun resetToIntro() {
        countdownJob?.cancel()
        clearSessionFiles()
        gardenEventSent = false
        _state.update { PhotoBoothUiState() }
    }

    fun clearMessage() {
        _state.update { it.copy(friendlyMessage = null, errorMessage = null) }
    }

    private fun composeFinalPhoto(shots: List<PhotoBoothShot>) {
        val snapshot = _state.value
        val frame = PhotoBoothCatalog.frame(snapshot.selectedFrameId)
        val layout = snapshot.selectedLayout
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    PhotoBoothComposer.compose(
                        context = getApplication<Application>().applicationContext,
                        shots = shots,
                        frameTemplate = frame,
                        layoutType = layout,
                        createdDateText = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        outputDir = File(getApplication<Application>().cacheDir, "photobooth/final")
                    )
                }
            }
            if (result.isSuccess) {
                clearTemporaryShots()
            }
            _state.update {
                if (result.isSuccess) {
                    it.copy(
                        phase = PhotoBoothPhase.PreviewFinal,
                        composedPhotoUri = result.getOrNull(),
                        friendlyMessage = "Ảnh photobooth của con đã sẵn sàng.",
                        errorMessage = null,
                        isBusy = false
                    )
                } else {
                    it.copy(
                        phase = PhotoBoothPhase.Error,
                        errorMessage = "Chưa ghép được ảnh. Con thử lại nhé.",
                        isBusy = false
                    )
                }
            }
        }
    }

    private fun createTempShotFile(emotionId: String): File {
        val dir = File(getApplication<Application>().cacheDir, "photobooth/shots").apply { mkdirs() }
        return File(dir, "shot_${emotionId}_${System.currentTimeMillis()}.jpg")
    }

    private fun saveFinalPhotoToAppAlbum(
        uriString: String,
        emotionIds: List<String>,
        frameId: String,
        layoutType: PhotoBoothLayoutType
    ) {
        val source = fileFromUri(uriString)
        albumRepository.save(
            source = source,
            sourceUri = uriString,
            emotionIds = emotionIds,
            frameId = frameId,
            layoutType = layoutType
        )
    }

    private fun fileFromUri(uriString: String): File {
        val uri = Uri.parse(uriString)
        val path = uri.path ?: throw IllegalArgumentException("Invalid photo")
        return File(path)
    }

    override fun onCleared() {
        countdownJob?.cancel()
        clearTemporaryShots()
        super.onCleared()
    }

    private fun clearSessionFiles() {
        clearTemporaryShots()
        File(getApplication<Application>().cacheDir, "photobooth/final").deleteRecursively()
    }

    private fun clearTemporaryShots() {
        File(getApplication<Application>().cacheDir, "photobooth/shots").deleteRecursively()
    }

    private fun deleteFile(uriString: String?) {
        uriString ?: return
        runCatching { fileFromUri(uriString).delete() }
    }

    private suspend fun recordGardenEventOnce(selectedEmotions: List<String>) {
        if (gardenEventSent) return
        gardenEventSent = true
        gardenRepository.onLearningEvent(LearningEvent.PhotoBoothSaved(selectedEmotions))
    }

    private companion object {
        const val MinEmotionCount = 2
        const val MaxEmotionCount = 4
    }
}
