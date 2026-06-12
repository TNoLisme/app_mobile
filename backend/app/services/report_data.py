from __future__ import annotations

import json
import unicodedata
from dataclasses import dataclass
from datetime import datetime
from typing import Any


SCORE_MAX = 100
EMOTION_NAMES = ["Vui vẻ", "Buồn bã", "Tức giận", "Sợ hãi", "Ngạc nhiên", "Ghê tởm"]
EMOTION_ALIASES = {
    "Vui vẻ": ("vui ve", "happy", "joy", "smile"),
    "Buồn bã": ("buon ba", "sad", "sadness"),
    "Tức giận": ("tuc gian", "angry", "anger"),
    "Sợ hãi": ("so hai", "fear", "fearful"),
    "Ngạc nhiên": ("ngac nhien", "surprised", "surprise"),
    "Ghê tởm": ("ghe tom", "disgusted", "disgust"),
}


@dataclass(frozen=True)
class EmotionStat:
    name: str
    correct: int
    incorrect: int
    attempts: int
    accuracy: int | None


@dataclass(frozen=True)
class GameStat:
    game_id: str
    game_name: str
    sessions: int
    average_score: int | None
    best_score: int | None
    current_level: int | None
    progress_percent: int | None


@dataclass(frozen=True)
class DailySession:
    label: str
    sessions: int


@dataclass(frozen=True)
class ReportData:
    child_name: str
    parent_email: str | None
    period_type: str
    start_date: str
    end_date: str
    created_at: datetime
    sessions_count: int
    average_score: int | None
    learned_emotion_count: int
    total_emotion_count: int
    total_minutes: int | None
    weak_emotions: list[EmotionStat]
    best_emotions: list[EmotionStat]
    most_practiced_emotion: EmotionStat | None
    emotion_stats: list[EmotionStat]
    game_stats: list[GameStat]
    daily_sessions: list[DailySession]
    achievements: list[str]
    parent_recommendations: list[str]
    progress_comment: str
    summary_text: str

    @property
    def average_score_text(self) -> str:
        return f"{self.average_score}/{SCORE_MAX}" if self.average_score is not None else "Chưa có"

    @property
    def period_display(self) -> str:
        if self.period_type == "monthly":
            month = self.end_date[3:] if len(self.end_date) >= 10 else self.end_date
            return f"Tháng: {month}"
        if self.period_type == "daily":
            return f"Ngày: {self.end_date or self.start_date}"
        return f"Tuần: {self.start_date} - {self.end_date}"


def strip_accents(text: str) -> str:
    normalized = unicodedata.normalize("NFKD", text)
    return "".join(ch for ch in normalized if not unicodedata.combining(ch)).lower()


def safe_float(value: Any, default: float = 0.0) -> float:
    try:
        if value is None:
            return default
        return float(value)
    except (TypeError, ValueError):
        return default


def safe_int(value: Any, default: int = 0) -> int:
    try:
        if value is None:
            return default
        return int(float(value))
    except (TypeError, ValueError):
        return default


def score_to_int(value: Any) -> int | None:
    if value is None:
        return None
    return max(0, min(SCORE_MAX, round(safe_float(value))))


def percent_to_int(value: Any) -> int | None:
    if value is None:
        return None
    return max(0, min(100, round(safe_float(value))))


def progress_comment(average_score: int | None) -> str:
    if average_score is None:
        return "Bé cần chơi thêm vài màn để EmoGarden đánh giá chính xác hơn."
    if average_score >= 80:
        return "Bé tiến bộ rất tốt và đang nhận diện cảm xúc khá chính xác."
    if average_score >= 60:
        return "Bé đang tiến bộ ổn. Phụ huynh có thể cho bé luyện thêm một số tình huống khó hơn."
    if average_score >= 40:
        return "Bé cần luyện thêm một số cảm xúc. Phụ huynh có thể cùng bé ôn lại các tình huống thường gặp."
    return "Bé nên ôn lại các cảm xúc cơ bản. Phụ huynh có thể luyện cùng bé mỗi ngày trong thời gian ngắn."


def build_summary_text(sessions_count: int, average_score: int | None, period_type: str = "weekly") -> str:
    period_text = "trong tuần này" if period_type == "weekly" else "trong kỳ này"
    if sessions_count <= 0:
        return f"Bé chưa có lượt luyện nào {period_text}. Hãy bắt đầu với một trò chơi cảm xúc nhé."
    if average_score is None:
        return f"Bé đã luyện {sessions_count} lượt {period_text}. Bé cần chơi thêm vài màn để EmoGarden đánh giá chính xác hơn."
    return f"Bé đã luyện {sessions_count} lượt {period_text}. Điểm trung bình là {average_score}/{SCORE_MAX}. {progress_comment(average_score)}"


def _parse_data(raw: str | None) -> dict[str, Any]:
    if not raw:
        return {}
    try:
        parsed = json.loads(raw)
    except Exception:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def _emotion_stats(raw_stats: dict[str, Any]) -> list[EmotionStat]:
    normalized_lookup = {
        strip_accents(str(key)): value
        for key, value in raw_stats.items()
        if isinstance(value, dict)
    }
    result: list[EmotionStat] = []
    for name in EMOTION_NAMES:
        aliases = (strip_accents(name), *EMOTION_ALIASES.get(name, ()))
        raw = next((normalized_lookup[alias] for alias in aliases if alias in normalized_lookup), {})
        correct = safe_int(raw.get("correct"), 0)
        incorrect = safe_int(raw.get("incorrect"), 0)
        attempts = safe_int(raw.get("attempts"), correct + incorrect)
        accuracy = raw.get("accuracy")
        if attempts <= 0:
            accuracy_value = None
        elif accuracy is None:
            accuracy_value = max(0, min(100, round(correct * 100.0 / attempts)))
        else:
            accuracy_value = max(0, min(100, round(safe_float(accuracy))))
        result.append(EmotionStat(name, correct, incorrect, attempts, accuracy_value))
    return result


def _game_stats(raw_games: list[Any]) -> list[GameStat]:
    selected: dict[str, GameStat] = {}
    for raw in raw_games:
        if not isinstance(raw, dict):
            continue
        sessions = safe_int(raw.get("sessions"), 0)
        if sessions <= 0:
            continue
        game_name = str(raw.get("game_name") or "Trò chơi")
        game_id = str(raw.get("game_id") or strip_accents(game_name))
        item = GameStat(
            game_id=game_id,
            game_name=game_name,
            sessions=sessions,
            average_score=score_to_int(raw.get("avg_score")),
            best_score=score_to_int(raw.get("best_score")),
            current_level=safe_int(raw.get("level"), 0) or None,
            progress_percent=percent_to_int(raw.get("progress_percent", raw.get("progressPercent", raw.get("avg_score")))),
        )
        current = selected.get(game_id)
        if current is None or item.sessions > current.sessions:
            selected[game_id] = item
    return sorted(selected.values(), key=lambda item: (item.sessions, item.average_score or 0), reverse=True)


def _daily_sessions(raw_daily: dict[str, Any]) -> list[DailySession]:
    return [DailySession(label=str(label).replace("\n", " "), sessions=safe_int(value, 0)) for label, value in raw_daily.items()]


def _achievements(report: "ReportData") -> list[str]:
    achievements: list[str] = []
    if report.sessions_count >= 5:
        achievements.append(f"Duy trì luyện tập tích cực với {report.sessions_count} lượt chơi trong tuần.")
    if report.learned_emotion_count > 0:
        achievements.append(f"Đã luyện {report.learned_emotion_count}/{report.total_emotion_count} cảm xúc cơ bản.")
    if report.average_score is not None and report.average_score >= 80:
        achievements.append("Đạt điểm trung bình cao.")
    for emotion in report.emotion_stats:
        if emotion.attempts >= 3 and emotion.accuracy is not None and emotion.accuracy >= 80:
            achievements.append(f"Làm tốt cảm xúc {emotion.name} với {emotion.correct}/{emotion.attempts} lượt chính xác.")
    return achievements


def _parent_recommendations(
    average_score: int | None,
    learned_emotion_count: int,
    weak_emotions: list[EmotionStat],
) -> list[str]:
    weak_names = ", ".join(item.name for item in weak_emotions[:2])
    if average_score is None:
        recommendations = ["Bé cần chơi thêm vài màn để EmoGarden đưa ra gợi ý chính xác hơn."]
    elif average_score >= 80:
        recommendations = ["Bé đang làm tốt. Phụ huynh có thể cho bé luyện thêm các tình huống khó hơn để củng cố khả năng nhận biết cảm xúc."]
    elif average_score >= 60:
        recommendations = ["Bé đang tiến bộ ổn. Phụ huynh có thể cho bé luyện thêm một số tình huống khó hơn."]
    elif average_score >= 40:
        recommendations = ["Bé cần luyện thêm một số cảm xúc. Phụ huynh có thể cùng bé đọc tình huống và hỏi: 'Con nghĩ bạn nhỏ đang cảm thấy thế nào?'"]
    else:
        recommendations = ["Bé nên ôn lại các cảm xúc cơ bản. Phụ huynh có thể luyện cùng bé mỗi ngày trong thời gian ngắn."]

    if weak_names:
        recommendations.append(f"Nên ưu tiên luyện thêm các cảm xúc: {weak_names}.")
    if any(item.name == "Sợ hãi" for item in weak_emotions):
        recommendations.append("Với cảm xúc Sợ hãi, phụ huynh có thể cùng bé luyện các tình huống như nghe tiếng sấm, ở nơi tối hoặc bị lạc.")
    if any(item.name == "Tức giận" for item in weak_emotions):
        recommendations.append("Với cảm xúc Tức giận, phụ huynh có thể luyện cùng bé cách nói: 'Con không thích điều đó' thay vì la hét hoặc đánh bạn.")
    if learned_emotion_count < len(EMOTION_NAMES):
        recommendations.append(f"Bé đã luyện {learned_emotion_count}/{len(EMOTION_NAMES)} cảm xúc. Nên cho bé thử thêm các cảm xúc còn lại để báo cáo đầy đủ hơn.")
    recommendations.append("Khi luyện cùng bé, phụ huynh nên dùng câu hỏi mở và tránh chê bé sai; hãy gợi ý nhẹ để bé thử lại biểu cảm.")
    return recommendations


def build_report_data(payload: dict[str, Any], parent_email: str | None = None) -> ReportData:
    data = _parse_data(payload.get("data"))
    stats = payload.get("stats") if isinstance(payload.get("stats"), dict) else {}
    created_at_raw = payload.get("generated_at")
    try:
        created_at = datetime.fromisoformat(str(created_at_raw).replace("Z", "+00:00")) if created_at_raw else datetime.utcnow()
    except Exception:
        created_at = datetime.utcnow()

    period_type = str(payload.get("report_type") or data.get("period") or "weekly")
    sessions_count = safe_int(data.get("total_sessions", stats.get("total_sessions")), 0)
    average_score = score_to_int(data.get("avg_score", stats.get("avg_score")))
    total_minutes_raw = data.get("total_playtime_minutes", stats.get("total_playtime_minutes"))
    total_minutes = safe_int(total_minutes_raw, 0) if total_minutes_raw is not None and safe_int(total_minutes_raw, 0) > 0 else None

    emotions = _emotion_stats(data.get("emotion_stats") if isinstance(data.get("emotion_stats"), dict) else {})
    learned_emotion_count = sum(1 for item in emotions if item.attempts > 0)
    practiced = [item for item in emotions if item.attempts > 0]
    weak = sorted(
        [item for item in practiced if item.accuracy is not None and item.accuracy < 60],
        key=lambda item: (item.accuracy or 0, -item.attempts),
    )
    best = sorted(
        [item for item in practiced if item.attempts >= 3 and item.accuracy is not None and item.accuracy >= 80],
        key=lambda item: (item.accuracy or 0, item.attempts),
        reverse=True,
    )
    most_practiced = max(practiced, key=lambda item: item.attempts, default=None)
    games = _game_stats(data.get("games_stats") if isinstance(data.get("games_stats"), list) else [])
    daily = _daily_sessions(data.get("daily_sessions") if isinstance(data.get("daily_sessions"), dict) else {})

    partial = ReportData(
        child_name=payload.get("child_name") or "bé",
        parent_email=parent_email,
        period_type=period_type,
        start_date=str(data.get("start_date") or ""),
        end_date=str(data.get("end_date") or ""),
        created_at=created_at,
        sessions_count=sessions_count,
        average_score=average_score,
        learned_emotion_count=learned_emotion_count,
        total_emotion_count=len(EMOTION_NAMES),
        total_minutes=total_minutes,
        weak_emotions=weak,
        best_emotions=best,
        most_practiced_emotion=most_practiced,
        emotion_stats=emotions,
        game_stats=games,
        daily_sessions=daily,
        achievements=[],
        parent_recommendations=[],
        progress_comment=progress_comment(average_score),
        summary_text=build_summary_text(sessions_count, average_score, period_type),
    )
    recommendations = _parent_recommendations(average_score, learned_emotion_count, weak)
    achievements = _achievements(partial)
    return ReportData(**{**partial.__dict__, "achievements": achievements, "parent_recommendations": recommendations})
