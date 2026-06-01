package com.example.appmobile.ui.pages.photobooth

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgSoftCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PhotoBoothAlbumUiState(
    val items: List<SavedPhotoBoothItem> = emptyList(),
    val selectedItem: SavedPhotoBoothItem? = null,
    val pendingDeleteItem: SavedPhotoBoothItem? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val savedToGalleryItemId: String? = null,
    val message: String? = null
)

class PhotoBoothAlbumViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PhotoBoothAlbumRepository(application.applicationContext)
    private val _state = MutableStateFlow(PhotoBoothAlbumUiState())
    val state: StateFlow<PhotoBoothAlbumUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { repository.load() }
            _state.value = _state.value.copy(items = items, isLoading = false)
        }
    }

    fun open(item: SavedPhotoBoothItem) {
        _state.value = _state.value.copy(selectedItem = item)
    }

    fun closePreview() {
        _state.value = _state.value.copy(selectedItem = null)
    }

    fun saveSelectedToGallery() {
        val current = _state.value
        if (current.isSaving) return
        val selected = current.selectedItem ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, message = "Đang lưu ảnh vào máy...")
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    PhotoBoothGallerySaver.save(getApplication<Application>().applicationContext, selected.photoUri)
                }
            }
            _state.value = _state.value.copy(
                isSaving = false,
                savedToGalleryItemId = if (result.isSuccess) selected.id else _state.value.savedToGalleryItemId,
                message = if (result.isSuccess) "Đã lưu ảnh photobooth vào máy." else "Chưa lưu được ảnh. Con thử lại nhé."
            )
        }
    }

    fun requestDeleteSelected() {
        _state.value = _state.value.copy(pendingDeleteItem = _state.value.selectedItem)
    }

    fun cancelDelete() {
        _state.value = _state.value.copy(pendingDeleteItem = null)
    }

    fun confirmDelete() {
        val selected = _state.value.pendingDeleteItem ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.delete(selected.id) }
            _state.value = _state.value.copy(
                selectedItem = null,
                pendingDeleteItem = null,
                message = "Đã xóa ảnh khỏi album."
            )
            refresh()
        }
    }
}

@Composable
fun PhotoBoothAlbumPage(
    onBack: () -> Unit,
    vm: PhotoBoothAlbumViewModel = viewModel()
) {
    val state by vm.state.collectAsState()

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
        Text("Album EmoGarden Photobooth", color = EgDesign.textPrimary, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text("Những bộ ảnh cảm xúc con đã lưu trong ứng dụng.", color = EgDesign.textSecondary, fontSize = 14.sp)
        state.message?.let { AlbumMessageCard(it) }

        when {
            state.isLoading -> Text("Đang mở album...", color = EgDesign.textSecondary)
            state.items.isEmpty() -> EmptyAlbumCard()
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    AlbumItemCard(item = item, onClick = { vm.open(item) })
                }
            }
        }
    }

    state.selectedItem?.let { item ->
        AlbumPreviewDialog(
            item = item,
            isSaving = state.isSaving,
            isSavedToGallery = state.savedToGalleryItemId == item.id,
            onDismiss = vm::closePreview,
            onSaveToGallery = vm::saveSelectedToGallery,
            onDelete = vm::requestDeleteSelected
        )
    }
    if (state.pendingDeleteItem != null) {
        DeleteAlbumPhotoDialog(onDismiss = vm::cancelDelete, onConfirm = vm::confirmDelete)
    }
}

@Composable
private fun EmptyAlbumCard() {
    EgSoftCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Album chưa có ảnh", color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text("Sau khi chụp xong, con bấm “Thêm vào album” để xem lại bộ ảnh tại đây.", color = EgDesign.textSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AlbumItemCard(item: SavedPhotoBoothItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.photoUri,
                contentDescription = "Ảnh EmoGarden Photobooth",
                modifier = Modifier
                    .width(82.dp)
                    .height(112.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EgDesign.cardSoft),
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(albumItemTitle(item), color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(
                    item.emotionIds.takeIf { it.isNotEmpty() }
                        ?.joinToString(" · ") { PhotoBoothCatalog.emotionName(it) }
                        ?: "Ảnh Photobooth đã lưu",
                    color = EgDesign.blue,
                    fontSize = 13.sp
                )
                Text(formatPhotoDate(item.createdAt), color = EgDesign.textSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AlbumPreviewDialog(
    item: SavedPhotoBoothItem,
    isSaving: Boolean,
    isSavedToGallery: Boolean,
    onDismiss: () -> Unit,
    onSaveToGallery: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ảnh photobooth", color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(EgDesign.cardSoft)
                ) {
                    AsyncImage(
                        model = item.photoUri,
                        contentDescription = "Ảnh EmoGarden Photobooth",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    PhotoBoothDownloadButton(
                        isBusy = isSaving,
                        isSaved = isSavedToGallery,
                        onClick = onSaveToGallery,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                }
                if (item.emotionIds.isNotEmpty()) {
                    Text(
                        "Cảm xúc: " + item.emotionIds.joinToString(" · ") { PhotoBoothCatalog.emotionName(it) },
                        color = EgDesign.textPrimary,
                        fontSize = 13.sp
                    )
                }
                Text("Ngày chụp: ${formatPhotoDate(item.createdAt)}", color = EgDesign.textSecondary, fontSize = 13.sp)
                TextButton(onClick = onDelete, enabled = !isSaving) {
                    Text("Xóa ảnh", color = Color(0xFFE5484D), fontWeight = FontWeight.ExtraBold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng", color = EgDesign.primaryDark) } },
        containerColor = EgDesign.card
    )
}

@Composable
private fun DeleteAlbumPhotoDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xóa ảnh này?", color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold) },
        text = { Text("Ảnh sẽ bị xóa khỏi album Photobooth.", color = EgDesign.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Xóa", color = Color(0xFFE5484D), fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = EgDesign.primaryDark, fontWeight = FontWeight.ExtraBold)
            }
        },
        containerColor = EgDesign.card
    )
}

@Composable
private fun AlbumMessageCard(message: String) {
    EgSoftCard {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            color = EgDesign.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun albumItemTitle(item: SavedPhotoBoothItem): String {
    return if (item.emotionIds.isEmpty()) "Bộ ảnh Photobooth" else "Bộ ảnh ${item.emotionIds.size} cảm xúc"
}

private fun formatPhotoDate(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm").format(
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    )
}
