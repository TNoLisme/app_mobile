package com.example.appmobile.ui.pages.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.ui.theme.SoftWhite

data class EmotionItem(val name: String, val emoji: String, val id: String)

@Composable
fun LearnPage(onBack: () -> Unit, onSelectEmotion: (String) -> Unit) {
    val emotions = listOf(
        EmotionItem("Vui vẻ", "😊", "happy"),
        EmotionItem("Buồn bã", "😢", "sad"),
        EmotionItem("Tức giận", "😠", "angry"),
        EmotionItem("Sợ hãi", "😨", "fear"),
        EmotionItem("Ngạc nhiên", "😲", "surprise"),
        EmotionItem("Ghê tởm", "🤢", "disgust")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .padding(16.dp)
    ) {
        // Back button
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("← Quay lại") }
            Spacer(modifier = Modifier.weight(1f))
            Text("Thẻ Học Cảm Xúc", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Nhấn vào cảm xúc để học thêm", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        // Emotion grid
        LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(emotions) { emotion ->
                EmotionCardGrid(emotion = emotion, onClick = { onSelectEmotion(emotion.id) })
            }
        }
    }
}

@Composable
fun EmotionCardGrid(emotion: EmotionItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = emotion.emoji, fontSize = 48.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = emotion.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
