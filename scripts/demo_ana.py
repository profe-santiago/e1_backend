"""Datos de la demo POR API (después de recrear la base con el seed):

  1. Teléfono de Ana Sofía Ramírez Mendoza -> el número de la demo (DEMO_TELEFONO, 10 dígitos)
  2. Cita SIN_CONFIRMAR mañana a las 11:00 (hora de la clínica) con la Dra. Mariana López Ruiz

El teléfono NO está en el código. Se toma, en este orden, de:
    --telefono 951XXXXXXX  |  variable de entorno DEMO_TELEFONO  |  DEMO_TELEFONO en el .env de la raíz

Uso (desde la raíz del repo, con el backend levantado en :8080):
    python scripts/demo_ana.py                       # con DEMO_TELEFONO en el entorno o en .env
    python scripts/demo_ana.py --telefono 951XXXXXXX
    python scripts/demo_ana.py --hora 12:30          # otra hora
    python scripts/demo_ana.py --api http://localhost:8080
    python scripts/demo_ana.py --limpiar             # además borra las DEMÁS citas de Ana de mañana

Es idempotente: si la cita ya existe y no está cancelada, solo imprime su id.
--limpiar solo borra citas de Ana Sofía de MAÑANA distintas de la de la demo, e imprime cuáles borró.
Lee la contraseña del seed desde SeedDataLoader.java y NUNCA imprime token ni contraseña.
"""
import argparse
import json
import os
import pathlib
import re
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

RAIZ = pathlib.Path(__file__).resolve().parent.parent
SEED = RAIZ / "mi-app/src/main/java/com/clinica/mi_app/seed/SeedDataLoader.java"
PACIENTE = "Ana Sofía Ramírez Mendoza"
MEDICA = "Dra. Mariana López Ruiz"
# America/Mexico_City: UTC-6 todo el año (sin horario de verano desde 2022)
CLINICA = timezone(timedelta(hours=-6))

for _flujo in (sys.stdout, sys.stderr):
    if hasattr(_flujo, "reconfigure"):
        _flujo.reconfigure(encoding="utf-8")


def telefono_demo(argumento):
    """--telefono > variable de entorno DEMO_TELEFONO > DEMO_TELEFONO en el .env de la raíz."""
    valor = argumento or os.environ.get("DEMO_TELEFONO", "")
    if not valor:
        env = RAIZ / ".env"
        if env.exists():
            for linea in env.read_text(encoding="utf-8").splitlines():
                if linea.strip().startswith("DEMO_TELEFONO="):
                    valor = linea.split("=", 1)[1].strip().strip('"').strip("'")
    valor = re.sub(r"\D", "", valor or "")
    if not valor:
        sys.exit("Falta el teléfono de la demo: usa --telefono 951XXXXXXX o define DEMO_TELEFONO "
                 "(variable de entorno o en .env). Ver .env.example.")
    if len(valor) != 10:
        sys.exit("DEMO_TELEFONO debe tener 10 dígitos (número de México sin +52).")
    return valor


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--api", default="http://localhost:8080")
    ap.add_argument("--hora", default="11:00")
    ap.add_argument("--telefono", help="10 dígitos; si no, DEMO_TELEFONO del entorno o del .env")
    ap.add_argument("--limpiar", action="store_true",
                    help="borra por API las demás citas de Ana de mañana (p. ej. la que crea el seed)")
    args = ap.parse_args()
    telefono = telefono_demo(args.telefono)

    src = SEED.read_text(encoding="utf-8")
    password = re.search(r'PASSWORD\s*=\s*"([^"]+)"', src).group(1)
    email = re.search(r'ADMIN_EMAIL\s*=\s*"([^"]+)"', src).group(1)

    def call(method, path, body=None, token=None):
        req = urllib.request.Request(
            args.api + path, method=method,
            data=json.dumps(body).encode() if body is not None else None,
            headers={"Content-Type": "application/json", "Accept": "application/json",
                     **({"Authorization": "Bearer " + token} if token else {})})
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                raw = r.read().decode()
                return r.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            raw = e.read().decode()
            try:
                return e.code, json.loads(raw)
            except ValueError:
                return e.code, raw
        except urllib.error.URLError:
            sys.exit(f"No hay conexión con el backend en {args.api}. ¿Está levantado mi-app?")

    st, auth = call("POST", "/api/auth/login", {"email": email, "password": password})
    if st != 200:
        sys.exit(f"Login de admin falló ({st}). ¿Está levantado el backend con APP_SEED_ENABLED=true?")
    tok, org = auth["token"], auth["organizacionId"]
    print("Login admin OK")

    # 1. Teléfono de Ana
    _, pacientes = call("GET", f"/api/pacientes/organizacion/{org}", token=tok)
    ana = next(p for p in pacientes if p["nombre"] == PACIENTE)
    if ana.get("telefono") != telefono:
        body = {k: ana.get(k) for k in ("nombre", "fechaNacimiento", "sexo", "email", "notas")}
        body.update({"organizacionId": org, "telefono": telefono})
        st, ana = call("PUT", f"/api/pacientes/{ana['id']}", body, tok)
        if st != 200:
            sys.exit(f"No se pudo actualizar el teléfono ({st}): {ana}")
    print(f"Paciente {ana['id']} · teléfono ******{str(ana['telefono'])[-4:]}")

    # 2. Cita mañana (convención: hora de reloj de la clínica expresada en UTC)
    manana = (datetime.now(CLINICA) + timedelta(days=1)).date()
    fecha_hora = f"{manana.isoformat()}T{args.hora}:00Z"
    _, medicos = call("GET", f"/api/medicos/organizacion/{org}", token=tok)
    mariana = next(m for m in medicos if m["nombre"] == MEDICA)

    _, citas = call("GET", f"/api/citas/paciente/{ana['id']}", token=tok)
    cita = next((c for c in citas if c.get("medicoId") == mariana["id"]
                 and str(c.get("fechaHora", "")).startswith(fecha_hora[:16])
                 and c.get("estado") != "CANCELADA"), None)
    if cita:
        print("La cita ya existía")
    else:
        st, cita = call("POST", "/api/citas", {
            "organizacionId": org, "pacienteId": ana["id"], "medicoId": mariana["id"],
            "consultorioId": mariana["consultorioId"], "fechaHora": fecha_hora, "duracionMin": 30,
            "estado": "SIN_CONFIRMAR", "motivo": "Demo recordatorio por enlace"}, tok)
        if st not in (200, 201):
            mensaje = cita.get("mensaje") if isinstance(cita, dict) else cita
            _, libres = call("GET", f"/api/citas/medico/{mariana['id']}/disponibilidad?fecha={manana.isoformat()}", token=tok)
            horas = ", ".join(h["hora"] for h in libres) if isinstance(libres, list) and libres else "ninguna"
            propias = [str(c.get("fechaHora", ""))[11:16] for c in citas
                       if str(c.get("fechaHora", "")).startswith(manana.isoformat())
                       and c.get("medicoId") == mariana["id"] and c.get("estado") != "CANCELADA"]
            sys.exit(f"No se pudo crear la cita de las {args.hora} ({st}): {mensaje}\n"
                     f"  Horas libres de la Dra. Mariana mañana: {horas}\n"
                     + (f"  Ana ya tiene cita mañana a las: {', '.join(propias)} (puedes usarla con --hora)\n" if propias else "")
                     + "  No se borró nada.")
    print(f"CITA_ID: {cita['id']} · {cita['estado']} · {cita['fechaHora']}")
    if cita.get("estado") != "SIN_CONFIRMAR":
        print(f"⚠ La cita de las {args.hora} está {cita.get('estado')}; no la modifico. Para la demo debe estar SIN_CONFIRMAR.")

    # 3. --limpiar: borra (por API) las DEMÁS citas de Ana de mañana, para que salga una sola vez
    if args.limpiar:
        clave_manana = manana.isoformat()
        otras = [c for c in citas
                 if str(c.get("fechaHora", "")).startswith(clave_manana) and c.get("id") != cita["id"]]
        if not otras:
            print("Limpieza: no había otras citas de Ana mañana.")
        for c in otras:
            st, _ = call("DELETE", f"/api/citas/{c['id']}", token=tok)
            hora = str(c.get("fechaHora", ""))[11:16]
            if st in (200, 204):
                print(f"Borrada: cita {c['id']} · mañana {hora} · {c.get('estado')}")
            else:
                print(f"⚠ No se pudo borrar la cita {c['id']} ({hora}): HTTP {st}")


if __name__ == "__main__":
    main()
