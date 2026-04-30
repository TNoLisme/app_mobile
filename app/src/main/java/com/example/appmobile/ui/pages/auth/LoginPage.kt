package com.example.appmobile.ui.pages.auth

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.appmobile.R
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.ui.components.atoms.AppButton
import com.example.appmobile.ui.theme.SoftWhite

@Composable
fun LoginPage(onNavigateToRegister: () -> Unit, onLoginSuccess: () -> Unit) {
    // 1. Khai báo công cụ và trạng thái
    val authHelper = FirebaseAuthHelper()
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) } // Trạng thái chờ xử lý

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo Emo Garden
        Image(
            painter = painterResource(id = R.drawable.logo_emo),
            contentDescription = "Logo",
            modifier = Modifier.size(150.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Ô nhập Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email của phụ huynh") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Ô nhập Mật khẩu
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mật khẩu") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Nút Quên mật khẩu (Căn lề phải)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                if (email.isNotEmpty()) {
                    authHelper.resetPassword(email) { success, error ->
                        val msg = if (success) "Kiểm tra email để đặt lại mật khẩu" else "Lỗi: $error"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "Vui lòng nhập email trước", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Quên mật khẩu?", color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Nút Đăng nhập chính (Có Logic Firebase)
        if (isLoading) {
            CircularProgressIndicator(color = Color(0xFFBBDEFB)) // Hiện vòng xoay khi đang load
        } else {
            AppButton(
                text = "ĐĂNG NHẬP",
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        isLoading = true
                        authHelper.login(email, password) { success, error ->
                            isLoading = false
                            if (success) {
                                onLoginSuccess()
                            } else {
                                Toast.makeText(context, "Đăng nhập thất bại: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Nút chuyển sang Đăng ký
        TextButton(onClick = onNavigateToRegister) {
            Text("Chưa có tài khoản? Đăng ký ngay", color = Color(0xFF1976D2))
        }
    }
}