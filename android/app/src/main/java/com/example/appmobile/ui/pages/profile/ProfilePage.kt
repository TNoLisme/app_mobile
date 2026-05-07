package com.example.appmobile.ui.pages.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.RecentGameDto
import com.example.appmobile.data.remote.dto.SessionHistoryItemDto
import com.example.appmobile.data.remote.dto.UserProfileDto
import com.example.appmobile.data.remote.dto.UserProfileUpdateDto
import com.example.appmobile.data.repository.UserRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.state.UserAvatarState
import com.google.firebase.auth.FirebaseAuth
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val ProfileBackgroundGradient = Brush.verticalGradient(
    listOf(
        Color(0xFFF8FDFF),
        Color(0xFFEAF7FF),
        Color(0xFFDDF2FF)
    )
)
private val ProfileButtonGradient = Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFF60A5FA)))
private val ProfileTextPrimary = Color(0xFF073B73)
private val ProfileTextSecondary = Color(0xFF64748B)
private val ProfileBlue = Color(0xFF0B5DAE)
private val ProfileCardBorder = Color(0xFFD7E7F3)
private val ProfileSoftSection = Color(0xFFF3FBFF)

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
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: AppSession.currentBackendUserId() ?: "local-player" }
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
    val avatarUri = UserAvatarState.avatarUri.value
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { selectedUri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            UserAvatarState.save(context, userId, selectedUri.toString())
            message = "Đã cập nhật ảnh đại diện."
        }
    }

    suspend fun loadProfileData() {
        loading = profile == null
        message = null
        coroutineScope {
            val profileDeferred = async { repository.getProfile(userId) }
            val recentGamesDeferred = async { repository.getRecentGames(userId) }
            val sessionsDeferred = async { repository.getSessionHistory(userId) }
            val cvScoresDeferred = async { repository.getCvEmotionScores(userId)?.scores.orEmpty() }

            profile = profileDeferred.await()
            profile?.avatarUrl?.takeIf { it.isNotBlank() }?.let { backendAvatarUrl ->
                UserAvatarState.save(context, userId, backendAvatarUrl)
            }
            message = if (profile == null) "Chưa tải được hồ sơ từ backend." else null
            loading = false

            recentGames = recentGamesDeferred.await()
            sessions = sessionsDeferred.await()
            cvEmotionScores = cvScoresDeferred.await()
        }
        message = if (profile == null) "Chưa tải được hồ sơ từ backend." else null
        loading = false
    }

    LaunchedEffect(userId) {
        UserAvatarState.load(context, userId)
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
                unlocked = unlockedBadges,
                avatarUri = avatarUri,
                onChangeAvatar = { avatarPicker.launch(arrayOf("image/*")) }
            )
            ProfilePersonalInfoGrid(profile)
            ProfileStatsSection(
                stats = profileStats(
                    sessions = sessions,
                    recentGames = recentGames,
                    unlockedBadges = unlockedBadges.size
                )
            )
            ProfileActions(onEdit = { showEdit = true })
            Spacer(modifier = Modifier.height(96.dp))
        }
    }

    if (showEdit) {
        EditPersonalProfileDialog(
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
                        message = "Cập nhật hồ sơ thành công."
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
            heightDp = 36,
            horizontalPaddingDp = 14,
            fontSizeSp = 13,
            shadowDp = 1
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
                color = ProfileBlue,
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
    unlocked: Set<String>,
    avatarUri: String?,
    onChangeAvatar: () -> Unit
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
                Avatar(avatarUri = avatarUri, onClick = onChangeAvatar)
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
private fun Avatar(avatarUri: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomEnd
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = Color(0xFFFFE082),
            border = BorderStroke(2.dp, Color.White),
            shadowElevation = 2.dp
        ) {
            if (!avatarUri.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = "Đổi ảnh đại diện",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFFFF1A8), Color(0xFFFFD54F))),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👶", fontSize = 28.sp)
                }
            }
        }
        Surface(
            modifier = Modifier.size(23.dp),
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(1.dp, ProfileCardBorder),
            shadowElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("📷", fontSize = 12.sp)
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
private fun ProfilePersonalInfoGrid(profile: UserProfileDto?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile("👶", "Tên hiển thị", personalFallback(profile?.name), Modifier.weight(1f))
            InfoTile("🎂", "Tuổi", profile?.child?.age?.let { "$it tuổi" } ?: "Chưa có", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile("📅", "Ngày sinh", formatPersonalDate(profile?.child?.dob), Modifier.weight(1f))
            InfoTile("🗓️", "Ngày tham gia", formatPersonalDate(profile?.createdAt), Modifier.weight(1f))
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
                color = ProfileTextSecondary,
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
        heightDp = 48,
        horizontalPaddingDp = 16,
        fontSizeSp = 14,
        shadowDp = 1
    )
}

@Composable
private fun ProfileSurface(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, ProfileCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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

@Composable
private fun EditPersonalProfileDialog(
    profile: UserProfileDto?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (UserProfileUpdateDto) -> Unit
) {
    var name by rememberSaveable(profile?.userId) { mutableStateOf(profile?.name.orEmpty()) }
    var age by rememberSaveable(profile?.userId) { mutableStateOf(profile?.child?.age?.toString().orEmpty()) }
    var dateOfBirth by rememberSaveable(profile?.userId) { mutableStateOf(profile?.child?.dob.orEmpty()) }
    var formError by rememberSaveable(profile?.userId) { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        val cleanName = name.trim()
        val cleanAge = age.trim()
        val cleanDob = dateOfBirth.trim()
        formError = when {
            cleanName.length !in 2..50 -> "Tên hiển thị phải từ 2 đến 50 ký tự."
            cleanAge.toIntOrNull() == null || cleanAge.toInt() !in 1..120 -> "Tuổi phải là số hợp lệ từ 1 đến 120."
            cleanDob.isNotEmpty() && !isValidBackendDate(cleanDob) -> "Ngày sinh phải đúng định dạng yyyy-MM-dd và là ngày hợp lệ."
            else -> null
        }
        return formError == null
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 18.dp)
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(1.dp, ProfileCardBorder),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EditProfileHeader(saving = saving, onDismiss = onDismiss)
                    Text(
                        text = "Ngày tham gia: ${formatPersonalDate(profile?.createdAt)}",
                        color = ProfileTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    formError?.let { FormErrorBanner(it) }

                    ProfileSectionCard(title = "Thông tin cá nhân") {
                        ProfileTextField(name, { name = it }, "Tên hiển thị", "Tên của bé")
                        ProfileTextField(
                            value = age,
                            onValueChange = { input -> if (input.all(Char::isDigit) && input.length <= 3) age = input },
                            label = "Tuổi",
                            placeholder = "Ví dụ: 6",
                            keyboardType = KeyboardType.Number
                        )
                        ProfileTextField(
                            value = dateOfBirth,
                            onValueChange = { dateOfBirth = it.take(10) },
                            label = "Ngày sinh",
                            placeholder = "yyyy-MM-dd",
                            trailing = "📅"
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryPillButton("❌ Hủy", enabled = !saving, onClick = onDismiss, modifier = Modifier.weight(1f))
                        GradientPill(
                            text = if (saving) "Đang lưu..." else "💾 Lưu thay đổi",
                            onClick = {
                                if (!saving && validate()) {
                                    onSave(
                                        UserProfileUpdateDto(
                                            name = name.trim(),
                                            age = age.trim().toIntOrNull(),
                                            dateOfBirth = dateOfBirth.trim().ifBlank { null }
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            heightDp = 48,
                            horizontalPaddingDp = 10,
                            fontSizeSp = 13,
                            shadowDp = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditProfileDialogV2(
    profile: UserProfileDto?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (UserProfileUpdateDto) -> Unit
) {
    var name by rememberSaveable(profile?.userId) { mutableStateOf(profile?.name.orEmpty()) }
    var username by rememberSaveable(profile?.userId) { mutableStateOf(profile?.username.orEmpty()) }
    var email by rememberSaveable(profile?.userId) { mutableStateOf(profile?.email.orEmpty()) }
    var newPassword by rememberSaveable(profile?.userId) { mutableStateOf("") }
    var confirmPassword by rememberSaveable(profile?.userId) { mutableStateOf("") }
    var age by rememberSaveable(profile?.userId) { mutableStateOf(profile?.child?.age?.toString().orEmpty()) }
    var gender by rememberSaveable(profile?.userId) { mutableStateOf(profile?.child?.gender.orEmpty()) }
    var dateOfBirth by rememberSaveable(profile?.userId) { mutableStateOf(profile?.child?.dob.orEmpty()) }
    var phone by rememberSaveable(profile?.userId) { mutableStateOf(profile?.child?.phone.orEmpty()) }
    var formError by rememberSaveable(profile?.userId) { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        val trimmedEmail = email.trim()
        val trimmedAge = age.trim()
        val trimmedPhone = phone.trim()
        formError = when {
            trimmedEmail.isNotEmpty() && !trimmedEmail.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) ->
                "Email không đúng định dạng."
            trimmedAge.isNotEmpty() && (trimmedAge.toIntOrNull() == null || trimmedAge.toInt() !in 1..120) ->
                "Tuổi phải là số hợp lệ từ 1 đến 120."
            trimmedPhone.isNotEmpty() && !trimmedPhone.matches(Regex("^\\d{8,15}$")) ->
                "Số điện thoại chỉ gồm số và dài 8-15 ký tự."
            newPassword.isNotBlank() && newPassword != confirmPassword ->
                "Mật khẩu mới và nhập lại mật khẩu mới không trùng nhau."
            else -> null
        }
        return formError == null
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 18.dp)
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            val twoColumns = maxWidth >= 620.dp

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.94f)
                    .widthIn(max = 720.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(1.dp, ProfileCardBorder),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EditProfileHeader(saving = saving, onDismiss = onDismiss)
                    formError?.let { FormErrorBanner(it) }

                    ProfileSectionCard(title = "Thông tin tài khoản") {
                        if (twoColumns) {
                            TwoColumnFields(
                                first = { ProfileTextField(username, { username = it }, "Tên đăng nhập", "Nhập tên đăng nhập") },
                                second = { ProfileTextField(email, { email = it }, "Email", "email@example.com", keyboardType = KeyboardType.Email) }
                            )
                            TwoColumnFields(
                                first = {
                                    ProfileTextField(
                                        newPassword,
                                        { newPassword = it },
                                        "Mật khẩu mới",
                                        "Để trống nếu không đổi",
                                        keyboardType = KeyboardType.Password,
                                        visualTransformation = PasswordVisualTransformation()
                                    )
                                },
                                second = {
                                    ProfileTextField(
                                        confirmPassword,
                                        { confirmPassword = it },
                                        "Nhập lại mật khẩu mới",
                                        "Để trống nếu không đổi",
                                        keyboardType = KeyboardType.Password,
                                        visualTransformation = PasswordVisualTransformation()
                                    )
                                }
                            )
                        } else {
                            ProfileTextField(username, { username = it }, "Tên đăng nhập", "Nhập tên đăng nhập")
                            ProfileTextField(email, { email = it }, "Email", "email@example.com", keyboardType = KeyboardType.Email)
                            ProfileTextField(newPassword, { newPassword = it }, "Mật khẩu mới", "Để trống nếu không đổi", keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation())
                            ProfileTextField(confirmPassword, { confirmPassword = it }, "Nhập lại mật khẩu mới", "Để trống nếu không đổi", keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation())
                        }
                    }

                    ProfileSectionCard(title = "Thông tin cá nhân") {
                        if (twoColumns) {
                            TwoColumnFields(
                                first = { ProfileTextField(name, { name = it }, "Tên hiển thị", "Tên của bé") },
                                second = { ProfileTextField(age, { input -> if (input.all(Char::isDigit) && input.length <= 3) age = input }, "Tuổi", "Ví dụ: 6", keyboardType = KeyboardType.Number) }
                            )
                            TwoColumnFields(
                                first = { GenderDropdown(gender, onValueChange = { gender = it }) },
                                second = { ProfileTextField(dateOfBirth, { dateOfBirth = it.take(10) }, "Ngày sinh", "YYYY-MM-DD", trailing = "📅") }
                            )
                            ProfileTextField(phone, { input -> if (input.all(Char::isDigit) && input.length <= 15) phone = input }, "Số điện thoại", "Nhập số điện thoại", keyboardType = KeyboardType.Phone)
                        } else {
                            ProfileTextField(name, { name = it }, "Tên hiển thị", "Tên của bé")
                            ProfileTextField(age, { input -> if (input.all(Char::isDigit) && input.length <= 3) age = input }, "Tuổi", "Ví dụ: 6", keyboardType = KeyboardType.Number)
                            GenderDropdown(gender, onValueChange = { gender = it })
                            ProfileTextField(dateOfBirth, { dateOfBirth = it.take(10) }, "Ngày sinh", "YYYY-MM-DD", trailing = "📅")
                            ProfileTextField(phone, { input -> if (input.all(Char::isDigit) && input.length <= 15) phone = input }, "Số điện thoại", "Nhập số điện thoại", keyboardType = KeyboardType.Phone)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryPillButton("❌ Hủy", enabled = !saving, onClick = onDismiss, modifier = Modifier.weight(1f))
                        GradientPill(
                            text = if (saving) "Đang lưu..." else "💾 Lưu thay đổi",
                            onClick = {
                                if (!saving && validate()) {
                                    // TODO: Backend DTO hiện chưa có field password. Khi có API/Firebase update password,
                                    // chỉ gửi newPassword nếu người dùng nhập mật khẩu mới.
                                    onSave(
                                        UserProfileUpdateDto(
                                            name = name.trim().ifBlank { null },
                                            username = username.trim().ifBlank { null },
                                            email = email.trim().ifBlank { null },
                                            age = age.trim().toIntOrNull(),
                                            gender = gender.trim().ifBlank { null },
                                            dateOfBirth = dateOfBirth.trim().ifBlank { null },
                                            phoneNumber = phone.trim().ifBlank { null }
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            heightDp = 48,
                            horizontalPaddingDp = 10,
                            fontSizeSp = 13,
                            shadowDp = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditProfileHeader(saving: Boolean, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(46.dp),
            shape = CircleShape,
            color = Color(0xFFE8F7FF),
            border = BorderStroke(1.dp, Color(0xFFCDE7FA))
        ) {
            Box(contentAlignment = Alignment.Center) { Text("✏️", fontSize = 22.sp) }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Chỉnh sửa hồ sơ", color = ProfileTextPrimary, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Cập nhật thông tin cá nhân để đồng bộ với trang hồ sơ của bạn.",
                color = ProfileTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        CloseButton(enabled = !saving, onClick = onDismiss)
    }
}

@Composable
private fun CloseButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = Color(0xFFF8FBFF),
        border = BorderStroke(1.dp, ProfileCardBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("×", color = ProfileTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FormErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFF1F2),
        border = BorderStroke(1.dp, Color(0xFFFDA4AF))
    ) {
        Text(
            text = "⚠️ $message",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            color = Color(0xFF9F1239),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ProfileSectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ProfileSoftSection,
        border = BorderStroke(1.dp, ProfileCardBorder),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, color = ProfileTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            content()
        }
    }
}

@Composable
private fun TwoColumnFields(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.weight(1f)) { first() }
        Box(modifier = Modifier.weight(1f)) { second() }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, color = ProfileBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        placeholder = { Text(placeholder, color = ProfileTextSecondary, fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        trailingIcon = trailing?.let { icon -> { Text(icon, fontSize = 16.sp) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ProfileBlue,
            unfocusedBorderColor = ProfileCardBorder,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedTextColor = ProfileTextPrimary,
            unfocusedTextColor = ProfileTextPrimary,
            cursorColor = ProfileBlue
        )
    )
}

@Composable
private fun GenderDropdown(value: String, onValueChange: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val options = listOf("Nam", "Nữ", "Khác")

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = BorderStroke(1.dp, ProfileCardBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Giới tính", color = ProfileBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = value.ifBlank { "Chọn giới tính" },
                        color = if (value.isBlank()) ProfileTextSecondary else ProfileTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text("⌄", color = ProfileTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = ProfileTextPrimary, fontWeight = FontWeight.SemiBold) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SecondaryPillButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ProfileCardBorder),
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, color = ProfileTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
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

private fun personalFallback(value: String?): String {
    return value?.takeIf { it.isNotBlank() } ?: "Chưa có"
}

private fun formatPersonalDate(value: String?): String {
    if (value.isNullOrBlank()) return "Chưa có"
    val normalized = value.substringBefore("T").substringBefore(" ")
    return runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(normalized)
        parsed?.let { SimpleDateFormat("dd/MM/yyyy", Locale.US).format(it) }
    }.getOrNull() ?: normalized
}

private fun isValidBackendDate(value: String): Boolean {
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)
    }.getOrNull() != null
}
