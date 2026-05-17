package com.example.appmobile.ui.pages.report

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.ReportPayloadDto
import com.example.appmobile.data.remote.dto.ReportPreviewDataDto
import com.example.appmobile.data.remote.dto.ReportStatsDto
import com.example.appmobile.data.repository.AnalysisRepository
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.theme.SoftWhite
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun ReportPage(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
    val loading = remember { mutableStateOf(true) }
    val creatingReport = remember { mutableStateOf(false) }
    val sendingReport = remember { mutableStateOf(false) }
    val actionMessage = remember { mutableStateOf<String?>(null) }

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

    fun refresh() {
        scope.launch { loadData() }
    }

    fun openReportPdf(reportId: String?) {
        if (reportId.isNullOrBlank()) {
            actionMessage.value = "Chưa có file PDF để xem trước."
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(NetworkClient.backendUrl("reports/$reportId/pdf")))
        runCatching { context.startActivity(intent) }
            .onFailure {
                actionMessage.value = "Không mở được PDF. Vui lòng thử lại sau."
            }
    }

    LaunchedEffect(userId) {
        loadData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppBackButton(onClick = onBack)
            Spacer(modifier = Modifier.weight(1f))
            Text("Báo cáo tiến bộ", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (loading.value) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val stats = preview.value?.stats
            ReportSummaryCard(summary = preview.value?.summary, stats = stats)

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        scope.launch {
                            creatingReport.value = true
                            actionMessage.value = "Đang tạo PDF để xem trước..."
                            val created = runCatching { repository.requestReport(userId, sendEmail = false) }.getOrNull()
                            if (created != null) {
                                pendingReport.value = created
                                actionMessage.value = "Đã tạo bản xem trước PDF. Kiểm tra nội dung rồi bấm gửi."
                            } else {
                                actionMessage.value = "Chưa tạo được báo cáo. Vui lòng thử lại."
                            }
                            creatingReport.value = false
                            loadData()
                        }
                    },
                    enabled = !creatingReport.value && !sendingReport.value,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (creatingReport.value) "Đang tạo..." else "Tạo PDF xem trước")
                }
                OutlinedButton(onClick = { refresh() }, modifier = Modifier.weight(1f)) {
                    Text("Tải lại")
                }
            }

            pendingReport.value?.let { report ->
                Spacer(modifier = Modifier.height(16.dp))
                ReportPdfPreviewCard(
                    report = report,
                    sending = sendingReport.value,
                    onOpenPdf = { openReportPdf(report.reportId) },
                    onSend = {
                        val reportId = report.reportId
                        if (reportId.isNullOrBlank()) {
                            actionMessage.value = "Chưa có mã báo cáo để gửi."
                            return@ReportPdfPreviewCard
                        }
                        scope.launch {
                            sendingReport.value = true
                            actionMessage.value = "Đang gửi PDF tới email phụ huynh..."
                            val sent = repository.sendReport(reportId)
                            actionMessage.value = if (sent) {
                                pendingReport.value = null
                                "Đã gửi PDF tới email phụ huynh."
                            } else {
                                "Chưa gửi được PDF. Kiểm tra email phụ huynh hoặc cấu hình SMTP."
                            }
                            sendingReport.value = false
                            loadData()
                        }
                    }
                )
            }

            actionMessage.value?.let { message ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(shape = MaterialTheme.shapes.large, color = Color(0xFFE7F1FF)) {
                    Text(
                        message,
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFF1E4E8C),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Lịch sử báo cáo", fontWeight = FontWeight.ExtraBold, color = Color.Gray)
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
private fun ReportSummaryCard(summary: String?, stats: ReportStatsDto?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Tổng quan", fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
            Text(summary ?: "Chưa có dữ liệu chơi game để tạo báo cáo.", color = Color.DarkGray)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatBox("Phiên chơi", (stats?.totalSessions ?: 0).toString(), Modifier.weight(1f))
                StatBox("Điểm TB", "%.1f".format(stats?.avgScore ?: 0f), Modifier.weight(1f))
                StatBox("Tiến trình", (stats?.progressCount ?: 0).toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReportPdfPreviewCard(
    report: ReportPayloadDto,
    sending: Boolean,
    onOpenPdf: () -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Bản xem trước PDF", fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E4E8C))
            Text(report.summary ?: "Không có nội dung báo cáo.", color = Color.DarkGray)
            report.stats?.let { stats ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatBox("Phiên chơi", (stats.totalSessions ?: 0).toString(), Modifier.weight(1f))
                    StatBox("Điểm TB", "%.1f".format(stats.avgScore ?: 0f), Modifier.weight(1f))
                    StatBox("Tiến trình", (stats.progressCount ?: 0).toString(), Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onOpenPdf, modifier = Modifier.weight(1f)) {
                    Text("Xem PDF")
                }
                Button(onClick = onSend, enabled = !sending, modifier = Modifier.weight(1f)) {
                    Text(if (sending) "Đang gửi..." else "Gửi PDF")
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = Color(0xFFF1F8E9)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 18.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Text(
            "Chưa có báo cáo đã lưu. Bấm “Tạo PDF xem trước” để tạo báo cáo đầu tiên.",
            modifier = Modifier.padding(18.dp),
            color = Color.Gray
        )
    }
}

@Composable
private fun ReportHistoryCard(report: ReportPayloadDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(report.reportType ?: "weekly", fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
                Text(formatDate(report.generatedAt), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Text(report.summary ?: "Không có nội dung báo cáo.", color = Color.DarkGray)
            report.stats?.let { stats ->
                Text(
                    "Phiên: ${stats.totalSessions ?: 0} • Điểm TB: ${"%.1f".format(stats.avgScore ?: 0f)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
            }
        }
    }
}

private fun formatDate(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return value.substringBefore("T")
}
