from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from app.db.session import get_db
from app.models.user import User, Child
from app.schemas.user import UserSyncRequest

router = APIRouter()

@router.post("/register-sync")
async def register_user_sync(request: UserSyncRequest, db: Session = Depends(get_db)):
    # Kiểm tra user tồn tại
    db_user = db.query(User).filter(User.user_id == request.user_id).first()
    if db_user:
        raise HTTPException(status_code=400, detail="User already synced")

    try:
        # Tạo bản ghi User
        new_user = User(
            user_id=request.user_id,
            email=request.email,
            name=request.name,
            role=request.role
        )
        db.add(new_user)
        
        # Tạo bản ghi Child
        new_child = Child(
            user_id=request.user_id,
            age=request.age,
            gender=request.gender
        )
        db.add(new_child)
        
        db.commit()
        return {"status": "success", "message": "Synced successfully"}
    
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))