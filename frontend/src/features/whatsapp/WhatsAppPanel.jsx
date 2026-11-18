import React from "react";
import { enmascararEnlaces } from "../../lib/display";
import { AlertTriangle, Check, CheckCheck, RefreshCw, Send, Smartphone } from "lucide-react";
import { Empty, ErrorBox, Loading, ViewHeader } from "../../components/ui";
import { formatHoraLocal } from "./RecordatorioWhatsApp";
import "./WhatsApp.css";

const REFRESCO_MS = 15000;

/** Conversaciones de WhatsApp con aspecto de chat de celular; simulador si el canal es "simulado". */
export default function WhatsAppPanel({ context }) {
  const [canal, setCanal] = React.useState(null);
  const [conversaciones, setConversaciones] = React.useState(null);
  const [seleccion, setSeleccion] = React.useState(null);
  const [mensajes, setMensajes] = React.useState([]);
  const [error, setError] = React.useState("");
  const [tick, setTick] = React.useState(0);

  React.useEffect(() => {
    context.api("/api/whatsapp/canal").then(setCanal).catch(() => setCanal({ proveedor: "?", simulado: false }));
  }, [context]);

  React.useEffect(() => {
    const id = window.setInterval(() => setTick((t) => t + 1), REFRESCO_MS);
    return () => window.clearInterval(id);
  }, []);

  React.useEffect(() => {
    let activo = true;
    context
      .api("/api/whatsapp/conversaciones/resumen")
      .then((data) => {
        if (!activo) return;
        setError("");
        setConversaciones(data || []);
        setSeleccion((actual) => actual || data?.[0]?.telefono || null);
      })
      .catch((err) => activo && setError(err.message));
    return () => {
      activo = false;
    };
  }, [context, tick]);

  const cargarMensajes = React.useCallback(async () => {
    if (!seleccion) {
      setMensajes([]);
      return;
    }
    try {
      setMensajes((await context.api(`/api/whatsapp/conversaciones?telefono=${encodeURIComponent(seleccion)}`)) || []);
    } catch (err) {
      context.notify(err.message, "error");
    }
  }, [context, seleccion]);

  React.useEffect(() => {
    cargarMensajes();
  }, [cargarMensajes, tick]);

  async function responderComoPaciente(texto) {
    const data = await context.api("/api/whatsapp/simulador/responder", {
      method: "POST",
      body: JSON.stringify({ telefono: seleccion, texto }),
    });
    setMensajes(data || []);
    setTick((t) => t + 1);
  }

  const actual = conversaciones?.find((c) => c.telefono === seleccion);

  return (
    <>
      <ViewHeader title="WhatsApp" subtitle="Recordatorios y respuestas de los pacientes">
        {canal && (
          <span className={`badge ${canal.simulado ? "warn" : "info"}`}>
            {canal.simulado ? "Canal de pruebas" : `Canal: ${nombreProveedor(canal.proveedor)}`}
          </span>
        )}
        <button className="btn icon" title="Actualizar" onClick={() => setTick((t) => t + 1)}>
          <RefreshCw size={18} />
        </button>
      </ViewHeader>

      {error && <ErrorBox message={error} />}
      {!conversaciones && !error && <Loading label="Cargando conversaciones..." />}

      {conversaciones && (
        <div className="wa-layout">
          <aside className="wa-lista" aria-label="Conversaciones">
            {conversaciones.length === 0 && (
              <Empty label="Aún no hay mensajes. Envía un recordatorio desde la agenda o el panel." />
            )}
            {conversaciones.map((c) => (
              <button
                key={c.telefono}
                type="button"
                className={`wa-conv ${c.telefono === seleccion ? "activa" : ""}`}
                onClick={() => setSeleccion(c.telefono)}
              >
                <span className="wa-avatar" aria-hidden="true">{iniciales(c.pacienteNombre || c.telefono)}</span>
                <span className="wa-conv-info">
                  <strong>{c.pacienteNombre || formatTelefono(c.telefono)}</strong>
                  <small>
                    {c.ultimaDireccion === "SALIENTE" ? "Tú: " : ""}
                    {c.ultimoTexto}
                  </small>
                </span>
                <time className="wa-conv-hora">{formatHoraLocal(c.ultimaFecha)}</time>
              </button>
            ))}
          </aside>

          <section className="wa-telefono" aria-label="Conversación">
            {!actual ? (
              <div className="wa-vacio">
                <Smartphone size={28} />
                <span>Selecciona una conversación</span>
              </div>
            ) : (
              <>
                <header className="wa-cabecera">
                  <span className="wa-avatar" aria-hidden="true">{iniciales(actual.pacienteNombre || actual.telefono)}</span>
                  <div>
                    <strong>{actual.pacienteNombre || "Paciente"}</strong>
                    <small>{formatTelefono(actual.telefono)}</small>
                  </div>
                  {canal?.simulado && <span className="wa-etiqueta">Canal de pruebas</span>}
                </header>
                <Mensajes mensajes={mensajes} />
                {canal?.simulado && <Simulador onEnviar={responderComoPaciente} notify={context.notify} />}
              </>
            )}
          </section>
        </div>
      )}
    </>
  );
}

function Mensajes({ mensajes }) {
  const ref = React.useRef(null);
  React.useEffect(() => {
    if (ref.current) ref.current.scrollTop = ref.current.scrollHeight;
  }, [mensajes]);

  return (
    <div className="wa-mensajes" ref={ref}>
      {mensajes.length === 0 && <div className="wa-sin-mensajes">Sin mensajes</div>}
      {mensajes.map((m) => (
        <div key={m.id} className={`wa-burbuja ${m.direccion === "SALIENTE" ? "saliente" : "entrante"}`}>
          <p>{enmascararEnlaces(m.texto)}</p>
          <span className="wa-meta">
            {formatHoraLocal(m.createdAt)}
            {m.direccion === "SALIENTE" && m.canal === "enlace" && m.estado !== "FALLIDO" && (
              <span className="wa-enlace-etiqueta">Enlace enviado</span>
            )}
            {m.direccion === "SALIENTE" && <EstadoTicks estado={m.estado} />}
          </span>
        </div>
      ))}
    </div>
  );
}

function EstadoTicks({ estado }) {
  if (estado === "FALLIDO") return <AlertTriangle size={13} className="wa-tick fallido" aria-label="Falló" />;
  if (estado === "LEIDO") return <CheckCheck size={14} className="wa-tick leido" aria-label="Leído" />;
  if (estado === "ENTREGADO") return <CheckCheck size={14} className="wa-tick" aria-label="Entregado" />;
  return <Check size={14} className="wa-tick" aria-label="Enviado" />;
}

function Simulador({ onEnviar, notify }) {
  const [texto, setTexto] = React.useState("");
  const [enviando, setEnviando] = React.useState(false);

  async function enviar(valor) {
    const limpio = valor.trim();
    if (!limpio) return;
    setEnviando(true);
    try {
      await onEnviar(limpio);
      setTexto("");
    } catch (err) {
      notify(err.message, "error");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form
      className="wa-simulador"
      onSubmit={(event) => {
        event.preventDefault();
        enviar(texto);
      }}
    >
      <div className="wa-rapidos">
        <span>Responder como paciente:</span>
        <button type="button" className="btn small" disabled={enviando} onClick={() => enviar("1")}>1 · Confirmar</button>
        <button type="button" className="btn small" disabled={enviando} onClick={() => enviar("2")}>2 · Cancelar</button>
      </div>
      <div className="wa-entrada">
        <input
          value={texto}
          onChange={(event) => setTexto(event.target.value)}
          placeholder="Escribe como paciente..."
          aria-label="Mensaje del paciente"
          maxLength={1000}
        />
        <button className="btn icon primary" disabled={enviando || !texto.trim()} title="Enviar">
          <Send size={16} />
        </button>
      </div>
    </form>
  );
}

function iniciales(texto) {
  return String(texto || "?")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join("")
    .toUpperCase();
}

function formatTelefono(tel) {
  const d = String(tel || "");
  return d.length === 10 ? `${d.slice(0, 3)} ${d.slice(3, 6)} ${d.slice(6)}` : d;
}

function nombreProveedor(p) {
  return { twilio: "Twilio", meta: "Meta", intoglobe: "InToGlobe", enlace: "Enlace (wa.me)" }[p] || p;
}
