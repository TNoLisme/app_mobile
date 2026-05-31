package com.example.appmobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.ui.state.AppSettingsState
import com.example.appmobile.ui.state.UserAvatarState
import com.google.firebase.auth.FirebaseAuth

enum class EgTab(val title: String) {
    Home("Trang chủ"),
    Learn("Học"),
    Games("Chơi game")
}

object EgDesign {
    private val isDark: Boolean get() = AppSettingsState.activeDarkTheme.value
    val background: Color get() = if (isDark) Color(0xFF101820) else Color(0xFFEAF7FF)
    val primary: Color get() = if (isDark) Color(0xFF7CC8FF) else Color(0xFF62B5FF)
    val primaryDark: Color get() = if (isDark) Color(0xFF58B7FF) else Color(0xFF2F80ED)
    val primaryGradient: SolidColor get() = SolidColor(primary)
    val softBlueGradient: SolidColor get() = SolidColor(if (isDark) Color(0xFF182638) else Color(0xFFF4FAFF))
    val card: Color get() = if (isDark) Color(0xFF182638) else Color.White
    val cardSoft: Color get() = if (isDark) Color(0xFF20324A) else Color(0xFFF4FAFF)
    val cardBorder: Color get() = if (isDark) Color(0xFF36526D) else Color(0xFFD9E8F5)
    val textPrimary: Color get() = if (isDark) Color(0xFFEAF7FF) else Color(0xFF0B3A6E)
    val textSecondary: Color get() = if (isDark) Color(0xFFC2CBD7) else Color(0xFF6B7280)
    val blue: Color get() = if (isDark) Color(0xFF9DD4FF) else Color(0xFF0B5DAE)
    val accentSoft: Color get() = if (isDark) Color(0xFF1E344D) else Color(0xFFEAF2FF)
    val cardRadius = 16.dp
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
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    var navHeightPx by remember(density) { mutableIntStateOf(with(density) { 78.dp.roundToPx() }) }
    var horizontalDragPx by remember { mutableFloatStateOf(0f) }
    val navHeightDp = with(density) { navHeightPx.toDp() }
    val topActionReserveDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() } + 76.dp
    val horizontalPaddingPx = with(density) { (EgDesign.screenPadding * 2).toPx() }
    val tabsTrackWidthPx = with(density) {
        (configuration.screenWidthDp.dp.toPx() - horizontalPaddingPx)
            .coerceAtLeast(220.dp.toPx())
    }
    val tabSlotWidthPx = (tabsTrackWidthPx / 3f).coerceAtLeast(1f)
    val swipeCommitRatio = 0.55f
    val tabIndicatorPosition = mainTabIndicatorPosition(
        activeTab = activeTab,
        horizontalDragPx = horizontalDragPx,
        tabSlotWidthPx = tabSlotWidthPx
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EgDesign.background)
            .clipToBounds()
            .pointerInput(activeTab, tabSlotWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDragPx = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val bounds = mainTabDragBounds(activeTab, tabSlotWidthPx)
                        horizontalDragPx = (horizontalDragPx + dragAmount)
                            .coerceIn(bounds.start, bounds.endInclusive)
                    },
                    onDragEnd = {
                        val dragRatio = horizontalDragPx / tabSlotWidthPx
                        when {
                            dragRatio >= swipeCommitRatio -> {
                                handleMainSwipe(activeTab, fingerMovesRight = true, onHome, onLearn, onGames)
                            }
                            dragRatio <= -swipeCommitRatio -> {
                                handleMainSwipe(activeTab, fingerMovesRight = false, onHome, onLearn, onGames)
                            }
                        }
                        horizontalDragPx = 0f
                    },
                    onDragCancel = { horizontalDragPx = 0f }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = EgDesign.screenPadding)
                .padding(top = topActionReserveDp),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            horizontalAlignment = horizontalAlignment
        ) {
            content()
            Spacer(
                modifier = Modifier
                    .height(navHeightDp + 18.dp)
                    .navigationBarsPadding()
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = EgDesign.screenPadding, vertical = 8.dp)
        ) {
            EgTopActions(
                onProfile = onProfile,
                onSettings = onSettings,
                modifier = Modifier.fillMaxWidth()
            )
        }

        EgMainBottomNavSurface(
            activeTab = activeTab,
            onHome = onHome,
            onLearn = onLearn,
            onGames = onGames,
            indicatorPosition = tabIndicatorPosition,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { coordinates ->
                    val measuredHeight = coordinates.size.height
                    if (measuredHeight > 0 && measuredHeight != navHeightPx) {
                        navHeightPx = measuredHeight
                    }
                }
        )
    }
}

private fun mainTabDragBounds(
    activeTab: EgTab,
    tabSlotWidthPx: Float
): ClosedFloatingPointRange<Float> {
    return when (activeTab) {
        EgTab.Home -> -tabSlotWidthPx..0f
        EgTab.Learn -> -tabSlotWidthPx..tabSlotWidthPx
        EgTab.Games -> 0f..tabSlotWidthPx
    }
}

private fun mainTabIndicatorPosition(
    activeTab: EgTab,
    horizontalDragPx: Float,
    tabSlotWidthPx: Float
): Float {
    val baseIndex = activeTab.ordinal.toFloat()
    if (tabSlotWidthPx <= 0f) return baseIndex
    val dragDeltaInTabs = -horizontalDragPx / tabSlotWidthPx
    return (baseIndex + dragDeltaInTabs).coerceIn(0f, 2f)
}

private fun handleMainSwipe(
    activeTab: EgTab,
    fingerMovesRight: Boolean,
    onHome: () -> Unit,
    onLearn: () -> Unit,
    onGames: () -> Unit
) {
    when (activeTab) {
        EgTab.Home -> if (!fingerMovesRight) onLearn()
        EgTab.Learn -> if (fingerMovesRight) onHome() else onGames()
        EgTab.Games -> if (fingerMovesRight) onLearn()
    }
}

@Composable
private fun EgMainBottomNavSurface(
    activeTab: EgTab,
    onHome: () -> Unit,
    onLearn: () -> Unit,
    onGames: () -> Unit,
    indicatorPosition: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = EgDesign.card,
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = EgDesign.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            EgSegmentedTabs(
                activeTab = activeTab,
                onHome = onHome,
                onLearn = onLearn,
                onGames = onGames,
                indicatorPosition = indicatorPosition
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
            EgIconButton(icon = "settings", onClick = it)
        }
    }
}

@Composable
fun EgSegmentedTabs(
    activeTab: EgTab,
    onHome: () -> Unit,
    onLearn: () -> Unit,
    onGames: () -> Unit,
    indicatorPosition: Float,
    modifier: Modifier = Modifier
) {
    val animatedIndicatorPosition by animateFloatAsState(
        targetValue = indicatorPosition,
        animationSpec = tween(durationMillis = 160),
        label = "main_tab_indicator"
    )
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EgTabButton(EgTab.Home, activeTab, onHome, Modifier.weight(1f))
            EgTabButton(EgTab.Learn, activeTab, onLearn, Modifier.weight(1f))
            EgTabButton(EgTab.Games, activeTab, onGames, Modifier.weight(1f))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(bottom = 1.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val segmentWidth = maxWidth / 3f
                val lineWidth = segmentWidth * 0.58f
                val leftPadding = (segmentWidth - lineWidth) / 2f
                Box(
                    modifier = Modifier
                        .offset(x = (segmentWidth * animatedIndicatorPosition) + leftPadding)
                        .height(4.dp)
                        .width(lineWidth)
                        .clip(RoundedCornerShape(999.dp))
                        .background(EgDesign.primaryDark)
                )
            }
        }
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
        shadowElevation = 1.dp
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
                EgVectorEmojiIcon("user", size = 31.dp)
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
            EgVectorEmojiIcon(icon, size = 31.dp)
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
    val color = if (tab == activeTab) EgDesign.primaryDark else EgDesign.textSecondary
    Column(
        modifier = modifier
            .height(54.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            EgMainTabIcon(tab = tab, color = color, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun EgMainTabIcon(tab: EgTab, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = (w * 0.09f).coerceAtLeast(2.2f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        when (tab) {
            EgTab.Home -> {
                val roof = Path().apply {
                    moveTo(w * 0.16f, h * 0.48f)
                    lineTo(w * 0.50f, h * 0.18f)
                    lineTo(w * 0.84f, h * 0.48f)
                }
                drawPath(roof, color = color, style = stroke)
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.44f),
                    size = androidx.compose.ui.geometry.Size(w * 0.50f, h * 0.38f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f),
                    style = stroke
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.82f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.64f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
            }
            EgTab.Learn -> {
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.16f, h * 0.22f),
                    size = androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.58f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f),
                    style = stroke
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.25f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.78f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.38f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.38f),
                    strokeWidth = stroke.width * 0.72f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(w * 0.58f, h * 0.38f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.38f),
                    strokeWidth = stroke.width * 0.72f,
                    cap = StrokeCap.Round
                )
            }
            EgTab.Games -> {
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.14f, h * 0.34f),
                    size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.34f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f, w * 0.16f),
                    style = stroke
                )
                drawCircle(
                    color = color,
                    radius = w * 0.045f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.62f, h * 0.48f)
                )
                drawCircle(
                    color = color,
                    radius = w * 0.045f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.74f, h * 0.54f)
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(w * 0.28f, h * 0.51f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.51f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.44f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.58f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
