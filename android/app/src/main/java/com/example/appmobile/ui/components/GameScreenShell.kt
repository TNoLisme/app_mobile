package com.example.appmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GameScreenShell(
    modifier: Modifier = Modifier,
    contentMaxWidth: Int = 1120,
    onOpenAssistant: (() -> Unit)? = null,
    scrollEnabled: Boolean = true,
    bottomSpacerHeight: Dp = 96.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EgDesign.background)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = contentMaxWidth.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(12.dp),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 2.dp,
                shadowElevation = 1.dp,
                color = EgDesign.card
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (scrollEnabled) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            }
                        )
                        .padding(16.dp)
                ) {
                    content()
                    if (bottomSpacerHeight > 0.dp) {
                        Spacer(modifier = Modifier.height(bottomSpacerHeight))
                    }
                }
            }
        }
        onOpenAssistant?.let { openAssistant ->
            EgGradientPill(
                text = "Trợ lý",
                onClick = openAssistant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
                height = 38.dp,
                fontSize = 13
            )
        }
    }
}
