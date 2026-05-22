import sys
sys.stdout.reconfigure(encoding='utf-8')
from sqlalchemy import create_engine, text
from app.core.config import settings

engine = create_engine(settings.DATABASE_URL)
with engine.begin() as conn:
    result = conn.execute(text("SELECT top 1 question_text FROM game_content WHERE question_text IS NOT NULL")).scalar()
    print('Sample:', result)
