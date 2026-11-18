import json
import logging
import os
import re
import time
from datetime import datetime, date

import groq as groq_sdk
from dotenv import load_dotenv
from groq import Groq

from services.spring_service import buscar_medicos_sync, consultar_horarios_sync, crear_cita_sync

load_dotenv()

client = Groq(api_key=os.getenv("GROQ_API_KEY"))
logger = logging.getLogger(__name__)

# ── Días y meses en español ──────────────────────────────────────────────────
_DIAS = ["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"]
_MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio",
          "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"]


def _fecha_a_espanol(fecha_str: str) -> str:
    """Convierte '2026-05-25' → 'lunes 25 de mayo de 2026'."""
    try:
        d = date.fromisoformat(fecha_str)
        return f"{_DIAS[d.weekday()]}, {d.day} de {_MESES[d.month - 1]} de {d.year}"
    except Exception:
        return fecha_str


# ── System prompt ────────────────────────────────────────────────────────────
SYSTEM_PROMPT = """Eres CliniClick, el recepcionista virtual de esta clínica. Tu ÚNICA función es ayudar a los pacientes a agendar citas médicas.

HERRAMIENTAS REALES DEL SISTEMA:
- buscar_medicos(especialidad): llámala cuando el paciente mencione una especialidad. Devuelve médicos reales con id, nombre, especialidad, consultorioId, consultorioNombre, consultorioDireccion y tarifaBase.
- consultar_horarios(medico_id, fecha): llámala SIEMPRE después de que el paciente elija un médico y mencione una fecha. Devuelve los horarios disponibles reales para ese día. fecha debe estar en formato YYYY-MM-DD.
- crear_cita(medico_id, consultorio_id, fecha_hora, duracion_min, motivo): llámala SOLO tras recibir confirmación explícita del paciente. NUNCA antes.

SÍNTOMAS URGENTES:
Si el paciente menciona síntomas potencialmente graves (dolor intenso en el pecho, dificultad para respirar, pérdida de conciencia, sangrado severo u otros síntomas alarmantes), responde PRIMERO con:
"⚠️ Si tus síntomas son intensos o te preocupan, te recomendamos buscar atención médica inmediata o acudir a urgencias."
Luego continúa normalmente con el proceso de agendamiento.

FLUJO OBLIGATORIO (no saltar ni duplicar pasos):
1. Paciente menciona especialidad → llamar buscar_medicos → listar médicos con nombre y especialidad.
2. Paciente elige médico → preguntar qué fecha prefiere.
3. Paciente menciona fecha → llamar consultar_horarios(medico_id, fecha_YYYY-MM-DD) → mostrar horarios disponibles reales:
   "Los horarios disponibles para el [fecha en español] son:
   • 09:00
   • 10:30
   • 14:00
   ¿Cuál prefieres?"
   Si no hay horarios: "La Dra./Dr. [nombre] no tiene disponibilidad ese día. ¿Probamos con otra fecha?"
4. Paciente elige horario → mostrar resumen COMPLETO antes de confirmar:
   📅 Fecha: [día de semana, DD de mes de AAAA]
   🕒 Hora: [HH:MM]
   🏥 Especialidad: [especialidad]
   👨‍⚕️ Médico: [nombre completo]
   🚪 Consultorio: [nombre del consultorio]
   📍 Dirección: [dirección]
   💲 Tarifa: $[tarifa] MXN (si está disponible)
   ¿Confirmamos tu cita?
5. Paciente confirma ("sí", "confirmo", "de acuerdo", "ok") → llamar crear_cita con fecha_hora en ISO 8601 CON timezone (ej: 2026-05-26T10:00:00-06:00) → informar resultado:
   - Éxito: "✅ ¡Tu cita ha sido registrada exitosamente! Te esperamos el [fecha en español] a las [hora]."
   - Error: "Lo sentimos, no fue posible registrar tu cita en este momento. Por favor intenta nuevamente o comunícate con nosotros."
   NUNCA mezclar éxito y error. NUNCA confirmar la cita si no recibiste respuesta exitosa del sistema.

INTENCIÓN DEL PACIENTE — distingue claramente:
- "¿Tiene disponibilidad?" / "¿Qué horarios tiene?" → consultar_horarios, NO agendar todavía.
- Paciente elige un horario → mostrar resumen y pedir confirmación.
- "Sí" / "confirmo" / "de acuerdo" → crear_cita.

FORMATO DE FECHAS (SIEMPRE):
- Usa lenguaje natural: "lunes 25 de mayo de 2026" o "mañana, martes 26 de mayo".
- NUNCA escribas fechas técnicas como "2026-05-25" al hablar con el paciente.
- Internamente usa YYYY-MM-DD solo para las herramientas.
- Para crear_cita, construye el ISO 8601 completo con timezone: YYYY-MM-DDTHH:MM:SS-06:00

MEMORIA CONVERSACIONAL:
- Recuerdas el médico elegido, especialidad, consultorio, dirección y tarifa durante toda la conversación.
- Si el paciente pregunta "¿crees que mañana tenga tiempo la doctorsita?" → responde usando el nombre completo del médico ya seleccionado.
- NUNCA repitas preguntas ya respondidas.
- Si el paciente cambia de médico, fecha u horario, actualiza sin volver a preguntar lo anterior.

GESTIÓN DE IDs (MUY IMPORTANTE):
Los UUIDs de médicos y consultorios son efímeros en la conversación. Cada vez que necesites un UUID:
- Para consultar_horarios: llama PRIMERO a buscar_medicos con la especialidad del médico ya elegido para obtener su id actual.
- Para crear_cita: usa el medico_id y consultorio_id obtenidos de la última llamada a buscar_medicos en este mismo turno.
- NUNCA inventes, supongas ni uses placeholders como "id_del_medico". SIEMPRE usa UUIDs reales obtenidos de las herramientas.

REGLAS GENERALES:
- El paciente ya está identificado. Nunca preguntes su nombre ni datos personales.
- Si menciona síntomas, reconócelos brevemente (aplica alerta urgente si aplica) y ofrece agendar con el especialista adecuado.
- Nunca des diagnósticos, tratamientos ni consejos médicos.
- Si pregunta algo fuera del agendamiento: "Solo puedo ayudarte a agendar citas. ¿Continuamos?"
- Tono: amable, empático y profesional.

FORMATO DE RESPUESTAS:
- Párrafos cortos, máximo 2 líneas cada uno.
- Línea en blanco entre secciones.
- Usa • para listas de médicos u horarios.
- Nunca bloques de texto corridos.

SIN DISPONIBILIDAD PARA LA ESPECIALIDAD:
"Lo sentimos, actualmente nuestra clínica no cuenta con médicos de [especialidad]. ¿Te gustaría buscar otra especialidad disponible?"
No sugieras otras clínicas ni fuentes externas."""

# ── Tool definitions ─────────────────────────────────────────────────────────
TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "buscar_medicos",
            "description": "Busca médicos disponibles por especialidad en esta clínica. Llama esta herramienta cuando el paciente mencione una especialidad médica.",
            "parameters": {
                "type": "object",
                "properties": {
                    "especialidad": {
                        "type": "string",
                        "description": "Especialidad médica (ej: cardiología, pediatría, dermatología)"
                    }
                },
                "required": ["especialidad"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "consultar_horarios",
            "description": "Consulta los horarios disponibles de un médico para una fecha específica. Llama esta herramienta cuando el paciente haya elegido un médico y mencione una fecha.",
            "parameters": {
                "type": "object",
                "properties": {
                    "medico_id": {
                        "type": "string",
                        "description": "UUID del médico seleccionado"
                    },
                    "fecha": {
                        "type": "string",
                        "description": "Fecha en formato YYYY-MM-DD (ej: 2026-05-26)"
                    }
                },
                "required": ["medico_id", "fecha"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "crear_cita",
            "description": "Crea la cita médica en el sistema. Llama esta herramienta ÚNICAMENTE cuando el paciente haya confirmado explícitamente todos los datos.",
            "parameters": {
                "type": "object",
                "properties": {
                    "medico_id": {
                        "type": "string",
                        "description": "UUID del médico seleccionado"
                    },
                    "consultorio_id": {
                        "type": "string",
                        "description": "UUID del consultorio del médico"
                    },
                    "fecha_hora": {
                        "type": "string",
                        "description": "Fecha y hora en ISO 8601 con timezone (ej: 2026-05-26T10:00:00-06:00). SIEMPRE incluir el offset de timezone."
                    },
                    "duracion_min": {
                        "type": "integer",
                        "description": "Duración en minutos (default 30)"
                    },
                    "motivo": {
                        "type": "string",
                        "description": "Motivo de la consulta"
                    }
                },
                "required": ["medico_id", "consultorio_id", "fecha_hora"]
            }
        }
    }
]

_ROLES_VALIDOS = {"user", "assistant"}

_GROQ_TRANSITORIOS = (
    groq_sdk.RateLimitError,
    groq_sdk.APITimeoutError,
    groq_sdk.APIConnectionError,
    groq_sdk.InternalServerError,
)


def _llamar_groq(kwargs: dict, max_intentos: int = 3):
    """Llama a Groq con reintentos para errores transitorios."""
    for intento in range(max_intentos):
        try:
            return client.chat.completions.create(**kwargs)
        except _GROQ_TRANSITORIOS as exc:
            if intento < max_intentos - 1:
                espera = 2 ** intento
                logger.warning(
                    "Groq error transitorio (intento %d/%d, %s). Reintentando en %ds…",
                    intento + 1, max_intentos, type(exc).__name__, espera,
                )
                time.sleep(espera)
            else:
                raise


_UUID_RE = re.compile(
    r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
    re.IGNORECASE
)

def _uuid_valido(valor: str) -> bool:
    return bool(valor and _UUID_RE.match(str(valor)))


def _sanitizar_historial(historial: list) -> list:
    resultado = []
    for msg in historial:
        if not isinstance(msg, dict):
            continue
        role = msg.get("role", "")
        if role == "ai":
            role = "assistant"
        if role not in _ROLES_VALIDOS:
            continue
        content = msg.get("content") or msg.get("text") or ""
        if not content:
            continue
        resultado.append({"role": role, "content": content})
    return resultado


def _ejecutar_herramienta(tool_call, org_id: str, auth_header: str) -> str:
    tool_name = getattr(tool_call.function, "name", "desconocida")
    try:
        name = tool_name
        try:
            args = json.loads(tool_call.function.arguments)
        except Exception:
            return json.dumps({"error": "Argumentos inválidos"})

        if name == "buscar_medicos":
            medicos = buscar_medicos_sync(args.get("especialidad", ""), org_id, auth_header)
            if not medicos:
                return json.dumps({"resultado": "Esta clínica no cuenta con médicos de esa especialidad."})
            resumen = [
                {
                    "id": m.get("id"),
                    "nombre": m.get("nombre"),
                    "especialidad": m.get("especialidad"),
                    "consultorioId": m.get("consultorioId"),
                    "consultorioNombre": m.get("consultorioNombre"),
                    "consultorioDireccion": m.get("consultorioDireccion"),
                    "tarifaBase": m.get("tarifaBase")
                }
                for m in medicos
            ]
            return json.dumps({"medicos": resumen})

        if name == "consultar_horarios":
            medico_id = args.get("medico_id", "")
            fecha = args.get("fecha", "")
            if not _uuid_valido(medico_id):
                return json.dumps({
                    "error": "medico_id no es un UUID válido. Llama primero a buscar_medicos para obtener el id real del médico y úsalo aquí."
                })
            slots = consultar_horarios_sync(medico_id, fecha, auth_header)
            fecha_legible = _fecha_a_espanol(fecha)
            if not slots:
                return json.dumps({
                    "fecha": fecha_legible,
                    "slots": [],
                    "mensaje": f"El médico no tiene horarios disponibles para el {fecha_legible}."
                })
            return json.dumps({"fecha": fecha_legible, "slots": slots})

        if name == "crear_cita":
            medico_id = args.get("medico_id", "")
            consultorio_id = args.get("consultorio_id", "")
            if not _uuid_valido(medico_id) or not _uuid_valido(consultorio_id):
                return json.dumps({
                    "error": "medico_id o consultorio_id no son UUIDs válidos. Debes llamar primero a buscar_medicos para obtener los IDs reales del médico, y luego usarlos aquí."
                })
            resultado = crear_cita_sync(args, auth_header)
            if not isinstance(resultado, dict):
                logger.error("crear_cita_sync devolvió un tipo inesperado: %s (%r)", type(resultado).__name__, resultado)
                return json.dumps({"exito": False, "error": "Respuesta inesperada del servidor al crear la cita."})
            if "error" in resultado:
                return json.dumps({"exito": False, "error": resultado["error"]})
            return json.dumps({
                "exito": True,
                "id": resultado.get("id"),
                "fechaHora": resultado.get("fechaHora"),
                "estado": resultado.get("estado", "SIN_CONFIRMAR")
            })

        return json.dumps({"error": f"Herramienta '{name}' desconocida"})

    except Exception as exc:
        logger.exception("Error inesperado en herramienta '%s': %s", tool_name, exc)
        return json.dumps({"error": f"Error interno ejecutando '{tool_name}'. Intenta de nuevo."})


def obtener_respuesta(
    mensaje: str,
    historial: list = [],
    patient_name: str = None,
    org_id: str = None,
    auth_header: str = ""
) -> str:
    hora = datetime.now().hour
    if 5 <= hora < 12:
        saludo_hora = "buenos días"
    elif 12 <= hora < 19:
        saludo_hora = "buenas tardes"
    else:
        saludo_hora = "buenas noches"

    ahora = datetime.now()
    fecha_hoy = f"{_DIAS[ahora.weekday()]}, {ahora.day} de {_MESES[ahora.month - 1]} de {ahora.year}"

    system_content = f"HOY ES: {fecha_hoy}. Usa esta fecha para interpretar 'mañana', 'próximo lunes', etc.\n\n{SYSTEM_PROMPT}"

    if patient_name:
        system_content += (
            f"\n\nEl paciente se llama {patient_name}. "
            f"Usa su nombre con naturalidad cuando sea apropiado. "
            f"El saludo correcto para este momento es '{saludo_hora}'."
        )

    mensajes = [{"role": "system", "content": system_content}]
    mensajes.extend(_sanitizar_historial(historial))
    mensajes.append({"role": "user", "content": mensaje})

    try:
        # Loop multi-turno: permite encadenar herramientas (ej: buscar_medicos → consultar_horarios)
        MAX_RONDAS = 5
        for ronda in range(MAX_RONDAS):
            usar_tools = ronda < MAX_RONDAS - 1  # última ronda sin tools para forzar respuesta final

            kwargs = {
                "model": "llama-3.3-70b-versatile",
                "messages": mensajes,
                "temperature": 0.3,
                "max_tokens": 600,
            }
            if usar_tools:
                kwargs["tools"] = TOOLS
                kwargs["tool_choice"] = "auto"

            respuesta = _llamar_groq(kwargs)
            msg = respuesta.choices[0].message

            # Sin tool calls: es la respuesta final
            if not msg.tool_calls:
                return msg.content or "No pude generar una respuesta."

            # Agregar mensaje del asistente con sus tool calls
            mensajes.append({
                "role": "assistant",
                "content": msg.content or "",
                "tool_calls": [
                    {
                        "id": tc.id,
                        "type": "function",
                        "function": {"name": tc.function.name, "arguments": tc.function.arguments}
                    }
                    for tc in msg.tool_calls
                ]
            })

            # Ejecutar cada herramienta y agregar resultado
            for tc in msg.tool_calls:
                resultado = _ejecutar_herramienta(tc, org_id or "", auth_header)
                mensajes.append({
                    "role": "tool",
                    "tool_call_id": tc.id,
                    "content": resultado
                })

        return "No pude generar una respuesta."

    except Exception as exc:
        logger.exception("Error en obtener_respuesta: %s", exc)
        return (
            "Hola, disculpa la interrupción. En este momento estoy experimentando una falla temporal.\n\n"
            "Por favor intenta nuevamente en unos segundos, con gusto te ayudo."
        )
