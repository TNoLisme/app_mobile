import json
import smtplib
import uuid
from datetime import datetime
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.mime.base import MIMEBase
from email import encoders
from io import BytesIO
from urllib.parse import quote
import unicodedata
import re

from app.services.report_generator_service import ReportGeneratorService
from app.core.config import settings

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Query
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, EmailStr
from sqlalchemy import func, text
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.models.analytics import ChildProgress, Report
from app.models.game import PlaySession
from app.models.user import User

router = APIRouter(prefix="/reports", tags=["Reports"])


class GenerateReportRequest(BaseModel):
    child_user_id: str
    report_type: str = "weekly"


class BatchReportRequest(BaseModel):
    report_type: str = "weekly"
    child_user_ids: list[str] | None = None


class TestEmailRequest(BaseModel):
    email: EmailStr


def _build_report_summary(db: Session, child_user_id: str) -> tuple[str, dict]:
    try:
        # Total sessions and average score
        total_sessions = int(db.query(func.count(PlaySession.session_id)).filter(PlaySession.user_id == child_user_id).scalar() or 0)
        avg_score = float(db.query(func.avg(PlaySession.score)).filter(PlaySession.user_id == child_user_id).scalar() or 0.0)

        # Total playtime in seconds (end_time - start_time)
        total_playtime_seconds = 0
        rows = db.query(PlaySession.start_time, PlaySession.end_time).filter(PlaySession.user_id == child_user_id).all()
        for s, e in rows:
            if s and e:
                delta = (e - s).total_seconds()
                total_playtime_seconds += max(0, int(delta))

        # Games stats: sessions per game and avg score per game
        games_q = db.query(PlaySession.game_id, func.count(PlaySession.session_id).label('sessions'), func.avg(PlaySession.score).label('avg_score'))
        games_q = games_q.filter(PlaySession.user_id == child_user_id).group_by(PlaySession.game_id).all()
        games = []
        for g in games_q:
            game_name = None
            try:
                game_row = db.execute(text("SELECT name FROM games WHERE game_id = :gid"), {"gid": g.game_id}).first()
                if game_row:
                    game_name = game_row[0]
            except Exception:
                game_name = None
            games.append({"game_id": g.game_id, "game_name": game_name or g.game_id, "sessions": int(g.sessions or 0), "avg_score": round(float(g.avg_score or 0.0), 1)})

        # Daily sessions for the last 7 days
        daily = {}
        try:
            recent = db.execute(text(
                "SELECT CAST(start_time AS DATE) as d, count(*) as cnt FROM sessions WHERE user_id = :uid AND start_time >= date('now','-7 day') GROUP BY d ORDER BY d"
            ), {"uid": child_user_id}).fetchall()
            for r in recent:
                daily[str(r[0])] = int(r[1])
        except Exception:
            daily = {}

        # Emotion stats: average cv_confidence per emotion (join session_questions -> game_content)
        emotion_stats = {}
        try:
            emo_q = db.execute(text(
                "SELECT gc.emotion, AVG(sq.cv_confidence) as avg_conf FROM session_questions sq JOIN game_content gc ON sq.question_id = gc.content_id JOIN sessions s ON sq.session_id = s.session_id WHERE s.user_id = :uid GROUP BY gc.emotion"
            ), {"uid": child_user_id}).fetchall()
            for r in emo_q:
                if r[0]:
                    emotion_stats[r[0]] = round(float(r[1] or 0.0), 2)
        except Exception:
            emotion_stats = {}

        # Achievements / progress items from ChildProgress
        achievements = []
        try:
            prog_rows = db.query(ChildProgress).filter(ChildProgress.child_id == child_user_id).order_by(ChildProgress.last_played.desc()).limit(10).all()
            for p in prog_rows:
                achievements.append({"game_id": p.game_id, "level": p.level, "accuracy": round(float(p.accuracy or 0.0), 1)})
        except Exception:
            achievements = []

        data = {
            "total_sessions": total_sessions,
            "avg_score": round(avg_score, 1),
            "total_playtime_seconds": int(total_playtime_seconds),
            "games": games,
            "daily_sessions": daily,
            "emotion_stats": emotion_stats,
            "achievements": achievements,
        }

        # Build human-friendly summary
        if total_sessions == 0:
            summary = "Bé chưa có hoạt động nào trong kỳ này."
        else:
            perf = "ổn" if avg_score >= 7 else "cần cố gắng" if avg_score < 6 else "khá"
            summary = f"Bé đã chơi {total_sessions} phiên, điểm trung bình {round(avg_score,1)} — {perf}."

        return summary, data
    except Exception as e:
        # Fallback minimal summary
        total_sessions = int(db.query(func.count(PlaySession.session_id)).filter(PlaySession.user_id == child_user_id).scalar() or 0)
        data = {"total_sessions": total_sessions}
        summary = f"Bé đã chơi {total_sessions} phiên."
        return summary, data


def _create_report(db: Session, child_user_id: str, report_type: str) -> Report:
    summary, data = _build_report_summary(db, child_user_id)
    report = Report(
        report_id=str(uuid.uuid4()),
        child_id=child_user_id,
        report_type=report_type,
        generated_at=datetime.utcnow(),
        summary=summary,
        data=json.dumps(data, ensure_ascii=False),
    )
    db.add(report)
    db.commit()
    db.refresh(report)
    return report


def _report_payload(report: Report, db: Session) -> dict:
    user = db.get(User, report.child_id)
    data = {}
    try:
        data = json.loads(report.data or "{}")
    except json.JSONDecodeError:
        data = {}
    return {
        "report_id": report.report_id,
        "child_id": report.child_id,
        "child_name": user.name if user else None,
        "child_email": user.email if user else None,
        "report_type": report.report_type,
        "generated_at": report.generated_at.isoformat() if report.generated_at else None,
        "summary": report.summary,
        "stats": data,
        "data": report.data,
    }



@router.get('/download/{report_id}')
def download_report(report_id: str, db: Session = Depends(get_db)):
    report = db.get(Report, report_id)
    if not report:
        raise HTTPException(status_code=404, detail="Report not found")

    user = db.get(User, report.child_id)
    if not user:
        raise HTTPException(status_code=404, detail="Child user not found")

    # Prepare data for PDF generation
    try:
        # Parse stored data
        stats = {}
        try:
            stats = json.loads(report.data or "{}")
        except Exception:
            stats = {}

        child_data = {"name": user.name or user.username, "email": user.email}
        progress_payload = {"period": report.report_type, "summary": report.summary or "", "stats": stats}

        pdf_buffer = ReportGeneratorService().generate_progress_report(child_data, progress_payload)
        pdf_buffer.seek(0)
        return StreamingResponse(pdf_buffer, media_type='application/pdf', headers={"Content-Disposition": f"attachment; filename=Report_{report.report_id}.pdf"})
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


def _build_report_email_html(child_name: str, report_type: str, summary: str, stats: dict, generated_at: datetime) -> str:
    stats_rows = "".join(
        f"<tr><td style='padding:8px 12px;border-bottom:1px solid #e5e7eb;'>{key.replace('_', ' ').title()}</td>"
        f"<td style='padding:8px 12px;border-bottom:1px solid #e5e7eb;font-weight:600;'>{value}</td></tr>"
        for key, value in stats.items()
    ) or "<tr><td colspan='2' style='padding:12px;color:#6b7280;'>Không có số liệu chi tiết.</td></tr>"

    period_label = "tuần" if report_type == "weekly" else "tháng" if report_type == "monthly" else report_type
    generated_text = generated_at.strftime("%d/%m/%Y %H:%M") if generated_at else ""

    return f"""
    <!DOCTYPE html>
    <html lang="vi">
    <head>
        <meta charset="UTF-8" />
        <style>
            body {{ font-family: Arial, sans-serif; color: #1f2937; margin: 0; padding: 0; background: #f3f4f6; }}
            .wrap {{ max-width: 640px; margin: 0 auto; padding: 24px; }}
            .card {{ background: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08); }}
            .header {{ background: linear-gradient(135deg, #2563eb, #10b981); color: white; padding: 24px; text-align: center; }}
            .body {{ padding: 24px; }}
            .badge {{ display: inline-block; padding: 6px 12px; border-radius: 999px; background: rgba(255,255,255,0.16); margin-bottom: 12px; font-size: 12px; }}
            .summary {{ background: #eff6ff; border-left: 4px solid #2563eb; padding: 14px 16px; border-radius: 12px; margin: 16px 0; }}
            table {{ width: 100%; border-collapse: collapse; margin-top: 12px; }}
            .footer {{ color: #6b7280; font-size: 13px; margin-top: 18px; }}
        </style>
    </head>
    <body>
        <div class="wrap">
            <div class="card">
                <div class="header">
                    <div class="badge">Báo cáo tiến độ {period_label}</div>
                    <h1 style="margin:0;font-size:28px;">EmoGarden</h1>
                    <p style="margin:8px 0 0;">Báo cáo học tập của bé {child_name}</p>
                </div>
                <div class="body">
                    <p>Kính gửi Quý phụ huynh,</p>
                    <p>Hệ thống vừa tạo báo cáo {period_label} cho bé <strong>{child_name}</strong> vào lúc {generated_text}.</p>
                    <div class="summary">
                        <strong>Tóm tắt:</strong>
                        <div style="margin-top:8px;">{summary}</div>
                    </div>
                    <h3 style="margin:20px 0 10px;">Số liệu chính</h3>
                    <table>
                        <tbody>
                            {stats_rows}
                        </tbody>
                    </table>
                    <p class="footer">Báo cáo này được gửi tự động từ hệ thống EmoGarden.</p>
                </div>
            </div>
        </div>
    </body>
    </html>
    """


def _send_report_email(
    to_email: str,
    child_name: str,
    report_type: str,
    summary: str,
    stats: dict,
    generated_at: datetime,
    report_id: str,
) -> None:
    email_user = settings.EMAIL_USER
    email_pass = settings.EMAIL_PASS

    if not email_user or not email_pass:
        print("[REPORT EMAIL] Skipped because EMAIL_USER/EMAIL_PASS are not configured.")
        return

    period_label = "tuần" if report_type == "weekly" else "tháng" if report_type == "monthly" else report_type
    html_body = _build_report_email_html(child_name, report_type, summary, stats, generated_at)
    text_body = (
        f"Kính gửi Quý phụ huynh,\n\n"
        f"Báo cáo {period_label} của bé {child_name} đã được tạo.\n"
        f"Tóm tắt: {summary}\n\n"
        f"Số liệu: {json.dumps(stats, ensure_ascii=False)}\n\n"
        f"Mã báo cáo: {report_id}\n"
    )

    # Build message (alternative for text+html) and attach PDF
    message = MIMEMultipart('alternative')
    message['From'] = email_user
    message['To'] = to_email
    message['Subject'] = f"Báo cáo tiến bộ {period_label} - {child_name}"

    # Attach text and html
    message.attach(MIMEText(text_body, 'plain', 'utf-8'))
    message.attach(MIMEText(html_body, 'html', 'utf-8'))

    try:
        # Generate PDF using ReportGeneratorService
        try:
            child_data = {"name": child_name, "email": to_email}
            progress_payload = {"period": report_type, "summary": summary, "stats": stats}
            pdf_buffer = ReportGeneratorService().generate_progress_report(child_data, progress_payload)

            # Attach PDF
            pdf_buffer.seek(0)
            attachment = MIMEBase('application', 'pdf')
            attachment.set_payload(pdf_buffer.read())
            encoders.encode_base64(attachment)

            filename_utf8 = f"BaoCao_{child_name}_{period_label}.pdf"
            # sanitize ASCII filename fallback
            def _sanitize_filename(name: str) -> str:
                name = (name or '').strip()
                name = unicodedata.normalize('NFKD', name)
                name = ''.join(ch for ch in name if not unicodedata.combining(ch))
                name = re.sub(r'[^A-Za-z0-9._-]+', '_', name)
                name = re.sub(r'_+', '_', name).strip('_')
                return name or 'BaoCao'

            filename_ascii = _sanitize_filename(filename_utf8)
            attachment.add_header(
                'Content-Disposition',
                f'attachment; filename="{filename_ascii}"; filename*=UTF-8\'\'{quote(filename_utf8)}'
            )
            message.attach(attachment)
        except Exception as gen_err:
            print(f"[REPORT EMAIL] PDF generation/attach failed: {gen_err}")

        with smtplib.SMTP("smtp.gmail.com", 587) as smtp:
            smtp.starttls()
            smtp.login(email_user, email_pass)
            smtp.send_message(message)
        print(f"[REPORT EMAIL] Sent successfully to {to_email}")
    except Exception as error:
        print(f"[REPORT EMAIL] Failed to send to {to_email}: {error}")


@router.get("/statistics")
def get_report_statistics(db: Session = Depends(get_db)):
    total = db.query(func.count(Report.report_id)).scalar() or 0
    weekly = db.query(func.count(Report.report_id)).filter(Report.report_type == "weekly").scalar() or 0
    monthly = db.query(func.count(Report.report_id)).filter(Report.report_type == "monthly").scalar() or 0
    latest = db.query(Report).order_by(Report.generated_at.desc()).limit(10).all()
    return {
        "status": "success",
        "data": {
            "total_reports": int(total),
            "weekly_reports": int(weekly),
            "monthly_reports": int(monthly),
            "recent_reports": [_report_payload(report, db) for report in latest],
        },
    }


@router.post("/generate-and-send")
def generate_and_send_report(request: GenerateReportRequest, background_tasks: BackgroundTasks, db: Session = Depends(get_db)):
    report = _create_report(db, request.child_user_id, request.report_type)
    return {"status": "success", "message": "Đã tạo báo cáo", "data": _report_payload(report, db)}


@router.post("/send-batch")
def send_batch_reports(request: BatchReportRequest, db: Session = Depends(get_db)):
    child_ids = request.child_user_ids
    if not child_ids:
        child_ids = [row.user_id for row in db.query(User).filter(User.role == "child").all()]
    reports = [_report_payload(_create_report(db, child_id, request.report_type), db) for child_id in child_ids]
    return {"status": "success", "message": f"Đã tạo {len(reports)} báo cáo", "data": reports}


@router.post("/request-report")
def request_report(request: GenerateReportRequest, background_tasks: BackgroundTasks, db: Session = Depends(get_db)):
    user = db.get(User, request.child_user_id)
    if not user:
        raise HTTPException(status_code=404, detail="Không tìm thấy người dùng")
    if not user.email:
        raise HTTPException(status_code=400, detail="Tài khoản chưa có email để nhận báo cáo")

    report = _create_report(db, request.child_user_id, request.report_type)
    payload = _report_payload(report, db)
    background_tasks.add_task(
        _send_report_email,
        user.email,
        user.name or user.username or "Bé",
        report.report_type,
        report.summary or "",
        payload.get("stats") or {},
        report.generated_at,
        report.report_id,
    )
    return {
        "status": "success",
        "message": f"Đang tạo báo cáo {report.report_type}. Email sẽ được gửi đến {user.email} trong giây lát.",
        "email": user.email,
        "data": payload,
    }


@router.get("/history")
def report_history(child_user_id: str | None = None, skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    query = db.query(Report)
    if child_user_id:
        query = query.filter(Report.child_id == child_user_id)
    rows = query.order_by(Report.generated_at.desc()).offset(skip).limit(limit).all()
    return {"status": "success", "data": [_report_payload(report, db) for report in rows]}


@router.get("/preview/{child_user_id}")
def preview_report(child_user_id: str, report_type: str = Query("weekly"), db: Session = Depends(get_db)):
    summary, data = _build_report_summary(db, child_user_id)
    return {
        "status": "success",
        "data": {
            "child_user_id": child_user_id,
            "report_type": report_type,
            "summary": summary,
            "stats": data,
        },
    }


@router.get("/all")
def list_all_reports(skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    rows = db.query(Report).order_by(Report.generated_at.desc()).offset(skip).limit(limit).all()
    return {"status": "success", "data": [_report_payload(report, db) for report in rows]}


@router.post("/test-email")
def test_email(request: TestEmailRequest):
    return {"status": "success", "message": f"Email test đã được giả lập gửi tới {request.email}"}

