package com.example.appmobile.ui.pages.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.EmotionAccuracyDto
import com.example.appmobile.data.remote.dto.RecentGameDto
import com.example.appmobile.data.remote.dto.UserProfileDto
import com.example.appmobile.data.remote.dto.UserProfileUpdateDto
import com.example.appmobile.data.remote.dto.WeakEmotionDto
import com.example.appmobile.data.repository.UserRepository
import com.example.appmobile.ui.theme.SoftWhite
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun ProfilePage(onBack: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "local-player" }
    val repository = remember {
        UserRepository(
            NetworkClient.apiService,
            FirebaseAuthHelper(),
            AppDatabase.getDatabase(context).userDao()
        )
    }

    var profile by remember { mutableStateOf<UserProfileDto?>(null) }
    var recentGames by remember { mutableStateOf<List<RecentGameDto>>(emptyList()) }
    var accuracy by remember { mutableStateOf<Map<String, EmotionAccuracyDto>>(emptyMap()) }
    var weakEmotions by remember { mutableStateOf<List<WeakEmotionDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showEdit by remember { mutableStateOf(false) }

    suspend fun loadProfileData() {
        loading = true
        profile = repository.getProfile(userId)
        recentGames = repository.getRecentGames(userId)
        accuracy = repository.getEmotionAccuracy(userId)
        weakEmotions = repository.getWeakEmotions(userId)
        message = if (profile == null) "Chưa tải được hồ sơ từ backend." else null
        loading = false
    }

    LaunchedEffect(userId) {
        loadProfileData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Quay lại") }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onLogout) { Text("Đăng xuất", color = Color.Red) }
        }

        Text("Hồ sơ của bé", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1976D2))
        Text("Thông tin tài khoản và tiến độ luyện tập", color = Color.Gray)
        Spacer(modifier = Modifier.height(20.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        message?.let {
            Surface(shape = MaterialTheme.shapes.large, color = Color(0xFFFFEBEE), modifier = Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(12.dp), color = Color(0xFFC62828))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        ProfileHeaderCard(profile)
        Spacer(modifier = Modifier.height(16.dp))

        ProfileInfoGrid(profile)
        Spacer(modifier = Modifier.height(16.dp))

        ProfileStatsCard(
            recentGames = recentGames,
            accuracy = accuracy,
            weakEmotions = weakEmotions
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { showEdit = true }, modifier = Modifier.weight(1f)) {
                Text("Chỉnh sửa")
            }
            OutlinedButton(
                onClick = { scope.launch { loadProfileData() } },
                modifier = Modifier.weight(1f)
            ) {
                Text("Tải lại")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        RecentGamesCard(recentGames)
        Spacer(modifier = Modifier.height(16.dp))
        EmotionAccuracyCard(accuracy, weakEmotions)
    }

    if (showEdit && profile != null) {
        EditProfileDialog(
            profile = profile,
            saving = saving,
            onDismiss = { if (!saving) showEdit = false },
            onSave = { update ->
                scope.launch {
                    saving = true
                    val updated = repository.updateProfile(userId, update)
                    saving = false
                    if (updated != null) {
                        profile = updated
                        message = "Đã cập nhật hồ sơ."
                        showEdit = false
                    } else {
                        message = "Cập nhật hồ sơ chưa thành công."
                    }
                }
            }
        )
    }
}

@Composable
private fun ProfileHeaderCard(profile: UserProfileDto?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(modifier = Modifier.size(86.dp), shape = CircleShape, color = Color(0xFFE3F2FD)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Bé", fontWeight = FontWeight.ExtraBold, color = Color(0xFF1976D2), fontSize = 24.sp)
                }
            }
            Text(profile?.name ?: "Bé yêu", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(profile?.email ?: "Chưa có email", color = Color.Gray)
        }
    }
}

@Composable
private fun ProfileInfoGrid(profile: UserProfileDto?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            InfoTile("Tên đăng nhập", profile?.username ?: "---", Modifier.weight(1f))
            InfoTile("Tuổi", profile?.child?.age?.let { "$it tuổi" } ?: "---", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            InfoTile("Giới tính", displayGender(profile?.child?.gender), Modifier.weight(1f))
            InfoTile("Ngày tham gia", formatDate(profile?.createdAt), Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
            Text(value, color = Color.DarkGray)
        }
    }
}

@Composable
private fun ProfileStatsCard(
    recentGames: List<RecentGameDto>,
    accuracy: Map<String, EmotionAccuracyDto>,
    weakEmotions: List<WeakEmotionDto>
) {
    val avgAccuracy = accuracy.values.mapNotNull { it.accuracy }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
    val totalAnswers = accuracy.values.sumOf { (it.correct ?: 0) + (it.incorrect ?: 0) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Thống kê luyện tập", fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E4E8C))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Game gần đây", recentGames.size.toString(), Modifier.weight(1f))
                StatTile("Đáp án", totalAnswers.toString(), Modifier.weight(1f))
                StatTile("Cần ôn", weakEmotions.size.toString(), Modifier.weight(1f))
            }
            LinearProgressIndicator(
                progress = { (avgAccuracy / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Color(0xFF2E7D32),
                trackColor = Color(0xFFE0E0E0)
            )
            Text("Độ chính xác trung bình: ${"%.1f".format(avgAccuracy)}%", color = Color.Gray)
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = Color(0xFFF1F8E9)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32), fontSize = 18.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun RecentGamesCard(games: List<RecentGameDto>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Game chơi gần đây", fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E4E8C))
            if (games.isEmpty()) {
                Text("Chưa có lịch sử chơi game.", color = Color.Gray)
            } else {
                games.forEach { game ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(game.name ?: "Game", fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
                        Text(formatDate(game.lastPlayed), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmotionAccuracyCard(
    accuracy: Map<String, EmotionAccuracyDto>,
    weakEmotions: List<WeakEmotionDto>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cảm xúc cần luyện thêm", fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E4E8C))
            if (weakEmotions.isEmpty()) {
                Text("Chưa có cảm xúc yếu hoặc chưa đủ dữ liệu.", color = Color.Gray)
            } else {
                weakEmotions.forEach { item ->
                    Text("${item.emotion ?: "Cảm xúc"}: sai ${"%.1f".format(item.errorRate ?: 0f)}%", color = Color.DarkGray)
                }
            }

            if (accuracy.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Độ chính xác theo cảm xúc", fontWeight = FontWeight.Bold, color = Color.Gray)
                accuracy.entries.sortedByDescending { it.value.accuracy ?: 0f }.forEach { (emotion, stat) ->
                    Text("$emotion: ${"%.1f".format(stat.accuracy ?: 0f)}%", color = Color.DarkGray)
                }
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    profile: UserProfileDto?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (UserProfileUpdateDto) -> Unit
) {
    var name by remember(profile?.userId) { mutableStateOf(profile?.name.orEmpty()) }
    var username by remember(profile?.userId) { mutableStateOf(profile?.username.orEmpty()) }
    var email by remember(profile?.userId) { mutableStateOf(profile?.email.orEmpty()) }
    var age by remember(profile?.userId) { mutableStateOf(profile?.child?.age?.toString().orEmpty()) }
    var gender by remember(profile?.userId) { mutableStateOf(profile?.child?.gender.orEmpty()) }
    var phone by remember(profile?.userId) { mutableStateOf(profile?.child?.phone.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chỉnh sửa hồ sơ") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tên hiển thị") }, singleLine = true)
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Tên đăng nhập") }, singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true)
                OutlinedTextField(
                    value = age,
                    onValueChange = { if (it.all(Char::isDigit)) age = it },
                    label = { Text("Tuổi") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(value = gender, onValueChange = { gender = it }, label = { Text("Giới tính") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Số điện thoại") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = {
                    onSave(
                        UserProfileUpdateDto(
                            name = name.trim().ifBlank { null },
                            username = username.trim().ifBlank { null },
                            email = email.trim().ifBlank { null },
                            age = age.toIntOrNull(),
                            gender = gender.trim().ifBlank { null },
                            phoneNumber = phone.trim().ifBlank { null }
                        )
                    )
                }
            ) {
                Text(if (saving) "Đang lưu..." else "Lưu")
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Hủy") }
        }
    )
}

private fun displayGender(value: String?): String {
    return when (value?.lowercase()) {
        "male", "nam" -> "Nam"
        "female", "nữ", "nu" -> "Nữ"
        "other" -> "Khác"
        else -> value ?: "---"
    }
}

private fun formatDate(value: String?): String {
    if (value.isNullOrBlank()) return "---"
    return value.substringBefore("T").substringBefore(" ")
}
