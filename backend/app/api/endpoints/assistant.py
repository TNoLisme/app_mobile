from __future__ import annotations

import os
from typing import Literal

from fastapi import APIRouter
from pydantic import BaseModel, Field

router = APIRouter(prefix="/assistant", tags=["Assistant"])


class ChatHistoryItem(BaseModel):
    role: Literal["user", "assistant"] | str = "user"
    text: str = ""


class ChatRequest(BaseModel):
    game_id: str = Field(default="home")
    level: int | None = None
    message: str
    child_id: str | None = None
    history: list[ChatHistoryItem] = Field(default_factory=list)


class ChatResponse(BaseModel):
    reply: str
    source: str = "fallback"
    suggestions: list[str] = Field(default_factory=list)


GAME_ALIASES: dict[str, str] = {
    "3bcb2108-721c-4a15-a585-31f3084ed000": "recognize_emotion",
    "33ecafaa-ec7e-40d2-9c67-ed0a29ac0051": "game_click_2",
    "08bbffbf-d147-4556-bccb-b7621cafbf15": "game_click_3",
    "aacaf79e-e15e-42a9-a3d1-a522720d919b": "game_click_4",
    "e05909f3-3dee-42a6-9a75-fd985b1bdf47": "gameCV",
    "61f5e09e-eefa-44c1-86e1-87dfceac3b8e": "game_cv_2",
}


GAME_RULES: dict[str, str] = {
    "home": (
        "Trang chủ giúp bé xem tỉ lệ đúng của các cảm xúc, trò chơi gần đây và báo cáo tiến bộ. "
        "Nếu mới bắt đầu, bé nên vào Học để xem cảm xúc trước rồi sang Chơi game."
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
}


def _normalize_game_id(game_id: str | None) -> str:
    raw = (game_id or "home").strip()
    return GAME_ALIASES.get(raw, raw)


def _contains_any(text: str, keywords: list[str]) -> bool:
    return any(keyword in text for keyword in keywords)


def _emotion_tip(text: str) -> str | None:
    for keyword, tip in EMOTION_TIPS.items():
        if keyword in text:
            return tip
    return None


def _fallback_reply(req: ChatRequest) -> str:
    game_id = _normalize_game_id(req.game_id)
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


def _build_prompt(req: ChatRequest) -> str:
    game_id = _normalize_game_id(req.game_id)
    rules = GAME_RULES.get(game_id, "Không có mô tả riêng cho màn này.")
    history_lines = []
    for item in req.history[-8:]:
        role = "Người chơi" if item.role == "user" else "Trợ lý"
        if item.text.strip():
            history_lines.append(f"{role}: {item.text.strip()}")
    history = "\n".join(history_lines) or "Chưa có lịch sử hội thoại."

    return f"""
Bạn là trợ lý trong app EmoGarden, app học cảm xúc cho trẻ em.
Trả lời bằng tiếng Việt, thân thiện với trẻ 6-10 tuổi, tối đa 3 câu ngắn.
Không dùng Markdown, không trả lời chủ đề ngoài game/cảm xúc/an toàn sử dụng app.

Ngữ cảnh:
- game_id: {game_id}
- level: {req.level}
- luật/mô tả: {rules}

Lịch sử gần đây:
{history}

Người chơi hỏi: {req.message}
""".strip()


def _ask_gemini(req: ChatRequest) -> str | None:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        return None

    try:
        import google.generativeai as genai  # type: ignore
    except Exception:
        return None

    try:
        genai.configure(api_key=api_key)
        model_name = os.getenv("GEMINI_MODEL", "gemini-1.5-flash")
        model = genai.GenerativeModel(model_name)
        result = model.generate_content(_build_prompt(req))
        text = getattr(result, "text", "") or ""
        text = str(text).strip()
        return text or None
    except Exception as exc:
        print(f"[assistant] Gemini unavailable: {exc}")
        return None


@router.post("/chat", response_model=ChatResponse)
def chat_with_assistant(req: ChatRequest) -> ChatResponse:
    game_id = _normalize_game_id(req.game_id)
    answer = _ask_gemini(req)
    source = "gemini" if answer else "fallback"
    if not answer:
        answer = _fallback_reply(req)

    return ChatResponse(
        reply=answer,
        source=source,
        suggestions=SUGGESTIONS_BY_CONTEXT.get(game_id, SUGGESTIONS_BY_CONTEXT["home"]),
    )
