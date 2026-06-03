package com.example.appmobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    onDismiss: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTermsOfUse: () -> Unit
) {
    LegalDialogShell(onDismiss = onDismiss) {
        LegalDialogHeader(
            icon = "lock",
            title = "Chính sách & Điều khoản",
            subtitle = "Phụ huynh/người giám hộ vui lòng đọc và đồng ý trước khi bé sử dụng EmoGarden."
        )
        LegalSummaryCard(
            title = "Tóm tắt nhanh",
            items = listOf(
                "EmoGarden lưu tên bé, tiến độ học, kết quả chơi game và báo cáo để cá nhân hóa trải nghiệm.",
                "Camera chỉ dùng để nhận diện biểu cảm khi chơi game. App không tự động lưu video.",
                "Ảnh Photobooth chỉ được lưu khi người dùng chọn lưu.",
                "Báo cáo chỉ được gửi đến email phụ huynh khi có xác nhận.",
                "Phụ huynh có thể xem lại, thay đổi hoặc xóa dữ liệu trong Cài đặt/Khu vực phụ huynh."
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LegalTextLink(
                text = "Xem Chính sách quyền riêng tư",
                onClick = onOpenPrivacyPolicy,
                modifier = Modifier.weight(1f)
            )
            LegalTextLink(
                text = "Xem Điều khoản sử dụng",
                onClick = onOpenTermsOfUse,
                modifier = Modifier.weight(1f)
            )
        }
        LegalDialogActions(
            primaryText = "Đồng ý & tiếp tục",
            onPrimary = onAccept,
            secondaryText = "Không đồng ý",
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
            subtitle = "Cách EmoGarden lưu, sử dụng và bảo vệ dữ liệu học tập của bé.",
            sections = listOf(
                LegalSection(
                    title = "Dữ liệu được lưu",
                    items = listOf(
                        "Tên đăng nhập, tên hiển thị, email tài khoản và email phụ huynh nếu được cung cấp.",
                        "Tiến độ học cảm xúc, kết quả chơi game, báo cáo tuần và trạng thái Vườn cảm xúc.",
                        "Tùy chọn giao diện, âm thanh, nhắc học và các cài đặt trên thiết bị."
                    )
                ),
                LegalSection(
                    title = "Camera",
                    items = listOf(
                        "Camera dùng để nhận diện biểu cảm trong game camera.",
                        "App không tự động lưu video khi bé chơi game biểu cảm.",
                        "Phụ huynh có thể tắt quyền camera trong cài đặt thiết bị bất cứ lúc nào."
                    )
                ),
                LegalSection(
                    title = "Photobooth",
                    items = listOf(
                        "Photobooth chỉ tạo ảnh khi người dùng chủ động chụp.",
                        "Ảnh ghép chỉ được lưu vào máy hoặc album trong app khi người dùng chọn lưu.",
                        "Ảnh không được tự động gửi ra ngoài."
                    )
                ),
                LegalSection(
                    title = "Báo cáo email",
                    items = listOf(
                        "Báo cáo tiến bộ chỉ được gửi đến email phụ huynh đã lưu.",
                        "Trước khi gửi báo cáo, app luôn yêu cầu xác nhận.",
                        "Phụ huynh có thể cập nhật email nhận báo cáo trong Khu vực phụ huynh."
                    )
                ),
                LegalSection(
                    title = "Quyền của phụ huynh",
                    items = listOf(
                        "Phụ huynh có thể xem lại, chỉnh sửa hoặc xóa dữ liệu học tập trong Cài đặt/Khu vực phụ huynh.",
                        "Phụ huynh có thể thay đổi quyền camera, thông báo và các tùy chọn học tập.",
                        "Nếu cần hỗ trợ, hãy dùng mục Liên hệ hỗ trợ trong Cài đặt."
                    )
                )
            )
        )

        LegalDocumentType.TermsOfUse -> LegalDocumentContent(
            icon = "document",
            title = "Điều khoản sử dụng",
            subtitle = "Các điều cần lưu ý khi dùng EmoGarden cho bé học và chơi.",
            sections = listOf(
                LegalSection(
                    title = "Sử dụng cùng phụ huynh",
                    items = listOf(
                        "EmoGarden dành cho bé học cảm xúc với sự đồng hành của phụ huynh/người giám hộ.",
                        "Phụ huynh chịu trách nhiệm giám sát việc bé sử dụng app và các thao tác liên quan email, ảnh, dữ liệu.",
                        "Các nội dung trong app hỗ trợ học tập, không thay thế tư vấn y tế hoặc chuyên môn."
                    )
                ),
                LegalSection(
                    title = "Tính năng cần xác nhận",
                    items = listOf(
                        "Gửi báo cáo qua email cần có email phụ huynh và xác nhận trước khi gửi.",
                        "Lưu ảnh Photobooth chỉ thực hiện khi người dùng chọn lưu.",
                        "Xóa dữ liệu hoặc đặt lại tiến độ cần xác nhận trong khu vực phù hợp."
                    )
                ),
                LegalSection(
                    title = "Cài đặt và cập nhật",
                    items = listOf(
                        "Phụ huynh có thể điều chỉnh âm thanh, nhắc học, camera và quyền riêng tư trong Cài đặt.",
                        "Tính năng có thể thay đổi theo từng bản cập nhật để cải thiện trải nghiệm học tập.",
                        "Khi chính sách hoặc điều khoản thay đổi, app sẽ yêu cầu phụ huynh đồng ý lại."
                    )
                ),
                LegalSection(
                    title = "Liên hệ hỗ trợ",
                    items = listOf(
                        "Nếu gặp lỗi hoặc cần góp ý, phụ huynh có thể dùng mục Liên hệ hỗ trợ trong Cài đặt.",
                        "Khi tiếp tục sử dụng app, phụ huynh xác nhận đã đọc và đồng ý với các điều khoản này."
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
                shape = RoundedCornerShape(EgDesign.radiusXLarge),
                color = EgDesign.card,
                border = BorderStroke(1.dp, EgDesign.cardBorder),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
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
            .background(EgDesign.cardSoft, RoundedCornerShape(EgDesign.radiusMedium))
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
private fun LegalTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.egTactileClick(onClick = onClick),
        shape = RoundedCornerShape(EgDesign.radiusMedium),
        color = EgDesign.accentSoft,
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            color = EgDesign.primaryDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
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
        modifier = modifier.height(44.dp).egTactileClick(onClick = onClick),
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
