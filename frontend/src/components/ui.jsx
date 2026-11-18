import { Ban, Check, Clock, RefreshCw, Send, TriangleAlert, X } from "lucide-react";

/**
 * Estados legibles (nunca el valor crudo como SIN_CONFIRMAR). Cada estado lleva icono además del color,
 * para no depender solo del color (accesibilidad).
 */
const ESTADOS = {
  // Citas
  CONFIRMADA: { label: "Confirmada", clase: "confirmada", Icono: Check },
  SIN_CONFIRMAR: { label: "Por confirmar", clase: "pendiente", Icono: Clock },
  CANCELADA: { label: "Cancelada", clase: "cancelada", Icono: X },
  NO_ASISTIO: { label: "No asistió", clase: "noasistio", Icono: Ban },
  REAGENDADA: { label: "Reagendada", clase: "reagendada", Icono: RefreshCw },
  // Pagos
  PAGADO: { label: "Pagado", clase: "confirmada", Icono: Check },
  PENDIENTE: { label: "Pendiente", clase: "pendiente", Icono: Clock },
  // Notificaciones / envíos
  ENVIADO: { label: "Enviado", clase: "confirmada", Icono: Send },
  FALLIDO: { label: "Falló", clase: "cancelada", Icono: TriangleAlert },
  // Estudios
  SOLICITADO: { label: "Solicitado", clase: "pendiente", Icono: Clock },
  SUBIDO: { label: "Subido", clase: "reagendada", Icono: Check },
  REVISADO: { label: "Revisado", clase: "confirmada", Icono: Check },
  // Otros catálogos
  PRINCIPAL: { label: "Principal", clase: "confirmada" },
  SECUNDARIO: { label: "Secundario", clase: "reagendada" },
  ADMIN: { label: "Administración", clase: "reagendada" },
  MEDICO: { label: "Médico", clase: "reagendada" },
  PACIENTE: { label: "Paciente", clase: "noasistio" },
  // Catálogos sin icono (solo etiqueta legible en selects y tablas)
  RECORDATORIO_48H: { label: "Recordatorio 48 h", clase: "neutro" },
  RECORDATORIO_24H: { label: "Recordatorio 24 h", clase: "neutro" },
  RECORDATORIO_MANUAL: { label: "Recordatorio manual", clase: "neutro" },
  CONFIRMACION_PACIENTE: { label: "Confirmación del paciente", clase: "confirmada", Icono: Check },
  CANCELACION_PACIENTE: { label: "Cancelación del paciente", clase: "cancelada", Icono: X },
  WHATSAPP: { label: "WhatsApp", clase: "neutro" },
  CLINICA: { label: "Clínica", clase: "neutro" },
};

/** "Destello del clic": tres rayitas que marcan lo que acaba de pasar (decorativo). */
export function Destello({ color = "var(--lima)", size = 22, className = "" }) {
  return (
    <svg className={className} aria-hidden="true" width={size} height={(size * 20) / 26} viewBox="0 0 26 20"
      fill="none" stroke={color} strokeWidth="2.4" strokeLinecap="round">
      <path d="M3 9L8 12M9 2l1.5 5.5M18 3l-3.5 5" />
    </svg>
  );
}

/** Etiqueta legible de un estado o catálogo: "SIN_CONFIRMAR" → "Por confirmar". */
export function etiquetaEstado(valor) {
  if (valor == null || valor === "") return "";
  const conocido = ESTADOS[String(valor).toUpperCase()];
  if (conocido) return conocido.label;
  const texto = String(valor).replace(/_/g, " ").toLowerCase();
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/** Pastilla de estado con icono y etiqueta legible. */
export function Estado({ valor, className = "" }) {
  if (valor == null || valor === "") return null;
  const info = ESTADOS[String(valor).toUpperCase()] || { label: etiquetaEstado(valor), clase: "neutro" };
  const { Icono } = info;
  return (
    <span className={`estado estado-${info.clase} ${className}`.trim()}>
      {Icono && <Icono size={14} strokeWidth={2.6} aria-hidden="true" />}
      {info.label}
    </span>
  );
}

export function ViewHeader({ title, subtitle, children }) {
  return (
    <header className="view-header">
      <div>
        <h2>{title}</h2>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {children && <div className="actions">{children}</div>}
    </header>
  );
}

export function Section({ title, badge, children }) {
  return (
    <section className="section">
      {(title || badge) && (
        <div className="section-head">
          {title ? <h3>{title}</h3> : <span />}
          {badge && <span className="badge info">{badge}</span>}
        </div>
      )}
      <div className="section-body">{children}</div>
    </section>
  );
}

export function Metric({ label, value, variant = "" }) {
  return (
    <article className={`metric ${variant}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

export function Loading({ label }) {
  return <div className="loading">{label}</div>;
}

export function Empty({ label }) {
  return <div className="empty">{label}</div>;
}

export function ErrorBox({ message }) {
  return <div className="error-box">{message}</div>;
}
