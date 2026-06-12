package com.example.appmobile.ui.viewmodel

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appmobile.data.garden.GardenRepository
import com.example.appmobile.data.garden.LearningEvent
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.ReportPayloadDto
import com.example.appmobile.data.remote.dto.ReportPreviewDataDto
import com.example.appmobile.data.remote.dto.ReportStatsDto
import com.example.appmobile.data.remote.dto.UserProfileDto
import com.example.appmobile.data.repository.AnalysisRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.text.Normalizer
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class ProgressReportUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val sendResultDialog: SendReportResultDialogUi? = null,
    val weeklySummary: WeeklySummary? = null,
    val emotionStats: List<ReportEmotionUi> = emptyList(),
    val parentSuggestion: String = "",
    val pdfState: PdfState = PdfState.NotGenerated,
    val currentReport: GeneratedReportUi? = null,
    val currentWeekReport: GeneratedReportUi? = null,
    val generatedReports: List<GeneratedReportUi> = emptyList(),
    val sentReports: List<SentReportUi> = emptyList(),
    val pdfPreviewPages: List<Bitmap> = emptyList(),
    val isPreviewVisible: Boolean = false,
    val hasRecipientEmail: Boolean = false,
    val parentEmail: String? = null
)

data class SendReportResultDialogUi(
    val isSuccess: Boolean,
    val title: String,
    val message: String
)

sealed class ReportOneTimeEvent {
    object ShowConfirmSendDialog : ReportOneTimeEvent()
    data class ShowSnackbar(val message: String) : ReportOneTimeEvent()
    object NavigateToAddParentEmail : ReportOneTimeEvent()
    object OpenReportPreview : ReportOneTimeEvent()
}

data class WeeklySummary(
    val sessionsCount: Int,
    val averageScore: Int?,
    val totalMinutes: Int?,
    val learnedEmotionCount: Int? = null,
    val activeDays: Int? = null,
    val previousAverageScore: Int? = null
)

data class ReportEmotionUi(
    val name: String,
    val emoji: String,
    val accuracy: Int,
    val attempts: Int
)

data class GeneratedReportUi(
    val id: String,
    val reportKey: String,
    val type: String,
    val title: String,
    val weekRange: String,
    val createdAtText: String,
    val summary: WeeklySummary,
    val isCurrentPeriod: Boolean = false
)

data class SentReportUi(
    val reportId: String,
    val title: String,
    val weekRange: String,
    val sentAtText: String,
    val recipientEmail: String?,
    val summaryText: String
)

sealed class PdfState {
    object NotGenerated : PdfState()
    object Generating : PdfState()
    data class Generated(val reportId: String) : PdfState()
    data class GenerateError(val message: String) : PdfState()
    data class PreviewError(val reportId: String) : PdfState()
    object EmailSending : PdfState()
    object EmailSent : PdfState()
    data class EmailError(val message: String) : PdfState()
}

class ReportViewModel(application: Application) : AndroidViewModel(application) {
    private val gardenRepository = GardenRepository(application.applicationContext)
    private val repository = AnalysisRepository(
        reportDao = null,
        apiService = NetworkClient.apiService
    )
    private val _state = MutableStateFlow(ProgressReportUiState(isLoading = true))
    val state: StateFlow<ProgressReportUiState> = _state.asStateFlow()
    private val _oneTimeEvents = MutableSharedFlow<ReportOneTimeEvent>(extraBufferCapacity = 1)
    val oneTimeEvents: SharedFlow<ReportOneTimeEvent> = _oneTimeEvents.asSharedFlow()

    private var reportPayloads: List<ReportPayloadDto> = emptyList()

    companion object {
        private const val REPORT_UI_PREF = "report_ui_state"
        private const val KEY_LAST_SENT_WEEKLY_PREFIX = "last_sent_weekly_"
        private const val KEY_SENT_REPORT_HISTORY_PREFIX = "sent_report_history_"
    }

    init {
        onRefreshReports(showFullLoading = true)
    }

    fun onRefreshReports(showFullLoading: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = showFullLoading,
                    isRefreshing = !showFullLoading,
                    errorMessage = null,
                    statusMessage = null
                )
            }

            val userId = currentUserId()
            val preview = repository.previewReport(userId)
            val history = repository.getReportHistory(userId)
            val profile = repository.getUserProfile(userId)

            if (preview == null && history.isEmpty()) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = "Không tải được dữ liệu báo cáo. Vui lòng thử lại."
                    )
                }
                return@launch
            }

            val distinctPayloads = distinctReports(history)
            reportPayloads = distinctPayloads
            val weeklySummary = preview?.toWeeklySummary()
            val emotions = preview.toEmotionStats()
            val generated = distinctPayloads.map { it.toGeneratedReport() }
            val existingCurrent = state.value.currentReport
            val currentReport = existingCurrent?.takeIf { report -> generated.any { it.id == report.id } }
            val currentWeekReport = generated.firstOrNull { it.type == "weekly" && it.isCurrentPeriod }
            val parentEmail = profile.reportEmail()

            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null,
                    weeklySummary = weeklySummary,
                    emotionStats = emotions,
                    parentSuggestion = buildParentSuggestion(weeklySummary, emotions),
                    generatedReports = generated,
                    sentReports = sentReportsForUi(userId, generated),
                    currentReport = currentReport,
                    currentWeekReport = currentWeekReport,
                    hasRecipientEmail = parentEmail != null,
                    parentEmail = parentEmail,
                    pdfState = restorePdfStateForCurrentWeek(
                        previousState = it.pdfState,
                        reports = generated,
                        currentWeekReport = currentWeekReport,
                        userId = userId
                    )
                )
            }
        }
    }

    fun onRetryLoad() {
        onRefreshReports(showFullLoading = state.value.weeklySummary == null)
    }

    fun onGeneratePdf() {
        val current = state.value.pdfState
        if (current == PdfState.Generating || current == PdfState.EmailSending) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    pdfState = PdfState.Generating,
                    statusMessage = null,
                    errorMessage = null
                )
            }

            val created = repository.requestReport(
                currentUserId(),
                sendEmail = false,
                parentEmail = state.value.parentEmail
            )
            if (created?.reportId.isNullOrBlank()) {
                _state.update {
                    it.copy(
                        pdfState = PdfState.GenerateError("Không tạo được báo cáo. Vui lòng thử lại."),
                        statusMessage = null
                    )
                }
                return@launch
            }

            val payload = created!!
            val generatedReport = payload.toGeneratedReport()
            clearLastSentWeeklyMarker(currentUserId(), generatedReport)
            applyReportPayload(
                payload = payload,
                pdfState = PdfState.Generated(generatedReport.id),
                statusMessage = "Báo cáo đã sẵn sàng. Nội dung đang khớp với thống kê tuần này."
            )
        }
    }

    fun onPreviewCurrentReport() {
        val current = state.value.pdfState
        if (current == PdfState.Generating || current == PdfState.EmailSending) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    pdfState = PdfState.Generating,
                    statusMessage = "Đang chuẩn bị báo cáo mới nhất...",
                    errorMessage = null
                )
            }

            val payload = repository.requestReport(
                currentUserId(),
                sendEmail = false,
                parentEmail = state.value.parentEmail
            )
            val targetId = payload?.reportId?.takeIf { it.isNotBlank() }
            if (payload == null || targetId == null) {
                _state.update {
                    it.copy(
                        pdfState = PdfState.GenerateError("Không tạo được báo cáo. Vui lòng thử lại."),
                        statusMessage = null
                    )
                }
                return@launch
            }

            applyReportPayload(
                payload = payload,
                pdfState = PdfState.Generated(targetId),
                statusMessage = "Đang mở báo cáo..."
            )
            clearLastSentWeeklyMarker(currentUserId(), payload.toGeneratedReport())
            openPdfPreview(targetId)
        }
    }

    fun onPreviewPdf(reportId: String? = null) {
        val targetId = reportId ?: activeReportId() ?: return
        viewModelScope.launch {
            openPdfPreview(targetId)
        }
    }

    fun onOpenReport(reportId: String) {
        val payload = reportPayloads.firstOrNull { it.reportId == reportId }
        _state.update {
            it.copy(
                currentReport = payload?.toGeneratedReport() ?: it.currentReport,
                pdfState = PdfState.Generated(reportId),
                statusMessage = null
            )
        }
        onPreviewPdf(reportId)
    }

    fun onDownloadPdf(reportId: String? = null) {
        val targetId = reportId ?: activeReportId() ?: return
        reportPayloads.firstOrNull { it.reportId == targetId }?.let { payload ->
            _state.update { it.copy(currentReport = payload.toGeneratedReport()) }
        }
        viewModelScope.launch {
            _state.update { it.copy(statusMessage = "Đang lưu báo cáo...") }
            val bytes = repository.downloadReportPdf(targetId)
            val saved = bytes?.let { savePdfToDownloads(targetId, it) } == true
            _state.update {
                it.copy(
                    statusMessage = if (saved) {
                        "Đã lưu báo cáo trên thiết bị."
                    } else {
                        "Không tải được báo cáo. Vui lòng thử lại."
                    }
                )
            }
        }
    }

    fun onSendEmail(reportId: String? = null) {
        val targetId = reportId ?: activeReportId()
        if (targetId.isNullOrBlank()) {
            _state.update {
                it.copy(pdfState = PdfState.EmailError("Chưa có báo cáo tuần này. Vui lòng tạo báo cáo trước."))
            }
            return
        }
        reportPayloads.firstOrNull { it.reportId == targetId }?.let { payload ->
            _state.update { it.copy(currentReport = payload.toGeneratedReport()) }
        }
        if (!state.value.hasRecipientEmail) {
            _state.update {
                it.copy(pdfState = PdfState.EmailError("Chưa có email phụ huynh hợp lệ. Vui lòng thêm email phụ huynh."))
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    pdfState = PdfState.EmailSending,
                    statusMessage = null,
                    sendResultDialog = null
                )
            }
            val result = repository.sendReport(targetId, state.value.parentEmail)
            val sent = result?.emailSent == true
            result?.data?.let { payload -> applyReportPayload(payload) }
            if (sent) {
                val sentReport = result?.data?.toGeneratedReport()
                    ?: state.value.currentWeekReport
                markLastSentWeeklyReport(currentUserId(), sentReport)
                recordSentReport(currentUserId(), sentReport, state.value.parentEmail)
                gardenRepository.onLearningEvent(LearningEvent.ReportSentToParent)
            }
            _state.update {
                it.copy(
                    pdfState = PdfState.NotGenerated,
                    statusMessage = null,
                    sentReports = sentReportsForUi(currentUserId(), it.generatedReports),
                    sendResultDialog = buildSendResultDialog(sent, result?.message)
                )
            }
        }
    }

    fun onSendReportToParent() {
        if (state.value.hasRecipientEmail) {
            _oneTimeEvents.tryEmit(ReportOneTimeEvent.ShowConfirmSendDialog)
        } else {
            onAddParentEmail()
        }
    }

    fun onConfirmSendReport(reportId: String? = null) {
        if (!state.value.hasRecipientEmail) {
            _state.update {
                it.copy(pdfState = PdfState.EmailError("Chưa có email của bố mẹ. Nhờ bố mẹ thêm email để nhận báo cáo hằng tuần nhé."))
            }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    pdfState = PdfState.EmailSending,
                    statusMessage = null,
                    errorMessage = null,
                    sendResultDialog = null
                )
            }

            if (!reportId.isNullOrBlank()) {
                val result = repository.sendReport(reportId, state.value.parentEmail)
                val sent = result?.emailSent == true
                result?.data?.let { payload -> applyReportPayload(payload) }
                if (sent) {
                    val sentReport = result?.data?.toGeneratedReport()
                        ?: state.value.currentWeekReport
                    markLastSentWeeklyReport(currentUserId(), sentReport)
                    recordSentReport(currentUserId(), sentReport, state.value.parentEmail)
                    gardenRepository.onLearningEvent(LearningEvent.ReportSentToParent)
                }
                _state.update {
                    it.copy(
                        pdfState = PdfState.NotGenerated,
                        statusMessage = null,
                        sentReports = sentReportsForUi(currentUserId(), it.generatedReports),
                        sendResultDialog = buildSendResultDialog(sent, result?.message)
                    )
                }
                return@launch
            }

            val result = repository.requestReportResponse(
                childId = currentUserId(),
                sendEmail = true,
                parentEmail = state.value.parentEmail
            )
            val payload = result?.data
            if (payload?.reportId.isNullOrBlank()) {
                _state.update {
                    it.copy(
                        pdfState = PdfState.NotGenerated,
                        statusMessage = null,
                        sendResultDialog = SendReportResultDialogUi(
                            isSuccess = false,
                            title = "Chưa gửi được báo cáo",
                            message = sanitizeSendFailureMessage(result?.message)
                        )
                    )
                }
                return@launch
            }

            applyReportPayload(payload!!, pdfState = PdfState.EmailSending, statusMessage = null)
            val sent = result.emailSent == true
            if (sent) {
                markLastSentWeeklyReport(currentUserId(), payload.toGeneratedReport())
                recordSentReport(currentUserId(), payload.toGeneratedReport(), state.value.parentEmail)
                gardenRepository.onLearningEvent(LearningEvent.ReportSentToParent)
            }
            _state.update {
                it.copy(
                    pdfState = PdfState.NotGenerated,
                    statusMessage = null,
                    sentReports = sentReportsForUi(currentUserId(), it.generatedReports),
                    sendResultDialog = buildSendResultDialog(sent, result.message)
                )
            }
        }
    }

    fun onViewReport() {
        onPreviewCurrentReport()
        _oneTimeEvents.tryEmit(ReportOneTimeEvent.OpenReportPreview)
    }

    fun onAddParentEmail() {
        _oneTimeEvents.tryEmit(ReportOneTimeEvent.NavigateToAddParentEmail)
    }

    fun onStartPractice() {
        _oneTimeEvents.tryEmit(ReportOneTimeEvent.ShowSnackbar("Cùng luyện tập thêm nhé!"))
    }

    fun onRetryPdf() {
        when (val pdfState = state.value.pdfState) {
            is PdfState.PreviewError -> onPreviewPdf(pdfState.reportId)
            is PdfState.GenerateError -> onGeneratePdf()
            else -> onGeneratePdf()
        }
    }

    fun onDismissPreview() {
        _state.update { it.copy(isPreviewVisible = false) }
    }

    fun clearStatusMessage() {
        _state.update { it.copy(statusMessage = null) }
    }

    fun dismissSendResultDialog() {
        _state.update { it.copy(sendResultDialog = null) }
    }

    private fun buildSendResultDialog(sent: Boolean, message: String?): SendReportResultDialogUi {
        return if (sent) {
            SendReportResultDialogUi(
                isSuccess = true,
                title = "Đã gửi báo cáo cho bố mẹ",
                message = "Báo cáo tuần này đã được gửi thành công."
            )
        } else {
            SendReportResultDialogUi(
                isSuccess = false,
                title = "Chưa gửi được báo cáo",
                message = sanitizeSendFailureMessage(message)
            )
        }
    }

    private fun sanitizeSendFailureMessage(message: String?): String {
        val clean = message?.trim().orEmpty()
        return if (clean.isBlank()) {
            "Chưa gửi được báo cáo. Vui lòng thử lại."
        } else {
            clean
        }
    }

    private fun currentUserId(): String {
        val context = getApplication<Application>()
        return FirebaseAuth.getInstance().currentUser?.uid
            ?: AppSession.currentBackendUserId()
            ?: AppSession.getBackendUserId(context)
            ?: "local-player"
    }

    private fun ReportPreviewDataDto?.toEmotionStats(): List<ReportEmotionUi> {
        return this?.insights?.emotionStats.orEmpty()
            .mapNotNull { (name, stat) ->
                val correct = stat.correct ?: 0
                val incorrect = stat.incorrect ?: 0
                val attempts = correct + incorrect
                if (attempts <= 0) return@mapNotNull null
                ReportEmotionUi(
                    name = name,
                    emoji = emotionEmoji(name),
                    accuracy = (stat.accuracy ?: 0f).roundToInt().coerceIn(0, 100),
                    attempts = attempts
                )
            }
            .sortedWith(compareByDescending<ReportEmotionUi> { it.attempts }.thenByDescending { it.accuracy })
    }

    private fun ReportPayloadDto.toEmotionStats(): List<ReportEmotionUi> {
        val emotionJson = parseReportData(data)?.optJSONObject("emotion_stats") ?: return emptyList()
        val names = emotionJson.names() ?: return emptyList()
        return (0 until names.length())
            .mapNotNull { index ->
                val name = names.optString(index)
                val stat = emotionJson.optJSONObject(name) ?: return@mapNotNull null
                val correct = stat.optInt("correct", 0)
                val incorrect = stat.optInt("incorrect", 0)
                val attempts = stat.optInt("attempts", correct + incorrect)
                if (attempts <= 0) return@mapNotNull null
                ReportEmotionUi(
                    name = name,
                    emoji = emotionEmoji(name),
                    accuracy = stat.optDouble("accuracy", 0.0).roundToInt().coerceIn(0, 100),
                    attempts = attempts
                )
            }
            .sortedWith(compareByDescending<ReportEmotionUi> { it.attempts }.thenByDescending { it.accuracy })
    }

    private fun ReportPreviewDataDto.toWeeklySummary(): WeeklySummary {
        return stats.toWeeklySummary(
            learnedEmotionCount = insights?.emotionStats.orEmpty().count { (_, stat) ->
                val correct = stat.correct ?: 0
                val incorrect = stat.incorrect ?: 0
                (stat.attempts ?: (correct + incorrect)) > 0
            }.takeIf { it > 0 },
            activeDays = insights?.dailySessions.orEmpty().count { (_, count) -> count > 0 }.takeIf { it > 0 }
        )
    }

    private fun ReportStatsDto?.toWeeklySummary(
        learnedEmotionCount: Int? = null,
        activeDays: Int? = null
    ): WeeklySummary {
        val sessions = this?.totalSessions ?: 0
        val avg = this?.avgScore?.roundToInt()?.coerceIn(0, 100)
            ?.takeIf { sessions > 0 }
        val minutes = this?.totalPlaytimeMinutes
            ?.takeIf { value -> value > 0 || sessions == 0 }
        val previous = this?.previousAvgScore?.roundToInt()?.coerceIn(0, 100)
        return WeeklySummary(
            sessionsCount = sessions,
            averageScore = avg,
            totalMinutes = minutes,
            learnedEmotionCount = learnedEmotionCount,
            activeDays = activeDays,
            previousAverageScore = previous
        )
    }

    private fun ReportPayloadDto.toGeneratedReport(): GeneratedReportUi {
        val dataJson = parseReportData(data)
        val start = dataJson?.optString("start_date").orEmpty()
        val end = dataJson?.optString("end_date").orEmpty()
        val weekRange = buildWeekRange(start, end)
        val emotionCount = dataJson
            ?.optJSONObject("emotion_stats")
            ?.let { json ->
                (0 until json.length()).count { index ->
                    val key = json.names()?.optString(index).orEmpty()
                    val stat = json.optJSONObject(key)
                    val correct = stat?.optInt("correct", 0) ?: 0
                    val incorrect = stat?.optInt("incorrect", 0) ?: 0
                    val attempts = stat?.optInt("attempts", correct + incorrect) ?: (correct + incorrect)
                    attempts > 0
                }
            }
            ?.takeIf { it > 0 }
        val activeDays = dataJson
            ?.optJSONObject("daily_sessions")
            ?.let { json ->
                (0 until json.length()).count { index ->
                    val key = json.names()?.optString(index).orEmpty()
                    json.optInt(key, 0) > 0
                }
            }
            ?.takeIf { it > 0 }
        val reportKey = reportPeriodKey(this)
        return GeneratedReportUi(
            id = reportId.orEmpty(),
            reportKey = reportKey,
            type = reportType ?: "weekly",
            title = reportTitle(reportType),
            weekRange = weekRange,
            createdAtText = formatIsoDate(generatedAt),
            summary = stats.toWeeklySummary(
                learnedEmotionCount = emotionCount,
                activeDays = activeDays
            ),
            isCurrentPeriod = isCurrentReportPeriod(start, end)
        )
    }

    private fun distinctReports(reports: List<ReportPayloadDto>): List<ReportPayloadDto> {
        val byId = reports.filter { !it.reportId.isNullOrBlank() }.distinctBy { it.reportId }
        return byId
            .groupBy { reportPeriodKey(it) }
            .values
            .map { group -> group.maxByOrNull { it.generatedAt.orEmpty() } ?: group.first() }
            .sortedByDescending { it.generatedAt.orEmpty() }
    }

    private fun reportPeriodKey(report: ReportPayloadDto): String {
        val dataJson = parseReportData(report.data)
        val start = dataJson?.optString("start_date").orEmpty()
        val end = dataJson?.optString("end_date").orEmpty()
        return if (start.isNotBlank() && end.isNotBlank()) {
            "${report.reportType ?: "weekly"}_${start}_${end}"
        } else {
            report.reportId.orEmpty()
        }
    }

    private fun keepPdfStateIfPossible(pdfState: PdfState, reports: List<GeneratedReportUi>): PdfState {
        val knownIds = reports.map { it.id }.toSet()
        return when (pdfState) {
            is PdfState.Generated -> if (pdfState.reportId in knownIds) pdfState else PdfState.NotGenerated
            is PdfState.PreviewError -> if (pdfState.reportId in knownIds) pdfState else PdfState.NotGenerated
            PdfState.EmailSent -> pdfState
            is PdfState.EmailError -> pdfState
            is PdfState.GenerateError -> pdfState
            PdfState.Generating,
            PdfState.EmailSending,
            PdfState.NotGenerated -> pdfState
        }
    }

    private fun restorePdfStateForCurrentWeek(
        previousState: PdfState,
        reports: List<GeneratedReportUi>,
        currentWeekReport: GeneratedReportUi?,
        userId: String
    ): PdfState {
        val keptState = keepPdfStateIfPossible(previousState, reports)
        if (keptState is PdfState.Generated || keptState is PdfState.PreviewError) {
            return keptState
        }
        if (keptState == PdfState.Generating || keptState == PdfState.EmailSending) {
            return keptState
        }
        val weeklyReport = currentWeekReport ?: return PdfState.NotGenerated
        val lastSentWeeklyKey = getLastSentWeeklyKey(userId)
        val wasSent = weeklyReport.reportKey.isNotBlank() && weeklyReport.reportKey == lastSentWeeklyKey
        return if (wasSent) {
            PdfState.NotGenerated
        } else {
            PdfState.Generated(weeklyReport.id)
        }
    }

    private fun markLastSentWeeklyReport(userId: String, report: GeneratedReportUi?) {
        if (report?.type != "weekly") return
        val key = report.reportKey.takeIf { it.isNotBlank() } ?: return
        reportUiPrefs().edit().putString(lastSentWeeklyPrefKey(userId), key).apply()
    }

    private fun recordSentReport(userId: String, report: GeneratedReportUi?, recipientEmail: String?) {
        val target = report ?: return
        if (target.id.isBlank() && target.reportKey.isBlank()) return
        val record = JSONObject().apply {
            put("report_id", target.id)
            put("report_key", target.reportKey)
            put("sent_at_ms", System.currentTimeMillis())
            put("recipient_email", recipientEmail.orEmpty())
        }
        val existing = readSentReportRecords(userId)
            .filterNot { item ->
                item.optString("report_id") == target.id ||
                    (target.reportKey.isNotBlank() && item.optString("report_key") == target.reportKey)
            }
        val next = JSONArray()
        next.put(record)
        existing.take(11).forEach { next.put(it) }
        reportUiPrefs().edit().putString(sentReportHistoryPrefKey(userId), next.toString()).apply()
    }

    private fun clearLastSentWeeklyMarker(userId: String, report: GeneratedReportUi?) {
        if (report?.type != "weekly") return
        reportUiPrefs().edit().remove(lastSentWeeklyPrefKey(userId)).apply()
    }

    private fun getLastSentWeeklyKey(userId: String): String? {
        return reportUiPrefs().getString(lastSentWeeklyPrefKey(userId), null)
            ?.takeIf { it.isNotBlank() }
    }

    private fun sentReportsForUi(userId: String, reports: List<GeneratedReportUi>): List<SentReportUi> {
        val byId = reports.associateBy { it.id }
        val byKey = reports.associateBy { it.reportKey }
        return readSentReportRecords(userId)
            .mapNotNull { record ->
                val report = byId[record.optString("report_id")] ?: byKey[record.optString("report_key")]
                report?.toSentReportUi(
                    sentAtMs = record.optLong("sent_at_ms", 0L),
                    recipientEmail = record.optString("recipient_email").takeIf { it.isRealEmail() }
                )
            }
    }

    private fun readSentReportRecords(userId: String): List<JSONObject> {
        val raw = reportUiPrefs().getString(sentReportHistoryPrefKey(userId), null).orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }
    }

    private fun reportUiPrefs() =
        getApplication<Application>().getSharedPreferences(REPORT_UI_PREF, 0)

    private fun lastSentWeeklyPrefKey(userId: String): String =
        "$KEY_LAST_SENT_WEEKLY_PREFIX$userId"

    private fun sentReportHistoryPrefKey(userId: String): String =
        "$KEY_SENT_REPORT_HISTORY_PREFIX$userId"

    private fun activeReportId(): String? {
        return when (val pdfState = state.value.pdfState) {
            is PdfState.Generated -> pdfState.reportId
            is PdfState.PreviewError -> pdfState.reportId
            else -> state.value.currentReport?.id
        }?.takeIf { it.isNotBlank() }
    }

    private fun applyReportPayload(
        payload: ReportPayloadDto,
        pdfState: PdfState? = null,
        statusMessage: String? = state.value.statusMessage
    ) {
        reportPayloads = distinctReports(listOf(payload) + reportPayloads)
        val generatedReport = payload.toGeneratedReport()
        val generatedReports = reportPayloads.map { it.toGeneratedReport() }
        val payloadSummary = generatedReport.summary
        val payloadEmotions = payload.toEmotionStats()

        _state.update { current ->
            val nextSummary = if (generatedReport.type == "weekly") payloadSummary else current.weeklySummary
            val nextEmotions = payloadEmotions.ifEmpty { current.emotionStats }
            current.copy(
                weeklySummary = nextSummary,
                emotionStats = nextEmotions,
                parentSuggestion = buildParentSuggestion(nextSummary, nextEmotions),
                currentReport = generatedReport,
                currentWeekReport = generatedReport.takeIf { report -> report.type == "weekly" } ?: current.currentWeekReport,
                generatedReports = generatedReports,
                sentReports = sentReportsForUi(currentUserId(), generatedReports),
                pdfState = pdfState ?: current.pdfState,
                statusMessage = statusMessage
            )
        }
    }

    private fun GeneratedReportUi.toSentReportUi(sentAtMs: Long, recipientEmail: String?): SentReportUi {
        return SentReportUi(
            reportId = id,
            title = title,
            weekRange = weekRange,
            sentAtText = formatSentAt(sentAtMs),
            recipientEmail = recipientEmail,
            summaryText = buildReportSummaryLine(summary)
        )
    }

    private fun buildReportSummaryLine(summary: WeeklySummary): String {
        val score = summary.averageScore?.let { "${it.coerceIn(0, 100)}/100" } ?: "Chưa có điểm"
        val emotionCount = summary.learnedEmotionCount?.let { "$it/6 cảm xúc" } ?: "Chưa rõ cảm xúc"
        return "${summary.sessionsCount} lượt luyện · $score · $emotionCount"
    }

    private fun formatSentAt(sentAtMs: Long): String {
        if (sentAtMs <= 0L) return "Vừa gửi"
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).format(Date(sentAtMs))
    }

    private suspend fun openPdfPreview(reportId: String) {
        _state.update {
            it.copy(
                statusMessage = "Đang mở báo cáo...",
                pdfState = PdfState.Generated(reportId)
            )
        }

        val pages = runCatching { renderPdfPages(reportId) }.getOrDefault(emptyList())
        if (pages.isEmpty()) {
            _state.update {
                it.copy(
                    pdfState = PdfState.PreviewError(reportId),
                    statusMessage = null
                )
            }
            return
        }

        replacePreviewPages(pages)
        _state.update {
            it.copy(
                pdfState = PdfState.Generated(reportId),
                isPreviewVisible = true,
                statusMessage = null
            )
        }
    }

    private suspend fun renderPdfPages(reportId: String): List<Bitmap> = withContext(Dispatchers.IO) {
        val bytes = repository.downloadReportPdf(reportId) ?: return@withContext emptyList()
        val file = File(getApplication<Application>().cacheDir, "emogarden-report-$reportId.pdf")
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

    private fun replacePreviewPages(pages: List<Bitmap>) {
        state.value.pdfPreviewPages.forEach { it.recycle() }
        _state.update { it.copy(pdfPreviewPages = pages) }
    }

    private suspend fun savePdfToDownloads(reportId: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val fileName = "BaoCao_EmoGarden_${reportId.take(8)}.pdf"
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@runCatching false
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                if (!dir.exists()) dir.mkdirs()
                File(dir, fileName).writeBytes(bytes)
                true
            }
        }.getOrDefault(false)
    }

    override fun onCleared() {
        state.value.pdfPreviewPages.forEach { it.recycle() }
        super.onCleared()
    }
}

fun buildProgressComment(summary: WeeklySummary?): String {
    if (summary == null) return "Chưa có dữ liệu báo cáo. Bé chơi một vài trò chơi để app tạo báo cáo tiến bộ nhé."
    if (summary.sessionsCount == 0) {
        return "Bé chưa có lượt chơi trong tuần này. Hãy bắt đầu với một trò chơi cảm xúc nhé."
    }
    val avg = summary.averageScore
        ?: return "Bé đã có ${summary.sessionsCount} lượt chơi. Hãy chơi thêm để app tính điểm trung bình nhé."

    return when {
        avg >= 80 -> "Bé tiến bộ rất tốt."
        avg >= 60 -> "Bé đang tiến bộ ổn."
        avg >= 40 -> "Bé cần luyện thêm một số cảm xúc."
        else -> "Bé nên ôn lại các cảm xúc cơ bản."
    }
}

fun buildWeeklySummaryText(summary: WeeklySummary?): String {
    if (summary == null) return "Chưa có dữ liệu báo cáo. Bé chơi một vài trò chơi để app tạo báo cáo tiến bộ nhé."
    if (summary.sessionsCount == 0) return buildProgressComment(summary)

    val scoreText = summary.averageScore?.let { "${it.coerceIn(0, 100)}/100" } ?: "chưa có"
    val comparison = buildComparisonText(summary)
    return "Bé đã chơi ${summary.sessionsCount} lượt trong tuần này. Điểm trung bình là $scoreText. ${buildProgressComment(summary)}$comparison"
}

fun buildParentSuggestion(summary: WeeklySummary?, emotions: List<ReportEmotionUi>): String {
    val avg = summary?.averageScore
    val weak = emotions
        .filter { it.attempts > 0 }
        .sortedBy { it.accuracy }
        .take(2)
        .map { it.name }

    if (summary == null || summary.sessionsCount == 0) {
        return "Bé cần chơi thêm một vài màn để app đưa ra gợi ý chính xác hơn. Phụ huynh có thể bắt đầu cùng bé với Vui vẻ, Buồn bã và Tức giận."
    }

    return when {
        avg == null -> "Bé cần chơi thêm một vài lượt để app có đủ dữ liệu gợi ý. Phụ huynh có thể cùng bé đọc tình huống ngắn rồi khuyến khích bé thể hiện cảm xúc trước camera."
        avg >= 80 -> "Bé đang làm tốt. Phụ huynh có thể cho bé luyện thêm các tình huống khó hơn để củng cố khả năng nhận biết cảm xúc."
        avg >= 60 -> "Bé nên luyện thêm các cảm xúc có điểm thấp. Hãy cùng bé đọc tình huống ngắn, hỏi 'Con nghĩ bạn nhỏ đang cảm thấy thế nào?' rồi khuyến khích bé thể hiện cảm xúc trước camera."
        avg >= 40 -> {
            val target = weak.joinToString(" và ").ifBlank { "các cảm xúc còn thấp" }
            "Bé cần luyện thêm cảm xúc $target. Phụ huynh có thể cùng bé đọc các tình huống ngắn, hỏi 'Con nghĩ bạn nhỏ đang cảm thấy thế nào?' rồi khuyến khích bé thể hiện cảm xúc trước camera."
        }
        else -> "Bé cần ôn lại các cảm xúc cơ bản. Phụ huynh nên bắt đầu với Vui vẻ, Buồn bã và Tức giận."
    }
}

fun formatReportScore(value: Int?): String = value?.let { "${it.coerceIn(0, 100)}/100" } ?: "Chưa có"

fun formatReportMinutes(value: Int?): String {
    val minutes = value ?: return "Chưa có"
    if (minutes < 60) return "$minutes phút"
    val hours = minutes / 60f
    return String.format(Locale("vi", "VN"), "%.1f giờ", hours)
}

private fun buildComparisonText(summary: WeeklySummary): String {
    val current = summary.averageScore ?: return ""
    val previous = summary.previousAverageScore ?: return ""
    val diff = current - previous
    return when {
        diff > 0 -> " Tăng $diff điểm so với tuần trước."
        diff < 0 -> " Giảm ${-diff} điểm so với tuần trước."
        else -> " Điểm trung bình giữ ổn định so với tuần trước."
    }
}

private fun UserProfileDto?.reportEmail(): String? {
    if (this == null) return null
    return extractEmail(child?.reportPref)
}

private fun extractEmail(rawValue: String?): String? {
    val raw = rawValue?.trim().orEmpty()
    if (raw.isBlank()) return null
    if (raw.isRealEmail()) return raw
    return runCatching {
        val json = JSONObject(raw)
        listOf("parent_email", "email", "receiver_email")
            .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isRealEmail() } }
    }.getOrNull()
}

private fun String?.isRealEmail(): Boolean {
    val value = this?.trim().orEmpty()
    if (value.isBlank() || value.endsWith("@local.invalid", ignoreCase = true)) return false
    return Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value)
}

private fun parseReportData(raw: String?): JSONObject? {
    return runCatching { JSONObject(raw.orEmpty()) }.getOrNull()
}

private fun buildWeekRange(start: String, end: String): String {
    if (start.isBlank() || end.isBlank()) return "Tuần hiện tại"
    return "${start.take(5)} - ${end.take(5)}"
}

private fun isCurrentReportPeriod(start: String, end: String): Boolean {
    if (start.isBlank() || end.isBlank()) return false
    val parser = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).apply {
        isLenient = false
    }
    return runCatching {
        val startDate = parser.parse(start) ?: return false
        val endDate = parser.parse(end) ?: return false
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        !today.before(startDate) && !today.after(endDate)
    }.getOrDefault(false)
}

private fun formatIsoDate(value: String?): String {
    val date = value?.substringBefore("T")?.substringBefore(" ").orEmpty()
    val parts = date.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else date
}

private fun reportTitle(type: String?): String {
    return when (type?.lowercase(Locale.US)) {
        "daily" -> "Báo cáo ngày"
        "monthly" -> "Báo cáo tháng"
        else -> "Báo cáo tuần"
    }
}

private fun emotionEmoji(name: String): String {
    val normalized = normalizeText(name)
    return when {
        "vui" in normalized || "happy" in normalized -> "happy"
        "buon" in normalized || "sad" in normalized -> "sad"
        "ngac" in normalized || "surprise" in normalized -> "surprise"
        "tuc" in normalized || "gian" in normalized || "angry" in normalized -> "angry"
        "so" in normalized || "fear" in normalized -> "fear"
        "ghe" in normalized || "disgust" in normalized -> "disgust"
        else -> "neutral"
    }
}

private fun normalizeText(value: String): String {
    val normalized = Normalizer.normalize(value.lowercase(Locale("vi", "VN")), Normalizer.Form.NFD)
    return normalized.replace(Regex("\\p{Mn}+"), "")
}
