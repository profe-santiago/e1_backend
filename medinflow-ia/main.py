from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from routers import chat, auth
from dotenv import load_dotenv
load_dotenv()

app = FastAPI(
    title="MedInFlow IA",
    description="Microservicio de IA para agendar citas médicas usando Llama 3",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(chat.router)

@app.get("/")
def root():
    return {
        "servicio": "MedInFlow IA",
        "version": "1.0.0",
        "modelo": "llama-3.3-70b-versatile",
        "status": "activo"
    }
