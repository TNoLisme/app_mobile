package com.example.appmobile.ui.pages.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.alpha
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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val ProfileBackgroundGradient = Brush.verticalGradient(
    listOf(
        Color(0xFFF4FBFF),
        Color(0xFFE6F5FD),
        Color(0xFFD9EEF9)
    )
)
private val ProfileButtonGradient = Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFF2563EB)))
private val ProfileTextPrimary = Color(0xFF1F2937)
private val ProfileTextSecondary = Color(0xFF6B7280)
private val ProfileBlue = Color(0xFF0B66C3)
private val ProfileCardBorder = Color(0xFFDDEAF5)

private data class ProfileBadge(
    val id: String,
    val title: String,
    val icon: String
)

private data class ProfileStat(
    val value: String,
    val label: String,
    val icon: String
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
            .background(ProfileBackgroundGradient)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProfileTopBar(onBack = onBack)

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ProfileBlue)
            }
        } else {
            message?.let { currentMessage ->
                if (profile == null) {
                    ErrorAlert(
                        message = "Chưa tải được hồ sơ từ backend.",
                        onRetry = {
                            scope.launch {
                                loadProfileData()
                            }
                        }
                    )
                } else {
                    StatusBanner(currentMessage)
                }
            }

            val unlockedBadges = unlockedBadgeIds(sessions, cvEmotionScores)
            ProfileCard(
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
            ProfileActions(onEdit = { showEdit = true })
            Spacer(modifier = Modifier.height(4.dp))
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GradientPill(
            text = "← Quay lại",
            onClick = onBack,
            heightDp = 34,
            horizontalPaddingDp = 14,
            fontSizeSp = 13,
            shadowDp = 2
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ErrorAlert(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFF8E7),
        border = BorderStroke(1.dp, Color(0xFFF9D26A)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("⚠️", fontSize = 15.sp)
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = Color(0xFF8A4B00),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Thử lại",
                modifier = Modifier.clickable(onClick = onRetry),
                color = Color(0xFF0B66C3),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProfileCard(
    profile: UserProfileDto?,
    badges: List<ProfileBadge>,
    unlocked: Set<String>
) {
    ProfileSurface {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Avatar()
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = profile?.name?.takeIf { it.isNotBlank() } ?: "Bé yêu",
                        color = ProfileTextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Cấp độ 1",
                        color = ProfileTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "🏆 Huy hiệu",
                    color = ProfileTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                BadgeGrid(badges = badges, unlocked = unlocked)
            }
        }
    }
}

@Composable
private fun Avatar() {
    Box(
        modifier = Modifier
            .size(54.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFFFFF1A8), Color(0xFFFFD54F))),
                CircleShape
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = Color(0xFFFFE082)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("👶", fontSize = 27.sp)
            }
        }
    }
}

@Composable
private fun BadgeGrid(badges: List<ProfileBadge>, unlocked: Set<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        badges.forEach { badge ->
            BadgeCircle(badge = badge, unlocked = badge.id in unlocked)
        }
    }
}

@Composable
private fun BadgeCircle(
    badge: ProfileBadge,
    unlocked: Boolean
) {
    Box(
        modifier = Modifier.size(34.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = if (unlocked) Color(0xFFFFF4B8) else Color(0xFFE5E7EB),
            border = BorderStroke(1.dp, if (unlocked) Color(0xFFFFD54F) else Color(0xFFD1D5DB)),
            shadowElevation = if (unlocked) 2.dp else 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = badge.icon,
                    modifier = Modifier.alpha(if (unlocked) 1f else 0.38f),
                    fontSize = 15.sp
                )
            }
        }
        if (!unlocked) {
            Text("🔒", fontSize = 7.sp)
        }
    }
}

@Composable
private fun ProfileInfoGrid(profile: UserProfileDto?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile("👤", "Tên đăng nhập", fallback(profile?.username), Modifier.weight(1f))
            InfoTile("✉️", "Email", fallback(profile?.email), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile("🗓️", "Ngày tham gia", formatDate(profile?.createdAt), Modifier.weight(1f))
            InfoTile("🎂", "Tuổi", profile?.child?.age?.let { "$it tuổi" } ?: "Chưa có", Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoTile(icon: String, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(62.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, ProfileCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$icon $label",
                color = ProfileBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                color = ProfileTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileStatsSection(stats: List<ProfileStat>) {
    ProfileSurface {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📊 Thống kê chơi game",
                color = ProfileTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                stats.chunked(2).forEach { rowStats ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowStats.forEach { stat ->
                            StatTile(stat, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(stat: ProfileStat, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(62.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color(0xFFCDE7FA)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(Color(0xFFE8F7FF), Color(0xFFCFEAFE))))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${stat.icon} ${stat.value}",
                color = Color(0xFF0B66C3),
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stat.label,
                color = Color(0xFF4B5563),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileActions(onEdit: () -> Unit) {
    GradientPill(
        text = "✏️ Chỉnh sửa hồ sơ",
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        heightDp = 44,
        horizontalPaddingDp = 16,
        fontSizeSp = 14,
        shadowDp = 2
    )
}

@Composable
private fun ProfileSurface(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, ProfileCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        content()
    }
}

@Composable
private fun GradientPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    heightDp: Int,
    horizontalPaddingDp: Int,
    fontSizeSp: Int,
    shadowDp: Int
) {
    Surface(
        modifier = modifier
            .height(heightDp.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent,
        shadowElevation = shadowDp.dp
    ) {
        Box(
            modifier = Modifier
                .background(ProfileButtonGradient)
                .padding(horizontal = horizontalPaddingDp.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSizeSp.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatusBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFEFFAF3),
        border = BorderStroke(1.dp, Color(0xFFC7E9D0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color(0xFF166534),
            lineHeight = 17.sp,
            fontSize = 13.sp
        )
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
        ProfileStat((sessions.size.takeIf { it > 0 } ?: recentGames.size).toString(), "Từ vựng đã chơi", "🎮"),
        ProfileStat(totalScore.toString(), "Đúng điểm", "⭐"),
        ProfileStat(unlockedBadges.toString(), "Thành tích", "🏆"),
        ProfileStat(String.format(Locale.US, "%.1fh", playTimeHours), "Thời gian chơi", "⏱️")
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

private fun fallback(value: String?): String {
    return value?.takeIf { it.isNotBlank() } ?: "Chưa có"
}

private fun formatDate(value: String?): String {
    if (value.isNullOrBlank()) return "Chưa có"
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
