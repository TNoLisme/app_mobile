from datetime import datetime

from sqlalchemy import Column, DateTime, Float, ForeignKey, Integer, String, Text

from app.db.base import Base


class ChildProgress(Base):
    __tablename__ = "child_progress"

    progress_id = Column(String(64), primary_key=True)
    child_id = Column(String(128), ForeignKey("users.user_id", ondelete="CASCADE"), nullable=False, index=True)
    game_id = Column(String(64), ForeignKey("games.game_id", ondelete="SET NULL"), index=True)
    level = Column(Integer, default=1)
    accuracy = Column(Float, default=0.0)
    avg_response_time = Column(Float, default=0.0)
    score = Column(Integer, default=0)
    last_played = Column(DateTime, default=datetime.utcnow)
    ratio = Column(Text)
    review_emotions = Column(Text)


class Report(Base):
    __tablename__ = "reports"

    report_id = Column(String(64), primary_key=True)
    child_id = Column(String(128), ForeignKey("users.user_id", ondelete="CASCADE"), nullable=False, index=True)
    report_type = Column(String(50), default="weekly")
    generated_at = Column(DateTime, default=datetime.utcnow)
    summary = Column(Text)
    data = Column(Text)


class ChatbotLog(Base):
    __tablename__ = "chatbot_logs"

    log_id = Column(Integer, primary_key=True, autoincrement=True)
    child_id = Column(String(128), ForeignKey("users.user_id", ondelete="CASCADE"), nullable=False, index=True)
    sender = Column(String(50), nullable=False)
    message_content = Column(Text, nullable=False)
    timestamp = Column(DateTime, default=datetime.utcnow)

