package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GameStatChip(text: String) {
    Surface(shape = MaterialTheme.shapes.large, color = Color(0xFFE7F1FF)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color(0xFF1E4E8C),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun GameFeedbackCard(message: String) {
    val isCorrect = message.startsWith("Đúng")
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
fun GameLevelSummaryCard(summary: String, onBack: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Kết thúc level", fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
            Text(summary)
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Quay lại chọn level")
            }
        }
    }
}
