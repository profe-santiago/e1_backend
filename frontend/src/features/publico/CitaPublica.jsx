import React from "react";
import { AlertCircle, CalendarX, Check, CheckCircle2, Hourglass, Lock, MapPin, Phone, UserRound } from "lucide-react";
import { UTC, formatCitaHora12 } from "../../lib/display";
import "./CitaPublica.css";

/**
 * Página pública /c/{token} (canal "enlace"): "pase de cita" para que el paciente confirme o cancele sin
 * iniciar sesión. Usa rutas relativas (/api/public/...) para funcionar por el proxy de Vite tanto en
 * Wi-Fi (http://<ip>:5173) como por el túnel, sin depender de VITE_API_URL.
 */
export default function CitaPublica({ token }) {
  const [cita, setCita] = React.useState(null);
  const [error, setError] = React.useState(null);
  const [cargando, setCargando] = React.useState(true);
  const [enviando, setEnviando] = React.useState(false);
  const [preguntarCancelar, setPreguntarCancelar] = React.useState(false);
  const [resultado, setResultado] = React.useState(null);

  React.useEffect(() => {
    document.title = "Tu cita · CliniClick";
    let activo = true;
    llamar("GET", token)
      .then((data) => activo && setCita(data))
      .catch((err) => activo && setError(err))
      .finally(() => activo && setCargando(false));
    return () => {
      activo = false;
    };
  }, [token]);

  async function responder(accion) {
    setEnviando(true);
    try {
      const data = await llamar("POST", token, { accion });
      setCita(data);
      setResultado(data.resultado || (accion === "CONFIRMAR" ? "CONFIRMADA" : "CANCELADA"));
    } catch (err) {
      setError(err);
    } finally {
      setEnviando(false);
      setPreguntarCancelar(false);
    }
  }

  const nombre = cita?.pacienteNombre;
  let titulo = "Tu cita";
  let subtitulo = "";
  if (cargando) titulo = "Cargando tu cita...";
  else if (error) titulo = TITULOS[error.codigo] || "No pudimos abrir tu cita";
  else if (resultado === "CONFIRMADA") titulo = "¡Gracias! Te esperamos";
  else if (resultado === "CANCELADA") titulo = "Tu cita fue cancelada";
  else if (cita) {
    titulo = nombre ? `Hola, ${nombre}` : "Hola";
    subtitulo = `${cita.clinica || "Tu clínica"} te recuerda tu cita. ¿Nos confirmas?`;
  }

  return (
    <div className="pase">
      <header className="pase-cabeza">
        <svg className="pase-corazon" viewBox="0 0 100 92" aria-hidden="true">
          <path d="M50 88 L10 50 A22 22 0 0 1 50 14 A22 22 0 0 1 90 50 Z" />
        </svg>
        <img className="pase-logo" src="/brand/cliniclick-blanco.png" alt="CliniClick" />
        <div className="pase-saludo" aria-live="polite">
          <h1>{titulo}</h1>
          {subtitulo && <p>{subtitulo}</p>}
        </div>
      </header>

      <main className="pase-cuerpo">
        {cargando && (
          <div className="pase-tarjeta pase-estado" aria-busy="true">
            <Hourglass size={34} className="pase-icono tenue" />
            <p>Un momento, estamos buscando tu cita.</p>
          </div>
        )}

        {!cargando && error && <EstadoError error={error} />}

        {!cargando && !error && cita && (
          <>
            <Boleto cita={cita} resultado={resultado} />

            {resultado ? (
              <Resultado resultado={resultado} cita={cita} />
            ) : preguntarCancelar ? (
              <div className="pase-acciones" role="group" aria-label="Confirmar cancelación">
                <p className="pase-pregunta">¿Seguro que quieres cancelar tu cita? Este enlace solo se puede usar una vez.</p>
                <button type="button" className="pase-boton peligro-lleno" disabled={enviando} onClick={() => responder("CANCELAR")}>
                  {enviando ? "Cancelando..." : "Sí, cancelar mi cita"}
                </button>
                <button type="button" className="pase-boton secundario" disabled={enviando} onClick={() => setPreguntarCancelar(false)}>
                  No, volver
                </button>
              </div>
            ) : (
              <div className="pase-acciones">
                {cita.estado === "CONFIRMADA" ? (
                  <p className="pase-ya"><CheckCircle2 size={18} /> Tu cita ya está confirmada.</p>
                ) : (
                  <button type="button" className="pase-boton primario" disabled={enviando} onClick={() => responder("CONFIRMAR")}>
                    <Check size={20} strokeWidth={2.8} />
                    {enviando ? "Enviando..." : "Sí, ahí estaré"}
                  </button>
                )}
                <button type="button" className="pase-boton peligro" disabled={enviando} onClick={() => setPreguntarCancelar(true)}>
                  No podré asistir
                </button>
                <LlamarClinica telefono={cita.telefonoClinica} />
              </div>
            )}
          </>
        )}
      </main>

      <footer className="pase-pie">
        <Lock size={16} aria-hidden="true" /> Enlace personal de un solo uso
      </footer>
    </div>
  );
}

/** Tarjeta tipo boleto: fecha grande en bloque Tinta, muescas laterales y datos de la cita. */
function Boleto({ cita, resultado }) {
  const fecha = new Date(cita.fechaHora);
  const dia = fecha.toLocaleDateString("es-MX", { weekday: "short", timeZone: UTC }).replace(".", "").toUpperCase();
  const numero = fecha.toLocaleDateString("es-MX", { day: "numeric", timeZone: UTC });
  const mes = fecha.toLocaleDateString("es-MX", { month: "short", timeZone: UTC }).replace(".", "");
  const [hora, ampm] = formatCitaHora12(cita.fechaHora).split(" ");
  const fechaCompleta = fecha.toLocaleDateString("es-MX", { weekday: "long", day: "numeric", month: "long", timeZone: UTC });
  return (
    <article className={`pase-tarjeta pase-boleto ${resultado === "CANCELADA" ? "cancelado" : ""}`} aria-label={`Cita del ${fechaCompleta} a las ${hora} ${ampm}`}>
      <div className="pase-boleto-arriba">
        <div className="pase-fecha" aria-hidden="true">
          <span className="pase-fecha-dia">{dia}</span>
          <span className="pase-fecha-num">{numero}</span>
          <span className="pase-fecha-mes">{mes}</span>
        </div>
        <div className="pase-hora">
          <span className="pase-hora-valor">{hora} <small>{ampm}</small></span>
          <span className="pase-hora-detalle">
            {cita.duracionMin ? `Consulta de ${cita.duracionMin} min` : "Consulta"}
          </span>
        </div>
      </div>
      <div className="pase-corte" aria-hidden="true"><span /></div>
      <dl className="pase-datos">
        <div>
          <dt><UserRound size={20} aria-hidden="true" /><span className="sr-only">Médico</span></dt>
          <dd>
            <strong>{cita.medico || "Tu médico"}</strong>
            {cita.especialidad && <span>{cita.especialidad}</span>}
          </dd>
        </div>
        {(cita.consultorio || cita.direccion) && (
          <div>
            <dt><MapPin size={20} aria-hidden="true" /><span className="sr-only">Lugar</span></dt>
            <dd>
              <strong>{cita.consultorio || cita.clinica}</strong>
              {cita.direccion && <span>{cita.direccion}</span>}
            </dd>
          </div>
        )}
      </dl>
    </article>
  );
}

function Resultado({ resultado, cita }) {
  const confirmada = resultado === "CONFIRMADA";
  return (
    <div className="pase-acciones">
      <div className={`pase-resultado ${confirmada ? "ok" : "cancelada"}`} role="status">
        {confirmada ? <CheckCircle2 size={22} /> : <CalendarX size={22} />}
        <span>
          {confirmada
            ? "Tu asistencia quedó confirmada. Si surge algún cambio, comunícate con la clínica."
            : "Avisamos a la clínica. Si deseas reagendar, comunícate con ellos."}
        </span>
      </div>
      <LlamarClinica telefono={cita.telefonoClinica} />
    </div>
  );
}

function LlamarClinica({ telefono }) {
  const digitos = String(telefono || "").replace(/\D/g, "");
  if (digitos.length < 10) return null;
  return (
    <a className="pase-llamar" href={`tel:+52${digitos.slice(-10)}`}>
      <Phone size={16} aria-hidden="true" /> Llamar a la clínica
    </a>
  );
}

function EstadoError({ error }) {
  const usado = error.codigo === "USADO";
  return (
    <div className="pase-tarjeta pase-estado">
      {usado ? <CheckCircle2 size={40} className="pase-icono ok" /> : <AlertCircle size={40} className="pase-icono aviso" />}
      <p>{error.mensaje}</p>
    </div>
  );
}

const TITULOS = {
  NO_ENCONTRADO: "Enlace no válido",
  USADO: "Ya recibimos tu respuesta",
  REEMPLAZADO: "Este enlace ya no está activo",
  EXPIRADO: "Este enlace expiró",
  NO_DISPONIBLE: "Esta cita ya no se puede modificar",
  DEMASIADOS_INTENTOS: "Demasiados intentos",
  SIN_CONEXION: "Sin conexión",
};

async function llamar(method, token, body) {
  let response;
  try {
    response = await fetch(`/api/public/citas/${encodeURIComponent(token)}`, {
      method,
      headers: body ? { "Content-Type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw { codigo: "SIN_CONEXION", mensaje: "No pudimos conectar con la clínica. Revisa tu conexión e intenta de nuevo." };
  }
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    throw {
      codigo: data?.codigo || "ERROR",
      mensaje: data?.mensaje || "Ocurrió un problema. Intenta de nuevo en unos minutos o comunícate con la clínica.",
    };
  }
  return data;
}
