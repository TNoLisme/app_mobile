from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.core.config import settings

# Engine kết nối SQL Server
engine = create_engine(settings.DATABASE_URL, pool_pre_ping=True)

# Tạo SessionLocal class
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# Dependency để inject vào các API
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()