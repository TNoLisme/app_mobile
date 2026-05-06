package com.example.appmobile.ui.pages.auth

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.R
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.ui.theme.SoftWhite

@Composable
fun LoginPage(onNavigateToRegister: () -> Unit, onLoginSuccess: () -> Unit) {
    val authHelper = remember { FirebaseAuthHelper() }
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(SoftWhite, Color(0xFFE3F2FD), Color(0xFFBBDEFB))))
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            AuthTopBrand(onNavigateToRegister = onNavigateToRegister)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.index_image),
                        contentDescription = "Đăng nhập EmoGarden",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 190.dp)
                            .clip(MaterialTheme.shapes.extraLarge),
                        contentScale = ContentScale.Crop
                    )

                    Text("Đăng nhập", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0B3C7D))
                    Text("Tiếp tục hành trình học cảm xúc của bé.", color = Color.Gray)

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        label = { Text("Email phụ huynh") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Mật khẩu") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )

                    errorMessage?.let {
                        Surface(shape = MaterialTheme.shapes.large, color = Color(0xFFFFEBEE), modifier = Modifier.fillMaxWidth()) {
                            Text(it, modifier = Modifier.padding(12.dp), color = Color(0xFFC62828))
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showForgotDialog = true }) {
                            Text("Quên mật khẩu?", color = Color.Gray)
                        }
                    }

                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            onClick = {
                                val validation = validateLogin(email, password)
                                if (validation != null) {
                                    errorMessage = validation
                                    return@Button
                                }

                                isLoading = true
                                authHelper.login(email.trim(), password) { success, error ->
                                    isLoading = false
                                    if (success) {
                                        Toast.makeText(context, "Đăng nhập thành công", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess()
                                    } else {
                                        errorMessage = error ?: "Sai tài khoản hoặc mật khẩu."
                                    }
                                }
                            }
                        ) {
                            Text("Đăng nhập", fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    TextButton(onClick = onNavigateToRegister) {
                        Text("Chưa có tài khoản? Đăng ký ngay", color = Color(0xFF1976D2))
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
private fun AuthTopBrand(onNavigateToRegister: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "EmoGarden",
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text("EmoGarden", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0B3C7D))
            Text("Học cảm xúc qua trò chơi", fontSize = 13.sp, color = Color.Gray)
        }
        TextButton(onClick = onNavigateToRegister) { Text("Đăng ký") }
    }
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                message?.let { Text(it, color = Color(0xFFC62828)) }
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
                }
            ) {
                Text(if (isSending) "Đang gửi..." else "Gửi email")
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
