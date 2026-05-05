import os
import json
from urllib.request import Request as UrlRequest, urlopen

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

router = APIRouter(prefix="/tts", tags=["TTS"])


class TTSRequest(BaseModel):
    text: str
    voice: str | None = "banmai"
    speed: float | None = 0


@router.post("")
async def text_to_speech(request: TTSRequest):
    api_key = os.getenv("FPT_API_KEY")
    if not api_key:
        return {
            "audioUrl": None,
            "message": "FPT_API_KEY chưa cấu hình, backend trả fallback không audio.",
            "text": request.text,
        }

    headers = {
        "api_key": api_key,
        "voice": request.voice or "banmai",
        "speed": str(request.speed if request.speed is not None else 0),
        "Cache-Control": "no-cache",
    }
    try:
        url_request = UrlRequest(
            "https://api.fpt.ai/hmi/tts/v5",
            data=request.text.encode("utf-8"),
            headers=headers,
            method="POST",
        )
        with urlopen(url_request, timeout=60) as response:
            data = json.loads(response.read().decode("utf-8"))
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Không gọi được FPT TTS: {exc}") from exc

    return {"audioUrl": data.get("async"), "raw": data}
