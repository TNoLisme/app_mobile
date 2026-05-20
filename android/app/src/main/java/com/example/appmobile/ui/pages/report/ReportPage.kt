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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.appmobile.ui.viewmodel.GeneratedReportUi
import com.example.appmobile.ui.viewmodel.PdfState
import com.example.appmobile.ui.viewmodel.ProgressReportUiState
import com.example.appmobile.ui.viewmodel.ReportEmotionUi
import com.example.appmobile.ui.viewmodel.ReportViewModel
import com.example.appmobile.ui.viewmodel.WeeklySummary
import com.example.appmobile.ui.viewmodel.buildWeeklySummaryText
import com.example.appmobile.ui.viewmodel.formatReportScore

private val ReportBlue: Color get() = EgDesign.primary
private val ReportNavy: Color get() = EgDesign.textPrimary
private val ReportInk: Color get() = EgDesign.textPrimary
private val ReportMuted: Color get() = EgDesign.textSecondary
private val ReportLine: Color get() = EgDesign.cardBorder
private val ReportSoftBlue: Color get() = EgDesign.cardSoft
private val ReportSoftGreen: Color get() = EgDesign.cardSoft
private val ReportSoftYellow: Color get() = EgDesign.cardSoft

@Composable
fun ReportPage(
    onBack: () -> Unit,
    onPlayNow: () -> Unit = {},
    onUpdateEmail: () -> Unit = {},
    viewModel: ReportViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingEmailReportId by remember { mutableStateOf<String?>(null) }
    var showAddEmailGate by remember { mutableStateOf(false) }
    var showParentDetailsGate by remember { mutableStateOf(false) }
    var showParentDetails by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onRefreshReports(showFullLoading = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestSendEmail(reportId: String?) {
        if (state.hasRecipientEmail) {
            pendingEmailReportId = reportId ?: ""
        } else {
            showAddEmailGate = true
        }
    }

    if (state.isPreviewVisible) {
        PdfPreviewDialog(
            pages = state.pdfPreviewPages,
            onDismiss = viewModel::onDismissPreview
        )
    }

    pendingEmailReportId?.let { reportId ->
        ConfirmSendReportDialog(
            email = state.parentEmail.orEmpty(),
            onDismiss = { pendingEmailReportId = null },
            onConfirm = {
                pendingEmailReportId = null
                viewModel.onSendReportToParent(reportId.takeIf { it.isNotBlank() })
            }
        )
    }

    if (showAddEmailGate) {
        ParentGateDialog(
            title = "Khu vực phụ huynh",
            message = "Phần này dành cho phụ huynh để thêm email nhận báo cáo.",
            confirmText = "Tiếp tục",
            onDismiss = { showAddEmailGate = false },
            onConfirm = {
                showAddEmailGate = false
                onUpdateEmail()
            }
        )
    }

    if (showParentDetailsGate) {
        ParentGateDialog(
            title = "Khu vực phụ huynh",
            message = "Phần này dành cho phụ huynh xem báo cáo chi tiết của bé.",
            confirmText = "Tiếp tục",
            onDismiss = { showParentDetailsGate = false },
            onConfirm = {
                showParentDetailsGate = false
                showParentDetails = true
            }
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

            ChildWeeklySummaryCard(summary = state.weeklySummary)
            ChildPracticeCard(emotions = state.emotionStats, onPlayNow = onPlayNow)

            if (state.hasRecipientEmail) {
                ChildSendReportCard(
                    state = state,
                    onSend = { requestSendEmail(null) },
                    onViewReport = viewModel::onPreviewCurrentReport
                )
            } else {
                MissingParentEmailCard(onAddEmail = { showAddEmailGate = true })
            }

            state.statusMessage?.let { message ->
                StatusMessageCard(message = message)
            }

            ParentDetailsLink(
                expanded = showParentDetails,
                onClick = {
                    if (showParentDetails) {
                        showParentDetails = false
                    } else {
                        showParentDetailsGate = true
                    }
                }
            )

            if (showParentDetails) {
                ParentSuggestionCard(text = state.parentSuggestion)
                GeneratedReportsSection(
                    reports = state.generatedReports,
                    isRefreshing = state.isRefreshing,
                    hasRecipientEmail = state.hasRecipientEmail,
                    parentEmail = state.parentEmail,
                    onRefresh = { viewModel.onRefreshReports(showFullLoading = false) },
                    onGenerate = viewModel::onGeneratePdf,
                    onOpen = viewModel::onOpenReport,
                    onDownload = viewModel::onDownloadPdf,
                    onSend = { reportId -> requestSendEmail(reportId) }
                )
            }
        }
    }
}

@Composable
private fun ReportHeader(onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppBackButton(onClick = onBack)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Báo cáo của bé",
                fontWeight = FontWeight.ExtraBold,
                color = ReportNavy,
                fontSize = 26.sp,
                lineHeight = 30.sp
            )
            Text(
                "Xem thành tích tuần này và gửi cho bố mẹ.",
                color = ReportMuted,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
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
private fun ChildWeeklySummaryCard(summary: WeeklySummary?) {
    val sessions = summary?.sessionsCount ?: 0
    val score = formatReportScore(summary?.averageScore)
    val emotionCount = formatEmotionMetric(summary)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Tuần này con đã học thế nào?", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 20.sp)
            Text(
                text = if (sessions > 0) {
                    "Tuần này con đã luyện $sessions lượt 🎉"
                } else {
                    "Tuần này con chưa có lượt chơi nào. Mình bắt đầu luyện cảm xúc nhé."
                },
                color = ReportInk,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ChildMetricBox("🎮", sessions.toString(), "Lượt chơi", Modifier.weight(1f))
                ChildMetricBox("⭐", score, "Điểm TB", Modifier.weight(1f))
                ChildMetricBox("🌈", emotionCount, "Cảm xúc đã luyện", Modifier.weight(1f))
            }
            Text(
                text = childProgressLine(summary),
                color = ReportNavy,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun ChildPracticeCard(emotions: List<ReportEmotionUi>, onPlayNow: () -> Unit) {
    val weak = weakEmotionItems(emotions)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Con nên luyện thêm", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 19.sp)
            if (weak.isEmpty()) {
                val hasPracticeData = emotions.any { it.attempts > 0 }
                Text(
                    if (hasPracticeData) {
                        "Con đang làm tốt. Hãy tiếp tục luyện tập nhé!"
                    } else {
                        "Con chơi thêm vài màn để app gợi ý cảm xúc cần luyện nhé."
                    },
                    color = ReportInk,
                    lineHeight = 21.sp
                )
                Button(
                    onClick = onPlayNow,
                    colors = ButtonDefaults.buttonColors(containerColor = ReportBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Chơi ngay", fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                weak.forEach { emotion ->
                    Surface(shape = RoundedCornerShape(16.dp), color = ReportSoftBlue) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(emotion.emoji, fontSize = 28.sp)
                            Text(emotion.name, color = ReportNavy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }
                }
                Text("Cùng bố mẹ luyện thêm các tình huống này nhé.", color = ReportMuted, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun ChildSendReportCard(
    state: ProgressReportUiState,
    onSend: () -> Unit,
    onViewReport: () -> Unit
) {
    val busy = state.pdfState == PdfState.Generating || state.pdfState == PdfState.EmailSending
    val buttonText = when (state.pdfState) {
        PdfState.Generating -> "Đang chuẩn bị báo cáo..."
        PdfState.EmailSending -> "Đang gửi báo cáo..."
        PdfState.EmailSent -> "Gửi lại cho bố mẹ"
        else -> "Gửi báo cáo cho bố mẹ"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFBFE2FF), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Gửi báo cáo cho bố mẹ", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 19.sp)
            state.parentEmail?.let { email ->
                Text("Báo cáo tuần này sẽ gửi đến: $email", color = ReportInk, lineHeight = 21.sp)
            }
            Button(
                onClick = onSend,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ReportBlue, disabledContainerColor = EgDesign.cardBorder)
            ) {
                Text(buttonText, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            OutlinedButton(
                onClick = onViewReport,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Xem báo cáo", color = ReportNavy, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MissingParentEmailCard(onAddEmail: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Chưa có email của bố mẹ", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 19.sp)
            Text("Nhờ bố mẹ thêm email để nhận báo cáo hằng tuần nhé.", color = ReportInk, lineHeight = 21.sp)
            PrimaryReportButton(text = "Thêm email phụ huynh", onClick = onAddEmail)
        }
    }
}

@Composable
private fun ParentDetailsLink(expanded: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ReportNavy)
    ) {
        Text(
            if (expanded) "Đóng báo cáo chi tiết" else "Phụ huynh xem báo cáo chi tiết >",
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun WeeklySummaryCard(summary: WeeklySummary?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Tuần này", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
            Text(
                buildWeeklySummaryText(summary),
                color = ReportInk,
                lineHeight = 22.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricBox((summary?.sessionsCount ?: 0).toString(), "Lượt chơi", Modifier.weight(1f))
                MetricBox(formatReportScore(summary?.averageScore), "Điểm TB", Modifier.weight(1f))
                MetricBox(formatEmotionMetric(summary), "Cảm xúc đã luyện", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmotionInsightCard(emotions: List<ReportEmotionUi>, onPlayNow: () -> Unit) {
    val practiced = emotions.filter { it.attempts > 0 }
    val best = practiced
        .filter { it.attempts >= 3 }
        .maxByOrNull { it.accuracy }
    val weak = practiced.sortedBy { it.accuracy }.take(2)
    val mostPracticed = practiced.maxByOrNull { it.attempts }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (practiced.isEmpty()) {
                Text("Chưa đủ dữ liệu cảm xúc", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
                Text(
                    "Bé chơi thêm vài màn để app gợi ý cảm xúc cần ôn nhé.",
                    color = ReportInk,
                    lineHeight = 21.sp
                )
                Button(
                    onClick = onPlayNow,
                    colors = ButtonDefaults.buttonColors(containerColor = ReportBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Chơi ngay", fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                Text("Cảm xúc nổi bật", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
                if (best == null) {
                    Text(
                        "Bé cần chơi thêm vài màn để app đánh giá cảm xúc làm tốt chính xác hơn.",
                        color = ReportInk,
                        lineHeight = 20.sp
                    )
                }
                best?.let {
                    EmotionInsightRow("Làm tốt", it, value = emotionAccuracyText(it))
                }
                weak.forEach { emotion ->
                    EmotionInsightRow("Cần luyện thêm", emotion, value = emotionAccuracyText(emotion))
                }
                mostPracticed?.let {
                    EmotionInsightRow("Luyện nhiều nhất", it, value = "${it.attempts} lượt")
                }
            }
        }
    }
}

@Composable
private fun EmotionInsightRow(label: String, emotion: ReportEmotionUi, value: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = ReportSoftBlue) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("$label:", color = ReportMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${emotion.name} ${emotion.emoji}",
                    color = ReportNavy,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(value, color = ReportInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ParentSuggestionCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Gợi ý cho phụ huynh", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
            Text(text, color = ReportInk, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun PdfStateCard(
    state: ProgressReportUiState,
    onGenerate: () -> Unit,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onUpdateEmail: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFBFE2FF), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val pdfState = state.pdfState) {
                PdfState.NotGenerated -> {
                    Text("Gửi báo cáo cho bố mẹ", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
                    Text("Tạo bản báo cáo tuần này để xem, tải xuống hoặc gửi qua email cho bố mẹ.", color = ReportInk, lineHeight = 21.sp)
                    PrimaryReportButton(text = "Tạo báo cáo", onClick = onGenerate)
                    Text("Bố mẹ có thể xem hoặc tải xuống trong phần báo cáo chi tiết.", color = ReportMuted, style = MaterialTheme.typography.labelMedium)
                }
                PdfState.Generating -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = ReportBlue, modifier = Modifier.height(24.dp))
                        Text("Đang tạo báo cáo...", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
                    }
                    Text("App đang chuẩn bị báo cáo tiến bộ của bé.", color = ReportInk)
                }
                is PdfState.Generated -> {
                    Text("Báo cáo đã sẵn sàng", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
                    Text("Bạn có thể xem báo cáo, tải xuống hoặc gửi qua email cho bố mẹ.", color = ReportInk, lineHeight = 21.sp)
                    PdfActionButtons(
                        hasRecipientEmail = state.hasRecipientEmail,
                        parentEmail = state.parentEmail,
                        onPreview = onPreview,
                        onDownload = onDownload,
                        onSend = onSend,
                        onUpdateEmail = onUpdateEmail
                    )
                }
                is PdfState.PreviewError -> {
                    Text("Chưa mở được báo cáo", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
                    Text("Không thể mở bản xem trước lúc này. Vui lòng thử lại.", color = ReportInk, lineHeight = 21.sp)
                    SecondaryReportButton(text = "Thử mở lại", onClick = onRetry)
                    PrimaryReportButton(text = "Tạo lại báo cáo", onClick = onGenerate)
                }
                is PdfState.GenerateError -> {
                    Text("Không tạo được báo cáo", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
                    Text(pdfState.message, color = ReportInk, lineHeight = 21.sp)
                    PrimaryReportButton(text = "Thử lại", onClick = onGenerate)
                }
                PdfState.EmailSending -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = ReportBlue, modifier = Modifier.height(24.dp))
                        Text("Đang gửi báo cáo...", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
                    }
                }
                PdfState.EmailSent -> {
                    Text("Đã gửi báo cáo cho phụ huynh", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
                    Text("Phụ huynh có thể kiểm tra hộp thư để xem báo cáo.", color = ReportInk, lineHeight = 21.sp)
                    PdfActionButtons(
                        hasRecipientEmail = state.hasRecipientEmail,
                        parentEmail = state.parentEmail,
                        onPreview = onPreview,
                        onDownload = onDownload,
                        onSend = onSend,
                        onUpdateEmail = onUpdateEmail
                    )
                }
                is PdfState.EmailError -> {
                    Text("Chưa gửi được báo cáo", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
                    Text(pdfState.message, color = ReportInk, lineHeight = 21.sp)
                    if (!state.hasRecipientEmail) {
                        SecondaryReportButton(text = "Thêm email phụ huynh", onClick = onUpdateEmail)
                    } else {
                        PrimaryReportButton(text = "Thử gửi lại", onClick = onSend)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfActionButtons(
    hasRecipientEmail: Boolean,
    parentEmail: String?,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onSend: () -> Unit,
    onUpdateEmail: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        PrimaryReportButton(text = "Xem báo cáo", onClick = onPreview)
        SecondaryReportButton(text = "Tải xuống", onClick = onDownload)
        parentEmail?.let { email ->
            Text("Sẽ gửi đến: $email", color = ReportMuted, style = MaterialTheme.typography.labelMedium)
        }
        Button(
            onClick = onSend,
            enabled = hasRecipientEmail,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ReportBlue,
                disabledContainerColor = EgDesign.cardBorder
            )
        ) {
            Text("Gửi email", fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
        if (!hasRecipientEmail) {
            Text("Chưa có email phụ huynh.", color = ReportMuted, style = MaterialTheme.typography.labelMedium)
            Text("Thêm email phụ huynh để gửi báo cáo hằng tuần.", color = ReportMuted, style = MaterialTheme.typography.labelSmall)
            OutlinedButton(onClick = onUpdateEmail, modifier = Modifier.fillMaxWidth()) {
                Text("Thêm email phụ huynh", maxLines = 1)
            }
        }
    }
}

@Composable
private fun GeneratedReportsSection(
    reports: List<GeneratedReportUi>,
    isRefreshing: Boolean,
    hasRecipientEmail: Boolean,
    parentEmail: String?,
    onRefresh: () -> Unit,
    onGenerate: () -> Unit,
    onOpen: (String) -> Unit,
    onDownload: (String) -> Unit,
    onSend: (String) -> Unit
) {
    val visibleReports = reports.distinctBy { report -> report.reportKey.ifBlank { report.id } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Báo cáo đã tạo", fontWeight = FontWeight.ExtraBold, color = ReportInk, fontSize = 18.sp)
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ReportNavy)
            ) {
                Text(if (isRefreshing) "Đang tải" else "Tải lại", maxLines = 1)
            }
        }

        if (visibleReports.isEmpty()) {
            EmptyReportsCard(onGenerate = onGenerate)
        } else {
            visibleReports.forEach { report ->
                GeneratedReportCard(
                    report = report,
                    hasRecipientEmail = hasRecipientEmail,
                    parentEmail = parentEmail,
                    onOpen = { onOpen(report.id) },
                    onDownload = { onDownload(report.id) },
                    onSend = { onSend(report.id) }
                )
            }
        }
    }
}

@Composable
private fun GeneratedReportCard(
    report: GeneratedReportUi,
    hasRecipientEmail: Boolean,
    parentEmail: String?,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(report.title, fontWeight = FontWeight.ExtraBold, color = ReportNavy)
                    Text(report.weekRange, color = ReportMuted, style = MaterialTheme.typography.labelMedium)
                }
                Text("Tạo ngày ${report.createdAtText}", color = ReportMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                "${report.summary.sessionsCount} lượt chơi · ${formatReportScore(report.summary.averageScore)} · ${formatEmotionMetric(report.summary)} cảm xúc đã luyện",
                color = ReportInk,
                fontWeight = FontWeight.SemiBold
            )
            parentEmail?.let { email ->
                Text("Email phụ huynh: $email", color = ReportMuted, style = MaterialTheme.typography.labelSmall)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SmallReportButton("Xem", onOpen, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SmallReportButton("Tải", onDownload, Modifier.weight(1f))
                    SmallReportButton("Gửi", onSend, Modifier.weight(1f), enabled = hasRecipientEmail)
                }
                if (!hasRecipientEmail) {
                    Text("Chưa có email phụ huynh.", color = ReportMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun EmptyReportsCard(onGenerate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Chưa có báo cáo nào", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
            Text("Tạo báo cáo tuần đầu tiên để theo dõi tiến bộ của bé.", color = ReportInk, lineHeight = 21.sp)
            PrimaryReportButton(text = "Tạo báo cáo", onClick = onGenerate)
        }
    }
}

@Composable
private fun MetricBox(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = ReportSoftGreen) {
        Column(
            modifier = Modifier
                .heightIn(min = 70.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                value,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF207B38),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = ReportMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChildMetricBox(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = EgDesign.cardSoft) {
        Column(
            modifier = Modifier
                .heightIn(min = 86.dp)
                .padding(horizontal = 7.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(icon, fontSize = 22.sp)
            Text(
                value,
                fontWeight = FontWeight.ExtraBold,
                color = ReportBlue,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = ReportMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatEmotionMetric(summary: WeeklySummary?): String {
    val count = summary?.learnedEmotionCount
    return if (count != null) "${count.coerceIn(0, 6)}/6" else "0/6"
}

private fun childProgressLine(summary: WeeklySummary?): String {
    val avg = summary?.averageScore
    return when {
        summary == null || summary.sessionsCount == 0 -> "Con hãy chơi vài màn để có thành tích tuần này nhé."
        avg == null -> "Con chơi thêm một chút để app tính điểm trung bình nhé."
        avg >= 80 -> "Con đang tiến bộ rất tốt."
        avg >= 60 -> "Con đang tiến bộ ổn."
        avg >= 40 -> "Con thử luyện thêm vài cảm xúc nữa nhé!"
        else -> "Con nên ôn lại các cảm xúc cơ bản."
    }
}

private fun weakEmotionItems(emotions: List<ReportEmotionUi>): List<ReportEmotionUi> {
    return emotions
        .filter { it.attempts > 0 && it.accuracy < 60 }
        .sortedWith(compareBy<ReportEmotionUi> { it.accuracy }.thenByDescending { it.attempts })
        .take(2)
}

private fun emotionAccuracyText(emotion: ReportEmotionUi): String {
    return if (emotion.attempts >= 3) {
        "${emotion.accuracy}% · ${emotion.attempts} lượt"
    } else {
        "${emotion.accuracy}% · dữ liệu còn ít"
    }
}

@Composable
private fun PrimaryReportButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ReportBlue)
    ) {
        Text(text, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun SecondaryReportButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ReportNavy)
    ) {
        Text(text, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SmallReportButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        contentPadding = ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ReportNavy)
    ) {
        Text(text, maxLines = 1, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusMessageCard(message: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = ReportSoftBlue) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            color = ReportNavy,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = ReportSoftYellow) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message, color = ReportInk, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
            SecondaryReportButton(text = "Thử lại", onClick = onRetry)
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
            shape = RoundedCornerShape(24.dp),
            color = EgDesign.card,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("👨‍👩‍👧", fontSize = 30.sp)
                Text(
                    "Gửi báo cáo cho bố mẹ?",
                    color = ReportNavy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Báo cáo tuần này sẽ được gửi đến: $email",
                    color = ReportInk,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = EgDesign.card,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("🔐", fontSize = 30.sp)
                Text(
                    title,
                    color = ReportNavy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    message,
                    color = ReportInk,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        Text(confirmText, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmRegenerateReportDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = EgDesign.card,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("📝", fontSize = 30.sp)
                Text(
                    "Báo cáo tuần này đã tồn tại",
                    color = ReportNavy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Bạn muốn tạo lại báo cáo tuần này không? Báo cáo cũ sẽ được cập nhật bằng dữ liệu mới nhất.",
                    color = ReportInk,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        Text("Tạo lại", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPreviewDialog(pages: List<Bitmap>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(18.dp),
            color = EgDesign.card
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Xem báo cáo", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ReportNavy)
                    ) {
                        Text("Đóng", maxLines = 1)
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
                                "Trang ${index + 1}/${pages.size}",
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
