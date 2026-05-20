from __future__ import annotations

import json
import re
import smtplib
import unicodedata
import uuid
from collections import defaultdict
from datetime import datetime, timedelta
from email import encoders
from email.header import Header
from email.mime.base import MIMEBase
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.utils import formataddr
from html import escape
from urllib.parse import quote

from fastapi import APIRouter, Depends, HTTPException, Query, Response
from pydantic import BaseModel, EmailStr
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.core.config import settings
from app.db.session import get_db
from app.models.analytics import ChildProgress, Report
from app.models.game import Game, GameContent, PlaySession, SessionQuestion
from app.models.user import Child, User
from app.services.report_data import build_report_data, build_summary_text
from app.services.report_pdf import REPORTLAB_AVAILABLE, ReportPdfService

router = APIRouter(prefix="/reports", tags=["Reports"])
pdf_service = ReportPdfService()


class GenerateReportRequest(BaseModel):
    child_user_id: str
    report_type: str = "weekly"
    parent_email: EmailStr | None = None
    send_email: bool = True


class BatchReportRequest(BaseModel):
    report_type: str = "weekly"
    child_user_ids: list[str] | None = None


class TestEmailRequest(BaseModel):
    email: EmailStr


class SendExistingReportRequest(BaseModel):
    parent_email: EmailStr | None = None


EMAIL_REGEX = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
SUPPORTED_REPORT_TYPES = {"weekly", "monthly", "daily"}

EMOTION_DISPLAY = {
    "happy": "Vui vẻ",
    "sad": "Buồn bã",
    "angry": "Tức giận",
    "fear": "Sợ hãi",
    "surprised": "Ngạc nhiên",
    "disgusted": "Ghê tởm",
}

EMOTION_ALIASES = {
    "happy": {"happy", "joy", "smile", "vui", "vui ve", "vui vẻ"},
    "sad": {"sad", "sadness", "buon", "buon ba", "buồn", "buồn bã"},
    "angry": {"angry", "anger", "tuc gian", "giận", "tức giận"},
    "fear": {"fear", "fearful", "so", "so hai", "sợ", "sợ hãi"},
    "surprised": {"surprised", "surprise", "ngac nhien", "ngạc nhiên"},
    "disgusted": {"disgusted", "disgust", "ghe tom", "ghê tởm"},
}

DAY_LABELS = {0: "T2", 1: "T3", 2: "T4", 3: "T5", 4: "T6", 5: "T7", 6: "CN"}


def _normalize_report_type(report_type: str | None) -> str:
    normalized = (report_type or "weekly").strip().lower()
    if normalized not in SUPPORTED_REPORT_TYPES:
        return "weekly"
    return normalized


def _period_window(report_type: str) -> tuple[datetime, datetime]:
    end_at = datetime.utcnow()
    if report_type == "monthly":
        start_at = end_at - timedelta(days=30)
    elif report_type == "daily":
        start_at = end_at - timedelta(days=1)
    else:
        start_at = end_at - timedelta(days=7)
    return start_at, end_at


def _strip_accents(text: str) -> str:
    normalized = unicodedata.normalize("NFKD", text)
    return "".join(ch for ch in normalized if not unicodedata.combining(ch))


def _normalize_emotion(value: str | None) -> str | None:
    if not value:
        return None

    cleaned = _strip_accents(value.strip().lower())
    for key, aliases in EMOTION_ALIASES.items():
        for alias in aliases:
            if _strip_accents(alias) in cleaned:
                return key
    return None


def _safe_float(value, default: float = 0.0) -> float:
    try:
        if value is None:
            return default
        return float(value)
    except (TypeError, ValueError):
        return default


def _safe_int(value, default: int = 0) -> int:
    try:
        if value is None:
            return default
        return int(value)
    except (TypeError, ValueError):
        return default


def _build_daily_sessions(sessions: list[PlaySession], start_at: datetime, end_at: datetime) -> dict[str, int]:
    counts_by_date: dict[str, int] = defaultdict(int)
    for session in sessions:
        if not session.start_time:
            continue
        counts_by_date[session.start_time.date().isoformat()] += 1

    result: dict[str, int] = {}
    cursor = start_at.date()
    last_date = end_at.date()
    while cursor <= last_date:
        key = f"{DAY_LABELS[cursor.weekday()]}\n{cursor.strftime('%d/%m')}"
        result[key] = counts_by_date.get(cursor.isoformat(), 0)
        cursor += timedelta(days=1)
    return result


def _build_games_stats(db: Session, sessions: list[PlaySession]) -> list[dict]:
    if not sessions:
        return []

    game_ids = {s.game_id for s in sessions if s.game_id}
    games = db.query(Game.game_id, Game.name).filter(Game.game_id.in_(game_ids)).all() if game_ids else []
    game_name_map = {game_id: name for game_id, name in games}

    grouped: dict[str, dict] = {}
    for session in sessions:
        if not session.game_id:
            continue
        game_key = session.game_id
        if game_key not in grouped:
            grouped[game_key] = {
                "game_id": game_key,
                "game_name": game_name_map.get(game_key, "Trò chơi"),
                "sessions": 0,
                "total_score": 0.0,
                "score_count": 0,
                "best_score": 0,
                "level": 0,
            }

        item = grouped[game_key]
        item["sessions"] += 1
        item["best_score"] = max(item["best_score"], _safe_int(session.score))
        item["level"] = max(item["level"], _safe_int(session.level, 1))
        if session.score is not None:
            item["total_score"] += _safe_float(session.score)
            item["score_count"] += 1

    enriched = []
    for item in grouped.values():
        avg_score = item["total_score"] / item["score_count"] if item["score_count"] else 0.0
        enriched.append(
            {
                "game_id": item["game_id"],
                "game_name": item["game_name"],
                "sessions": item["sessions"],
                "avg_score": round(avg_score, 2),
                "best_score": item["best_score"],
                "level": item["level"],
            }
        )

    enriched.sort(key=lambda row: (row["sessions"], row["avg_score"]), reverse=True)
    return enriched


def _build_emotion_stats(db: Session, child_user_id: str, start_at: datetime, end_at: datetime) -> dict[str, dict]:
    stats = {
        display: {"correct": 0, "incorrect": 0, "attempts": 0, "accuracy": 0.0}
        for display in EMOTION_DISPLAY.values()
    }

    rows = (
        db.query(GameContent.emotion, SessionQuestion.is_correct, func.count(SessionQuestion.id))
        .join(SessionQuestion, SessionQuestion.question_id == GameContent.content_id)
        .join(PlaySession, PlaySession.session_id == SessionQuestion.session_id)
        .filter(PlaySession.user_id == child_user_id)
        .filter(PlaySession.start_time >= start_at)
        .filter(PlaySession.start_time < end_at)
        .group_by(GameContent.emotion, SessionQuestion.is_correct)
        .all()
    )

    for emotion, is_correct, count in rows:
        normalized = _normalize_emotion(emotion)
        if not normalized:
            continue
        label = EMOTION_DISPLAY[normalized]
        if _safe_int(is_correct) == 1:
            stats[label]["correct"] += _safe_int(count)
        else:
            stats[label]["incorrect"] += _safe_int(count)

    for label in stats:
        correct = stats[label]["correct"]
        incorrect = stats[label]["incorrect"]
        total = correct + incorrect
        stats[label]["attempts"] = total
        stats[label]["accuracy"] = round((correct * 100.0 / total), 1) if total else 0.0

    return stats


def _build_achievements(total_sessions: int, avg_score: float | None, games_stats: list[dict], emotion_stats: dict[str, dict]) -> list[str]:
    achievements: list[str] = []

    if total_sessions >= 5:
        achievements.append(f"Duy trì luyện tập đều với {total_sessions} lượt chơi.")

    if avg_score is not None and avg_score >= 80:
        achievements.append("Đạt điểm trung bình cao.")

    for name, values in emotion_stats.items():
        if not isinstance(values, dict):
            continue
        correct = _safe_int(values.get("correct"), 0)
        incorrect = _safe_int(values.get("incorrect"), 0)
        attempts = _safe_int(values.get("attempts"), correct + incorrect)
        accuracy = _safe_float(values.get("accuracy"), 0)
        if attempts >= 3 and accuracy >= 80:
            achievements.append(f"Làm tốt cảm xúc {name}.")

    if not achievements:
        achievements.append("Chưa có thành tựu nổi bật trong kỳ này.")

    return achievements


def _previous_period_average(db: Session, child_user_id: str, start_at: datetime, end_at: datetime) -> float | None:
    window = end_at - start_at
    previous_start = start_at - window
    previous_end = start_at
    rows = (
        db.query(PlaySession.score)
        .filter(PlaySession.user_id == child_user_id)
        .filter(PlaySession.start_time >= previous_start)
        .filter(PlaySession.start_time < previous_end)
        .filter(PlaySession.end_time.isnot(None))
        .filter(PlaySession.score.isnot(None))
        .all()
    )
    values = [_safe_float(row[0]) for row in rows if row[0] is not None]
    return round(sum(values) / len(values), 1) if values else None


def _build_report_summary(db: Session, child_user_id: str, report_type: str) -> tuple[str, dict]:
    report_type = _normalize_report_type(report_type)
    start_at, end_at = _period_window(report_type)

    sessions = (
        db.query(PlaySession)
        .filter(PlaySession.user_id == child_user_id)
        .filter(PlaySession.start_time >= start_at)
        .filter(PlaySession.end_time.isnot(None))
        .all()
    )
    progress_count = (
        db.query(func.count(ChildProgress.progress_id))
        .filter(ChildProgress.child_id == child_user_id)
        .scalar()
        or 0
    )

    total_sessions = len(sessions)
    total_playtime_minutes = 0
    score_values: list[float] = []
    game_ids: set[str] = set()

    for session in sessions:
        if session.end_time and session.start_time:
            delta_seconds = max((session.end_time - session.start_time).total_seconds(), 0.0)
            total_playtime_minutes += int(delta_seconds // 60)
        if session.score is not None:
            score_values.append(_safe_float(session.score))
        if session.game_id:
            game_ids.add(session.game_id)

    avg_score = round((sum(score_values) / len(score_values)), 1) if score_values else None
    previous_avg_score = _previous_period_average(db, child_user_id, start_at, end_at)
    daily_sessions = _build_daily_sessions(sessions, start_at, end_at)
    games_stats = _build_games_stats(db, sessions)
    total_games = len([game for game in games_stats if _safe_int(game.get("sessions"), 0) > 0])
    emotion_stats = _build_emotion_stats(db, child_user_id, start_at, end_at)
    achievements = _build_achievements(total_sessions, avg_score, games_stats, emotion_stats)

    summary = build_summary_text(
        sessions_count=total_sessions,
        average_score=None if avg_score is None else round(avg_score),
        period_type=report_type,
    )

    data = {
        "period": report_type,
        "start_date": start_at.strftime("%d/%m/%Y"),
        "end_date": end_at.strftime("%d/%m/%Y"),
        "total_sessions": total_sessions,
        "total_playtime_minutes": total_playtime_minutes,
        "avg_score": avg_score,
        "previous_avg_score": previous_avg_score,
        "total_games": total_games,
        "progress_count": _safe_int(progress_count),
        "daily_sessions": daily_sessions,
        "games_stats": games_stats,
        "emotion_stats": emotion_stats,
        "achievements": achievements,
    }
    return summary, data


def _create_report(db: Session, child_user_id: str, report_type: str) -> Report:
    normalized_type = _normalize_report_type(report_type)
    summary, data = _build_report_summary(db, child_user_id, normalized_type)
    start_date = data.get("start_date")
    end_date = data.get("end_date")

    existing_reports = (
        db.query(Report)
        .filter(Report.child_id == child_user_id)
        .filter(Report.report_type == normalized_type)
        .order_by(Report.generated_at.desc())
        .all()
    )
    for existing in existing_reports:
        try:
            existing_data = json.loads(existing.data or "{}")
        except json.JSONDecodeError:
            existing_data = {}
        if existing_data.get("start_date") == start_date and existing_data.get("end_date") == end_date:
            existing.summary = summary
            existing.data = json.dumps(data, ensure_ascii=False)
            existing.generated_at = datetime.utcnow()
            db.commit()
            db.refresh(existing)
            return existing

    report = Report(
        report_id=str(uuid.uuid4()),
        child_id=child_user_id,
        report_type=normalized_type,
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
    parsed_data = {}
    try:
        parsed_data = json.loads(report.data or "{}")
    except json.JSONDecodeError:
        parsed_data = {}

    raw_avg_score = parsed_data.get("avg_score")
    raw_previous_avg_score = parsed_data.get("previous_avg_score")
    raw_games_stats = parsed_data.get("games_stats") if isinstance(parsed_data.get("games_stats"), list) else []
    games_played_count = len(
        [game for game in raw_games_stats if isinstance(game, dict) and _safe_int(game.get("sessions"), 0) > 0]
    )
    stats = {
        "total_sessions": _safe_int(parsed_data.get("total_sessions"), 0),
        "avg_score": None if raw_avg_score is None else round(_safe_float(raw_avg_score), 1),
        "progress_count": _safe_int(parsed_data.get("progress_count"), 0),
        "total_games": games_played_count,
        "total_playtime_minutes": _safe_int(parsed_data.get("total_playtime_minutes"), 0),
        "previous_avg_score": None if raw_previous_avg_score is None else round(_safe_float(raw_previous_avg_score), 1),
    }

    return {
        "report_id": report.report_id,
        "child_id": report.child_id,
        "child_name": user.name if user else None,
        "child_email": user.email if user else None,
        "report_type": report.report_type,
        "generated_at": report.generated_at.isoformat() if report.generated_at else None,
        "summary": report.summary,
        "stats": stats,
        "data": report.data,
    }


def _report_period_key(report: Report) -> str:
    try:
        parsed_data = json.loads(report.data or "{}")
    except json.JSONDecodeError:
        parsed_data = {}
    start_date = parsed_data.get("start_date")
    end_date = parsed_data.get("end_date")
    if start_date and end_date:
        return f"{report.child_id}:{report.report_type}:{start_date}:{end_date}"
    return report.report_id


def _dedupe_report_rows(rows: list[Report]) -> list[Report]:
    selected: dict[str, Report] = {}
    for row in rows:
        key = _report_period_key(row)
        current = selected.get(key)
        if current is None or (row.generated_at or datetime.min) > (current.generated_at or datetime.min):
            selected[key] = row
    return sorted(selected.values(), key=lambda item: item.generated_at or datetime.min, reverse=True)


def _extract_email_from_preferences(raw_value: str | None) -> str | None:
    if not raw_value:
        return None

    value = raw_value.strip()
    if EMAIL_REGEX.match(value):
        return value

    try:
        parsed = json.loads(value)
    except json.JSONDecodeError:
        return None

    if isinstance(parsed, dict):
        for key in ("parent_email", "email", "receiver_email"):
            candidate = parsed.get(key)
            if isinstance(candidate, str) and EMAIL_REGEX.match(candidate.strip()):
                return candidate.strip()
    return None


def _resolve_parent_email(db: Session, child_user_id: str, explicit_email: str | None) -> str | None:
    explicit = (explicit_email or "").strip()
    if explicit and EMAIL_REGEX.match(explicit):
        return explicit

    child = db.get(Child, child_user_id)
    from_preferences = _extract_email_from_preferences(child.report_preferences if child else None)
    if from_preferences:
        return from_preferences

    user = db.get(User, child_user_id)
    candidate = (user.email or "").strip() if user else ""
    if candidate and EMAIL_REGEX.match(candidate):
        return candidate
    return None


def _build_report_email_body(payload: dict) -> str:
    report = build_report_data(payload)
    weak_names = ", ".join(item.name for item in report.weak_emotions[:2])
    recommendation = report.progress_comment
    if weak_names:
        recommendation = f"{recommendation} Phụ huynh có thể cùng bé ôn lại các tình huống về {weak_names}."

    return (
        "EmoGarden - Báo cáo tiến độ cảm xúc\n\n"
        "Kính gửi Quý phụ huynh,\n\n"
        f"EmoGarden gửi báo cáo tiến độ học cảm xúc tuần này của bé {report.child_name}.\n\n"
        f"Tuần này, bé đã luyện {report.sessions_count} lượt.\n"
        f"Điểm trung bình: {report.average_score_text}.\n"
        f"Bé đã luyện {report.learned_emotion_count}/{report.total_emotion_count} cảm xúc.\n\n"
        f"{recommendation}\n\n"
        "Báo cáo chi tiết đã được đính kèm trong email này.\n\n"
        "Trân trọng,\n"
        "EmoGarden"
    )


def _build_report_email_html(payload: dict) -> str:
    report = build_report_data(payload)
    weak_names = ", ".join(item.name for item in report.weak_emotions[:2])
    recommendation = report.progress_comment
    if weak_names:
        recommendation = f"{recommendation} Phụ huynh có thể cùng bé ôn lại các tình huống về {weak_names}."

    return f"""
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <style>
            body {{
                font-family: Arial, sans-serif;
                line-height: 1.6;
                color: #1f2937;
                max-width: 640px;
                margin: 0 auto;
                padding: 20px;
                background: #f4f8fb;
            }}
            .header {{
                background: linear-gradient(135deg, #5bb3f5 0%, #2677c9 100%);
                color: white;
                padding: 28px;
                border-radius: 14px 14px 0 0;
                text-align: center;
            }}
            .content {{
                background: #ffffff;
                padding: 28px;
                border-radius: 0 0 14px 14px;
                border: 1px solid #dbeafe;
            }}
            .summary {{
                background: #eff6ff;
                border-left: 4px solid #5bb3f5;
                padding: 14px;
                border-radius: 8px;
                margin: 18px 0;
            }}
            .metric-row {{
                display: table;
                width: 100%;
                border-spacing: 8px;
                margin: 16px 0;
            }}
            .metric {{
                display: table-cell;
                background: #f0f9ff;
                border: 1px solid #dbeafe;
                border-radius: 12px;
                padding: 12px;
                text-align: center;
                width: 33.33%;
            }}
            .metric strong {{
                display: block;
                color: #0f6fbf;
                font-size: 20px;
            }}
            .metric span {{
                color: #64748b;
                font-size: 12px;
            }}
            .footer {{
                color: #64748b;
                font-size: 12px;
                margin-top: 18px;
            }}
        </style>
    </head>
    <body>
        <div class="header">
            <h1>EmoGarden</h1>
            <h2>Báo cáo tiến độ cảm xúc</h2>
        </div>
        <div class="content">
            <p>Kính gửi Quý Phụ huynh,</p>
            <p>EmoGarden gửi báo cáo tiến độ học cảm xúc tuần này của bé <strong>{escape(report.child_name)}</strong>.</p>
            <div class="metric-row">
                <div class="metric"><strong>{report.sessions_count}</strong><span>Lượt luyện</span></div>
                <div class="metric"><strong>{report.average_score_text}</strong><span>Điểm trung bình</span></div>
                <div class="metric"><strong>{report.learned_emotion_count}/{report.total_emotion_count}</strong><span>Cảm xúc đã luyện</span></div>
            </div>
            <div class="summary">{escape(recommendation)}</div>
            <p>Báo cáo chi tiết đã được đính kèm trong email này.</p>
            <p>Trân trọng,<br/>EmoGarden</p>
            <p class="footer">Email này được tạo tự động từ EmoGarden.</p>
        </div>
    </body>
    </html>
    """


def _sanitize_filename(filename: str) -> str:
    normalized = _strip_accents(filename)
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "_", normalized)
    cleaned = re.sub(r"_+", "_", cleaned).strip("_")
    return cleaned or "BaoCao"


def _build_pdf_bytes(payload: dict) -> bytes | None:
    if not REPORTLAB_AVAILABLE:
        return None
    report = build_report_data(payload)
    child_name = report.child_name or "Bé"
    report_type = report.period_type or "weekly"
    summary = report.summary_text
    generated_at = None
    generated_at_raw = payload.get("generated_at")
    if generated_at_raw:
        try:
            generated_at = datetime.fromisoformat(generated_at_raw.replace("Z", "+00:00"))
        except Exception:
            generated_at = None

    return pdf_service.generate_pdf(
        child_name=child_name,
        report_type=report_type,
        summary=summary,
        report_data_json=payload.get("data"),
        generated_at=generated_at,
    )


def _send_report_email(recipient: str, payload: dict) -> tuple[bool, str]:
    report = build_report_data(payload, parent_email=recipient)
    smtp_username = (settings.SMTP_USERNAME or settings.EMAIL_USER or "").strip()
    smtp_password = settings.SMTP_PASSWORD or settings.EMAIL_PASS
    smtp_host = (settings.SMTP_HOST or "smtp.gmail.com").strip()
    smtp_from = (settings.SMTP_FROM_EMAIL or smtp_username).strip()

    if not smtp_username or not smtp_password or not smtp_host or not smtp_from:
        return False, "Chưa thể gửi email lúc này. Vui lòng thử lại sau."

    pdf_bytes = _build_pdf_bytes(payload)
    if not pdf_bytes:
        return False, "Chưa tạo được báo cáo. Vui lòng thử lại."

    child_name = report.child_name or "Bé"
    report_type = report.period_type or "weekly"
    date_part = datetime.utcnow().strftime("%Y%m%d")
    filename_utf8 = f"BaoCao_{child_name}_{report_type}_{date_part}.pdf"
    filename_ascii = _sanitize_filename(filename_utf8)

    message = MIMEMultipart("mixed")
    message["Subject"] = str(Header(f"Báo cáo tiến độ cảm xúc của {child_name} - EmoGarden", "utf-8"))
    message["From"] = formataddr((str(Header(settings.SMTP_FROM_NAME or "EmoGarden", "utf-8")), smtp_from))
    message["To"] = recipient

    alternative = MIMEMultipart("alternative")
    alternative.attach(MIMEText(_build_report_email_body(payload), "plain", "utf-8"))
    alternative.attach(MIMEText(_build_report_email_html(payload), "html", "utf-8"))
    message.attach(alternative)

    attachment = MIMEBase("application", "pdf")
    attachment.set_payload(pdf_bytes)
    encoders.encode_base64(attachment)
    attachment.add_header(
        "Content-Disposition",
        f"attachment; filename=\"{filename_ascii}\"; filename*=UTF-8''{quote(filename_utf8)}",
    )
    message.attach(attachment)

    try:
        with smtplib.SMTP(smtp_host, settings.SMTP_PORT, timeout=20) as server:
            if settings.SMTP_USE_TLS:
                server.starttls()
            if smtp_username and smtp_password:
                server.login(smtp_username, smtp_password)
            server.send_message(message)
        if pdf_bytes:
            return True, f"Đã gửi báo cáo tới {recipient}."
        return True, f"Đã gửi nội dung báo cáo tới {recipient}."
    except Exception:  # pragma: no cover - external service
        return False, "Chưa gửi được email. Vui lòng thử lại."


def _create_and_optionally_send_report(
    db: Session,
    child_user_id: str,
    report_type: str,
    send_email: bool,
    explicit_parent_email: str | None,
) -> tuple[dict, bool, str]:
    report = _create_report(db, child_user_id, report_type)
    payload = _report_payload(report, db)
    sent = False
    message = "Đã tạo báo cáo."

    if send_email:
        recipient = _resolve_parent_email(db, child_user_id, explicit_parent_email)
        if recipient:
            sent, email_message = _send_report_email(recipient, payload)
            message = f"{message} {email_message}"
        else:
            message = f"{message} Chưa có email phụ huynh hợp lệ để gửi."
    return payload, sent, message


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
def generate_and_send_report(request: GenerateReportRequest, db: Session = Depends(get_db)):
    payload, sent, message = _create_and_optionally_send_report(
        db=db,
        child_user_id=request.child_user_id,
        report_type=request.report_type,
        send_email=request.send_email,
        explicit_parent_email=request.parent_email,
    )
    return {
        "status": "success",
        "message": message,
        "data": payload,
        "email_sent": sent,
        "pdf_enabled": REPORTLAB_AVAILABLE,
    }


@router.post("/send-batch")
def send_batch_reports(request: BatchReportRequest, db: Session = Depends(get_db)):
    child_ids = request.child_user_ids
    if not child_ids:
        child_ids = [row.user_id for row in db.query(User).filter(User.role == "child").all()]

    reports = []
    for child_id in child_ids:
        payload, sent, message = _create_and_optionally_send_report(
            db=db,
            child_user_id=child_id,
            report_type=request.report_type,
            send_email=True,
            explicit_parent_email=None,
        )
        reports.append(
            {
                **payload,
                "email_sent": sent,
                "email_message": message,
            }
        )

    return {
        "status": "success",
        "message": f"Đã tạo {len(reports)} báo cáo",
        "data": reports,
        "pdf_enabled": REPORTLAB_AVAILABLE,
    }


@router.post("/request-report")
def request_report(request: GenerateReportRequest, db: Session = Depends(get_db)):
    payload, sent, message = _create_and_optionally_send_report(
        db=db,
        child_user_id=request.child_user_id,
        report_type=request.report_type,
        send_email=request.send_email,
        explicit_parent_email=request.parent_email,
    )
    return {
        "status": "success",
        "message": message,
        "data": payload,
        "email_sent": sent,
        "pdf_enabled": REPORTLAB_AVAILABLE,
    }


@router.post("/{report_id}/send")
def send_existing_report(
    report_id: str,
    request: SendExistingReportRequest | None = None,
    db: Session = Depends(get_db),
):
    report = db.get(Report, report_id)
    if report is None:
        raise HTTPException(status_code=404, detail="Report not found")

    payload = _report_payload(report, db)
    explicit_email = request.parent_email if request else None
    recipient = _resolve_parent_email(db, report.child_id, explicit_email)
    if not recipient:
        return {
            "status": "warning",
            "message": "Chưa có email phụ huynh hợp lệ để gửi.",
            "data": payload,
            "email_sent": False,
            "pdf_enabled": REPORTLAB_AVAILABLE,
        }

    sent, message = _send_report_email(recipient, payload)
    return {
        "status": "success" if sent else "error",
        "message": message,
        "data": payload,
        "email_sent": sent,
        "pdf_enabled": REPORTLAB_AVAILABLE,
    }


@router.get("/history")
def report_history(child_user_id: str | None = None, skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    query = db.query(Report)
    if child_user_id:
        query = query.filter(Report.child_id == child_user_id)
    rows = query.order_by(Report.generated_at.desc()).all()
    deduped = _dedupe_report_rows(rows)[skip : skip + limit]
    return {"status": "success", "data": [_report_payload(report, db) for report in deduped]}


@router.get("/preview/{child_user_id}")
def preview_report(
    child_user_id: str,
    report_type: str = Query("weekly"),
    db: Session = Depends(get_db),
):
    normalized_type = _normalize_report_type(report_type)
    summary, data = _build_report_summary(db, child_user_id, normalized_type)
    raw_avg_score = data.get("avg_score")
    raw_previous_avg_score = data.get("previous_avg_score")
    return {
        "status": "success",
        "data": {
            "child_user_id": child_user_id,
            "report_type": normalized_type,
            "summary": summary,
            "stats": {
                "total_sessions": _safe_int(data.get("total_sessions"), 0),
                "avg_score": None if raw_avg_score is None else round(_safe_float(raw_avg_score), 1),
                "progress_count": _safe_int(data.get("progress_count"), 0),
                "total_games": _safe_int(data.get("total_games"), 0),
                "total_playtime_minutes": _safe_int(data.get("total_playtime_minutes"), 0),
                "previous_avg_score": None if raw_previous_avg_score is None else round(_safe_float(raw_previous_avg_score), 1),
            },
            "insights": {
                "total_playtime_minutes": data.get("total_playtime_minutes", 0),
                "daily_sessions": data.get("daily_sessions", {}),
                "games_stats": data.get("games_stats", []),
                "emotion_stats": data.get("emotion_stats", {}),
                "achievements": data.get("achievements", []),
                "previous_avg_score": None if raw_previous_avg_score is None else round(_safe_float(raw_previous_avg_score), 1),
            },
            "pdf_enabled": REPORTLAB_AVAILABLE,
        },
    }


@router.get("/{report_id}/pdf")
def report_pdf(report_id: str, db: Session = Depends(get_db)):
    report = db.get(Report, report_id)
    if report is None:
        raise HTTPException(status_code=404, detail="Report not found")

    payload = _report_payload(report, db)
    pdf_bytes = _build_pdf_bytes(payload)
    if not pdf_bytes:
        raise HTTPException(status_code=503, detail="PDF generation is not available")

    child_name = _sanitize_filename(payload.get("child_name") or "child")
    report_type = payload.get("report_type") or "weekly"
    date_part = (payload.get("generated_at") or datetime.utcnow().isoformat()).split("T")[0]
    filename = f"BaoCao_{child_name}_{report_type}_{date_part}.pdf"
    return Response(
        content=pdf_bytes,
        media_type="application/pdf",
        headers={
            "Content-Disposition": f"inline; filename*=UTF-8''{quote(filename)}",
        },
    )


@router.get("/all")
def list_all_reports(skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    rows = db.query(Report).order_by(Report.generated_at.desc()).all()
    deduped = _dedupe_report_rows(rows)[skip : skip + limit]
    return {"status": "success", "data": [_report_payload(report, db) for report in deduped]}


@router.post("/test-email")
def test_email(request: TestEmailRequest):
    payload = {
        "child_name": "Bé thử nghiệm",
        "report_type": "test",
        "generated_at": datetime.utcnow().isoformat(),
        "summary": "Đây là email kiểm tra từ EmoGarden.",
        "stats": {"total_sessions": 0, "avg_score": 0, "progress_count": 0},
        "data": json.dumps({"games_stats": [], "achievements": []}, ensure_ascii=False),
    }
    sent, message = _send_report_email(request.email, payload)
    return {
        "status": "success" if sent else "warning",
        "message": message,
        "email_sent": sent,
        "pdf_enabled": REPORTLAB_AVAILABLE,
    }


@router.get("/{child_id}")
def get_reports_by_child(child_id: str, db: Session = Depends(get_db)):
    rows = db.query(Report).filter(Report.child_id == child_id).order_by(Report.generated_at.desc()).all()
    return [
        {
            "report_id": report.report_id,
            "report_type": report.report_type,
            "summary": report.summary,
            "data": report.data,
        }
        for report in rows
    ]
