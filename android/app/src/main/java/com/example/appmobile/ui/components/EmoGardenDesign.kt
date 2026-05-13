package com.example.appmobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.ui.state.UserAvatarState
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.roundToInt

enum class EgTab(val title: String) {
    Home("Trang chủ"),
    Learn("Học"),
    Games("Chơi game")
}

object EgDesign {
    val background = Color(0xFFEAF7FF)
    val primary = Color(0xFF62B5FF)
    val primaryDark = Color(0xFF2F80ED)
    val primaryGradient = SolidColor(primary)
    val softBlueGradient = SolidColor(Color(0xFFF4FAFF))
    val card = Color.White
    val cardSoft = Color(0xFFF4FAFF)
    val cardBorder = Color(0xFFD9E8F5)
    val textPrimary = Color(0xFF0B3A6E)
    val textSecondary = Color(0xFF6B7280)
    val blue = Color(0xFF0B5DAE)
    val accentSoft = Color(0xFFEAF2FF)
    val cardRadius = 18.dp
    val pillRadius = 999.dp
    val screenPadding = 16.dp
}

@Composable
fun EgScreenColumn(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EgDesign.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = EgDesign.screenPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}

@Composable
fun EgCollapsibleMainScaffold(
    activeTab: EgTab,
    onHome: () -> Unit,
    onLearn: () -> Unit,
    onGames: () -> Unit,
    onProfile: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var navHeightPx by remember(density) { mutableIntStateOf(with(density) { 128.dp.roundToPx() }) }
    var navOffsetPx by remember { mutableFloatStateOf(0f) }
    var horizontalDragPx by remember { mutableFloatStateOf(0f) }
    val navHeightDp = with(density) { navHeightPx.toDp() }
    val swipeThresholdPx = with(density) { 72.dp.toPx() }
    val navProgress = if (navHeightPx == 0) {
        1f
    } else {
        (1f + navOffsetPx / navHeightPx.toFloat()).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember(navHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (scrollState.maxValue <= 0) {
                    navOffsetPx = 0f
                    return Offset.Zero
                }
                if (navHeightPx <= 0) return Offset.Zero
                val nextOffset = (navOffsetPx + available.y)
                    .coerceIn(-navHeightPx.toFloat(), 0f)
                navOffsetPx = nextOffset
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(activeTab) {
        navOffsetPx = 0f
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value to scrollState.maxValue }.collect { (currentScroll, maxScroll) ->
            if (maxScroll <= 0 || currentScroll <= 0) {
                navOffsetPx = 0f
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EgDesign.background)
            .clipToBounds()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .pointerInput(activeTab, swipeThresholdPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDragPx = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            horizontalDragPx += dragAmount
                        },
                        onDragEnd = {
                            when {
                                horizontalDragPx > swipeThresholdPx -> {
                                    navOffsetPx = 0f
                                    handleMainSwipe(activeTab, toRight = true, onHome, onLearn, onGames)
                                }
                                horizontalDragPx < -swipeThresholdPx -> {
                                    navOffsetPx = 0f
                                    handleMainSwipe(activeTab, toRight = false, onHome, onLearn, onGames)
                                }
                            }
                            horizontalDragPx = 0f
                        },
                        onDragCancel = { horizontalDragPx = 0f }
                    )
                }
                .verticalScroll(scrollState)
                .padding(horizontal = EgDesign.screenPadding)
                .padding(top = navHeightDp + 10.dp),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            horizontalAlignment = horizontalAlignment
        ) {
            content()
            Spacer(
                modifier = Modifier
                    .height(96.dp)
                    .navigationBarsPadding()
            )
        }

        EgMainTopNavSurface(
            activeTab = activeTab,
            onHome = onHome,
            onLearn = onLearn,
            onGames = onGames,
            onProfile = onProfile,
            onSettings = onSettings,
            progress = navProgress,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, navOffsetPx.roundToInt()) }
                .onGloballyPositioned { coordinates ->
                    val measuredHeight = coordinates.size.height
                    if (measuredHeight > 0 && measuredHeight != navHeightPx) {
                        navHeightPx = measuredHeight
                        navOffsetPx = navOffsetPx.coerceIn(-measuredHeight.toFloat(), 0f)
                    }
                }
        )
    }
}

private fun handleMainSwipe(
    activeTab: EgTab,
    toRight: Boolean,
    onHome: () -> Unit,
    onLearn: () -> Unit,
    onGames: () -> Unit
) {
    when (activeTab) {
        EgTab.Home -> if (toRight) onLearn()
        EgTab.Learn -> if (toRight) onGames() else onHome()
        EgTab.Games -> if (!toRight) onLearn()
    }
}

@Composable
private fun EgMainTopNavSurface(
    activeTab: EgTab,
    onHome: () -> Unit,
    onLearn: () -> Unit,
    onGames: () -> Unit,
    onProfile: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = progress },
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
        color = EgDesign.card,
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = if (progress > 0.05f) 3.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = EgDesign.screenPadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EgTopActions(onProfile = onProfile, onSettings = onSettings)
            EgSegmentedTabs(
                activeTab = activeTab,
                onHome = onHome,
                onLearn = onLearn,
                onGames = onGames
            )
        }
    }
}

@Composable
fun EgTopActions(
    onProfile: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.uid
        ?: AppSession.currentBackendUserId()
        ?: "local-player"
    val avatarUri = UserAvatarState.avatarUri.value

    LaunchedEffect(userId) {
        UserAvatarState.load(context, userId)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        onProfile?.let {
            EgProfileAvatarButton(avatarUri = avatarUri, onClick = it)
        }
        Spacer(modifier = Modifier.weight(1f))
        onSettings?.let {
            EgIconButton(icon = "⚙️", onClick = it)
        }
    }
}

@Composable
fun EgSegmentedTabs(
    activeTab: EgTab,
    onHome: () -> Unit,
    onLearn: () -> Unit,
    onGames: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EgTabButton(EgTab.Home, activeTab, onHome, Modifier.weight(1f))
        EgTabButton(EgTab.Learn, activeTab, onLearn, Modifier.weight(1f))
        EgTabButton(EgTab.Games, activeTab, onGames, Modifier.weight(1f))
    }
}

@Composable
fun EgHeroCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    EgSoftCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = title,
                color = EgDesign.blue,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.56f)
                    .height(3.dp)
                    .background(EgDesign.primary, CircleShape)
            )
            Text(
                text = description,
                color = EgDesign.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun EgSoftCard(
    modifier: Modifier = Modifier,
    radius: Dp = EgDesign.cardRadius,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        content()
    }
}

@Composable
fun EgGradientPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 38.dp,
    fontSize: Int = 12
) {
    Surface(
        modifier = modifier
            .height(height)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(EgDesign.pillRadius),
        color = Color.Transparent,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .background(EgDesign.primary)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AppBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "← Quay lại"
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(EgDesign.pillRadius),
        color = EgDesign.card,
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = 1.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = EgDesign.blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EgProfileAvatarButton(avatarUri: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = EgDesign.card,
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = 1.dp
    ) {
        if (!avatarUri.isNullOrBlank()) {
            AsyncImage(
                model = avatarUri,
                contentDescription = "Hồ sơ",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text("👤", fontSize = 19.sp)
            }
        }
    }
}

@Composable
private fun EgIconButton(icon: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = EgDesign.card,
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 19.sp)
        }
    }
}

@Composable
private fun EgTabButton(
    tab: EgTab,
    activeTab: EgTab,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = tab == activeTab
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(EgDesign.pillRadius),
        color = Color.Transparent,
        border = if (active) null else BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = if (active) 1.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(if (active) EgDesign.primary else EgDesign.cardSoft)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tab.title,
                color = if (active) Color.White else EgDesign.blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
