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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.garden.GardenRepository
import com.example.appmobile.data.repository.AssistantRepository
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgAssistantMascot
import com.example.appmobile.ui.components.EgVectorEmojiIcon
import com.example.appmobile.ui.components.egTactileClick
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

private data class AssistantMessage(
    val role: MessageRole,
    val text: String,
    val actions: List<ChatAction> = emptyList()
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
    level: Int? = null,
    screenContext: String? = null,
    onChatAction: (ChatAction) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { AssistantRepository(NetworkClient.apiService) }
    val gardenRepository = remember(context) { GardenRepository(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val gson = remember { Gson() }
    val contextId = remember(gameId, screenContext) {
        screenContext?.takeIf { it.isNotBlank() } ?: gameId
    }
    val gardenSummary = remember(contextId) { gardenRepository.getHomeSummary() }
    val chatContext = remember(contextId, level, gardenSummary) {
        AssistantKnowledge.contextFor(
            contextId = contextId,
            level = level,
            gardenSummary = gardenSummary
        )
    }
    val childId = remember(context) {
        AppSession.getBackendUserId(context)
            ?: FirebaseAuth.getInstance().currentUser?.uid
            ?: "local-player"
    }
    val historyKey = remember(childId, contextId, level) {
        "assistant_history_${childId}_${contextId}_${level ?: 0}"
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
                        text = AssistantKnowledge.welcome(chatContext)
                    )
                )
            }
        }
    }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
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
        scope.launch {
            sending = true
            runCatching {
                repository.uploadLog(childId = childId, sender = "child", content = text)
            }
            runCatching {
                val reply = AssistantKnowledge.reply(text, chatContext)
                messages.add(
                    AssistantMessage(
                        role = MessageRole.Assistant,
                        text = reply.text,
                        actions = reply.actions
                    )
                )
                persistMessages()
                runCatching {
                    repository.uploadLog(childId = childId, sender = "bot", content = reply.text)
                }
            }.onFailure {
                messages.add(
                    AssistantMessage(
                        role = MessageRole.Assistant,
                        text = "Mình đang gặp trục trặc nhỏ. Con có thể hỏi lại về học cảm xúc, báo cáo, Photobooth hoặc Vườn cảm xúc nhé."
                    )
                )
                persistMessages()
            }
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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói câu hỏi cho Mầm Mầm")
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
            addAssistantSystemMessage("Con cần cấp quyền micro để hỏi Mầm Mầm bằng giọng nói.")
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
        AssistantHeader(onBack = onBack, onClear = { showClearConfirm = true })

        AssistantIntroCard(chatContext = chatContext)

        SuggestionRow(
            suggestions = AssistantKnowledge.quickSuggestions(chatContext),
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
                AssistantBubble(message = message, onActionClick = onChatAction)
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

    if (showClearConfirm) {
        ClearChatConfirmDialog(
            onDismiss = { showClearConfirm = false },
            onConfirm = {
                showClearConfirm = false
                messages.clear()
                messages.add(AssistantMessage(MessageRole.Assistant, AssistantKnowledge.welcome(chatContext)))
                persistMessages()
            }
        )
    }
}

@Composable
private fun AssistantHeader(onBack: () -> Unit, onClear: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppBackButton(onClick = onBack, text = "← Quay lại")
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "Mầm Mầm",
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
private fun ClearChatConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { EgVectorEmojiIcon("trash", size = 26.dp, tint = EgDesign.primary) },
        title = {
            Text("Xóa cuộc trò chuyện?", color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Text(
                "Các tin nhắn hiện tại sẽ bị xóa khỏi màn hình.",
                color = EgDesign.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = EgDesign.textSecondary, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Xóa", color = Color(0xFFDC2626), fontWeight = FontWeight.ExtraBold)
            }
        },
        containerColor = EgDesign.card,
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
private fun AssistantIntroCard(chatContext: AppChatContext) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EgDesign.radiusXLarge),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .background(EgDesign.cardSoft)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(64.dp)
                    .widthIn(min = 64.dp)
                    .clip(CircleShape)
                    .background(EgDesign.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                EgAssistantMascot(size = 58.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Mầm Mầm",
                    color = EgDesign.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
                Text(
                    "Mình đang theo ngữ cảnh màn ${chatContext.currentScreenName}.",
                    color = EgDesign.blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    AssistantKnowledge.contextText(chatContext),
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
    suggestions: List<String>,
    enabled: Boolean,
    onSuggestionClick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion ->
            Surface(
                modifier = Modifier
                    .height(42.dp)
                    .widthIn(min = 116.dp)
                    .egTactileClick(enabled = enabled) { onSuggestionClick(suggestion) },
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
private fun AssistantBubble(
    message: AssistantMessage,
    onActionClick: (ChatAction) -> Unit
) {
    val isUser = message.role == MessageRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            AssistantMascotAvatar()
            Spacer(modifier = Modifier.size(8.dp))
        }
        Surface(
            modifier = Modifier.widthIn(max = if (isUser) 300.dp else 270.dp),
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
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = message.text,
                    color = if (isUser) Color.White else EgDesign.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                if (!isUser && message.actions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        message.actions.chunked(2).forEach { rowActions ->
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                rowActions.forEach { action ->
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .egTactileClick { onActionClick(action) },
                                        shape = RoundedCornerShape(EgDesign.pillRadius),
                                        color = EgDesign.cardSoft,
                                        border = BorderStroke(1.dp, EgDesign.cardBorder)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = action.label,
                                                color = EgDesign.blue,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                if (rowActions.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantTypingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        AssistantMascotAvatar(speaking = true)
        Spacer(modifier = Modifier.size(8.dp))
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
private fun AssistantMascotAvatar(speaking: Boolean = false) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFFFFF0B8), EgDesign.accentSoft)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        EgAssistantMascot(size = 38.dp, speaking = speaking)
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
            placeholder = {
                Text(
                    "Hỏi Mầm Mầm về cảm xúc hoặc cách chơi...",
                    color = EgDesign.textSecondary
                )
            },
            minLines = 1,
            maxLines = 3,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EgDesign.primary,
                unfocusedBorderColor = EgDesign.cardBorder,
                focusedContainerColor = EgDesign.card,
                unfocusedContainerColor = EgDesign.card,
                focusedTextColor = EgDesign.textPrimary,
                unfocusedTextColor = EgDesign.textPrimary,
                cursorColor = EgDesign.primary
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
                if (listening) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                } else {
                    EgVectorEmojiIcon("microphone", size = 20.dp, tint = EgDesign.blue)
                }
            }
        }
        Surface(
            modifier = Modifier
                .height(54.dp)
                .widthIn(min = 68.dp)
                .clickable(enabled = input.isNotBlank() && !sending) { onSend() },
            shape = RoundedCornerShape(EgDesign.pillRadius),
            color = if (input.isNotBlank() && !sending) EgDesign.primary else EgDesign.cardSoft,
            border = BorderStroke(1.dp, EgDesign.cardBorder),
            shadowElevation = if (input.isNotBlank() && !sending) 2.dp else 0.dp
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Gửi",
                    color = if (input.isNotBlank() && !sending) Color.White else EgDesign.textSecondary,
                    fontWeight = FontWeight.ExtraBold
                )
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

