from __future__ import annotations

import json
import unicodedata
from datetime import datetime
from html import escape
from io import BytesIO
from pathlib import Path

REPORTLAB_AVAILABLE = True

try:
    from reportlab.graphics.charts.barcharts import VerticalBarChart
    from reportlab.graphics.shapes import Drawing, Line, Rect, String
    from reportlab.lib import colors
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
    from reportlab.lib.units import inch
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle
except Exception:  # pragma: no cover - depends on runtime package
    REPORTLAB_AVAILABLE = False

from app.services.report_data import ReportData, build_report_data


class ReportPdfService:
    EMOTION_NAMES = ["Vui vẻ", "Buồn bã", "Tức giận", "Sợ hãi", "Ngạc nhiên", "Ghê tởm"]
    EMOTION_SHORT_NAMES = ["Vui", "Buồn", "Giận", "Sợ", "Ngạc", "Ghê"]
    EMOTION_ALIASES = {
        "Vui vẻ": ("vui ve", "happy", "joy", "smile"),
        "Buồn bã": ("buon ba", "sad", "sadness"),
        "Tức giận": ("tuc gian", "angry", "anger"),
        "Sợ hãi": ("so hai", "fear", "fearful"),
        "Ngạc nhiên": ("ngac nhien", "surprised", "surprise"),
        "Ghê tởm": ("ghe tom", "disgusted", "disgust"),
    }

    def __init__(self):
        self.font_regular = "Helvetica"
        self.font_bold = "Helvetica-Bold"
        self.font_italic = "Helvetica-Oblique"
        self.page_width = 7.0 * inch if REPORTLAB_AVAILABLE else 0
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
        italic_candidates = [
            Path("C:/Windows/Fonts/ariali.ttf"),
            Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Oblique.ttf"),
            Path("/usr/share/fonts/truetype/liberation/LiberationSans-Italic.ttf"),
        ]

        regular = next((p for p in font_candidates if p.exists()), None)
        bold = next((p for p in bold_candidates if p.exists()), None)
        italic = next((p for p in italic_candidates if p.exists()), None)

        if not regular or not bold:
            return

        try:
            pdfmetrics.registerFont(TTFont("EmoUnicode", str(regular)))
            pdfmetrics.registerFont(TTFont("EmoUnicode-Bold", str(bold)))
            if italic:
                pdfmetrics.registerFont(TTFont("EmoUnicode-Italic", str(italic)))
            else:
                pdfmetrics.registerFont(TTFont("EmoUnicode-Italic", str(regular)))
            self.font_regular = "EmoUnicode"
            self.font_bold = "EmoUnicode-Bold"
            self.font_italic = "EmoUnicode-Italic"
        except Exception:
            # Keep default fonts if registration fails.
            pass

    def _styles(self) -> dict[str, ParagraphStyle]:
        styles = getSampleStyleSheet()
        return {
            "title": ParagraphStyle(
                "ReportTitle",
                parent=styles["Heading1"],
                fontName=self.font_bold,
                fontSize=22,
                leading=28,
                alignment=1,
                textColor=colors.HexColor("#0B3A6E"),
                spaceAfter=8,
            ),
            "subtitle": ParagraphStyle(
                "ReportSubtitle",
                parent=styles["Normal"],
                fontName=self.font_regular,
                fontSize=10,
                leading=15,
                alignment=1,
                textColor=colors.HexColor("#5D6B7A"),
                spaceAfter=14,
            ),
            "section": ParagraphStyle(
                "ReportSection",
                parent=styles["Heading2"],
                fontName=self.font_bold,
                fontSize=12,
                leading=16,
                textColor=colors.white,
            ),
            "body": ParagraphStyle(
                "ReportBody",
                parent=styles["Normal"],
                fontName=self.font_regular,
                fontSize=9.5,
                leading=14,
                textColor=colors.HexColor("#1F2937"),
            ),
            "small": ParagraphStyle(
                "ReportSmall",
                parent=styles["Normal"],
                fontName=self.font_regular,
                fontSize=8,
                leading=11,
                textColor=colors.HexColor("#64748B"),
            ),
            "metric_value": ParagraphStyle(
                "MetricValue",
                parent=styles["Normal"],
                fontName=self.font_bold,
                fontSize=16,
                leading=20,
                alignment=1,
                textColor=colors.HexColor("#0B72C6"),
            ),
            "metric_label": ParagraphStyle(
                "MetricLabel",
                parent=styles["Normal"],
                fontName=self.font_regular,
                fontSize=8.5,
                leading=11,
                alignment=1,
                textColor=colors.HexColor("#475569"),
            ),
            "footer": ParagraphStyle(
                "ReportFooter",
                parent=styles["Normal"],
                fontName=self.font_italic,
                fontSize=7.5,
                leading=10,
                alignment=1,
                textColor=colors.HexColor("#64748B"),
            ),
        }

    def _html(self, text: object) -> str:
        return escape(str(text or "")).replace("\n", "<br/>")

    def _paragraph(self, text: object, style: ParagraphStyle) -> Paragraph:
        return Paragraph(self._html(text), style)

    def _to_dict(self, report_data_json: str | None) -> dict:
        if not report_data_json:
            return {}
        try:
            parsed = json.loads(report_data_json)
        except Exception:
            return {}
        return parsed if isinstance(parsed, dict) else {}

    def _safe_float(self, value: object, default: float = 0.0) -> float:
        try:
            if value is None:
                return default
            return float(value)
        except (TypeError, ValueError):
            return default

    def _safe_int(self, value: object, default: int = 0) -> int:
        try:
            if value is None:
                return default
            return int(float(value))
        except (TypeError, ValueError):
            return default

    def _strip_accents(self, text: str) -> str:
        normalized = unicodedata.normalize("NFKD", text)
        return "".join(ch for ch in normalized if not unicodedata.combining(ch)).lower()

    def _score_text(self, value: object) -> str:
        if value is None:
            return "Chưa có"
        return f"{round(self._safe_float(value))}/100"

    def _period_label(self, report_type: str) -> str:
        return {"weekly": "Tuần", "monthly": "Tháng", "daily": "Ngày"}.get(report_type, report_type.title())

    def _period_range(self, report_data: dict) -> str:
        start_date = report_data.get("start_date") or "-"
        end_date = report_data.get("end_date") or "-"
        return f"{start_date} - {end_date}"

    def _section_bar(self, text: str, color: str = "#5BB3F5") -> Drawing:
        drawing = Drawing(self.page_width, 0.38 * inch)
        rect = Rect(0, 0, self.page_width, 0.38 * inch)
        rect.fillColor = colors.HexColor(color)
        rect.strokeColor = colors.HexColor(color)
        drawing.add(rect)

        label = String(0.22 * inch, 0.12 * inch, text)
        label.fontName = self.font_bold
        label.fontSize = 11
        label.fillColor = colors.white
        drawing.add(label)
        return drawing

    def _top_line(self) -> Drawing:
        drawing = Drawing(self.page_width, 0.08 * inch)
        line = Line(0, 0, self.page_width, 0)
        line.strokeColor = colors.HexColor("#5BB3F5")
        line.strokeWidth = 3
        drawing.add(line)
        return drawing

    def _info_table(self, styles: dict[str, ParagraphStyle], report: ReportData, generated_at: datetime) -> Table:
        rows = [
            ["Tên bé", report.child_name or "Bé"],
            ["Kỳ báo cáo", report.period_display],
            ["Ngày tạo", generated_at.strftime("%d/%m/%Y lúc %H:%M")],
            ["Ứng dụng", "EmoGarden - học và luyện biểu cảm cảm xúc"],
        ]
        table = Table(rows, colWidths=[1.65 * inch, 5.35 * inch])
        table.setStyle(
            TableStyle(
                [
                    ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#F2FAFF")),
                    ("TEXTCOLOR", (0, 0), (-1, -1), colors.HexColor("#1F2937")),
                    ("FONTNAME", (0, 0), (0, -1), self.font_bold),
                    ("FONTNAME", (1, 0), (1, -1), self.font_regular),
                    ("FONTSIZE", (0, 0), (-1, -1), 9),
                    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 10),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 10),
                    ("TOPPADDING", (0, 0), (-1, -1), 7),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
                    ("BOX", (0, 0), (-1, -1), 0.6, colors.HexColor("#CFE3F7")),
                    ("INNERGRID", (0, 0), (-1, -1), 0.25, colors.HexColor("#E2EDF8")),
                ]
            )
        )
        return table

    def _summary_box(self, styles: dict[str, ParagraphStyle], summary: str) -> Table:
        content = self._paragraph(summary or "Chưa có dữ liệu báo cáo trong kỳ này.", styles["body"])
        table = Table([[content]], colWidths=[self.page_width])
        table.setStyle(
            TableStyle(
                [
                    ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#F0F8FF")),
                    ("BOX", (0, 0), (-1, -1), 0.6, colors.HexColor("#B7DDF9")),
                    ("LEFTPADDING", (0, 0), (-1, -1), 12),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 12),
                    ("TOPPADDING", (0, 0), (-1, -1), 10),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 10),
                ]
            )
        )
        return table

    def _metric_card(self, styles: dict[str, ParagraphStyle], value: str, label: str) -> Table:
        table = Table(
            [
                [self._paragraph(value, styles["metric_value"])],
                [self._paragraph(label, styles["metric_label"])],
            ],
            colWidths=[2.15 * inch],
            rowHeights=[0.34 * inch, 0.24 * inch],
        )
        table.setStyle(
            TableStyle(
                [
                    ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#F1FAEE")),
                    ("BOX", (0, 0), (-1, -1), 0.6, colors.HexColor("#CFE8D1")),
                    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 8),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 8),
                    ("TOPPADDING", (0, 0), (-1, -1), 4),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
                ]
            )
        )
        return table

    def _emotion_rows(self, report_data: dict) -> list[dict]:
        raw_stats = report_data.get("emotion_stats") or {}
        if not isinstance(raw_stats, dict):
            raw_stats = {}

        normalized_lookup = {
            self._strip_accents(str(key)): value
            for key, value in raw_stats.items()
            if isinstance(value, dict)
        }

        rows: list[dict] = []
        for name in self.EMOTION_NAMES:
            aliases = (self._strip_accents(name), *self.EMOTION_ALIASES.get(name, ()))
            raw = next((normalized_lookup[alias] for alias in aliases if alias in normalized_lookup), {})
            correct = self._safe_int(raw.get("correct"), 0)
            incorrect = self._safe_int(raw.get("incorrect"), 0)
            attempts = self._safe_int(raw.get("attempts"), correct + incorrect)
            accuracy = raw.get("accuracy")
            accuracy_value = self._safe_float(accuracy, 0.0)
            if attempts > 0 and accuracy is None:
                accuracy_value = correct * 100.0 / attempts
            rows.append(
                {
                    "name": name,
                    "correct": correct,
                    "incorrect": incorrect,
                    "attempts": attempts,
                    "accuracy": round(accuracy_value),
                }
            )
        return rows

    def _learned_emotion_count(self, report_data: dict) -> int:
        return sum(1 for row in self._emotion_rows(report_data) if row["attempts"] > 0)

    def _overview_metrics(self, styles: dict[str, ParagraphStyle], report: ReportData) -> Table:
        total_sessions = report.sessions_count
        avg_score = report.average_score_text
        learned_emotions = report.learned_emotion_count
        total_games = len(report.game_stats)

        cards = [
            self._metric_card(styles, str(total_sessions), "Lượt chơi"),
            self._metric_card(styles, avg_score, "Điểm trung bình"),
            self._metric_card(styles, f"{learned_emotions}/{report.total_emotion_count}", "Cảm xúc đã luyện"),
        ]
        if total_games > 0:
            cards.append(self._metric_card(styles, str(total_games), "Trò chơi đã luyện"))

        rows = [cards[:3]]
        if len(cards) > 3:
            rows.append(cards[3:] + [""] * (3 - len(cards[3:])))

        table = Table(rows, colWidths=[2.25 * inch, 2.25 * inch, 2.25 * inch])
        table.setStyle(
            TableStyle(
                [
                    ("ALIGN", (0, 0), (-1, -1), "CENTER"),
                    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 3),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 3),
                    ("TOPPADDING", (0, 0), (-1, -1), 3),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
                ]
            )
        )
        return table

    def _daily_sessions_chart(self, report_data: dict) -> Drawing | None:
        daily_sessions = report_data.get("daily_sessions") or {}
        if not isinstance(daily_sessions, dict) or not daily_sessions:
            return None

        labels = [str(label).replace("\n", " ") for label in daily_sessions.keys()]
        values = [self._safe_int(value, 0) for value in daily_sessions.values()]
        if not any(values):
            return None

        drawing = Drawing(self.page_width, 2.1 * inch)
        chart = VerticalBarChart()
        chart.x = 0.45 * inch
        chart.y = 0.35 * inch
        chart.height = 1.35 * inch
        chart.width = self.page_width - 0.9 * inch
        chart.data = [values]
        chart.categoryAxis.categoryNames = labels
        chart.categoryAxis.labels.fontName = self.font_regular
        chart.categoryAxis.labels.fontSize = 7
        chart.categoryAxis.labels.boxAnchor = "n"
        chart.valueAxis.valueMin = 0
        chart.valueAxis.valueMax = max(values) + 1
        chart.valueAxis.valueStep = max(1, max(values) // 4)
        chart.valueAxis.labels.fontName = self.font_regular
        chart.valueAxis.labels.fontSize = 8
        chart.bars[0].fillColor = colors.HexColor("#5BB3F5")
        chart.bars.strokeColor = colors.white
        drawing.add(chart)
        return drawing

    def _emotion_chart(self, report_data: dict) -> Drawing | None:
        rows = self._emotion_rows(report_data)
        if not any(row["attempts"] > 0 for row in rows):
            return None

        drawing = Drawing(self.page_width, 2.0 * inch)
        chart = VerticalBarChart()
        chart.x = 0.45 * inch
        chart.y = 0.35 * inch
        chart.height = 1.25 * inch
        chart.width = self.page_width - 0.9 * inch
        chart.data = [[row["accuracy"] for row in rows]]
        chart.categoryAxis.categoryNames = self.EMOTION_SHORT_NAMES
        chart.categoryAxis.labels.fontName = self.font_regular
        chart.categoryAxis.labels.fontSize = 8
        chart.categoryAxis.labels.boxAnchor = "n"
        chart.valueAxis.valueMin = 0
        chart.valueAxis.valueMax = 100
        chart.valueAxis.valueStep = 20
        chart.valueAxis.labels.fontName = self.font_regular
        chart.valueAxis.labels.fontSize = 8
        chart.bars[0].fillColor = colors.HexColor("#71B7F8")
        chart.bars.strokeColor = colors.white
        drawing.add(chart)
        return drawing

    def _emotion_highlights(self, styles: dict[str, ParagraphStyle], report: ReportData) -> Table:
        practiced = [emotion for emotion in report.emotion_stats if emotion.attempts > 0]
        if not practiced:
            return self._message_table(
                styles,
                "Bé cần chơi thêm vài màn để EmoGarden đánh giá cảm xúc nổi bật chính xác hơn.",
                "#FFF7ED",
                "#FED7AA",
            )

        best = report.best_emotions[0] if report.best_emotions else None
        weak = report.weak_emotions[:2]
        most_practiced = report.most_practiced_emotion

        data: list[list[object]] = [["Nhóm", "Cảm xúc", "Kết quả", "Ghi chú"]]
        if best:
            data.append(["Làm tốt", best.name, f"{best.accuracy}/100", f"{best.attempts} lượt"])
        else:
            data.append(["Làm tốt", "Chưa đủ dữ liệu", "-", "Cần ít nhất 3 lượt/cảm xúc"])

        if weak:
            for item in weak:
                data.append(["Cần luyện thêm", item.name, f"{item.accuracy}/100", f"{item.attempts} lượt"])
        else:
            data.append(["Cần luyện thêm", "Chưa có cảm xúc nổi bật", "-", "Tiếp tục luyện đều"])

        if most_practiced:
            accuracy = f"{most_practiced.accuracy}/100" if most_practiced.accuracy is not None else "Chưa có"
            data.append(["Luyện nhiều nhất", most_practiced.name, f"{most_practiced.attempts} lượt", accuracy])

        table = Table(data, colWidths=[1.45 * inch, 1.65 * inch, 1.2 * inch, 2.7 * inch])
        table.setStyle(self._default_table_style("#71B7F8"))
        return table

    def _emotion_note(self, attempts: int, accuracy: int | None) -> str:
        if attempts <= 0:
            return "Chưa có dữ liệu"
        if attempts < 3:
            return "Dữ liệu còn ít"
        if accuracy is not None and accuracy < 50:
            return "Cần luyện thêm"
        if accuracy is not None and accuracy >= 80:
            return "Làm tốt"
        return "Đang luyện"

    def _emotion_detail_table(self, report: ReportData) -> Table:
        data: list[list[object]] = [["Cảm xúc", "Đúng", "Chưa đúng", "Tổng lượt", "Độ chính xác", "Ghi chú"]]
        for emotion in report.emotion_stats:
            data.append(
                [
                    emotion.name,
                    str(emotion.correct),
                    str(emotion.incorrect),
                    str(emotion.attempts),
                    "Chưa luyện" if emotion.attempts == 0 else f"{emotion.accuracy}/100",
                    self._emotion_note(emotion.attempts, emotion.accuracy),
                ]
            )

        table = Table(data, colWidths=[1.35 * inch, 0.75 * inch, 0.95 * inch, 0.95 * inch, 1.2 * inch, 1.8 * inch])
        table.setStyle(self._default_table_style("#71B7F8"))
        return table

    def _games_table(self, styles: dict[str, ParagraphStyle], report: ReportData) -> Table:
        games_stats = report.game_stats
        if not games_stats:
            return self._message_table(styles, "Chưa có dữ liệu trò chơi trong kỳ này.", "#F8FAFC", "#D6E3F0")

        data: list[list[object]] = [["Trò chơi", "Lượt chơi", "Điểm TB", "Điểm cao", "Cấp độ"]]
        for game in games_stats[:8]:
            data.append(
                [
                    game.game_name or "Trò chơi",
                    str(game.sessions),
                    self._score_text(game.average_score),
                    self._score_text(game.best_score),
                    str(game.current_level or "-"),
                ]
            )

        table = Table(data, colWidths=[2.7 * inch, 1.0 * inch, 1.1 * inch, 1.1 * inch, 1.1 * inch])
        table.setStyle(self._default_table_style("#5BB3F5"))
        return table

    def _default_table_style(self, header_color: str) -> TableStyle:
        return TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor(header_color)),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("FONTNAME", (0, 0), (-1, 0), self.font_bold),
                ("FONTNAME", (0, 1), (-1, -1), self.font_regular),
                ("FONTSIZE", (0, 0), (-1, 0), 8.5),
                ("FONTSIZE", (0, 1), (-1, -1), 8),
                ("ALIGN", (1, 1), (-1, -1), "CENTER"),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("LEFTPADDING", (0, 0), (-1, -1), 7),
                ("RIGHTPADDING", (0, 0), (-1, -1), 7),
                ("TOPPADDING", (0, 0), (-1, -1), 6),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
                ("BOX", (0, 0), (-1, -1), 0.5, colors.HexColor("#CFE3F7")),
                ("INNERGRID", (0, 0), (-1, -1), 0.25, colors.HexColor("#E2EDF8")),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FBFF")]),
            ]
        )

    def _message_table(self, styles: dict[str, ParagraphStyle], text: str, background: str, border: str) -> Table:
        table = Table([[self._paragraph(text, styles["body"])]], colWidths=[self.page_width])
        table.setStyle(
            TableStyle(
                [
                    ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor(background)),
                    ("BOX", (0, 0), (-1, -1), 0.6, colors.HexColor(border)),
                    ("LEFTPADDING", (0, 0), (-1, -1), 12),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 12),
                    ("TOPPADDING", (0, 0), (-1, -1), 10),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 10),
                ]
            )
        )
        return table

    def _parent_suggestions(self, report_data: dict) -> list[str]:
        total_sessions = self._safe_int(report_data.get("total_sessions"), 0)
        avg_score = report_data.get("avg_score")
        learned_count = self._learned_emotion_count(report_data)
        practiced = [row for row in self._emotion_rows(report_data) if row["attempts"] > 0]
        weak = sorted([row for row in practiced if row["accuracy"] < 60], key=lambda item: item["accuracy"])[:2]
        weak_names = ", ".join(row["name"] for row in weak)

        if total_sessions == 0:
            return [
                "Bé chưa có lượt chơi trong kỳ này. Phụ huynh có thể bắt đầu cùng bé bằng các cảm xúc quen thuộc như Vui vẻ, Buồn bã và Tức giận.",
                "Mỗi ngày chỉ cần 10-15 phút để bé làm quen với tình huống và luyện biểu cảm trước camera.",
            ]

        if avg_score is None:
            suggestions = ["Bé đã bắt đầu luyện tập. Hãy cho bé chơi thêm vài màn để báo cáo có đủ dữ liệu đánh giá."]
        else:
            score = self._safe_float(avg_score)
            if score >= 80:
                suggestions = ["Bé đang làm tốt. Phụ huynh có thể cho bé luyện thêm các tình huống khó hơn để củng cố khả năng nhận biết cảm xúc."]
            elif score >= 60:
                suggestions = ["Bé đang tiến bộ ổn. Phụ huynh nên duy trì lịch luyện ngắn mỗi ngày và khen bé khi bé diễn tả đúng cảm xúc."]
            elif score >= 40:
                focus = weak_names or "các cảm xúc có điểm thấp"
                suggestions = [f"Bé cần luyện thêm {focus}. Hãy cùng bé đọc tình huống, hỏi bé nhân vật đang cảm thấy thế nào, rồi khuyến khích bé thể hiện trước camera."]
            else:
                suggestions = ["Bé nên ôn lại các cảm xúc cơ bản. Phụ huynh có thể bắt đầu với Vui vẻ, Buồn bã và Tức giận trước khi chuyển sang cảm xúc khó hơn."]

        if learned_count < 6:
            suggestions.append(f"Bé đã luyện {learned_count}/6 cảm xúc. Nên cho bé thử thêm các cảm xúc còn lại để báo cáo đầy đủ hơn.")
        if weak_names:
            suggestions.append(f"Nên ưu tiên các tình huống về {weak_names} trong các buổi luyện tiếp theo.")
        suggestions.append("Khi luyện cùng bé, phụ huynh nên dùng câu hỏi mở và tránh chê bé sai; hãy gợi ý nhẹ để bé thử lại biểu cảm.")
        return suggestions

    def _suggestion_box(self, styles: dict[str, ParagraphStyle], report: ReportData) -> Table:
        suggestion_text = "<br/>".join(f"- {self._html(item)}" for item in report.parent_recommendations)
        paragraph = Paragraph(suggestion_text, styles["body"])
        table = Table([[paragraph]], colWidths=[self.page_width])
        table.setStyle(
            TableStyle(
                [
                    ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#FFFBEB")),
                    ("BOX", (0, 0), (-1, -1), 0.6, colors.HexColor("#FDE68A")),
                    ("LEFTPADDING", (0, 0), (-1, -1), 12),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 12),
                    ("TOPPADDING", (0, 0), (-1, -1), 10),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 10),
                ]
            )
        )
        return table

    def _achievements_table(self, styles: dict[str, ParagraphStyle], report: ReportData) -> Table:
        achievements = report.achievements
        if not achievements:
            return self._message_table(styles, "Chưa có thành tựu nổi bật trong kỳ này.", "#F8FAFC", "#D6E3F0")

        rows = [[self._paragraph(f"- {item}", styles["body"])] for item in achievements[:8]]
        table = Table(rows, colWidths=[self.page_width])
        table.setStyle(
            TableStyle(
                [
                    ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#F0FDF4")),
                    ("BOX", (0, 0), (-1, -1), 0.6, colors.HexColor("#BBF7D0")),
                    ("LEFTPADDING", (0, 0), (-1, -1), 12),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 12),
                    ("TOPPADDING", (0, 0), (-1, -1), 6),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
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
        report = build_report_data(
            {
                "child_name": child_name,
                "report_type": report_type,
                "summary": summary,
                "data": json.dumps(report_data, ensure_ascii=False),
                "generated_at": generated_time.isoformat(),
            }
        )

        buffer = BytesIO()
        doc = SimpleDocTemplate(
            buffer,
            pagesize=A4,
            leftMargin=0.5 * inch,
            rightMargin=0.5 * inch,
            topMargin=0.5 * inch,
            bottomMargin=0.5 * inch,
            title=f"BaoCao_EmoGarden_{child_name}_{generated_time.strftime('%Y%m%d')}",
        )

        subtitle = (
            "Dành cho phụ huynh theo dõi quá trình học cảm xúc của bé<br/>"
            f"{report.period_display}"
        )

        elements: list[object] = [
            self._top_line(),
            Spacer(1, 16),
            Paragraph("BÁO CÁO TIẾN BỘ CỦA BÉ", styles["title"]),
            Paragraph(subtitle, styles["subtitle"]),
            self._section_bar("THÔNG TIN BÁO CÁO"),
            Spacer(1, 10),
            self._info_table(styles, report, generated_time),
            Spacer(1, 16),
            self._section_bar("TỔNG QUAN TUẦN NÀY" if report.period_type == "weekly" else "TỔNG QUAN KỲ NÀY"),
            Spacer(1, 10),
            self._summary_box(styles, report.summary_text),
            Spacer(1, 10),
            self._overview_metrics(styles, report),
        ]

        daily_chart = self._daily_sessions_chart(report_data)
        if daily_chart:
            elements.extend(
                [
                    Spacer(1, 14),
                    self._section_bar("LƯỢT CHƠI THEO NGÀY", "#7EADFA"),
                    Spacer(1, 8),
                    daily_chart,
                ]
            )

        emotion_chart = self._emotion_chart(report_data)
        elements.extend(
            [
                Spacer(1, 16),
                self._section_bar("CẢM XÚC NỔI BẬT", "#71B7F8"),
                Spacer(1, 10),
                self._emotion_highlights(styles, report),
            ]
        )
        if emotion_chart:
            elements.extend([Spacer(1, 8), emotion_chart])

        elements.extend(
            [
                Spacer(1, 12),
                self._emotion_detail_table(report),
                Spacer(1, 16),
                self._section_bar("HOẠT ĐỘNG THEO TRÒ CHƠI"),
                Spacer(1, 10),
                self._games_table(styles, report),
                Spacer(1, 16),
                self._section_bar("THÀNH TỰU NỔI BẬT", "#60A5FA"),
                Spacer(1, 10),
                self._achievements_table(styles, report),
                Spacer(1, 16),
                self._section_bar("GỢI Ý CHO PHỤ HUYNH", "#F59E0B"),
                Spacer(1, 10),
                self._suggestion_box(styles, report),
                Spacer(1, 16),
            ]
        )

        footer_line = Drawing(self.page_width, 0.04 * inch)
        line = Line(0, 0, self.page_width, 0)
        line.strokeColor = colors.HexColor("#D6E3F0")
        line.strokeWidth = 1
        footer_line.add(line)
        elements.append(footer_line)
        elements.append(Spacer(1, 6))
        elements.append(
            Paragraph(
                f"Báo cáo được tạo tự động bởi EmoGarden vào {generated_time.strftime('%d/%m/%Y lúc %H:%M')}.",
                styles["footer"],
            )
        )

        doc.build(elements)
        buffer.seek(0)
        return buffer.read()
