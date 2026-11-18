from fastapi import APIRouter, Request, HTTPException
from pydantic import BaseModel
from services.groq_service import obtener_respuesta
from services.spring_service import obtener_medicos
import jwt  # pip install PyJWT
import os

router = APIRouter(prefix="/api/ia", tags=["IA - Chat"])


class MensajeRequest(BaseModel):
    mensaje: str
    historial: list = []
    organizacion_id: str = None


class MensajeResponse(BaseModel):
    respuesta: str
    fuente: str = "llama-3.3-70b-versatile"


def _decode_jwt_payload(request: Request) -> dict:
    """Decodifica el JWT sin verificar firma (Spring Boot ya lo validó)."""
    try:
        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            return {}
        token = auth_header.split(" ")[1]
        return jwt.decode(token, options={"verify_signature": False}, algorithms=["HS256"])
    except Exception:
        return {}


def extraer_nombre_paciente(payload: dict) -> str | None:
    return (
        payload.get("name")
        or payload.get("nombre")
        or payload.get("preferred_username")
        or payload.get("sub")
    )


def extraer_organizacion_id(payload: dict) -> str | None:
    """
    Extrae organizacionId del JWT — fuente autoritativa para el tenant.
    Ignoramos cualquier org_id que venga en el body para garantizar aislamiento.
    """
    return payload.get("organizacionId")


@router.post("/chat", response_model=MensajeResponse)
def chat(request: Request, body: MensajeRequest):
    auth_header = request.headers.get("Authorization", "")
    payload = _decode_jwt_payload(request)
    patient_name = extraer_nombre_paciente(payload)
    org_id = extraer_organizacion_id(payload)  # siempre desde el JWT, nunca del body

    respuesta = obtener_respuesta(
        mensaje=body.mensaje,
        historial=body.historial,
        patient_name=patient_name,
        org_id=org_id,
        auth_header=auth_header
    )

    return MensajeResponse(respuesta=respuesta)


@router.get("/disponibilidad/{organizacion_id}")
async def disponibilidad(organizacion_id: str):
    medicos = await obtener_medicos(organizacion_id)
    return {
        "organizacion_id": organizacion_id,
        "medicos_disponibles": medicos,
        "total": len(medicos)
    }


@router.get("/salud")
def salud():
    return {"status": "ok", "servicio": "CliniClick IA"}