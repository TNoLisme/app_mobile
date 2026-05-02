package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.R
import com.example.appmobile.ui.components.GameScreenShell

@Composable
fun RecognizeEmotionPage(onBack: () -> Unit) {
    val currentIndex = remember { mutableStateOf(0) }
    val selectedIndex = remember { mutableStateOf(-1) }

    val options = listOf(
        Triple("Vui vẻ", R.drawable.happy_1, "😊"),
        Triple("Buồn bã", R.drawable.sad_1, "😢"),
        Triple("Ngạc nhiên", R.drawable.surprise_1, "😲"),
        Triple("Tức giận", R.drawable.angry_1, "😠"),
        Triple("Sợ hãi", R.drawable.fear_1, "😨"),
        Triple("Ghê tởm", R.drawable.disgust_1, "🤢"),
    )

    GameScreenShell(contentMaxWidth = 800) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Thoát") }
                Spacer(modifier = Modifier.weight(1f))
                Surface(shape = MaterialTheme.shapes.large, color = Color(0xFFE7F1FF), border = BorderStroke(1.dp, Color(0xFFBFD7FF))) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(progress = (currentIndex.value + 1) / 10f, modifier = Modifier.width(60.dp).height(6.dp), color = Color(0xFF3B82F6))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Câu ${currentIndex.value + 1}/10", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Chiếc hộp cảm xúc", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
            Text("Bé hãy nhìn hình và chọn cảm xúc đúng nhất nhé", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

            Spacer(modifier = Modifier.height(20.dp))

            // Câu hỏi
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.recognize_emotion),
                        contentDescription = null,
                        modifier = Modifier.size(150.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Đây là cảm xúc gì?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tùy chọn trả lời
            val rows = options.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEachIndexed { rIdx, rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowItems.forEachIndexed { cIdx, item ->
                            val index = rIdx * 2 + cIdx
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedIndex.value = index },
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(2.dp, if (selectedIndex.value == index) Color(0xFF3B82F6) else Color(0xFFF1F5F9)),
                                colors = CardDefaults.cardColors(containerColor = if (selectedIndex.value == index) Color(0xFFE7F1FF) else Color.White)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.third, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(item.first, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { currentIndex.value = (currentIndex.value + 1) % 10; selectedIndex.value = -1 },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = selectedIndex.value != -1
            ) {
                Text("Trả lời")
            }
        }
    }
}
