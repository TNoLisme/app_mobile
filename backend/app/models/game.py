from datetime import datetime

from sqlalchemy import Column, DateTime, Float, ForeignKey, Integer, String, Text, Unicode, UnicodeText

from app.db.base import Base


class Game(Base):
    __tablename__ = "games"

    game_id = Column(String(64), primary_key=True)
    game_type = Column(String(50), nullable=False, index=True)
    name = Column(Unicode(255), nullable=False)
    level = Column(Integer, default=1)
    difficulty_level = Column(Unicode(50))
    max_errors = Column(Integer, default=3)
    level_threshold = Column(Float, default=70.0)
    time_limit = Column(Integer)


class GameContent(Base):
    __tablename__ = "game_content"

    content_id = Column(String(64), primary_key=True)
    game_id = Column(String(64), ForeignKey("games.game_id", ondelete="CASCADE"), nullable=False, index=True)
    level = Column(Integer, default=1, index=True)
    content_type = Column(Unicode(50), nullable=False)
    media_path = Column(Unicode(500))
    question_text = Column(UnicodeText)
    correct_answer = Column(Unicode(100))
    emotion = Column(Unicode(100))
    explanation = Column(UnicodeText)


class EmotionConcept(Base):
    __tablename__ = "emotion_concepts"

    concept_id = Column(String(64), primary_key=True)
    emotion = Column(Unicode(100), nullable=False)
    level = Column(Integer, default=1)
    title = Column(Unicode(255), nullable=False)
    video_path = Column(Unicode(500))
    image_path = Column(Unicode(500))
    audio_path = Column(Unicode(500))
    description = Column(UnicodeText)


class PlaySession(Base):
    __tablename__ = "sessions"

    session_id = Column(String(64), primary_key=True)
    user_id = Column(String(128), ForeignKey("users.user_id", ondelete="CASCADE"), nullable=False, index=True)
    game_id = Column(String(64), ForeignKey("games.game_id", ondelete="SET NULL"), index=True)
    start_time = Column(DateTime, default=datetime.utcnow)
    end_time = Column(DateTime)
    state = Column(String(30), default="playing")
    score = Column(Integer, default=0)
    emotion_errors = Column(Text)
    max_errors = Column(Integer, default=3)
    level_threshold = Column(Float, default=70.0)
    ratio = Column(Text)
    time_limit = Column(Integer)
    question_ids = Column(Text)
    level = Column(Integer, default=1)


class SessionQuestion(Base):
    __tablename__ = "session_questions"

    id = Column(String(64), primary_key=True)
    session_id = Column(String(64), ForeignKey("sessions.session_id", ondelete="CASCADE"), nullable=False, index=True)
    question_id = Column(String(64), ForeignKey("game_content.content_id", ondelete="SET NULL"), index=True)
    is_correct = Column(Integer, default=0)
    response_time_ms = Column(Integer, default=0)
    cv_confidence = Column(Float)
    used_hint = Column(Integer, default=0)
