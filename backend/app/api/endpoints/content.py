import json
import uuid
from datetime import datetime
from urllib.request import Request, urlopen

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import Response, StreamingResponse
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.models.analytics import ChildProgress
from app.models.game import Game, GameContent, PlaySession, SessionQuestion
from app.models.user import User
from app.schemas.content import CvScenarioListOut, CvScenarioOut, GameContentOut, GameOut
from app.schemas.runtime import EndLevelRequest, StartGameRequest

router = APIRouter()


class CvStartRequest(BaseModel):
    user_id: str
    game_type: str | None = None


class CvResultRequest(BaseModel):
    session_id: str
    scenario_id: str
    target_emotion: str
    detected_emotion: str | None = None
    success: bool
    time_taken: int | None = 0
    confidence_score: float | None = 0.0
    check_hint: bool | None = False


class CvEndRequest(BaseModel):
    session_id: str


def _question_count_for_level(level: int) -> int:
    if level <= 2:
        return 10
    if level <= 4:
        return 15
    if level <= 6:
        return 20
    if level <= 8:
        return 25
    return 30


def _threshold_for_level(game: Game, level: int) -> float:
    if game.game_type != "camera_game":
        return float(game.level_threshold or 70)
    return {1: 40, 2: 50, 3: 60, 4: 70, 5: 80}.get(level, 90)


def _cv_scenario(content: GameContent) -> CvScenarioOut:
    return CvScenarioOut(
        id=content.content_id,
        title=content.correct_answer or content.emotion or "emotion",
        description=content.question_text or "",
        target_emotion=content.correct_answer or content.emotion or "",
        instruction=content.question_text or "",
        hint=content.explanation,
        image_path=content.media_path,
        explanation=content.explanation,
        level=content.level,
    )


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


@router.get("/games", response_model=list[GameOut])
def list_games(game_type: str | None = Query(default=None), db: Session = Depends(get_db)):
    query = db.query(Game).order_by(Game.game_type, Game.name)
    if game_type:
        query = query.filter(Game.game_type == game_type)
    return query.all()


@router.get("/games/progress/{game_id}")
def get_game_progress(game_id: str, user_id: str = Query(...), db: Session = Depends(get_db)):
    progress = (
        db.query(ChildProgress)
        .filter(ChildProgress.child_id == user_id, ChildProgress.game_id == game_id)
        .order_by(ChildProgress.level.desc())
        .first()
    )
    if progress is None:
        return None
    return {
        "progress_id": progress.progress_id,
        "child_id": progress.child_id,
        "game_id": progress.game_id,
        "level": progress.level,
        "accuracy": progress.accuracy,
        "score": progress.score,
        "last_played": progress.last_played.isoformat() if progress.last_played else None,
        "ratio": progress.ratio,
        "review_emotions": progress.review_emotions,
    }


@router.get("/game-content/{game_id}", response_model=list[GameContentOut])
def list_game_content(game_id: str, level: int | None = Query(default=None), db: Session = Depends(get_db)):
    query = db.query(GameContent).filter(GameContent.game_id == game_id)
    if level is not None:
        query = query.filter(GameContent.level == level)
    return query.order_by(GameContent.level, GameContent.content_id).all()


@router.get("/games/cv/scenarios", response_model=CvScenarioListOut)
def list_cv_scenarios(level: int = 1, db: Session = Depends(get_db)):
    rows = (
        db.query(GameContent)
        .filter(GameContent.game_id == "e05909f3-3dee-42a6-9a75-fd985b1bdf47")
        .filter(GameContent.level == level)
        .order_by(GameContent.content_id)
        .all()
    )
    return CvScenarioListOut(scenarios=[_cv_scenario(row) for row in rows])


@router.get("/games/cv/requests", response_model=CvScenarioListOut)
def list_cv_requests(db: Session = Depends(get_db)):
    rows = (
        db.query(GameContent)
        .filter(GameContent.game_id == "61f5e09e-eefa-44c1-86e1-87dfceac3b8e")
        .order_by(GameContent.level, GameContent.content_id)
        .all()
    )
    return CvScenarioListOut(scenarios=[_cv_scenario(row) for row in rows])


@router.get("/games/{game_id}", response_model=GameOut)
def get_game(game_id: str, db: Session = Depends(get_db)):
    game = db.get(Game, game_id)
    if game is None:
        raise HTTPException(status_code=404, detail="Game not found")
    return game


@router.post("/games/cv/start")
def start_cv_session(body: CvStartRequest, db: Session = Depends(get_db)):
    game_id = "61f5e09e-eefa-44c1-86e1-87dfceac3b8e" if body.game_type == "request" else "e05909f3-3dee-42a6-9a75-fd985b1bdf47"
    result = start_game(game_id, StartGameRequest(user_id=body.user_id, level=1), db)
    return {"session_id": result["session_id"], "message": "Session started"}


@router.post("/games/cv/result")
def save_cv_result(body: CvResultRequest, db: Session = Depends(get_db)):
    session = db.get(PlaySession, body.session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")

    db.add(
        SessionQuestion(
            id=str(uuid.uuid4()),
            session_id=body.session_id,
            question_id=body.scenario_id,
            is_correct=1 if body.success else 0,
            response_time_ms=int(body.time_taken or 0),
            cv_confidence=float(body.confidence_score or 0.0),
            used_hint=1 if body.check_hint else 0,
        )
    )
    db.commit()
    return {"message": "Result saved"}


@router.post("/games/cv/end")
def end_cv_session(body: CvEndRequest, db: Session = Depends(get_db)):
    session = db.get(PlaySession, body.session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")

    rows = db.query(SessionQuestion).filter(SessionQuestion.session_id == body.session_id).all()
    total = max(len(rows), 1)
    correct = sum(1 for row in rows if row.is_correct)
    score = round(correct * 100 / total)
    emotion_errors: dict[str, dict] = {}

    for row in rows:
        content = db.get(GameContent, row.question_id) if row.question_id else None
        emotion = content.emotion if content else "unknown"
        stats = emotion_errors.setdefault(emotion, {"wrong": 0, "best_confidence": 0.0})
        stats["best_confidence"] = max(float(stats["best_confidence"]), float(row.cv_confidence or 0.0))
        if not row.is_correct:
            stats["wrong"] += 1

    session.state = "end"
    session.end_time = datetime.utcnow()
    session.score = score
    session.emotion_errors = json.dumps(emotion_errors, ensure_ascii=False)
    db.commit()

    return {
        "message": "Session ended",
        "session_id": session.session_id,
        "score": score,
        "emotion_errors": emotion_errors,
    }


@router.get("/games/cv/emotion-scores")
def get_cv_emotion_scores(user_id: str, db: Session = Depends(get_db)):
    scores = {"vui": 0.0, "buồn": 0.0, "ngạc nhiên": 0.0, "tức giận": 0.0, "sợ hãi": 0.0, "ghê tởm": 0.0}
    emotion_map = {
        "happy": "vui",
        "sad": "buồn",
        "surprise": "ngạc nhiên",
        "angry": "tức giận",
        "fear": "sợ hãi",
        "disgust": "ghê tởm",
    }
    rows = (
        db.query(GameContent.emotion, SessionQuestion.cv_confidence)
        .join(SessionQuestion, SessionQuestion.question_id == GameContent.content_id)
        .join(PlaySession, PlaySession.session_id == SessionQuestion.session_id)
        .filter(PlaySession.user_id == user_id)
        .all()
    )
    for emotion, confidence in rows:
        key = emotion_map.get((emotion or "").lower())
        if key:
            scores[key] = max(scores[key], round(float(confidence or 0.0), 2))
    return {"scores": scores}


@router.get("/games/cv/completed-levels")
def get_cv_completed_levels(user_id: str, db: Session = Depends(get_db)):
    max_level = 6
    rows = (
        db.query(ChildProgress)
        .filter(ChildProgress.child_id == user_id, ChildProgress.game_id == "e05909f3-3dee-42a6-9a75-fd985b1bdf47")
        .all()
    )
    progress_by_level = {row.level: row for row in rows}
    levels = []
    current_level = 1
    for level in range(1, max_level + 1):
        progress = progress_by_level.get(level)
        score = int(progress.score or 0) if progress else 0
        completed = score >= 80
        unlocked = level == 1 or (level - 1 in progress_by_level and int(progress_by_level[level - 1].score or 0) >= 80)
        if unlocked and not completed:
            current_level = level
        levels.append({"level": level, "score": score, "max_score": 100, "completed": completed, "unlocked": unlocked})
    return {"levels": levels, "current_level": current_level, "max_level": max_level}


@router.get("/games/cv/audio-proxy")
def audio_proxy(url: str):
    try:
        request = Request(url, headers={"User-Agent": "EmoGarden/1.0"})
        with urlopen(request, timeout=30) as response:
            content = response.read()
            content_type = response.headers.get("content-type", "audio/mpeg")
        return Response(content=content, media_type=content_type, headers={"Access-Control-Allow-Origin": "*"})
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Không tải được audio: {exc}") from exc


@router.post("/games/start/{game_id}")
def start_game(game_id: str, body: StartGameRequest, db: Session = Depends(get_db)):
    game = db.get(Game, game_id)
    if game is None:
        raise HTTPException(status_code=404, detail="Game not found")
    _ensure_user(db, body.user_id)

    questions = (
        db.query(GameContent)
        .filter(GameContent.game_id == game_id, GameContent.level == body.level)
        .order_by(GameContent.content_id)
        .limit(_question_count_for_level(body.level))
        .all()
    )
    if not questions:
        questions = (
            db.query(GameContent)
            .filter(GameContent.game_id == game_id)
            .order_by(GameContent.level, GameContent.content_id)
            .limit(_question_count_for_level(body.level))
            .all()
        )

    session = PlaySession(
        session_id=str(uuid.uuid4()),
        user_id=body.user_id,
        game_id=game_id,
        start_time=datetime.utcnow(),
        state="playing",
        score=0,
        emotion_errors="{}",
        max_errors=game.max_errors,
        level_threshold=_threshold_for_level(game, body.level),
        ratio="[]",
        time_limit=game.time_limit,
        question_ids=json.dumps([question.content_id for question in questions]),
        level=body.level,
    )
    db.add(session)
    db.commit()

    return {
        "session_id": session.session_id,
        "game_id": game_id,
        "level": body.level,
        "max_errors": game.max_errors,
        "level_threshold": session.level_threshold,
        "time_limit": game.time_limit,
        "questions": [GameContentOut.model_validate(question).model_dump() for question in questions],
    }


@router.post("/games/start-dynamic/{game_id}")
def start_game_dynamic(game_id: str, body: StartGameRequest, db: Session = Depends(get_db)):
    return start_game(game_id, body, db)


@router.post("/games/end-level")
def end_level(body: EndLevelRequest, db: Session = Depends(get_db)):
    session = db.get(PlaySession, body.session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")

    total = max(len(body.results), 1)
    correct = sum(1 for result in body.results if result.is_correct)
    accuracy = correct * 100.0 / total
    emotion_errors: dict[str, int] = {}

    for result in body.results:
        if result.is_correct:
            continue
        content = db.get(GameContent, result.question_id)
        if content and content.emotion:
            emotion_errors[content.emotion] = emotion_errors.get(content.emotion, 0) + 1

    session.state = "end"
    session.end_time = datetime.utcnow()
    session.score = round(accuracy)
    session.emotion_errors = json.dumps(emotion_errors, ensure_ascii=False)

    progress = (
        db.query(ChildProgress)
        .filter(ChildProgress.child_id == session.user_id, ChildProgress.game_id == session.game_id, ChildProgress.level == session.level)
        .first()
    )
    if progress is None:
        progress = ChildProgress(
            progress_id=str(uuid.uuid4()),
            child_id=session.user_id,
            game_id=session.game_id,
            level=session.level,
        )
        db.add(progress)

    progress.accuracy = accuracy
    progress.score = round(accuracy)
    progress.last_played = datetime.utcnow()
    progress.review_emotions = json.dumps(body.review_emotions, ensure_ascii=False)

    db.commit()

    return {
        "session_id": session.session_id,
        "score": session.score,
        "accuracy": accuracy,
        "emotion_errors": emotion_errors,
        "passed": accuracy >= float(session.level_threshold or 70),
    }
