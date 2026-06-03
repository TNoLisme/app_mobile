package com.example.appmobile.ui.pages.report

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgVectorEmojiIcon
import com.example.appmobile.ui.viewmodel.PdfState
import com.example.appmobile.ui.viewmodel.ReportOneTimeEvent
import com.example.appmobile.ui.viewmodel.ReportViewModel
import com.example.appmobile.ui.viewmodel.SendReportResultDialogUi
import com.example.appmobile.ui.viewmodel.SentReportUi
import com.example.appmobile.ui.viewmodel.WeeklySummary
import kotlinx.coroutines.flow.collect

private val ReportBlue: Color get() = EgDesign.primary
private val ReportNavy: Color get() = EgDesign.textPrimary
private val ReportInk: Color get() = EgDesign.textPrimary
private val ReportMuted: Color get() = EgDesign.textSecondary
private val ReportLine: Color get() = EgDesign.cardBorder
private val ReportSoftBlue: Color get() = EgDesign.cardSoft

private data class ChildReportSummaryUi(
    val learnedEmotionCount: Int,
    val totalEmotionCount: Int,
    val parentEmail: String?
)

@Composable
fun ReportPage(
    onBack: () -> Unit,
    onPlayNow: () -> Unit = {},
    onUpdateEmail: () -> Unit = {},
    viewModel: ReportViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSendConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showAddEmailGateDialog by rememberSaveable { mutableStateOf(false) }
    var oneTimeMessage by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onRefreshReports(showFullLoading = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.oneTimeEvents.collect { event ->
            when (event) {
                ReportOneTimeEvent.ShowConfirmSendDialog -> showSendConfirmDialog = true
                ReportOneTimeEvent.NavigateToAddParentEmail -> showAddEmailGateDialog = true
                ReportOneTimeEvent.OpenReportPreview -> Unit
                is ReportOneTimeEvent.ShowSnackbar -> oneTimeMessage = event.message
            }
        }
    }

    val startPractice = {
        viewModel.onStartPractice()
        onPlayNow()
    }

    val childSummary = remember(
        state.weeklySummary,
        state.emotionStats,
        state.parentEmail
    ) {
        val learnedCount = state.weeklySummary?.learnedEmotionCount
            ?: state.emotionStats.count { it.attempts > 0 }
        ChildReportSummaryUi(
            learnedEmotionCount = learnedCount.coerceAtLeast(0).coerceAtMost(6),
            totalEmotionCount = 6,
            parentEmail = state.parentEmail?.takeIf { it.isNotBlank() }
        )
    }

    if (state.isPreviewVisible) {
        PdfPreviewDialog(
            pages = state.pdfPreviewPages,
            onDismiss = viewModel::onDismissPreview
        )
    }

    if (showSendConfirmDialog) {
        ConfirmSendReportDialog(
            email = childSummary.parentEmail.orEmpty(),
            onDismiss = { showSendConfirmDialog = false },
            onConfirm = {
                showSendConfirmDialog = false
                viewModel.onConfirmSendReport()
            }
        )
    }

    if (showAddEmailGateDialog) {
        ParentGateDialog(
            title = "Khu vực phụ huynh",
            message = "Phần này dành cho phụ huynh để thêm email nhận báo cáo của bé.",
            onDismiss = { showAddEmailGateDialog = false },
            onConfirm = {
                showAddEmailGateDialog = false
                onUpdateEmail()
            }
        )
    }

    state.sendResultDialog?.let { result ->
        SendReportResultDialog(
            result = result,
            onDismiss = viewModel::dismissSendResultDialog
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EgDesign.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ReportHeader(onBack = onBack)

        if (state.isLoading && state.weeklySummary == null) {
            LoadingCard()
        } else {
            state.errorMessage?.let { message ->
                ErrorCard(message = message, onRetry = viewModel::onRetryLoad)
            }

            ChildWeeklySummaryCard(
                summary = state.weeklySummary,
                learnedEmotionCount = childSummary.learnedEmotionCount,
                totalEmotionCount = childSummary.totalEmotionCount,
                onStartPractice = startPractice
            )

            if (childSummary.parentEmail != null) {
                val hasPreparedReport = when (state.pdfState) {
                    is PdfState.Generated,
                    is PdfState.PreviewError -> true
                    else -> false
                }
                SendReportCard(
                    parentEmail = childSummary.parentEmail,
                    hasPreparedReport = hasPreparedReport,
                    isPreparingReport = state.pdfState == PdfState.Generating,
                    isSending = state.pdfState == PdfState.EmailSending,
                    onCreateReport = viewModel::onGeneratePdf,
                    onRequestSend = viewModel::onSendReportToParent,
                    onViewReport = viewModel::onViewReport
                )
            } else {
                MissingParentEmailCard(
                    onAddEmail = viewModel::onAddParentEmail
                )
            }

            SentReportHistoryCard(
                reports = state.sentReports,
                onOpenReport = viewModel::onOpenReport
            )

            oneTimeMessage?.let { message ->
                OneTimeMessageCard(
                    message = message,
                    onDismiss = { oneTimeMessage = null }
                )
            }
        }
    }
}

@Composable
private fun ReportHeader(onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppBackButton(onClick = onBack, text = "← Quay lại")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Báo cáo của bé",
                fontWeight = FontWeight.ExtraBold,
                color = ReportNavy,
                fontSize = 28.sp,
                lineHeight = 32.sp
            )
            Text(
                text = "Xem thành tích tuần này và gửi cho bố mẹ.",
                color = ReportMuted,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EgDesign.radiusXLarge),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = ReportBlue)
        }
    }
}

@Composable
private fun ChildWeeklySummaryCard(
    summary: WeeklySummary?,
    learnedEmotionCount: Int,
    totalEmotionCount: Int,
    onStartPractice: () -> Unit
) {
    val sessions = summary?.sessionsCount ?: 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EgDesign.radiusXLarge),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Tuần này con đã học thế nào?",
                fontWeight = FontWeight.ExtraBold,
                color = ReportNavy,
                fontSize = 20.sp
            )
            if (sessions > 0) {
                Text(
                    text = "Con đã luyện $sessions lượt",
                    color = ReportInk,
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )
            } else {
                Text(
                    text = "Con chưa có lượt luyện nào trong tuần này.",
                    color = ReportInk,
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )
                Button(
                    onClick = onStartPractice,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ReportBlue)
                ) {
                    Text("Bắt đầu luyện tập", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ChildMetricBox(
                    icon = "gamepad",
                    value = sessions.toString(),
                    label = "Lượt luyện",
                    modifier = Modifier.weight(1f)
                )
                ChildMetricBox(
                    icon = "star",
                    value = averageScoreText(summary?.averageScore),
                    label = "Điểm trung bình",
                    modifier = Modifier.weight(1f)
                )
                ChildMetricBox(
                    icon = "sparkle",
                    value = "$learnedEmotionCount/$totalEmotionCount",
                    label = "Đã học cảm xúc",
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = if (sessions > 0) {
                    "Con thử luyện thêm vài cảm xúc nữa nhé!"
                } else {
                    "Cùng luyện thêm để nhận biết cảm xúc tốt hơn nhé!"
                },
                color = ReportNavy,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun ChildMetricBox(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = ReportSoftBlue,
        border = androidx.compose.foundation.BorderStroke(1.dp, ReportLine.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = 94.dp)
                .padding(horizontal = 7.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EgVectorEmojiIcon(icon, size = 25.dp, tint = ReportBlue)
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                color = ReportBlue,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ReportMuted,
                maxLines = 2,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SendReportCard(
    parentEmail: String,
    hasPreparedReport: Boolean,
    isPreparingReport: Boolean,
    isSending: Boolean,
    onCreateReport: () -> Unit,
    onRequestSend: () -> Unit,
    onViewReport: () -> Unit
) {
    val isBusy = isPreparingReport || isSending
    val primaryText = when {
        isSending -> "Đang gửi báo cáo..."
        isPreparingReport -> "Đang tạo báo cáo..."
        hasPreparedReport -> "Gửi báo cáo cho bố mẹ"
        else -> "Tạo báo cáo"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ReportLine, RoundedCornerShape(EgDesign.radiusXLarge)),
        shape = RoundedCornerShape(EgDesign.radiusXLarge),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Gửi báo cáo cho bố mẹ",
                fontWeight = FontWeight.ExtraBold,
                color = ReportNavy,
                fontSize = 19.sp
            )
            Text(
                text = "Báo cáo tuần này sẽ gửi đến:",
                color = ReportInk,
                lineHeight = 21.sp
            )
            Text(
                text = parentEmail,
                color = ReportBlue,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
            Button(
                onClick = if (hasPreparedReport) onRequestSend else onCreateReport,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ReportBlue,
                    disabledContainerColor = EgDesign.cardBorder
                )
            ) {
                Text(
                    text = primaryText,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            if (hasPreparedReport) {
                OutlinedButton(
                    onClick = onViewReport,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ReportNavy)
                ) {
                    Text("Xem báo cáo", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MissingParentEmailCard(onAddEmail: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EgDesign.radiusXLarge),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Chưa có email của bố mẹ",
                fontWeight = FontWeight.ExtraBold,
                color = ReportNavy,
                fontSize = 19.sp
            )
            Text(
                text = "Nhờ bố mẹ thêm email để nhận báo cáo hằng tuần nhé.",
                color = ReportInk,
                lineHeight = 21.sp
            )
            Button(
                onClick = onAddEmail,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ReportBlue)
            ) {
                Text("Thêm email phụ huynh", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun SentReportHistoryCard(
    reports: List<SentReportUi>,
    onOpenReport: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EgDesign.radiusXLarge),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Lịch sử báo cáo đã gửi",
                fontWeight = FontWeight.ExtraBold,
                color = ReportNavy,
                fontSize = 19.sp
            )

            if (reports.isEmpty()) {
                Text(
                    text = "Chưa có báo cáo nào được gửi.",
                    color = ReportMuted,
                    lineHeight = 20.sp
                )
            } else {
                reports.take(5).forEach { report ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = ReportSoftBlue,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReportLine.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                EgVectorEmojiIcon("mail", size = 24.dp, tint = ReportBlue)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = report.title,
                                        color = ReportNavy,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = report.weekRange,
                                        color = ReportMuted,
                                        fontSize = 12.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onOpenReport(report.reportId) },
                                    enabled = report.reportId.isNotBlank(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ReportNavy)
                                ) {
                                    Text("Xem", fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                            Text(
                                text = report.summaryText,
                                color = ReportInk,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            Text(
                                text = buildSentReportMeta(report),
                                color = ReportMuted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OneTimeMessageCard(message: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ReportSoftBlue,
        border = androidx.compose.foundation.BorderStroke(1.dp, ReportLine.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = ReportNavy,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = ReportNavy, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ReportSoftBlue,
        border = androidx.compose.foundation.BorderStroke(1.dp, ReportLine.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = message,
                color = ReportInk,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Thử lại", color = ReportNavy, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ConfirmSendReportDialog(
    email: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(EgDesign.radiusXLarge),
            color = EgDesign.card,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Gửi báo cáo cho bố mẹ?",
                    color = ReportNavy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Báo cáo tuần này sẽ được gửi đến:\n$email",
                    color = ReportInk,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("Hủy", color = ReportNavy, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ReportBlue)
                    ) {
                        Text("Gửi", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentGateDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(EgDesign.radiusXLarge),
            color = EgDesign.card,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = title,
                    color = ReportNavy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = message,
                    color = ReportInk,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("Hủy", color = ReportNavy, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ReportBlue)
                    ) {
                        Text("Tiếp tục", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SendReportResultDialog(
    result: SendReportResultDialogUi,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(EgDesign.radiusXLarge),
            color = EgDesign.card,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EgVectorEmojiIcon(
                        if (result.isSuccess) "sparkle" else "warning",
                        size = 30.dp,
                        tint = if (result.isSuccess) Color(0xFF3BC274) else Color(0xFFFF8D8D)
                    )
                }
                Text(
                    text = result.title,
                    color = ReportNavy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = result.message,
                    color = ReportInk,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ReportBlue)
                ) {
                    Text("Đã hiểu", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun PdfPreviewDialog(pages: List<Bitmap>, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(18.dp),
            color = EgDesign.card
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Xem báo cáo",
                        fontWeight = FontWeight.ExtraBold,
                        color = ReportNavy,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ReportNavy)
                    ) {
                        Text("Đóng")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .background(EgDesign.cardSoft, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pages.forEachIndexed { index, bitmap ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Trang ${index + 1}/${pages.size}",
                                color = ReportMuted,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Trang báo cáo ${index + 1}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, ReportLine, RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun averageScoreText(score: Int?): String {
    return score?.coerceIn(0, 100)?.let { "$it/100" } ?: "Chưa có điểm"
}

private fun buildSentReportMeta(report: SentReportUi): String {
    val recipient = report.recipientEmail?.takeIf { it.isNotBlank() }
    return if (recipient != null) {
        "Đã gửi lúc ${report.sentAtText} đến $recipient"
    } else {
        "Đã gửi lúc ${report.sentAtText}"
    }
}
