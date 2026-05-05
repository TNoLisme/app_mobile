from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.models.game import EmotionConcept
from app.schemas.content import EmotionConceptOut

router = APIRouter()


@router.get("/emotion-concepts", response_model=list[EmotionConceptOut])
def list_emotion_concepts(db: Session = Depends(get_db)):
    return db.query(EmotionConcept).order_by(EmotionConcept.level, EmotionConcept.emotion).all()


@router.get("/emotions/concepts")
def list_emotion_concepts_web_shape(db: Session = Depends(get_db)):
    concepts = [
        EmotionConceptOut.model_validate(row).model_dump()
        for row in db.query(EmotionConcept).order_by(EmotionConcept.level, EmotionConcept.emotion).all()
    ]
    return {"status": "success", "data": {"concepts": concepts}}

