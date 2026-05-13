package com.example.appmobile.ui.pages.auth

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.R
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.UserRepository
import kotlinx.coroutines.launch

@Composable
fun LoginPage(onNavigateToRegister: () -> Unit, onLoginSuccess: () -> Unit) {
    val authHelper = remember { FirebaseAuthHelper() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val userRepository = remember { UserRepository(NetworkClient.apiService, authHelper, db.userDao()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background Image
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Logo and Brand
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Logo",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "EmoGarden",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7CB9E8)
                        )
                    }

                    Text(
                        "Đăng nhập",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4A4A4A)
                    )
                    Text(
                        "Tiếp tục hành trình học cảm xúc của bé",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    // Illustration
                    Image(
                        painter = painterResource(id = R.drawable.duatre),
                        contentDescription = "Illustration",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentScale = ContentScale.Fit
                    )

                    // Input Fields
                    AuthTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        placeholder = "Email phụ huynh",
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )

                    AuthTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        placeholder = "Mật khẩu",
                        isPassword = true,
                        imeAction = ImeAction.Done
                    )

                    errorMessage?.let {
                        Text(it, color = Color.Red, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                    }

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            "Quên mật khẩu?",
                            color = Color(0xFF8D6E63),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { showForgotDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isLoading) {
                        CircularProgressIndicator(color = Color(0xFFFFA726))
                    } else {
                        Button(
                            onClick = {
                                val validation = validateLogin(email, password)
                                if (validation != null) {
                                    errorMessage = validation
                                    return@Button
                                }
                                isLoading = true
                                authHelper.login(email.trim(), password) { success, error ->
                                    if (success) {
                                        isLoading = false
                                        AppSession.clear(context)
                                        Toast.makeText(context, "Đăng nhập thành công", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess()
                                    } else {
                                        scope.launch {
                                            val backendResult = userRepository.loginWithBackend(email.trim(), password)
                                            isLoading = false
                                            backendResult.onSuccess { profile ->
                                                profile.userId?.let { AppSession.saveBackendUserId(context, it) }
                                                Toast.makeText(context, "Đăng nhập thành công", Toast.LENGTH_SHORT).show()
                                                onLoginSuccess()
                                            }.onFailure {
                                                errorMessage = error ?: it.message ?: "Sai tài khoản hoặc mật khẩu."
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .shadow(4.dp, RoundedCornerShape(28.dp)),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726))
                        ) {
                            Text("Đăng nhập", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chưa có tài khoản? ", color = Color.DarkGray, fontSize = 14.sp)
                        Text(
                            "Đăng ký ngay",
                            color = Color(0xFF8D6E63),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onNavigateToRegister() }
                        )
                    }
                }
            }
        }
    }

    if (showForgotDialog) {
        ForgotPasswordDialog(
            initialEmail = email,
            authHelper = authHelper,
            onDismiss = { showForgotDialog = false }
        )
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color.Gray) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFAED9FF),
            unfocusedBorderColor = Color(0xFFAED9FF),
            focusedContainerColor = Color(0xFFF0F8FF),
            unfocusedContainerColor = Color(0xFFF0F8FF)
        )
    )
}

@Composable
private fun CloudIcon(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.5f),
        shape = RoundedCornerShape(50)
    ) {}
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
        title = { Text("Quên mật khẩu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nhập email tài khoản, hệ thống sẽ gửi link đặt lại mật khẩu.", color = Color.Gray)
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        message = null
                    },
                    label = { Text("Email của bạn") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp)
                )
                message?.let { Text(it, color = Color.Red) }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSending,
                onClick = {
                    if (!isValidEmail(email)) {
                        message = "Email không hợp lệ."
                        return@Button
                    }
                    isSending = true
                    authHelper.resetPassword(email.trim()) { success, error ->
                        isSending = false
                        if (success) {
                            Toast.makeText(context, "Kiểm tra email để đặt lại mật khẩu", Toast.LENGTH_LONG).show()
                            onDismiss()
                        } else {
                            message = error ?: "Không gửi được email đặt lại mật khẩu."
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726))
            ) {
                Text(if (isSending) "Đang gửi..." else "Gửi email", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(enabled = !isSending, onClick = onDismiss) { Text("Hủy") }
        }
    )
}

private fun validateLogin(email: String, password: String): String? {
    if (!isValidEmail(email)) return "Vui lòng nhập email hợp lệ."
    if (password.isBlank()) return "Vui lòng nhập mật khẩu."
    return null
}

private fun isValidEmail(value: String): Boolean {
    return value.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
}
