package com.example.appmobile.ui.pages.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appmobile.R
import com.example.appmobile.ui.components.molecules.EmotionCard
import com.example.appmobile.ui.components.molecules.GameCard
import com.example.appmobile.ui.theme.SoftWhite
import com.example.appmobile.ui.viewmodel.HomeRecentGameUi
import com.example.appmobile.ui.viewmodel.HomeViewModel

@Composable
fun HomePage(
    onLogout: () -> Unit,
    onNavigateToGame: (String) -> Unit,
    onNavigateToLearn: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    vm: HomeViewModel = viewModel()
) {
    val childName by vm.childName
    val emotions = vm.emotions
    val recentGames = vm.recentGames
    val loading by vm.isLoading
    val errorMessage by vm.errorMessage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Header: Chào bé
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Chào ${childName?.takeIf { it.isNotBlank() } ?: "bé yêu"}! 👋",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "Hôm nay bé cảm thấy thế nào?", fontSize = 16.sp, color = Color.Gray)
            }
            Row {
                TextButton(onClick = onNavigateToProfile) {
                    Text("Hồ sơ", color = Color(0xFF1976D2))
                }
                TextButton(onClick = onLogout) {
                    Text("Thoát", color = Color.Red)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        errorMessage?.let { message ->
            Surface(shape = MaterialTheme.shapes.large, color = Color(0xFFFFEBEE), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(message, color = Color(0xFFC62828), modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::refresh) { Text("Thử lại") }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Mục 1: Học tập
        Text(text = "KHÁM PHÁ", fontWeight = FontWeight.ExtraBold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        GameCard(
            title = "Thẻ Học Cảm Xúc",
            imageRes = R.drawable.logo_emo,
            backgroundColor = Color(0xFFE1F5FE),
            onClick = { onNavigateToLearn() }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Mục 2: Luyện tập
        Text(text = "LUYỆN TẬP", fontWeight = FontWeight.ExtraBold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        GameCard(
            title = "Gương Soi Thông Minh",
            imageRes = R.drawable.logo_emo,
            backgroundColor = Color(0xFFF1F8E9),
            onClick = { onNavigateToGame("camera_game") }
        )

        Spacer(modifier = Modifier.height(20.dp))

        GameCard(
            title = "Trò Chơi Nhận Diện",
            imageRes = R.drawable.logo_emo,
            backgroundColor = Color(0xFFFFF3E0),
            onClick = { onNavigateToGame("click_game") }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "Game gần đây", fontWeight = FontWeight.ExtraBold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (recentGames.isEmpty()) {
            EmptyHomeCard("Chưa có lịch sử chơi game. Bé hãy bắt đầu một màn luyện tập nhé.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recentGames.forEach { game ->
                    RecentGameRow(game)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "Độ chính xác cảm xúc", fontWeight = FontWeight.ExtraBold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (emotions.isEmpty()) {
            EmptyHomeCard("Chưa có thống kê cảm xúc. Dữ liệu sẽ hiện sau khi bé chơi vài câu hỏi.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(emotions) { e ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300)) +
                                slideInVertically(initialOffsetY = { 40 }, animationSpec = tween(300))
                    ) {
                        EmotionCard(
                            name = e.name,
                            accuracy = e.accuracy,
                            correct = e.correct,
                            incorrect = e.incorrect
                        )
                    }
                }
            }
        }

        

        // Báo cáo và tiến bộ
        Text(text = "Báo cáo và tiến bộ", fontWeight = FontWeight.ExtraBold, color = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))
        GameCard(
            title = "Báo cáo tiến bộ",
            imageRes = R.drawable.logo_emo,
            backgroundColor = Color(0xFFE8F5E9),
            onClick = onNavigateToReport
        )
    }
}

@Composable
private fun EmptyHomeCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            color = Color.Gray
        )
    }
}

@Composable
private fun RecentGameRow(game: HomeRecentGameUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(game.name, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E4E8C))
            Text(game.lastPlayed, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}
