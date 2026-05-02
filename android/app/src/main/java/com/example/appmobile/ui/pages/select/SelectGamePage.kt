package com.example.appmobile.ui.pages.select

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.appmobile.R
import com.example.appmobile.ui.components.GameScreenShell

private data class GameEntry(
    val id: String,
    val title: String,
    val description: String,
    val imageRes: Int,
    val type: String
)

@Composable
fun SelectGamePage(
    type: String, // "camera_game" hoặc "click_game"
    onBack: () -> Unit,
    onOpenLevel: (String) -> Unit
) {
    val allGames = listOf(
        // Game Nhận Biết (click_game)
        GameEntry("3bcb2108-721c-4a15-a585-31f3084ed000", "Chiếc hộp cảm xúc", "Chọn cảm xúc đúng qua hình ảnh", R.drawable.recognize_emotion, "click_game"),
        GameEntry("33ecafaa-ec7e-40d2-9c67-ed0a29ac0051", "Xưởng lắp ghép", "Ghép các bộ phận khuôn mặt", R.drawable.game_click_2, "click_game"),
        GameEntry("08bbffbf-d147-4556-bccb-b7621cafbf15", "Cảm xúc đúng chỗ", "Đặt cảm xúc vào ngữ cảnh", R.drawable.game_click_3, "click_game"),
        GameEntry("aacaf79e-e15e-42a9-a3d1-a522720d919b", "Thám tử cảm xúc", "Tìm kiếm cảm xúc ẩn giấu", R.drawable.game_click_4, "click_game"),
        
        // Game Biểu Cảm (camera_game)
        GameEntry("e05909f3-3dee-42a6-9a75-fd985b1bdf47", "Câu chuyện khuôn mặt", "Bắt chước biểu cảm nhân vật", R.drawable.game_cv, "camera_game"),
        GameEntry("61f5e09e-eefa-44c1-86e1-87dfceac3b8e", "Thử thách biểu cảm", "Thể hiện cảm xúc yêu cầu", R.drawable.game_cv_2, "camera_game"),
    )

    val filteredGames = allGames.filter { it.type == type }
    val pageTitle = if (type == "camera_game") "Gương Soi Thông Minh" else "Trò Chơi Nhận Diện"

    GameScreenShell(contentMaxWidth = 600) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.headlineSmall) }
                Text(pageTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                filteredGames.forEach { game ->
                    GameListItem(game = game, onOpenLevel = onOpenLevel)
                }
            }
        }
    }
}

@Composable
private fun GameListItem(game: GameEntry, onOpenLevel: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenLevel(game.id) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(70.dp), shape = MaterialTheme.shapes.large, color = Color(0xFFF0F7FF)) {
                Image(painter = painterResource(id = game.imageRes), contentDescription = null, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(game.title, fontWeight = FontWeight.Bold, color = Color(0xFF203864))
                Text(game.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text("▶", color = Color.LightGray)
        }
    }
}
