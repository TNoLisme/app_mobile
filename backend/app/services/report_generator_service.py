from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from datetime import datetime
from io import BytesIO
from typing import Dict
import os


class ReportGeneratorService:
    def __init__(self):
        self.main_font = 'Times-Roman'
        self.bold_font = 'Times-Bold'
        try:
            current_dir = os.path.dirname(os.path.abspath(__file__))
            # optional font path relative to project; ignore if missing
            fonts_dir = os.path.abspath(os.path.join(current_dir, '..', '..', 'fe', 'assets', 'fonts'))
            regular_path = os.path.join(fonts_dir, 'DejaVuSans.ttf')
            bold_path = os.path.join(fonts_dir, 'DejaVuSans-Bold.ttf')
            if os.path.exists(regular_path) and os.path.exists(bold_path):
                pdfmetrics.registerFont(TTFont('DejaVu', regular_path))
                pdfmetrics.registerFont(TTFont('DejaVu-Bold', bold_path))
                self.main_font = 'DejaVu'
                self.bold_font = 'DejaVu-Bold'
        except Exception:
            pass

    def generate_progress_report(self, child_data: Dict, progress_data: Dict) -> BytesIO:
        buffer = BytesIO()
        period = progress_data.get('period', 'weekly') if isinstance(progress_data, dict) else 'weekly'
        child_name = (child_data.get('name') or 'Student').replace(' ', '_')
        date_str = datetime.now().strftime('%Y%m%d')
        filename = f"Report_{period}_{child_name}_{date_str}.pdf"

        doc = SimpleDocTemplate(
            buffer,
            pagesize=A4,
            rightMargin=0.5 * inch,
            leftMargin=0.5 * inch,
            topMargin=0.5 * inch,
            bottomMargin=0.5 * inch,
            title=filename,
        )

        elements = []
        styles = getSampleStyleSheet()

        title_style = ParagraphStyle('CustomTitle', parent=styles['Heading1'], fontSize=18, alignment=1, fontName=self.bold_font)
        normal = styles['Normal']

        elements.append(Paragraph(f"Báo cáo tiến độ - {child_data.get('name', 'Bé')}", title_style))
        elements.append(Spacer(1, 12))

        summary = progress_data.get('summary') if isinstance(progress_data, dict) else ''
        if summary:
            elements.append(Paragraph(f"<b>Tóm tắt:</b> {summary}", normal))
            elements.append(Spacer(1, 8))

        # Stats table
        stats = progress_data.get('stats') if isinstance(progress_data, dict) else {}
        if not stats:
            # try progress_data itself
            stats = progress_data
        table_data = [["Chỉ số", "Giá trị"]]
        for k, v in (stats.items() if isinstance(stats, dict) else []):
            table_data.append([k.replace('_', ' ').title(), str(v)])

        if len(table_data) > 1:
            table = Table(table_data, colWidths=[3 * inch, 3 * inch])
            table.setStyle(TableStyle([
                ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#f3f4f6')),
                ('TEXTCOLOR', (0, 0), (-1, 0), colors.HexColor('#111827')),
                ('GRID', (0, 0), (-1, -1), 0.5, colors.HexColor('#e5e7eb')),
                ('FONTNAME', (0, 0), (-1, -1), self.main_font),
            ]))
            elements.append(table)

        doc.build(elements)
        buffer.seek(0)
        return buffer
