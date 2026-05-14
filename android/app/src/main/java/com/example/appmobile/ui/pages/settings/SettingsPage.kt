package com.example.appmobile.ui.pages.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.UserProfileDto
import com.example.appmobile.data.remote.dto.UserProfileUpdateDto
import com.example.appmobile.data.repository.UserRepository
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgSoftCard
import com.example.appmobile.ui.state.AppSettingsState
import com.example.appmobile.ui.state.AppThemeMode
import com.example.appmobile.ui.state.CvEmotionScoreState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun SettingsPage(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val repository = remember {
        UserRepository(NetworkClient.apiService, FirebaseAuthHelper(), database.userDao())
    }
    val userId = remember {
        FirebaseAuth.getInstance().currentUser?.uid ?: AppSession.getBackendUserId(context)
    }
    val isLoggedIn = userId != null
    val systemSettingsIntent = remember(context) {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }

    var profile by remember { mutableStateOf<UserProfileDto?>(null) }
    var accountLoading by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showParentGate by remember { mutableStateOf(false) }
    var showParentArea by remember { mutableStateOf(false) }
    var showEditAccount by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showCameraPrivacy by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    val assistantBubbleEnabled by AppSettingsState.assistantBubbleEnabled
    val autoPlayVideo by AppSettingsState.learnVideoAutoplayEnabled
    val videoSoundEnabled by AppSettingsState.learnVideoSoundEnabled
    val soundEffectsEnabled by AppSettingsState.soundEffectsEnabled
    val learningReminderEnabled by AppSettingsState.learningReminderEnabled
    val dynamicColorEnabled by AppSettingsState.dynamicColorEnabled
    val themeMode by AppSettingsState.themeMode

    fun openSystemSettings() {
        context.startActivity(systemSettingsIntent)
    }

    fun loadProfile() {
        if (!isLoggedIn || userId == null) {
            accountLoading = false
            accountError = false
            profile = null
            return
        }
        scope.launch {
            accountLoading = true
            accountError = false
            val loaded = repository.getProfile(userId)
            profile = loaded
            accountError = loaded == null
            accountLoading = false
        }
    }

    LaunchedEffect(userId) {
        loadProfile()
    }

    SettingsScreen(
        themeMode = themeMode,
        dynamicColorEnabled = dynamicColorEnabled,
        assistantBubbleEnabled = assistantBubbleEnabled,
        autoPlayVideo = autoPlayVideo,
        videoSoundEnabled = videoSoundEnabled,
        soundEffectsEnabled = soundEffectsEnabled,
        learningReminderEnabled = learningReminderEnabled,
        isLoggedIn = isLoggedIn,
        statusMessage = statusMessage,
        onBack = onBack,
        onThemeModeChanged = { AppSettingsState.setThemeMode(context, it) },
        onDynamicColorChanged = { AppSettingsState.setDynamicColorEnabled(context, it) },
        onAssistantBubbleChanged = { AppSettingsState.setAssistantBubbleEnabled(context, it) },
        onAutoPlayVideoChanged = { AppSettingsState.setLearnVideoAutoplayEnabled(context, it) },
        onVideoSoundChanged = { AppSettingsState.setLearnVideoSoundEnabled(context, it) },
        onSoundEffectsChanged = { AppSettingsState.setSoundEffectsEnabled(context, it) },
        onOpenParentArea = { showParentGate = true },
        onLogin = onLogin,
        onLogout = { confirmAction = ConfirmAction.Logout }
    )

    if (showParentGate) {
        ParentGateDialog(
            onDismiss = { showParentGate = false },
            onContinue = {
                showParentGate = false
                showParentArea = true
            }
        )
    }

    if (showParentArea) {
        ParentAreaBottomSheet(
            isLoggedIn = isLoggedIn,
            loading = accountLoading,
            error = accountError,
            profile = profile,
            onDismiss = { showParentArea = false },
            onLogin = onLogin,
            onRetry = { loadProfile() },
            onEditAccount = {
                if (isLoggedIn) showEditAccount = true else statusMessage = "Vui lòng đăng nhập để cập nhật tài khoản."
            },
            onChangePassword = {
                if (isLoggedIn) showChangePassword = true else statusMessage = "Vui lòng đăng nhập để đổi mật khẩu."
            },
            onCameraPrivacy = { showCameraPrivacy = true },
            onOpenSystemSettings = ::openSystemSettings,
            onResetPreferences = { confirmAction = ConfirmAction.ResetPreferences },
            onClearProgress = { confirmAction = ConfirmAction.ClearProgress }
        )
    }

    if (showEditAccount) {
        EditAccountDialog(
            profile = profile,
            saving = saving,
            onDismiss = { if (!saving) showEditAccount = false },
            onSave = { update ->
                val targetUserId = userId ?: return@EditAccountDialog
                scope.launch {
                    saving = true
                    val updated = repository.updateProfile(targetUserId, update)
                    saving = false
                    if (updated != null) {
                        profile = updated
                        accountError = false
                        statusMessage = "Đã cập nhật thông tin tài khoản."
                        showEditAccount = false
                    } else {
                        statusMessage = "Không lưu được thông tin. Vui lòng thử lại."
                    }
                }
            }
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            saving = saving,
            onDismiss = { if (!saving) showChangePassword = false },
            onSave = { newPassword ->
                val targetUserId = userId ?: return@ChangePasswordDialog
                scope.launch {
                    saving = true
                    val updated = repository.updateProfile(targetUserId, UserProfileUpdateDto(password = newPassword))
                    saving = false
                    if (updated != null) {
                        statusMessage = "Đã đổi mật khẩu."
                        showChangePassword = false
                    } else {
                        statusMessage = "Không đổi được mật khẩu. Vui lòng thử lại."
                    }
                }
            }
        )
    }

    if (showCameraPrivacy) {
        CameraPrivacyDialog(onDismiss = { showCameraPrivacy = false }, onOpenSystemSettings = ::openSystemSettings)
    }

    confirmAction?.let { action ->
        ConfirmActionDialog(
            action = action,
            onDismiss = { confirmAction = null },
            onConfirm = {
                confirmAction = null
                when (action) {
                    ConfirmAction.ResetPreferences -> {
                        AppSettingsState.resetLocalPreferences(context)
                        statusMessage = "Đã đặt lại tùy chọn trên máy."
                    }
                    ConfirmAction.ClearProgress -> {
                        scope.launch {
                            val targetUserId = userId ?: "local-player"
                            runCatching {
                                database.sessionDao().clearLearningHistoryForUser(targetUserId)
                                database.reportDao().clearProgressForChild(targetUserId)
                                database.reportDao().clearOldReports(targetUserId)
                                CvEmotionScoreState.clearScores(context, targetUserId)
                            }.onSuccess {
                                statusMessage = "Đã xóa tiến độ học trên thiết bị này."
                            }.onFailure {
                                statusMessage = "Không xóa được tiến độ học. Vui lòng thử lại."
                            }
                        }
                    }
                    ConfirmAction.Logout -> onLogout()
                }
            }
        )
    }
}

@Composable
private fun SettingsScreen(
    themeMode: AppThemeMode,
    dynamicColorEnabled: Boolean,
    assistantBubbleEnabled: Boolean,
    autoPlayVideo: Boolean,
    videoSoundEnabled: Boolean,
    soundEffectsEnabled: Boolean,
    learningReminderEnabled: Boolean,
    isLoggedIn: Boolean,
    statusMessage: String?,
    onBack: () -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAssistantBubbleChanged: (Boolean) -> Unit,
    onAutoPlayVideoChanged: (Boolean) -> Unit,
    onVideoSoundChanged: (Boolean) -> Unit,
    onSoundEffectsChanged: (Boolean) -> Unit,
    onOpenParentArea: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EgDesign.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = EgDesign.screenPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsHeader(onBack = onBack)
        statusMessage?.let { SettingsStatusBanner(it) }
        AppearanceSection(
            themeMode = themeMode,
            dynamicColorEnabled = dynamicColorEnabled,
            onThemeModeChanged = onThemeModeChanged,
            onDynamicColorChanged = onDynamicColorChanged
        )
        LearningExperienceSection(
            assistantBubbleEnabled = assistantBubbleEnabled,
            autoPlayVideo = autoPlayVideo,
            videoSoundEnabled = videoSoundEnabled,
            soundEffectsEnabled = soundEffectsEnabled,
            learningReminderEnabled = learningReminderEnabled,
            onAssistantBubbleChanged = onAssistantBubbleChanged,
            onAutoPlayVideoChanged = onAutoPlayVideoChanged,
            onVideoSoundChanged = onVideoSoundChanged,
            onSoundEffectsChanged = onSoundEffectsChanged
        )
        ParentAreaEntryCard(onClick = onOpenParentArea)
        AboutAppSection()
        SessionSection(isLoggedIn = isLoggedIn, onLogin = onLogin, onLogout = onLogout)
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppBackButton(onClick = onBack)
            Spacer(modifier = Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cài đặt", color = EgDesign.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Tùy chỉnh trải nghiệm học và chơi cho bé.",
                color = EgDesign.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    themeMode: AppThemeMode,
    dynamicColorEnabled: Boolean,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit
) {
    SettingsSection(title = "Giao diện", icon = "🎨") {
        SegmentedThemeSelector(themeMode = themeMode, onThemeModeChanged = onThemeModeChanged)
        SwitchSettingsRow(
            icon = "🎨",
            title = "Màu theo thiết bị",
            description = "Giao diện tự đồng bộ màu theo cài đặt của điện thoại nếu được hỗ trợ.",
            checked = dynamicColorEnabled,
            onCheckedChange = onDynamicColorChanged
        )
    }
}

@Composable
private fun LearningExperienceSection(
    assistantBubbleEnabled: Boolean,
    autoPlayVideo: Boolean,
    videoSoundEnabled: Boolean,
    soundEffectsEnabled: Boolean,
    learningReminderEnabled: Boolean,
    onAssistantBubbleChanged: (Boolean) -> Unit,
    onAutoPlayVideoChanged: (Boolean) -> Unit,
    onVideoSoundChanged: (Boolean) -> Unit,
    onSoundEffectsChanged: (Boolean) -> Unit
) {
    SettingsSection(title = "Trải nghiệm học", icon = "✨") {
        SwitchSettingsRow(
            icon = "💬",
            title = "Bong bóng trợ lý",
            description = "Hiện nút trợ lý nhỏ ở góc màn hình.",
            checked = assistantBubbleEnabled,
            onCheckedChange = onAssistantBubbleChanged
        )
        ThinDivider()
        SwitchSettingsRow(
            icon = "▶",
            title = "Tự phát video mẫu",
            description = "Video ở trang Học sẽ tự chạy khi mở cảm xúc.",
            checked = autoPlayVideo,
            onCheckedChange = onAutoPlayVideoChanged
        )
        ThinDivider()
        SwitchSettingsRow(
            icon = "🔊",
            title = "Âm thanh video",
            description = "Bật hoặc tắt tiếng cho video mẫu.",
            checked = videoSoundEnabled,
            onCheckedChange = onVideoSoundChanged
        )
        ThinDivider()
        SwitchSettingsRow(
            icon = "♪",
            title = "Âm thanh hiệu ứng",
            description = "Bật âm thanh khi bấm nút, trả lời đúng hoặc nhận phần thưởng.",
            checked = soundEffectsEnabled,
            onCheckedChange = onSoundEffectsChanged
        )
        ThinDivider()
        SwitchSettingsRow(
            icon = "🔔",
            title = "Nhắc nhở học tập",
            description = "Nhắc bé luyện tập mỗi ngày.",
            checked = learningReminderEnabled,
            onCheckedChange = {},
            enabled = false,
            badge = "Sắp ra mắt"
        )
    }
}

@Composable
private fun ParentAreaEntryCard(onClick: () -> Unit) {
    EgSoftCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsIcon("👤")
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Khu vực phụ huynh", color = EgDesign.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Quản lý tài khoản, bảo mật, quyền riêng tư và dữ liệu học tập của bé.",
                    color = EgDesign.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(">", color = EgDesign.textSecondary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AboutAppSection() {
    SettingsSection(title = "Về ứng dụng", icon = "ℹ") {
        CompactValueRow("ℹ", "Phiên bản ứng dụng", "1.0")
        ThinDivider()
        CompactValueRow("🔐", "Chính sách quyền riêng tư", "Sắp cập nhật")
        ThinDivider()
        CompactValueRow("📄", "Điều khoản sử dụng", "Sắp cập nhật")
        ThinDivider()
        CompactValueRow("☎", "Liên hệ hỗ trợ", "Sắp cập nhật")
    }
}

@Composable
private fun SessionSection(
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    if (isLoggedIn) {
        SettingsButton(
            text = "Đăng xuất",
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            danger = true,
            tonal = true
        )
    } else {
        SettingsButton(
            text = "Đăng nhập",
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ParentAreaBottomSheet(
    isLoggedIn: Boolean,
    loading: Boolean,
    error: Boolean,
    profile: UserProfileDto?,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    onEditAccount: () -> Unit,
    onChangePassword: () -> Unit,
    onCameraPrivacy: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onResetPreferences: () -> Unit,
    onClearProgress: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .heightIn(max = 720.dp),
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                color = EgDesign.card,
                border = BorderStroke(1.dp, EgDesign.cardBorder),
                shadowElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BottomSheetHeader(
                        title = "Khu vực phụ huynh",
                        subtitle = "Quản lý tài khoản, quyền riêng tư và dữ liệu của bé.",
                        onDismiss = onDismiss
                    )
                    ParentAccountSection(
                        isLoggedIn = isLoggedIn,
                        loading = loading,
                        error = error,
                        profile = profile,
                        onLogin = onLogin,
                        onRetry = onRetry,
                        onEdit = onEditAccount
                    )
                    ParentCompactSection(title = "Bảo mật", icon = "🔒") {
                        ActionSettingsRow(
                            icon = "🔒",
                            title = "Đổi mật khẩu",
                            description = "Bạn có thể đổi mật khẩu đăng nhập tại đây.",
                            actionText = "Đổi",
                            onClick = onChangePassword
                        )
                    }
                    ParentCompactSection(title = "Quyền riêng tư", icon = "📷") {
                        ActionSettingsRow(
                            icon = "📷",
                            title = "Quyền riêng tư camera",
                            description = "Camera chỉ dùng để nhận diện biểu cảm khi chơi. App không lưu ảnh hoặc video của bé.",
                            actionText = "Xem",
                            onClick = onCameraPrivacy
                        )
                        ThinDivider()
                        ActionSettingsRow(
                            icon = "⚙",
                            title = "Quyền ứng dụng",
                            description = "Cấp quyền camera để chơi thử thách biểu cảm và quyền thông báo để nhận nhắc nhở học tập.",
                            actionText = "Mở",
                            onClick = onOpenSystemSettings
                        )
                    }
                    ParentCompactSection(title = "Dữ liệu học tập", icon = "💾") {
                        ActionSettingsRow(
                            icon = "↺",
                            title = "Đặt lại tùy chọn",
                            description = "Khôi phục giao diện, trợ lý, video và âm thanh về mặc định.",
                            actionText = "Đặt lại",
                            onClick = onResetPreferences
                        )
                        ThinDivider()
                        ActionSettingsRow(
                            icon = "💾",
                            title = "Xóa tiến độ học",
                            description = "Xóa điểm, lịch sử chơi và tiến độ học của bé.",
                            actionText = "Xóa tiến độ",
                            onClick = onClearProgress,
                            danger = true,
                            fullWidthAction = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentAccountSection(
    isLoggedIn: Boolean,
    loading: Boolean,
    error: Boolean,
    profile: UserProfileDto?,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit
) {
    ParentCompactSection(title = "Tài khoản", icon = "👤") {
        when {
            !isLoggedIn -> {
                ActionSettingsRow(
                    icon = "👤",
                    title = "Bạn chưa đăng nhập",
                    description = "Đăng nhập để đồng bộ tiến độ học của bé.",
                    actionText = "Đăng nhập",
                    onClick = onLogin
                )
            }
            loading -> AccountLoadingRow()
            error -> {
                ActionSettingsRow(
                    icon = "!",
                    title = "Không tải được thông tin tài khoản.",
                    description = "Vui lòng thử lại.",
                    actionText = "Thử lại",
                    onClick = onRetry
                )
            }
            else -> {
                CompactValueRow("👤", "Tên đăng nhập", missing(profile?.username))
                ThinDivider()
                CompactValueRow("✉", "Email", missing(profile?.email))
                ThinDivider()
                CompactValueRow("☎", "Số điện thoại", missing(profile?.child?.phone))
                ThinDivider()
                ActionSettingsRow(
                    icon = "👤",
                    title = "Hồ sơ bé",
                    description = "Cập nhật thông tin tài khoản khi cần.",
                    actionText = "Sửa",
                    onClick = onEdit
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: String,
    content: @Composable ColumnScope.() -> Unit
) {
    EgSoftCard {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(title = title, icon = icon)
            content()
        }
    }
}

@Composable
private fun ParentCompactSection(
    title: String,
    icon: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title = title, icon = icon)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = EgDesign.cardSoft,
            border = BorderStroke(1.dp, EgDesign.cardBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        SettingsIcon(icon, size = 34.dp)
        Text(title, color = EgDesign.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SegmentedThemeSelector(
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsChoiceButton("Hệ thống", themeMode == AppThemeMode.System, { onThemeModeChanged(AppThemeMode.System) }, Modifier.weight(1f))
        SettingsChoiceButton("Sáng", themeMode == AppThemeMode.Light, { onThemeModeChanged(AppThemeMode.Light) }, Modifier.weight(1f))
        SettingsChoiceButton("Tối", themeMode == AppThemeMode.Dark, { onThemeModeChanged(AppThemeMode.Dark) }, Modifier.weight(1f))
    }
}

@Composable
private fun SwitchSettingsRow(
    icon: String,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    badge: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsIcon(icon, size = 36.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = if (enabled) EgDesign.textPrimary else EgDesign.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                badge?.let { SettingsBadge(it) }
            }
            Text(description, color = EgDesign.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EgDesign.primary,
                checkedBorderColor = EgDesign.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = EgDesign.cardSoft,
                uncheckedBorderColor = EgDesign.cardBorder,
                disabledCheckedThumbColor = Color.White.copy(alpha = 0.7f),
                disabledCheckedTrackColor = EgDesign.cardBorder,
                disabledUncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                disabledUncheckedTrackColor = EgDesign.cardBorder
            )
        )
    }
}

@Composable
private fun ActionSettingsRow(
    icon: String,
    title: String,
    description: String,
    actionText: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    fullWidthAction: Boolean = false
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 360.dp || fullWidthAction
        if (narrow) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactTextBlock(icon = icon, title = title, description = description)
                SettingsButton(
                    text = actionText,
                    onClick = onClick,
                    modifier = if (fullWidthAction) Modifier.fillMaxWidth() else Modifier.align(Alignment.End),
                    danger = danger,
                    tonal = danger,
                    minWidth = 120.dp
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactTextBlock(icon = icon, title = title, description = description, modifier = Modifier.weight(1f))
                SettingsButton(text = actionText, onClick = onClick, danger = danger, tonal = danger, minWidth = 112.dp)
            }
        }
    }
}

@Composable
private fun CompactValueRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsIcon(icon, size = 34.dp)
        Text(label, modifier = Modifier.weight(1f), color = EgDesign.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = EgDesign.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
    }
}

@Composable
private fun CompactTextBlock(
    icon: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsIcon(icon, size = 34.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = EgDesign.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(description, color = EgDesign.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AccountLoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = EgDesign.primary)
        Text("Đang tải thông tin tài khoản...", color = EgDesign.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BottomSheetHeader(title: String, subtitle: String, onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsIcon("👤")
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = EgDesign.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Surface(
            modifier = Modifier.size(40.dp).clickable(onClick = onDismiss),
            shape = CircleShape,
            color = EgDesign.cardSoft,
            border = BorderStroke(1.dp, EgDesign.cardBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("x", color = EgDesign.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsChoiceButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(42.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(EgDesign.pillRadius),
        color = if (selected) EgDesign.primary else EgDesign.cardSoft,
        border = BorderStroke(1.dp, if (selected) EgDesign.primaryDark else EgDesign.cardBorder),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (selected) Color.White else EgDesign.primaryDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    tonal: Boolean = false,
    enabled: Boolean = true,
    minWidth: Dp = 132.dp
) {
    val background = when {
        danger && tonal -> Color(0xFFFFF1F2)
        danger -> Color(0xFFEF4444)
        tonal -> EgDesign.cardSoft
        else -> EgDesign.primary
    }
    val textColor = when {
        danger && tonal -> Color(0xFFB91C1C)
        danger -> Color.White
        tonal -> EgDesign.primaryDark
        else -> Color.White
    }
    Surface(
        modifier = modifier.height(44.dp).widthIn(min = minWidth).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(EgDesign.pillRadius),
        color = if (enabled) background else EgDesign.cardBorder,
        border = BorderStroke(1.dp, if (danger && tonal) Color(0xFFFECACA) else EgDesign.cardBorder),
        shadowElevation = if (enabled) 1.dp else 0.dp
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (enabled) textColor else EgDesign.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SettingsIcon(icon: String, size: Dp = 40.dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = EgDesign.accentSoft,
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(icon, fontSize = if (size < 36.dp) 15.sp else 19.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SettingsBadge(text: String) {
    Surface(shape = RoundedCornerShape(EgDesign.pillRadius), color = EgDesign.cardSoft, border = BorderStroke(1.dp, EgDesign.cardBorder)) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = EgDesign.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ThinDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(EgDesign.cardBorder.copy(alpha = 0.55f)))
}

@Composable
private fun SettingsStatusBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFEFFAF3),
        border = BorderStroke(1.dp, Color(0xFFC7E9D0))
    ) {
        Text(message, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = Color(0xFF166534), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFF1F2),
        border = BorderStroke(1.dp, Color(0xFFFDA4AF))
    ) {
        Text(message, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = Color(0xFF9F1239), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EditAccountDialog(
    profile: UserProfileDto?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (UserProfileUpdateDto) -> Unit
) {
    var username by rememberSaveable(profile?.userId) { mutableStateOf(profile?.username.orEmpty()) }
    var email by rememberSaveable(profile?.userId) { mutableStateOf(profile?.email.orEmpty()) }
    var phone by rememberSaveable(profile?.userId) { mutableStateOf(profile?.child?.phone.orEmpty()) }
    var error by rememberSaveable(profile?.userId) { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        error = when {
            username.trim().isBlank() -> "Tên đăng nhập không được để trống."
            email.trim().isBlank() -> "Email không được để trống."
            !email.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) -> "Email chưa đúng định dạng."
            phone.trim().isNotEmpty() && !phone.trim().matches(Regex("^\\d{9,11}$")) -> "Số điện thoại nên gồm 9-11 chữ số."
            else -> null
        }
        return error == null
    }

    SettingsDialog(onDismiss = onDismiss) {
        DialogHeader("👤", "Thông tin tài khoản", "Cập nhật tên đăng nhập, email và số điện thoại.")
        error?.let { ErrorBanner(it) }
        SettingsTextField(username, { username = it }, "Tên đăng nhập", "Nhập tên đăng nhập")
        SettingsTextField(email, { email = it }, "Email", "email@example.com", keyboardType = KeyboardType.Email)
        SettingsTextField(
            phone,
            { input: String -> if (input.all(Char::isDigit) && input.length <= 11) phone = input },
            "Số điện thoại",
            "Nhập số điện thoại",
            keyboardType = KeyboardType.Phone
        )
        DialogActions(
            primaryText = if (saving) "Đang lưu..." else "Lưu thay đổi",
            primaryDanger = false,
            saving = saving,
            onCancel = onDismiss,
            onSave = {
                if (!saving && validate()) {
                    onSave(
                        UserProfileUpdateDto(
                            username = username.trim(),
                            email = email.trim(),
                            phoneNumber = phone.trim().ifBlank { null }
                        )
                    )
                }
            }
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        error = when {
            newPassword.isBlank() -> "Mật khẩu mới không được để trống."
            newPassword.length < 8 -> "Mật khẩu mới cần có ít nhất 8 ký tự."
            newPassword != confirmPassword -> "Mật khẩu nhập lại chưa trùng khớp."
            else -> null
        }
        return error == null
    }

    SettingsDialog(onDismiss = onDismiss) {
        DialogHeader("🔒", "Đổi mật khẩu", "Để bảo mật, app sẽ không hiển thị mật khẩu hiện tại.")
        error?.let { ErrorBanner(it) }
        SettingsTextField(currentPassword, { currentPassword = it }, "Mật khẩu hiện tại", "Nhập mật khẩu hiện tại", keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation())
        SettingsTextField(newPassword, { newPassword = it }, "Mật khẩu mới", "Nhập mật khẩu mới", keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation())
        SettingsTextField(confirmPassword, { confirmPassword = it }, "Nhập lại mật khẩu mới", "Nhập lại mật khẩu mới", keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation())
        DialogActions(
            primaryText = if (saving) "Đang lưu..." else "Lưu thay đổi",
            primaryDanger = false,
            saving = saving,
            onCancel = onDismiss,
            onSave = {
                if (!saving && validate()) onSave(newPassword)
            }
        )
    }
}

@Composable
private fun CameraPrivacyDialog(onDismiss: () -> Unit, onOpenSystemSettings: () -> Unit) {
    SettingsDialog(onDismiss = onDismiss) {
        DialogHeader("📷", "Quyền riêng tư camera", "Camera chỉ dùng trong các trò chơi biểu cảm.")
        CameraPrivacyItem("Khi nào camera được bật?", "Camera chỉ bật khi bé bắt đầu trò chơi cần nhận diện khuôn mặt.")
        CameraPrivacyItem("Camera dùng để làm gì?", "Camera giúp app nhận diện biểu cảm trong lúc bé chơi.")
        CameraPrivacyItem("Dữ liệu có được lưu không?", "App không lưu ảnh hoặc video của bé.")
        CameraPrivacyItem("Dữ liệu có gửi lên máy chủ không?", "Quá trình nhận diện được xử lý trên thiết bị; app chỉ lưu kết quả luyện tập như điểm số.")
        CameraPrivacyItem("Cách tắt quyền camera", "Bạn có thể vào quyền ứng dụng của điện thoại và tắt Camera bất cứ lúc nào.")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsButton("Đóng", onClick = onDismiss, modifier = Modifier.weight(1f), tonal = true)
            SettingsButton("Mở cài đặt", onClick = onOpenSystemSettings, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CameraPrivacyItem(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EgDesign.cardSoft, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, color = EgDesign.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Text(description, color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun ParentGateDialog(onDismiss: () -> Unit, onContinue: () -> Unit) {
    SettingsDialog(onDismiss = onDismiss) {
        DialogHeader("👤", "Khu vực phụ huynh", "Phần này dành cho phụ huynh để quản lý tài khoản và dữ liệu của bé.")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsButton("Hủy", onClick = onDismiss, modifier = Modifier.weight(1f), tonal = true)
            SettingsButton("Tiếp tục", onClick = onContinue, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ConfirmActionDialog(
    action: ConfirmAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val content = confirmContent(action)
    SettingsDialog(onDismiss = onDismiss) {
        DialogHeader(content.icon, content.title, content.message)
        DialogActions(
            primaryText = content.confirmText,
            primaryDanger = content.danger,
            saving = false,
            onCancel = onDismiss,
            onSave = onConfirm
        )
    }
}

@Composable
private fun SettingsDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 18.dp)
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                shape = RoundedCornerShape(24.dp),
                color = EgDesign.card,
                border = BorderStroke(1.dp, EgDesign.cardBorder),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun DialogHeader(icon: String, title: String, subtitle: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        SettingsIcon(icon)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = EgDesign.textPrimary, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun DialogActions(
    primaryText: String,
    primaryDanger: Boolean,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsButton("Hủy", enabled = !saving, onClick = onCancel, modifier = Modifier.weight(1f), tonal = true)
        SettingsButton(primaryText, enabled = !saving, onClick = onSave, modifier = Modifier.weight(1f), danger = primaryDanger)
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = EgDesign.primaryDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        placeholder = { Text(placeholder, color = EgDesign.textSecondary, fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EgDesign.primaryDark,
            unfocusedBorderColor = EgDesign.cardBorder,
            focusedContainerColor = EgDesign.card,
            unfocusedContainerColor = EgDesign.card,
            focusedTextColor = EgDesign.textPrimary,
            unfocusedTextColor = EgDesign.textPrimary,
            cursorColor = EgDesign.primaryDark
        )
    )
}

private enum class ConfirmAction {
    ResetPreferences,
    ClearProgress,
    Logout
}

private data class ConfirmContent(
    val icon: String,
    val title: String,
    val message: String,
    val confirmText: String,
    val danger: Boolean
)

private fun confirmContent(action: ConfirmAction): ConfirmContent {
    return when (action) {
        ConfirmAction.ResetPreferences -> ConfirmContent(
            icon = "↺",
            title = "Đặt lại tùy chọn?",
            message = "Các cài đặt giao diện, trợ lý, video và âm thanh sẽ được đưa về mặc định.",
            confirmText = "Đặt lại",
            danger = false
        )
        ConfirmAction.ClearProgress -> ConfirmContent(
            icon = "💾",
            title = "Xóa tiến độ học?",
            message = "Điểm số, lịch sử chơi và tiến độ học của bé sẽ bị xóa. Hành động này không thể hoàn tác.",
            confirmText = "Xóa",
            danger = true
        )
        ConfirmAction.Logout -> ConfirmContent(
            icon = "🚪",
            title = "Đăng xuất?",
            message = "Bạn sẽ cần đăng nhập lại để đồng bộ dữ liệu học tập.",
            confirmText = "Đăng xuất",
            danger = true
        )
    }
}

private fun missing(value: String?): String = value?.takeIf { it.isNotBlank() } ?: "Chưa cập nhật"
