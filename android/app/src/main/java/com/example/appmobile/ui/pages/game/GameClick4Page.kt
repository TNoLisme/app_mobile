package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.appmobile.R
import com.example.appmobile.ui.components.GameScreenShell

@Composable
fun GameClick4Page(onBack: () -> Unit) {
    GameScreenShell(contentMaxWidth = 600) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Quay lại") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Thám tử cảm xúc", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painter = painterResource(id = R.drawable.game_click_4), contentDescription = null, modifier = Modifier.size(180.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Bé hãy quan sát thật kỹ để tìm ra cảm xúc nhé!", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = { }, modifier = Modifier.fillMaxWidth()) { Text("Bắt đầu tìm") }
                }
            }
        }
    }
}
