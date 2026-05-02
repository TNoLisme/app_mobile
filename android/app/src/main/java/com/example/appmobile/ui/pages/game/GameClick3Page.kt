package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
fun GameClick3Page(onBack: () -> Unit) {
    val faces = listOf(R.drawable.happy_1, R.drawable.sad_1, R.drawable.angry_1, R.drawable.fear_1)
    val nameCards = listOf("Vui vẻ", "Buồn bã", "Tức giận", "Sợ hãi")

    GameScreenShell(contentMaxWidth = 800) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Quay lại") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Cảm xúc đúng chỗ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Bé hãy nối tên cảm xúc vào hình tương ứng", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val rows = faces.chunked(2)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows.forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowItems.forEach { face ->
                                    Card(modifier = Modifier.weight(1f).height(100.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FBFF))) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Image(painter = painterResource(id = face), contentDescription = null, modifier = Modifier.size(60.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("Thẻ tên", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                nameCards.forEach { name ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color(0xFFE1F5FE),
                        modifier = Modifier.weight(1f).clickable { }
                    ) {
                        Text(name, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
