from sqlalchemy import Column, String, Unicode, Integer, DateTime, ForeignKey, Date
from sqlalchemy.orm import relationship
from datetime import datetime
from app.db.base import Base

class User(Base):
    __tablename__ = "users"
    user_id = Column(String(128), primary_key=True)
    username = Column(Unicode(50))
    email = Column(Unicode(100), nullable=False, unique=True)
    password = Column(Unicode(255))
    role = Column(Unicode(20), default="child")
    name = Column(Unicode(100))
    created_at = Column(DateTime, default=datetime.utcnow)

    child_profile = relationship("Child", back_populates="user", uselist=False)

class Child(Base):
    __tablename__ = "children"
    user_id = Column(String(128), ForeignKey("users.user_id", ondelete="CASCADE"), primary_key=True)
    age = Column(Integer)
    gender = Column(String(10))
    date_of_birth = Column(Date)
    phone_number = Column(String(20))
    report_preferences = Column(String(512))
    
    # Bổ sung các cột thiếu
    created_at = Column(DateTime, default=datetime.utcnow)
    last_login = Column(DateTime)
    last_played = Column(DateTime)
    
    user = relationship("User", back_populates="child_profile")
