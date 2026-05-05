import json
import uuid
from datetime import datetime

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Query
from pydantic import BaseModel, EmailStr
from sqlalchemy import func
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
    total_sessions = db.query(func.count(PlaySession.session_id)).filter(PlaySession.user_id == child_user_id).scalar() or 0
    avg_score = db.query(func.avg(PlaySession.score)).filter(PlaySession.user_id == child_user_id).scalar() or 0
    progress_count = db.query(func.count(ChildProgress.progress_id)).filter(ChildProgress.child_id == child_user_id).scalar() or 0
    data = {
        "total_sessions": int(total_sessions),
        "avg_score": round(float(avg_score), 1),
        "progress_count": int(progress_count),
    }
    summary = (
        f"Bé đã chơi {data['total_sessions']} phiên, "
        f"điểm trung bình {data['avg_score']}."
    )
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
def request_report(request: GenerateReportRequest, db: Session = Depends(get_db)):
    report = _create_report(db, request.child_user_id, request.report_type)
    return {"status": "success", "message": "Đã nhận yêu cầu báo cáo", "data": _report_payload(report, db)}


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

