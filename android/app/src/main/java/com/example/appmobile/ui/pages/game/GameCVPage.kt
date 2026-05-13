package com.example.appmobile.ui.pages.game

import androidx.compose.runtime.Composable
import com.example.appmobile.R
import com.example.appmobile.ui.catalog.GameUiCatalog

@Composable
fun GameCVPage(level: Int = 1, onBack: () -> Unit, onOpenAssistant: () -> Unit = {}) {
    CvTrainingGamePage(
        gameId = GameUiCatalog.GAME_CV_STORY,
        level = level,
        title = "Câu chuyện khuôn mặt",
        subtitle = "Đọc tình huống, chụp biểu cảm và lưu kết quả luyện tập",
        imageRes = R.drawable.game_cv,
        defaultPrompt = GameUiCatalog.cvStoryPrompt,
        promptLabel = "Tình huống",
        targetLabel = "Cảm xúc cần đạt",
        onBack = onBack,
        onOpenAssistant = onOpenAssistant
    )
}
