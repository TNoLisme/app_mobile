import json
import uuid
from datetime import datetime

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.models.analytics import ChatbotLog, ChildProgress, Report
from app.models.game import PlaySession, SessionQuestion
from app.models.user import Child, User
from app.schemas.runtime import ChatbotLogRequest, ProgressOut, ReportOut, SessionQuestionSaveRequest, SessionSaveRequest

router = APIRouter()


def _parse_datetime(value: str | None) -> datetime:
    if not value:
        return datetime.utcnow()
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).replace(tzinfo=None)
    except ValueError:
        return datetime.utcnow()


def _ensure_user(db: Session, user_id: str) -> None:
    if db.get(User, user_id) is None:
        db.add(
            User(
                user_id=user_id,
                email=f"{user_id}@local.invalid",
                role="child",
                name="Local Player",
            )
        )
        db.flush()


@router.get("/children/{uid}")
def get_child(uid: str, db: Session = Depends(get_db)):
    child = db.get(Child, uid)
    if child is None:
        return {
            "user_id": uid,
            "age": None,
            "gender": None,
            "date_of_birth": None,
            "phone_number": None,
            "report_preferences": None,
        }

    return {
        "user_id": child.user_id,
        "age": child.age,
        "gender": child.gender,
        "date_of_birth": child.date_of_birth.isoformat() if child.date_of_birth else None,
        "phone_number": child.phone_number,
        "report_preferences": child.report_preferences,
    }


@router.post("/sessions/save", response_model=bool)
def save_session(body: SessionSaveRequest, db: Session = Depends(get_db)):
    _ensure_user(db, body.user_id)
    session = db.get(PlaySession, body.session_id)
    if session is None:
        session = PlaySession(
            session_id=body.session_id,
            user_id=body.user_id,
            start_time=_parse_datetime(body.start_time),
        )
        db.add(session)

    session.score = body.score
    session.emotion_errors = body.emotion_errors
    db.commit()
    return True


@router.post("/sessions/questions", response_model=bool)
def save_session_questions(body: list[SessionQuestionSaveRequest], db: Session = Depends(get_db)):
    for item in body:
        row = db.get(SessionQuestion, item.id)
        if row is None:
            row = SessionQuestion(id=item.id, session_id=item.session_id)
            db.add(row)
        row.question_id = item.question_id
        row.is_correct = 1 if item.is_correct else 0
        row.response_time_ms = item.response_time_ms
        row.cv_confidence = item.cv_confidence
        row.used_hint = 1 if item.used_hint else 0
    db.commit()
    return True


@router.post("/chatbot/logs", response_model=bool)
def save_chatbot_log(body: ChatbotLogRequest, db: Session = Depends(get_db)):
    _ensure_user(db, body.child_id)
    db.add(
        ChatbotLog(
            child_id=body.child_id,
            sender=body.sender,
            message_content=body.message_content,
            timestamp=_parse_datetime(body.timestamp),
        )
    )
    db.commit()
    return True


@router.get("/sessions/user/{user_id}/history")
def get_user_session_history(user_id: str, skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    rows = (
        db.query(PlaySession)
        .filter(PlaySession.user_id == user_id)
        .order_by(PlaySession.start_time.desc())
        .offset(skip)
        .limit(limit)
        .all()
    )
    return {
        "status": "success",
        "sessions": [
            {
                "session_id": row.session_id,
                "game_id": row.game_id,
                "start_time": row.start_time.isoformat() if row.start_time else None,
                "end_time": row.end_time.isoformat() if row.end_time else None,
                "score": int(row.score or 0),
                "level": int(row.level or 1),
            }
            for row in rows
        ],
    }


@router.get("/progress/{child_id}", response_model=list[ProgressOut])
def list_progress(child_id: str, db: Session = Depends(get_db)):
    rows = db.query(ChildProgress).filter(ChildProgress.child_id == child_id).order_by(ChildProgress.last_played.desc()).all()
    return [
        ProgressOut(
            progress_id=row.progress_id,
            child_id=row.child_id,
            accuracy=float(row.accuracy or 0),
            score=int(row.score or 0),
            last_played=row.last_played.isoformat() if row.last_played else "",
        )
        for row in rows
    ]


@router.get("/reports/{identifier}")
def list_reports(identifier: str, db: Session = Depends(get_db)):
    report = db.get(Report, identifier)
    if report is not None:
        return {
            "report_id": report.report_id,
            "child_id": report.child_id,
            "report_type": report.report_type,
            "generated_at": report.generated_at.isoformat() if report.generated_at else None,
            "summary": report.summary,
            "data": report.data,
        }

    rows = db.query(Report).filter(Report.child_id == identifier).order_by(Report.generated_at.desc()).all()
    if rows:
        return [
            ReportOut(
                report_id=row.report_id,
                report_type=row.report_type,
                summary=row.summary or "",
                data=row.data,
            )
            for row in rows
        ]

    return [
        ReportOut(
            report_id=str(uuid.uuid4()),
            report_type="weekly",
            summary="Chưa có dữ liệu chơi game để tạo báo cáo.",
            data=json.dumps({"total": 0, "correct": 0}),
        )
    ]
