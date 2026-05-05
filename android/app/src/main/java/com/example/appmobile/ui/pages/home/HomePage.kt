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
import com.example.appmobile.ui.viewmodel.HomeViewModel

@Composable
fun HomePage(
    onLogout: () -> Unit,
    onNavigateToGame: (String) -> Unit,
    onNavigateToLearn: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    vm: HomeViewModel = viewModel()
) {
    val emotions = vm.emotions
    val loading by vm.isLoading

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
                Text(text = "Chào bé yêu! 👋", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(text = "Hôm nay bé cảm thấy thế nào?", fontSize = 16.sp, color = Color.Gray)
            }
            // Nút đăng xuất nhỏ ở góc
            TextButton(onClick = onLogout) {
                Text("Thoát", color = Color.Red)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

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

        // Emotion cards
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
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(emotions) { e ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300)) +
                                slideInVertically(initialOffsetY = { 40 }, animationSpec = tween(300))
                    ) {
                        EmotionCard(name = e)
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
