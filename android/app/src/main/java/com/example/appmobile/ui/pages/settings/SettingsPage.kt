package com.example.appmobile.ui.pages.settings

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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.appmobile.ui.components.EgGradientPill
import com.example.appmobile.ui.components.EgSoftCard
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun SettingsPage(
    assistantBubbleEnabled: Boolean,
    onAssistantBubbleChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: AppSession.currentBackendUserId() ?: "local-player" }
    val repository = remember {
        UserRepository(NetworkClient.apiService, FirebaseAuthHelper(), AppDatabase.getDatabase(context).userDao())
    }

    var profile by remember { mutableStateOf<UserProfileDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showEditAccount by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }

    suspend fun loadProfile() {
        loading = true
        profile = repository.getProfile(userId)
        loading = false
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppBackButton(onClick = onBack)
            Spacer(modifier = Modifier.weight(1f))
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cài đặt", color = EgDesign.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Tuỳ chỉnh trải nghiệm học và chơi cho bé.",
                color = EgDesign.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }

        AssistantBubbleCard(
            assistantBubbleEnabled = assistantBubbleEnabled,
            onAssistantBubbleChanged = onAssistantBubbleChanged
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EgDesign.primary)
            }
        } else {
            message?.let { SettingsStatusBanner(it) }
            AccountSecuritySection(
                profile = profile,
                onEditAccount = { showEditAccount = true },
                onChangePassword = { showChangePassword = true }
            )
        }

        EgSoftCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Phiên đăng nhập", color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                LogoutButton(onLogout = onLogout)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showEditAccount) {
        EditAccountDialog(
            profile = profile,
            saving = saving,
            onDismiss = { if (!saving) showEditAccount = false },
            onSave = { update ->
                scope.launch {
                    saving = true
                    val updated = repository.updateProfile(userId, update)
                    saving = false
                    if (updated != null) {
                        profile = updated
                        message = "Cập nhật tài khoản thành công."
                        showEditAccount = false
                    } else {
                        message = "Cập nhật tài khoản chưa thành công."
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
                scope.launch {
                    saving = true
                    val updated = repository.updateProfile(userId, UserProfileUpdateDto(password = newPassword))
                    saving = false
                    if (updated != null) {
                        message = "Đổi mật khẩu thành công."
                        showChangePassword = false
                    } else {
                        message = "Đổi mật khẩu chưa thành công."
                    }
                }
            }
        )
    }
}

@Composable
private fun AssistantBubbleCard(
    assistantBubbleEnabled: Boolean,
    onAssistantBubbleChanged: (Boolean) -> Unit
) {
    EgSoftCard {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RoundIcon("💬")
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Bong bóng trợ lý", color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(
                    "Bật để hiện nút chat nổi ở góc màn hình.",
                    color = EgDesign.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            Switch(
                checked = assistantBubbleEnabled,
                onCheckedChange = onAssistantBubbleChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = EgDesign.primary,
                    checkedBorderColor = EgDesign.primary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE5EEF7),
                    uncheckedBorderColor = EgDesign.cardBorder
                )
            )
        }
    }
}

@Composable
private fun AccountSecuritySection(
    profile: UserProfileDto?,
    onEditAccount: () -> Unit,
    onChangePassword: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EgSoftCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tài khoản & bảo mật", color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                SettingsInfoRow("👤", "Tên đăng nhập", fallback(profile?.username))
                SettingsInfoRow("✉️", "Email", fallback(profile?.email))
                SettingsInfoRow("📞", "Số điện thoại", fallback(profile?.child?.phone))
                EgGradientPill(
                    text = "Chỉnh sửa tài khoản",
                    onClick = onEditAccount,
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp,
                    fontSize = 14
                )
            }
        }

        EgSoftCard {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoundIcon("🔒")
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Bảo mật", color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text("Đổi mật khẩu đăng nhập. Không hiển thị mật khẩu hiện tại.", color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                }
                SmallOutlineButton("Đổi mật khẩu", onClick = onChangePassword)
            }
        }
    }
}

@Composable
private fun SettingsInfoRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FCFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(icon, fontSize = 18.sp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = EgDesign.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = EgDesign.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
            username.trim().isBlank() -> "Tên đăng nhập không được rỗng."
            !email.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) -> "Email không hợp lệ."
            phone.trim().isNotEmpty() && !phone.trim().matches(Regex("^\\d{9,11}$")) -> "Số điện thoại phải gồm 9-11 chữ số."
            else -> null
        }
        return error == null
    }

    SettingsDialog(onDismiss = onDismiss) {
        DialogHeader("👤", "Chỉnh sửa tài khoản", "Cập nhật tên đăng nhập, email và số điện thoại.")
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
            saving = saving,
            primaryText = if (saving) "Đang lưu..." else "💾 Lưu thay đổi",
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
            newPassword.isBlank() -> "Mật khẩu mới không được rỗng."
            newPassword.length < 8 -> "Mật khẩu mới phải có ít nhất 8 ký tự."
            newPassword != confirmPassword -> "Nhập lại mật khẩu mới không trùng."
            else -> null
        }
        return error == null
    }

    SettingsDialog(onDismiss = onDismiss) {
        DialogHeader("🔒", "Đổi mật khẩu", "Mật khẩu hiện tại không bao giờ được hiển thị từ database.")
        error?.let { ErrorBanner(it) }
        SettingsTextField(currentPassword, { currentPassword = it }, "Mật khẩu hiện tại", "Nhập nếu backend yêu cầu", keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation())
        SettingsTextField(newPassword, { newPassword = it }, "Mật khẩu mới", "Nhập mật khẩu mới", keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation())
        SettingsTextField(confirmPassword, { confirmPassword = it }, "Nhập lại mật khẩu mới", "Nhập lại mật khẩu mới", keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation())
        DialogActions(
            saving = saving,
            primaryText = if (saving) "Đang lưu..." else "💾 Lưu thay đổi",
            onCancel = onDismiss,
            onSave = {
                if (!saving && validate()) {
                    // Backend hiện chỉ nhận password mới trong update; currentPassword được giữ cho UI nếu sau này backend yêu cầu.
                    onSave(newPassword)
                }
            }
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
                color = Color.White,
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
        RoundIcon(icon)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = EgDesign.textPrimary, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
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
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedTextColor = EgDesign.textPrimary,
            unfocusedTextColor = EgDesign.textPrimary,
            cursorColor = EgDesign.primaryDark
        )
    )
}

@Composable
private fun DialogActions(
    saving: Boolean,
    primaryText: String,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SmallOutlineButton("❌ Hủy", enabled = !saving, onClick = onCancel, modifier = Modifier.weight(1f))
        EgGradientPill(text = primaryText, onClick = onSave, modifier = Modifier.weight(1f), height = 48.dp, fontSize = 13)
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
        Text("⚠️ $message", modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = Color(0xFF9F1239), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
private fun RoundIcon(icon: String) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = Color(0xFFE8F7FF),
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Box(contentAlignment = Alignment.Center) { Text(icon, fontSize = 24.sp) }
    }
}

@Composable
private fun SmallOutlineButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(EgDesign.pillRadius),
        color = Color.White,
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = EgDesign.primaryDark, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun LogoutButton(onLogout: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onLogout),
        shape = RoundedCornerShape(EgDesign.pillRadius),
        color = Color(0xFFFFF1F2),
        border = BorderStroke(1.dp, Color(0xFFFECACA)),
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("Đăng xuất", color = Color(0xFFB91C1C), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun fallback(value: String?): String = value?.takeIf { it.isNotBlank() } ?: "Chưa có"
