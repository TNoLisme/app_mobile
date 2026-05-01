from pydantic import BaseModel, EmailStr
from typing import Optional

class UserSyncRequest(BaseModel):
    user_id: str
    email: EmailStr
    name: Optional[str] = None
    age: Optional[int] = None
    gender: Optional[str] = None
    role: str = "child"