package com.example.appmobile.ui.pages.game

import androidx.compose.runtime.Composable
import com.example.appmobile.R
import com.example.appmobile.ui.catalog.GameUiCatalog

@Composable
fun GameCV2Page(level: Int = 1, onBack: () -> Unit) {
    CvTrainingGamePage(
        gameId = GameUiCatalog.GAME_CV_REQUEST,
        level = level,
        title = "Thử thách cảm xúc",
        subtitle = "Chụp biểu cảm đúng theo yêu cầu và lưu kết quả luyện tập",
        imageRes = R.drawable.game_cv_2,
        defaultPrompt = GameUiCatalog.cvRequestPrompt,
        promptLabel = "Yêu cầu",
        targetLabel = "Cảm xúc",
        onBack = onBack
    )
}
