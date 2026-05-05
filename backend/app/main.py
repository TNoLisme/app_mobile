from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.endpoints import assistant, auth, content, emotions, reports, runtime, tts

app = FastAPI(title="Emo Garden API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router, prefix="/users", tags=["Authentication"])
app.include_router(content.router, tags=["Content"])
app.include_router(emotions.router, tags=["Emotions"])
app.include_router(assistant.router)
app.include_router(tts.router)
app.include_router(reports.router)
app.include_router(runtime.router, tags=["Runtime"])


@app.get("/")
def read_root():
    return {"message": "Welcome to Emo Garden Backend (Python/FastAPI)"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
