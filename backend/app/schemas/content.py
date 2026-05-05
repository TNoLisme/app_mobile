from pydantic import BaseModel, ConfigDict


class GameOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    game_id: str
    game_type: str
    name: str
    level: int
    max_errors: int
    time_limit: int | None = None


class GameContentOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    content_id: str
    game_id: str
    level: int
    content_type: str
    media_path: str | None = None
    question_text: str | None = None
    correct_answer: str | None = None
    emotion: str | None = None
    explanation: str | None = None


class EmotionConceptOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    concept_id: str
    emotion: str
    title: str
    video_path: str | None = None
    image_path: str | None = None
    description: str | None = None


class CvScenarioOut(BaseModel):
    id: str
    title: str
    description: str
    target_emotion: str
    instruction: str
    hint: str | None = None
    image_path: str | None = None
    explanation: str | None = None
    level: int = 1


class CvScenarioListOut(BaseModel):
    scenarios: list[CvScenarioOut]
