package com.example.appmobile.ui.pages.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.SessionHistoryItemDto
import com.example.appmobile.data.remote.dto.UserProfileDto
import com.example.appmobile.data.remote.dto.UserProfileUpdateDto
import com.example.appmobile.data.repository.UserRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgVectorEmojiIcon
import com.example.appmobile.ui.components.EgCollapsibleMainScaffold
import com.example.appmobile.ui.components.EgTab
import com.example.appmobile.ui.components.egTactileClick
import com.example.appmobile.ui.state.UserAvatarState
import com.google.firebase.auth.FirebaseAuth
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Locale

private val ProfileButtonGradient: Color get() = EgDesign.primary
private val ProfileTextPrimary: Color get() = EgDesign.textPrimary
private val ProfileTextSecondary: Color get() = EgDesign.textSecondary
private val ProfileBlue: Color get() = EgDesign.blue
private val ProfileCardBorder: Color get() = EgDesign.cardBorder
private val ProfileSoftSection: Color get() = EgDesign.cardSoft

private data class ProfileBadge(
    val id: String,
    val title: String,
    val icon: String
)

@Composable
fun ProfilePage(
    onGoHome: () -> Unit = {},
    onOpenLearn: () -> Unit = {},
    onOpenGames: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = remember {
        AppSession.getBackendUserId(context)
            ?: AppSession.currentBackendUserId()
            ?: FirebaseAuth.getInstance().currentUser?.uid
            ?: "local-player"
    }
    val repository = remember {
        UserRepository(
            NetworkClient.apiService,
            FirebaseAuthHelper(),
            AppDatabase.getDatabase(context).userDao()
        )
    }

    var profile by remember { mutableStateOf<UserProfileDto?>(null) }
    var sessions by remember { mutableStateOf<List<SessionHistoryItemDto>>(emptyList()) }
    var cvEmotionScores by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var selectedBadge by remember { mutableStateOf<ProfileBadge?>(null) }
    val unlockedBadges = remember(sessions, cvEmotionScores) { unlockedBadgeIds(sessions, cvEmotionScores) }
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
        if (profile == null) {
            profile = UserProfileDto(
                userId = userId,
                username = "Local Player",
                email = "",
                role = "child",
                name = "Local Player"
            )
            loading = false
        }

        runCatching { repository.getCachedProfile(userId) }
            .onSuccess { cachedProfile ->
                if (profile == null && cachedProfile != null) {
                    profile = cachedProfile
                    loading = false
                    message = null
                } else {
                    loading = profile == null
                }
            }
            .onFailure {
                loading = profile == null
            }

        message = null

        val loaded = runCatching {
            withTimeoutOrNull(8000) {
                coroutineScope {
                val profileDeferred = async { repository.getProfile(userId) }
                val sessionsDeferred = async { repository.getSessionHistory(userId) }
                val cvScoresDeferred = async { repository.getCvEmotionScores(userId)?.scores.orEmpty() }

                profileDeferred.await()?.let { profile = it }
                profile?.avatarUrl?.takeIf { it.isNotBlank() }?.let { backendAvatarUrl ->
                    UserAvatarState.save(context, userId, backendAvatarUrl)
                }
                message = if (profile == null) "Chưa tải được hồ sơ từ backend." else null
                loading = false

                sessions = sessionsDeferred.await()
                cvEmotionScores = cvScoresDeferred.await()
                    true
                }
            }
        }.getOrNull() == true

        if (!loaded) {
            if (profile == null) {
                message = "Chưa tải được hồ sơ từ backend."
            }
            loading = false
        }
        message = if (profile == null) "Chưa tải được hồ sơ từ backend." else null
        loading = false
    }

    LaunchedEffect(userId) {
        UserAvatarState.load(context, userId)
        loadProfileData()
    }

    EgCollapsibleMainScaffold(
        activeTab = EgTab.Profile,
        onHome = onGoHome,
        onLearn = onOpenLearn,
        onGames = onOpenGames,
        onProfile = null,
        onSettings = onOpenSettings,
        onBack = onGoHome
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

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

            ProfilePageIntro()
            ProfileCard(
                profile = profile,
                badges = profileBadges(),
                unlocked = unlockedBadges,
                avatarUri = avatarUri,
                onChangeAvatar = { avatarPicker.launch(arrayOf("image/*")) },
                onBadgeClick = { badge -> selectedBadge = badge }
            )
            ProfilePersonalInfoGrid(profile)
            ProfileActions(onEdit = { showEdit = true })
            Spacer(modifier = Modifier.height(32.dp))
        }
        }
    }

    if (showEdit) {
        EditProfileDialogV2(
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

    selectedBadge?.let { badge ->
        BadgeRequirementDialog(
            badge = badge,
            unlocked = badge.id in unlockedBadges,
            onDismiss = { selectedBadge = null }
        )
    }
}

@Composable
private fun ErrorAlert(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = EgDesign.cardSoft,
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EgVectorEmojiIcon("warning", size = 16.dp, tint = Color(0xFFF59E0B))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = ProfileTextPrimary,
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
private fun ProfilePageIntro() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "Hồ sơ của bé",
            color = ProfileTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Thông tin cá nhân và những huy hiệu bé đã mở khóa.",
            color = ProfileTextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ProfileCard(
    profile: UserProfileDto?,
    badges: List<ProfileBadge>,
    unlocked: Set<String>,
    avatarUri: String?,
    onChangeAvatar: () -> Unit,
    onBadgeClick: (ProfileBadge) -> Unit
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
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = profile?.name?.takeIf { it.isNotBlank() } ?: "Bé yêu",
                        color = ProfileTextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProfileMetaChip("Cấp độ 1")
                        ProfileMetaChip("${unlocked.size}/${badges.size} huy hiệu")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EgVectorEmojiIcon("trophy", size = 24.dp)
                    Text(
                        text = "Huy hiệu",
                        color = ProfileTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                BadgeGrid(badges = badges, unlocked = unlocked, onBadgeClick = onBadgeClick)
            }
        }
    }
}

@Composable
private fun ProfileMetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = ProfileSoftSection,
        border = BorderStroke(1.dp, ProfileCardBorder)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = ProfileBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold
        )
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
            color = ProfileSoftSection,
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
                            ProfileSoftSection,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    EgVectorEmojiIcon("child", size = 42.dp, tint = ProfileBlue)
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
                EgVectorEmojiIcon("camera", size = 16.dp)
            }
        }
    }
}

@Composable
private fun BadgeGrid(
    badges: List<ProfileBadge>,
    unlocked: Set<String>,
    onBadgeClick: (ProfileBadge) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        badges.forEach { badge ->
            BadgeCircle(
                badge = badge,
                unlocked = badge.id in unlocked,
                onClick = { onBadgeClick(badge) }
            )
        }
    }
}

@Composable
private fun BadgeCircle(
    badge: ProfileBadge,
    unlocked: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .egTactileClick(onClick = onClick),
        contentAlignment = Alignment.TopEnd
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = if (unlocked) Color(0xFFFFF8D6) else EgDesign.cardSoft,
            border = BorderStroke(1.dp, if (unlocked) Color(0xFFFFD166) else ProfileCardBorder),
            shadowElevation = if (unlocked) 2.dp else 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                ProfileBadgeArt(icon = badge.icon, unlocked = unlocked, modifier = Modifier.size(34.dp))
            }
        }
        if (!unlocked) {
            Surface(
                modifier = Modifier.size(14.dp),
                shape = CircleShape,
                color = EgDesign.card,
                border = BorderStroke(1.dp, ProfileCardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EgVectorEmojiIcon("lock", size = 9.dp, tint = ProfileTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ProfileBadgeArt(icon: String, unlocked: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.alpha(if (unlocked) 1f else 0.36f)) {
        drawProfileBadgeArt(icon = icon, unlocked = unlocked)
    }
}

private fun DrawScope.drawProfileBadgeArt(icon: String, unlocked: Boolean) {
    val s = minOf(size.width, size.height)
    val c = center
    val accent = if (unlocked) profileBadgeAccent(icon) else Color(0xFF8EA2B7)
    val dark = if (unlocked) Color(0xFF174062) else Color(0xFF6B7C8D)

    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.96f), accent.copy(alpha = 0.22f), accent.copy(alpha = 0.38f)),
            center = Offset(c.x - s * 0.18f, c.y - s * 0.20f),
            radius = s * 0.78f
        ),
        radius = s * 0.47f,
        center = c
    )
    drawCircle(accent.copy(alpha = 0.50f), s * 0.43f, c, style = Stroke(s * 0.035f))
    drawCircle(Color.White.copy(alpha = 0.65f), s * 0.08f, Offset(c.x - s * 0.17f, c.y - s * 0.18f))

    when (icon) {
        "gift" -> drawGiftBadge(c, s, accent, dark)
        "eye" -> drawEyeBadge(c, s, accent, dark)
        "target" -> drawTargetBadge(c, s, accent, dark)
        "puzzle" -> drawPuzzleBadge(c, s, accent, dark)
        "palette" -> drawPaletteBadge(c, s, accent, dark)
        "camera" -> drawCameraBadge(c, s, accent, dark)
        else -> drawStarBadge(c, s, accent, dark)
    }
}

private fun profileBadgeAccent(icon: String): Color = when (icon) {
    "gift" -> Color(0xFFFFB84D)
    "eye" -> Color(0xFF5FB5FF)
    "target" -> Color(0xFFFF7A7A)
    "puzzle" -> Color(0xFF64C58E)
    "palette" -> Color(0xFFFFC75F)
    "camera" -> Color(0xFF6FA7F7)
    else -> Color(0xFFFFD24D)
}

private fun DrawScope.drawGiftBadge(c: Offset, s: Float, accent: Color, dark: Color) {
    drawRect(accent, Offset(c.x - s * 0.22f, c.y - s * 0.04f), Size(s * 0.44f, s * 0.25f))
    drawRect(accent.copy(alpha = 0.82f), Offset(c.x - s * 0.25f, c.y - s * 0.17f), Size(s * 0.50f, s * 0.13f))
    drawLine(Color.White, Offset(c.x, c.y - s * 0.17f), Offset(c.x, c.y + s * 0.21f), s * 0.035f, StrokeCap.Round)
    drawLine(Color.White, Offset(c.x - s * 0.24f, c.y - s * 0.04f), Offset(c.x + s * 0.24f, c.y - s * 0.04f), s * 0.026f, StrokeCap.Round)
    drawArc(dark, 130f, 210f, false, Offset(c.x - s * 0.19f, c.y - s * 0.31f), Size(s * 0.20f, s * 0.18f), style = Stroke(s * 0.035f, cap = StrokeCap.Round))
    drawArc(dark, -20f, 210f, false, Offset(c.x + s * 0.01f, c.y - s * 0.31f), Size(s * 0.20f, s * 0.18f), style = Stroke(s * 0.035f, cap = StrokeCap.Round))
}

private fun DrawScope.drawEyeBadge(c: Offset, s: Float, accent: Color, dark: Color) {
    val eye = Path().apply {
        moveTo(c.x - s * 0.29f, c.y)
        cubicTo(c.x - s * 0.12f, c.y - s * 0.18f, c.x + s * 0.12f, c.y - s * 0.18f, c.x + s * 0.29f, c.y)
        cubicTo(c.x + s * 0.12f, c.y + s * 0.18f, c.x - s * 0.12f, c.y + s * 0.18f, c.x - s * 0.29f, c.y)
        close()
    }
    drawPath(eye, accent.copy(alpha = 0.34f))
    drawPath(eye, dark, style = Stroke(s * 0.036f, cap = StrokeCap.Round))
    drawCircle(accent, s * 0.115f, c)
    drawCircle(dark, s * 0.060f, c)
    drawCircle(Color.White, s * 0.022f, Offset(c.x - s * 0.025f, c.y - s * 0.026f))
}

private fun DrawScope.drawTargetBadge(c: Offset, s: Float, accent: Color, dark: Color) {
    drawCircle(accent.copy(alpha = 0.25f), s * 0.25f, c)
    drawCircle(dark, s * 0.25f, c, style = Stroke(s * 0.034f))
    drawCircle(accent, s * 0.145f, c, style = Stroke(s * 0.034f))
    drawCircle(dark, s * 0.040f, c)
    drawLine(dark, Offset(c.x, c.y - s * 0.32f), Offset(c.x, c.y - s * 0.22f), s * 0.026f, StrokeCap.Round)
    drawLine(dark, Offset(c.x, c.y + s * 0.22f), Offset(c.x, c.y + s * 0.32f), s * 0.026f, StrokeCap.Round)
    drawLine(dark, Offset(c.x - s * 0.32f, c.y), Offset(c.x - s * 0.22f, c.y), s * 0.026f, StrokeCap.Round)
    drawLine(dark, Offset(c.x + s * 0.22f, c.y), Offset(c.x + s * 0.32f, c.y), s * 0.026f, StrokeCap.Round)
}

private fun DrawScope.drawPuzzleBadge(c: Offset, s: Float, accent: Color, dark: Color) {
    drawRect(accent.copy(alpha = 0.78f), Offset(c.x - s * 0.23f, c.y - s * 0.19f), Size(s * 0.42f, s * 0.38f))
    drawCircle(Color.White.copy(alpha = 0.92f), s * 0.060f, Offset(c.x - s * 0.02f, c.y - s * 0.19f))
    drawCircle(Color.White.copy(alpha = 0.92f), s * 0.058f, Offset(c.x + s * 0.19f, c.y + s * 0.02f))
    drawLine(dark, Offset(c.x - s * 0.23f, c.y - s * 0.19f), Offset(c.x + s * 0.19f, c.y - s * 0.19f), s * 0.032f, StrokeCap.Round)
    drawLine(dark, Offset(c.x - s * 0.23f, c.y + s * 0.19f), Offset(c.x + s * 0.19f, c.y + s * 0.19f), s * 0.032f, StrokeCap.Round)
}

private fun DrawScope.drawPaletteBadge(c: Offset, s: Float, accent: Color, dark: Color) {
    drawOval(accent.copy(alpha = 0.50f), Offset(c.x - s * 0.28f, c.y - s * 0.20f), Size(s * 0.56f, s * 0.40f))
    drawCircle(Color.White, s * 0.075f, Offset(c.x + s * 0.12f, c.y + s * 0.05f))
    drawCircle(Color(0xFFFF7A7A), s * 0.040f, Offset(c.x - s * 0.13f, c.y - s * 0.06f))
    drawCircle(Color(0xFF64C58E), s * 0.040f, Offset(c.x, c.y - s * 0.11f))
    drawCircle(Color(0xFF5FB5FF), s * 0.040f, Offset(c.x + s * 0.11f, c.y - s * 0.05f))
    drawPath(Path().apply {
        moveTo(c.x - s * 0.20f, c.y + s * 0.14f)
        quadraticBezierTo(c.x + s * 0.03f, c.y + s * 0.29f, c.x + s * 0.22f, c.y + s * 0.10f)
    }, dark, style = Stroke(s * 0.030f, cap = StrokeCap.Round))
}

private fun DrawScope.drawCameraBadge(c: Offset, s: Float, accent: Color, dark: Color) {
    drawRect(accent.copy(alpha = 0.72f), Offset(c.x - s * 0.27f, c.y - s * 0.11f), Size(s * 0.54f, s * 0.33f))
    drawRect(dark.copy(alpha = 0.80f), Offset(c.x - s * 0.17f, c.y - s * 0.19f), Size(s * 0.20f, s * 0.08f))
    drawCircle(Color.White, s * 0.125f, c)
    drawCircle(dark, s * 0.085f, c)
    drawCircle(Color.White.copy(alpha = 0.75f), s * 0.024f, Offset(c.x - s * 0.03f, c.y - s * 0.035f))
}

private fun DrawScope.drawStarBadge(c: Offset, s: Float, accent: Color, dark: Color) {
    val star = Path().apply {
        moveTo(c.x, c.y - s * 0.30f)
        lineTo(c.x + s * 0.07f, c.y - s * 0.08f)
        lineTo(c.x + s * 0.29f, c.y - s * 0.08f)
        lineTo(c.x + s * 0.11f, c.y + s * 0.04f)
        lineTo(c.x + s * 0.18f, c.y + s * 0.27f)
        lineTo(c.x, c.y + s * 0.12f)
        lineTo(c.x - s * 0.18f, c.y + s * 0.27f)
        lineTo(c.x - s * 0.11f, c.y + s * 0.04f)
        lineTo(c.x - s * 0.29f, c.y - s * 0.08f)
        lineTo(c.x - s * 0.07f, c.y - s * 0.08f)
        close()
    }
    drawPath(star, accent)
    drawPath(star, dark.copy(alpha = 0.55f), style = Stroke(s * 0.024f, cap = StrokeCap.Round))
}

@Composable
private fun BadgeRequirementDialog(
    badge: ProfileBadge,
    unlocked: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { ProfileBadgeArt(icon = badge.icon, unlocked = unlocked, modifier = Modifier.size(46.dp)) },
        title = {
            Text(
                badge.title,
                color = ProfileTextPrimary,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (unlocked) "Huy hiệu này đã mở khóa." else "Điều kiện mở khóa:",
                    color = ProfileTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    badgeUnlockCondition(badge),
                    color = ProfileTextSecondary,
                    lineHeight = 20.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đã hiểu", color = ProfileBlue, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = EgDesign.card,
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
private fun ProfilePersonalInfoGrid(profile: UserProfileDto?) {
    ProfileSurface {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                EgVectorEmojiIcon("user", size = 20.dp)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "Thông tin của bé",
                        color = ProfileTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Bố mẹ có thể chỉnh sửa thông tin khi cần.",
                        color = ProfileTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTile("user", "Tên đăng nhập", fallback(profile?.username), Modifier.weight(1f))
                InfoTile("mail", "Email", fallback(profile?.email), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTile("child", "Tên hiển thị", personalFallback(profile?.name), Modifier.weight(1f))
                InfoTile("cake", "Tuổi", profile?.child?.age?.let { "$it tuổi" } ?: "Chưa có", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTile("calendar", "Ngày sinh", formatPersonalDate(profile?.child?.dob), Modifier.weight(1f))
                InfoTile("user", "Giới tính", formatGender(profile?.child?.gender), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTile("phone", "Số điện thoại", fallback(profile?.child?.phone), Modifier.weight(1f))
                InfoTile("calendar", "Ngày tham gia", formatPersonalDate(profile?.createdAt), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfoTile(icon: String, label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(62.dp),
        shape = RoundedCornerShape(11.dp),
        color = ProfileSoftSection,
        border = BorderStroke(1.dp, ProfileCardBorder.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                EgVectorEmojiIcon(icon, size = 17.dp)
                Text(
                    text = label,
                    color = ProfileBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
private fun ProfileActions(onEdit: () -> Unit) {
    GradientPill(
        text = "Chỉnh sửa hồ sơ",
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
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
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
            .egTactileClick(onClick = onClick),
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
        color = EgDesign.cardSoft,
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = ProfileTextPrimary,
            lineHeight = 17.sp,
            fontSize = 13.sp
        )
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
            dateOfBirth.trim().isNotEmpty() && !isValidBackendDate(dateOfBirth.trim()) ->
                "Ngày sinh phải đúng định dạng yyyy-MM-dd và là ngày hợp lệ."
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
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 18.dp)
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val twoColumns = maxWidth >= 620.dp

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .widthIn(max = 720.dp),
                shape = RoundedCornerShape(EgDesign.radiusXLarge),
                color = EgDesign.card,
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
                        } else {
                            ProfileTextField(username, { username = it }, "Tên đăng nhập", "Nhập tên đăng nhập")
                            ProfileTextField(email, { email = it }, "Email", "email@example.com", keyboardType = KeyboardType.Email)
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
                                second = { ProfileTextField(dateOfBirth, { dateOfBirth = it.take(10) }, "Ngày sinh", "YYYY-MM-DD", trailing = "calendar") }
                            )
                            ProfileTextField(phone, { input -> if (input.all(Char::isDigit) && input.length <= 15) phone = input }, "Số điện thoại", "Nhập số điện thoại", keyboardType = KeyboardType.Phone)
                        } else {
                            ProfileTextField(name, { name = it }, "Tên hiển thị", "Tên của bé")
                            ProfileTextField(age, { input -> if (input.all(Char::isDigit) && input.length <= 3) age = input }, "Tuổi", "Ví dụ: 6", keyboardType = KeyboardType.Number)
                            GenderDropdown(gender, onValueChange = { gender = it })
                            ProfileTextField(dateOfBirth, { dateOfBirth = it.take(10) }, "Ngày sinh", "YYYY-MM-DD", trailing = "calendar")
                            ProfileTextField(phone, { input -> if (input.all(Char::isDigit) && input.length <= 15) phone = input }, "Số điện thoại", "Nhập số điện thoại", keyboardType = KeyboardType.Phone)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryPillButton("Hủy", enabled = !saving, onClick = onDismiss, modifier = Modifier.weight(1f))
                        GradientPill(
                            text = if (saving) "Đang lưu..." else "Lưu thay đổi",
                            onClick = {
                                if (!saving && validate()) {
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
            color = EgDesign.cardSoft,
            border = BorderStroke(1.dp, ProfileCardBorder)
        ) {
            Box(contentAlignment = Alignment.Center) { EgVectorEmojiIcon("edit", size = 23.dp, tint = ProfileBlue) }
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
            .egTactileClick(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = EgDesign.cardSoft,
        border = BorderStroke(1.dp, ProfileCardBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            EgVectorEmojiIcon("close", size = 20.dp, tint = ProfileTextPrimary)
        }
    }
}

@Composable
private fun FormErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = EgDesign.cardSoft,
        border = BorderStroke(1.dp, ProfileCardBorder)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            color = Color(0xFFF43F5E),
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
        trailingIcon = trailing?.let { icon -> { EgVectorEmojiIcon(icon, size = 17.dp, tint = ProfileBlue) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ProfileBlue,
            unfocusedBorderColor = ProfileCardBorder,
            focusedContainerColor = EgDesign.card,
            unfocusedContainerColor = EgDesign.card,
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
            color = EgDesign.card,
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
                EgVectorEmojiIcon("expand", size = 20.dp, tint = ProfileTextPrimary)
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
            .egTactileClick(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = EgDesign.card,
        border = BorderStroke(1.dp, ProfileCardBorder),
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, color = ProfileTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

private fun profileBadges(): List<ProfileBadge> {
    return listOf(
        ProfileBadge(GameUiCatalog.GAME_RECOGNIZE_EMOTION, "Kho Báu Cảm Xúc", "gift"),
        ProfileBadge(GameUiCatalog.GAME_DETECTIVE, "Mắt Thần Cảm Xúc", "eye"),
        ProfileBadge(GameUiCatalog.GAME_EMOTION_MATCH, "Xạ Thủ Cảm Xúc", "target"),
        ProfileBadge(GameUiCatalog.GAME_FACE_ASSEMBLY, "Bậc Thầy Lắp Ghép", "puzzle"),
        ProfileBadge(GameUiCatalog.GAME_CV_STORY, "Nghệ Sĩ Khuôn Mặt", "palette"),
        ProfileBadge(GameUiCatalog.GAME_CV_REQUEST, "Ngôi Sao Biểu Cảm", "camera"),
        ProfileBadge("all", "Vương Miện Cảm Xúc", "star")
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
        .filter { gameId -> hasCompletedAllGameLevels(gameId, sessions) }
        .toMutableSet()

    val cvComplete = listOf("happy", "sad", "surprise", "angry", "fear", "disgust")
        .all { emotion -> cvEmotionScore(cvEmotionScores, emotion) > 90f }
    if (cvComplete) unlocked += GameUiCatalog.GAME_CV_REQUEST

    if (profileBadges().filter { it.id != "all" }.all { it.id in unlocked }) {
        unlocked += "all"
    }
    return unlocked
}

private fun hasCompletedAllGameLevels(gameId: String, sessions: List<SessionHistoryItemDto>): Boolean {
    val maxLevel = GameUiCatalog.gameById(gameId)?.maxLevel ?: return false
    return (1..maxLevel).all { level ->
        sessions.any { session ->
            session.gameId == gameId &&
                (session.level ?: 1) == level &&
                (session.score ?: 0) >= badgePassThreshold(gameId, level)
        }
    }
}

private fun badgePassThreshold(gameId: String, level: Int): Int {
    if (GameUiCatalog.isClickGame(gameId)) return 30
    return when (level) {
        1 -> 40
        2 -> 50
        3 -> 60
        4 -> 70
        5 -> 80
        else -> 90
    }
}

private fun cvEmotionScore(scores: Map<String, Float>, emotionId: String): Float {
    val aliases = when (emotionId) {
        "happy" -> listOf("happy", "vui", "vui vẻ")
        "sad" -> listOf("sad", "buồn", "buồn bã")
        "surprise" -> listOf("surprise", "surprised", "ngạc nhiên")
        "angry" -> listOf("angry", "tức giận")
        "fear" -> listOf("fear", "sợ hãi")
        "disgust" -> listOf("disgust", "disgusted", "ghê tởm")
        else -> listOf(emotionId)
    }
    return aliases.maxOf { alias -> scores[alias] ?: 0f }
}

private fun badgeUnlockCondition(badge: ProfileBadge): String {
    return when (badge.id) {
        GameUiCatalog.GAME_CV_REQUEST ->
            "Mở khóa khi cả 6 cảm xúc trong Thử thách cảm xúc đều đạt trên 90%."
        "all" ->
            "Mở khóa khi tất cả huy hiệu game khác đã được mở."
        else -> {
            val gameName = GameUiCatalog.gameById(badge.id)?.title ?: badge.title
            "Mở khóa khi bé vượt qua tất cả cấp độ của game $gameName."
        }
    }
}

private fun fallback(value: String?): String {
    return value?.takeIf { it.isNotBlank() } ?: "Chưa có"
}

private fun formatGender(value: String?): String {
    return when (value?.trim()?.lowercase(Locale.US)) {
        "male", "nam" -> "Nam"
        "female", "nữ", "nu" -> "Nữ"
        "other", "khác", "khac" -> "Khác"
        null, "" -> "Chưa có"
        else -> value.trim()
    }
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
