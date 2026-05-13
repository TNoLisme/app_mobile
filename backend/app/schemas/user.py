from pydantic import BaseModel, EmailStr
from typing import Optional
from datetime import date, datetime

class UserDto(BaseModel):
    user_id: str
    username: Optional[str] = None
    email: EmailStr
    role: str
    name: Optional[str] = None
    created_at: Optional[datetime] = None

    class Config:
        from_attributes = True

class UserSyncRequest(BaseModel):
    user_id: str
    username: Optional[str] = None
    email: EmailStr
    name: Optional[str] = None
    age: Optional[int] = None
    gender: Optional[str] = None
    date_of_birth: Optional[date] = None
    phone_number: Optional[str] = None
    report_preferences: Optional[str] = None
    role: str = "child"
