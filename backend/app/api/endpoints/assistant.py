from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter(prefix="/assistant", tags=["Assistant"])


class ChatRequest(BaseModel):
    game_id: str
    level: int | None = None
    message: str


class ChatResponse(BaseModel):
    reply: str


GAME_RULES = {
    "home": "Đây là trang chính của EmoGarden. Bé có thể học cảm xúc hoặc chọn trò chơi để luyện tập.",
    "select_game": "Ở màn chọn game, hãy chọn trò phù hợp. Nếu mới chơi, nên bắt đầu với Chiếc hộp cảm xúc.",
    "level_select": "Chọn cấp độ từ dễ đến khó. Hoàn thành cấp trước rồi tiếp tục cấp sau.",
    "recognize_emotion": "Nhìn hình hoặc tình huống, sau đó chọn cảm xúc đúng trong các lựa chọn.",
    "game_click_2": "Ghép lông mày, mắt và miệng để tạo khuôn mặt đúng với cảm xúc.",
    "game_click_3": "Đọc tình huống rồi kéo tên hoặc cảm xúc vào đúng vị trí.",
    "game_click_4": "Đọc câu chuyện ngắn và chọn cảm xúc phù hợp nhất.",
    "gameCV": "Bật camera, đọc yêu cầu, rồi thể hiện khuôn mặt đúng với cảm xúc mục tiêu.",
}


def _fallback_reply(request: ChatRequest) -> str:
    text = request.message.lower()
    rules = GAME_RULES.get(request.game_id, "")
    if "chơi gì" in text or "nên chơi" in text or "goi y" in text or "gợi ý" in text:
        return "Nếu mới bắt đầu, con nên chơi Chiếc hộp cảm xúc trước vì game này dễ và giúp nhận biết cảm xúc cơ bản."
    if rules:
        return f"Mình nhắc nhanh nhé: {rules}"
    return "Mình chưa có đủ thông tin về màn này. Con hãy làm theo hướng dẫn trên màn hình hoặc hỏi người lớn giúp nhé."


@router.post("/chat", response_model=ChatResponse)
def chat_with_assistant(request: ChatRequest):
    return ChatResponse(reply=_fallback_reply(request))

