package com.example.appmobile.ui.pages.settings

import android.content.Intent
import android.app.TimePickerDialog
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.width
import androidx.core.content.ContextCompat
import com.example.appmobile.notifications.InAppNotificationManager
import com.example.appmobile.notifications.NotificationUtils
import com.example.appmobile.notifications.ReminderScheduler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.from
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.garden.GardenRepository
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.UserProfileDto
import com.example.appmobile.data.remote.dto.UserProfileUpdateDto
import com.example.appmobile.data.repository.UserRepository
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgSoftCard
import com.example.appmobile.ui.components.EgVectorEmojiIcon
import com.example.appmobile.ui.components.LegalDocumentDialog
import com.example.appmobile.ui.components.LegalDocumentType
import com.example.appmobile.ui.components.SupportContactDialog
import com.example.appmobile.ui.components.EgCollapsibleMainScaffold
import com.example.appmobile.ui.components.EgTab
import com.example.appmobile.ui.components.egTactileClick
import com.example.appmobile.ui.state.AppSettingsState
import com.example.appmobile.ui.state.AppThemeMode
import com.example.appmobile.ui.state.CvEmotionScoreState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsPage(
    onBack: () -> Unit,
    onGoHome: () -> Unit = {},
    onOpenLearn: () -> Unit = {},
    onOpenGames: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onLogout: () -> Unit,
    onLogin: () -> Unit = {},
    openParentArea: Boolean = false,
    openReportEmailEditor: Boolean = false,
    onReportEmailSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val gardenRepository = remember(context) { GardenRepository(context) }
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
    var accountEditTarget by remember { mutableStateOf<AccountEditTarget?>(null) }
    var showReportEmailEditor by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showCameraPrivacy by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showTermsOfUse by remember { mutableStateOf(false) }
    var showSupportContact by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    val supportEmailIntent = remember(context) {
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:tandao1908zz@gmail.com")).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Hỗ trợ ứng dụng")
        }
    }
    val supportPhoneIntent = remember(context) {
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:+84865124683"))
    }

    val assistantBubbleEnabled by AppSettingsState.assistantBubbleEnabled
    val autoPlayVideo by AppSettingsState.learnVideoAutoplayEnabled
    val autoTransitionContent by AppSettingsState.learnContentAutoTransitionEnabled
    val videoSoundEnabled by AppSettingsState.learnVideoSoundEnabled
    val soundEffectsEnabled by AppSettingsState.soundEffectsEnabled
    val learningReminderEnabled by AppSettingsState.learningReminderEnabled
    val learningReminderHour by AppSettingsState.learningReminderHour
    val learningReminderMinute by AppSettingsState.learningReminderMinute
    val dynamicColorEnabled by AppSettingsState.dynamicColorEnabled
    val themeMode by AppSettingsState.themeMode
    val acceptedPolicyVersion by AppSettingsState.acceptedPolicyVersion
    val acceptedTermsVersion by AppSettingsState.acceptedTermsVersion
    val legalAcceptedAt by AppSettingsState.legalAcceptedAt

    val notificationSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val notificationsEnabled = from(context).areNotificationsEnabled()
        if (notificationsEnabled) {
            AppSettingsState.setLearningReminderEnabled(context, true)
            ReminderScheduler.scheduleDailyReminder(context, learningReminderHour, learningReminderMinute)
            NotificationUtils.showAppNotification(context, "Nhắc nhở học tập", "Đã bật nhắc nhở học tập thành công!")
        } else {
            AppSettingsState.setLearningReminderEnabled(context, false)
            Toast.makeText(context, "Chưa cấp quyền thông báo.", Toast.LENGTH_SHORT).show()
        }
    }

    val onLearningReminderChanged: (Boolean) -> Unit = { enabled ->
        if (enabled) {
            val notificationsEnabled = from(context).areNotificationsEnabled()
            if (notificationsEnabled) {
                AppSettingsState.setLearningReminderEnabled(context, true)
                ReminderScheduler.scheduleDailyReminder(context, learningReminderHour, learningReminderMinute)
                NotificationUtils.showAppNotification(context, "Nhắc nhở học tập", "Đã bật nhắc nhở học tập thành công!")
            } else {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                runCatching {
                    notificationSettingsLauncher.launch(intent)
                }.onFailure {
                    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    runCatching { notificationSettingsLauncher.launch(fallbackIntent) }.onFailure {
                        InAppNotificationManager.emit("Thông báo", "Không thể mở cài đặt. Hãy tự bật trong Cài đặt máy.")
                    }
                }
            }
        } else {
            // Tắt nhắc học: huỷ lịch hẹn và điều hướng sang Settings để tắt toàn bộ thông báo
            AppSettingsState.setLearningReminderEnabled(context, false)
            ReminderScheduler.cancelDailyReminder(context)
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            runCatching {
                notificationSettingsLauncher.launch(intent)
            }.onFailure {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                runCatching { notificationSettingsLauncher.launch(fallbackIntent) }.onFailure {
                    InAppNotificationManager.emit("Thông báo", "Không thể mở cài đặt. Hãy tự tắt trong Cài đặt máy.")
                }
            }
        }
    }

    val onOpenReminderTimePicker: () -> Unit = {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                AppSettingsState.setLearningReminderTime(context, hourOfDay, minute)
                if (learningReminderEnabled) {
                    ReminderScheduler.scheduleDailyReminder(context, hourOfDay, minute)
                    NotificationUtils.showAppNotification(
                        context,
                        "Nhắc nhở học tập",
                        "Đã đổi giờ nhắc học sang ${formatReminderTime(hourOfDay, minute)}. ⏰"
                    )
                }
            },
            learningReminderHour,
            learningReminderMinute,
            true
        ).show()
    }

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

    LaunchedEffect(openParentArea, openReportEmailEditor, userId) {
        if (openParentArea) showParentArea = true
        if (openReportEmailEditor) {
            if (isLoggedIn) {
                showReportEmailEditor = true
            } else {
                statusMessage = "Vui lòng đăng nhập để thêm email phụ huynh."
            }
        }
    }

    EgCollapsibleMainScaffold(
        activeTab = EgTab.Settings,
        onHome = onGoHome,
        onLearn = onOpenLearn,
        onGames = onOpenGames,
        onProfile = onOpenProfile,
        onSettings = null,
        onBack = onBack,
        topBar = { SettingsHeader() }
    ) {
        SettingsScreen(
            themeMode = themeMode,
            dynamicColorEnabled = dynamicColorEnabled,
            assistantBubbleEnabled = assistantBubbleEnabled,
            autoPlayVideo = autoPlayVideo,
            autoTransitionContent = autoTransitionContent,
            videoSoundEnabled = videoSoundEnabled,
            soundEffectsEnabled = soundEffectsEnabled,
            learningReminderEnabled = learningReminderEnabled,
            isLoggedIn = isLoggedIn,
            statusMessage = statusMessage,
            acceptedPolicyVersion = acceptedPolicyVersion,
            acceptedTermsVersion = acceptedTermsVersion,
            acceptedAtText = formatConsentDate(legalAcceptedAt),
            onThemeModeChanged = { AppSettingsState.setThemeMode(context, it) },
            onDynamicColorChanged = { AppSettingsState.setDynamicColorEnabled(context, it) },
            onAssistantBubbleChanged = { AppSettingsState.setAssistantBubbleEnabled(context, it) },
            onAutoPlayVideoChanged = { AppSettingsState.setLearnVideoAutoplayEnabled(context, it) },
            onAutoTransitionContentChanged = { AppSettingsState.setLearnContentAutoTransitionEnabled(context, it) },
            onVideoSoundChanged = { AppSettingsState.setLearnVideoSoundEnabled(context, it) },
            onSoundEffectsChanged = { AppSettingsState.setSoundEffectsEnabled(context, it) },
            onLearningReminderChanged = onLearningReminderChanged,
            reminderTimeText = formatReminderTime(learningReminderHour, learningReminderMinute),
            onOpenReminderTimePicker = onOpenReminderTimePicker,
            onOpenParentArea = { showParentGate = true },
            onLogin = onLogin,
            onOpenPrivacyPolicy = { showPrivacyPolicy = true },
            onOpenTerms = { showTermsOfUse = true },
            onOpenSupportContact = { showSupportContact = true },
            onLogout = { confirmAction = ConfirmAction.Logout }
        )
    }

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
            onEditAccount = { target ->
                if (isLoggedIn) accountEditTarget = target else statusMessage = "Vui lòng đăng nhập để cập nhật tài khoản."
            },
            onEditReportEmail = {
                if (isLoggedIn) showReportEmailEditor = true else statusMessage = "Vui lòng đăng nhập để thêm email phụ huynh."
            },
            onChangePassword = {
                if (isLoggedIn) showChangePassword = true else statusMessage = "Vui lòng đăng nhập để đổi mật khẩu."
            },
            onCameraPrivacy = { showCameraPrivacy = true },
            onResetPreferences = { confirmAction = ConfirmAction.ResetPreferences },
            onClearProgress = { confirmAction = ConfirmAction.ClearProgress }
        )
    }

    if (showReportEmailEditor) {
        ReportEmailDialog(
            profile = profile,
            saving = saving,
            onDismiss = { if (!saving) showReportEmailEditor = false },
            onSave = { email ->
                val targetUserId = userId ?: return@ReportEmailDialog
                scope.launch {
                    saving = true
                    val updated = repository.updateProfile(targetUserId, UserProfileUpdateDto(reportPreferences = email))
                    saving = false
                    if (updated != null) {
                        profile = updated
                        accountError = false
                        statusMessage = "Đã lưu email phụ huynh."
                        showReportEmailEditor = false
                        onReportEmailSaved()
                    } else {
                        statusMessage = "Không lưu được email phụ huynh. Vui lòng thử lại."
                    }
                }
            }
        )
    }

    accountEditTarget?.let { target ->
        EditAccountDialog(
            target = target,
            profile = profile,
            saving = saving,
            onDismiss = { if (!saving) accountEditTarget = null },
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
                        accountEditTarget = null
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

    if (showPrivacyPolicy) {
        LegalDocumentDialog(
            type = LegalDocumentType.PrivacyPolicy,
            onDismiss = { showPrivacyPolicy = false }
        )
    }

    if (showTermsOfUse) {
        LegalDocumentDialog(
            type = LegalDocumentType.TermsOfUse,
            onDismiss = { showTermsOfUse = false }
        )
    }

    if (showSupportContact) {
        SupportContactDialog(
            onDismiss = { showSupportContact = false },
            onEmailSupport = {
                runCatching { context.startActivity(supportEmailIntent) }
                    .onFailure { statusMessage = "Không mở được ứng dụng email trên máy." }
            },
            onCallSupport = {
                runCatching { context.startActivity(supportPhoneIntent) }
                    .onFailure { statusMessage = "Không mở được trình gọi điện trên máy." }
            }
        )
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
                                gardenRepository.resetGarden()
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
    autoTransitionContent: Boolean,
    videoSoundEnabled: Boolean,
    soundEffectsEnabled: Boolean,
    learningReminderEnabled: Boolean,
    isLoggedIn: Boolean,
    statusMessage: String?,
    acceptedPolicyVersion: String?,
    acceptedTermsVersion: String?,
    acceptedAtText: String,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAssistantBubbleChanged: (Boolean) -> Unit,
    onAutoPlayVideoChanged: (Boolean) -> Unit,
    onAutoTransitionContentChanged: (Boolean) -> Unit,
    onVideoSoundChanged: (Boolean) -> Unit,
    onSoundEffectsChanged: (Boolean) -> Unit,
    onLearningReminderChanged: (Boolean) -> Unit,
    reminderTimeText: String,
    onOpenReminderTimePicker: () -> Unit,
    onOpenParentArea: () -> Unit,
    onLogin: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenSupportContact: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
            autoTransitionContent = autoTransitionContent,
            videoSoundEnabled = videoSoundEnabled,
            soundEffectsEnabled = soundEffectsEnabled,
            learningReminderEnabled = learningReminderEnabled,
            onAssistantBubbleChanged = onAssistantBubbleChanged,
            onAutoPlayVideoChanged = onAutoPlayVideoChanged,
            onAutoTransitionContentChanged = onAutoTransitionContentChanged,
            onVideoSoundChanged = onVideoSoundChanged,
            onSoundEffectsChanged = onSoundEffectsChanged,
            onLearningReminderChanged = onLearningReminderChanged,
            reminderTimeText = reminderTimeText,
            onOpenReminderTimePicker = onOpenReminderTimePicker
        )
        ParentAreaEntryCard(onClick = onOpenParentArea)
        AboutAppSection(
            acceptedPolicyVersion = acceptedPolicyVersion,
            acceptedTermsVersion = acceptedTermsVersion,
            acceptedAtText = acceptedAtText,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            onOpenTerms = onOpenTerms,
            onOpenSupportContact = onOpenSupportContact
        )
        SessionSection(isLoggedIn = isLoggedIn, onLogin = onLogin, onLogout = onLogout)
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    SettingsSection(title = "Giao diện", icon = "palette") {
        SegmentedThemeSelector(themeMode = themeMode, onThemeModeChanged = onThemeModeChanged)
        SwitchSettingsRow(
            icon = "palette",
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
    autoTransitionContent: Boolean,
    videoSoundEnabled: Boolean,
    soundEffectsEnabled: Boolean,
    learningReminderEnabled: Boolean,
    onAssistantBubbleChanged: (Boolean) -> Unit,
    onAutoPlayVideoChanged: (Boolean) -> Unit,
    onAutoTransitionContentChanged: (Boolean) -> Unit,
    onVideoSoundChanged: (Boolean) -> Unit,
    onSoundEffectsChanged: (Boolean) -> Unit,
    onLearningReminderChanged: (Boolean) -> Unit,
    reminderTimeText: String,
    onOpenReminderTimePicker: () -> Unit
) {
    SettingsSection(title = "Trải nghiệm học", icon = "sparkle") {
        SwitchSettingsRow(
            icon = "chat",
            title = "Bong bóng Mầm Mầm",
            description = "Hiện Mầm Mầm ở góc màn hình.",
            checked = assistantBubbleEnabled,
            onCheckedChange = onAssistantBubbleChanged
        )
        ThinDivider()
        SwitchSettingsRow(
            icon = "play",
            title = "Tự phát video mẫu",
            description = "Video ở trang Học sẽ tự chạy khi mở cảm xúc.",
            checked = autoPlayVideo,
            onCheckedChange = onAutoPlayVideoChanged
        )
        ThinDivider()
        SwitchSettingsRow(
            icon = "refresh",
            title = "Tự chuyển nội dung học",
            description = "Tự động chuyển đổi giữa ảnh tĩnh và video trong thẻ học.",
            checked = autoTransitionContent,
            onCheckedChange = onAutoTransitionContentChanged
        )
        ThinDivider()
        SwitchSettingsRow(
            icon = "speaker",
            title = "Âm thanh video",
            description = "Bật hoặc tắt tiếng cho video mẫu.",
            checked = videoSoundEnabled,
            onCheckedChange = onVideoSoundChanged
        )
        ThinDivider()
        SwitchSettingsRow(
            icon = "speaker",
            title = "Âm thanh hiệu ứng",
            description = "Bật âm thanh khi bấm nút, trả lời đúng hoặc nhận phần thưởng.",
            checked = soundEffectsEnabled,
            onCheckedChange = onSoundEffectsChanged
        )
        ThinDivider()
        SwitchSettingsRow(
            icon = "bell",
            title = "Nhắc nhở học tập",
            description = "Nhắc bé luyện tập mỗi ngày.",
            checked = learningReminderEnabled,
            onCheckedChange = onLearningReminderChanged,
            enabled = true,
            badge = null
        )
        ThinDivider()
        ActionSettingsRow(
            icon = "clock",
            title = "Giờ nhắc học",
            description = "Đang nhắc lúc $reminderTimeText mỗi ngày.",
            actionText = "Đổi giờ",
            onClick = onOpenReminderTimePicker,
            enabled = learningReminderEnabled
        )
    }
}

private fun formatReminderTime(hour: Int, minute: Int): String {
    return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
}

private fun formatConsentDate(timestamp: Long?): String {
    return timestamp
        ?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it)) }
        ?: "Chưa đồng ý"
}

@Composable
private fun ParentAreaEntryCard(onClick: () -> Unit) {
    EgSoftCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .egTactileClick(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsIcon("user")
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Khu vực phụ huynh", color = EgDesign.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Quản lý tài khoản, bảo mật, quyền riêng tư và dữ liệu học tập của bé.",
                    color = EgDesign.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            EgVectorEmojiIcon("next", size = 22.dp, tint = EgDesign.textSecondary)
        }
    }
}

@Composable
private fun AboutAppSection(
    acceptedPolicyVersion: String?,
    acceptedTermsVersion: String?,
    acceptedAtText: String,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenSupportContact: () -> Unit
) {
    SettingsSection(title = "Về ứng dụng", icon = "info") {
        CompactValueRow("info", "Phiên bản ứng dụng", "2.1.9")
        ThinDivider()
        CompactValueRow(
            "document",
            "Phiên bản chính sách",
            "${acceptedPolicyVersion ?: AppSettingsState.CURRENT_PRIVACY_POLICY_VERSION} / ${acceptedTermsVersion ?: AppSettingsState.CURRENT_TERMS_VERSION}"
        )
        ThinDivider()
        CompactValueRow("clock", "Ngày đã đồng ý", acceptedAtText)
        ThinDivider()
        ActionSettingsRow(
            icon = "lock",
            title = "Chính sách quyền riêng tư",
            description = "Xem app dùng dữ liệu nào và bảo vệ dữ liệu ra sao.",
            actionText = "Xem",
            onClick = onOpenPrivacyPolicy
        )
        ThinDivider()
        ActionSettingsRow(
            icon = "document",
            title = "Điều khoản sử dụng",
            description = "Xem các điều kiện khi dùng app cho bé học và chơi.",
            actionText = "Xem",
            onClick = onOpenTerms
        )
        ThinDivider()
        ActionSettingsRow(
            icon = "phone",
            title = "Liên hệ hỗ trợ",
            description = "Gửi email hoặc gọi nếu cần trợ giúp.",
            actionText = "Liên hệ",
            onClick = onOpenSupportContact
        )
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
    onEditAccount: (AccountEditTarget) -> Unit,
    onEditReportEmail: () -> Unit,
    onChangePassword: () -> Unit,
    onCameraPrivacy: () -> Unit,
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                        BottomSheetHeader(
                            title = "Khu vực phụ huynh",
                            subtitle = "Quản lý tài khoản, quyền riêng tư và dữ liệu của bé.",
                            onDismiss = onDismiss
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                    ParentAccountSection(
                        isLoggedIn = isLoggedIn,
                        loading = loading,
                        error = error,
                        profile = profile,
                        onLogin = onLogin,
                        onRetry = onRetry,
                        onEdit = onEditAccount,
                        onEditReportEmail = onEditReportEmail
                    )
                    ParentCompactSection(title = "Bảo mật", icon = "lock") {
                        ActionSettingsRow(
                            icon = "lock",
                            title = "Đổi mật khẩu",
                            description = "Bạn có thể đổi mật khẩu đăng nhập tại đây.",
                            actionText = "Đổi",
                            onClick = onChangePassword
                        )
                    }
                    ParentCompactSection(title = "Quyền riêng tư", icon = "camera") {
                        ActionSettingsRow(
                            icon = "camera",
                            title = "Quyền riêng tư camera",
                            description = "Xem cách app dùng camera và mở cài đặt quyền khi cần.",
                            actionText = "Xem",
                            onClick = onCameraPrivacy
                        )
                    }
                    ParentCompactSection(title = "Dữ liệu học tập", icon = "save") {
                        ActionSettingsRow(
                            icon = "refresh",
                            title = "Đặt lại tùy chọn",
                            description = "Khôi phục giao diện, trợ lý, video và âm thanh về mặc định.",
                            actionText = "Đặt lại",
                            onClick = onResetPreferences
                        )
                        ThinDivider()
                        ActionSettingsRow(
                            icon = "trash",
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
}

@Composable
private fun ParentAccountSection(
    isLoggedIn: Boolean,
    loading: Boolean,
    error: Boolean,
    profile: UserProfileDto?,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    onEdit: (AccountEditTarget) -> Unit,
    onEditReportEmail: () -> Unit
) {
    val reportEmail = profile.effectiveReportEmail()
    val accountEmail = profile.accountEmail()
    val username = profile?.username?.trim()?.takeIf { it.isNotBlank() }
    val parentPhone = profile?.child?.phone?.trim()?.takeIf { it.isNotBlank() }
    ParentCompactSection(title = "Tài Khoản", icon = "user") {
        when {
            !isLoggedIn -> {
                ActionSettingsRow(
                    icon = "user",
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
                AccountInfoRow(
                    icon = "user",
                    label = "Tên đăng nhập",
                    value = username ?: "Chưa có",
                    actionText = if (username == null) "Thêm tên" else "Sửa",
                    onClick = { onEdit(AccountEditTarget.Username) }
                )
                ThinDivider()
                AccountInfoRow(
                    icon = "mail",
                    label = "Email",
                    value = accountEmail ?: "Chưa có",
                    description = "Email tài khoản để nhận mã và thông báo.",
                    actionText = if (accountEmail == null) "Thêm email" else "Sửa",
                    onClick = { onEdit(AccountEditTarget.Email) }
                )
                ThinDivider()
                AccountInfoRow(
                    icon = "phone",
                    label = "Số điện thoại phụ huynh",
                    value = parentPhone ?: "Chưa có",
                    actionText = if (parentPhone == null) "Thêm SĐT" else "Sửa",
                    onClick = { onEdit(AccountEditTarget.Phone) }
                )
                ThinDivider()
                AccountInfoRow(
                    icon = "mail",
                    label = "Email phụ huynh",
                    value = reportEmail ?: "Chưa có",
                    description = "Email này sẽ nhận báo cáo tiến bộ của bé.",
                    actionText = if (reportEmail == null) "Thêm email" else "Sửa",
                    onClick = onEditReportEmail
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
            Text(description, color = EgDesign.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EgDesign.primary,
                checkedBorderColor = EgDesign.primary,
                uncheckedThumbColor = EgDesign.textSecondary,
                uncheckedTrackColor = EgDesign.cardSoft,
                uncheckedBorderColor = EgDesign.textSecondary,
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
    fullWidthAction: Boolean = false,
    enabled: Boolean = true
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val narrow = screenWidth < 360.dp || fullWidthAction
    if (narrow) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).alpha(if (enabled) 1f else 0.4f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactTextBlock(icon = icon, title = title, description = description)
            SettingsButton(
                text = actionText,
                onClick = onClick,
                modifier = if (fullWidthAction) Modifier.fillMaxWidth() else Modifier.align(Alignment.End),
                danger = danger,
                tonal = danger,
                minWidth = 120.dp,
                enabled = enabled
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).alpha(if (enabled) 1f else 0.4f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CompactTextBlock(icon = icon, title = title, description = description)
            }
            Spacer(modifier = Modifier.width(16.dp))
            SettingsButton(
                text = actionText,
                onClick = onClick,
                danger = danger,
                tonal = danger,
                minWidth = 120.dp,
                enabled = enabled
            )
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
        Text(label, modifier = Modifier.weight(1f), color = EgDesign.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp)
        Text(value, color = EgDesign.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, lineHeight = 17.sp)
    }
}

@Composable
private fun AccountInfoRow(
    icon: String,
    label: String,
    value: String,
    actionText: String,
    onClick: () -> Unit,
    description: String? = null
) {
    val narrow = LocalConfiguration.current.screenWidthDp.dp < 360.dp
    if (narrow) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AccountInfoText(icon = icon, label = label, value = value, description = description)
            SettingsButton(
                text = actionText,
                onClick = onClick,
                modifier = Modifier.align(Alignment.End),
                minWidth = 104.dp
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AccountInfoText(
                icon = icon,
                label = label,
                value = value,
                description = description,
                modifier = Modifier.weight(1f)
            )
            SettingsButton(text = actionText, onClick = onClick, minWidth = 104.dp)
        }
    }
}

@Composable
private fun AccountInfoText(
    icon: String,
    label: String,
    value: String,
    description: String?,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsIcon(icon, size = 34.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = EgDesign.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 17.sp)
            Text(value, color = EgDesign.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
            description?.let {
                Text(it, color = EgDesign.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
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
            Text(title, color = EgDesign.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 18.sp)
            Text(description, color = EgDesign.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
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
        SettingsIcon("user")
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = EgDesign.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Surface(
            modifier = Modifier.size(40.dp).egTactileClick(onClick = onDismiss),
            shape = CircleShape,
            color = EgDesign.cardSoft,
            border = BorderStroke(1.dp, EgDesign.cardBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                EgVectorEmojiIcon("close", size = 20.dp, tint = EgDesign.textPrimary)
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
        modifier = modifier.height(42.dp).egTactileClick(onClick = onClick),
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
    val isDark = AppSettingsState.activeDarkTheme.value
    val background = when {
        danger && tonal -> if (isDark) Color(0xFF51222B) else Color(0xFFFFF1F2)
        danger -> Color(0xFFEF4444)
        tonal -> EgDesign.cardSoft
        else -> EgDesign.primary
    }
    val textColor = when {
        danger && tonal -> if (isDark) Color(0xFFFDA4AF) else Color(0xFFB91C1C)
        danger -> Color.White
        tonal -> EgDesign.primaryDark
        else -> Color.White
    }
    Surface(
        modifier = modifier.height(44.dp).widthIn(min = minWidth).egTactileClick(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(EgDesign.pillRadius),
        color = if (enabled) background else EgDesign.cardBorder,
        border = BorderStroke(1.dp, if (danger && tonal && !isDark) Color(0xFFFECACA) else EgDesign.cardBorder),
        shadowElevation = if (enabled) 1.dp else 0.dp
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (enabled) textColor else EgDesign.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SettingsIcon(icon: String, size: Dp = 40.dp) {
    val iconSize = (size.value - if (size < 36.dp) 3f else 4f).dp
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        EgVectorEmojiIcon(icon, size = iconSize)
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
    val isDark = AppSettingsState.activeDarkTheme.value
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color(0xFF153E2A) else Color(0xFFEFFAF3),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF2E7D32) else Color(0xFFC7E9D0))
    ) {
        Text(message, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = if (isDark) Color(0xFF86EFAC) else Color(0xFF166534), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val isDark = AppSettingsState.activeDarkTheme.value
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color(0xFF51222B) else Color(0xFFFFF1F2),
        border = BorderStroke(1.dp, if (isDark) Color(0xFFBE123C) else Color(0xFFFDA4AF))
    ) {
        Text(message, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = if (isDark) Color(0xFFFDA4AF) else Color(0xFF9F1239), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReportEmailDialog(
    profile: UserProfileDto?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var email by rememberSaveable(profile?.userId, profile?.child?.reportPref) {
        mutableStateOf(profile.effectiveReportEmail().orEmpty())
    }
    var error by rememberSaveable(profile?.userId) { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        val trimmed = email.trim()
        error = when {
            trimmed.isBlank() -> "Vui lòng nhập email phụ huynh."
            !trimmed.isUsableEmail() -> "Email phụ huynh chưa hợp lệ. Vui lòng kiểm tra lại."
            else -> null
        }
        return error == null
    }

    SettingsDialog(onDismiss = onDismiss) {
        DialogHeader("mail", "Email phụ huynh", "Bố mẹ sẽ nhận báo cáo tiến bộ hằng tuần qua email này.")
        error?.let { ErrorBanner(it) }
        SettingsTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email phụ huynh",
            placeholder = "vidu@email.com",
            keyboardType = KeyboardType.Email
        )
        DialogActions(
            primaryText = if (saving) "Đang lưu..." else "Lưu email",
            primaryDanger = false,
            saving = saving,
            onCancel = onDismiss,
            onSave = {
                if (!saving && validate()) onSave(email.trim())
            }
        )
    }
}

@Composable
private fun EditAccountDialog(
    target: AccountEditTarget,
    profile: UserProfileDto?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (UserProfileUpdateDto) -> Unit
) {
    val content = accountEditContent(target)
    val initialValue = when (target) {
        AccountEditTarget.Username -> profile?.username.orEmpty()
        AccountEditTarget.Email -> profile.accountEmail().orEmpty()
        AccountEditTarget.Phone -> profile?.child?.phone.orEmpty()
    }
    var value by rememberSaveable(profile?.userId, target.name) { mutableStateOf(initialValue) }
    var error by rememberSaveable(profile?.userId) { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        val trimmed = value.trim()
        error = when {
            target == AccountEditTarget.Username && trimmed.isBlank() -> "Tên đăng nhập không được để trống."
            target == AccountEditTarget.Email && trimmed.isBlank() -> "Vui lòng nhập email."
            target == AccountEditTarget.Email && !trimmed.isUsableEmail() -> "Email chưa hợp lệ. Vui lòng kiểm tra lại."
            target == AccountEditTarget.Phone && trimmed.isBlank() -> "Vui lòng nhập số điện thoại phụ huynh."
            target == AccountEditTarget.Phone && !trimmed.matches(Regex("^\\d{9,11}$")) -> "Số điện thoại nên gồm 9-11 chữ số."
            else -> null
        }
        return error == null
    }

    SettingsDialog(onDismiss = onDismiss) {
        DialogHeader(content.icon, content.title, content.subtitle)
        error?.let { ErrorBanner(it) }
        SettingsTextField(
            value = value,
            onValueChange = { input ->
                if (target == AccountEditTarget.Phone) {
                    if (input.all(Char::isDigit) && input.length <= 11) value = input
                } else {
                    value = input
                }
            },
            label = content.fieldLabel,
            placeholder = content.placeholder,
            keyboardType = content.keyboardType
        )
        DialogActions(
            primaryText = if (saving) "Đang lưu..." else "Lưu thay đổi",
            primaryDanger = false,
            saving = saving,
            onCancel = onDismiss,
            onSave = {
                if (!saving && validate()) {
                    val trimmed = value.trim()
                    onSave(
                        when (target) {
                            AccountEditTarget.Username -> UserProfileUpdateDto(username = trimmed)
                            AccountEditTarget.Email -> UserProfileUpdateDto(email = trimmed)
                            AccountEditTarget.Phone -> UserProfileUpdateDto(phoneNumber = trimmed)
                        }
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
    var currentPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var newPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
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
        DialogHeader("lock", "Đổi mật khẩu", "Để bảo mật, app sẽ không hiển thị mật khẩu hiện tại.")
        error?.let { ErrorBanner(it) }
        SettingsTextField(currentPassword, { currentPassword = it }, "Mật khẩu hiện tại", "Nhập mật khẩu hiện tại", keyboardType = KeyboardType.Password, isPassword = true)
        SettingsTextField(newPassword, { newPassword = it }, "Mật khẩu mới", "Nhập mật khẩu mới", keyboardType = KeyboardType.Password, isPassword = true)
        SettingsTextField(confirmPassword, { confirmPassword = it }, "Nhập lại mật khẩu mới", "Nhập lại mật khẩu mới", keyboardType = KeyboardType.Password, isPassword = true)
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
        DialogHeader("camera", "Quyền riêng tư camera", "Camera chỉ dùng trong các trò chơi biểu cảm.")
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
        DialogHeader("user", "Khu vực phụ huynh", "Phần này dành cho phụ huynh để quản lý tài khoản và dữ liệu của bé.")
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
                shape = RoundedCornerShape(EgDesign.radiusXLarge),
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
    isPassword: Boolean = false
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = EgDesign.primaryDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        placeholder = { Text(placeholder, color = EgDesign.textSecondary, fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword && !passwordVisible) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        trailingIcon = if (isPassword) {
            {
                androidx.compose.material3.IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    com.example.appmobile.ui.components.EgVectorEmojiIcon(
                        value = if (passwordVisible) "eye_off" else "eye",
                        size = 20.dp,
                        tint = EgDesign.textSecondary
                    )
                }
            }
        } else null,
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

private enum class AccountEditTarget {
    Username,
    Email,
    Phone
}

private data class AccountEditContent(
    val icon: String,
    val title: String,
    val subtitle: String,
    val fieldLabel: String,
    val placeholder: String,
    val keyboardType: KeyboardType
)

private fun accountEditContent(target: AccountEditTarget): AccountEditContent {
    return when (target) {
        AccountEditTarget.Username -> AccountEditContent(
            icon = "user",
            title = "Tên đăng nhập",
            subtitle = "Tên dùng để đăng nhập vào tài khoản cùng mật khẩu.",
            fieldLabel = "Tên đăng nhập",
            placeholder = "Nhập tên đăng nhập",
            keyboardType = KeyboardType.Text
        )
        AccountEditTarget.Email -> AccountEditContent(
            icon = "mail",
            title = "Email",
            subtitle = "Email tài khoản để nhận mã và thông báo.",
            fieldLabel = "Email",
            placeholder = "email@example.com",
            keyboardType = KeyboardType.Email
        )
        AccountEditTarget.Phone -> AccountEditContent(
            icon = "phone",
            title = "Số điện thoại phụ huynh",
            subtitle = "Số điện thoại phụ huynh dùng khi cần liên hệ.",
            fieldLabel = "Số điện thoại phụ huynh",
            placeholder = "Nhập số điện thoại phụ huynh",
            keyboardType = KeyboardType.Phone
        )
    }
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
            icon = "refresh",
            title = "Đặt lại tùy chọn?",
            message = "Các cài đặt giao diện, trợ lý, video và âm thanh sẽ được đưa về mặc định.",
            confirmText = "Đặt lại",
            danger = false
        )
        ConfirmAction.ClearProgress -> ConfirmContent(
            icon = "save",
            title = "Xóa tiến độ học?",
            message = "Điểm số, lịch sử chơi và tiến độ học của bé sẽ bị xóa. Hành động này không thể hoàn tác.",
            confirmText = "Xóa",
            danger = true
        )
        ConfirmAction.Logout -> ConfirmContent(
            icon = "exit",
            title = "Đăng xuất?",
            message = "Bạn sẽ cần đăng nhập lại để đồng bộ dữ liệu học tập.",
            confirmText = "Đăng xuất",
            danger = true
        )
    }
}

private fun missing(value: String?): String = value?.trim()?.takeIf { it.isNotBlank() } ?: "Chưa cập nhật"

private fun UserProfileDto?.effectiveReportEmail(): String? {
    if (this == null) return null
    return extractReportEmail(child?.reportPref)
}

private fun UserProfileDto?.accountEmail(): String? {
    if (this == null) return null
    return email?.takeIf { it.isUsableEmail() }
}

private fun extractReportEmail(rawValue: String?): String? {
    val raw = rawValue?.trim().orEmpty()
    if (raw.isBlank()) return null
    if (raw.isUsableEmail()) return raw
    return runCatching {
        val json = JSONObject(raw)
        listOf("parent_email", "email", "receiver_email")
            .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isUsableEmail() } }
    }.getOrNull()
}

private fun String?.isUsableEmail(): Boolean {
    val value = this?.trim().orEmpty()
    if (value.isBlank() || value.endsWith("@local.invalid", ignoreCase = true)) return false
    return Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value)
}
