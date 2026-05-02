package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.appmobile.R
import com.example.appmobile.ui.components.GameScreenShell

@Composable
fun GameClick2Page(onBack: () -> Unit) {
    val selectedEyebrow = remember { mutableStateOf("(chưa chọn)") }
    val selectedEyes = remember { mutableStateOf("(chưa chọn)") }
    val selectedMouth = remember { mutableStateOf("(chưa chọn)") }

    GameScreenShell(contentMaxWidth = 900) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Quay lại") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Xưởng lắp ghép", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            BoxWithConstraints {
                val isMobile = maxWidth < 750.dp
                if (isMobile) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        PreviewCard()
                        AssemblyControls(selectedEyebrow, selectedEyes, selectedMouth)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(1f)) { PreviewCard() }
                        Box(modifier = Modifier.weight(1f)) { AssemblyControls(selectedEyebrow, selectedEyes, selectedMouth) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard() {
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painter = painterResource(id = R.drawable.game_click_2), contentDescription = null, modifier = Modifier.height(180.dp), contentScale = ContentScale.Fit)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Hãy chọn bộ phận phù hợp", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AssemblyControls(eyebrow: androidx.compose.runtime.MutableState<String>, eyes: androidx.compose.runtime.MutableState<String>, mouth: androidx.compose.runtime.MutableState<String>) {
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ControlItem("👁️ Lông mày", eyebrow) { eyebrow.value = "Vui" }
            ControlItem("👀 Mắt", eyes) { eyes.value = "To" }
            ControlItem("👄 Miệng", mouth) { mouth.value = "Cười" }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Kiểm tra ✓") }
        }
    }
}

@Composable
private fun ControlItem(title: String, state: androidx.compose.runtime.MutableState<String>, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text(state.value, fontWeight = FontWeight.Bold)
        }
    }
}
