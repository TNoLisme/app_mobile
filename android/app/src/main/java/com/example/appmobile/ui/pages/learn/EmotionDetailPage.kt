package com.example.appmobile.ui.pages.learn

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.R
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.pages.game.emotionLearningInfo
import com.example.appmobile.ui.theme.SoftWhite

@Composable
fun EmotionDetailPage(emotionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }
    var emotion by remember(emotionId) { mutableStateOf(GameUiCatalog.emotionById(emotionId)) }
    val imageResourceId = rememberEmotionImageResource(emotionId)

    LaunchedEffect(emotionId) {
        val backendEmotion = runCatching {
            repository.getEmotionConcepts()
                .firstOrNull { it.id == emotionId }
                ?.let { concept ->
                    GameUiCatalog.emotionFromBackend(
                        id = concept.id,
                        title = concept.name,
                        description = concept.desc
                    )
                }
        }.getOrNull()

        if (backendEmotion != null) emotion = backendEmotion
    }

    val learningInfo = emotionLearningInfo(emotionId)
    val example = learningInfo.situation

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            AppBackButton(onClick = onBack)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = emotion?.let { "${it.name} ${it.emoji}" } ?: "Cảm xúc",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) {
            Image(
                painter = painterResource(id = imageResourceId),
                contentDescription = emotion?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "Tình huống:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = example,
                modifier = Modifier.padding(16.dp),
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "Dấu hiệu nhận biết:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = emotion?.description ?: learningInfo.description,
                    fontSize = 14.sp
                )
                learningInfo.cues.forEach { cue ->
                    Text("• $cue", fontSize = 14.sp, color = Color.DarkGray)
                }
            }
        }
    }
}

@Composable
private fun rememberEmotionImageResource(emotionId: String): Int {
    val context = LocalContext.current
    val resourceName = when (emotionId) {
        "happy" -> "happy_1"
        "sad" -> "sad_1"
        "angry" -> "angry_1"
        "fear" -> "fear_1"
        "surprise" -> "surprise_1"
        "disgust" -> "disgust_1"
        else -> null
    }

    if (resourceName != null) {
        listOf(resourceName, "learn_emotion_$resourceName").forEach { candidate ->
            val id = context.resources.getIdentifier(candidate, "drawable", context.packageName)
            if (id != 0) return id
        }
    }

    return R.drawable.logo_emo
}
