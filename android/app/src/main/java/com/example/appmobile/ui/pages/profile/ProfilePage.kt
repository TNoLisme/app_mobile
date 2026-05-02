package com.example.appmobile.ui.pages.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProfilePage(onBack: () -> Unit, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("← Quay lại") }
            Spacer(modifier = Modifier.weight(1f))
            Text("Hồ sơ")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Tên: Bé Yêu")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Email: parent@example.com")

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onLogout) {
            Text("Đăng xuất")
        }
    }
}
