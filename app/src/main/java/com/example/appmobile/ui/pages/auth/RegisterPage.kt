package com.example.appmobile.ui.pages.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager // Thêm cái này
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction // Thêm cái này
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.ui.components.atoms.AppButton
import com.example.appmobile.ui.theme.SoftWhite

@Composable
fun RegisterPage(onNavigateBack: () -> Unit) {
    val authHelper = FirebaseAuthHelper()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current // Khai báo FocusManager

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Tham Gia Emo Garden",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF1976D2),
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Tên của bé
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Tên của bé") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true, // Chỉ cho nhập 1 dòng
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), // Hiển thị nút "Tiếp theo"
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) } // Nhấn Enter nhảy xuống ô dưới
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email phụ huynh") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Mật khẩu
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mật khẩu (ít nhất 6 ký tự)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done // Hiển thị nút "Xong" (Dấu tích xanh)
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() } // Nhấn Enter sẽ tắt bàn phím
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator(color = Color(0xFFBBDEFB))
        } else {
            AppButton(
                text = "TẠO TÀI KHOẢN",
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty() && name.isNotEmpty()) {
                        focusManager.clearFocus() // Tắt bàn phím khi bấm nút đăng ký
                        isLoading = true
                        authHelper.register(email, password) { success, error ->
                            isLoading = false
                            if (success) {
                                Toast.makeText(context, "Đăng ký thành công!", Toast.LENGTH_LONG).show()
                                onNavigateBack()
                            } else {
                                Toast.makeText(context, "Lỗi: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateBack) {
            Text("Đã có tài khoản? Quay lại Đăng nhập", color = Color(0xFF1976D2))
        }
    }
}