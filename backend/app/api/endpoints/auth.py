from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.models.user import Child, User
from app.schemas.user import UserDto, UserSyncRequest

router = APIRouter()


@router.post("/sync", response_model=UserDto)
async def sync_user(request: UserDto, db: Session = Depends(get_db)):
    db_user = db.query(User).filter(User.user_id == request.user_id).first()
    if db_user is None:
        db_user = User(user_id=request.user_id)
        db.add(db_user)

    db_user.username = request.username
    db_user.email = request.email
    db_user.name = request.name
    db_user.role = request.role

    db.commit()
    db.refresh(db_user)
    return db_user


@router.post("/register-sync")
async def register_user_sync(request: UserSyncRequest, db: Session = Depends(get_db)):
    try:
        db_user = db.query(User).filter(User.user_id == request.user_id).first()
        if db_user is None:
            db_user = User(user_id=request.user_id)
            db.add(db_user)

        db_user.email = request.email
        db_user.name = request.name
        db_user.role = request.role

        child = db.query(Child).filter(Child.user_id == request.user_id).first()
        if child is None:
            child = Child(user_id=request.user_id)
            db.add(child)

        child.age = request.age
        child.gender = request.gender

        db.commit()
        return {"status": "success", "message": "Synced successfully"}
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e)) from e
