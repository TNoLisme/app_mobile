package com.example.appmobile.ui.pages.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.UserRepository
import com.example.appmobile.ui.components.atoms.AppButton
import com.example.appmobile.ui.theme.SoftWhite
import com.example.appmobile.ui.viewmodel.AuthViewModel

@Composable
fun RegisterPage(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Khởi tạo Repository với UserDao
    val authHelper = remember { FirebaseAuthHelper() }
    val db = remember { AppDatabase.getDatabase(context) }
    val userRepository = remember { UserRepository(NetworkClient.apiService, authHelper, db.userDao()) }
    val viewModel = remember { AuthViewModel(userRepository) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Nam") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(text = "Tạo Tài Khoản", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Tên của bé") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = age, onValueChange = { if (it.all { char -> char.isDigit() }) age = it },
            label = { Text("Tuổi của bé") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Chọn giới tính đơn giản
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Giới tính: ")
            RadioButton(selected = gender == "Nam", onClick = { gender = "Nam" })
            Text("Nam")
            Spacer(modifier = Modifier.width(8.dp))
            RadioButton(selected = gender == "Nữ", onClick = { gender = "Nữ" })
            Text("Nữ")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email phụ huynh") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Mật khẩu") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            AppButton(
                text = "ĐĂNG KÝ",
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty() && name.isNotEmpty() && age.isNotEmpty()) {
                        isLoading = true
                        viewModel.register(email, password, name, age.toInt(), gender) { success, error ->
                            isLoading = false
                            if (success) {
                                Toast.makeText(context, "Đăng ký thành công!", Toast.LENGTH_LONG).show()
                                onNavigateBack()
                            } else {
                                Toast.makeText(context, "Lỗi: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Vui lòng nhập đủ thông tin", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        TextButton(onClick = onNavigateBack) { Text("Đã có tài khoản? Đăng nhập") }
    }
}