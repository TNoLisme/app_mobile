from pydantic import BaseModel, EmailStr
from typing import Optional
from datetime import datetime

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
    email: EmailStr
    name: Optional[str] = None
    age: Optional[int] = None
    gender: Optional[str] = None
    role: str = "child"
