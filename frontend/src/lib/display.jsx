import { Estado, etiquetaEstado } from "../components/ui";
export function formatCell(value, type, support) {
  if (value == null || value === "") return <span className="muted">-</span>;
  if (type === "datetime") return <span className="nowrap">{formatDate(value)}</span>;
  if (type === "instant") return formatInstant(value);
  if (type === "money") return formatMoney(value);
  if (type === "bool") return <span className={`badge ${value ? "ok" : "danger"}`}>{value ? "Sí" : "No"}</span>;
  if (type === "status") return <Estado valor={value} />;
  if (type === "patient") return nameById("pacientes", value, support);
  if (type === "doctor") return nameById("medicos", value, support);
  if (type === "office") return nameById("consultorios", value, support);
  if (type === "appointment") return nameById("citas", value, support);
  if (type === "hora") return formatHoraTexto(value);
  if (type === "label") return etiquetaEstado(value);
  if (type === "day") return ["Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"][Number(value)] || value;
  // Solo los identificadores (UUID) se acortan; los textos largos (p. ej. direcciones) se muestran completos
  return /^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(String(value)) ? <span className="mono">{shortId(value)}</span> : String(value);
}

export function statusVariant(value) {
  const text = String(value);
  if (["CONFIRMADA", "PAGADO", "PRINCIPAL", "ADMIN"].includes(text)) return "ok";
  if (["SIN_CONFIRMAR", "PENDIENTE", "REAGENDADA", "SECUNDARIO", "MEDICO"].includes(text)) return "warn";
  if (["CANCELADA", "NO_ASISTIO", "FALLIDO"].includes(text)) return "danger";
  return "info";
}

export function displayName(row, source, support) {
  if (source === "citas") return `${formatDate(row.fechaHora)} · ${nameById("pacientes", row.pacienteId, support)}`;
  if (source === "usuarios") return row.email || row.id;
  if (source === "adjuntos") return row.nombreArchivo || row.id;
  return row.nombre || row.email || shortId(row.id);
}

export function nameById(source, id, support) {
  const row = (support[source] || []).find((item) => String(item.id) === String(id));
  return row ? displayName(row, source, support) : shortId(id);
}

export function initials(value) {
  return String(value || "MI")
    .split(/[ @._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

export function sessionDisplayName(session) {
  const value =
    session?.nombre ||
    session?.displayName ||
    session?.nombreUsuario ||
    session?.claims?.nombre ||
    session?.claims?.name ||
    session?.email ||
    session?.claims?.sub ||
    "Usuario";

  if (String(value).includes("@")) {
    return String(value).split("@")[0].replace(/[._-]+/g, " ");
  }

  return String(value);
}

export function shortId(value) {
  const text = String(value || "");
  return text.length > 12 ? `${text.slice(0, 8)}...` : text || "-";
}

// ── Fechas y horas ────────────────────────────────────────────────────────────
// Convención del sistema: la hora de una cita se guarda como hora de reloj de la
// clínica expresada en UTC (10:00 en la clínica → "10:00Z"). Por eso las fechas de
// citas se formatean SIEMPRE con timeZone "UTC", y "ahora"/"hoy" se calculan con el
// reloj de la clínica (clinicNow / clinicToday), no con el reloj real del navegador.

export const CLINIC_TIME_ZONE = "America/Mexico_City";
export const UTC = "UTC";

/** Hora de reloj actual de la clínica, como Date en la convención UTC de las citas. */
export function clinicNow() {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en-US", {
      timeZone: CLINIC_TIME_ZONE,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hourCycle: "h23",
    })
      .formatToParts(new Date())
      .map((part) => [part.type, part.value])
  );
  return new Date(Date.UTC(+parts.year, +parts.month - 1, +parts.day, +parts.hour, +parts.minute, +parts.second));
}

/** Fecha de hoy en la clínica, "YYYY-MM-DD". */
export function clinicToday() {
  return clinicNow().toISOString().slice(0, 10);
}

/** Fecha "YYYY-MM-DD" de una cita (parte UTC). */
export function citaDateKey(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toISOString().slice(0, 10);
}

/** Hora "HH:MM" (24 h) de una cita (parte UTC). */
export function citaTimeKey(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toISOString().slice(11, 16);
}

/** Suma días a una fecha "YYYY-MM-DD" sin depender de la zona del navegador. */
export function addDaysKey(dateKey, days) {
  const date = new Date(`${dateKey}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** Fecha y hora de una cita. */
// ── Horas: UN solo formato en toda la app, 12 h sin cero inicial: "9:30 a.m.", "1:00 p.m." ──

/** "9:30 a.m." a partir de hora (0–23) y minuto. */
export function hora12(h, m) {
  const hh = h % 12 === 0 ? 12 : h % 12;
  return `${hh}:${String(m).padStart(2, "0")} ${h < 12 ? "a.m." : "p.m."}`;
}

/** "1994-03-18" → "18 mar 1994" (fecha de calendario, sin zona). */
export function formatFechaCorta(value) {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(value || ""));
  if (!m) return value ? String(value) : "-";
  const d = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]), 12));
  return d.toLocaleDateString("es-MX", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" }).replace(".", "");
}

/** "F" → "Femenino", "M" → "Masculino", "O" → "Otro". */
export function formatSexo(value) {
  return { F: "Femenino", M: "Masculino", O: "Otro" }[String(value || "").toUpperCase()] || (value || "-");
}

/** Rol legible: "Administración", "Médica"/"Médico" (según el nombre) o "Paciente". */
export function rolLegible(rol, nombre = "") {
  if (rol === "ADMIN") return "Administración";
  if (rol === "PACIENTE") return "Paciente";
  if (rol === "MEDICO") return /^dra\.?\s/i.test(String(nombre).trim()) ? "Médica" : "Médico";
  return rol || "";
}

/**
 * Texto de un mensaje tal como se MUESTRA en pantalla (el mensaje real guardado o enviado no cambia):
 * oculta el token del enlace personal /c/{token} y unifica la hora de mensajes antiguos ("9:30 a. m." → "9:30 a.m.").
 */
export function enmascararEnlaces(texto) {
  return String(texto || "")
    .replace(/(\/c\/)[A-Za-z0-9_-]{12,}/g, "$1••••••")
    .replace(/(\d) ([ap])\. m\./g, "$1 $2.m.");
}

/** Hora "HH:MM" o "HH:MM:SS" (horarios, horas disponibles) → "9:30 a.m.". */
export function formatHoraTexto(value) {
  const m = /^(\d{1,2}):(\d{2})/.exec(String(value || ""));
  return m ? hora12(Number(m[1]), Number(m[2])) : String(value || "");
}

/** Hora de un INSTANTE real (createdAt, enviadaEn...) en una zona; por defecto, la del navegador. */
export function formatHoraInstante(value, timeZone) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const p = Object.fromEntries(
    new Intl.DateTimeFormat("en-US", { hour: "numeric", minute: "2-digit", hourCycle: "h23", timeZone })
      .formatToParts(date)
      .map((x) => [x.type, x.value])
  );
  return hora12(Number(p.hour) % 24, Number(p.minute));
}

/** Fecha y hora de una cita: "19 nov 2026, 9:30 a.m." (convención de reloj en UTC). */
export function formatDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const fecha = date.toLocaleDateString("es-MX", { day: "numeric", month: "short", year: "numeric", timeZone: UTC }).replace(".", "");
  return `${fecha}, ${hora12(date.getUTCHours(), date.getUTCMinutes())}`;
}

/** Hora de una cita. */
export function formatTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "--:--";
  return hora12(date.getUTCHours(), date.getUTCMinutes());
}

/** Fecha corta "dd/mm/aaaa" de una cita. */
export function formatCitaFecha(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleDateString("es-MX", { day: "2-digit", month: "2-digit", year: "numeric", timeZone: UTC });
}

/** Hora 12 h ("10:00 a.m.") de una cita. */
export function formatCitaHora12(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "--:--";
  return hora12(date.getUTCHours(), date.getUTCMinutes());
}

/** Momento real (createdAt, enviadaEn...): se muestra en la hora local del navegador. */
export function formatInstant(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const fecha = date.toLocaleDateString("es-MX", { day: "numeric", month: "short", year: "numeric" }).replace(".", "");
  return `${fecha}, ${formatHoraInstante(date)}`;
}

export function formatMoney(value) {
  return `$${Number(value || 0).toLocaleString("es-MX", { minimumFractionDigits: 2 })}`;
}

/** La cita es de hoy en la clínica. */
export function isToday(value) {
  return citaDateKey(value) === clinicToday();
}

/** La cita aún no ocurre según el reloj de la clínica. */
export function isFuture(value) {
  const date = new Date(value);
  return !Number.isNaN(date.getTime()) && date.getTime() >= clinicNow().getTime();
}

export function formatTodayLabel() {
  return new Date().toLocaleDateString("es-MX", { weekday: "long", day: "numeric", month: "long", timeZone: CLINIC_TIME_ZONE });
}

export function formatWeekday(value) {
  return ["Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"][Number(value)] || value;
}

export function sliceTime(value) {
  return String(value || "").slice(0, 5) || "--:--";
}
