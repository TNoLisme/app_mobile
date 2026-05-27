package com.example.appmobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class LegalDocumentType {
    PrivacyPolicy,
    TermsOfUse
}

@Composable
fun LegalConsentDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    LegalDialogShell(onDismiss = onDismiss) {
        LegalDialogHeader(
            icon = "lock",
            title = "Chính sách & điều khoản",
            subtitle = "Trước khi vào app, bạn cần đồng ý với cách ứng dụng thu thập và sử dụng dữ liệu."
        )
        LegalSummaryCard(
            title = "Tóm tắt nhanh",
            items = listOf(
                "App lưu tài khoản, tiến độ học và tùy chọn của bé để đồng bộ trên thiết bị.",
                "Camera chỉ dùng khi chơi game biểu cảm và không lưu ảnh/video của bé.",
                "Thông báo chỉ dùng để nhắc học hằng ngày khi bạn bật tính năng này.",
                "Bạn có thể xem chi tiết Chính sách quyền riêng tư và Điều khoản sử dụng trong Cài đặt."
            )
        )
        LegalDialogActions(
            primaryText = "Đồng ý & tiếp tục",
            onPrimary = onAccept,
            secondaryText = "Thoát",
            onSecondary = onDismiss
        )
    }
}

@Composable
fun LegalDocumentDialog(
    type: LegalDocumentType,
    onDismiss: () -> Unit
) {
    val content = legalDocumentContent(type)
    LegalDialogShell(onDismiss = onDismiss) {
        LegalDialogHeader(icon = content.icon, title = content.title, subtitle = content.subtitle)
        content.sections.forEach { section ->
            LegalSummaryCard(title = section.title, items = section.items)
        }
        LegalDialogActions(primaryText = "Đóng", onPrimary = onDismiss)
    }
}

@Composable
fun SupportContactDialog(
    onDismiss: () -> Unit,
    onEmailSupport: () -> Unit,
    onCallSupport: () -> Unit
) {
    LegalDialogShell(onDismiss = onDismiss) {
        LegalDialogHeader(
            icon = "phone",
            title = "Liên hệ hỗ trợ",
            subtitle = "Chọn cách liên hệ nếu cần trợ giúp hoặc góp ý về ứng dụng."
        )
        LegalSummaryCard(
            title = "Kênh hỗ trợ",
            items = listOf(
                "Email: support@example.com",
                "Điện thoại: +84 900 000 000",
                "Phản hồi trong giờ làm việc: 08:00 - 17:00"
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LegalActionButton(text = "Gửi email", onClick = onEmailSupport, modifier = Modifier.weight(1f))
            LegalActionButton(text = "Gọi hỗ trợ", onClick = onCallSupport, modifier = Modifier.weight(1f), tonal = true)
        }
        LegalDialogActions(primaryText = "Đóng", onPrimary = onDismiss)
    }
}

private data class LegalSection(
    val title: String,
    val items: List<String>
)

private data class LegalDocumentContent(
    val icon: String,
    val title: String,
    val subtitle: String,
    val sections: List<LegalSection>
)

private fun legalDocumentContent(type: LegalDocumentType): LegalDocumentContent {
    return when (type) {
        LegalDocumentType.PrivacyPolicy -> LegalDocumentContent(
            icon = "lock",
            title = "Chính sách quyền riêng tư",
            subtitle = "Tóm tắt cách app thu thập, dùng và bảo vệ dữ liệu.",
            sections = listOf(
                LegalSection(
                    title = "Dữ liệu được dùng",
                    items = listOf(
                        "Tài khoản, tên hiển thị, email và số điện thoại phụ huynh.",
                        "Tiến độ học, điểm số và lịch sử trò chơi của bé.",
                        "Tùy chọn giao diện, âm thanh và nhắc nhở học tập."
                    )
                ),
                LegalSection(
                    title = "Quyền truy cập thiết bị",
                    items = listOf(
                        "Camera chỉ dùng trong game cần nhận diện biểu cảm.",
                        "Thông báo chỉ dùng để nhắc học hằng ngày.",
                        "Bạn có thể tắt các quyền này trong cài đặt điện thoại bất cứ lúc nào."
                    )
                ),
                LegalSection(
                    title = "Cam kết bảo vệ",
                    items = listOf(
                        "App không chủ động bán dữ liệu cá nhân cho bên thứ ba.",
                        "Ảnh và video không được lưu nếu không cần cho tính năng.",
                        "Dữ liệu chỉ dùng để vận hành, đồng bộ và hỗ trợ học tập."
                    )
                )
            )
        )

        LegalDocumentType.TermsOfUse -> LegalDocumentContent(
            icon = "document",
            title = "Điều khoản sử dụng",
            subtitle = "Các điều cần lưu ý khi dùng ứng dụng cho bé học và chơi.",
            sections = listOf(
                LegalSection(
                    title = "Cách sử dụng",
                    items = listOf(
                        "Ứng dụng dành cho học tập và giải trí có hướng dẫn của người lớn.",
                        "Phụ huynh chịu trách nhiệm giám sát việc sử dụng của trẻ.",
                        "Không được can thiệp, sửa đổi hay lạm dụng app vào mục đích khác."
                    )
                ),
                LegalSection(
                    title = "Quyền và trách nhiệm",
                    items = listOf(
                        "Bạn đồng ý cung cấp thông tin đúng khi tạo hoặc quản lý tài khoản.",
                        "Bạn có thể tắt nhắc nhở, quyền camera và quyền thông báo trong máy.",
                        "Các tính năng có thể thay đổi theo từng bản cập nhật."
                    )
                ),
                LegalSection(
                    title = "Giới hạn trách nhiệm",
                    items = listOf(
                        "App được cung cấp theo trạng thái hiện có để hỗ trợ học tập.",
                        "Nếu có lỗi hoặc cần trợ giúp, hãy dùng mục Liên hệ hỗ trợ trong Cài đặt.",
                        "Khi tiếp tục sử dụng app, bạn chấp nhận các điều khoản này."
                    )
                )
            )
        )
    }
}

@Composable
private fun LegalDialogShell(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
                shape = RoundedCornerShape(24.dp),
                color = EgDesign.card,
                border = BorderStroke(1.dp, EgDesign.cardBorder),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun LegalDialogHeader(
    icon: String,
    title: String,
    subtitle: String
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = EgDesign.accentSoft,
            border = BorderStroke(1.dp, EgDesign.cardBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                EgVectorEmojiIcon(icon, size = 22.dp, tint = EgDesign.primary)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = EgDesign.textPrimary, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun LegalSummaryCard(
    title: String,
    items: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EgDesign.cardSoft, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = EgDesign.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", color = EgDesign.primaryDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(item, color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LegalActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tonal: Boolean = false
) {
    val background = if (tonal) EgDesign.cardSoft else EgDesign.primary
    val textColor = if (tonal) EgDesign.primaryDark else Color.White
    Surface(
        modifier = modifier.height(44.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(EgDesign.pillRadius),
        color = background,
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
            Text(text, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LegalDialogActions(
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (secondaryText != null && onSecondary != null) {
            LegalActionButton(secondaryText, onSecondary, modifier = Modifier.weight(1f), tonal = true)
            LegalActionButton(primaryText, onPrimary, modifier = Modifier.weight(1f))
        } else {
            LegalActionButton(primaryText, onPrimary, modifier = Modifier.weight(1f))
        }
    }
}
