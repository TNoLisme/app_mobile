from __future__ import annotations

import json
from datetime import datetime
from io import BytesIO
from pathlib import Path

REPORTLAB_AVAILABLE = True

try:
    from reportlab.lib import colors
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
    from reportlab.lib.units import inch
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle
except Exception:  # pragma: no cover - depends on runtime package
    REPORTLAB_AVAILABLE = False


class ReportPdfService:
    def __init__(self):
        self.font_regular = "Helvetica"
        self.font_bold = "Helvetica-Bold"
        self._register_unicode_font()

    def _register_unicode_font(self) -> None:
        if not REPORTLAB_AVAILABLE:
            return

        font_candidates = [
            Path("C:/Windows/Fonts/arial.ttf"),
            Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
            Path("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"),
        ]
        bold_candidates = [
            Path("C:/Windows/Fonts/arialbd.ttf"),
            Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
            Path("/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"),
        ]

        regular = next((p for p in font_candidates if p.exists()), None)
        bold = next((p for p in bold_candidates if p.exists()), None)

        if not regular or not bold:
            return

        try:
            pdfmetrics.registerFont(TTFont("EmoUnicode", str(regular)))
            pdfmetrics.registerFont(TTFont("EmoUnicode-Bold", str(bold)))
            self.font_regular = "EmoUnicode"
            self.font_bold = "EmoUnicode-Bold"
        except Exception:
            # Keep default fonts if registration fails
            pass

    def _styles(self):
        styles = getSampleStyleSheet()
        return {
            "title": ParagraphStyle(
                "ReportTitle",
                parent=styles["Heading1"],
                fontName=self.font_bold,
                fontSize=21,
                leading=26,
                alignment=1,
                textColor=colors.HexColor("#0B3A6E"),
                spaceAfter=10,
            ),
            "subtitle": ParagraphStyle(
                "ReportSubtitle",
                parent=styles["Normal"],
                fontName=self.font_regular,
                fontSize=11,
                leading=15,
                alignment=1,
                textColor=colors.HexColor("#5D6B7A"),
                spaceAfter=18,
            ),
            "section": ParagraphStyle(
                "ReportSection",
                parent=styles["Heading2"],
                fontName=self.font_bold,
                fontSize=13,
                leading=17,
                textColor=colors.HexColor("#0B3A6E"),
                spaceBefore=8,
                spaceAfter=7,
            ),
            "body": ParagraphStyle(
                "ReportBody",
                parent=styles["Normal"],
                fontName=self.font_regular,
                fontSize=10,
                leading=14,
                textColor=colors.HexColor("#1E293B"),
            ),
            "bullet": ParagraphStyle(
                "ReportBullet",
                parent=styles["Normal"],
                fontName=self.font_regular,
                fontSize=10,
                leading=14,
                textColor=colors.HexColor("#1E293B"),
                leftIndent=10,
                spaceAfter=3,
            ),
        }

    def _to_dict(self, report_data_json: str | None) -> dict:
        if not report_data_json:
            return {}
        try:
            return json.loads(report_data_json)
        except Exception:
            return {}

    def _build_stats_table(self, styles: dict, report_data: dict) -> Table:
        data = [
            ["Chỉ số", "Giá trị"],
            ["Tổng phiên chơi", str(report_data.get("total_sessions", 0))],
            ["Điểm trung bình", str(report_data.get("avg_score", 0))],
            ["Thời gian luyện (phút)", str(report_data.get("total_playtime_minutes", 0))],
            ["Số trò đã chơi", str(report_data.get("total_games", 0))],
            ["Số bản ghi tiến trình", str(report_data.get("progress_count", 0))],
        ]
        table = Table(data, colWidths=[2.4 * inch, 3.7 * inch])
        table.setStyle(
            TableStyle(
                [
                    ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#DDEEFF")),
                    ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#0B3A6E")),
                    ("FONTNAME", (0, 0), (-1, 0), self.font_bold),
                    ("FONTNAME", (0, 1), (-1, -1), self.font_regular),
                    ("FONTSIZE", (0, 0), (-1, -1), 10),
                    ("ALIGN", (0, 0), (-1, -1), "LEFT"),
                    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                    ("GRID", (0, 0), (-1, -1), 0.6, colors.HexColor("#D0DDEA")),
                    ("TOPPADDING", (0, 0), (-1, -1), 7),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
                ]
            )
        )
        return table

    def generate_pdf(
        self,
        child_name: str,
        report_type: str,
        summary: str,
        report_data_json: str | None,
        generated_at: datetime | None = None,
    ) -> bytes | None:
        if not REPORTLAB_AVAILABLE:
            return None

        report_data = self._to_dict(report_data_json)
        styles = self._styles()
        generated_time = generated_at or datetime.utcnow()

        period_label = {"weekly": "tuần", "monthly": "tháng", "daily": "ngày"}.get(report_type, report_type)
        title = "BÁO CÁO TIẾN ĐỘ HỌC CẢM XÚC"
        subtitle = (
            f"Học viên: {child_name}<br/>"
            f"Kỳ báo cáo: {period_label} ({report_data.get('start_date', '-')}"
            f" - {report_data.get('end_date', '-')})<br/>"
            f"Thời điểm tạo: {generated_time.strftime('%d/%m/%Y %H:%M')}"
        )

        buffer = BytesIO()
        doc = SimpleDocTemplate(
            buffer,
            pagesize=A4,
            leftMargin=0.55 * inch,
            rightMargin=0.55 * inch,
            topMargin=0.55 * inch,
            bottomMargin=0.55 * inch,
            title=f"BaoCao_{child_name}_{generated_time.strftime('%Y%m%d')}",
        )

        elements = [
            Paragraph(title, styles["title"]),
            Paragraph(subtitle, styles["subtitle"]),
            Paragraph("Tổng quan", styles["section"]),
            Paragraph(summary or "Chưa có dữ liệu tổng quan.", styles["body"]),
            Spacer(1, 8),
            self._build_stats_table(styles, report_data),
            Spacer(1, 12),
            Paragraph("Thành tựu nổi bật", styles["section"]),
        ]

        achievements = report_data.get("achievements", [])
        if achievements:
            for item in achievements[:8]:
                elements.append(Paragraph(f"• {item}", styles["bullet"]))
        else:
            elements.append(Paragraph("• Chưa có thành tựu nổi bật trong kỳ này.", styles["bullet"]))

        elements.append(Spacer(1, 10))
        elements.append(Paragraph("Trò chơi bé luyện nhiều", styles["section"]))
        games_stats = report_data.get("games_stats", [])
        if games_stats:
            for game in games_stats[:5]:
                name = game.get("game_name", "Trò chơi")
                sessions = game.get("sessions", 0)
                avg_score = game.get("avg_score", 0)
                elements.append(
                    Paragraph(f"• {name}: {sessions} lượt, điểm TB {avg_score}", styles["bullet"])
                )
        else:
            elements.append(Paragraph("• Chưa có dữ liệu trò chơi.", styles["bullet"]))

        elements.append(Spacer(1, 14))
        elements.append(
            Paragraph(
                "Báo cáo được tạo tự động từ EmoGarden và gửi cho phụ huynh theo thiết lập tài khoản.",
                styles["body"],
            )
        )

        doc.build(elements)
        buffer.seek(0)
        return buffer.read()
