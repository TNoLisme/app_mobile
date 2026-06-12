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
    from reportlab.pdfgen import canvas
    from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle
except Exception:  # pragma: no cover - depends on runtime package
    REPORTLAB_AVAILABLE = False

from app.services.report_data import SCORE_MAX, ReportData, build_report_data


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
        score = max(0, min(SCORE_MAX, round(self._safe_float(value))))
        return f"{score}/{SCORE_MAX}"

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

    def _canvas_style(self, size: float, leading: float, color: str = "#102A43", bold: bool = False, align: int = 0) -> ParagraphStyle:
        return ParagraphStyle(
            f"CanvasStyle{size}{color}{bold}{align}",
            fontName=self.font_bold if bold else self.font_regular,
            fontSize=size,
            leading=leading,
            textColor=colors.HexColor(color),
            alignment=align,
        )

    def _draw_page_base(self, c: "canvas.Canvas", page_number: int) -> None:
        width, height = A4
        c.setFillColor(colors.HexColor("#F3F8FC"))
        c.rect(0, 0, width, height, stroke=0, fill=1)
        c.setFont(self.font_regular, 8)
        c.setFillColor(colors.HexColor("#62748A"))
        c.drawString(30, 24, f"Báo cáo được tạo tự động bởi EmoGarden - Trang {page_number}")
        c.drawRightString(width - 30, 24, "support@emogarden.com")

    def _draw_round_rect(self, c: "canvas.Canvas", x: float, y: float, w: float, h: float, fill: str = "#FFFFFF", stroke: str = "#D6E8F8", radius: float = 14, line_width: float = 0.8) -> None:
        c.setStrokeColor(colors.HexColor(stroke))
        c.setLineWidth(line_width)
        c.setFillColor(colors.HexColor(fill))
        c.roundRect(x, y, w, h, radius, stroke=1, fill=1)

    def _draw_text(self, c: "canvas.Canvas", text: str, x: float, y: float, size: float = 10, color: str = "#102A43", bold: bool = False) -> None:
        c.setFont(self.font_bold if bold else self.font_regular, size)
        c.setFillColor(colors.HexColor(color))
        c.drawString(x, y, text)

    def _draw_right_text(self, c: "canvas.Canvas", text: str, x: float, y: float, size: float = 10, color: str = "#102A43", bold: bool = False) -> None:
        c.setFont(self.font_bold if bold else self.font_regular, size)
        c.setFillColor(colors.HexColor(color))
        c.drawRightString(x, y, text)

    def _draw_paragraph(self, c: "canvas.Canvas", text: str, x: float, top: float, w: float, size: float = 9.5, leading: float = 13, color: str = "#102A43", bold: bool = False, max_h: float = 120) -> float:
        paragraph = Paragraph(self._html(text), self._canvas_style(size, leading, color, bold))
        _, used_h = paragraph.wrap(w, max_h)
        paragraph.drawOn(c, x, top - used_h)
        return used_h

    def _mask_email(self, email: str | None) -> str:
        if not email or "@" not in email:
            return "Chưa có"
        local, domain = email.split("@", 1)
        if len(local) <= 4:
            masked = local[:1] + "***"
        else:
            masked = local[:4] + "***"
        return f"{masked}@{domain}"

    def _child_code(self, child_name: str, generated_time: datetime) -> str:
        slug = self._strip_accents(child_name or "local").upper()
        slug = "".join(ch for ch in slug if ch.isalnum())[:8] or "LOCAL"
        return f"EG-{slug}-{generated_time.year}"

    def _active_days(self, report: ReportData) -> int:
        return sum(1 for item in report.daily_sessions if item.sessions > 0)

    def _daily_label(self, label: str) -> str:
        try:
            parsed = datetime.strptime(label.split()[0], "%d/%m/%Y")
            weekday = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"][parsed.weekday()]
            return f"{weekday}\n{parsed.strftime('%d/%m')}"
        except Exception:
            return label.replace(" ", "\n")

    def _emotion_note_text(self, attempts: int, accuracy: int | None) -> str:
        if attempts <= 0:
            return "Chưa luyện"
        if attempts < 3:
            return "Dữ liệu còn ít"
        if accuracy is not None and accuracy >= 80:
            return "Làm tốt"
        if accuracy is not None and accuracy < 60:
            return "Cần luyện thêm"
        return "Đang tiến bộ"

    def _emotion_bar_color(self, accuracy: int | None, attempts: int) -> str:
        if attempts <= 0:
            return "#D8E8F2"
        if accuracy is not None and accuracy >= 80:
            return "#1F8E4D"
        if accuracy is not None and accuracy >= 50:
            return "#F39B3D"
        return "#E04444"

    def _focus_emotions(self, report: ReportData, limit: int = 3) -> list:
        weak = list(report.weak_emotions)
        if len(weak) > 1:
            weakest = weak[0]
            order = {name: idx for idx, name in enumerate(self.EMOTION_NAMES)}
            weak = [weakest] + sorted(weak[1:], key=lambda item: order.get(item.name, 99))
        unpracticed = [item for item in report.emotion_stats if item.attempts == 0]
        selected = weak + [item for item in unpracticed if item.name not in {w.name for w in weak}]
        return selected[:limit]

    def _summary_for_dashboard(self, report: ReportData) -> str:
        focus = self._focus_emotions(report, 2)
        focus_text = ""
        if focus:
            focus_text = ", đặc biệt là " + " và ".join(item.name for item in focus)
        return (
            f"Bé đã luyện {report.sessions_count} lượt trong tuần này. "
            f"Điểm trung bình là {report.average_score_text}. "
            f"{report.progress_comment.split('.')[0]}{focus_text}."
        )

    def _draw_header_page1(self, c: "canvas.Canvas") -> None:
        x, y, w, h = 30, 735, 535, 76
        self._draw_round_rect(c, x, y, w, h, fill="#349BD4", stroke="#349BD4", radius=18)
        c.setFillColor(colors.HexColor("#0B3A6E"))
        c.circle(x + 36, y + 38, 18, stroke=0, fill=1)
        c.setFillColor(colors.white)
        c.setFont(self.font_bold, 12)
        c.drawCentredString(x + 36, y + 33, "EG")
        self._draw_text(c, "BÁO CÁO TIẾN BỘ CẢM XÚC CỦA BÉ", x + 70, y + 40, 20, "#FFFFFF", True)
        self._draw_text(c, "Dành cho phụ huynh theo dõi quá trình học cảm xúc", x + 70, y + 18, 10, "#FFFFFF")

    def _draw_info_card(self, c: "canvas.Canvas", report: ReportData, generated_time: datetime, parent_email: str | None, child_age: int | None, child_code: str | None) -> None:
        x, y, w, h = 30, 655, 535, 66
        self._draw_round_rect(c, x, y, w, h)
        self._draw_text(c, "Thông tin báo cáo", x + 16, y + h - 23, 15, "#0B3A6E", True)
        age_text = f"{child_age} tuổi" if child_age else "Chưa cập nhật"
        code = child_code or self._child_code(report.child_name, generated_time)
        self._draw_text(c, f"Tên bé: {report.child_name}    Tuổi: {age_text}    Mã: {code}", x + 16, y + 35, 8.5, "#62748A")
        period_text = report.period_display.replace("Tuần: ", "").replace("Ngày: ", "").replace("Tháng: ", "")
        self._draw_text(c, f"Kỳ báo cáo: {period_text}    Ngày tạo: {generated_time.strftime('%d/%m/%Y lúc %H:%M')}", x + 16, y + 23, 8.5, "#62748A")
        self._draw_text(c, f"Email phụ huynh: {self._mask_email(parent_email or report.parent_email)}", x + 16, y + 11, 8.5, "#62748A")

    def _draw_metric_tile(self, c: "canvas.Canvas", x: float, y: float, w: float, h: float, value: str, label: str, accent: str) -> None:
        self._draw_round_rect(c, x, y, w, h, fill="#F7FBFF", stroke="#D4E7F7", radius=10)
        c.setFillColor(colors.HexColor(accent))
        c.roundRect(x + 8, y + h - 10, 24, 5, 2.5, stroke=0, fill=1)
        value_size = 14 if len(value) > 6 else 17
        c.setFillColor(colors.HexColor("#0B3A6E"))
        c.setFont(self.font_bold, value_size)
        c.drawCentredString(x + w / 2, y + h - 25, value)
        c.setFillColor(colors.HexColor("#62748A"))
        c.setFont(self.font_regular, 8.3)
        c.drawCentredString(x + w / 2, y + 10, label)

    def _draw_page1(self, c: "canvas.Canvas", report: ReportData, generated_time: datetime, parent_email: str | None, child_age: int | None, child_code: str | None) -> None:
        self._draw_page_base(c, 1)
        self._draw_header_page1(c)
        self._draw_info_card(c, report, generated_time, parent_email, child_age, child_code)

        x, y, w, h = 30, 525, 535, 112
        self._draw_round_rect(c, x, y, w, h)
        self._draw_text(c, "Tổng quan tuần này" if report.period_type == "weekly" else "Tổng quan kỳ này", x + 16, y + h - 26, 16, "#0B3A6E", True)
        self._draw_paragraph(c, self._summary_for_dashboard(report), x + 16, y + h - 36, w - 32, 9.2, 12, "#102A43", max_h=34)
        tile_y = y + 10
        tile_w = 126
        gap = 8
        metrics = [
            (str(report.sessions_count), "Lượt chơi", "#349BD4"),
            (report.average_score_text, "Điểm TB", "#F39B3D"),
            (f"{report.learned_emotion_count}/{report.total_emotion_count}", "Cảm xúc", "#1F8E4D"),
            (str(len(report.game_stats)), "Trò chơi", "#7E5BEF"),
        ]
        for idx, (value, label, accent) in enumerate(metrics):
            self._draw_metric_tile(c, x + 16 + idx * (tile_w + gap), tile_y, tile_w, 43, value, label, accent)

        x, y, w, h = 30, 378, 535, 130
        self._draw_round_rect(c, x, y, w, h)
        self._draw_text(c, "Dashboard nhanh", x + 16, y + h - 27, 16, "#0B3A6E", True)
        self._draw_text(c, "Điểm trung bình", x + 16, y + 84, 9.2, "#102A43", True)
        self._draw_right_text(c, report.average_score_text, x + 225, y + 84, 9.2, "#102A43", True)
        track_x, track_y, track_w, track_h = x + 16, y + 62, 210, 16
        c.setFillColor(colors.HexColor("#E7F0F7"))
        c.roundRect(track_x, track_y, track_w, track_h, 8, stroke=0, fill=1)
        score = report.average_score or 0
        c.setFillColor(colors.HexColor("#F39B3D" if score < 80 else "#1F8E4D"))
        c.roundRect(track_x, track_y, max(4, track_w * score / SCORE_MAX), track_h, 8, stroke=0, fill=1)
        focus = self._focus_emotions(report, 2)
        focus_text = ", ".join(item.name for item in focus) if focus else "các cảm xúc cần luyện"
        self._draw_paragraph(c, f"Nhận xét: Bé đang ở mức cần luyện thêm. Nên ưu tiên các trò chơi tình huống và biểu cảm với cảm xúc {focus_text}.", x + 16, y + 52, 230, 8.7, 12, "#62748A", max_h=42)
        minutes_text = f"{report.total_minutes} phút" if report.total_minutes is not None else "Chưa đo"
        self._draw_metric_tile(c, x + 285, y + 58, 100, 48, minutes_text, "Thời gian", "#349BD4")
        self._draw_metric_tile(c, x + 395, y + 58, 100, 48, f"{self._active_days(report)} ngày", "Có hoạt động", "#1F8E4D")
        self._draw_paragraph(c, "Gợi ý ngắn: Phụ huynh có thể luyện cùng bé 10-15 phút/ngày bằng cách đọc tình huống rồi hỏi bé cảm xúc của nhân vật.", x + 285, y + 50, 220, 8.6, 11.5, "#62748A", max_h=44)

        x, y, w, h = 30, 266, 535, 92
        self._draw_round_rect(c, x, y, w, h)
        self._draw_text(c, "Con nên luyện thêm", x + 16, y + h - 27, 16, "#0B3A6E", True)
        focus_items = self._focus_emotions(report, 3)
        if not focus_items:
            self._draw_text(c, "Bé đang làm tốt. Hãy tiếp tục duy trì luyện tập nhé.", x + 16, y + 34, 9.5, "#62748A")
        else:
            card_w = 164
            for idx, emotion in enumerate(focus_items):
                cx = x + 16 + idx * (card_w + 8)
                self._draw_round_rect(c, cx, y + 14, card_w, 42, fill="#F7FBFF", stroke="#D4E7F7", radius=9)
                self._draw_text(c, emotion.name, cx + 10, y + 38, 9.5, "#0B3A6E", True)
                label = "Chưa luyện" if emotion.attempts == 0 else f"{emotion.accuracy}% - {emotion.attempts} lượt"
                self._draw_text(c, label, cx + 10, y + 23, 8, "#62748A")

    def _draw_page_title(self, c: "canvas.Canvas", title: str, subtitle: str) -> None:
        self._draw_text(c, title, 30, 792, 19, "#0B3A6E", True)
        self._draw_text(c, subtitle, 30, 779, 9, "#62748A")

    def _draw_daily_chart_card(self, c: "canvas.Canvas", report: ReportData) -> None:
        x, y, w, h = 30, 515, 535, 240
        self._draw_round_rect(c, x, y, w, h)
        self._draw_text(c, "Lượt chơi theo ngày", x + 16, y + h - 25, 14, "#0B3A6E", True)
        sessions = report.daily_sessions[:7]
        if not sessions:
            self._draw_text(c, "Chưa có dữ liệu hoạt động theo ngày.", x + 16, y + 105, 10, "#62748A")
            return
        values = [max(0, item.sessions) for item in sessions]
        max_value = max(values + [1])
        chart_x, chart_y, chart_w, chart_h = x + 32, y + 45, w - 64, 145
        c.setStrokeColor(colors.HexColor("#E4EEF6"))
        c.setLineWidth(0.5)
        for step in range(4):
            gy = chart_y + chart_h * step / 3
            c.line(chart_x, gy, chart_x + chart_w, gy)
            self._draw_right_text(c, str(round(max_value * step / 3)), chart_x - 5, gy - 3, 7, "#62748A")
        bar_gap = 10
        bar_w = (chart_w - bar_gap * (len(values) + 1)) / max(1, len(values))
        for idx, item in enumerate(sessions):
            bx = chart_x + bar_gap + idx * (bar_w + bar_gap)
            bh = chart_h * (item.sessions / max_value) if max_value else 0
            c.setFillColor(colors.HexColor("#349BD4"))
            c.roundRect(bx, chart_y, bar_w, max(2, bh), 4, stroke=0, fill=1)
            self._draw_text(c, str(item.sessions), bx + bar_w / 2 - 3, chart_y + max(2, bh) + 6, 8, "#102A43", True)
            label_lines = self._daily_label(item.label).split("\n")
            c.setFont(self.font_regular, 7)
            c.setFillColor(colors.HexColor("#62748A"))
            c.drawCentredString(bx + bar_w / 2, chart_y - 14, label_lines[0])
            if len(label_lines) > 1:
                c.drawCentredString(bx + bar_w / 2, chart_y - 28, label_lines[1])

    def _draw_activity_insight(self, c: "canvas.Canvas", report: ReportData) -> None:
        x, y, w, h = 30, 423, 535, 82
        self._draw_round_rect(c, x, y, w, h)
        self._draw_text(c, "Insight hoạt động", x + 16, y + h - 25, 15, "#0B3A6E", True)
        active = self._active_days(report)
        top = max(report.daily_sessions, key=lambda item: item.sessions, default=None)
        if top and top.sessions > 0:
            text = f"Bé luyện nhiều nhất vào {top.label} với {top.sessions} lượt chơi. Trong tuần, bé có hoạt động trong {active}/7 ngày. Nên duy trì thói quen luyện ngắn mỗi ngày để kết quả ổn định hơn."
        else:
            text = "Chưa có đủ dữ liệu theo ngày. Bé luyện thêm vài màn để báo cáo hoạt động rõ hơn."
        self._draw_paragraph(c, text, x + 16, y + h - 35, w - 32, 9.2, 12.5, "#102A43", max_h=45)

    def _draw_games_card(self, c: "canvas.Canvas", report: ReportData) -> None:
        x, y, w, h = 30, 225, 535, 180
        self._draw_round_rect(c, x, y, w, h)
        self._draw_text(c, "Phân tích trò chơi", x + 16, y + h - 25, 15, "#0B3A6E", True)
        table_x, table_y = x + 16, y + 72
        col_ws = [155, 45, 65, 65, 45, 65]
        headers = ["Trò chơi", "Lượt", "Điểm TB", "Điểm cao", "Cấp", "Tiến độ"]
        row_h = 24
        c.setFillColor(colors.HexColor("#0B3A6E"))
        c.rect(table_x, table_y + row_h * 3, sum(col_ws), row_h, stroke=0, fill=1)
        tx = table_x
        for idx, header in enumerate(headers):
            self._draw_text(c, header, tx + 6, table_y + row_h * 3 + 8, 7.8, "#FFFFFF", True)
            tx += col_ws[idx]
        games = report.game_stats[:3]
        if not games:
            self._draw_text(c, "Chưa có dữ liệu trò chơi trong kỳ này.", table_x, table_y + 46, 9, "#62748A")
        for row_idx, game in enumerate(games):
            ry = table_y + row_h * (2 - row_idx)
            c.setFillColor(colors.white)
            c.rect(table_x, ry, sum(col_ws), row_h, stroke=0, fill=1)
            c.setStrokeColor(colors.HexColor("#E4EEF6"))
            c.rect(table_x, ry, sum(col_ws), row_h, stroke=1, fill=0)
            cells = [
                game.game_name,
                str(game.sessions),
                self._score_text(game.average_score),
                self._score_text(game.best_score),
                str(game.current_level or "-"),
                f"{game.progress_percent if game.progress_percent is not None else (game.average_score or 0)}%",
            ]
            tx = table_x
            for idx, cell in enumerate(cells):
                align_center = idx > 0
                if align_center:
                    c.setFont(self.font_regular, 8)
                    c.setFillColor(colors.black)
                    c.drawCentredString(tx + col_ws[idx] / 2, ry + 8, cell)
                else:
                    self._draw_text(c, cell[:32], tx + 6, ry + 8, 8, "#102A43")
                tx += col_ws[idx]
        top_game = games[0].game_name if games else "trò chơi đã luyện"
        self._draw_paragraph(c, f"Nhận xét: Trò chơi được luyện nhiều nhất là {top_game}. Điểm còn thấp, nên cho bé luyện lại các màn có phản hồi theo từng cảm xúc.", x + 16, y + 46, w - 32, 8.5, 11.5, "#62748A", max_h=35)

    def _draw_page2(self, c: "canvas.Canvas", report: ReportData) -> None:
        self._draw_page_base(c, 2)
        self._draw_page_title(c, "Dashboard hoạt động", "Theo dõi tần suất luyện tập và mức độ tham gia của bé trong kỳ báo cáo.")
        self._draw_daily_chart_card(c, report)
        self._draw_activity_insight(c, report)
        self._draw_games_card(c, report)

    def _draw_emotion_bars(self, c: "canvas.Canvas", report: ReportData) -> None:
        x, y, w, h = 30, 535, 535, 215
        self._draw_round_rect(c, x, y, w, h)
        self._draw_text(c, "Độ chính xác theo cảm xúc", x + 16, y + h - 27, 15, "#0B3A6E", True)
        start_y = y + h - 65
        for idx, emotion in enumerate(report.emotion_stats):
            row_y = start_y - idx * 25
            self._draw_right_text(c, emotion.name, x + 102, row_y + 2, 8.5, "#102A43", True)
            track_x, track_w = x + 110, 330
            c.setFillColor(colors.HexColor("#E6F0F7"))
            c.roundRect(track_x, row_y, track_w, 7, 3.5, stroke=0, fill=1)
            if emotion.attempts > 0 and emotion.accuracy is not None:
                c.setFillColor(colors.HexColor(self._emotion_bar_color(emotion.accuracy, emotion.attempts)))
                c.roundRect(track_x, row_y, max(4, track_w * emotion.accuracy / 100), 7, 3.5, stroke=0, fill=1)
                self._draw_text(c, f"{emotion.accuracy}%", track_x + track_w + 8, row_y - 1, 8, "#102A43", True)
                self._draw_right_text(c, f"{emotion.attempts} lượt", x + w - 22, row_y - 1, 8, "#62748A")
            else:
                self._draw_text(c, "Chưa luyện", track_x + track_w + 8, row_y - 1, 8, "#62748A")
                self._draw_right_text(c, "0 lượt", x + w - 22, row_y - 1, 8, "#62748A")

    def _draw_emotion_table(self, c: "canvas.Canvas", report: ReportData) -> None:
        x, y, w, h = 30, 300, 535, 210
        self._draw_round_rect(c, x, y, w, h)
        self._draw_text(c, "Bảng thống kê cảm xúc", x + 16, y + h - 27, 15, "#0B3A6E", True)
        table_x, table_y = x + 16, y + 24
        col_ws = [90, 50, 70, 45, 80, 145]
        headers = ["Cảm xúc", "Đúng", "Chưa đúng", "Tổng", "Độ chính xác", "Ghi chú"]
        row_h = 22
        c.setFillColor(colors.HexColor("#0B3A6E"))
        c.rect(table_x, table_y + row_h * 6, sum(col_ws), row_h, stroke=0, fill=1)
        tx = table_x
        for idx, header in enumerate(headers):
            self._draw_text(c, header, tx + 6, table_y + row_h * 6 + 7, 7.6, "#FFFFFF", True)
            tx += col_ws[idx]
        for row_idx, emotion in enumerate(report.emotion_stats):
            ry = table_y + row_h * (5 - row_idx)
            c.setFillColor(colors.white)
            c.rect(table_x, ry, sum(col_ws), row_h, stroke=0, fill=1)
            c.setStrokeColor(colors.HexColor("#E4EEF6"))
            c.rect(table_x, ry, sum(col_ws), row_h, stroke=1, fill=0)
            accuracy = "Chưa luyện" if emotion.attempts == 0 else f"{emotion.accuracy}%"
            cells = [emotion.name, str(emotion.correct), str(emotion.incorrect), str(emotion.attempts), accuracy, self._emotion_note_text(emotion.attempts, emotion.accuracy)]
            tx = table_x
            for idx, cell in enumerate(cells):
                if idx == 0 or idx == 5:
                    self._draw_text(c, cell, tx + 6, ry + 7, 7.5, "#102A43")
                else:
                    c.setFont(self.font_regular, 7.5)
                    c.setFillColor(colors.black)
                    c.drawCentredString(tx + col_ws[idx] / 2, ry + 7, cell)
                tx += col_ws[idx]
        focus = self._focus_emotions(report, 3)
        focus_names = ", ".join(item.name for item in focus)
        unpracticed = [item.name for item in report.emotion_stats if item.attempts == 0]
        note = f"Cần ưu tiên: {focus_names}." if focus_names else "Bé đang có kết quả ổn định ở các cảm xúc đã luyện."
        if unpracticed:
            note += f" Với {', '.join(unpracticed[:2])}, bé chưa có lượt luyện trong kỳ này nên chưa thể đánh giá."
        self._draw_paragraph(c, note, x + 16, y + 30, w - 32, 8.4, 11, "#62748A", max_h=28)

    def _draw_page3(self, c: "canvas.Canvas", report: ReportData) -> None:
        self._draw_page_base(c, 3)
        self._draw_page_title(c, "Dashboard cảm xúc", "Phân tích độ chính xác theo từng cảm xúc. Các cảm xúc có ít hơn 3 lượt sẽ được đánh dấu là dữ liệu còn ít.")
        self._draw_emotion_bars(c, report)
        self._draw_emotion_table(c, report)

    def _draw_achievements_card(self, c: "canvas.Canvas", report: ReportData) -> None:
        x, y, w, h = 30, 560, 535, 185
        self._draw_round_rect(c, x, y, w, h)
        self._draw_text(c, "Thành tựu nổi bật", x + 16, y + h - 27, 15, "#0B3A6E", True)
        achievements = report.achievements[:4] or ["Chưa có thành tựu nổi bật trong kỳ này."]
        bullet_y = y + h - 62
        for idx, item in enumerate(achievements):
            cy = bullet_y - idx * 31
            c.setFillColor(colors.HexColor("#1F8E4D"))
            c.circle(x + 24, cy + 3, 4, stroke=0, fill=1)
            self._draw_paragraph(c, item, x + 36, cy + 10, w - 58, 9.6, 12, "#102A43", max_h=24)
        if report.average_score is not None and report.average_score < 80:
            note = f"Lưu ý: Điểm trung bình {report.average_score_text} chưa đủ để kết luận bé đạt mức rất tốt. Báo cáo ưu tiên khuyến nghị luyện thêm thay vì khen quá mức."
            self._draw_paragraph(c, note, x + 16, y + 36, w - 32, 8.4, 11, "#62748A", max_h=28)

    def _recommendations_for_pdf(self, report: ReportData) -> list[str]:
        recommendations = [
            "Dành 10-15 phút mỗi ngày để cùng bé đọc tình huống ngắn rồi hỏi: \"Con nghĩ bạn nhỏ đang cảm thấy thế nào?\""
        ]
        weak_names = {item.name for item in self._focus_emotions(report, 3)}
        if "Sợ hãi" in weak_names:
            recommendations.append("Với cảm xúc Sợ hãi, hãy luyện các tình huống như nghe tiếng sấm, ở nơi tối hoặc bị lạc.")
        if "Tức giận" in weak_names:
            recommendations.append("Với cảm xúc Tức giận, khuyến khích bé nói: \"Con không thích điều đó\" thay vì la hét hoặc đánh bạn.")
        recommendations.append("Khi bé trả lời chưa đúng, tránh chê bé sai. Hãy gợi ý nhẹ và cho bé thử lại biểu cảm trước camera.")
        unpracticed = [item.name for item in report.emotion_stats if item.attempts == 0]
        if unpracticed:
            recommendations.append(f"Nên cho bé luyện thêm cảm xúc {unpracticed[0]} vì kỳ này chưa có dữ liệu đánh giá.")
        for item in report.parent_recommendations:
            if len(recommendations) >= 5:
                break
            if item not in recommendations:
                recommendations.append(item)
        return recommendations[:5]

    def _draw_recommendations_card(self, c: "canvas.Canvas", report: ReportData) -> None:
        x, y, w, h = 30, 240, 535, 290
        self._draw_round_rect(c, x, y, w, h)
        self._draw_text(c, "Gợi ý cho phụ huynh", x + 16, y + h - 27, 15, "#0B3A6E", True)
        top = y + h - 48
        for idx, item in enumerate(self._recommendations_for_pdf(report), start=1):
            item_y = top - (idx - 1) * 45
            c.setFillColor(colors.HexColor("#349BD4"))
            c.circle(x + 28, item_y - 8, 9, stroke=0, fill=1)
            c.setFillColor(colors.white)
            c.setFont(self.font_bold, 8)
            c.drawCentredString(x + 28, item_y - 11, str(idx))
            self._draw_paragraph(c, item, x + 45, item_y, w - 70, 9.1, 12, "#102A43", max_h=40)

    def _draw_page4(self, c: "canvas.Canvas", report: ReportData) -> None:
        self._draw_page_base(c, 4)
        self._draw_page_title(c, "Thành tựu và khuyến nghị", "Các nhận xét dưới đây được sinh theo số liệu thật của kỳ báo cáo. Không tạo thành tựu nếu điều kiện chưa đạt.")
        self._draw_achievements_card(c, report)
        self._draw_recommendations_card(c, report)

    def generate_pdf(
        self,
        child_name: str,
        report_type: str,
        summary: str,
        report_data_json: str | None,
        generated_at: datetime | None = None,
        parent_email: str | None = None,
        child_age: int | None = None,
        child_code: str | None = None,
    ) -> bytes | None:
        if not REPORTLAB_AVAILABLE:
            return None

        report_data = self._to_dict(report_data_json)
        generated_time = generated_at or datetime.utcnow()
        report = build_report_data(
            {
                "child_name": child_name,
                "report_type": report_type,
                "summary": summary,
                "data": json.dumps(report_data, ensure_ascii=False),
                "generated_at": generated_time.isoformat(),
            },
            parent_email=parent_email,
        )

        buffer = BytesIO()
        pdf = canvas.Canvas(buffer, pagesize=A4)
        pdf.setTitle(f"BaoCao_EmoGarden_{child_name}_{generated_time.strftime('%Y%m%d')}")
        self._draw_page1(pdf, report, generated_time, parent_email, child_age, child_code)
        pdf.showPage()
        self._draw_page2(pdf, report)
        pdf.showPage()
        self._draw_page3(pdf, report)
        pdf.showPage()
        self._draw_page4(pdf, report)
        pdf.save()
        buffer.seek(0)
        return buffer.read()
