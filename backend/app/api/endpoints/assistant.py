from __future__ import annotations

import json
import logging
from typing import Literal

import requests
from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.core.config import settings
from app.db.session import get_db
from app.models.analytics import ChildProgress
from app.models.game import Game, PlaySession, SessionQuestion
from app.models.user import Child, User

router = APIRouter(prefix="/assistant", tags=["Assistant"])
logger = logging.getLogger(__name__)


class ChatHistoryItem(BaseModel):
    role: Literal["user", "assistant"] | str = "user"
    text: str = ""


class ChatRequest(BaseModel):
    game_id: str = Field(default="home")
    level: int | None = None
    screen_context: str | None = None
    message: str
    child_id: str | None = None
    history: list[ChatHistoryItem] = Field(default_factory=list)


class ChatActionResponse(BaseModel):
    type: str
    label: str
    target: str | None = None


class ChatResponse(BaseModel):
    reply: str
    source: str = "fallback"
    suggestions: list[str] = Field(default_factory=list)
    actions: list[ChatActionResponse] = Field(default_factory=list)


class AssistantAiResponse(BaseModel):
    reply: str
    actions: list[ChatActionResponse] = Field(default_factory=list)
    suggestions: list[str] = Field(default_factory=list)


GAME_ALIASES: dict[str, str] = {
    "emotions_box": "recognize_emotion",
    "face_assembly": "game_click_2",
    "emotion_match": "game_click_3",
    "detective_game": "game_click_4",
    "3bcb2108-721c-4a15-a585-31f3084ed000": "recognize_emotion",
    "3bcb2108-721c-4a15-a585-31f084ed0000": "recognize_emotion",
    "33ecafaa-ec7e-40d2-9c67-ed0a29ac0051": "game_click_2",
    "08bbffbf-d147-4556-bccb-b7621cafbf15": "game_click_3",
    "aacaf79e-e15e-42a9-a3d1-a522720d919b": "game_click_4",
    "e05909f3-3dee-42a6-9a75-fd985b1bdf47": "gameCV",
    "61f5e09e-eefa-44c1-86e1-87dfceac3b8e": "game_cv_2",
}


GAME_CATALOG: dict[str, dict[str, str]] = {
    "3bcb2108-721c-4a15-a585-31f084ed0000": {
        "name": "Chiếc hộp cảm xúc",
        "skill": "nhận biết cảm xúc qua ảnh và tình huống",
    },
    "33ecafaa-ec7e-40d2-9c67-ed0a29ac0051": {
        "name": "Xưởng lắp ghép cảm xúc",
        "skill": "ghép mắt, lông mày và miệng thành biểu cảm",
    },
    "aacaf79e-e15e-42a9-a3d1-a522720d919b": {
        "name": "Cảm xúc đúng chỗ",
        "skill": "đặt cảm xúc vào đúng ngữ cảnh",
    },
    "08bbffbf-d147-4556-bccb-b7621cafbf15": {
        "name": "Thám tử cảm xúc",
        "skill": "suy luận cảm xúc từ câu chuyện và manh mối",
    },
    "e05909f3-3dee-42a6-9a75-fd985b1bdf47": {
        "name": "Câu chuyện khuôn mặt",
        "skill": "đọc tình huống rồi thể hiện cảm xúc qua camera",
    },
    "61f5e09e-eefa-44c1-86e1-87dfceac3b8e": {
        "name": "Thử thách cảm xúc",
        "skill": "thể hiện một cảm xúc được yêu cầu qua camera",
    },
}

SAFE_ACTION_TYPES = {
    "OPEN_LEARNING",
    "OPEN_GAME",
    "OPEN_REPORT",
    "OPEN_GARDEN",
    "OPEN_PHOTOBOOTH",
    "OPEN_SETTINGS",
    "OPEN_PARENT_EMAIL_SETTINGS",
    "OPEN_PRIVACY_SETTINGS",
    "OPEN_EMOTION_LESSON",
    "START_EMOTION_CHALLENGE",
}
EMOTION_IDS = {"happy", "sad", "angry", "fear", "surprise", "disgust"}


GAME_RULES: dict[str, str] = {
    "home": (
        "Trang chủ gợi ý bài học hôm nay, mở Báo cáo của bé, xem tiến độ tuần và tối đa hai game gần đây. "
        "Thống kê chi tiết nằm trong Báo cáo, không nằm trên Trang chủ."
    ),
    "learn": (
        "Màn Học giúp bé chọn một cảm xúc, xem video mẫu và đọc dấu hiệu nhận biết. "
        "Bé nên quan sát mắt, miệng, lông mày và tình huống đi kèm."
    ),
    "select_game": (
        "Màn Chơi game có các trò luyện nhận diện và biểu cảm. "
        "Bé có thể bắt đầu từ Chiếc hộp cảm xúc, sau đó thử Thử thách cảm xúc khi đã quen."
    ),
    "level_select": (
        "Màn Chọn cấp độ dùng để chọn mức chơi phù hợp. "
        "Bé nên chơi cấp độ mở sẵn trước, hoàn thành rồi mới thử cấp cao hơn."
    ),
    "recognize_emotion": (
        "Chiếc hộp cảm xúc yêu cầu bé nhìn hình hoặc tình huống rồi chọn cảm xúc đúng. "
        "Hãy đọc kỹ câu hỏi và so sánh nét mặt với 6 cảm xúc cơ bản."
    ),
    "game_click_2": (
        "Xưởng lắp ghép giúp bé ghép lông mày, mắt và miệng để tạo khuôn mặt đúng cảm xúc. "
        "Hãy chú ý từng bộ phận thay đổi thế nào khi vui, buồn, sợ hoặc tức giận."
    ),
    "game_click_3": (
        "Cảm xúc đúng chỗ yêu cầu bé đặt cảm xúc vào đúng ngữ cảnh. "
        "Hãy đọc tình huống trước rồi chọn cảm xúc phù hợp nhất."
    ),
    "game_click_4": (
        "Thám tử cảm xúc yêu cầu bé tìm cảm xúc ẩn trong tình huống. "
        "Hãy suy nghĩ nhân vật đang gặp chuyện gì rồi chọn cảm xúc gần nhất."
    ),
    "gameCV": (
        "Câu chuyện khuôn mặt đưa ra một tình huống để bé tự đoán cảm xúc, sau đó thể hiện biểu cảm qua camera. "
        "Camera chỉ bật khi bé bấm bắt đầu thử thách."
    ),
    "game_cv_2": (
        "Thử thách cảm xúc yêu cầu bé chọn một cảm xúc rồi thể hiện đúng biểu cảm qua camera. "
        "Hãy giữ mặt trong khung hình, đủ sáng và giữ biểu cảm vài giây."
    ),
    "profile": (
        "Hồ sơ hiển thị thông tin cá nhân, huy hiệu và thống kê chơi game của bé. "
        "Phụ huynh có thể chỉnh tên, tuổi và ngày sinh ở đây."
    ),
    "settings": (
        "Cài đặt dùng để bật tắt bong bóng trợ lý, chỉnh tài khoản, số điện thoại và đổi mật khẩu. "
        "Không chia sẻ mật khẩu cho người khác."
    ),
    "report": (
        "Báo cáo tiến bộ giúp phụ huynh xem bé luyện cảm xúc nào tốt và cảm xúc nào cần ôn thêm. "
        "Nếu chưa có dữ liệu, hãy chơi vài lượt trước."
    ),
}


EMOTION_TIPS: dict[str, str] = {
    "vui": "Vui vẻ: hãy mỉm cười, mắt hơi híp lại và giữ khuôn mặt thoải mái.",
    "vui vẻ": "Vui vẻ: hãy mỉm cười, mắt hơi híp lại và giữ khuôn mặt thoải mái.",
    "buồn": "Buồn bã: hạ khóe miệng, ánh mắt nhìn xuống và giữ nét mặt nhẹ nhàng.",
    "buồn bã": "Buồn bã: hạ khóe miệng, ánh mắt nhìn xuống và giữ nét mặt nhẹ nhàng.",
    "tức giận": "Tức giận: nhíu mày, mím môi và nhìn nghiêm trong vài giây.",
    "giận": "Tức giận: nhíu mày, mím môi và nhìn nghiêm trong vài giây.",
    "sợ": "Sợ hãi: mở to mắt, hơi lùi mặt lại và giữ biểu cảm bất ngờ lo lắng.",
    "sợ hãi": "Sợ hãi: mở to mắt, hơi lùi mặt lại và giữ biểu cảm bất ngờ lo lắng.",
    "ngạc nhiên": "Ngạc nhiên: mở to mắt, há miệng nhẹ và nâng lông mày.",
    "ghê tởm": "Ghê tởm: nhăn mũi, hơi cau mày và làm vẻ không thích.",
    "ghe tom": "Ghê tởm: nhăn mũi, hơi cau mày và làm vẻ không thích.",
}


SUGGESTIONS_BY_CONTEXT: dict[str, list[str]] = {
    "home": ["Con nên chơi gì?", "Hôm nay nên luyện cảm xúc nào?", "Báo cáo tiến bộ là gì?"],
    "learn": ["Cách nhận biết vui vẻ", "Cách nhận biết buồn bã", "Con nên học cảm xúc nào?"],
    "select_game": ["Nên chơi game nào trước?", "Game nào dễ nhất?", "Game camera chơi thế nào?"],
    "level_select": ["Nên chọn cấp độ nào?", "Cấp độ bị khóa là gì?", "Làm sao mở cấp tiếp theo?"],
    "gameCV": ["Game này chơi thế nào?", "Camera không bật thì sao?", "Làm sao đoán tình huống?"],
    "game_cv_2": ["Cách giữ mặt trong camera", "Gợi ý biểu cảm vui vẻ", "Camera không bật thì sao?"],
    "profile": ["Sửa hồ sơ ở đâu?", "Xem thành tích thế nào?", "Đổi ảnh đại diện ra sao?"],
    "settings": ["Đổi mật khẩu ở đâu?", "Tắt trợ lý nổi thế nào?", "Gửi báo cáo qua mail ra sao?"],
    "report": ["Tạo báo cáo thế nào?", "Gửi báo cáo cho phụ huynh", "Đọc chỉ số báo cáo"],
}


def _normalize_game_id(game_id: str | None) -> str:
    raw = (game_id or "home").strip()
    return GAME_ALIASES.get(raw, raw)


def _context_from_screen(screen_context: str | None) -> str | None:
    if not screen_context:
        return None
    route = screen_context.strip().lower()
    if route.startswith("game/"):
        if "61f5e09e-eefa-44c1-86e1-87dfceac3b8e" in route:
            return "game_cv_2"
        if "e05909f3-3dee-42a6-9a75-fd985b1bdf47" in route:
            return "gameCV"
        if "3bcb2108-721c-4a15-a585-31f3084ed000" in route:
            return "recognize_emotion"
        if "33ecafaa-ec7e-40d2-9c67-ed0a29ac0051" in route:
            return "game_click_2"
        if "08bbffbf-d147-4556-bccb-b7621cafbf15" in route:
            return "game_click_3"
        if "aacaf79e-e15e-42a9-a3d1-a522720d919b" in route:
            return "game_click_4"
    if route.startswith("level_select"):
        return "level_select"
    if route.startswith("select_game"):
        return "select_game"
    if route.startswith("learn"):
        return "learn"
    if route.startswith("home"):
        return "home"
    if route.startswith("profile"):
        return "profile"
    if route.startswith("settings"):
        return "settings"
    if route.startswith("report"):
        return "report"
    return None


def _resolve_context(req: ChatRequest) -> str:
    context_from_route = _context_from_screen(req.screen_context)
    if context_from_route:
        return context_from_route
    return _normalize_game_id(req.game_id)


def _contains_any(text: str, keywords: list[str]) -> bool:
    return any(keyword in text for keyword in keywords)


def _emotion_tip(text: str) -> str | None:
    for keyword, tip in EMOTION_TIPS.items():
        if keyword in text:
            return tip
    return None


def _fallback_reply(req: ChatRequest) -> str:
    game_id = _resolve_context(req)
    text = (req.message or "").strip().lower()
    rules = GAME_RULES.get(game_id)
    level_text = f" Cấp độ hiện tại là {req.level}." if req.level else ""

    if not text:
        return "Con hãy nhập câu hỏi ngắn, ví dụ: “Game này chơi thế nào?” hoặc “Con nên luyện cảm xúc nào?”."

    if _contains_any(text, ["camera", "máy ảnh", "không bật", "khong bat", "quyền", "quyen"]):
        return (
            "Nếu camera chưa bật, con hãy bấm Bắt đầu thử thách rồi cho phép quyền camera. "
            "Hãy để mặt ở giữa khung hình, phòng đủ sáng và không che mặt nhé."
        )

    if _contains_any(text, ["gợi ý", "goi y", "biểu cảm", "bieu cam", "làm mặt", "lam mat"]):
        tip = _emotion_tip(text)
        if tip:
            return f"Gợi ý cho con nhé: {tip}"
        return (
            "Khi thể hiện cảm xúc, con hãy nhìn vào mắt, miệng và lông mày. "
            "Giữ khuôn mặt trong khung hình vài giây để hệ thống nhận diện rõ hơn."
        )

    if _contains_any(text, ["nên chơi", "nen choi", "chơi gì", "choi gi", "game nào", "game nao"]):
        return (
            "Nếu mới chơi, con nên bắt đầu với Chiếc hộp cảm xúc để làm quen 6 cảm xúc. "
            "Khi đã quen rồi, con thử Thử thách cảm xúc để luyện biểu cảm qua camera nhé."
        )

    if _contains_any(text, ["luật", "luat", "cách chơi", "cach choi", "chơi thế nào", "choi the nao", "hướng dẫn", "huong dan"]):
        if rules:
            return f"Mình nhắc nhanh nhé: {rules}{level_text}"
        return "Con hãy đọc hướng dẫn trên màn hình, chọn đáp án hoặc bấm bắt đầu theo từng bước của game nhé."

    if _contains_any(text, ["level", "cấp độ", "cap do", "khóa", "khoa", "mở", "mo"]):
        return (
            "Cấp độ giúp game khó dần lên. "
            "Con hãy hoàn thành cấp đang mở trước; khi đủ tiến bộ, cấp tiếp theo sẽ được mở."
        )

    if _contains_any(text, ["buồn", "sợ", "giận", "tức", "vui", "ngạc nhiên", "ghê tởm", "ghe tom"]):
        tip = _emotion_tip(text)
        if tip:
            return f"{tip} Nếu chưa chắc, con có thể vào màn Học để xem video mẫu trước."

    if rules:
        return f"Mình đang ở đúng ngữ cảnh của màn này. {rules}{level_text}"

    return (
        "Mình có thể giúp con hiểu luật chơi, chọn game phù hợp hoặc gợi ý cách thể hiện cảm xúc. "
        "Con thử hỏi ngắn hơn nhé."
    )


def _display_game_name(game_id: str | None, database_names: dict[str, str]) -> str:
    if not game_id:
        return "Trò chơi"
    return database_names.get(game_id) or GAME_CATALOG.get(game_id, {}).get("name") or "Trò chơi"


def _emotion_name(raw: str | None) -> str | None:
    value = (raw or "").strip().lower()
    aliases = {
        "happy": "Vui vẻ",
        "vui": "Vui vẻ",
        "vui vẻ": "Vui vẻ",
        "sad": "Buồn bã",
        "buồn": "Buồn bã",
        "buồn bã": "Buồn bã",
        "angry": "Tức giận",
        "giận": "Tức giận",
        "tức giận": "Tức giận",
        "fear": "Sợ hãi",
        "sợ": "Sợ hãi",
        "sợ hãi": "Sợ hãi",
        "surprise": "Ngạc nhiên",
        "ngạc nhiên": "Ngạc nhiên",
        "disgust": "Ghê tởm",
        "ghê tởm": "Ghê tởm",
    }
    return aliases.get(value)


def _load_child_context(db: Session, child_id: str | None) -> dict:
    if not child_id or child_id == "local-player":
        return {"available": False, "reason": "Không có tài khoản backend để đọc tiến độ."}

    try:
        user = db.get(User, child_id)
        child = db.get(Child, child_id)
        games = db.query(Game).all()
        game_names = {row.game_id: row.name for row in games}
        progress_rows = (
            db.query(ChildProgress)
            .filter(ChildProgress.child_id == child_id)
            .order_by(ChildProgress.last_played.desc())
            .all()
        )
        sessions = (
            db.query(PlaySession)
            .filter(PlaySession.user_id == child_id)
            .filter(PlaySession.end_time.isnot(None))
            .order_by(PlaySession.end_time.desc())
            .limit(30)
            .all()
        )

        completed_scores = [int(row.score or 0) for row in sessions]
        progress = [
            {
                "game": _display_game_name(row.game_id, game_names),
                "game_id": row.game_id,
                "unlocked_level": int(row.level or 1),
                "best_or_latest_score": int(row.score or 0),
                "accuracy_percent": round(float(row.accuracy or 0), 1),
            }
            for row in progress_rows
        ]
        recent_games = [
            {
                "game": _display_game_name(row.game_id, game_names),
                "game_id": row.game_id,
                "level": int(row.level or 1),
                "score": int(row.score or 0),
            }
            for row in sessions[:5]
        ]

        emotion_stats: dict[str, dict[str, int]] = {}
        session_ids = [row.session_id for row in sessions]
        if session_ids:
            question_rows = (
                db.query(SessionQuestion)
                .filter(SessionQuestion.session_id.in_(session_ids))
                .filter(SessionQuestion.correct_answer.isnot(None))
                .limit(300)
                .all()
            )
            for row in question_rows:
                emotion = _emotion_name(row.correct_answer)
                if not emotion:
                    continue
                stats = emotion_stats.setdefault(emotion, {"attempts": 0, "correct": 0})
                stats["attempts"] += 1
                stats["correct"] += 1 if row.is_correct else 0

        emotion_accuracy = []
        for emotion, values in emotion_stats.items():
            attempts = values["attempts"]
            emotion_accuracy.append(
                {
                    "emotion": emotion,
                    "attempts": attempts,
                    "accuracy_percent": round(values["correct"] * 100 / attempts) if attempts else None,
                }
            )
        emotion_accuracy.sort(key=lambda item: (item["accuracy_percent"], -item["attempts"]))

        return {
            "available": True,
            "child_name": user.name if user else None,
            "age": child.age if child else None,
            "completed_sessions": len(sessions),
            "average_score": round(sum(completed_scores) / len(completed_scores)) if completed_scores else None,
            "game_progress": progress,
            "recent_games": recent_games,
            "weak_emotions": emotion_accuracy[:3],
            "parent_email_configured": bool(child and child.report_preferences),
        }
    except Exception as exc:
        logger.warning("Could not load assistant context for child %s: %s", child_id, exc)
        return {"available": False, "reason": "Không đọc được tiến độ ở lần gọi này."}


def _build_prompt(req: ChatRequest, child_context: dict) -> str:
    game_id = _resolve_context(req)
    rules = GAME_RULES.get(game_id, "Không có mô tả riêng cho màn này.")
    history_lines = []
    for item in req.history[-8:]:
        role = "Người chơi" if item.role == "user" else "Trợ lý"
        if item.text.strip():
            history_lines.append(f"{role}: {item.text.strip()}")
    history = "\n".join(history_lines) or "Chưa có lịch sử hội thoại."

    catalog_text = json.dumps(GAME_CATALOG, ensure_ascii=False)
    child_context_text = json.dumps(child_context, ensure_ascii=False, default=str)

    return f"""
Bạn là Mầm Mầm, trợ lý thông minh trong EmoGarden, một app giúp trẻ 6-10 tuổi học nhận diện và thể hiện cảm xúc.

Yêu cầu trả lời:
- Hiểu tiếng Việt đời thường, lỗi chính tả, viết tắt và cách nói ngắn của trẻ.
- Trả lời thẳng câu hỏi trước, bằng 2-4 câu ngắn, ấm áp nhưng không nói kiểu máy móc hoặc quá trẻ con.
- Dùng lịch sử hội thoại để hiểu câu hỏi nối tiếp như "còn game khác?", "tại sao?", "chơi thế nào?".
- Khi gợi ý game, phải nêu đúng một game ưu tiên và lý do dựa trên tiến độ/cảm xúc yếu. Nếu chưa có dữ liệu, ưu tiên Chiếc hộp cảm xúc.
- Chỉ dùng số liệu có trong dữ liệu bé; không tự bịa điểm, cấp độ, email hay tiến độ.
- Nếu người dùng hỏi về màn hiện tại, bám đúng ngữ cảnh màn và luật bên dưới.
- Không nói rằng đã gửi email, lưu ảnh, đổi cài đặt hoặc mở khóa cấp độ nếu hệ thống chưa thực hiện hành động đó.
- Không dùng Markdown. Không nhắc tới backend, API, model, prompt hay lỗi kỹ thuật.
- Nếu câu hỏi ngoài EmoGarden, chuyển nhẹ về học cảm xúc, trò chơi hoặc an toàn cảm xúc.
- Nếu trẻ nói đang bị đau, bị đe dọa, muốn làm hại bản thân hoặc người khác, khuyên báo ngay cho người lớn tin cậy và tìm trợ giúp khẩn cấp; không biến thành trò chơi.

Các action hợp lệ:
- OPEN_LEARNING
- OPEN_GAME, target phải là game_id trong danh mục nếu muốn mở thẳng game
- OPEN_REPORT, OPEN_GARDEN, OPEN_PHOTOBOOTH, OPEN_SETTINGS
- OPEN_PARENT_EMAIL_SETTINGS, OPEN_PRIVACY_SETTINGS
- OPEN_EMOTION_LESSON hoặc START_EMOTION_CHALLENGE, target là happy/sad/angry/fear/surprise/disgust
Chỉ đề xuất tối đa 2 action thật sự liên quan. Không tạo action khác.

Ngữ cảnh:
- game_id: {game_id}
- screen_context: {req.screen_context}
- level: {req.level}
- luật/mô tả: {rules}
- danh mục game chính xác: {catalog_text}
- dữ liệu tiến độ của bé: {child_context_text}

Lịch sử gần đây:
{history}

Người chơi hỏi: {req.message}
""".strip()


def _sanitize_actions(actions: list[ChatActionResponse]) -> list[ChatActionResponse]:
    result: list[ChatActionResponse] = []
    for action in actions[:2]:
        action_type = action.type.strip().upper()
        if action_type not in SAFE_ACTION_TYPES:
            continue

        target = action.target.strip() if action.target else None
        if action_type == "OPEN_GAME" and target and target not in GAME_CATALOG:
            target = None
        if action_type in {"OPEN_EMOTION_LESSON", "START_EMOTION_CHALLENGE"}:
            if target not in EMOTION_IDS:
                continue
        if action_type not in {"OPEN_GAME", "OPEN_EMOTION_LESSON", "START_EMOTION_CHALLENGE"}:
            target = None

        label = action.label.strip()[:60]
        if not label:
            continue
        result.append(ChatActionResponse(type=action_type, label=label, target=target))
    return result


def _sanitize_suggestions(values: list[str], fallback: list[str]) -> list[str]:
    result: list[str] = []
    for value in values:
        text = value.strip()
        if text and text not in result:
            result.append(text[:90])
        if len(result) == 3:
            break
    return result or fallback[:3]


def _extract_json_object(raw_text: str) -> dict | None:
    text = raw_text.strip()
    if not text:
        return None
    if text.startswith("```"):
        lines = text.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip().startswith("```"):
            lines = lines[:-1]
        text = "\n".join(lines).strip()
    try:
        parsed = json.loads(text)
        return parsed if isinstance(parsed, dict) else None
    except json.JSONDecodeError:
        start = text.find("{")
        end = text.rfind("}")
        if start == -1 or end <= start:
            return None
        try:
            parsed = json.loads(text[start : end + 1])
            return parsed if isinstance(parsed, dict) else None
        except json.JSONDecodeError:
            return None


def _parse_beeknoee_response(payload: dict) -> AssistantAiResponse | None:
    content = ""
    choices = payload.get("choices")
    if isinstance(choices, list) and choices:
        message = choices[0].get("message") if isinstance(choices[0], dict) else None
        if isinstance(message, dict):
            raw_content = message.get("content")
            if isinstance(raw_content, list):
                content = "\n".join(
                    part.get("text", "") for part in raw_content if isinstance(part, dict)
                )
            else:
                content = str(raw_content or "")
        elif "text" in choices[0]:
            content = str(choices[0].get("text") or "")
    if not content:
        content = str(payload.get("output_text") or payload.get("text") or "")
    data = _extract_json_object(content)
    if data:
        return AssistantAiResponse.model_validate(data)
    content = content.strip()
    return AssistantAiResponse(reply=content) if content else None


def _ask_beeknoee(req: ChatRequest, child_context: dict) -> AssistantAiResponse | None:
    api_key = settings.BEEKNOEE_API_KEY
    if not api_key:
        logger.warning("BEEKNOEE_API_KEY is not configured; assistant is using fallback replies")
        return None

    try:
        base_url = settings.BEEKNOEE_BASE_URL.rstrip("/")
        endpoint = f"{base_url}/chat/completions"
        prompt = _build_prompt(req, child_context)
        schema_hint = (
            "Trả về đúng một JSON object, không markdown, không giải thích ngoài JSON. "
            "Schema: {\"reply\": string, \"actions\": [{\"type\": string, \"label\": string, \"target\": string|null}], "
            "\"suggestions\": [string, string, string]}."
        )
        request_body = {
            "model": settings.MODEL,
            "messages": [
                {"role": "system", "content": schema_hint},
                {"role": "user", "content": prompt},
            ],
            "temperature": 0.35,
            "max_tokens": 700,
            "response_format": {"type": "json_object"},
        }
        response = requests.post(
            endpoint,
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
            },
            json=request_body,
            timeout=20,
        )
        if response.status_code >= 400 and "response_format" in request_body:
            request_body.pop("response_format", None)
            response = requests.post(
                endpoint,
                headers={
                    "Authorization": f"Bearer {api_key}",
                    "Content-Type": "application/json",
                },
                json=request_body,
                timeout=20,
            )
        response.raise_for_status()
        return _parse_beeknoee_response(response.json())
    except Exception as exc:
        logger.warning("Beeknoee assistant unavailable: %s", exc)
        return None


@router.post("/chat", response_model=ChatResponse)
def chat_with_assistant(req: ChatRequest, db: Session = Depends(get_db)) -> ChatResponse:
    game_id = _resolve_context(req)
    default_suggestions = SUGGESTIONS_BY_CONTEXT.get(game_id, SUGGESTIONS_BY_CONTEXT["home"])
    child_context = _load_child_context(db, req.child_id)
    ai_result = _ask_beeknoee(req, child_context)
    if ai_result and ai_result.reply.strip():
        return ChatResponse(
            reply=ai_result.reply.strip(),
            source="beeknoee",
            suggestions=_sanitize_suggestions(ai_result.suggestions, default_suggestions),
            actions=_sanitize_actions(ai_result.actions),
        )

    return ChatResponse(
        reply=_fallback_reply(req),
        source="fallback",
        suggestions=default_suggestions,
    )
