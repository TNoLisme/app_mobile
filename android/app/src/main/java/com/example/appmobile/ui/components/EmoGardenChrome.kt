package com.example.appmobile.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class EmoGardenNavItem(val title: String) {
    Home("Trang chủ"),
    Learn("Học"),
    Games("Chơi game")
}

val EmoGardenBackground = SolidColor(Color(0xFFEAF7FF))

val EmoGardenButtonGradient = SolidColor(Color(0xFF62B5FF))

@Composable
fun EmoGardenTopNav(
    activeItem: EmoGardenNavItem,
    onHome: () -> Unit,
    onLearn: () -> Unit,
    onGames: () -> Unit,
    onProfile: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.98f),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                onProfile?.let {
                    RoundNavAction(text = "👤", onClick = it)
                }
                Spacer(modifier = Modifier.weight(1f))
                onSettings?.let {
                    RoundNavAction(text = "⚙", onClick = it)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavPill(
                    item = EmoGardenNavItem.Home,
                    activeItem = activeItem,
                    onClick = onHome,
                    modifier = Modifier.weight(1f)
                )
                NavPill(
                    item = EmoGardenNavItem.Learn,
                    activeItem = activeItem,
                    onClick = onLearn,
                    modifier = Modifier.weight(1f)
                )
                NavPill(
                    item = EmoGardenNavItem.Games,
                    activeItem = activeItem,
                    onClick = onGames,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RoundNavAction(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color(0xFFEAF7FF),
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, fontSize = 19.sp)
        }
    }
}

@Composable
private fun NavPill(
    item: EmoGardenNavItem,
    activeItem: EmoGardenNavItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = item == activeItem
    val background = if (active) Color(0xFF62B5FF) else Color(0xFFF8FCFF)
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        shadowElevation = if (active) 1.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(background, CircleShape)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.title,
                color = if (active) Color.White else Color(0xFF0B3C7D),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GradientActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .background(EmoGardenButtonGradient, CircleShape)
                .padding(horizontal = 18.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}

@Composable
fun AssistantChatBubble(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .size(58.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.75f)),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EmoGardenButtonGradient, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("💬", fontSize = 28.sp)
        }
    }
}
