package com.example.appmobile.ui.pages.auth

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.UserRepository
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgVectorEmojiIcon
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

@Composable
fun LoginPage(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authHelper = remember { FirebaseAuthHelper() }
    val db = remember { AppDatabase.getDatabase(context) }
    val userRepository = remember { UserRepository(NetworkClient.apiService, authHelper, db.userDao()) }

    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }
    val googleWebClientId = remember(context) { findGoogleWebClientId(context) }
    val googleSignInClient = remember(context, googleWebClientId) {
        if (googleWebClientId.isBlank()) {
            null
        } else {
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(googleWebClientId)
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, options)
        }
    }
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            isGoogleLoading = false
            return@rememberLauncherForActivityResult
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                isGoogleLoading = false
                errorMessage = "Chưa bật đăng nhập Google cho app này. Vui lòng kiểm tra cấu hình rồi thử lại."
                return@rememberLauncherForActivityResult
            }

            authHelper.loginWithGoogleIdToken(idToken) { success, error ->
                if (!success) {
                    isGoogleLoading = false
                    errorMessage = mapGoogleLoginError(error)
                    return@loginWithGoogleIdToken
                }

                val firebaseUser = authHelper.auth.currentUser
                val email = firebaseUser?.email ?: account.email
                val userId = firebaseUser?.uid
                if (userId.isNullOrBlank() || email.isNullOrBlank()) {
                    authHelper.auth.signOut()
                    isGoogleLoading = false
                    errorMessage = "Google chưa trả về đủ thông tin tài khoản. Vui lòng thử lại."
                    return@loginWithGoogleIdToken
                }

                scope.launch {
                    val syncResult = userRepository.syncGoogleAccount(
                        userId = userId,
                        email = email,
                        displayName = account.displayName ?: firebaseUser.displayName
                    )
                    isGoogleLoading = false
                    syncResult.onSuccess { profile ->
                        errorMessage = null
                        AppSession.clear(context)
                        profile.userId?.let { AppSession.saveBackendUserId(context, it) }
                        Toast.makeText(context, "Đăng nhập Google thành công", Toast.LENGTH_SHORT).show()
                        onLoginSuccess()
                    }.onFailure {
                        authHelper.auth.signOut()
                        errorMessage = it.message ?: "Chưa đăng nhập được với Google. Vui lòng thử lại."
                    }
                }
            }
        } catch (error: ApiException) {
            isGoogleLoading = false
            errorMessage = mapGoogleLoginError(error)
        } catch (error: Exception) {
            isGoogleLoading = false
            errorMessage = mapGoogleLoginError(error)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EgDesign.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(EgDesign.radiusXLarge),
                color = EgDesign.card,
                border = androidx.compose.foundation.BorderStroke(1.dp, EgDesign.cardBorder),
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "EmoGarden",
                        color = EgDesign.primaryDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                    Text(
                        text = "Đăng nhập",
                        color = EgDesign.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        lineHeight = 34.sp
                    )
                    Text(
                        text = "Tiếp tục hành trình học cảm xúc của bé.",
                        color = EgDesign.textSecondary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )

                    AuthTextField(
                        value = loginId,
                        onValueChange = {
                            loginId = it
                            errorMessage = null
                        },
                        label = "Tên đăng nhập hoặc email",
                        placeholder = "Nhập tên đăng nhập hoặc email tài khoản",
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )

                    AuthTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = "Mật khẩu",
                        placeholder = "Nhập mật khẩu",
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = EgDesign.danger,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = "Quên mật khẩu?",
                        color = EgDesign.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable { showForgotDialog = true }
                    )

                    if (isLoading) {
                        CircularProgressIndicator(color = EgDesign.primary, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        Button(
                            enabled = !isGoogleLoading,
                            onClick = {
                                val validation = validateLogin(loginId, password)
                                if (validation != null) {
                                    errorMessage = validation
                                    return@Button
                                }

                                isLoading = true
                                errorMessage = null
                                val identity = loginId.trim()
                                val handleBackendLogin: () -> Unit = {
                                    scope.launch {
                                        authHelper.auth.signOut()
                                        val backendResult = userRepository.loginWithBackend(identity, password)
                                        isLoading = false
                                        backendResult.onSuccess { profile ->
                                            errorMessage = null
                                            AppSession.clear(context)
                                            profile.userId?.let { AppSession.saveBackendUserId(context, it) }
                                            Toast.makeText(context, "Đăng nhập thành công", Toast.LENGTH_SHORT).show()
                                            onLoginSuccess()
                                        }.onFailure {
                                            errorMessage = mapLoginError(it)
                                        }
                                    }
                                }
                                handleBackendLogin()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                        ) {
                            Text(
                                text = "Đăng nhập",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                        }
                    }

                    AuthDivider()

                    GoogleSignInButton(
                        enabled = !isLoading && !isGoogleLoading,
                        isLoading = isGoogleLoading,
                        onClick = {
                            if (googleSignInClient == null) {
                                errorMessage = "Chưa bật đăng nhập Google cho app này. Vui lòng kiểm tra cấu hình rồi thử lại."
                                return@GoogleSignInButton
                            }
                            isGoogleLoading = true
                            errorMessage = null
                            googleSignInClient.signOut().addOnCompleteListener {
                                googleLauncher.launch(googleSignInClient.signInIntent)
                            }
                        }
                    )

                    FacebookSignInButton(
                        enabled = !isLoading && !isGoogleLoading,
                        onClick = {
                            errorMessage = "Đăng nhập Facebook đang được chuẩn bị."
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Chưa có tài khoản? ",
                            color = EgDesign.textSecondary
                        )
                        Text(
                            text = "Đăng ký ngay",
                            color = EgDesign.primary,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.clickable(onClick = onNavigateToRegister)
                        )
                    }
                }
            }
        }
    }

    if (showForgotDialog) {
        ForgotPasswordDialog(
            initialEmail = loginId.takeIf { isValidEmail(it) }.orEmpty(),
            authHelper = authHelper,
            onDismiss = { showForgotDialog = false }
        )
    }
}

@Composable
private fun AuthDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = EgDesign.cardBorder
        )
        Text(
            text = "Hoặc",
            color = EgDesign.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = EgDesign.cardBorder
        )
    }
}

@Composable
private fun GoogleSignInButton(
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, EgDesign.cardBorder),
        colors = ButtonDefaults.buttonColors(
            containerColor = EgDesign.card,
            contentColor = EgDesign.textPrimary,
            disabledContainerColor = EgDesign.cardSoft,
            disabledContentColor = EgDesign.textSecondary
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = EgDesign.primary
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = "Đang đăng nhập Google...",
                fontWeight = FontWeight.Bold
            )
        } else {
            GoogleLogoMark()
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = "Đăng nhập với Google",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun GoogleLogoMark() {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "G",
                color = Color(0xFF4285F4),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FacebookSignInButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, EgDesign.cardBorder),
        colors = ButtonDefaults.buttonColors(
            containerColor = EgDesign.card,
            contentColor = EgDesign.textPrimary,
            disabledContainerColor = EgDesign.cardSoft,
            disabledContentColor = EgDesign.textSecondary
        )
    ) {
        FacebookLogoMark()
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = "Đăng nhập với Facebook",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun FacebookLogoMark() {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1877F2)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "f",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder, color = EgDesign.textSecondary) },
        shape = RoundedCornerShape(EgDesign.controlRadius),
        singleLine = true,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { onTogglePasswordVisibility?.invoke() }) {
                    EgVectorEmojiIcon(
                        value = "eye",
                        size = 20.dp,
                        tint = if (passwordVisible) EgDesign.primary else EgDesign.textSecondary
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EgDesign.primary,
            unfocusedBorderColor = EgDesign.cardBorder,
            focusedContainerColor = EgDesign.cardSoft,
            unfocusedContainerColor = EgDesign.cardSoft,
            focusedTextColor = EgDesign.textPrimary,
            unfocusedTextColor = EgDesign.textPrimary,
            focusedLabelColor = EgDesign.primary,
            unfocusedLabelColor = EgDesign.textSecondary,
            cursorColor = EgDesign.primary
        )
    )
}

@Composable
private fun ForgotPasswordDialog(
    initialEmail: String,
    authHelper: FirebaseAuthHelper,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var email by remember(initialEmail) { mutableStateOf(initialEmail) }
    var isSending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Quên mật khẩu", color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Nhập email tài khoản để nhận liên kết đặt lại mật khẩu.",
                    color = EgDesign.textSecondary,
                    lineHeight = 20.sp
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        message = null
                    },
                    label = { Text("Email tài khoản") },
                    placeholder = { Text("example@email.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(EgDesign.controlRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EgDesign.primary,
                        unfocusedBorderColor = EgDesign.cardBorder,
                        focusedContainerColor = EgDesign.cardSoft,
                        unfocusedContainerColor = EgDesign.cardSoft,
                        focusedTextColor = EgDesign.textPrimary,
                        unfocusedTextColor = EgDesign.textPrimary,
                        focusedLabelColor = EgDesign.primary,
                        unfocusedLabelColor = EgDesign.textSecondary,
                        cursorColor = EgDesign.primary
                    )
                )
                message?.let {
                    Text(
                        text = it,
                        color = EgDesign.danger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSending,
                onClick = {
                    if (!isValidEmail(email)) {
                        message = "Email chưa hợp lệ."
                        return@Button
                    }
                    isSending = true
                    authHelper.resetPassword(email.trim()) { success, error ->
                        isSending = false
                        if (success) {
                            Toast.makeText(context, "Đã gửi email đặt lại mật khẩu.", Toast.LENGTH_LONG).show()
                            onDismiss()
                        } else {
                            message = error ?: "Chưa gửi được email đặt lại mật khẩu."
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
            ) {
                Text(if (isSending) "Đang gửi..." else "Gửi email", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSending,
                onClick = onDismiss
            ) {
                Text("Hủy", color = EgDesign.textPrimary)
            }
        },
        containerColor = EgDesign.card,
        textContentColor = EgDesign.textPrimary
    )
}

private fun validateLogin(identity: String, password: String): String? {
    val trimmed = identity.trim()
    if (trimmed.isBlank()) return "Vui lòng nhập tên đăng nhập hoặc email."
    if ("@" in trimmed && !isValidEmail(trimmed)) return "Email chưa hợp lệ."
    if (password.isBlank()) return "Vui lòng nhập mật khẩu."
    return null
}

private fun isValidEmail(value: String): Boolean {
    return value.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
}

private fun findGoogleWebClientId(context: Context): String {
    val resourceId = context.resources.getIdentifier(
        "default_web_client_id",
        "string",
        context.packageName
    )
    return if (resourceId == 0) "" else context.getString(resourceId).trim()
}

private fun mapLoginError(error: Throwable): String {
    val message = (error.message ?: "").lowercase()
    return if (
        "failed to connect" in message ||
        "unable to resolve host" in message ||
        "timeout" in message ||
        "cannot connect to backend" in message ||
        "connection refused" in message
    ) {
        "Không kết nối được máy chủ. Kiểm tra backend và mạng rồi thử lại."
    } else {
        "Tên đăng nhập/email hoặc mật khẩu chưa đúng."
    }
}

private fun mapGoogleLoginError(error: Throwable): String = mapGoogleLoginError(error.message)

private fun mapGoogleLoginError(rawMessage: String?): String {
    val message = (rawMessage ?: "").lowercase()
    return when {
        "developer_error" in message ||
            "10:" in message ||
            "default_web_client_id" in message ||
            "id token" in message -> "Chưa bật đăng nhập Google cho app này. Vui lòng kiểm tra cấu hình rồi thử lại."
        "failed to connect" in message ||
            "unable to resolve host" in message ||
            "timeout" in message ||
            "cannot connect to backend" in message ||
            "connection refused" in message ||
            "không kết nối" in message ||
            "máy chủ" in message ||
            "mạng" in message -> "Không kết nối được máy chủ. Kiểm tra backend và mạng rồi thử lại."
        else -> "Chưa đăng nhập được với Google. Vui lòng thử lại."
    }
}
