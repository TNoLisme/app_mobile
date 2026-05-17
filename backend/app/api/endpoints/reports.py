from __future__ import annotations

import json
import re
import smtplib
import unicodedata
import uuid
from collections import defaultdict
from datetime import datetime, timedelta
from email.message import EmailMessage
from urllib.parse import quote

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, EmailStr
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.core.config import settings
from app.db.session import get_db
from app.models.analytics import ChildProgress, Report
from app.models.game import Game, GameContent, PlaySession, SessionQuestion
from app.models.user import Child, User
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


def _build_emotion_stats(db: Session, child_user_id: str, start_at: datetime) -> dict[str, dict]:
    stats = {
        display: {"correct": 0, "incorrect": 0, "accuracy": 0.0}
        for display in EMOTION_DISPLAY.values()
    }

    rows = (
        db.query(GameContent.emotion, SessionQuestion.is_correct, func.count(SessionQuestion.id))
        .join(SessionQuestion, SessionQuestion.question_id == GameContent.content_id)
        .join(PlaySession, PlaySession.session_id == SessionQuestion.session_id)
        .filter(PlaySession.user_id == child_user_id)
        .filter(PlaySession.start_time >= start_at)
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
        stats[label]["accuracy"] = round((correct * 100.0 / total), 1) if total else 0.0

    return stats


def _build_achievements(total_sessions: int, avg_score: float, games_stats: list[dict], emotion_stats: dict[str, dict]) -> list[str]:
    achievements: list[str] = []

    if total_sessions >= 20:
        achievements.append(f"Hoàn thành {total_sessions} phiên học trong kỳ.")
    elif total_sessions >= 10:
        achievements.append(f"Duy trì luyện tập đều với {total_sessions} phiên học.")
    elif total_sessions >= 5:
        achievements.append(f"Bé đã bắt đầu hình thành thói quen học với {total_sessions} phiên.")

    if avg_score >= 8:
        achievements.append("Điểm trung bình đạt mức xuất sắc.")
    elif avg_score >= 7:
        achievements.append("Điểm trung bình đạt mức tốt.")
    elif avg_score >= 6:
        achievements.append("Điểm trung bình đạt mức khá.")

    if games_stats:
        top_game = games_stats[0]
        if top_game["sessions"] >= 3:
            achievements.append(f"Chơi nhiều nhất: {top_game['game_name']} ({top_game['sessions']} lượt).")

    strong_emotions = [name for name, values in emotion_stats.items() if values.get("accuracy", 0) >= 80]
    if strong_emotions:
        achievements.append(f"Nhận diện tốt các cảm xúc: {', '.join(strong_emotions)}.")

    if not achievements:
        achievements.append("Chưa đủ dữ liệu nổi bật trong kỳ này.")

    return achievements


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

    avg_score = round((sum(score_values) / len(score_values)), 1) if score_values else 0.0
    total_games = len(game_ids)
    daily_sessions = _build_daily_sessions(sessions, start_at, end_at)
    games_stats = _build_games_stats(db, sessions)
    emotion_stats = _build_emotion_stats(db, child_user_id, start_at)
    achievements = _build_achievements(total_sessions, avg_score, games_stats, emotion_stats)

    if total_sessions == 0:
        summary = "Kỳ này bé chưa có phiên chơi nào. Hãy thử chơi thêm để tạo báo cáo chi tiết."
    elif avg_score >= 8:
        summary = f"Bé đã chơi {total_sessions} phiên, điểm trung bình {avg_score}. Bé tiến bộ rất tốt."
    elif avg_score >= 6:
        summary = f"Bé đã chơi {total_sessions} phiên, điểm trung bình {avg_score}. Bé đang tiến bộ ổn định."
    else:
        summary = f"Bé đã chơi {total_sessions} phiên, điểm trung bình {avg_score}. Nên luyện thêm để cải thiện."

    data = {
        "period": report_type,
        "start_date": start_at.strftime("%d/%m/%Y"),
        "end_date": end_at.strftime("%d/%m/%Y"),
        "total_sessions": total_sessions,
        "total_playtime_minutes": total_playtime_minutes,
        "avg_score": avg_score,
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

    stats = {
        "total_sessions": _safe_int(parsed_data.get("total_sessions"), 0),
        "avg_score": round(_safe_float(parsed_data.get("avg_score"), 0.0), 1),
        "progress_count": _safe_int(parsed_data.get("progress_count"), 0),
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
    child_name = payload.get("child_name") or "bé"
    report_type = payload.get("report_type") or "weekly"
    summary = payload.get("summary") or "Chưa có dữ liệu chi tiết."
    stats = payload.get("stats") or {}
    generated_at = payload.get("generated_at") or datetime.utcnow().isoformat()

    total_sessions = stats.get("total_sessions", 0)
    avg_score = stats.get("avg_score", 0)
    progress_count = stats.get("progress_count", 0)

    report_data = {}
    try:
        report_data = json.loads(payload.get("data") or "{}")
    except json.JSONDecodeError:
        report_data = {}

    top_games = report_data.get("games_stats", [])[:3]
    top_games_text = "\n".join(
        f"- {game.get('game_name', 'Trò chơi')}: {game.get('sessions', 0)} lượt"
        for game in top_games
    ) or "- Chưa có dữ liệu trò chơi."

    achievements = report_data.get("achievements", [])[:3]
    achievements_text = "\n".join(f"- {item}" for item in achievements) or "- Chưa có thành tựu nổi bật."

    reportlab_note = (
        ""
        if REPORTLAB_AVAILABLE
        else "\n\nLưu ý: hệ thống chưa có thư viện tạo PDF, email này chưa kèm tệp đính kèm."
    )

    return (
        f"EmoGarden - Báo cáo tiến độ ({report_type})\n\n"
        f"Người học: {child_name}\n"
        f"Thời điểm tạo: {generated_at}\n\n"
        f"Tóm tắt:\n{summary}\n\n"
        f"Thống kê chính:\n"
        f"- Tổng số phiên: {total_sessions}\n"
        f"- Điểm trung bình: {avg_score}\n"
        f"- Số bản ghi tiến trình: {progress_count}\n\n"
        f"Trò chơi luyện nhiều:\n{top_games_text}\n\n"
        f"Thành tựu nổi bật:\n{achievements_text}\n\n"
        "Phụ huynh vui lòng xem file PDF đính kèm để xem báo cáo đầy đủ."
        f"{reportlab_note}\n\n"
        "Email này được tạo tự động từ EmoGarden."
    )


def _sanitize_filename(filename: str) -> str:
    normalized = _strip_accents(filename)
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "_", normalized)
    cleaned = re.sub(r"_+", "_", cleaned).strip("_")
    return cleaned or "BaoCao"


def _build_pdf_bytes(payload: dict) -> bytes | None:
    if not REPORTLAB_AVAILABLE:
        return None
    child_name = payload.get("child_name") or "Be"
    report_type = payload.get("report_type") or "weekly"
    summary = payload.get("summary") or "Chưa có dữ liệu."
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
    smtp_host = (settings.SMTP_HOST or "").strip()
    smtp_from = (settings.SMTP_FROM_EMAIL or settings.SMTP_USERNAME or "").strip()

    if not smtp_host or not smtp_from:
        return False, "SMTP chưa được cấu hình đầy đủ."

    message = EmailMessage()
    message["Subject"] = "EmoGarden - Báo cáo tiến độ"
    message["From"] = f"{settings.SMTP_FROM_NAME} <{smtp_from}>"
    message["To"] = recipient
    message.set_content(_build_report_email_body(payload))

    pdf_bytes = _build_pdf_bytes(payload)
    if pdf_bytes:
        child_name = payload.get("child_name") or "Be"
        report_type = payload.get("report_type") or "weekly"
        date_part = datetime.utcnow().strftime("%Y%m%d")
        filename_utf8 = f"BaoCao_{child_name}_{report_type}_{date_part}.pdf"
        filename_ascii = _sanitize_filename(filename_utf8)
        message.add_attachment(
            pdf_bytes,
            maintype="application",
            subtype="pdf",
            filename=filename_ascii,
            disposition="attachment",
            params={"filename*": f"UTF-8''{quote(filename_utf8)}"},
        )

    try:
        with smtplib.SMTP(smtp_host, settings.SMTP_PORT, timeout=20) as server:
            if settings.SMTP_USE_TLS:
                server.starttls()
            if settings.SMTP_USERNAME and settings.SMTP_PASSWORD:
                server.login(settings.SMTP_USERNAME, settings.SMTP_PASSWORD)
            server.send_message(message)
        if pdf_bytes:
            return True, f"Đã gửi báo cáo PDF tới {recipient}."
        return True, f"Đã gửi báo cáo tới {recipient} (không có tệp PDF đính kèm)."
    except Exception as exc:  # pragma: no cover - external service
        return False, f"Gửi email thất bại: {exc}"


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


@router.get("/history")
def report_history(child_user_id: str | None = None, skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    query = db.query(Report)
    if child_user_id:
        query = query.filter(Report.child_id == child_user_id)
    rows = query.order_by(Report.generated_at.desc()).offset(skip).limit(limit).all()
    return {"status": "success", "data": [_report_payload(report, db) for report in rows]}


@router.get("/preview/{child_user_id}")
def preview_report(
    child_user_id: str,
    report_type: str = Query("weekly"),
    db: Session = Depends(get_db),
):
    normalized_type = _normalize_report_type(report_type)
    summary, data = _build_report_summary(db, child_user_id, normalized_type)
    return {
        "status": "success",
        "data": {
            "child_user_id": child_user_id,
            "report_type": normalized_type,
            "summary": summary,
            "stats": {
                "total_sessions": _safe_int(data.get("total_sessions"), 0),
                "avg_score": round(_safe_float(data.get("avg_score"), 0.0), 1),
                "progress_count": _safe_int(data.get("progress_count"), 0),
            },
            "insights": {
                "total_playtime_minutes": data.get("total_playtime_minutes", 0),
                "daily_sessions": data.get("daily_sessions", {}),
                "games_stats": data.get("games_stats", []),
                "emotion_stats": data.get("emotion_stats", {}),
                "achievements": data.get("achievements", []),
            },
            "pdf_enabled": REPORTLAB_AVAILABLE,
        },
    }


@router.get("/all")
def list_all_reports(skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    rows = db.query(Report).order_by(Report.generated_at.desc()).offset(skip).limit(limit).all()
    return {"status": "success", "data": [_report_payload(report, db) for report in rows]}


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
