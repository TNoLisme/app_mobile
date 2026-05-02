package com.example.appmobile.ui.pages.select

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.appmobile.ui.components.GameScreenShell

@Composable
fun LevelSelectPage(gameId: String, onBack: () -> Unit, onStartGame: (String) -> Unit) {
    val levels = listOf(
        LevelItem("Dễ", "Phù hợp cho bé mới bắt đầu", Color(0xFF81C784)),
        LevelItem("Trung bình", "Thử thách hơn một chút", Color(0xFFFFB74D)),
        LevelItem("Khó", "Dành cho bé đã thành thạo", Color(0xFFE57373))
    )

    GameScreenShell(contentMaxWidth = 500) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { 
                    Text("←", style = MaterialTheme.typography.headlineMedium) 
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Chọn Cấp Độ", 
                    style = MaterialTheme.typography.headlineSmall, 
                    fontWeight = FontWeight.Bold, 
                    color = Color(0xFF1E4E8C)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sử dụng Column thay vì LazyColumn để tránh lỗi "Infinity Height" khi lồng vào Shell
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                levels.forEach { lvl ->
                    LevelCard(lvl, onStartGame)
                }
            }
        }
    }
}

private data class LevelItem(val name: String, val desc: String, val color: Color)

@Composable
private fun LevelCard(level: LevelItem, onStartGame: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStartGame(level.name) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(level.color, MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = level.name, 
                    fontWeight = FontWeight.Bold, 
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = level.desc, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = Color.Gray
                )
            }
            Text("▶", color = Color.LightGray)
        }
    }
}
