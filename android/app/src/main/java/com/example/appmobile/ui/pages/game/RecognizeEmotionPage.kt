package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
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
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.AnswerResultDto
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.GameScreenShell
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private data class RecognizeQuestionUi(
    val questionId: String,
    val questionText: String,
    val imageRes: Int,
    val correctEmotion: String,
    val optionEmotionIds: List<String> = GameUiCatalog.emotions.map { it.id }
)

@Composable
fun RecognizeEmotionPage(level: Int = 1, onBack: () -> Unit, onOpenAssistant: () -> Unit = {}) {
    val currentIndex = remember(level) { mutableIntStateOf(0) }
    val score = remember(level) { mutableIntStateOf(0) }
    val selectedEmotionId = remember(level) { mutableStateOf<String?>(null) }
    val feedback = remember(level) { mutableStateOf<String?>(null) }
    val questions = remember(level) { mutableStateOf(fallbackRecognizeQuestions()) }
    val sessionId = remember(level) { mutableStateOf<String?>(null) }
    val results = remember(level) { mutableStateOf<List<AnswerResultDto>>(emptyList()) }
    val summary = remember(level) { mutableStateOf<String?>(null) }
    val isSubmitting = remember(level) { mutableStateOf(false) }
    val questionStartMs = remember(level) { mutableStateOf(System.currentTimeMillis()) }
    val maxErrors = remember(level) { mutableIntStateOf(3) }
    val emotionErrors = remember(level) { mutableStateMapOf<String, Int>() }
    val learnedEmotions = remember(level) { mutableStateListOf<String>() }
    val learningEmotionId = remember(level) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: AppSession.currentBackendUserId() ?: "local-player" }
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }

    fun finishLevel(finalResults: List<AnswerResultDto>) {
        if (isSubmitting.value || summary.value != null) return
        scope.launch {
            isSubmitting.value = true
            val response = sessionId.value?.let {
                repository.endLevel(it, finalResults, learnedEmotions.distinct())
            }
            summary.value = if (response != null) {
                val status = if (response.passed) "ÄÃ£ qua level" else "ChÆ°a qua level"
                "$status. Äiá»ƒm: ${response.score}/50."
            } else {
                "HoÃ n thÃ nh. Äiá»ƒm táº¡m tÃ­nh: ${score.intValue}."
            }
            response?.reviewEmotionsToLearn
                ?.firstOrNull()
                ?.let { learningEmotionId.value = normalizeEmotionForLearning(it) }
            isSubmitting.value = false
        }
    }

    LaunchedEffect(level, userId) {
        val started = repository.startGame(GameUiCatalog.GAME_RECOGNIZE_EMOTION, userId, level)
        sessionId.value = started?.sessionId
        maxErrors.intValue = started?.maxErrors ?: 3
        val backendQuestions = started?.questions
            ?.mapNotNull { content ->
                val emotion = normalizeEmotionForLearning((content.correctAnswer ?: content.emotion ?: "").ifBlank { return@mapNotNull null })
                RecognizeQuestionUi(
                    questionId = content.contentId,
                    questionText = content.questionText?.ifBlank { "ÄÃ¢y lÃ  cáº£m xÃºc gÃ¬?" } ?: "ÄÃ¢y lÃ  cáº£m xÃºc gÃ¬?",
                    imageRes = emotionImageResource(emotion),
                    correctEmotion = emotion,
                    optionEmotionIds = optionEmotionIdsFromBackend(content.options, emotion)
                )
            }
            .orEmpty()

        questions.value = backendQuestions.ifEmpty { fallbackRecognizeQuestions() }
        currentIndex.intValue = 0
        score.intValue = 0
        selectedEmotionId.value = null
        feedback.value = null
        results.value = emptyList()
        summary.value = null
        emotionErrors.clear()
        learnedEmotions.clear()
        learningEmotionId.value = null
        questionStartMs.value = System.currentTimeMillis()
    }

    val currentQuestion = questions.value[currentIndex.intValue % questions.value.size]
    val options = currentQuestion.optionEmotionIds
        .mapNotNull { GameUiCatalog.emotionById(it) }
        .ifEmpty { GameUiCatalog.emotions }

    GameScreenShell(contentMaxWidth = 800, onOpenAssistant = onOpenAssistant) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("â† ThoÃ¡t") }
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = Color(0xFFE7F1FF),
                    border = BorderStroke(1.dp, Color(0xFFBFD7FF))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { (currentIndex.intValue + 1).toFloat() / questions.value.size },
                            modifier = Modifier.width(60.dp).height(6.dp),
                            color = Color(0xFF3B82F6)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CÃ¢u ${currentIndex.intValue + 1}/${questions.value.size}")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Chiáº¿c há»™p cáº£m xÃºc",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E4E8C)
            )
            Text(
                "BÃ© hÃ£y nhÃ¬n hÃ¬nh vÃ  chá»n cáº£m xÃºc Ä‘Ãºng nháº¥t nhÃ©",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            if (summary.value != null) {
                Spacer(modifier = Modifier.height(20.dp))
                LevelSummaryCard(summary = summary.value.orEmpty(), onBack = onBack)
                EmotionLearningDialog(
                    emotionId = learningEmotionId.value,
                    onDismiss = {
                        val emotion = learningEmotionId.value
                        learningEmotionId.value = null
                        if (emotion != null) {
                            scope.launch {
                                repository.resetReviewEmotions(GameUiCatalog.GAME_RECOGNIZE_EMOTION, userId, listOf(emotion))
                            }
                        }
                    }
                )
                return@Column
            }

            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = currentQuestion.imageRes),
                        contentDescription = null,
                        modifier = Modifier.size(150.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        currentQuestion.questionText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (feedback.value != null) {
                Spacer(modifier = Modifier.height(12.dp))
                FeedbackCard(feedback.value.orEmpty())
            }

            Spacer(modifier = Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                options.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowItems.forEach { item ->
                            val visualState = answerVisualState(
                                optionId = item.id,
                                correctEmotion = currentQuestion.correctEmotion,
                                selectedEmotionId = selectedEmotionId.value,
                                hasFeedback = feedback.value != null
                            )
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = feedback.value == null) { selectedEmotionId.value = item.id },
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(
                                    2.dp,
                                    visualState.borderColor
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = visualState.containerColor
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.emoji, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(item.name, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (feedback.value == null) {
                        val selected = selectedEmotionId.value ?: return@Button
                        val isCorrect = selected == currentQuestion.correctEmotion
                        if (isCorrect) score.intValue += 10
                        val reviewEmotion = normalizeEmotionForLearning(currentQuestion.correctEmotion)
                        if (!isCorrect) {
                            val nextErrorCount = (emotionErrors[reviewEmotion] ?: 0) + 1
                            emotionErrors[reviewEmotion] = nextErrorCount
                            if (nextErrorCount >= maxErrors.intValue && reviewEmotion !in learnedEmotions) {
                                learnedEmotions.add(reviewEmotion)
                            }
                        }
                        val updatedResults = results.value + AnswerResultDto(
                            questionId = currentQuestion.questionId,
                            answer = selected,
                            isCorrect = isCorrect,
                            responseTimeMs = (System.currentTimeMillis() - questionStartMs.value).toInt()
                        )
                        results.value = updatedResults
                        val targetName = GameUiCatalog.emotionById(currentQuestion.correctEmotion)?.name
                            ?: currentQuestion.correctEmotion
                        feedback.value = if (isCorrect) "ÄÃºng rá»“i." else "ChÆ°a Ä‘Ãºng. ÄÃ¡p Ã¡n lÃ  $targetName."
                        return@Button
                    }

                    val isLastQuestion = currentIndex.intValue >= questions.value.lastIndex
                    if (isLastQuestion) {
                        finishLevel(results.value)
                    } else {
                        currentIndex.intValue += 1
                        selectedEmotionId.value = null
                        feedback.value = null
                        questionStartMs.value = System.currentTimeMillis()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = selectedEmotionId.value != null && !isSubmitting.value
            ) {
                Text(
                    when {
                        isSubmitting.value -> "Äang lÆ°u..."
                        feedback.value == null -> "Tráº£ lá»i"
                        currentIndex.intValue >= questions.value.lastIndex -> "HoÃ n thÃ nh"
                        else -> "CÃ¢u tiáº¿p theo"
                    }
                )
            }
            EmotionLearningDialog(
                emotionId = learningEmotionId.value,
                onDismiss = {
                    val emotion = learningEmotionId.value
                    learningEmotionId.value = null
                    if (emotion != null) {
                        scope.launch {
                            repository.resetReviewEmotions(GameUiCatalog.GAME_RECOGNIZE_EMOTION, userId, listOf(emotion))
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun FeedbackCard(message: String) {
    val isCorrect = message.startsWith("ÄÃºng")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isCorrect) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFE65100),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LevelSummaryCard(summary: String, onBack: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Káº¿t thÃºc level", fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
            Text(summary)
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Quay láº¡i chá»n level")
            }
        }
    }
}

private fun fallbackRecognizeQuestions(): List<RecognizeQuestionUi> {
    return listOf(
        RecognizeQuestionUi("fallback-recognize-happy", "ÄÃ¢y lÃ  cáº£m xÃºc gÃ¬?", R.drawable.happy_1, "happy"),
        RecognizeQuestionUi("fallback-recognize-sad", "ÄÃ¢y lÃ  cáº£m xÃºc gÃ¬?", R.drawable.sad_1, "sad"),
        RecognizeQuestionUi("fallback-recognize-surprise", "ÄÃ¢y lÃ  cáº£m xÃºc gÃ¬?", R.drawable.surprise_1, "surprise"),
        RecognizeQuestionUi("fallback-recognize-angry", "ÄÃ¢y lÃ  cáº£m xÃºc gÃ¬?", R.drawable.angry_1, "angry"),
        RecognizeQuestionUi("fallback-recognize-fear", "ÄÃ¢y lÃ  cáº£m xÃºc gÃ¬?", R.drawable.fear_1, "fear"),
        RecognizeQuestionUi("fallback-recognize-disgust", "ÄÃ¢y lÃ  cáº£m xÃºc gÃ¬?", R.drawable.disgust_1, "disgust")
    )
}

private fun emotionImageResource(emotion: String): Int {
    return when (emotion) {
        "happy" -> R.drawable.happy_1
        "sad" -> R.drawable.sad_1
        "angry" -> R.drawable.angry_1
        "fear" -> R.drawable.fear_1
        "surprise" -> R.drawable.surprise_1
        "disgust" -> R.drawable.disgust_1
        else -> R.drawable.recognize_emotion
    }
}

