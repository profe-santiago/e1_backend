export function formatCell(value, type, support) {
  if (value == null || value === "") return <span className="muted">-</span>;
  if (type === "datetime") return formatDate(value);
  if (type === "money") return formatMoney(value);
  if (type === "bool") return <span className={`badge ${value ? "ok" : "danger"}`}>{value ? "SI" : "NO"}</span>;
  if (type === "status") return <span className={`badge ${statusVariant(value)}`}>{String(value)}</span>;
  if (type === "patient") return nameById("pacientes", value, support);
  if (type === "doctor") return nameById("medicos", value, support);
  if (type === "office") return nameById("consultorios", value, support);
  if (type === "appointment") return nameById("citas", value, support);
  if (type === "day") return ["Dom", "Lun", "Mar", "Mie", "Jue", "Vie", "Sab"][Number(value)] || value;
  return String(value).length > 42 ? <span className="mono">{shortId(value)}</span> : String(value);
}

export function statusVariant(value) {
  const text = String(value);
  if (["CONFIRMADA", "PAGADO", "PRINCIPAL", "ADMIN"].includes(text)) return "ok";
  if (["SIN_CONFIRMAR", "PENDIENTE", "REAGENDADA", "SECUNDARIO", "MEDICO"].includes(text)) return "warn";
  if (["CANCELADA", "NO_ASISTIO", "FALLIDO"].includes(text)) return "danger";
  return "info";
}

export function displayName(row, source, support) {
  if (source === "citas") return `${formatDate(row.fechaHora)} / ${nameById("pacientes", row.pacienteId, support)}`;
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

export function formatDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString("es-MX", { dateStyle: "medium", timeStyle: "short" });
}

export function formatTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "--:--";
  return date.toLocaleTimeString("es-MX", { hour: "2-digit", minute: "2-digit" });
}

export function formatMoney(value) {
  return `$${Number(value || 0).toLocaleString("es-MX", { minimumFractionDigits: 2 })}`;
}

export function isToday(value) {
  const date = new Date(value);
  const now = new Date();
  return !Number.isNaN(date.getTime()) && date.toDateString() === now.toDateString();
}

export function isFuture(value) {
  const date = new Date(value);
  return !Number.isNaN(date.getTime()) && date.getTime() >= Date.now();
}

export function formatTodayLabel() {
  return new Date().toLocaleDateString("es-MX", { weekday: "long", day: "numeric", month: "long" });
}

export function formatWeekday(value) {
  return ["Domingo", "Lunes", "Martes", "Miercoles", "Jueves", "Viernes", "Sabado"][Number(value)] || value;
}

export function sliceTime(value) {
  return String(value || "").slice(0, 5) || "--:--";
}
