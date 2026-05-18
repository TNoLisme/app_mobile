package com.example.appmobile.ui.pages.report

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.ReportPayloadDto
import com.example.appmobile.data.remote.dto.ReportPreviewDataDto
import com.example.appmobile.data.remote.dto.ReportStatsDto
import com.example.appmobile.data.repository.AnalysisRepository
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.theme.SoftWhite
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private val ReportBlue = Color(0xFF57AEF2)
private val ReportNavy = Color(0xFF0B3F75)
private val ReportInk = Color(0xFF26384D)
private val ReportLine = Color(0xFFD7E5F0)

@Composable
fun ReportPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = remember(context) {
        runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: AppSession.currentBackendUserId()?.takeIf { it.isNotBlank() }
            ?: AppSession.getBackendUserId(context)?.takeIf { it.isNotBlank() }
            ?: "local-player"
    }
    val repository = remember {
        AnalysisRepository(
            reportDao = null,
            apiService = NetworkClient.apiService
        )
    }
    val preview = remember { mutableStateOf<ReportPreviewDataDto?>(null) }
    val history = remember { mutableStateOf<List<ReportPayloadDto>>(emptyList()) }
    val pendingReport = remember { mutableStateOf<ReportPayloadDto?>(null) }
    val pdfPages = remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    val showPdfPreview = remember { mutableStateOf(false) }
    val pdfViewed = remember { mutableStateOf(false) }
    val loading = remember { mutableStateOf(true) }
    val creatingReport = remember { mutableStateOf(false) }
    val loadingPdf = remember { mutableStateOf(false) }
    val sendingReport = remember { mutableStateOf(false) }
    val actionMessage = remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { pdfPages.value.forEach { it.recycle() } }
    }

    suspend fun loadData() {
        loading.value = true
        try {
            preview.value = repository.previewReport(userId)
            history.value = repository.getReportHistory(userId)
        } catch (_: Exception) {
            actionMessage.value = "Không tải được báo cáo. Vui lòng thử lại."
        } finally {
            loading.value = false
        }
    }

    suspend fun renderReportPdf(reportId: String): List<Bitmap> = withContext(Dispatchers.IO) {
        val bytes = repository.downloadReportPdf(reportId) ?: return@withContext emptyList()
        val file = File(context.cacheDir, "emogarden-report-$reportId.pdf")
        file.writeBytes(bytes)

        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        try {
            List(renderer.pageCount) { index ->
                val page = renderer.openPage(index)
                try {
                    val scale = 2f
                    val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                    val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        bitmap.eraseColor(AndroidColor.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    fun openPdfPreview(report: ReportPayloadDto) {
        val reportId = report.reportId
        if (reportId.isNullOrBlank()) {
            actionMessage.value = "Chưa có mã báo cáo để xem PDF."
            return
        }
        scope.launch {
            loadingPdf.value = true
            actionMessage.value = "Đang mở bản PDF xem trước..."
            val pages = runCatching { renderReportPdf(reportId) }.getOrDefault(emptyList())
            loadingPdf.value = false
            if (pages.isEmpty()) {
                actionMessage.value = "Chưa mở được PDF. Kiểm tra backend hoặc thư viện tạo PDF."
                return@launch
            }
            pdfPages.value.forEach { it.recycle() }
            pdfPages.value = pages
            pdfViewed.value = true
            showPdfPreview.value = true
            actionMessage.value = null
        }
    }

    LaunchedEffect(userId) {
        loadData()
    }

    if (showPdfPreview.value) {
        PdfPreviewDialog(
            pages = pdfPages.value,
            onDismiss = { showPdfPreview.value = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AppBackButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                "Báo cáo tiến bộ",
                fontWeight = FontWeight.ExtraBold,
                color = ReportNavy,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (loading.value) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ReportBlue)
            }
        } else {
            WeeklyReportCard(preview.value)

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    scope.launch {
                        creatingReport.value = true
                        pdfViewed.value = false
                        actionMessage.value = "Đang tạo file PDF..."
                        val created = runCatching { repository.requestReport(userId, sendEmail = false) }.getOrNull()
                        creatingReport.value = false
                        if (created == null) {
                            actionMessage.value = "Chưa tạo được báo cáo. Vui lòng thử lại."
                            return@launch
                        }
                        pendingReport.value = created
                        openPdfPreview(created)
                        loadData()
                    }
                },
                enabled = !creatingReport.value && !loadingPdf.value && !sendingReport.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ReportBlue)
            ) {
                Text(
                    if (creatingReport.value) "Đang tạo..." else "Tạo bản xem trước PDF",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            pendingReport.value?.let { report ->
                Spacer(modifier = Modifier.height(14.dp))
                ReadyPdfCard(
                    report = report,
                    pdfViewed = pdfViewed.value,
                    loadingPdf = loadingPdf.value,
                    sending = sendingReport.value,
                    onPreview = { openPdfPreview(report) },
                    onSend = {
                        val reportId = report.reportId
                        if (reportId.isNullOrBlank()) {
                            actionMessage.value = "Chưa có mã báo cáo để gửi."
                            return@ReadyPdfCard
                        }
                        scope.launch {
                            sendingReport.value = true
                            actionMessage.value = "Đang gửi PDF tới email phụ huynh..."
                            val result = repository.sendReport(reportId)
                            val sent = result?.emailSent == true
                            actionMessage.value = if (sent) {
                                pendingReport.value = null
                                "Đã gửi PDF tới email phụ huynh."
                            } else {
                                result?.message ?: "Chưa gửi được PDF. Kiểm tra email phụ huynh hoặc SMTP."
                            }
                            sendingReport.value = false
                            loadData()
                        }
                    }
                )
            }

            actionMessage.value?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFE9F5FF)) {
                    Text(
                        message,
                        modifier = Modifier.padding(14.dp),
                        color = ReportNavy,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Báo cáo đã tạo", fontWeight = FontWeight.ExtraBold, color = ReportInk, fontSize = 18.sp)
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { scope.launch { loadData() } },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ReportNavy)
                ) {
                    Text("Tải lại")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (history.value.isEmpty()) {
                EmptyHistoryCard()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    history.value.forEach { report ->
                        ReportHistoryCard(report)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyReportCard(preview: ReportPreviewDataDto?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Tuần này", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
            Text(
                preview?.summary ?: "Chưa có dữ liệu chơi game để tạo báo cáo.",
                color = ReportInk,
                lineHeight = 21.sp
            )
            ReportStatsRow(preview?.stats)
        }
    }
}

@Composable
private fun ReadyPdfCard(
    report: ReportPayloadDto,
    pdfViewed: Boolean,
    loadingPdf: Boolean,
    sending: Boolean,
    onPreview: () -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFBFE2FF), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FCFF)),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PDF đã sẵn sàng", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
            Text(
                report.summary ?: "Báo cáo đã được tạo. Mở bản xem trước trước khi gửi email.",
                color = ReportInk,
                lineHeight = 21.sp
            )
            ReportStatsRow(report.stats)
            if (!pdfViewed) {
                Text(
                    "Cần xem trước PDF rồi mới gửi email.",
                    color = Color(0xFF6B7C93),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onPreview,
                    enabled = !loadingPdf && !sending,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ReportNavy)
                ) {
                    Text(if (loadingPdf) "Đang mở..." else "Xem trước PDF", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSend,
                    enabled = pdfViewed && !sending && !loadingPdf,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReportBlue,
                        disabledContainerColor = Color(0xFFCFDDEA)
                    )
                ) {
                    Text(if (sending) "Đang gửi..." else "Gửi email", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReportStatsRow(stats: ReportStatsDto?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatBox("Lượt chơi", (stats?.totalSessions ?: 0).toString(), Modifier.weight(1f))
        StatBox("Điểm TB", formatScore(stats?.avgScore), Modifier.weight(1f))
        StatBox("Thời gian", formatMinutes(stats?.totalPlaytimeMinutes ?: 0), Modifier.weight(1f))
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = Color(0xFFEFF8EC)) {
        Column(
            modifier = Modifier
                .heightIn(min = 68.dp)
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
                color = Color(0xFF6F7E8E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Text(
            "Chưa có báo cáo đã lưu. Bấm “Tạo bản xem trước PDF” để tạo báo cáo đầu tiên.",
            modifier = Modifier.padding(18.dp),
            color = Color(0xFF6F7E8E),
            lineHeight = 21.sp
        )
    }
}

@Composable
private fun ReportHistoryCard(report: ReportPayloadDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatReportType(report.reportType), fontWeight = FontWeight.ExtraBold, color = ReportNavy)
                Spacer(modifier = Modifier.weight(1f))
                Text(formatDate(report.generatedAt), style = MaterialTheme.typography.labelSmall, color = Color(0xFF6F7E8E))
            }
            Text(
                report.summary ?: "Không có nội dung báo cáo.",
                color = ReportInk,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            report.stats?.let { stats ->
                Text(
                    "${stats.totalSessions ?: 0} lượt chơi • ${formatScore(stats.avgScore)} • ${formatMinutes(stats.totalPlaytimeMinutes ?: 0)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6F7E8E)
                )
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
            color = Color.White
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Xem trước PDF", fontWeight = FontWeight.ExtraBold, color = ReportNavy, fontSize = 18.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ReportNavy)
                    ) {
                        Text("Đóng")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE9EEF4), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(pages) { index, bitmap ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Trang ${index + 1}/${pages.size}",
                                color = Color(0xFF607086),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Trang PDF ${index + 1}",
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

private fun formatScore(value: Float?): String {
    val score = (value ?: 0f).roundToInt().coerceIn(0, 100)
    return "$score/100"
}

private fun formatMinutes(value: Int): String {
    if (value < 60) return "$value phút"
    return String.format(Locale.US, "%.1f giờ", value / 60f)
}

private fun formatReportType(value: String?): String {
    return when (value?.lowercase(Locale.US)) {
        "daily" -> "Báo cáo ngày"
        "monthly" -> "Báo cáo tháng"
        else -> "Báo cáo tuần"
    }
}

private fun formatDate(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return value.substringBefore("T")
}
