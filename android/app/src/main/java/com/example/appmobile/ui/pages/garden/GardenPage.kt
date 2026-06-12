package com.example.appmobile.ui.pages.garden

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appmobile.data.garden.EmotionPlant
import com.example.appmobile.data.garden.GardenInventory
import com.example.appmobile.data.garden.GardenRepository
import com.example.appmobile.data.garden.GardenTask
import com.example.appmobile.data.garden.GardenTaskStatus
import com.example.appmobile.data.garden.GardenTaskType
import com.example.appmobile.data.garden.GardenUiState
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgEmotionVectorIcon
import com.example.appmobile.ui.components.EgSoftCard
import com.example.appmobile.ui.components.EgVectorEmojiIcon
import com.example.appmobile.ui.components.egTactileClick
import kotlin.math.min

@Composable
fun GardenPage(
    onBack: () -> Unit,
    onLearnEmotion: (String) -> Unit,
    onOpenGames: () -> Unit,
    onOpenPhotoBooth: () -> Unit,
    onOpenReport: () -> Unit,
    onStartEmotionChallenge: (String) -> Unit,
    vm: GardenViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by vm.state.collectAsState()
    var showTasks by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EgDesign.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppBackButton(onClick = onBack)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Vườn cảm xúc",
                    color = EgDesign.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Chăm sóc cảm xúc mỗi ngày để vườn luôn tươi tốt nhé!",
                    color = EgDesign.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        if (state.isLoading) {
            LoadingCard()
        } else {
            GardenTopCard(state = state)
            PlantGardenGrid(
                plants = state.plants,
                modifier = Modifier.weight(1f),
                onOpenPlant = vm::openPlant
            )
            GardenTaskLauncher(state = state, onOpenTasks = { showTasks = true })
        }
        }

        if (!state.isLoading) {
            state.message?.let { msg ->
                LaunchedEffect(msg) {
                    delay(3000)
                    vm.clearMessage()
                }
                GardenMessageCard(
                    message = msg,
                    onDismiss = vm::clearMessage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 86.dp)
                )
            }
        }
    }

    if (showTasks) {
        GardenTaskPanel(
            state = state,
            onDismiss = { showTasks = false },
            onTaskAction = { task ->
                handleTaskAction(
                    task = task,
                    vm = vm,
                    onLearnEmotion = { onLearnEmotion(state.plants.firstOrNull()?.emotionId ?: "happy") },
                    onOpenGames = onOpenGames,
                    onOpenPhotoBooth = onOpenPhotoBooth,
                    onOpenReport = onOpenReport,
                    onStartEmotionChallenge = { onStartEmotionChallenge(state.plants.firstOrNull()?.emotionId ?: "happy") }
                )
            },
            onClearMessage = vm::clearMessage
        )
    }

    val selectedPlant = state.selectedPlantId?.let { emotionId ->
        state.plants.firstOrNull { it.emotionId == emotionId }
    }
    if (selectedPlant != null) {
        PlantDetailDialog(
            plant = selectedPlant,
            inventory = state.inventory,
            busy = state.isBusy,
            onDismiss = vm::closePlant,
            onWater = { vm.waterPlant(selectedPlant.emotionId) },
            onSun = { vm.sunPlant(selectedPlant.emotionId) }
        )
    }
}

private fun handleTaskAction(
    task: GardenTask,
    vm: GardenViewModel,
    onLearnEmotion: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenPhotoBooth: () -> Unit,
    onOpenReport: () -> Unit,
    onStartEmotionChallenge: () -> Unit
) {
    when (task.status) {
        GardenTaskStatus.COMPLETED_NOT_CLAIMED -> vm.claimReward(task.id)
        GardenTaskStatus.CLAIMED -> Unit
        GardenTaskStatus.NOT_STARTED,
        GardenTaskStatus.IN_PROGRESS -> when (task.type) {
            GardenTaskType.DAILY_CHECK_IN -> vm.checkIn()
            GardenTaskType.LEARN_EMOTION,
            GardenTaskType.PRACTICE_WEAK_EMOTION -> onLearnEmotion()
            GardenTaskType.PLAY_GAME -> onOpenGames()
            GardenTaskType.COMPLETE_CAMERA_CHALLENGE -> onStartEmotionChallenge()
            GardenTaskType.CREATE_PHOTOBOOTH -> onOpenPhotoBooth()
            GardenTaskType.SEND_REPORT -> onOpenReport()
        }
    }
}

@Composable
private fun LoadingCard() {
    EgSoftCard {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = EgDesign.primary, strokeWidth = 2.dp)
            Text("Đang chuẩn bị vườn cảm xúc...", color = EgDesign.textSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun GardenTopCard(state: GardenUiState) {
    val statusText = when {
        state.pendingRewardCount > 0 -> "${state.pendingRewardCount} phần thưởng chờ nhận"
        state.streakDays > 0 -> "Đã chăm vườn ${state.streakDays} ngày"
        else -> "Hôm nay vườn đang chờ bé chăm sóc"
    }
    EgSoftCard(radius = 24.dp) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GardenScenePreview(
                    plants = state.plants,
                    modifier = Modifier.size(70.dp)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Hôm nay mình chăm vườn nhé!",
                        color = EgDesign.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    PendingRewardChip(text = statusText, active = state.pendingRewardCount > 0)
                    GardenProgressBar(progress = state.gardenProgressPercent / 100f)
                    Text(
                        text = "Vườn phát triển ${state.gardenProgressPercent}%",
                        color = EgDesign.blue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            ResourceRow(state.inventory)
        }
    }
}

@Composable
private fun PendingRewardChip(text: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (active) Color(0xFFE8F8EE) else EgDesign.cardSoft,
        border = BorderStroke(1.dp, if (active) Color(0xFF86D39E) else EgDesign.cardBorder)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = if (active) Color(0xFF15803D) else EgDesign.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun ResourceRow(inventory: GardenInventory) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ResourceChip(label = "Giọt nước", value = inventory.water, type = "water", modifier = Modifier.weight(1f))
        ResourceChip(label = "Ánh nắng", value = inventory.sunlight, type = "sun", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ResourceChip(label: String, value: Int, type: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 42.dp),
        shape = RoundedCornerShape(16.dp),
        color = EgDesign.cardSoft,
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            GardenResourceIcon(type = type, modifier = Modifier.size(20.dp))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(6.dp))
            Text(value.toString(), color = EgDesign.primaryDark, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = EgDesign.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GardenMessageCard(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .widthIn(min = 250.dp, max = 326.dp)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(18.dp))
            .clickable(onClick = onDismiss),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xEE1B2A3F)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(10.dp),
                color = EgDesign.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EgVectorEmojiIcon("check", size = 20.dp, tint = Color.White)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Vườn cảm xúc",
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = message,
                    color = Color(0xFFD7E2F0),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlantGardenGrid(plants: List<EmotionPlant>, modifier: Modifier = Modifier, onOpenPlant: (String) -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Khu vườn của con", color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            plants.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    row.forEach { plant ->
                        PlantCard(
                            plant = plant,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onOpenPlant(plant.emotionId) }
                        )
                    }
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PlantCard(plant: EmotionPlant, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val progress = if (plant.pointsToNextLevel == 0) 1f else plant.growthPoints.toFloat() / plant.pointsToNextLevel.toFloat()
    val species = GardenRepository.plantSpecies(plant.emotionId)
    val scale by animateFloatAsState(
        targetValue = if (plant.totalGrowthPoints > 0) 1.03f else 1f,
        label = "plantGrowScale"
    )
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = EgDesign.card,
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmotionPlantVisual(plant = plant, modifier = Modifier.size(76.dp).scale(scale))
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                    shape = CircleShape,
                    color = EgDesign.cardSoft,
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EgEmotionVectorIcon(emotion = plant.emotionId, size = 18.dp)
                    }
                }
            }
            Text(
                text = GardenRepository.emotionName(plant.emotionId),
                color = EgDesign.textPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
            )
            Text(
                text = species.speciesName,
                color = EgDesign.textPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Lv. ${plant.level} · ${GardenRepository.plantStageName(plant.emotionId, plant.level)}",
                color = EgDesign.textSecondary,
                fontSize = 9.sp,
            )
            GardenProgressBar(
                progress = progress,
                height = 5.dp
            )
        }
    }
}

@Composable
private fun GardenTaskLauncher(state: GardenUiState, onOpenTasks: () -> Unit) {
    val completedDaily = state.dailyTasks.count {
        it.status == GardenTaskStatus.CLAIMED || it.status == GardenTaskStatus.COMPLETED_NOT_CLAIMED
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .egTactileClick(onClick = onOpenTasks),
        shape = RoundedCornerShape(18.dp),
        color = EgDesign.card,
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TaskIcon(
                type = GardenTaskType.DAILY_CHECK_IN,
                status = if (state.pendingRewardCount > 0) {
                    GardenTaskStatus.COMPLETED_NOT_CLAIMED
                } else {
                    GardenTaskStatus.IN_PROGRESS
                },
                modifier = Modifier.size(38.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Nhiệm vụ chăm vườn", color = EgDesign.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "$completedDaily/${state.dailyTasks.size} nhiệm vụ hôm nay · ${state.pendingRewardCount} phần thưởng chờ nhận",
                    color = EgDesign.textSecondary,
                    fontSize = 11.sp,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = EgDesign.cardSoft,
                border = BorderStroke(1.dp, EgDesign.cardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Xem", color = EgDesign.blue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    EgVectorEmojiIcon("next", size = 17.dp, tint = EgDesign.blue)
                }
            }
        }
    }
}

@Composable
private fun GardenTaskPanel(
    state: GardenUiState,
    onDismiss: () -> Unit,
    onTaskAction: (GardenTask) -> Unit,
    onClearMessage: () -> Unit
) {
    var showWeekly by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f),
                shape = RoundedCornerShape(EgDesign.radiusXLarge),
                color = EgDesign.card,
                border = BorderStroke(1.dp, EgDesign.cardBorder),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TaskIcon(
                            type = GardenTaskType.DAILY_CHECK_IN,
                            status = GardenTaskStatus.IN_PROGRESS,
                            modifier = Modifier.size(42.dp)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Nhiệm vụ chăm vườn", color = EgDesign.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "Hoàn thành nhiệm vụ để nhận nước và ánh nắng.",
                                color = EgDesign.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Surface(
                            modifier = Modifier.size(34.dp).egTactileClick(onClick = onDismiss),
                            shape = CircleShape,
                            color = EgDesign.cardSoft,
                            border = BorderStroke(1.dp, EgDesign.cardBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                EgVectorEmojiIcon("close", size = 19.dp, tint = EgDesign.textPrimary)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        GardenTaskTab(
                            label = "Hôm nay",
                            count = state.dailyTasks.size,
                            selected = !showWeekly,
                            onClick = { showWeekly = false },
                            modifier = Modifier.weight(1f)
                        )
                        GardenTaskTab(
                            label = "Tuần này",
                            count = state.weeklyTasks.size,
                            selected = showWeekly,
                            onClick = { showWeekly = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        val tasks = if (showWeekly) state.weeklyTasks else state.dailyTasks
                        tasks.forEach { task ->
                            TaskCard(task = task, busy = state.isBusy, onAction = { onTaskAction(task) })
                        }
                        UnlocksPreview()
                    }
                    GardenPill("Đóng", onDismiss, Modifier.fillMaxWidth(), primary = true, enabled = !state.isBusy)
                }
            }
            state.message?.let { msg ->
                GardenMessageCard(
                    message = msg,
                    onDismiss = onClearMessage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun GardenTaskTab(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) EgDesign.primary else EgDesign.cardSoft,
        border = BorderStroke(1.dp, if (selected) EgDesign.primary else EgDesign.cardBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "$label · $count",
                color = if (selected) Color.White else EgDesign.blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun TaskCard(task: GardenTask, busy: Boolean, onAction: () -> Unit) {
    val waitingReward = task.status == GardenTaskStatus.COMPLETED_NOT_CLAIMED
    val claimed = task.status == GardenTaskStatus.CLAIMED
    Surface(
        modifier = Modifier.fillMaxWidth().alpha(if (claimed) 0.78f else 1f),
        shape = RoundedCornerShape(18.dp),
        color = if (waitingReward) EgDesign.cardSoft else EgDesign.card,
        border = BorderStroke(1.dp, if (waitingReward) Color(0xFF86D39E) else EgDesign.cardBorder),
        shadowElevation = if (waitingReward) 3.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TaskIcon(type = task.type, status = task.status, modifier = Modifier.size(42.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = task.title,
                        color = EgDesign.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (waitingReward) {
                        SmallTaskBadge("Chờ nhận")
                    }
                }
                Text(
                    text = task.description,
                    color = EgDesign.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                Text(
                    text = "${task.progress.coerceAtMost(task.target)}/${task.target} · ${rewardText(task)}",
                    color = EgDesign.blue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            TaskActionButton(task = task, busy = busy, onClick = onAction)
        }
    }
}

@Composable
private fun SmallTaskBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFDCFCE7),
        border = BorderStroke(1.dp, Color(0xFF86D39E))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            color = Color(0xFF15803D),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun TaskActionButton(task: GardenTask, busy: Boolean, onClick: () -> Unit) {
    val enabled = task.status != GardenTaskStatus.CLAIMED && !busy
    val primary = task.status == GardenTaskStatus.COMPLETED_NOT_CLAIMED ||
        task.type == GardenTaskType.DAILY_CHECK_IN
    val label = when (task.status) {
        GardenTaskStatus.COMPLETED_NOT_CLAIMED -> "Nhận"
        GardenTaskStatus.CLAIMED -> "Đã nhận"
        GardenTaskStatus.NOT_STARTED,
        GardenTaskStatus.IN_PROGRESS -> when (task.type) {
            GardenTaskType.DAILY_CHECK_IN -> "Nhận nước"
            GardenTaskType.LEARN_EMOTION,
            GardenTaskType.PRACTICE_WEAK_EMOTION -> "Học"
            GardenTaskType.PLAY_GAME -> "Chơi"
            GardenTaskType.COMPLETE_CAMERA_CHALLENGE -> "Thử"
            GardenTaskType.CREATE_PHOTOBOOTH -> "Chụp"
            GardenTaskType.SEND_REPORT -> "Gửi"
        }
    }
    Surface(
        modifier = Modifier
            .height(36.dp)
            .alpha(if (enabled) 1f else 0.65f)
            .egTactileClick(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (primary && enabled) EgDesign.primary else EgDesign.cardSoft,
        border = if (primary && enabled) null else BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = if (primary && enabled) 2.dp else 0.dp
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (primary && enabled) Color.White else EgDesign.blue,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun UnlocksPreview() {
    EgSoftCard {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EgVectorEmojiIcon("gift", size = 34.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Trang trí đã mở khóa", color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(
                    "Khi các loài thực vật lớn lên, con sẽ mở thêm sticker và đồ trang trí cho vườn.",
                    color = EgDesign.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun PlantDetailDialog(
    plant: EmotionPlant,
    inventory: GardenInventory,
    busy: Boolean,
    onDismiss: () -> Unit,
    onWater: () -> Unit,
    onSun: () -> Unit
) {
    val species = GardenRepository.plantSpecies(plant.emotionId)
    val stageName = GardenRepository.plantStageName(plant.emotionId, plant.level)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(EgDesign.radiusXLarge),
            color = EgDesign.card,
            border = BorderStroke(1.dp, EgDesign.cardBorder),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EmotionPlantVisual(plant = plant, modifier = Modifier.size(124.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EgEmotionVectorIcon(emotion = plant.emotionId, size = 30.dp)
                    Text(
                        text = GardenRepository.emotionName(plant.emotionId),
                        color = EgDesign.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = "${species.speciesName} · Level ${plant.level} · $stageName",
                    color = EgDesign.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
                GardenProgressBar(
                    progress = if (plant.pointsToNextLevel == 0) 1f else plant.growthPoints.toFloat() / plant.pointsToNextLevel.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (plant.pointsToNextLevel == 0) {
                        "${species.speciesName} đã trưởng thành rồi!"
                    } else {
                        "${plant.growthPoints}/${plant.pointsToNextLevel} điểm để lên cấp"
                    },
                    color = EgDesign.blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Bé có thể dùng giọt nước và ánh nắng để giúp loài thực vật này lớn lên.",
                    color = EgDesign.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = "Nước giúp thực vật lớn từng chút. Ánh nắng giúp thực vật lớn nhanh hơn.",
                    color = EgDesign.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                if (inventory.water <= 0 || inventory.sunlight <= 0) {
                    Text(
                        text = buildString {
                            if (inventory.water <= 0) append("Con chưa có giọt nước nào. ")
                            if (inventory.sunlight <= 0) append("Con chưa có ánh nắng nào.")
                        }.trim(),
                        color = EgDesign.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GardenPill(
                        text = "(${inventory.water})",
                        icon = "water",
                        onClick = onWater,
                        modifier = Modifier.weight(1f),
                        primary = true,
                        enabled = inventory.water > 0 && plant.level < 5 && !busy,
                        height = 42.dp
                    )
                    GardenPill(
                        text = "(${inventory.sunlight})",
                        icon = "sun",
                        onClick = onSun,
                        modifier = Modifier.weight(1f),
                        primary = false,
                        enabled = inventory.sunlight > 0 && plant.level < 5 && !busy,
                        height = 42.dp
                    )
                }
                GardenPill("Đóng", onDismiss, Modifier.fillMaxWidth(), enabled = !busy)
            }
        }
    }
}

@Composable
private fun GardenPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 42.dp
) {
    Surface(
        modifier = modifier
            .heightIn(min = height)
            .alpha(if (enabled) 1f else 0.55f)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (primary && enabled) EgDesign.primary else EgDesign.cardSoft,
        border = if (primary && enabled) null else BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = if (primary && enabled) 2.dp else 0.dp
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                GardenResourceIcon(type = icon, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text,
                color = if (primary && enabled) Color.White else EgDesign.blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun GardenProgressBar(progress: Float, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 9.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(EgDesign.cardBorder.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(999.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF6EDB8F), EgDesign.primary)))
        )
    }
}

@Composable
private fun GardenScenePreview(plants: List<EmotionPlant>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE8F8EE)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            plants.take(6).forEach { plant ->
                EmotionPlantVisual(
                    plant = plant,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun EmotionPlantVisual(plant: EmotionPlant, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(GardenPlantAssets.assetFor(plant.emotionId, plant.level)),
            contentDescription = GardenRepository.plantSpeciesName(plant.emotionId),
            modifier = Modifier
                .fillMaxSize()
                .scale(gardenPlantVisualZoom(plant.level)),
            contentScale = ContentScale.Fit
        )
    }
}

private fun gardenPlantVisualZoom(level: Int): Float {
    return when (level.coerceIn(0, 5)) {
        0 -> 1.85f
        1 -> 1.65f
        2 -> 1.35f
        3 -> 1.18f
        else -> 1.08f
    }
}

@Composable
private fun GardenResourceIcon(type: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = min(size.width, size.height)
        val c = Offset(size.width / 2f, size.height / 2f)
        if (type == "water") {
            val p = Path().apply {
                moveTo(c.x, c.y - s * 0.38f)
                cubicTo(c.x + s * 0.32f, c.y - s * 0.05f, c.x + s * 0.25f, c.y + s * 0.36f, c.x, c.y + s * 0.38f)
                cubicTo(c.x - s * 0.25f, c.y + s * 0.36f, c.x - s * 0.32f, c.y - s * 0.05f, c.x, c.y - s * 0.38f)
                close()
            }
            drawPath(p, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.65f), Color(0xFF5FC8FF)), c, s * 0.50f))
        } else {
            repeat(10) { i ->
                val angle = Math.toRadians((i * 36).toDouble())
                drawLine(
                    Color(0xFFFFC928),
                    Offset(c.x + kotlin.math.cos(angle).toFloat() * s * 0.28f, c.y + kotlin.math.sin(angle).toFloat() * s * 0.28f),
                    Offset(c.x + kotlin.math.cos(angle).toFloat() * s * 0.44f, c.y + kotlin.math.sin(angle).toFloat() * s * 0.44f),
                    s * 0.045f
                )
            }
            drawCircle(Color(0xFFFFD54F), s * 0.27f, c)
        }
    }
}

@Composable
private fun TaskIcon(type: GardenTaskType, status: GardenTaskStatus, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (status == GardenTaskStatus.CLAIMED) Color(0xFFE8F8EE) else EgDesign.cardSoft,
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (type) {
                GardenTaskType.DAILY_CHECK_IN -> GardenResourceIcon("water", Modifier.size(24.dp))
                GardenTaskType.LEARN_EMOTION,
                GardenTaskType.PRACTICE_WEAK_EMOTION -> EgVectorEmojiIcon("book", size = 23.dp)
                GardenTaskType.PLAY_GAME -> EgVectorEmojiIcon("gamepad", size = 24.dp)
                GardenTaskType.COMPLETE_CAMERA_CHALLENGE -> EgVectorEmojiIcon("camera", size = 24.dp)
                GardenTaskType.CREATE_PHOTOBOOTH -> EgVectorEmojiIcon("sparkle", size = 24.dp)
                GardenTaskType.SEND_REPORT -> EgVectorEmojiIcon("report", size = 24.dp)
            }
        }
    }
}

private fun rewardText(task: GardenTask): String {
    val parts = buildList {
        if (task.reward.water > 0) add("+${task.reward.water} nước")
        if (task.reward.sunlight > 0) add("+${task.reward.sunlight} nắng")
        if (task.reward.seeds > 0) add("+${task.reward.seeds} hạt")
        if (task.reward.emotionStars > 0) add("+${task.reward.emotionStars} sao")
    }
    return parts.joinToString(" · ").ifBlank { "chăm vườn" }
}
