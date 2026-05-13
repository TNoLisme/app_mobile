package com.example.appmobile.ui.pages.assistant

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.AssistantChatHistoryDto
import com.example.appmobile.data.repository.AssistantRepository
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.components.EgDesign
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

private data class AssistantMessage(
    val role: MessageRole,
    val text: String
)

private enum class MessageRole {
    User,
    Assistant
}

private data class StoredAssistantMessage(
    val role: String,
    val text: String
)

@Composable
fun AssistantPage(
    onBack: () -> Unit,
    gameId: String = "home",
    level: Int? = null
) {
    val context = LocalContext.current
    val repository = remember { AssistantRepository(NetworkClient.apiService) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val gson = remember { Gson() }
    val childId = remember(context) {
        AppSession.getBackendUserId(context)
            ?: FirebaseAuth.getInstance().currentUser?.uid
            ?: "local-player"
    }
    val historyKey = remember(childId, gameId, level) {
        "assistant_history_${childId}_${gameId}_${level ?: 0}"
    }
    val preferences = remember(context) {
        context.getSharedPreferences("assistant_chat", Context.MODE_PRIVATE)
    }
    val messages = remember(historyKey) {
        mutableStateListOf<AssistantMessage>().apply {
            addAll(loadStoredMessages(preferences, gson, historyKey))
            if (isEmpty()) {
                add(
                    AssistantMessage(
                        role = MessageRole.Assistant,
                        text = welcomeMessage(gameId, level)
                    )
                )
            }
        }
    }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    fun persistMessages() {
        saveStoredMessages(preferences, gson, historyKey, messages)
    }

    fun sendMessage(rawText: String = input) {
        val text = rawText.trim()
        if (text.isEmpty() || sending) return

        input = ""
        messages.add(AssistantMessage(MessageRole.User, text))
        persistMessages()
        val requestHistory = messages
            .takeLast(10)
            .map {
                AssistantChatHistoryDto(
                    role = if (it.role == MessageRole.User) "user" else "assistant",
                    text = it.text
                )
            }

        scope.launch {
            sending = true
            repository.uploadLog(childId = childId, sender = "child", content = text)
            val reply = repository.chat(
                gameId = gameId,
                level = level,
                message = text,
                childId = childId,
                history = requestHistory
            )
            messages.add(AssistantMessage(MessageRole.Assistant, reply))
            persistMessages()
            repository.uploadLog(childId = childId, sender = "bot", content = reply)
            sending = false
        }
    }

    fun addAssistantSystemMessage(text: String) {
        messages.add(AssistantMessage(MessageRole.Assistant, text))
        persistMessages()
    }

    fun speechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói câu hỏi cho trợ lý EmoGarden")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    val systemVoiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            listening = false
            return@rememberLauncherForActivityResult
        }
        listening = false
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (spokenText.isNotBlank()) {
            sendMessage(spokenText)
        } else {
            addAssistantSystemMessage("Mình chưa nghe rõ. Con thử bấm mic và nói lại chậm hơn nhé.")
        }
    }

    fun openSystemVoiceInput() {
        runCatching {
            listening = true
            systemVoiceLauncher.launch(speechIntent())
        }.onFailure { error ->
            listening = false
            if (error is ActivityNotFoundException) {
                addAssistantSystemMessage("Máy này chưa có dịch vụ nhận diện giọng nói. Con có thể cài Google Speech Services hoặc gõ câu hỏi nhé.")
            } else {
                addAssistantSystemMessage("Không mở được micro. Con kiểm tra quyền micro rồi thử lại nhé.")
            }
        }
    }

    fun startVoiceRecognition() {
        if (sending || listening) return
        if (speechRecognizer == null) {
            openSystemVoiceInput()
            return
        }
        runCatching {
            listening = true
            speechRecognizer.startListening(speechIntent())
        }.onFailure {
            listening = false
            addAssistantSystemMessage("Không mở được micro. Con kiểm tra quyền micro rồi thử lại nhé.")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecognition()
        } else {
            addAssistantSystemMessage("Con cần cấp quyền micro để hỏi trợ lý bằng giọng nói.")
        }
    }

    val sendVoiceResult by rememberUpdatedState<(String) -> Unit> { spokenText ->
        sendMessage(spokenText)
    }
    val addVoiceMessage by rememberUpdatedState<(String) -> Unit> { text ->
        addAssistantSystemMessage(text)
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                listening = false
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Mình chưa nghe rõ. Con thử nói chậm hơn nhé."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Mình chưa nghe thấy câu hỏi. Con bấm mic rồi nói lại nhé."
                    SpeechRecognizer.ERROR_AUDIO -> "Micro đang gặp lỗi. Con thử lại hoặc gõ câu hỏi nhé."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Con cần cấp quyền micro để hỏi bằng giọng nói."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Nhận diện giọng nói cần mạng ổn định. Con thử lại hoặc gõ câu hỏi nhé."
                    else -> "Chưa nhận diện được giọng nói. Con thử lại hoặc gõ câu hỏi nhé."
                }
                addVoiceMessage(message)
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val spokenText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (spokenText.isNotBlank()) {
                    sendVoiceResult(spokenText)
                } else {
                    addVoiceMessage("Mình chưa nghe rõ. Con thử bấm mic và nói lại chậm hơn nhé.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            runCatching { speechRecognizer?.cancel() }
            runCatching { speechRecognizer?.destroy() }
        }
    }

    fun openVoiceInput() {
        if (sending || listening) return
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startVoiceRecognition()
    }

    LaunchedEffect(messages.size, sending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EgDesign.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = EgDesign.screenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AssistantHeader(onBack = onBack, onClear = {
            messages.clear()
            messages.add(AssistantMessage(MessageRole.Assistant, welcomeMessage(gameId, level)))
            persistMessages()
        })

        AssistantIntroCard(gameId = gameId, level = level)

        SuggestionRow(
            gameId = gameId,
            enabled = !sending,
            onSuggestionClick = { suggestion -> sendMessage(suggestion) }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { message ->
                AssistantBubble(message)
            }
            if (sending) {
                item { AssistantTypingBubble() }
            }
        }

        AssistantInputRow(
            input = input,
            sending = sending,
            listening = listening,
            onInputChange = { input = it },
            onSend = { sendMessage() },
            onVoiceInput = { openVoiceInput() }
        )
    }
}

@Composable
private fun AssistantHeader(onBack: () -> Unit, onClear: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppBackButton(onClick = onBack, text = "← Quay lại")
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "Trợ lý",
            color = EgDesign.textPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp
        )
        Spacer(modifier = Modifier.weight(1f))
        Surface(
            modifier = Modifier
                .height(38.dp)
                .clickable(onClick = onClear),
            shape = RoundedCornerShape(EgDesign.pillRadius),
            color = EgDesign.card,
            border = BorderStroke(1.dp, EgDesign.cardBorder),
            shadowElevation = 1.dp
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text("Xóa", color = EgDesign.blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AssistantIntroCard(gameId: String, level: Int?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(46.dp)
                    .widthIn(min = 46.dp)
                    .clip(CircleShape)
                    .background(EgDesign.cardSoft),
                contentAlignment = Alignment.Center
            ) {
                Text("💬", fontSize = 24.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "Trợ lý EmoGarden",
                    color = EgDesign.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
                Text(
                    assistantContextText(gameId, level),
                    color = EgDesign.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    gameId: String,
    enabled: Boolean,
    onSuggestionClick: (String) -> Unit
) {
    val suggestions = quickSuggestions(gameId)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.take(3).forEach { suggestion ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clickable(enabled = enabled) { onSuggestionClick(suggestion) },
                shape = RoundedCornerShape(EgDesign.pillRadius),
                color = EgDesign.cardSoft,
                border = BorderStroke(1.dp, EgDesign.cardBorder)
            ) {
                Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        suggestion,
                        color = EgDesign.blue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantBubble(message: AssistantMessage) {
    val isUser = message.role == MessageRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 5.dp,
                bottomEnd = if (isUser) 5.dp else 18.dp
            ),
            color = if (isUser) EgDesign.primary else EgDesign.card,
            border = if (isUser) null else BorderStroke(1.dp, EgDesign.cardBorder),
            shadowElevation = 1.dp
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(14.dp),
                color = if (isUser) Color.White else EgDesign.textPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun AssistantTypingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = EgDesign.card,
            border = BorderStroke(1.dp, EgDesign.cardBorder),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.8.dp,
                    color = EgDesign.primary
                )
                Text("Đang trả lời...", color = EgDesign.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AssistantInputRow(
    input: String,
    sending: Boolean,
    listening: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceInput: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Hỏi cách chơi hoặc gợi ý cảm xúc...") },
            minLines = 1,
            maxLines = 3,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EgDesign.primary,
                unfocusedBorderColor = EgDesign.cardBorder,
                focusedContainerColor = EgDesign.card,
                unfocusedContainerColor = EgDesign.card
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() })
        )
        Surface(
            modifier = Modifier
                .height(54.dp)
                .widthIn(min = 54.dp)
                .clickable(enabled = !sending && !listening) { onVoiceInput() },
            shape = CircleShape,
            color = if (listening) EgDesign.primary else EgDesign.card,
            border = BorderStroke(1.dp, EgDesign.cardBorder),
            shadowElevation = 1.dp
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(if (listening) "●" else "🎙️", color = if (listening) Color.White else EgDesign.blue, fontSize = 20.sp)
            }
        }
        Surface(
            modifier = Modifier
                .height(54.dp)
                .widthIn(min = 68.dp)
                .clickable(enabled = input.isNotBlank() && !sending) { onSend() },
            shape = RoundedCornerShape(EgDesign.pillRadius),
            color = if (input.isNotBlank() && !sending) EgDesign.primary else Color(0xFFD8E6F3),
            shadowElevation = if (input.isNotBlank() && !sending) 2.dp else 0.dp
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text("Gửi", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun loadStoredMessages(
    preferences: android.content.SharedPreferences,
    gson: Gson,
    key: String
): List<AssistantMessage> {
    val raw = preferences.getString(key, null) ?: return emptyList()
    return runCatching {
        val type = object : TypeToken<List<StoredAssistantMessage>>() {}.type
        val stored = gson.fromJson<List<StoredAssistantMessage>>(raw, type) ?: emptyList()
        stored.takeLast(80).mapNotNull { item ->
            val role = if (item.role == "user") MessageRole.User else MessageRole.Assistant
            item.text.takeIf { it.isNotBlank() }?.let { AssistantMessage(role, it) }
        }
    }.getOrDefault(emptyList())
}

private fun saveStoredMessages(
    preferences: android.content.SharedPreferences,
    gson: Gson,
    key: String,
    messages: List<AssistantMessage>
) {
    val stored = messages.takeLast(80).map {
        StoredAssistantMessage(
            role = if (it.role == MessageRole.User) "user" else "assistant",
            text = it.text
        )
    }
    preferences.edit().putString(key, gson.toJson(stored)).apply()
}

private fun welcomeMessage(gameId: String, level: Int?): String {
    val context = when (gameId) {
        "gameCV" -> "màn Câu chuyện khuôn mặt"
        "game_cv_2" -> "màn Thử thách cảm xúc"
        "learn" -> "màn Học"
        "select_game" -> "màn Chơi game"
        "level_select" -> "màn Chọn cấp độ"
        else -> "màn hiện tại"
    }
    val suffix = if (level != null) " ở cấp độ $level" else ""
    return "Chào bé! Mình là trợ lý EmoGarden. Con có thể hỏi cách chơi, gợi ý biểu cảm hoặc nhờ mình giải thích $context$suffix."
}

private fun assistantContextText(gameId: String, level: Int?): String {
    val levelText = if (level != null) " Cấp độ $level." else ""
    return when (gameId) {
        "home" -> "Hỏi mình nên học cảm xúc nào hoặc nên chơi game gì hôm nay."
        "learn" -> "Mình có thể giải thích dấu hiệu nhận biết từng cảm xúc."
        "select_game" -> "Mình có thể gợi ý game phù hợp với bé."
        "level_select" -> "Mình có thể giải thích cách chọn cấp độ.$levelText"
        "gameCV" -> "Mình có thể nhắc cách đoán tình huống và đứng trước camera.$levelText"
        "game_cv_2" -> "Mình có thể gợi ý cách thể hiện cảm xúc qua khuôn mặt.$levelText"
        "recognize_emotion" -> "Mình có thể nhắc cách chọn cảm xúc đúng.$levelText"
        else -> "Mình sẽ trả lời theo màn bé đang mở.$levelText"
    }
}

private fun quickSuggestions(gameId: String): List<String> {
    return when (gameId) {
        "learn" -> listOf("Nhận biết vui vẻ", "Nhận biết buồn bã", "Học gì trước?")
        "select_game" -> listOf("Game nào dễ?", "Nên chơi gì?", "Game camera?")
        "level_select" -> listOf("Chọn cấp nào?", "Sao bị khóa?", "Cách mở cấp")
        "gameCV" -> listOf("Cách chơi?", "Đoán tình huống?", "Camera không bật")
        "game_cv_2" -> listOf("Cách chơi?", "Gợi ý vui vẻ", "Camera không bật")
        else -> listOf("Con nên chơi gì?", "Gợi ý cảm xúc", "Cách dùng app")
    }
}
