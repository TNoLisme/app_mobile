package com.example.appmobile.ui.pages.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.RecentGameDto
import com.example.appmobile.data.remote.dto.SessionHistoryItemDto
import com.example.appmobile.data.remote.dto.UserProfileDto
import com.example.appmobile.data.remote.dto.UserProfileUpdateDto
import com.example.appmobile.data.repository.UserRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.EmoGardenBackground
import com.example.appmobile.ui.components.EmoGardenButtonGradient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private data class ProfileBadge(
    val id: String,
    val title: String,
    val icon: String
)

private data class ProfileStat(
    val value: String,
    val label: String
)

@Composable
fun ProfilePage(onBack: () -> Unit) {
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
    var sessions by remember { mutableStateOf<List<SessionHistoryItemDto>>(emptyList()) }
    var cvEmotionScores by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showEdit by remember { mutableStateOf(false) }

    suspend fun loadProfileData() {
        loading = true
        profile = repository.getProfile(userId)
        recentGames = repository.getRecentGames(userId)
        sessions = repository.getSessionHistory(userId)
        cvEmotionScores = repository.getCvEmotionScores(userId)?.scores.orEmpty()
        message = if (profile == null) "Chưa tải được hồ sơ từ backend." else null
        loading = false
    }

    LaunchedEffect(userId) {
        loadProfileData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EmoGardenBackground)
    ) {
        ProfileTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF1976D2))
                }
            } else {
                message?.let {
                    MessageBanner(it)
                }

                val unlockedBadges = unlockedBadgeIds(sessions, cvEmotionScores)
                ProfileHeroCard(
                    profile = profile,
                    badges = profileBadges(),
                    unlocked = unlockedBadges
                )
                ProfileInfoGrid(profile)
                ProfileStatsSection(
                    stats = profileStats(
                        sessions = sessions,
                        recentGames = recentGames,
                        unlockedBadges = unlockedBadges.size
                    )
                )
                ProfileActions(
                    onEdit = { showEdit = true }
                )
            }
        }
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
private fun ProfileTopBar(onBack: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.98f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientPill(text = "← Quay lại", onClick = onBack)
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProfileHeroCard(
    profile: UserProfileDto?,
    badges: List<ProfileBadge>,
    unlocked: Set<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.width(104.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(Brush.linearGradient(listOf(Color(0xFFFFF1A8), Color(0xFFFFD54F))), CircleShape)
                            .padding(5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = Color(0xFFFFE082)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("👶", fontSize = 38.sp)
                            }
                        }
                    }
                    Surface(shape = CircleShape, color = Color(0xFFFFD700), shadowElevation = 3.dp) {
                        Text("🏅", modifier = Modifier.padding(5.dp), fontSize = 14.sp)
                    }
                }
                Text(
                    profile?.name?.ifBlank { null } ?: "Bé yêu",
                    color = Color(0xFF263238),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Cấp độ 1", color = Color(0xFF52616F), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🏆 Huy hiệu", color = Color(0xFF263238), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(badges) { badge ->
                        BadgeCircle(badge = badge, unlocked = badge.id in unlocked)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoGrid(profile: UserProfileDto?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard("👤", "Tên đăng nhập", profile?.username ?: "---", Modifier.weight(1f))
            InfoCard("✉️", "Email", profile?.email ?: "---", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard("🗓️", "Ngày tham gia", formatDate(profile?.createdAt), Modifier.weight(1f))
            InfoCard("🎂", "Tuổi", profile?.child?.age?.let { "$it tuổi" } ?: "---", Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoCard(icon: String, title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("$icon $title", color = Color(0xFF0B3C7D), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                value,
                color = Color(0xFF455A64),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileStatsSection(stats: List<ProfileStat>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("📊 Thống kê chơi game", color = Color(0xFF263238), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.chunked(2).forEach { rowStats ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowStats.forEach { stat ->
                            StatBox(stat, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(stat: ProfileStat, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(74.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stat.value, color = Color(0xFF1976D2), fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(stat.label, color = Color(0xFF52616F), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BadgeCircle(badge: ProfileBadge, unlocked: Boolean) {
    Box(contentAlignment = Alignment.TopEnd) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = if (unlocked) 5.dp else 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (unlocked) {
                            Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFF176)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFFE5E7EB), Color(0xFFF1F5F9)))
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(badge.icon, fontSize = 23.sp, color = if (unlocked) Color.Unspecified else Color.Gray)
            }
        }
        if (!unlocked) {
            Surface(shape = CircleShape, color = Color(0xFFE0E0E0)) {
                Text("🔒", modifier = Modifier.padding(3.dp), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ProfileActions(onEdit: () -> Unit) {
    GradientPill(text = "✏️ Chỉnh sửa thông tin", onClick = onEdit, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun GradientPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent,
        shadowElevation = 5.dp
    ) {
        Box(
            modifier = Modifier
                .background(EmoGardenButtonGradient)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun MessageBanner(message: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF3E0), modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(12.dp), color = Color(0xFF8A4B00), lineHeight = 19.sp)
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
    var dateOfBirth by remember(profile?.userId) { mutableStateOf(profile?.child?.dob.orEmpty()) }
    var phone by remember(profile?.userId) { mutableStateOf(profile?.child?.phone.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chỉnh sửa hồ sơ") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                OutlinedTextField(value = dateOfBirth, onValueChange = { dateOfBirth = it.take(10) }, label = { Text("Ngày sinh") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { if (it.all(Char::isDigit)) phone = it.take(10) }, label = { Text("Số điện thoại") }, singleLine = true)
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
                            dateOfBirth = dateOfBirth.trim().ifBlank { null },
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

private fun profileStats(
    sessions: List<SessionHistoryItemDto>,
    recentGames: List<RecentGameDto>,
    unlockedBadges: Int
): List<ProfileStat> {
    val totalScore = sessions.sumOf { it.score ?: 0 }
    val playTimeHours = sessions.sumOf { sessionDurationMillis(it) }.toDouble() / (1000 * 60 * 60)
    return listOf(
        ProfileStat((sessions.size.takeIf { it > 0 } ?: recentGames.size).toString(), "Trò chơi đã chơi"),
        ProfileStat(totalScore.toString(), "Tổng điểm"),
        ProfileStat(unlockedBadges.toString(), "Thành tích"),
        ProfileStat("${"%.1f".format(playTimeHours)}h", "Thời gian chơi")
    )
}

private fun profileBadges(): List<ProfileBadge> {
    return listOf(
        ProfileBadge(GameUiCatalog.GAME_RECOGNIZE_EMOTION, "Hộp", "📦"),
        ProfileBadge(GameUiCatalog.GAME_DETECTIVE, "Thám tử", "🕵️"),
        ProfileBadge(GameUiCatalog.GAME_EMOTION_MATCH, "Đúng chỗ", "🎯"),
        ProfileBadge(GameUiCatalog.GAME_FACE_ASSEMBLY, "Lắp ghép", "🧩"),
        ProfileBadge(GameUiCatalog.GAME_CV_STORY, "Khuôn mặt", "🎭"),
        ProfileBadge(GameUiCatalog.GAME_CV_REQUEST, "CV 6 cảm xúc", "📷"),
        ProfileBadge("all", "Siêu sao", "🌟")
    )
}

private fun unlockedBadgeIds(sessions: List<SessionHistoryItemDto>, cvEmotionScores: Map<String, Float>): Set<String> {
    val baseGameIds = setOf(
        GameUiCatalog.GAME_RECOGNIZE_EMOTION,
        GameUiCatalog.GAME_DETECTIVE,
        GameUiCatalog.GAME_EMOTION_MATCH,
        GameUiCatalog.GAME_FACE_ASSEMBLY,
        GameUiCatalog.GAME_CV_STORY
    )
    val unlocked = baseGameIds
        .filter { gameId -> sessions.any { it.gameId == gameId && (it.score ?: 0) >= 80 } }
        .toMutableSet()

    val cvComplete = listOf("vui", "buồn", "ngạc nhiên", "tức giận", "sợ hãi", "ghê tởm")
        .all { emotion -> (cvEmotionScores[emotion] ?: 0f) >= 100f }
    if (cvComplete) unlocked += GameUiCatalog.GAME_CV_REQUEST

    if (profileBadges().filter { it.id != "all" }.all { it.id in unlocked }) {
        unlocked += "all"
    }
    return unlocked
}

private fun formatDate(value: String?): String {
    if (value.isNullOrBlank()) return "---"
    return value.substringBefore("T").substringBefore(" ")
}

private fun sessionDurationMillis(session: SessionHistoryItemDto): Long {
    val start = parseBackendDate(session.startTime) ?: return 0L
    val end = parseBackendDate(session.endTime) ?: return 0L
    return (end - start).coerceAtLeast(0L)
}

private fun parseBackendDate(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val normalized = value.substringBefore(".").substringBefore("+").replace("Z", "")
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(normalized)?.time
    }.getOrNull()
}
