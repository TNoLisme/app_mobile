from pydantic import BaseModel


class SessionSaveRequest(BaseModel):
    session_id: str
    user_id: str
    score: int = 0
    start_time: str | None = None
    emotion_errors: str | None = None


class SessionQuestionSaveRequest(BaseModel):
    id: str
    session_id: str
    question_id: str | None = None
    is_correct: bool = False
    response_time_ms: int = 0
    cv_confidence: float | None = None
    used_hint: bool = False


class ChatbotLogRequest(BaseModel):
    log_id: int | None = None
    child_id: str
    sender: str
    message_content: str
    timestamp: str | None = None


class ProgressOut(BaseModel):
    progress_id: str
    child_id: str
    accuracy: float
    score: int
    last_played: str


class ReportOut(BaseModel):
    report_id: str
    report_type: str
    summary: str
    data: str | None = None


class StartGameRequest(BaseModel):
    user_id: str
    level: int = 1


class AnswerResult(BaseModel):
    question_id: str
    answer: str | None = None
    is_correct: bool
    response_time_ms: int = 0
    used_hint: bool = False
    cv_confidence: float | None = None


class EndLevelRequest(BaseModel):
    session_id: str
    results: list[AnswerResult]
    review_emotions: list[str] = []
    reset_review_emotions: list[str] = []
