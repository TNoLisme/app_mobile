import json
import random
import uuid
from datetime import datetime
from urllib.request import Request, urlopen

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import Response, StreamingResponse
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.models.analytics import ChildProgress
from app.models.game import Game, GameContent, GameData, GameDataQuestion, PlaySession, SessionQuestion
from app.models.user import User, Child
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


class ResetReviewRequest(BaseModel):
    user_id: str
    emotions: list[str] = []


GAME_RECOGNIZE_EMOTION = "3bcb2108-721c-4a15-a585-31f3084ed000"
GAME_FACE_ASSEMBLY = "33ecafaa-ec7e-40d2-9c67-ed0a29ac0051"
GAME_EMOTION_MATCH = "08bbffbf-d147-4556-bccb-b7621cafbf15"
GAME_DETECTIVE = "aacaf79e-e15e-42a9-a3d1-a522720d919b"
CV_STORY_GAME_ID = "e05909f3-3dee-42a6-9a75-fd985b1bdf47"
CV_REQUEST_GAME_ID = "61f5e09e-eefa-44c1-86e1-87dfceac3b8e"
CV_STORY_QUESTION_COUNT = 5
CV_STORY_MAX_LEVEL = 5
CV_REQUEST_QUESTION_COUNT = 6
CLICK_GAME_IDS = {
    GAME_RECOGNIZE_EMOTION,
    GAME_FACE_ASSEMBLY,
    GAME_EMOTION_MATCH,
    GAME_DETECTIVE,
}
CLICK_MAX_LEVEL = 8
CLICK_QUESTION_COUNT = 5
CLICK_PASS_SCORE = 30
EMOTION_KEYS = ["happy", "sad", "angry", "fear", "surprise", "disgust"]
EMOTION_ALIASES = {
    "happy": "happy",
    "vui": "happy",
    "sad": "sad",
    "buon": "sad",
    "buồn": "sad",
    "angry": "angry",
    "tuc": "angry",
    "tức": "angry",
    "fear": "fear",
    "so": "fear",
    "sợ": "fear",
    "surprise": "surprise",
    "ngac": "surprise",
    "ngạc": "surprise",
    "disgust": "disgust",
    "ghe": "disgust",
    "ghê": "disgust",
}
DEFAULT_RATIO = [0.1667, 0.1667, 0.1667, 0.1667, 0.1667, 0.1665]
DEFAULT_REVIEW_EMOTIONS = {emotion: 0 for emotion in EMOTION_KEYS}


def _load_json(value, fallback):
    if value is None:
        return fallback.copy() if isinstance(fallback, dict) else list(fallback)
    if isinstance(value, (dict, list)):
        return value
    try:
        parsed = json.loads(value)
    except (TypeError, ValueError):
        return fallback.copy() if isinstance(fallback, dict) else list(fallback)
    return parsed if isinstance(parsed, type(fallback)) else fallback.copy() if isinstance(fallback, dict) else list(fallback)


def _normalize_emotion(value: str | None) -> str | None:
    if not value:
        return None
    lowered = value.strip().lower()
    if lowered in EMOTION_ALIASES:
        return EMOTION_ALIASES[lowered]
    for token, emotion in EMOTION_ALIASES.items():
        if token in lowered:
            return emotion
    return lowered if lowered in EMOTION_KEYS else None


def _normalized_ratio(value) -> list[float]:
    parsed = _load_json(value, DEFAULT_RATIO)
    if not isinstance(parsed, list) or len(parsed) != len(EMOTION_KEYS):
        return list(DEFAULT_RATIO)
    try:
        ratio = [max(float(item), 0.0) for item in parsed]
    except (TypeError, ValueError):
        return list(DEFAULT_RATIO)
    total = sum(ratio)
    if total <= 0:
        return list(DEFAULT_RATIO)
    normalized = [round(item / total, 4) for item in ratio]
    normalized[-1] = round(1.0 - sum(normalized[:-1]), 4)
    return normalized


def _normalized_review(value) -> dict[str, int]:
    parsed = _load_json(value, DEFAULT_REVIEW_EMOTIONS)
    review = dict(DEFAULT_REVIEW_EMOTIONS)
    if isinstance(parsed, dict):
        for key, count in parsed.items():
            emotion = _normalize_emotion(key)
            if emotion in review:
                try:
                    review[emotion] = max(int(count), 0)
                except (TypeError, ValueError):
                    review[emotion] = 0
    elif isinstance(parsed, list):
        for item in parsed:
            emotion = _normalize_emotion(item)
            if emotion in review:
                review[emotion] += 1
    return review


def _merge_click_review_emotions(db: Session, user_id: str) -> dict[str, int]:
    rows = db.query(ChildProgress).filter(ChildProgress.child_id == user_id, ChildProgress.game_id.in_(CLICK_GAME_IDS)).all()
    merged = dict(DEFAULT_REVIEW_EMOTIONS)
    for row in rows:
        review = _normalized_review(row.review_emotions)
        for emotion in EMOTION_KEYS:
            merged[emotion] = max(merged[emotion], review.get(emotion, 0))
    return merged


def _sync_click_review_emotions(db: Session, user_id: str, review: dict[str, int]) -> None:
    payload = json.dumps(review, ensure_ascii=False)
    rows = db.query(ChildProgress).filter(ChildProgress.child_id == user_id, ChildProgress.game_id.in_(CLICK_GAME_IDS)).all()
    for row in rows:
        row.review_emotions = payload


def _ensure_progress(db: Session, user_id: str, game_id: str) -> ChildProgress:
    progress = (
        db.query(ChildProgress)
        .filter(ChildProgress.child_id == user_id, ChildProgress.game_id == game_id)
        .order_by(ChildProgress.level.desc())
        .first()
    )
    shared_review = _merge_click_review_emotions(db, user_id) if game_id in CLICK_GAME_IDS else dict(DEFAULT_REVIEW_EMOTIONS)
    if progress is None:
        progress = ChildProgress(
            progress_id=str(uuid.uuid4()),
            child_id=user_id,
            game_id=game_id,
            level=1,
            accuracy=0.0,
            avg_response_time=0.0,
            score=0,
            last_played=datetime.utcnow(),
            ratio=json.dumps(DEFAULT_RATIO),
            review_emotions=json.dumps(shared_review, ensure_ascii=False),
        )
        db.add(progress)
        db.flush()
    else:
        progress.ratio = json.dumps(_normalized_ratio(progress.ratio))
        if game_id in CLICK_GAME_IDS:
            progress.review_emotions = json.dumps(shared_review, ensure_ascii=False)
        else:
            progress.review_emotions = json.dumps(_normalized_review(progress.review_emotions), ensure_ascii=False)
    return progress


def _progress_payload(progress: ChildProgress, review: dict[str, int] | None = None) -> dict:
    return {
        "progress_id": progress.progress_id,
        "child_id": progress.child_id,
        "game_id": progress.game_id,
        "level": progress.level,
        "accuracy": progress.accuracy,
        "score": progress.score,
        "last_played": progress.last_played.isoformat() if progress.last_played else None,
        "ratio": _normalized_ratio(progress.ratio),
        "review_emotions": review or _normalized_review(progress.review_emotions),
    }


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


def _question_count_for_game_level(game_id: str, level: int) -> int:
    if game_id in CLICK_GAME_IDS:
        return CLICK_QUESTION_COUNT
    if game_id == CV_STORY_GAME_ID:
        return CV_STORY_QUESTION_COUNT
    if game_id == CV_REQUEST_GAME_ID:
        return CV_REQUEST_QUESTION_COUNT
    return _question_count_for_level(level)


def _level_content(db: Session, game_id: str, level: int) -> list[GameContent]:
    return (
        db.query(GameContent)
        .filter(GameContent.game_id == game_id, GameContent.level == level)
        .order_by(GameContent.content_id)
        .all()
    )


def _cached_click_questions(db: Session, game_id: str, user_id: str, level: int) -> list[GameContent]:
    game_data = (
        db.query(GameData)
        .filter(GameData.game_id == game_id, GameData.user_id == user_id, GameData.level == level)
        .order_by(GameData.created_at.desc())
        .first()
    )
    if game_data is None:
        return []
    ids = [
        row.question_id
        for row in db.query(GameDataQuestion)
        .filter(GameDataQuestion.data_id == game_data.data_id)
        .all()
    ]
    if not ids:
        return []
    rows = db.query(GameContent).filter(GameContent.content_id.in_(ids)).all()
    by_id = {row.content_id: row for row in rows}
    return [by_id[item] for item in ids if item in by_id]


def _counts_from_ratio(ratio: list[float], count: int) -> dict[str, int]:
    counts = {emotion: round(ratio[index] * count) for index, emotion in enumerate(EMOTION_KEYS)}
    while sum(counts.values()) > count:
        emotion = max(counts, key=counts.get)
        counts[emotion] -= 1
    while sum(counts.values()) < count:
        emotion = min(counts, key=counts.get)
        counts[emotion] += 1
    return counts


def _generate_click_questions(db: Session, game_id: str, user_id: str, level: int, ratio: list[float]) -> list[GameContent]:
    count = CLICK_QUESTION_COUNT
    candidates = _level_content(db, game_id, level)
    if len(candidates) < count:
        raise HTTPException(
            status_code=409,
            detail={
                "code": "level_content_unavailable",
                "message": "Level này đang được cập nhật câu hỏi.",
                "available": len(candidates),
                "required": count,
            },
        )

    grouped: dict[str, list[GameContent]] = {emotion: [] for emotion in EMOTION_KEYS}
    for item in candidates:
        emotion = _normalize_emotion(item.emotion or item.correct_answer)
        if emotion in grouped:
            grouped[emotion].append(item)

    selected: list[GameContent] = []
    used_ids: set[str] = set()
    for emotion, target_count in _counts_from_ratio(ratio, count).items():
        pool = [item for item in grouped.get(emotion, []) if item.content_id not in used_ids]
        take = min(len(pool), target_count)
        if take > 0:
            picks = random.sample(pool, take)
            selected.extend(picks)
            used_ids.update(item.content_id for item in picks)

    if len(selected) < count:
        remaining = [item for item in candidates if item.content_id not in used_ids]
        selected.extend(random.sample(remaining, count - len(selected)))

    data_id = str(uuid.uuid4())
    db.add(GameData(data_id=data_id, game_id=game_id, user_id=user_id, level=level))
    for item in selected:
        db.add(GameDataQuestion(data_id=data_id, question_id=item.content_id))
    db.flush()
    return selected


def _select_click_questions(db: Session, game_id: str, user_id: str, level: int, ratio: list[float]) -> list[GameContent]:
    cached = _cached_click_questions(db, game_id, user_id, level)
    if len(cached) >= CLICK_QUESTION_COUNT:
        return random.sample(cached, CLICK_QUESTION_COUNT)
    return _generate_click_questions(db, game_id, user_id, level, ratio)


def _select_questions_for_run(db: Session, game_id: str, level: int, user_id: str | None = None, ratio: list[float] | None = None) -> list[GameContent]:
    count = _question_count_for_game_level(game_id, level)

    if game_id in CLICK_GAME_IDS:
        if user_id is None:
            raise HTTPException(status_code=400, detail="Missing user_id")
        return _select_click_questions(db, game_id, user_id, level, ratio or list(DEFAULT_RATIO))

    if game_id != CV_STORY_GAME_ID:
        questions = _level_content(db, game_id, level)[:count]
        if questions:
            return questions
        return (
            db.query(GameContent)
            .filter(GameContent.game_id == game_id)
            .order_by(GameContent.level, GameContent.content_id)
            .limit(count)
            .all()
        )

    level_questions = _level_content(db, game_id, level)
    if len(level_questions) >= count:
        return random.sample(level_questions, count)

    used_ids = {question.content_id for question in level_questions}
    fallback_questions = (
        db.query(GameContent)
        .filter(GameContent.game_id == game_id)
        .order_by(GameContent.level, GameContent.content_id)
        .all()
    )
    fallback_questions = [question for question in fallback_questions if question.content_id not in used_ids]
    combined_questions = level_questions + fallback_questions
    if len(combined_questions) <= count:
        return combined_questions
    return random.sample(combined_questions, count)


def _threshold_for_level(game: Game, level: int) -> float:
    if game.game_type != "camera_game":
        return float(game.level_threshold or 70)
    return {1: 40, 2: 50, 3: 60, 4: 70, 5: 80}.get(level, 90)


def _click_pass_threshold(game: Game) -> float:
    return float(CLICK_PASS_SCORE)
    # return float(game.level_threshold or CLICK_PASS_SCORE)


def _question_option(content: GameContent) -> dict:
    return {
        "content_id": content.content_id,
        "media_path": content.media_path,
        "answer_text": content.correct_answer or content.emotion,
        "emotion": _normalize_emotion(content.emotion or content.correct_answer),
    }


def _format_game_content(content: GameContent, options_pool: list[GameContent] | None = None) -> dict:
    payload = GameContentOut.model_validate(content).model_dump()
    if options_pool is None:
        return payload

    correct_emotion = _normalize_emotion(content.emotion or content.correct_answer)
    option_by_emotion: dict[str, GameContent] = {}
    for option in random.sample(options_pool, len(options_pool)):
        emotion = _normalize_emotion(option.emotion or option.correct_answer)
        if emotion and emotion != correct_emotion and emotion not in option_by_emotion:
            option_by_emotion[emotion] = option

    distractors = list(option_by_emotion.values())[:3]
    options = [content] + distractors
    random.shuffle(options)
    payload["options"] = [_question_option(option) for option in options]
    return payload


def _format_questions_for_run(db: Session, game_id: str, level: int, questions: list[GameContent]) -> list[dict]:
    options_pool = _level_content(db, game_id, level) if game_id in CLICK_GAME_IDS else None
    return [_format_game_content(question, options_pool) for question in questions]


def _updated_ratio(current_ratio: list[float], emotion_errors: dict[str, int]) -> list[float]:
    errors = {emotion: count for emotion, count in emotion_errors.items() if emotion in EMOTION_KEYS and count > 0}
    if not errors:
        return current_ratio

    total_errors = sum(errors.values())
    transfer_amount = 0.15
    new_ratio = list(current_ratio)
    error_indexes = {EMOTION_KEYS.index(emotion) for emotion in errors}
    non_error_total = sum(new_ratio[index] for index in range(len(new_ratio)) if index not in error_indexes)
    transfer_amount = min(transfer_amount, non_error_total)

    if non_error_total > 0:
        for index in range(len(new_ratio)):
            if index not in error_indexes:
                new_ratio[index] -= (current_ratio[index] / non_error_total) * transfer_amount

    for emotion, count in errors.items():
        index = EMOTION_KEYS.index(emotion)
        new_ratio[index] += (count / total_errors) * transfer_amount

    total = sum(new_ratio)
    if total <= 0:
        return list(DEFAULT_RATIO)
    normalized = [round(max(item, 0.0) / total, 4) for item in new_ratio]
    normalized[-1] = round(1.0 - sum(normalized[:-1]), 4)
    return normalized


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
    if db.get(Child, user_id) is None:
        db.add(Child(user_id=user_id))
        db.flush()


@router.get("/games", response_model=list[GameOut])
def list_games(game_type: str | None = Query(default=None), db: Session = Depends(get_db)):
    query = db.query(Game).order_by(Game.game_type, Game.name)
    if game_type:
        query = query.filter(Game.game_type == game_type)
    return query.all()


@router.get("/games/progress/{game_id}")
def get_game_progress(game_id: str, user_id: str = Query(...), db: Session = Depends(get_db)):
    _ensure_user(db, user_id)
    progress = _ensure_progress(db, user_id, game_id)
    shared_review = _merge_click_review_emotions(db, user_id) if game_id in CLICK_GAME_IDS else None
    db.commit()
    return _progress_payload(progress, shared_review)


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
        .filter(GameContent.game_id == CV_STORY_GAME_ID)
        .filter(GameContent.level == level)
        .order_by(GameContent.content_id)
        .all()
    )
    return CvScenarioListOut(scenarios=[_cv_scenario(row) for row in rows])


@router.get("/games/cv/requests", response_model=CvScenarioListOut)
def list_cv_requests(db: Session = Depends(get_db)):
    rows = (
        db.query(GameContent)
        .filter(GameContent.game_id == CV_REQUEST_GAME_ID)
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
    game_id = CV_REQUEST_GAME_ID if body.game_type == "request" else CV_STORY_GAME_ID
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
    scores = {"happy": 0.0, "sad": 0.0, "surprise": 0.0, "angry": 0.0, "fear": 0.0, "disgust": 0.0}
    emotion_map = {key: key for key in scores}
    rows = (
        db.query(GameContent.emotion, SessionQuestion.cv_confidence)
        .join(SessionQuestion, SessionQuestion.question_id == GameContent.content_id)
        .join(PlaySession, PlaySession.session_id == SessionQuestion.session_id)
        .filter(PlaySession.user_id == user_id)
        .filter(PlaySession.game_id == CV_REQUEST_GAME_ID)
        .all()
    )
    for emotion, confidence in rows:
        key = emotion_map.get((emotion or "").lower())
        if key:
            scores[key] = max(scores[key], round(float(confidence or 0.0), 2))
    return {"scores": scores}


@router.get("/games/cv/completed-levels")
def get_cv_completed_levels(user_id: str, db: Session = Depends(get_db)):
    max_level = CV_STORY_MAX_LEVEL
    rows = (
        db.query(ChildProgress)
        .filter(ChildProgress.child_id == user_id, ChildProgress.game_id == CV_STORY_GAME_ID)
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

    if game_id in CLICK_GAME_IDS:
        if game.game_type != "click_game":
            raise HTTPException(status_code=400, detail="Game is not a click game")
        if body.level < 1 or body.level > CLICK_MAX_LEVEL:
            raise HTTPException(status_code=400, detail=f"Level must be between 1 and {CLICK_MAX_LEVEL}")

    progress = _ensure_progress(db, body.user_id, game_id)
    if game_id in CLICK_GAME_IDS and body.level > int(progress.level or 1):
        raise HTTPException(
            status_code=403,
            detail={
                "code": "level_locked",
                "message": "Level này chưa được mở khóa.",
                "unlocked_level": int(progress.level or 1),
            },
        )
    ratio = _normalized_ratio(progress.ratio)
    review_emotions = _merge_click_review_emotions(db, body.user_id) if game_id in CLICK_GAME_IDS else _normalized_review(progress.review_emotions)
    questions = _select_questions_for_run(db, game_id, body.level, body.user_id, ratio)
    level_threshold = _click_pass_threshold(game) if game_id in CLICK_GAME_IDS else _threshold_for_level(game, body.level)
    formatted_questions = _format_questions_for_run(db, game_id, body.level, questions)

    session = PlaySession(
        session_id=str(uuid.uuid4()),
        user_id=body.user_id,
        game_id=game_id,
        start_time=datetime.utcnow(),
        state="playing",
        score=0,
        emotion_errors=json.dumps(review_emotions, ensure_ascii=False),
        max_errors=game.max_errors,
        level_threshold=level_threshold,
        ratio=json.dumps(ratio),
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
        "ratio": ratio,
        "review_emotions": review_emotions,
        "questions": formatted_questions,
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
    score = correct * 10
    emotion_errors: dict[str, int] = {}

    for result in body.results:
        if result.is_correct:
            continue
        content = db.get(GameContent, result.question_id)
        emotion = _normalize_emotion((content.emotion or content.correct_answer) if content else None)
        if emotion:
            emotion_errors[emotion] = emotion_errors.get(emotion, 0) + 1

    db.query(SessionQuestion).filter(SessionQuestion.session_id == session.session_id).delete()
    for result in body.results:
        db.add(
            SessionQuestion(
                id=str(uuid.uuid4()),
                session_id=session.session_id,
                question_id=result.question_id,
                is_correct=1 if result.is_correct else 0,
                response_time_ms=int(result.response_time_ms or 0),
                cv_confidence=result.cv_confidence,
                used_hint=1 if result.used_hint else 0,
            )
        )

    session.state = "end"
    session.end_time = datetime.utcnow()
    session.score = score
    session.emotion_errors = json.dumps(emotion_errors, ensure_ascii=False)

    progress = _ensure_progress(db, session.user_id, session.game_id)
    current_ratio = _normalized_ratio(progress.ratio)
    new_ratio = _updated_ratio(current_ratio, emotion_errors)
    review_emotions = _merge_click_review_emotions(db, session.user_id) if session.game_id in CLICK_GAME_IDS else _normalized_review(progress.review_emotions)
    for emotion, count in emotion_errors.items():
        review_emotions[emotion] = review_emotions.get(emotion, 0) + count
    for emotion in body.reset_review_emotions:
        normalized = _normalize_emotion(emotion)
        if normalized in review_emotions:
            review_emotions[normalized] = 0

    max_errors = int(session.max_errors or 3)
    review_emotions_to_learn = [
        emotion for emotion, count in review_emotions.items()
        if emotion in EMOTION_KEYS and count >= max_errors
    ]

    passed = score >= CLICK_PASS_SCORE if session.game_id in CLICK_GAME_IDS else accuracy >= float(session.level_threshold or 70)
    unlocked_level = int(progress.level or 1)
    if session.game_id in CLICK_GAME_IDS and passed and int(session.level or 1) >= unlocked_level:
        unlocked_level = min(int(session.level or 1) + 1, CLICK_MAX_LEVEL)
    elif session.game_id not in CLICK_GAME_IDS and passed and int(session.level or 1) >= unlocked_level:
        game = db.get(Game, session.game_id) if session.game_id else None
        max_level = int(game.level or int(session.level or 1) + 1) if game else int(session.level or 1) + 1
        unlocked_level = min(int(session.level or 1) + 1, max_level)

    progress.accuracy = accuracy
    progress.score = score
    progress.level = unlocked_level
    progress.avg_response_time = (
        sum(int(result.response_time_ms or 0) for result in body.results) / total
        if total > 0 else 0.0
    )
    progress.last_played = datetime.utcnow()
    progress.ratio = json.dumps(new_ratio)
    progress.review_emotions = json.dumps(review_emotions, ensure_ascii=False)
    if session.game_id in CLICK_GAME_IDS:
        _sync_click_review_emotions(db, session.user_id, review_emotions)

    db.commit()

    return {
        "session_id": session.session_id,
        "score": session.score,
        "accuracy": accuracy,
        "emotion_errors": emotion_errors,
        "passed": passed,
        "progress_level": unlocked_level,
        "ratio": new_ratio,
        "review_emotions": review_emotions,
        "review_emotions_to_learn": review_emotions_to_learn,
    }


@router.post("/games/progress/{game_id}/review/reset")
def reset_review_emotions(game_id: str, body: ResetReviewRequest, db: Session = Depends(get_db)):
    _ensure_user(db, body.user_id)
    progress = _ensure_progress(db, body.user_id, game_id)
    review_emotions = _merge_click_review_emotions(db, body.user_id) if game_id in CLICK_GAME_IDS else _normalized_review(progress.review_emotions)
    for emotion in body.emotions:
        normalized = _normalize_emotion(emotion)
        if normalized in review_emotions:
            review_emotions[normalized] = 0
    progress.review_emotions = json.dumps(review_emotions, ensure_ascii=False)
    if game_id in CLICK_GAME_IDS:
        _sync_click_review_emotions(db, body.user_id, review_emotions)
    db.commit()
    return {"review_emotions": review_emotions}
