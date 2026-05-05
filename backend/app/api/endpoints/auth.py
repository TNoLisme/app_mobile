import json
import uuid
from datetime import date, datetime

from fastapi import APIRouter, Body, Depends, HTTPException, Query
from pydantic import BaseModel, EmailStr
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.models.analytics import ChildProgress
from app.models.game import Game, GameContent, PlaySession, SessionQuestion
from app.models.user import Child, User
from app.schemas.user import UserDto, UserSyncRequest

router = APIRouter()

EMOTION_MAP = {
    "happy": "Vui vẻ",
    "sad": "Buồn bã",
    "angry": "Tức giận",
    "fear": "Sợ hãi",
    "surprise": "Ngạc nhiên",
    "disgust": "Ghê tởm",
}

EMOTION_VARIATIONS = {
    "happy": ["happy", "vui", "vui ve", "vui vẻ"],
    "sad": ["sad", "buon", "buồn", "buon ba", "buồn bã"],
    "angry": ["angry", "gian", "giận", "tuc gian", "tức giận"],
    "fear": ["fear", "so", "sợ", "so hai", "sợ hãi"],
    "surprise": ["surprise", "ngac", "ngạc", "ngac nhien", "ngạc nhiên"],
    "disgust": ["disgust", "ghe", "ghê", "ghe tom", "ghê tởm"],
}


class ChildRegisterRequest(BaseModel):
    username: str | None = None
    email: EmailStr
    password: str
    role: str = "child"
    name: str | None = None
    age: int | None = None
    report_preferences: str | None = None
    gender: str | None = None
    date_of_birth: date | None = None
    phone_number: str | None = None


class LoginRequest(BaseModel):
    username: str
    password: str


class VerifyOtpRequest(BaseModel):
    email: EmailStr
    otp: str


class ForgotPasswordRequest(BaseModel):
    email: EmailStr


class ResetPasswordRequest(BaseModel):
    email: EmailStr
    otp: str
    new_password: str


def _normalize_emotion(emotion: str | None) -> str | None:
    if not emotion:
        return None
    cleaned = emotion.strip().lower()
    for key, variations in EMOTION_VARIATIONS.items():
        if any(variant in cleaned for variant in variations):
            return key
    return None


def _user_payload(user: User, child: Child | None = None) -> dict:
    payload = {
        "user_id": user.user_id,
        "username": user.username,
        "email": user.email,
        "role": user.role,
        "name": user.name,
        "created_at": user.created_at.isoformat() if user.created_at else None,
    }
    if child is not None:
        payload["child"] = {
            "user_id": child.user_id,
            "age": child.age,
            "gender": child.gender,
            "date_of_birth": child.date_of_birth.isoformat() if child.date_of_birth else None,
            "phone_number": child.phone_number,
            "report_preferences": child.report_preferences,
        }
    return payload


@router.post("/sync", response_model=UserDto)
async def sync_user(request: UserDto, db: Session = Depends(get_db)):
    db_user = db.query(User).filter(User.user_id == request.user_id).first()
    if db_user is None:
        db_user = User(user_id=request.user_id)
        db.add(db_user)

    db_user.username = request.username
    db_user.email = request.email
    db_user.name = request.name
    db_user.role = request.role

    db.commit()
    db.refresh(db_user)
    return db_user


@router.post("/register-sync")
async def register_user_sync(request: UserSyncRequest, db: Session = Depends(get_db)):
    try:
        db_user = db.query(User).filter(User.user_id == request.user_id).first()
        if db_user is None:
            db_user = User(user_id=request.user_id)
            db.add(db_user)

        db_user.email = request.email
        db_user.name = request.name
        db_user.role = request.role

        child = db.query(Child).filter(Child.user_id == request.user_id).first()
        if child is None:
            child = Child(user_id=request.user_id)
            db.add(child)

        child.age = request.age
        child.gender = request.gender

        db.commit()
        return {"status": "success", "message": "Synced successfully"}
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e)) from e


@router.post("/register")
async def register(user: ChildRegisterRequest, db: Session = Depends(get_db)):
    existing = db.query(User).filter((User.email == user.email) | (User.username == user.username)).first()
    if existing is not None:
        raise HTTPException(status_code=400, detail="User already exists")

    user_id = str(uuid.uuid4())
    new_user = User(
        user_id=user_id,
        username=user.username or user.email.split("@")[0],
        email=user.email,
        password=user.password,
        role=user.role,
        name=user.name,
    )
    db.add(new_user)

    if user.role == "child":
        db.add(
            Child(
                user_id=user_id,
                age=user.age,
                gender=user.gender,
                date_of_birth=user.date_of_birth,
                phone_number=user.phone_number,
                report_preferences=user.report_preferences,
            )
        )

    db.commit()
    return {"status": "success", "message": "Đăng ký thành công", "data": {"user_id": user_id}}


@router.post("/login")
async def login(request: LoginRequest, db: Session = Depends(get_db)):
    user = db.query(User).filter((User.username == request.username) | (User.email == request.username)).first()
    if user is None or (user.password and user.password != request.password):
        raise HTTPException(status_code=400, detail="Sai tài khoản hoặc mật khẩu")

    child = db.query(Child).filter(Child.user_id == user.user_id).first()
    return {"success": True, "message": "Đăng nhập thành công", "user": _user_payload(user, child)}


@router.post("/verify-otp")
async def verify_otp(request: VerifyOtpRequest):
    return {"status": "success", "message": "OTP hợp lệ"}


@router.post("/logout")
async def api_logout():
    return {"success": True, "message": "Đăng xuất thành công"}


@router.post("/forgot-password")
async def forgot_password(request: ForgotPasswordRequest, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.email == request.email).first()
    if user is None:
        raise HTTPException(status_code=404, detail="Không tìm thấy email")
    return {"status": "success", "message": "OTP đặt lại mật khẩu: 123456", "otp": "123456"}


@router.post("/reset-password")
async def reset_password(request: ResetPasswordRequest, db: Session = Depends(get_db)):
    if request.otp != "123456":
        raise HTTPException(status_code=400, detail="OTP không hợp lệ")
    user = db.query(User).filter(User.email == request.email).first()
    if user is None:
        raise HTTPException(status_code=404, detail="Không tìm thấy email")
    user.password = request.new_password
    db.commit()
    return {"status": "success", "message": "Đổi mật khẩu thành công"}


@router.get("/me")
async def get_profile(user_id: str = Query(...), db: Session = Depends(get_db)):
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=404, detail="User not found")
    child = db.query(Child).filter(Child.user_id == user_id).first()
    return _user_payload(user, child)


@router.put("/me")
async def update_profile(payload: dict = Body(...), db: Session = Depends(get_db)):
    user_id = payload.get("user_id")
    update = payload.get("update", {})
    if not user_id:
        raise HTTPException(status_code=400, detail="Thiếu user_id")

    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=404, detail="Không tìm thấy người dùng")

    for field in ["name", "username", "email", "password", "role"]:
        if update.get(field) not in [None, ""]:
            setattr(user, field, update[field])

    child = db.query(Child).filter(Child.user_id == user_id).first()
    if child is None and user.role == "child":
        child = Child(user_id=user_id)
        db.add(child)

    if child is not None:
        for field in ["age", "gender", "date_of_birth", "phone_number", "report_preferences"]:
            if update.get(field) not in [None, ""]:
                setattr(child, field, update[field])

    db.commit()
    db.refresh(user)
    return _user_payload(user, child)


@router.get("/stats/recent-games/{user_id}")
async def get_recent_games(user_id: str, limit: int = 4, db: Session = Depends(get_db)):
    rows = (
        db.query(Game.game_id, Game.name, Game.game_type, func.max(PlaySession.start_time).label("last_played"))
        .join(PlaySession, PlaySession.game_id == Game.game_id)
        .filter(PlaySession.user_id == user_id)
        .group_by(Game.game_id, Game.name, Game.game_type)
        .order_by(func.max(PlaySession.start_time).desc())
        .limit(limit)
        .all()
    )
    return {
        "status": "success",
        "data": [
            {
                "game_id": row.game_id,
                "name": row.name,
                "game_type": row.game_type,
                "last_played": row.last_played.isoformat() if row.last_played else None,
            }
            for row in rows
        ],
    }


@router.get("/stats/emotion-accuracy/{user_id}")
async def get_emotion_accuracy_stats(user_id: str, db: Session = Depends(get_db)):
    stats = {name: {"correct": 0, "incorrect": 0, "accuracy": 0.0} for name in EMOTION_MAP.values()}
    rows = (
        db.query(GameContent.emotion, SessionQuestion.is_correct, func.count(SessionQuestion.id))
        .join(SessionQuestion, SessionQuestion.question_id == GameContent.content_id)
        .join(PlaySession, PlaySession.session_id == SessionQuestion.session_id)
        .filter(PlaySession.user_id == user_id)
        .group_by(GameContent.emotion, SessionQuestion.is_correct)
        .all()
    )
    for emotion, is_correct, count in rows:
        normalized = _normalize_emotion(emotion)
        if normalized is None:
            continue
        display = EMOTION_MAP[normalized]
        if is_correct:
            stats[display]["correct"] += int(count)
        else:
            stats[display]["incorrect"] += int(count)

    for item in stats.values():
        total = item["correct"] + item["incorrect"]
        item["accuracy"] = round(item["correct"] * 100 / total, 1) if total else 0.0

    return {"status": "success", "data": stats}


@router.get("/stats/emotion-improvement/{user_id}")
async def get_emotion_improvement_stats(user_id: str, db: Session = Depends(get_db)):
    timelines = {key: [] for key in EMOTION_MAP}
    rows = (
        db.query(ChildProgress.review_emotions, ChildProgress.accuracy)
        .filter(ChildProgress.child_id == user_id)
        .order_by(ChildProgress.last_played.asc())
        .all()
    )
    for review_emotions, accuracy in rows:
        try:
            emotions = json.loads(review_emotions or "[]")
        except json.JSONDecodeError:
            emotions = []
        for emotion in emotions:
            normalized = _normalize_emotion(str(emotion))
            if normalized:
                timelines[normalized].append(float(accuracy or 0))

    data = {}
    for key, display in EMOTION_MAP.items():
        values = timelines[key]
        if len(values) >= 2:
            mid = len(values) // 2
            old_avg = sum(values[:mid]) / len(values[:mid])
            new_avg = sum(values[mid:]) / len(values[mid:])
            data[display] = round(new_avg - old_avg, 1)
        else:
            data[display] = 0
    return {"status": "success", "data": data}


@router.get("/stats/game-play-ratio/{user_id}")
async def get_game_play_ratio(user_id: str, db: Session = Depends(get_db)):
    rows = (
        db.query(Game.name, func.count(PlaySession.session_id))
        .join(PlaySession, PlaySession.game_id == Game.game_id)
        .filter(PlaySession.user_id == user_id)
        .group_by(Game.name)
        .all()
    )
    total = sum(int(count) for _, count in rows)
    data = {name: round(int(count) * 100 / total, 1) for name, count in rows} if total else {}
    return {"status": "success", "data": data}


@router.get("/stats/weak-emotions/{user_id}")
async def get_weak_emotions(user_id: str, limit: int = 3, db: Session = Depends(get_db)):
    accuracy_response = await get_emotion_accuracy_stats(user_id, db)
    items = []
    for emotion, values in accuracy_response["data"].items():
        accuracy = float(values["accuracy"])
        if accuracy < 80:
            items.append({"emotion": emotion, "error_rate": round(100 - accuracy, 1)})
    items.sort(key=lambda item: item["error_rate"], reverse=True)
    return {"status": "success", "data": items[:limit]}
