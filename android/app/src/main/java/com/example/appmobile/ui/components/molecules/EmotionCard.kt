package com.example.appmobile.ui.components.molecules

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EmotionCard(
    name: String, 
    modifier: Modifier = Modifier, 
    accuracy: Double? = null, 
    correct: Int? = null, 
    incorrect: Int? = null
) {
    Card(
        modifier = modifier.width(180.dp).height(120.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = name, 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E4E8C)
            )
            Spacer(modifier = Modifier.height(6.dp))

            val accText = accuracy?.let { String.format("%.1f", it) + "%" } ?: "-"
            Text(
                text = "Độ chính xác: $accText", 
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Đúng: ${correct ?: "-"}", fontSize = 12.sp, color = Color(0xFF4CAF50))
                Text(text = "Sai: ${incorrect ?: "-"}", fontSize = 12.sp, color = Color(0xFFF44336))
            }
        }
    }
}
