package com.example.appmobile.ui.pages.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.R
import com.example.appmobile.ui.theme.SoftWhite

@Composable
fun EmotionDetailPage(emotionId: String, onBack: () -> Unit) {
    val emotionNames = mapOf(
        "happy" to "Vui vẻ 😊",
        "sad" to "Buồn bã 😢",
        "angry" to "Tức giận 😠",
        "fear" to "Sợ hãi 😨",
        "surprise" to "Ngạc nhiên 😲",
        "disgust" to "Ghê tởm 🤢"
    )

    val situations = mapOf(
        "happy" to "Lan được tặng một món quà bất ngờ nên Lan rất vui và mỉm cười.",
        "sad" to "An đánh rơi kem rồi, nên An buồn và khóc.",
        "angry" to "Nam bị bạn giật đồ chơi mà không xin phép nên Nam tức giận.",
        "fear" to "Bé Mai đi lạc mẹ trong siêu thị nên cảm thấy rất sợ hãi.",
        "surprise" to "Huy mở hộp quà ra và thấy món đồ chơi mình rất thích nên rất ngạc nhiên.",
        "disgust" to "Minh ngửi thấy mùi rác thối nên cảm thấy rất ghê tởm."
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
            Text(emotionNames[emotionId] ?: "Cảm xúc", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Image: try drawable resource first (happy_1, sad_1, ...), then TTNM workspace file, then fallback to drawable logo
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) {
            val context = LocalContext.current
            val workspaceBase = "E:/CodeFiles/BTL ANDROIDDDDDDDDDDDDDD/app_mobile/TTNM-Game-main/fe/assets/images"
            val filePath = when (emotionId) {
                "happy" -> "$workspaceBase/happy/happy_1.jpg"
                "sad" -> "$workspaceBase/sad/sad_1.jpg"
                "angry" -> "$workspaceBase/angry/angry_1.jpg"
                "fear" -> "$workspaceBase/fear/fear_1.jpg"
                "surprise" -> "$workspaceBase/surprise/surprise_1.jpg"
                "disgust" -> "$workspaceBase/disgust/disgust_1.jpg"
                else -> null
            }

            // try asset first: assets/learn_emotion/{name}
            val assetRelative = when (emotionId) {
                "happy" -> "learn_emotion/happy_1.jpg"
                "sad" -> "learn_emotion/sad_1.jpg"
                "angry" -> "learn_emotion/angry_1.jpg"
                "fear" -> "learn_emotion/fear_1.jpg"
                "surprise" -> "learn_emotion/surprise_1.jpg"
                "disgust" -> "learn_emotion/disgust_1.jpg"
                else -> null
            }
            val assetExists = try {
                if (assetRelative != null) {
                    context.assets.open(assetRelative).close(); true
                } else false
            } catch (e: Exception) { false }
            val assetUri = if (assetRelative != null) "file:///android_asset/$assetRelative" else null
            // try drawable resource named like "happy_1" or with folder prefix "learn_emotion_happy_1"
            val resName = when (emotionId) {
                "happy" -> "happy_1"
                "sad" -> "sad_1"
                "angry" -> "angry_1"
                "fear" -> "fear_1"
                "surprise" -> "surprise_1"
                "disgust" -> "disgust_1"
                else -> null
            }

            var resId = 0
            if (resName != null) {
                val candidates = listOf(resName, "learn_emotion_${resName}")
                for (name in candidates) {
                    val id = context.resources.getIdentifier(name, "drawable", context.packageName)
                    if (id != 0) { resId = id; break }
                }
            }

            if (resId != 0) {
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = emotionNames[emotionId],
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (filePath != null && File(filePath).exists()) {
                AsyncImage(
                    model = File(filePath),
                    contentDescription = emotionNames[emotionId],
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.logo_emo),
                    contentDescription = emotionNames[emotionId],
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Situation
        Text(text = "Tình huống:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = situations[emotionId] ?: "Tình huống ví dụ",
                modifier = Modifier.padding(16.dp),
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Info
        Text(text = "Thường gặp khi:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "• Bạn cảm thấy vui vui", fontSize = 14.sp)
                Text(text = "• Bạn được khích lệ", fontSize = 14.sp)
                Text(text = "• Bạn nghe câu chuyện vui", fontSize = 14.sp)
            }
        }
    }
}
