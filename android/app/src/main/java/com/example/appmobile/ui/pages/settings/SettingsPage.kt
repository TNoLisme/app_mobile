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
    val systemSettingsIntent = remember(context) {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
    val userId = remember {
        FirebaseAuth.getInstance().currentUser?.uid ?: AppSession.getBackendUserId(context)
    }
    val isLoggedIn = userId != null

    var profile by remember { mutableStateOf<UserProfileDto?>(null) }
    var accountLoading by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showEditAccount by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showCameraPrivacy by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }
    var parentAction by remember { mutableStateOf<ParentAction?>(null) }

    val assistantBubbleEnabled by AppSettingsState.assistantBubbleEnabled
    val learnVideoAutoplayEnabled by AppSettingsState.learnVideoAutoplayEnabled
    val learnVideoSoundEnabled by AppSettingsState.learnVideoSoundEnabled
    val soundEffectsEnabled by AppSettingsState.soundEffectsEnabled
    val learningReminderEnabled by AppSettingsState.learningReminderEnabled
    val dynamicColorEnabled by AppSettingsState.dynamicColorEnabled
    val themeMode by AppSettingsState.themeMode

    fun openSystemSettings() {
        context.startActivity(systemSettingsIntent)
    }

    fun runParentAction(action: ParentAction) {
        when (action) {
            ParentAction.Account -> {
                if (isLoggedIn) showEditAccount = true else message = "Vui lòng đăng nhập để cập nhật tài khoản."
            }
            ParentAction.Password -> {
                if (isLoggedIn) showChangePassword = true else message = "Vui lòng đăng nhập để đổi mật khẩu."
            }
            ParentAction.CameraPrivacy -> showCameraPrivacy = true
            ParentAction.AppPermissions -> openSystemSettings()
        }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EgDesign.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = EgDesign.screenPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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

        message?.let { SettingsStatusBanner(it) }

        AppearanceSection(
            themeMode = themeMode,
            dynamicColorEnabled = dynamicColorEnabled,
            onThemeModeChanged = { mode -> AppSettingsState.setThemeMode(context, mode) },
            onDynamicColorChanged = { enabled -> AppSettingsState.setDynamicColorEnabled(context, enabled) }
        )

        AssistantSection(
            assistantBubbleEnabled = assistantBubbleEnabled,
            onAssistantBubbleChanged = { enabled -> AppSettingsState.setAssistantBubbleEnabled(context, enabled) }
        )

        LearningMediaSection(
            learnVideoAutoplayEnabled = learnVideoAutoplayEnabled,
            learnVideoSoundEnabled = learnVideoSoundEnabled,
            soundEffectsEnabled = soundEffectsEnabled,
            learningReminderEnabled = learningReminderEnabled,
            onLearnVideoAutoplayChanged = { enabled -> AppSettingsState.setLearnVideoAutoplayEnabled(context, enabled) },
            onLearnVideoSoundChanged = { enabled -> AppSettingsState.setLearnVideoSoundEnabled(context, enabled) },
            onSoundEffectsChanged = { enabled -> AppSettingsState.setSoundEffectsEnabled(context, enabled) }
        )

        ParentAreaCard(
            onOpenAccount = { parentAction = ParentAction.Account },
            onOpenPassword = { parentAction = ParentAction.Password },
            onOpenCameraPrivacy = { parentAction = ParentAction.CameraPrivacy },
            onOpenPermissions = { parentAction = ParentAction.AppPermissions }
        )

        AccountSection(
            isLoggedIn = isLoggedIn,
            loading = accountLoading,
            error = accountError,
            profile = profile,
            onLogin = onLogin,
            onRetry = { loadProfile() },
            onEdit = { parentAction = ParentAction.Account }
        )

        SecuritySection(onChangePassword = { parentAction = ParentAction.Password })

        CameraPrivacySection(onViewDetail = { parentAction = ParentAction.CameraPrivacy })

        AppPermissionsSection(onOpenSystemSettings = { parentAction = ParentAction.AppPermissions })

        DataSection(
            onResetPreferences = { confirmAction = ConfirmAction.ResetPreferences },
            onClearProgress = { confirmAction = ConfirmAction.ClearProgress }
        )

        SessionSection(
            isLoggedIn = isLoggedIn,
            onLogout = { confirmAction = ConfirmAction.Logout },
            onLogin = onLogin
        )

        AboutSection()

        Spacer(modifier = Modifier.height(24.dp))
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
                        message = "Đã cập nhật thông tin tài khoản."
                        showEditAccount = false
                    } else {
                        message = "Không lưu được thông tin. Vui lòng thử lại."
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
                        message = "Đã đổi mật khẩu."
                        showChangePassword = false
                    } else {
                        message = "Không đổi được mật khẩu. Vui lòng thử lại."
                    }
                }
            }
        )
    }

    if (showCameraPrivacy) {
        CameraPrivacyDialog(onDismiss = { showCameraPrivacy = false }, onOpenSystemSettings = ::openSystemSettings)
    }

    parentAction?.let { action ->
        ParentGateDialog(
            onDismiss = { parentAction = null },
            onContinue = {
                parentAction = null
                runParentAction(action)
            }
        )
    }

    confirmAction?.let { action ->
        SettingsConfirmDialog(
            action = action,
            onDismiss = { confirmAction = null },
            onConfirm = {
                confirmAction = null
                when (action) {
                    ConfirmAction.ResetPreferences -> {
                        AppSettingsState.resetLocalPreferences(context)
                        message = "Đã đặt lại tùy chọn trên máy."
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
                                message = "Đã xóa tiến độ học lưu trên máy."
                            }.onFailure {
                                message = "Không xóa được tiến độ học. Vui lòng thử lại."
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
private fun AppearanceSection(
    themeMode: AppThemeMode,
    dynamicColorEnabled: Boolean,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit
) {
    SettingsSection(title = "Giao diện", icon = "🎨") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsChoiceButton("Hệ thống", themeMode == AppThemeMode.System, { onThemeModeChanged(AppThemeMode.System) }, Modifier.weight(1f))
            SettingsChoiceButton("Sáng", themeMode == AppThemeMode.Light, { onThemeModeChanged(AppThemeMode.Light) }, Modifier.weight(1f))
            SettingsChoiceButton("Tối", themeMode == AppThemeMode.Dark, { onThemeModeChanged(AppThemeMode.Dark) }, Modifier.weight(1f))
        }
        SettingsSwitchTile(
            icon = "🎨",
            title = "Màu theo thiết bị",
            description = "Giao diện sẽ tự đồng bộ màu theo cài đặt của điện thoại nếu được hỗ trợ.",
            checked = dynamicColorEnabled,
            onCheckedChange = onDynamicColorChanged
        )
    }
}

@Composable
private fun AssistantSection(
    assistantBubbleEnabled: Boolean,
    onAssistantBubbleChanged: (Boolean) -> Unit
) {
    SettingsSection(title = "Trợ lý", icon = "💬") {
        SettingsSwitchTile(
            icon = "💬",
            title = "Bong bóng trợ lý",
            description = "Hiện nút trợ lý nhỏ ở góc màn hình để hỗ trợ bé khi cần.",
            checked = assistantBubbleEnabled,
            onCheckedChange = onAssistantBubbleChanged
        )
    }
}

@Composable
private fun LearningMediaSection(
    learnVideoAutoplayEnabled: Boolean,
    learnVideoSoundEnabled: Boolean,
    soundEffectsEnabled: Boolean,
    learningReminderEnabled: Boolean,
    onLearnVideoAutoplayChanged: (Boolean) -> Unit,
    onLearnVideoSoundChanged: (Boolean) -> Unit,
    onSoundEffectsChanged: (Boolean) -> Unit
) {
    SettingsSection(title = "Học tập & video", icon = "🎬") {
        SettingsSwitchTile(
            icon = "▶",
            title = "Tự phát video mẫu",
            description = "Video ở trang Học sẽ tự chạy khi mở cảm xúc.",
            checked = learnVideoAutoplayEnabled,
            onCheckedChange = onLearnVideoAutoplayChanged
        )
        SettingsSwitchTile(
            icon = "🔊",
            title = "Âm thanh video",
            description = "Bật hoặc tắt tiếng cho video mẫu.",
            checked = learnVideoSoundEnabled,
            onCheckedChange = onLearnVideoSoundChanged
        )
        SettingsSwitchTile(
            icon = "♪",
            title = "Âm thanh hiệu ứng",
            description = "Bật âm thanh khi bấm nút, trả lời đúng hoặc nhận phần thưởng.",
            checked = soundEffectsEnabled,
            onCheckedChange = onSoundEffectsChanged
        )
        SettingsSwitchTile(
            icon = "🔔",
            title = "Nhắc nhở học tập",
            description = "Gửi thông báo nhắc bé luyện tập mỗi ngày.",
            checked = learningReminderEnabled,
            onCheckedChange = {},
            enabled = false,
            status = "Sắp ra mắt"
        )
    }
}

@Composable
private fun ParentAreaCard(
    onOpenAccount: () -> Unit,
    onOpenPassword: () -> Unit,
    onOpenCameraPrivacy: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    SettingsSection(
        title = "Khu vực phụ huynh",
        description = "Quản lý tài khoản, quyền riêng tư và dữ liệu của bé.",
        icon = "👤"
    ) {
        ParentAreaRow("👤", "Thông tin tài khoản", onOpenAccount)
        ParentAreaRow("🔒", "Đổi mật khẩu", onOpenPassword)
        ParentAreaRow("📷", "Quyền riêng tư camera", onOpenCameraPrivacy)
        ParentAreaRow("⚙", "Quyền ứng dụng", onOpenPermissions)
    }
}

@Composable
private fun AccountSection(
    isLoggedIn: Boolean,
    loading: Boolean,
    error: Boolean,
    profile: UserProfileDto?,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit
) {
    SettingsSection(title = "Tài khoản", icon = "👤") {
        when {
            !isLoggedIn -> {
                SettingsTile(icon = "👤", title = "Bạn chưa đăng nhập", description = "Đăng nhập để đồng bộ tiến độ học của bé.")
                SettingsButton("Đăng nhập", onClick = onLogin, modifier = Modifier.fillMaxWidth())
            }
            loading -> AccountLoadingCard()
            error -> {
                SettingsTile(
                    icon = "!",
                    title = "Không tải được thông tin tài khoản.",
                    description = "Vui lòng thử lại."
                )
                SettingsButton("Thử lại", onClick = onRetry, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                SettingsInfoRow("👤", "Tên đăng nhập", missing(profile?.username))
                SettingsInfoRow("✉", "Email", missing(profile?.email))
                SettingsInfoRow("☎", "Số điện thoại", missing(profile?.child?.phone))
                SettingsButton("Cập nhật thông tin", onClick = onEdit, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SecuritySection(onChangePassword: () -> Unit) {
    SettingsSection(title = "Bảo mật", icon = "🔒") {
        SettingsActionTile(
            icon = "🔒",
            title = "Bảo mật",
            description = "Bạn có thể đổi mật khẩu đăng nhập tại đây.",
            actionText = "Đổi mật khẩu",
            onClick = onChangePassword
        )
    }
}

@Composable
private fun CameraPrivacySection(onViewDetail: () -> Unit) {
    SettingsSection(title = "Quyền riêng tư camera", icon = "📷") {
        SettingsActionTile(
            icon = "📷",
            title = "Quyền riêng tư camera",
            description = "Camera chỉ dùng để nhận diện biểu cảm khi chơi. App không lưu ảnh hoặc video của bé.",
            actionText = "Xem chi tiết",
            onClick = onViewDetail
        )
    }
}

@Composable
private fun AppPermissionsSection(onOpenSystemSettings: () -> Unit) {
    SettingsSection(title = "Quyền ứng dụng", icon = "⚙") {
        SettingsActionTile(
            icon = "⚙",
            title = "Quyền ứng dụng",
            description = "Cấp quyền camera để chơi thử thách biểu cảm và quyền thông báo để nhận nhắc nhở học tập.",
            actionText = "Mở cài đặt",
            onClick = onOpenSystemSettings
        )
    }
}

@Composable
private fun DataSection(
    onResetPreferences: () -> Unit,
    onClearProgress: () -> Unit
) {
    SettingsSection(title = "Dữ liệu", icon = "💾") {
        SettingsActionTile(
            icon = "↺",
            title = "Đặt lại tùy chọn",
            description = "Khôi phục giao diện, trợ lý, video và âm thanh về mặc định.",
            actionText = "Đặt lại",
            onClick = onResetPreferences
        )
        SettingsActionTile(
            icon = "💾",
            title = "Đặt lại tiến độ học",
            description = "Xóa điểm, lịch sử chơi và tiến độ học của bé.",
            actionText = "Xóa tiến độ",
            onClick = onClearProgress,
            danger = true
        )
    }
}

@Composable
private fun SessionSection(
    isLoggedIn: Boolean,
    onLogout: () -> Unit,
    onLogin: () -> Unit
) {
    SettingsSection(title = "Phiên đăng nhập", icon = "🚪") {
        if (isLoggedIn) {
            SettingsButton(
                text = "Đăng xuất",
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                danger = true,
                tonal = true
            )
        } else {
            SettingsActionTile(
                icon = "👤",
                title = "Bạn chưa đăng nhập",
                description = "Đăng nhập để đồng bộ dữ liệu học tập.",
                actionText = "Đăng nhập",
                onClick = onLogin
            )
        }
    }
}

@Composable
private fun AboutSection() {
    SettingsSection(title = "Về ứng dụng", icon = "ℹ") {
        SettingsInfoRow("ℹ", "Phiên bản ứng dụng", "1.0")
        SettingsInfoRow("🔐", "Chính sách quyền riêng tư", "Sắp cập nhật")
        SettingsInfoRow("📄", "Điều khoản sử dụng", "Sắp cập nhật")
        SettingsInfoRow("☎", "Liên hệ hỗ trợ", "Sắp cập nhật")
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    EgSoftCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsIcon(icon)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    description?.let {
                        Text(it, color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun SettingsTile(
    icon: String,
    title: String,
    description: String,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EgDesign.cardSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsIcon(icon, size = 38.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = if (enabled) EgDesign.textPrimary else EgDesign.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(description, color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        trailing?.invoke()
    }
}

@Composable
private fun SettingsSwitchTile(
    icon: String,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    status: String? = null
) {
    SettingsTile(
        icon = icon,
        title = title,
        description = description,
        enabled = enabled,
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        disabledCheckedThumbColor = Color.White.copy(alpha = 0.65f),
                        disabledCheckedTrackColor = EgDesign.cardBorder,
                        disabledUncheckedThumbColor = Color.White.copy(alpha = 0.65f),
                        disabledUncheckedTrackColor = EgDesign.cardBorder
                    )
                )
                status?.let {
                    Text(it, color = EgDesign.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
private fun SettingsActionTile(
    icon: String,
    title: String,
    description: String,
    actionText: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    enabled: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EgDesign.cardSoft, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsIcon(icon, size = 38.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = EgDesign.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text(description, color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        SettingsButton(
            text = actionText,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            danger = danger,
            tonal = danger,
            enabled = enabled
        )
    }
}

@Composable
private fun ParentAreaRow(icon: String, title: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = EgDesign.cardSoft,
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsIcon(icon, size = 34.dp)
            Text(title, modifier = Modifier.weight(1f), color = EgDesign.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(">", color = EgDesign.textSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsInfoRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EgDesign.cardSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsIcon(icon, size = 36.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = EgDesign.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = EgDesign.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AccountLoadingCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EgDesign.cardSoft, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = EgDesign.primary)
        Text("Đang tải thông tin tài khoản...", color = EgDesign.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(EgDesign.pillRadius),
        color = if (selected) EgDesign.primary else EgDesign.card,
        border = BorderStroke(1.dp, if (selected) EgDesign.primaryDark else EgDesign.cardBorder),
        shadowElevation = if (selected) 2.dp else 1.dp
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
        modifier = modifier
            .height(44.dp)
            .widthIn(min = minWidth)
            .clickable(enabled = enabled, onClick = onClick),
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
private fun SettingsIcon(icon: String, size: Dp = 42.dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = EgDesign.accentSoft,
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(icon, fontSize = if (size < 38.dp) 16.sp else 20.sp, textAlign = TextAlign.Center)
        }
    }
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
        CameraPrivacyItem("Khi nào camera được bật?", "Camera chỉ bật khi bé vào trò chơi cần nhận diện khuôn mặt.")
        CameraPrivacyItem("Dữ liệu có được lưu không?", "App không lưu ảnh hoặc video của bé trên thiết bị.")
        CameraPrivacyItem("Dữ liệu có gửi lên máy chủ không?", "App chỉ lưu kết quả luyện tập như điểm số và cảm xúc đã hoàn thành.")
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
        DialogHeader("👤", "Khu vực phụ huynh", "Đây là khu vực dành cho phụ huynh. Bạn có muốn tiếp tục không?")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsButton("Hủy", onClick = onDismiss, modifier = Modifier.weight(1f), tonal = true)
            SettingsButton("Tiếp tục", onClick = onContinue, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SettingsConfirmDialog(
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
                color = EgDesign.card,
                border = BorderStroke(1.dp, EgDesign.cardBorder),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
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

private enum class ParentAction {
    Account,
    Password,
    CameraPrivacy,
    AppPermissions
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
