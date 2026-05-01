from fastapi import FastAPI
from app.api.endpoints import auth
from app.db.base import Base
from app.db.session import engine

# Khởi tạo FastAPI
app = FastAPI(title="Emo Garden API", version="1.0.0")

# Đăng ký các router
app.include_router(auth.router, prefix="/users", tags=["Authentication"])

@app.get("/")
def read_root():
    return {"message": "Welcome to Emo Garden Backend (Python/FastAPI)"}

if __name__ == "__main__":
    import uvicorn
    # Chạy server trên port 8000
    uvicorn.run(app, host="0.0.0.0", port=8000)