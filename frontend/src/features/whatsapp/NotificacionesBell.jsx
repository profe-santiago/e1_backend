import React from "react";
import { Bell } from "lucide-react";
import { formatCitaFecha, formatCitaHora12, formatInstant } from "../../lib/display";
import "./WhatsApp.css";

// Mismo evento que ActividadView: la campana y Actividad comparten el estado de leído (tabla notificacion)
const EVENTO_NOTIFICACIONES = "medinflow:notificaciones";
const avisar = () => window.dispatchEvent(new CustomEvent(EVENTO_NOTIFICACIONES));

const REFRESCO_MS = 30000;

/**
 * Campana con las respuestas del paciente y contador de no leídas: cancelaciones (CANCELACION_PACIENTE,
 * por WhatsApp o desde el enlace) y confirmaciones desde el enlace (CONFIRMACION_PACIENTE).
 */
export default function NotificacionesBell({ context }) {
  const [datos, setDatos] = React.useState({ noLeidas: 0, items: [] });
  const [abierto, setAbierto] = React.useState(false);
  const ref = React.useRef(null);
  const ultimoId = React.useRef(null);

  const cargar = React.useCallback(async () => {
    try {
      const nuevos = (await context.api("/api/notificaciones/recientes")) || { noLeidas: 0, items: [] };
      setDatos(nuevos);
      // Llegó una respuesta nueva: que Actividad (si está abierta) se refresque sin esperar su ciclo
      const primero = nuevos.items?.[0]?.id || null;
      if (ultimoId.current !== null && primero && primero !== ultimoId.current) avisar();
      ultimoId.current = primero || "";
    } catch {
      // silencioso: la campana no debe romper el layout
    }
  }, [context]);

  React.useEffect(() => {
    cargar();
    const id = window.setInterval(cargar, REFRESCO_MS);
    const alVolver = () => document.visibilityState === "visible" && cargar();
    document.addEventListener("visibilitychange", alVolver);
    window.addEventListener(EVENTO_NOTIFICACIONES, cargar);
    return () => {
      window.clearInterval(id);
      document.removeEventListener("visibilitychange", alVolver);
      window.removeEventListener(EVENTO_NOTIFICACIONES, cargar);
    };
  }, [cargar]);

  React.useEffect(() => {
    if (!abierto) return undefined;
    const cerrar = (event) => {
      if (ref.current && !ref.current.contains(event.target)) setAbierto(false);
    };
    const escape = (event) => event.key === "Escape" && setAbierto(false);
    document.addEventListener("mousedown", cerrar);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("mousedown", cerrar);
      document.removeEventListener("keydown", escape);
    };
  }, [abierto]);

  async function marcarLeida(item) {
    if (item.leida) return;
    try {
      await context.api(`/api/notificaciones/${item.id}/leida`, { method: "PATCH" });
      avisar();
    } catch (err) {
      context.notify(err.message, "error");
    }
  }

  async function marcarTodas() {
    try {
      await context.api("/api/notificaciones/leidas", { method: "POST" });
      avisar();
    } catch (err) {
      context.notify(err.message, "error");
    }
  }

  return (
    <div className="bell" ref={ref}>
      <button
        type="button"
        className="btn icon ghost bell-btn"
        aria-label={`Notificaciones${datos.noLeidas ? `, ${datos.noLeidas} sin leer` : ""}`}
        aria-expanded={abierto}
        onClick={() => setAbierto((v) => !v)}
      >
        <Bell size={19} />
        {datos.noLeidas > 0 && <span className="bell-contador">{datos.noLeidas > 9 ? "9+" : datos.noLeidas}</span>}
      </button>
      {abierto && (
        <div className="bell-panel" role="dialog" aria-label="Respuestas recientes de pacientes">
          <div className="bell-head">
            <strong>Respuestas de pacientes</strong>
            {datos.noLeidas > 0 && (
              <button type="button" className="btn small ghost" onClick={marcarTodas}>Marcar todas</button>
            )}
          </div>
          {datos.items.length === 0 ? (
            <p className="bell-vacio">Sin respuestas recientes.</p>
          ) : (
            <ul className="bell-lista">
              {datos.items.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    className={`bell-item ${item.leida ? "" : "nueva"} ${item.tipo === "CONFIRMACION_PACIENTE" ? "confirmacion" : "cancelacion"}`}
                    onClick={() => marcarLeida(item)}
                  >
                    <strong>
                      {item.pacienteNombre || "Paciente"}{" "}
                      {item.tipo === "CONFIRMACION_PACIENTE" ? "confirmó su asistencia" : "canceló su cita"}
                    </strong>
                    {item.respuesta && /enlace/i.test(item.respuesta) && <em className="bell-origen">Desde el enlace</em>}
                    <span>
                      {item.citaFechaHora ? `${formatCitaFecha(item.citaFechaHora)} · ${formatCitaHora12(item.citaFechaHora)}` : ""}
                      {item.medicoNombre ? ` · ${item.medicoNombre}` : ""}
                    </span>
                    <small>{formatInstant(item.creadaEn)}</small>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
