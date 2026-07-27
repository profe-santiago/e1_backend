import React from "react";
import { createRoot } from "react-dom/client";
import { createPortal } from "react-dom";
import {
  Activity,
  Bot,
  Building2,
  CalendarDays,
  CalendarPlus,
  Clock3,
  CreditCard,
  FileText,
  LayoutDashboard,
  LogOut,
  Menu,
  Power,
  RefreshCw,
  Save,
  Search,
  Settings,
  Stethoscope,
  Trash2,
  Upload,
  UserCog,
  Users,
  X,
} from "lucide-react";
import MedicoAgenda from "./features/medico/agenda/MedicoAgenda";
import MedicoConsultaDetalle from "./features/medico/consulta/MedicoConsultaDetalle";
import MedicoDashboard from "./features/medico/dashboard/MedicoDashboard";
import MedicoDiagnosticosAdjuntos from "./features/medico/diagnosticos-adjuntos/MedicoDiagnosticosAdjuntos";
import MedicoExpedientePaciente from "./features/medico/expediente/MedicoExpedientePaciente";
import PacienteAgendarCita from "./features/paciente/agendar/PacienteAgendarCita";
import PacienteCitas from "./features/paciente/citas/PacienteCitas";
import PacienteDashboard from "./features/paciente/dashboard/PacienteDashboard";
import "./styles.css";

const STORAGE_KEY = "medinflow.session";
const API_KEY = "medinflow.apiUrl";
const DEFAULT_API_URL = normalizeApiUrl(import.meta.env.VITE_API_URL || "http://localhost:8081");

const iconMap = {
  dashboard: LayoutDashboard,
  citas: CalendarDays,
  pacientes: Users,
  medicos: Stethoscope,
  consultorios: Building2,
  horarios: Clock3,
  pagos: CreditCard,
  diagnosticos: FileText,
  adjuntos: Upload,
  notificaciones: Activity,
  usuarios: UserCog,
  organizaciones: Building2,
  medicoClinico: FileText,
  pacienteAgendar: CalendarPlus,
  pacienteCitas: CalendarDays,
  ia: Bot,
  settings: Settings,
};

const modules = {
  citas: {
    label: "Citas",
    roles: ["ADMIN", "MEDICO"],
    description: "Agenda médica por organización, médico, paciente y estado.",
    clientFiltered: true,
    list: ({ orgId, role, medicoId }) => role === "MEDICO" && medicoId ? `/api/citas/medico/${medicoId}` : `/api/citas/organizacion/${orgId}`,
    endpoint: "/api/citas",
    columns: [
      ["fechaHora", "Fecha", "datetime"],
      ["pacienteId", "Paciente", "patient"],
      ["medicoId", "Medico", "doctor"],
      ["estado", "Estado", "status"],
      ["motivo", "Motivo", "motivo"],
    ],
    filters: [
      { key: "pacienteId", label: "Paciente", type: "combobox", source: "pacientes", live: true },
      { key: "medicoId", label: "Medico", type: "combobox", source: "medicos", live: true, roles: ["ADMIN"] },
      { key: "estado", label: "Estado", options: ["", "SIN_CONFIRMAR", "CONFIRMADA", "CANCELADA", "REAGENDADA", "NO_ASISTIO"], live: true },
    ],
    fields: [
      { key: "organizacionId", type: "hiddenOrg", required: true },
      { key: "pacienteId", label: "Paciente", source: "pacientes", required: true },
      { key: "medicoId", label: "Medico", source: "medicos", required: true },
      { key: "consultorioId", label: "Consultorio", source: "consultorios", required: true },
      { key: "duracionMin", label: "Duración", type: "select", options: ["20", "30", "45", "60"], required: true },
      { key: "fechaHora", label: "Fecha y hora", type: "datetime-local", required: true, full: true },
      { key: "motivo", label: "Motivo", type: "textarea", full: true },
    ],
  },
  pacientes: {
    label: "Pacientes",
    roles: ["ADMIN", "MEDICO"],
    description: "Expedientes básicos de pacientes por organización.",
    clientFiltered: true,
    list: ({ orgId }) => `/api/pacientes/organizacion/${orgId}`,
    support: ["citas"],
    clientFilter: (rows, filters, role, support) => {
      if (role !== "MEDICO") return rows;
      const patientIds = new Set((support.citas || []).map((cita) => String(cita.pacienteId)));
      return rows.filter((row) => patientIds.has(String(row.id)));
    },
    endpoint: "/api/pacientes",
    columns: [
      ["nombre", "Paciente"],
      ["telefono", "Telefono"],
      ["email", "Correo"],
      ["sexo", "Sexo"],
      ["activo", "Activo", "bool"],
    ],
    filters: [
      { key: "selectedId", label: "Buscar nombre", type: "combobox", source: "pacientes", live: true },
      { key: "activos", label: "Solo activos", type: "checkbox", live: true },
    ],
    fields: [
      { key: "organizacionId", type: "hiddenOrg", required: true },
      { key: "nombre", label: "Nombre", required: true, onlyLetters: true, value: (item) => patientNamePart(item.nombre, 0) },
      { key: "apellidoPaterno", label: "Apellido paterno", onlyLetters: true, value: (item) => patientNamePart(item.nombre, 1) },
      { key: "apellidoMaterno", label: "Apellido materno", onlyLetters: true, value: (item) => patientNamePart(item.nombre, 2) },
      { key: "telefono", label: "Telefono", onlyDigits: true, maxLength: 10 },
      { key: "fechaNacimiento", label: "Nacimiento", type: "date" },
      { key: "sexo", label: "Sexo", type: "select", options: ["", "M", "F", "O"] },
      { key: "email", label: "Correo", type: "email", emailLocalMax: 64 },
      { key: "notas", label: "Notas", type: "textarea", full: true },
    ],
    transformPayload: (payload) => {
      payload.nombre = [payload.nombre, payload.apellidoPaterno, payload.apellidoMaterno]
        .map((value) => String(value || "").trim())
        .filter(Boolean)
        .join(" ");
      delete payload.apellidoPaterno;
      delete payload.apellidoMaterno;
      return payload;
    },
  },
  medicos: {
    label: "Médicos",
    roles: ["ADMIN"],
    description: "Directorio médico y tarifas base.",
    clientFiltered: true,
    list: ({ orgId }) => `/api/medicos/organizacion/${orgId}`,
    endpoint: "/api/medicos",
    columns: [
      ["nombre", "Medico"],
      ["especialidad", "Especialidad"],
      ["consultorioId", "Consultorio", "office"],
      ["tarifaBase", "Tarifa", "money"],
      ["activo", "Activo", "bool"],
    ],
    filters: [
      { key: "selectedId", label: "Buscar médico", type: "combobox", source: "medicos", live: true },
      { key: "consultorioId", label: "Consultorio", type: "combobox", source: "consultorios", live: true },
      { key: "activos", label: "Estado", type: "checkbox", live: true },
    ],
    fields: [
      { key: "organizacionId", type: "hiddenOrg", required: true },
      { key: "consultorioId", label: "Consultorio", source: "consultorios", required: true },
      { key: "nombre", label: "Nombre", required: true, onlyLetters: true },
      { key: "especialidad", label: "Especialidad" },
      { key: "cedula", label: "Cedula" },
      { key: "telefono", label: "Telefono", onlyDigits: true, maxLength: 10 },
      { key: "tarifaBase", label: "Tarifa base", type: "number", min: 0 },
    ],
  },
  consultorios: {
    label: "Consultorios",
    roles: ["ADMIN"],
    description: "Sedes, consultorios y teléfonos de contacto.",
    clientFiltered: true,
    list: ({ orgId }) => `/api/consultorios/organizacion/${orgId}`,
    endpoint: "/api/consultorios",
    columns: [
      ["nombre", "Consultorio"],
      ["direccion", "Direccion"],
      ["telefono", "Telefono"],
      ["activo", "Activo", "bool"],
    ],
    filters: [
      { key: "selectedId", label: "Buscar consultorio", type: "combobox", source: "consultorios", live: true },
      { key: "activos", label: "Estado", type: "checkbox", live: true },
    ],
    fields: [
      { key: "organizacionId", type: "hiddenOrg", required: true },
      { key: "nombre", label: "Nombre", required: true, onlyLetters: true },
      { key: "direccion", label: "Direccion", type: "textarea", full: true },
      { key: "telefono", label: "Telefono", onlyDigits: true, maxLength: 10 },
    ],
  },
  horarios: {
    label: "Horarios",
    roles: ["ADMIN"],
    description: "Disponibilidad semanal por médico.",
    list: ({ filters }) => (filters.medicoId ? `/api/horarios/medico/${filters.medicoId}` : null),
    endpoint: "/api/horarios",
    columns: [
      ["medicoId", "Medico", "doctor"],
      ["diaSemana", "Dia", "day"],
      ["horaInicio", "Inicio"],
      ["horaFin", "Fin"],
      ["duracionConsulta", "Duracion"],
    ],
    filters: [{ key: "medicoId", label: "Medico", type: "combobox", source: "medicos", live: true }],
    fields: [
      { key: "medicoId", label: "Medico", source: "medicos", required: true },
      { key: "diaSemana", label: "Dia", type: "select", options: [
        { value: "0", label: "Domingo" },
        { value: "1", label: "Lunes" },
        { value: "2", label: "Martes" },
        { value: "3", label: "Miercoles" },
        { value: "4", label: "Jueves" },
        { value: "5", label: "Viernes" },
        { value: "6", label: "Sabado" },
      ], required: true },
      { key: "horaInicio", label: "Hora inicio", type: "time", required: true },
      { key: "horaFin", label: "Hora fin", type: "time", required: true },
      { key: "duracionConsulta", label: "Duracion", type: "select", options: ["20", "30", "45", "60"], required: true },
    ],
  },
  pagos: {
    label: "Pagos",
    roles: ["ADMIN", "MEDICO"],
    description: "Cobros, métodos de pago y referencias.",
    list: ({ orgId, filters, role }) => {
      if (filters.citaId) return `/api/pagos/cita/${filters.citaId}`;
      if (filters.estado) return `/api/pagos/organizacion/${orgId}/estado/${filters.estado}`;
      return role === "PACIENTE" ? null : `/api/pagos/organizacion/${orgId}`;
    },
    support: ["citas"],
    clientFilter: (rows, filters, role, support) => {
      if (role !== "MEDICO") return rows;
      const citaIds = new Set((support.citas || []).map((cita) => String(cita.id)));
      return rows.filter((row) => citaIds.has(String(row.citaId)));
    },
    endpoint: "/api/pagos",
    columns: [
      ["citaId", "Cita", "appointment"],
      ["monto", "Monto", "money"],
      ["metodo", "Metodo"],
      ["estado", "Estado", "status"],
      ["referencia", "Referencia"],
    ],
    filters: [
      { key: "citaId", label: "Cita", source: "citas" },
      { key: "estado", label: "Estado", options: ["", "PAGADO", "PENDIENTE"] },
    ],
    fields: [
      { key: "organizacionId", type: "hiddenOrg", required: true },
      { key: "citaId", label: "Cita", source: "citas", required: true },
      { key: "monto", label: "Monto", type: "number", required: true },
      { key: "metodo", label: "Metodo", type: "select", options: ["EFECTIVO", "TRANSFERENCIA", "TARJETA"], required: true },
      { key: "concepto", label: "Concepto" },
      { key: "referencia", label: "Referencia" },
    ],
  },
  diagnosticos: {
    label: "Diagnósticos",
    roles: ["ADMIN"],
    description: "Diagnósticos clínicos asociados a citas.",
    list: ({ filters }) => {
      if (!filters.citaId) return null;
      return filters.tipo ? `/api/diagnosticos/cita/${filters.citaId}/tipo/${filters.tipo}` : `/api/diagnosticos/cita/${filters.citaId}`;
    },
    endpoint: "/api/diagnosticos",
    columns: [
      ["citaId", "Cita", "appointment"],
      ["codigoCie10", "CIE-10"],
      ["descripcion", "Descripcion"],
      ["tipo", "Tipo", "status"],
      ["createdAt", "Creado", "datetime"],
    ],
    filters: [
      { key: "pacienteId", label: "Paciente", source: "pacientes" },
      { key: "citaId", label: "Cita", source: "citas" },
      { key: "tipo", label: "Tipo", options: ["", "PRINCIPAL", "SECUNDARIO"] },
    ],
    fields: [
      { key: "citaId", label: "Cita", source: "citas", required: true },
      { key: "_medicoRef", source: "medicos" },
      { key: "codigoCie10", label: "Codigo CIE-10" },
      { key: "tipo", label: "Tipo", type: "select", options: ["PRINCIPAL", "SECUNDARIO"] },
      { key: "descripcion", label: "Descripcion", type: "textarea", full: true, required: true },
    ],
  },
  adjuntos: {
    label: "Adjuntos",
    roles: ["ADMIN", "MEDICO", "PACIENTE"],
    description: "Metadatos de archivos clínicos y documentos.",
    list: ({ filters }) => {
      if (filters.citaId) return `/api/adjuntos/cita/${filters.citaId}`;
      if (filters.pacienteId) return `/api/adjuntos/paciente/${filters.pacienteId}`;
      return null;
    },
    endpoint: "/api/adjuntos",
    columns: [
      ["nombreArchivo", "Archivo"],
      ["tipo", "Tipo"],
      ["pacienteId", "Paciente", "patient"],
      ["mimeType", "MIME"],
      ["notificar", "Notificar", "bool"],
    ],
    filters: [
      { key: "pacienteId", label: "Paciente", source: "pacientes", required: true },
      { key: "citaId", label: "Cita", source: "citas" },
    ],
    fields: [
      { key: "organizacionId", type: "hiddenOrg", required: true },
      { key: "pacienteId", label: "Paciente", source: "pacientes", required: true },
      { key: "citaId", label: "Cita", source: "citas" },
      { key: "subidoPorId", type: "hiddenUser", required: true },
      { key: "tipo", label: "Tipo", required: true },
      { key: "nombreArchivo", label: "Nombre del archivo", required: true },
      { key: "urlArchivo", label: "URL del archivo", full: true, required: true },
      { key: "mimeType", label: "MIME" },
      { key: "notificar", label: "Notificar", type: "checkbox" },
    ],
  },
  notificaciones: {
    label: "Notificaciones",
    roles: ["ADMIN", "MEDICO"],
    description: "Recordatorios y avisos ligados a citas.",
    list: ({ filters, role }) => {
      if (filters.citaId) return `/api/notificaciones/cita/${filters.citaId}`;
      return role === "ADMIN" ? "/api/notificaciones/pendientes" : null;
    },
    endpoint: "/api/notificaciones",
    columns: [
      ["citaId", "Cita", "appointment"],
      ["canal", "Canal"],
      ["tipo", "Tipo"],
      ["estado", "Estado", "status"],
      ["enviadaEn", "Enviada", "datetime"],
    ],
    filters: [{ key: "citaId", label: "Cita", source: "citas" }],
    fields: [
      { key: "organizacionId", type: "hiddenOrg", required: true },
      { key: "citaId", label: "Cita", source: "citas", required: true },
      { key: "adjuntoId", label: "Adjunto", source: "adjuntos" },
      { key: "canal", label: "Canal", type: "select", options: ["WHATSAPP"], required: true },
      { key: "tipo", label: "Tipo", type: "select", options: ["RECORDATORIO_48H", "RECORDATORIO_24H", "ADJUNTO"], required: true },
    ],
  },
  usuarios: {
    label: "Usuarios",
    roles: ["ADMIN"],
    description: "Cuentas internas para administradores y médicos.",
    list: ({ orgId }) => `/api/usuarios/organizacion/${orgId}`,
    endpoint: "/api/usuarios",
    columns: [
      ["email", "Correo"],
      ["rol", "Rol", "status"],
      ["medicoId", "Medico", "doctor"],
      ["activo", "Activo", "bool"],
    ],
    fields: [
      { key: "organizacionId", type: "hiddenOrg", required: true },
      { key: "medicoId", label: "Medico", source: "medicos" },
      { key: "email", label: "Correo", type: "email", required: true },
      { key: "rol", label: "Rol", type: "select", options: ["ADMIN", "MEDICO"], required: true },
      { key: "password", label: "Password", type: "password", required: true },
    ],
  },
  organizaciones: {
    label: "Organizaciones",
    roles: ["ADMIN"],
    description: "Clínicas, planes y período de prueba.",
    list: () => "/api/organizaciones",
    endpoint: "/api/organizaciones",
    columns: [
      ["nombre", "Nombre"],
      ["plan", "Plan", "status"],
      ["trialHasta", "Trial", "datetime"],
      ["activo", "Activo", "bool"],
    ],
    fields: [
      { key: "nombre", label: "Nombre", required: true },
      { key: "plan", label: "Plan", type: "select", options: ["SOLO", "CLINICA", "ENTERPRISE"], required: true },
      { key: "trialHasta", label: "Trial hasta", type: "datetime-local" },
    ],
  },
};

function readJson(key) {
  try {
    return JSON.parse(localStorage.getItem(key));
  } catch {
    return null;
  }
}

function decodeJwt(token) {
  if (!token?.includes(".")) return {};
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const json = decodeURIComponent(
      atob(payload)
        .split("")
        .map((char) => `%${`00${char.charCodeAt(0).toString(16)}`.slice(-2)}`)
        .join("")
    );
    return JSON.parse(json);
  } catch {
    return {};
  }
}

function requestValue(value) {
  if (value === "" || value == null) return undefined;
  return value;
}

function normalizeApiUrl(value) {
  return String(value || "").replace(/\/$/, "");
}

function App() {
  const [session, setSession] = React.useState(() => readJson(STORAGE_KEY));
  const [apiUrl, setApiUrlState] = React.useState(() => localStorage.getItem(API_KEY) || DEFAULT_API_URL);
  const [route, setRoute] = React.useState(() => {
    const path = location.pathname.replace(/^\//, "");
    return path || (session ? "dashboard" : "login");
  });
  const [toast, setToast] = React.useState(null);

  React.useEffect(() => {
    const onPop = () => {
      const path = location.pathname.replace(/^\//, "");
      setRoute(path || (session ? "dashboard" : "login"));
    };
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, [session]);

  const role = session?.rol || session?.claims?.rol || "PACIENTE";
  const orgId = session?.organizacionId || session?.claims?.organizacionId || "";
  const userId = session?.userId || session?.claims?.userId || session?.claims?.usuarioId || "";
  const medicoId = session?.medicoId || session?.claims?.medicoId || "";
  const pacienteId = session?.pacienteId || session?.claims?.pacienteId || "";
  const userName = sessionDisplayName(session);

  const notify = React.useCallback((message, kind = "ok") => {
    setToast({ message, kind });
    window.setTimeout(() => setToast(null), 4200);
  }, []);

  const setApiUrl = React.useCallback((value) => {
    const clean = normalizeApiUrl(value);
    localStorage.setItem(API_KEY, clean);
    setApiUrlState(clean);
  }, []);

  const api = React.useCallback(
    async (pathname, options = {}) => {
      const response = await fetch(`${apiUrl}${pathname}`, {
        ...options,
        headers: {
          Accept: "application/json",
          ...(options.body ? { "Content-Type": "application/json" } : {}),
          ...(session?.token ? { Authorization: `Bearer ${session.token}` } : {}),
          ...(options.headers || {}),
        },
      });
      const contentType = response.headers.get("content-type") || "";
      const body = contentType.includes("application/json") ? await response.json().catch(() => null) : await response.text();
      if (!response.ok) {
        const msg = typeof body === "string"
          ? body
          : (body?.message || body?.mensaje || body?.detail || body?.error || `HTTP ${response.status}`);
        throw new Error(msg);
      }
      return body;
    },
    [apiUrl, session?.token]
  );

  const list = React.useCallback(
    async (pathname, silent = false) => {
      try {
        const result = await api(pathname);
        if (!result) return [];
        return Array.isArray(result) ? result : [result];
      } catch (error) {
        if (silent) return [];
        throw error;
      }
    },
    [api]
  );

  const go = React.useCallback((nextRoute) => {
    history.pushState(null, "", `/${nextRoute}`);
    setRoute(nextRoute);
  }, []);

  const onLogin = async (payload, mode) => {
    const result = await api(mode === "registro" ? "/api/auth/registro" : "/api/auth/login", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    const next = { ...result, claims: decodeJwt(result.token) };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    setSession(next);
    notify("Sesion iniciada");
    go("dashboard");
  };

  const logout = () => {
    localStorage.removeItem(STORAGE_KEY);
    setSession(null);
    go("login");
  };

  const context = React.useMemo(
    () => ({ api, list, notify, go, role, orgId, userId, medicoId, pacienteId, userName, session, apiUrl, setApiUrl }),
    [api, list, notify, go, role, orgId, userId, medicoId, pacienteId, userName, session, apiUrl, setApiUrl]
  );

  if (!session) {
    return (
      <>
        <AuthPage mode={route === "registro" ? "registro" : "login"} onSubmit={onLogin} go={go} notify={notify} />
        <Toast toast={toast} />
      </>
    );
  }

  return (
    <>
      <Shell context={context} route={route} logout={logout}>
        <CurrentView route={route} context={context} />
      </Shell>
      <Toast toast={toast} />
    </>
  );
}

function AuthPage({ mode, onSubmit, go, notify }) {
  const isRegister = mode === "registro";
  const [submitting, setSubmitting] = React.useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    const form = new FormData(event.currentTarget);
    try {
      await onSubmit(
        {
          email: form.get("email"),
          password: form.get("password"),
          ...(isRegister ? { organizacionId: form.get("organizacionId") } : {}),
        },
        mode
      );
    } catch (error) {
      notify(error.message, "error");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-panel">
        <div className="auth-card">
          <div className="brand">
            <h1>MedInFlow</h1>
            <p>Plataforma medica integral</p>
          </div>
          <form className="stack" onSubmit={handleSubmit}>
            <Field label="Correo electronico" name="email" type="email" placeholder="admin@clinica.com" required />
            <Field label="Password" name="password" type="password" placeholder="Minimo 8 caracteres" required />
            {isRegister && <Field label="Organizacion ID" name="organizacionId" placeholder="UUID de la organizacion" required />}
            <button className="btn primary wide" disabled={submitting}>
              {submitting ? "Conectando..." : isRegister ? "Crear cuenta paciente" : "Iniciar sesion"}
            </button>
          </form>
          <button className="btn ghost wide" onClick={() => go(isRegister ? "login" : "registro")}>
            {isRegister ? "Ya tengo cuenta" : "Registrar paciente"}
          </button>
        </div>
      </section>
      <section className="auth-art">
        <div className="visual-window">
          <div className="monitor-card">
            <span className="badge info">OPERACION CLINICA</span>
            <h2>Agenda, expedientes y pagos en un solo panel.</h2>
            <div className="metric-lines">
              <span style={{ width: "86%" }} />
              <span style={{ width: "64%", background: "var(--secondary)" }} />
              <span style={{ width: "42%", background: "var(--tertiary)" }} />
            </div>
          </div>
        </div>
        <h2>Precision clinica para operar el dia.</h2>
        <p>El diseno de Stitch queda como base visual; esta version ya vive en React y habla con los endpoints reales.</p>
      </section>
    </main>
  );
}

function Shell({ context, route, logout, children }) {
  const [open, setOpen] = React.useState(false);
  const [search, setSearch] = React.useState("");
  const allowed = Object.entries(modules).filter(([, config]) => config.roles.includes(context.role));
  const active = route.startsWith("modulo/") ? route.split("/")[1] : route;
  const navigate = React.useCallback(
    (nextRoute) => {
      context.go(nextRoute);
      setOpen(false);
    },
    [context]
  );

  React.useEffect(() => {
    document.querySelectorAll("tbody tr").forEach((row) => {
      row.hidden = Boolean(search) && !row.textContent.toLowerCase().includes(search.toLowerCase());
    });
  }, [search, children]);

  return (
    <div className="app-shell">
      {open && <button className="sidebar-backdrop" aria-label="Cerrar menu" onClick={() => setOpen(false)} />}
      <aside className={`sidebar ${open ? "open" : ""}`}>
        <div className="sidebar-header">
          <div>
            <div className="brand-mark">MedInFlow</div>
            <p>{context.userName}</p>
          </div>
          <button className="btn icon sidebar-close" onClick={() => setOpen(false)} aria-label="Cerrar menu">
            <X size={18} />
          </button>
        </div>
        <nav className="nav-list">
          <NavButton id="dashboard" label="Dashboard" active={active === "dashboard"} onClick={() => navigate("dashboard")} />
          {allowed.map(([key, config]) => (
            <NavButton key={key} id={key} label={config.label} active={active === key} onClick={() => navigate(`modulo/${key}`)} />
          ))}
          {context.role === "MEDICO" && (
            <NavButton
              id="medicoClinico"
              label="Diagnosticos y Adjuntos"
              active={route === "medico/diagnosticos-adjuntos"}
              onClick={() => navigate("medico/diagnosticos-adjuntos")}
            />
          )}
          {context.role === "PACIENTE" && (
            <>
              <NavButton
                id="pacienteAgendar"
                label="Agendar cita"
                active={route === "paciente/agendar"}
                onClick={() => navigate("paciente/agendar")}
              />
              <NavButton
                id="pacienteCitas"
                label="Mis citas"
                active={route === "paciente/citas"}
                onClick={() => navigate("paciente/citas")}
              />
            </>
          )}
          <NavButton id="ia" label="IA Chat" active={active === "ia"} onClick={() => navigate("ia")} />
        </nav>
        <div className="nav-footer">
          <NavButton id="settings" label="Ajustes" active={active === "settings"} onClick={() => navigate("settings")} />
          <button className="nav-item" onClick={logout}>
            <LogOut size={18} />
            <span>Salir</span>
          </button>
        </div>
      </aside>
      <header className="topbar">
        <button className="btn icon mobile-menu" onClick={() => setOpen((value) => !value)} aria-label="Menu">
          <Menu size={18} />
        </button>
        <label className="search">
          <Search size={17} />
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar en la vista actual..." />
        </label>
        <div className="user-chip">
          <div className="user-meta">
            <strong>{context.userName}</strong>
            <span>{context.role}</span>
          </div>
          <div className="avatar">{initials(context.userName)}</div>
        </div>
      </header>
      <main className="content">
        <div className="content-inner">{children}</div>
      </main>
    </div>
  );
}

function NavButton({ id, label, active, onClick }) {
  const Icon = iconMap[id] || Activity;
  return (
    <button className={`nav-item ${active ? "active" : ""}`} onClick={onClick}>
      <Icon size={18} />
      <span>{label}</span>
    </button>
  );
}

function CurrentView({ route, context }) {
  if (route === "dashboard") return <Dashboard context={context} />;
  if (route === "medico/agenda") {
    return context.role === "MEDICO" ? <MedicoAgenda context={context} /> : <ErrorBox message="Tu rol no tiene acceso a esta vista." />;
  }
  if (route.startsWith("medico/consulta/")) {
    const citaId = route.split("/")[2];
    return context.role === "MEDICO" ? <MedicoConsultaDetalle context={context} citaId={citaId} /> : <ErrorBox message="Tu rol no tiene acceso a esta vista." />;
  }
  if (route.startsWith("medico/expediente/")) {
    const pacienteId = route.split("/")[2];
    return context.role === "MEDICO" ? <MedicoExpedientePaciente context={context} pacienteId={pacienteId} /> : <ErrorBox message="Tu rol no tiene acceso a esta vista." />;
  }
  if (route === "medico/diagnosticos-adjuntos") {
    return context.role === "MEDICO" ? <MedicoDiagnosticosAdjuntos context={context} /> : <ErrorBox message="Tu rol no tiene acceso a esta vista." />;
  }
  if (route === "paciente/citas") {
    return context.role === "PACIENTE" ? <PacienteCitas context={context} /> : <ErrorBox message="Tu rol no tiene acceso a esta vista." />;
  }
  if (route === "paciente/agendar") {
    return context.role === "PACIENTE" ? <PacienteAgendarCita context={context} /> : <ErrorBox message="Tu rol no tiene acceso a esta vista." />;
  }
  if (route === "ia") return <Chat context={context} />;
  if (route === "settings") return <SettingsView context={context} />;
  if (route.startsWith("modulo/")) {
    const key = route.split("/")[1];
    const config = modules[key];
    if (!config?.roles.includes(context.role)) return <ErrorBox message="Tu rol no tiene acceso a este modulo." />;
    if (key === "diagnosticos") return <DiagnosticosModule context={context} />;
    if (key === "adjuntos")    return <AdjuntosModule context={context} />;
    return <ModuleView moduleKey={key} config={config} context={context} />;
  }
  return <Dashboard context={context} />;
}

function Dashboard({ context }) {
  if (context.role === "MEDICO") return <MedicoDashboard context={context} />;
  if (context.role === "PACIENTE") return <PacienteDashboard context={context} />;
  const [data, setData] = React.useState(null);
  const [error, setError] = React.useState("");

  React.useEffect(() => {
    let active = true;
    async function load() {
      try {
        const [pacientes, medicos, citas, pagos] = await Promise.all([
          context.role === "PACIENTE" ? [] : context.list(`/api/pacientes/organizacion/${context.orgId}`, true),
          context.role === "PACIENTE" ? [] : context.list(`/api/medicos/organizacion/${context.orgId}`, true),
          context.role === "PACIENTE" ? [] : context.list(`/api/citas/organizacion/${context.orgId}`, true),
          context.role === "PACIENTE" ? [] : context.list(`/api/pagos/organizacion/${context.orgId}`, true),
        ]);
        if (active) setData({ pacientes, medicos, citas, pagos });
      } catch (err) {
        if (active) setError(err.message);
      }
    }
    load();
    return () => {
      active = false;
    };
  }, [context]);

  if (error) return <ErrorBox message={error} />;
  if (!data) return <Loading label="Cargando resumen operativo..." />;

  const unconfirmed = data.citas.filter((cita) => cita.estado === "SIN_CONFIRMAR").length;
  const pendingPayments = data.pagos.filter((pago) => pago.estado === "PENDIENTE").length;

  return (
    <>
      <ViewHeader title="Panel de Control" subtitle={`Resumen operativo conectado a la organizacion ${shortId(context.orgId)}.`}>
        <button className="btn primary" onClick={() => context.go("modulo/citas")}>
          Nueva cita
        </button>
      </ViewHeader>
      <section className="metrics">
        <Metric label="Citas" value={data.citas.length} />
        <Metric label="Medicos activos" value={data.medicos.filter((item) => item.activo !== false).length} variant="secondary" />
        <Metric label="Pagos pendientes" value={pendingPayments} variant="warning" />
        <Metric label="Sin confirmar" value={unconfirmed} variant="danger" />
      </section>
      <Section title="Proximas citas">
        <DataTable rows={data.citas.slice(0, 6)} columns={modules.citas.columns} support={data} />
      </Section>
    </>
  );
}

function applyClientFilters(rows, filters, support) {
  let result = rows;
  if (filters.pacienteId) {
    result = result.filter((row) => String(row.pacienteId) === String(filters.pacienteId));
  }
  if (filters.medicoId) {
    result = result.filter((row) => String(row.medicoId) === String(filters.medicoId));
  }
  if (filters.estado) {
    result = result.filter((row) => row.estado === filters.estado);
  }
  if (filters.selectedId) {
    const val = String(filters.selectedId);
    if (val.startsWith("_text_")) {
      const text = val.slice(6).toLowerCase();
      result = result.filter((row) => (row.nombre || row.email || "").toLowerCase().includes(text));
    } else {
      result = result.filter((row) => String(row.id) === String(filters.selectedId));
    }
  }
  if (filters.consultorioId) {
    const val = String(filters.consultorioId);
    if (val.startsWith("_text_")) {
      const text = val.slice(6).toLowerCase();
      result = result.filter((row) => {
        const consultorio = (support.consultorios || []).find((c) => String(c.id) === String(row.consultorioId));
        return (consultorio?.nombre || "").toLowerCase().includes(text);
      });
    } else {
      result = result.filter((row) => String(row.consultorioId) === String(filters.consultorioId));
    }
  }
  if (filters.activos === "true" || filters.activos === true) {
    result = result.filter((row) => row.activo === true);
  }
  if (filters.activos === "false") {
    result = result.filter((row) => !row.activo);
  }
  return result;
}

function ModuleView({ moduleKey, config, context }) {
  const [support, setSupport] = React.useState({});
  const [filters, setFilters] = React.useState({});
  const [liveFilters, setLiveFilters] = React.useState({});
  const [filterResetKey, setFilterResetKey] = React.useState(0);
  const [rows, setRows] = React.useState([]);
  const [formState, setFormState] = React.useState(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState("");
  const [pendingDeleteId, setPendingDeleteId] = React.useState(null);
  const [deleting, setDeleting] = React.useState(false);
  const [estadoTarget, setEstadoTarget] = React.useState(null);
  const [savingEstado, setSavingEstado] = React.useState(false);
  const [togglingId, setTogglingId] = React.useState(null);

  const supportNames = React.useMemo(() => requiredSupport(config, context.role), [config, context.role]);

  const loadSupport = React.useCallback(async () => {
    const entries = await Promise.all(
      supportNames.map(async (name) => {
        const endpoint = supportEndpoint(name, context);
        return [name, endpoint ? await context.list(endpoint, true) : []];
      })
    );
    return Object.fromEntries(entries);
  }, [context, supportNames]);

  const loadRows = React.useCallback(
    async (nextFilters = filters, nextSupport = support) => {
      setLoading(true);
      setError("");
      try {
        const path = config.list({ orgId: context.orgId, filters: nextFilters, role: context.role, medicoId: context.medicoId });
        setRows(path ? await context.list(path) : []);
        setSupport(nextSupport);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    },
    [config, context, filters, support]
  );

  React.useEffect(() => {
    let active = true;
    async function boot() {
      setLoading(true);
      const loadedSupport = await loadSupport();
      const defaults = { ...filters };
      for (const filter of config.filters || []) {
        if (filter.required && !defaults[filter.key] && loadedSupport[filter.source]?.[0]?.id) {
          defaults[filter.key] = loadedSupport[filter.source][0].id;
        }
      }
      if (!active) return;
      setSupport(loadedSupport);
      setFilters(defaults);
      await loadRows(defaults, loadedSupport);
    }
    boot();
    return () => { active = false; };
  }, [moduleKey]);

  // Filas a mostrar: client-side para módulos con clientFiltered, directas para el resto
  const displayRows = React.useMemo(() => {
    if (config.clientFilter) return config.clientFilter(rows, { ...filters, ...liveFilters }, context.role, support, context);
    if (!config.clientFiltered) return rows;
    return applyClientFilters(rows, { ...filters, ...liveFilters }, support);
  }, [rows, filters, liveFilters, support, config, context]);

  const hasActiveFilters = config.clientFiltered &&
    Object.values({ ...filters, ...liveFilters }).some(Boolean);

  function handleApplyFilters(next) {
    setFilters(next);
    if (!config.clientFiltered) loadRows(next);
  }

  function handleClearFilters() {
    setFilters({});
    setLiveFilters({});
    setFilterResetKey((k) => k + 1);
    if (!config.clientFiltered) loadRows({});
  }

  function handleLiveChange(key, value) {
    if (config.clientFiltered) {
      setLiveFilters((prev) => ({ ...prev, [key]: value }));
    } else {
      const next = { ...filters, [key]: value };
      setFilters(next);
      loadRows(next);
    }
  }

  async function saveEntity(payload) {
    try {
      const editing = formState?.mode === "edit";
      const finalPayload = config.transformPayload ? config.transformPayload({ ...payload }, { editing, item: formState?.item }) : payload;
      await context.api(editing ? `${config.endpoint}/${formState.item.id}` : config.endpoint, {
        method: editing ? "PUT" : "POST",
        body: JSON.stringify(finalPayload),
      });
      context.notify(editing ? "Registro actualizado" : "Registro creado");
      setFormState(null);
      await loadRows();
    } catch (err) {
      context.notify(err.message, "error");
    }
  }

  async function handleToggleActivo(item) {
    setTogglingId(item.id);
    try {
      await context.api(`${config.endpoint}/${item.id}/activo`, { method: "PATCH" });
      context.notify(`${item.nombre || "Registro"} ${item.activo ? "desactivado" : "activado"}`);
      await loadRows();
    } catch (err) {
      context.notify(err.message, "error");
    } finally {
      setTogglingId(null);
    }
  }

  async function handleCambiarEstado(nuevoEstado) {
    setSavingEstado(true);
    try {
      await context.api(`${config.endpoint}/${estadoTarget.id}/estado`, {
        method: "PATCH",
        body: JSON.stringify({ estado: nuevoEstado }),
      });
      context.notify("Estado actualizado");
      setEstadoTarget(null);
      await loadRows();
    } catch (err) {
      context.notify(err.message, "error");
    } finally {
      setSavingEstado(false);
    }
  }

  async function confirmDelete() {
    setDeleting(true);
    try {
      await context.api(`${config.endpoint}/${pendingDeleteId}`, { method: "DELETE" });
      context.notify("Registro eliminado");
      setPendingDeleteId(null);
      await loadRows();
    } catch (err) {
      context.notify(err.message, "error");
    } finally {
      setDeleting(false);
    }
  }

  return (
    <>
      <ViewHeader title={config.label} subtitle={config.description}>
        <button className="btn" onClick={() => loadRows()}>
          <RefreshCw size={16} /> Actualizar
        </button>
        <button className="btn primary" onClick={() => setFormState({ mode: "new", item: {} })}>
          Nuevo
        </button>
      </ViewHeader>
      <FilterBar
        config={config}
        filters={filters}
        support={support}
        loading={loading}
        role={context.role}
        onApply={handleApplyFilters}
        onClear={handleClearFilters}
        onLiveChange={handleLiveChange}
        resetKey={filterResetKey}
      />
      <div className="split">
        <Section title={loading ? "Cargando..." : `${displayRows.length} registros`}>
          {error ? <ErrorBox message={error} /> : (
            <DataTable
              rows={displayRows}
              columns={config.columns}
              support={support}
              onOpen={moduleKey === "pacientes" && context.role === "MEDICO" ? (item) => context.go(`medico/expediente/${item.id}`) : undefined}
              onEdit={(item) => setFormState({ mode: "edit", item })}
              onDelete={(id) => setPendingDeleteId(id)}
              onChangeStatus={moduleKey === "citas" ? (item) => setEstadoTarget(item) : undefined}
              onToggleActive={["pacientes", "medicos", "consultorios"].includes(moduleKey) ? handleToggleActivo : undefined}
              togglingId={togglingId}
              emptyLabel={hasActiveFilters ? "No se encontraron coincidencias." : `No hay ${config.label.toLowerCase()} registradas.`}
              emptyHint={hasActiveFilters ? "Intenta limpiar los filtros para ver todos los registros." : `Presiona «Nuevo» para crear el primer registro.`}
            />
          )}
        </Section>
        <Section title={formState?.mode === "edit" ? "Editar registro" : "Nuevo registro"}>
          {formState ? (
            moduleKey === "citas" ? (
              <CitaForm item={formState.item} context={context} support={support} onCancel={() => setFormState(null)} onSubmit={saveEntity} />
            ) : moduleKey === "horarios" ? (
              <HorarioForm
                item={formState.item}
                context={context}
                support={support}
                onCancel={() => setFormState(null)}
                onSubmit={saveEntity}
                onEditExisting={(conflictItem) => setFormState({ mode: "edit", item: conflictItem })}
              />
            ) : moduleKey === "diagnosticos" ? (
              <DiagnosticoForm item={formState.item} context={context} support={support} onCancel={() => setFormState(null)} onSubmit={saveEntity} />
            ) : (
              <EntityForm config={config} item={formState.item} context={context} support={support} onCancel={() => setFormState(null)} onSubmit={saveEntity} />
            )
          ) : (
            <div className="empty-capture">
              <FileText size={36} strokeWidth={1.25} />
              <p className="empty-capture-title">Nada seleccionado</p>
              <p className="empty-capture-hint">Elige un registro de la tabla para editarlo,<br />o pulsa <strong>Nuevo</strong> para crear uno.</p>
            </div>
          )}
        </Section>
      </div>
      {estadoTarget && (
        <CambiarEstadoModal
          item={estadoTarget}
          saving={savingEstado}
          onClose={() => setEstadoTarget(null)}
          onSubmit={handleCambiarEstado}
        />
      )}
      {pendingDeleteId && (
        <ConfirmModal
          title="Confirmar eliminación"
          message="¿Estás seguro de eliminar este registro? Esta acción no se puede deshacer."
          loading={deleting}
          onCancel={() => setPendingDeleteId(null)}
          onConfirm={confirmDelete}
        />
      )}
    </>
  );
}

function FilterBar({ config, filters, support, loading, role, onApply, onClear, onLiveChange, resetKey }) {
  if (!config.filters?.length) return null;
  const fields = config.filters.filter((field) => !field.roles || field.roles.includes(role));
  return (
    <section className="section filter-section">
      <form
        key={resetKey}
        className="filters"
        onChange={(e) => {
          const fieldCfg = fields.find((f) => f.key === e.target.name && f.live && f.type !== "combobox");
          if (fieldCfg && onLiveChange) onLiveChange(e.target.name, e.target.value);
        }}
        onSubmit={(event) => {
          event.preventDefault();
          const applyFields = fields.filter((f) => f.type !== "combobox");
          onApply(formPayload(event.currentTarget, applyFields, {}, support, true));
        }}
      >
        {fields.map((field) =>
          field.type === "combobox" ? (
            <ComboboxFilter key={field.key} field={field} support={support} loading={loading} onLiveChange={onLiveChange} />
          ) : (
            <FormControl
              key={field.key}
              field={field}
              defaultValue={filters[field.key]}
              support={support}
              onLiveChange={field.live ? onLiveChange : undefined}
            />
          )
        )}
        <div className="actions">
          <button className="btn" type="button" onClick={() => { if (onClear) onClear(); else onApply({}); }}>
            Limpiar
          </button>
        </div>
      </form>
    </section>
  );
}

function ComboboxFilter({ field, support, loading, onLiveChange }) {
  const [search, setSearch] = React.useState("");
  const [open, setOpen] = React.useState(false);
  const [selectedId, setSelectedId] = React.useState("");
  const [selectedLabel, setSelectedLabel] = React.useState("");
  const [dropPos, setDropPos] = React.useState({ top: 0, left: 0, width: 0 });
  const fieldRef = React.useRef(null);
  const listRef = React.useRef(null);

  const items = support[field.source] || [];

  React.useEffect(() => {
    function onClickOutside(e) {
      const inField = fieldRef.current && fieldRef.current.contains(e.target);
      const inList = listRef.current && listRef.current.contains(e.target);
      if (!inField && !inList) setOpen(false);
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  function openDropdown() {
    if (fieldRef.current) {
      const rect = fieldRef.current.getBoundingClientRect();
      setDropPos({ top: rect.bottom + 4, left: rect.left, width: rect.width });
    }
    setOpen(true);
  }

  const filtered = search
    ? items.filter((item) => (item.nombre || item.email || "").toLowerCase().includes(search.toLowerCase()))
    : items;

  function handleSelect(item) {
    const label = item.nombre || item.email || shortId(item.id);
    setSelectedId(item.id);
    setSelectedLabel(label);
    setSearch("");
    setOpen(false);
    if (onLiveChange) onLiveChange(field.key, item.id);
  }

  function handleClear() {
    setSelectedId("");
    setSelectedLabel("");
    setSearch("");
    setOpen(false);
    if (onLiveChange) onLiveChange(field.key, "");
  }

  return (
    <label className="field" ref={fieldRef}>
      <span>{field.label}</span>
      <div className="combobox-input-wrap">
        <input
          type="text"
          placeholder={loading && items.length === 0 ? "Cargando..." : "Buscar por nombre..."}
          value={selectedId ? selectedLabel : search}
          readOnly={Boolean(selectedId)}
          onChange={(e) => {
            if (!selectedId) {
              const newSearch = e.target.value;
              setSearch(newSearch);
              if (!open) openDropdown();
              if (onLiveChange) onLiveChange(field.key, newSearch ? `_text_${newSearch}` : "");
            }
          }}
          onFocus={() => { if (!selectedId) openDropdown(); }}
          onKeyDown={(e) => { if (e.key === "Enter") e.preventDefault(); }}
        />
        {(selectedId || search) && (
          <button type="button" className="combobox-clear" onClick={handleClear} aria-label="Limpiar selección">
            ×
          </button>
        )}
      </div>
      {open && !selectedId && createPortal(
        <ul
          ref={listRef}
          className="combobox-list"
          style={{ position: "fixed", top: dropPos.top, left: dropPos.left, width: dropPos.width, zIndex: 9999 }}
        >
          {filtered.length === 0 ? (
            <li className="combobox-item combobox-empty">
              {loading && items.length === 0 ? "Cargando..." : search ? "Sin resultados" : "Sin datos disponibles"}
            </li>
          ) : (
            filtered.slice(0, 60).map((item) => (
              <li
                key={item.id}
                className="combobox-item"
                onMouseDown={(e) => { e.preventDefault(); handleSelect(item); }}
              >
                {item.nombre || item.email || shortId(item.id)}
              </li>
            ))
          )}
        </ul>,
        document.body
      )}
    </label>
  );
}

function EntityForm({ config, item, context, support, onCancel, onSubmit }) {
  return (
    <form
      className="form-grid"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit(formPayload(event.currentTarget, config.fields, context, support));
      }}
    >
      {config.fields.map((field) => (
        <FormControl key={field.key} field={field} defaultValue={fieldValue(field, item, context, support)} support={support} />
      ))}
      <div className="actions full">
        <button className="btn" type="button" onClick={onCancel}>
          <X size={16} /> Cancelar
        </button>
        <button className="btn primary">
          <Save size={16} /> Guardar
        </button>
      </div>
    </form>
  );
}

function FormControl({ field, defaultValue, support, onLiveChange }) {
  const [emailHint, setEmailHint] = React.useState("");

  if (field.type === "hiddenOrg" || field.type === "hiddenUser") {
    return <input type="hidden" name={field.key} defaultValue={defaultValue || ""} />;
  }

  const label = field.label || field.key;
  const className = `field ${field.full ? "full" : ""}`;

  if (field.type === "textarea") {
    return (
      <label className={className}>
        <span>{label}</span>
        <textarea name={field.key} defaultValue={defaultValue || ""} required={field.required} />
      </label>
    );
  }

  if (field.type === "checkbox") {
    return (
      <label className={className}>
        <span>{label}</span>
        <select
          name={field.key}
          defaultValue={defaultValue || ""}
          onChange={onLiveChange ? (e) => onLiveChange(field.key, e.target.value) : undefined}
        >
          <option value="">Todos</option>
          <option value="true">Solo activos</option>
          <option value="false">Solo inactivos</option>
        </select>
      </label>
    );
  }

  if (field.source) {
    const rows = support[field.source] || [];
    if (!rows.length) {
      return (
        <label className={className}>
          <span>{label}</span>
          <input name={field.key} defaultValue={defaultValue || ""} placeholder={`UUID de ${label.toLowerCase()}`} required={field.required} />
          <small>No hay registros cargados; puedes pegar el UUID manualmente.</small>
        </label>
      );
    }
    return (
      <label className={className}>
        <span>{label}</span>
        <select name={field.key} defaultValue={defaultValue || ""} required={field.required}>
          <option value="">Selecciona...</option>
          {rows.map((row) => (
            <option key={row.id} value={row.id}>
              {displayName(row, field.source, support)}
            </option>
          ))}
        </select>
      </label>
    );
  }

  if (field.options) {
    return (
      <label className={className}>
        <span>{label}</span>
        <select name={field.key} defaultValue={defaultValue || ""} required={field.required}>
          {field.options.map((option) => {
            const val = typeof option === "object" ? option.value : option;
            const lbl = typeof option === "object" ? option.label : (option || "Todos");
            return <option key={val || "empty"} value={val}>{lbl}</option>;
          })}
        </select>
      </label>
    );
  }

  return (
    <label className={`${className} field-hint-wrap`}>
      <span>{label}</span>
      <input
        name={field.key}
        type={field.type || "text"}
        defaultValue={defaultValue || ""}
        required={field.required}
        placeholder={field.placeholder || ""}
        maxLength={field.maxLength || undefined}
        min={field.min !== undefined ? field.min : undefined}
        onInput={(e) => {
          if (field.onlyLetters) {
            e.target.value = e.target.value.replace(/[^a-zA-ZáéíóúüñÁÉÍÓÚÜÑ\s]/g, "");
          } else if (field.onlyDigits) {
            e.target.value = e.target.value.replace(/\D/g, "").slice(0, field.maxLength || 999);
          }
          if (field.emailLocalMax) {
            const val = e.target.value;
            const at = val.indexOf("@");
            const local = at >= 0 ? val.slice(0, at) : val;
            if (!val) {
              setEmailHint("");
              e.target.setCustomValidity("");
            } else if (at === -1 && val.length >= 2) {
              setEmailHint("Añade @ a tu correo · ej: nombre@dominio.com");
              e.target.setCustomValidity("");
            } else if (local.length > field.emailLocalMax) {
              setEmailHint(`Máximo ${field.emailLocalMax} caracteres antes del @`);
              e.target.setCustomValidity(`Máximo ${field.emailLocalMax} caracteres antes del @`);
            } else {
              setEmailHint("");
              e.target.setCustomValidity("");
            }
          }
        }}
        onBlur={() => field.emailLocalMax && setEmailHint("")}
      />
      {emailHint && <div className="field-hint">{emailHint}</div>}
    </label>
  );
}

// ── Modal de confirmación genérico ───────────────────────────────────────────

function ConfirmModal({ title, message, loading, onCancel, onConfirm }) {
  return (
    <div className="modal-overlay" onClick={(e) => { if (e.target === e.currentTarget && !loading) onCancel(); }}>
      <div className="modal-card" role="dialog" aria-modal="true">
        <h3 className="modal-title">{title}</h3>
        <p className="modal-message">{message}</p>
        <div className="modal-actions">
          <button className="btn" onClick={onCancel} disabled={loading}>
            Cancelar
          </button>
          <button className="btn btn-danger" onClick={onConfirm} disabled={loading}>
            {loading ? "Eliminando..." : "Confirmar eliminación"}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Modal de cambio de estado ────────────────────────────────────────────────

const ESTADOS_CITA = ["SIN_CONFIRMAR", "CONFIRMADA", "CANCELADA", "REAGENDADA", "NO_ASISTIO"];

function CambiarEstadoModal({ item, saving, onClose, onSubmit }) {
  const [estado, setEstado] = React.useState(item.estado || "SIN_CONFIRMAR");

  React.useEffect(() => {
    function onKey(e) { if (e.key === "Escape" && !saving) onClose(); }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose, saving]);

  return createPortal(
    <div className="modal-overlay" onClick={(e) => { if (e.target === e.currentTarget && !saving) onClose(); }}>
      <div className="modal-card" role="dialog" aria-modal="true">
        <h3 className="modal-title">Cambiar estado</h3>
        <p className="modal-message">Selecciona el nuevo estado para esta cita.</p>
        <label className="field" style={{ marginBottom: "var(--space-lg)" }}>
          <span>Estado</span>
          <select value={estado} onChange={(e) => setEstado(e.target.value)} disabled={saving}>
            {ESTADOS_CITA.map((s) => (
              <option key={s} value={s}>{s.replace(/_/g, " ")}</option>
            ))}
          </select>
        </label>
        <div className="modal-actions">
          <button className="btn" onClick={onClose} disabled={saving}>Cancelar</button>
          <button
            className="btn primary"
            onClick={() => onSubmit(estado)}
            disabled={saving || estado === item.estado}
          >
            {saving ? "Guardando..." : "Guardar"}
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}

// ── Modal de motivo completo ─────────────────────────────────────────────────

function MotivoModal({ text, onClose }) {
  React.useEffect(() => {
    function onKey(e) { if (e.key === "Escape") onClose(); }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  return createPortal(
    <div className="modal-overlay" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="modal-card motivo-modal-card" role="dialog" aria-modal="true">
        <h3 className="modal-title">Motivo de la cita</h3>
        <div className="motivo-modal-body">{text}</div>
        <div className="modal-actions">
          <button className="btn primary" onClick={onClose}>Cerrar</button>
        </div>
      </div>
    </div>,
    document.body
  );
}

function MotivoCellWithModal({ text }) {
  const [open, setOpen] = React.useState(false);
  const preview = text.length > 45 ? text.slice(0, 45) + "…" : text;
  return (
    <>
      <span className="motivo-cell" data-tooltip="Ver motivo completo" onClick={() => setOpen(true)}>
        {preview}
      </span>
      {open && <MotivoModal text={text} onClose={() => setOpen(false)} />}
    </>
  );
}

// ── CitaForm: formulario especializado con disponibilidad real ────────────────

const DAY_NAMES = ["Dom", "Lun", "Mar", "Mie", "Jue", "Vie", "Sab"];

function utcDateStr(isoString) {
  const d = new Date(isoString);
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}-${String(d.getUTCDate()).padStart(2, "0")}`;
}

function utcHoraStr(isoString) {
  const d = new Date(isoString);
  return `${String(d.getUTCHours()).padStart(2, "0")}:${String(d.getUTCMinutes()).padStart(2, "0")}`;
}

function todayStr() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function DisabledTooltip({ active, tooltip, children }) {
  if (!active) return children;
  return (
    <div className="disabled-tooltip-wrap" title={tooltip}>
      {children}
    </div>
  );
}

function CitaForm({ item, context, support, onCancel, onSubmit }) {
  const isEditing = Boolean(item?.id);
  const fixedMedicoId = context.role === "MEDICO" ? context.medicoId : "";

  const [medicoId, setMedicoId] = React.useState(item?.medicoId || fixedMedicoId || "");
  const [horarios, setHorarios] = React.useState([]);
  const [fecha, setFecha] = React.useState(item?.fechaHora ? utcDateStr(item.fechaHora) : "");
  const [slots, setSlots] = React.useState([]);
  const [selectedSlot, setSelectedSlot] = React.useState(null);
  const [loadingSlots, setLoadingSlots] = React.useState(false);
  const [dayError, setDayError] = React.useState("");
  const [noSlots, setNoSlots] = React.useState(false);
  const [slotsError, setSlotsError] = React.useState("");

  // Distinguir la carga inicial (edición) de los cambios manuales del usuario
  const initialMedicoId = React.useRef(item?.medicoId || "");
  const today = todayStr();

  // Efecto 1: cargar horarios cuando cambia el médico
  React.useEffect(() => {
    if (!medicoId) {
      setHorarios([]);
      setSlots([]);
      setSelectedSlot(null);
      setDayError("");
      setNoSlots(false);
      setSlotsError("");
      return;
    }
    context.api(`/api/horarios/medico/${medicoId}`)
      .then((data) => {
        setHorarios(data || []);
        // Si el médico cambió manualmente (no es la carga inicial de edición) → limpiar fecha
        if (medicoId !== initialMedicoId.current) {
          setFecha("");
          setSlots([]);
          setSelectedSlot(null);
          setDayError("");
          setNoSlots(false);
          setSlotsError("");
        }
      })
      .catch(() => setHorarios([]));
  }, [medicoId]);

  // Efecto 2: cargar slots cuando cambia la fecha, los horarios o el médico
  React.useEffect(() => {
    setSlotsError("");
    if (!medicoId || !fecha || horarios.length === 0) {
      setSlots([]);
      setNoSlots(false);
      if (!fecha) setDayError("");
      return;
    }

    // Validar día de semana contra días configurados del médico
    const jsDay = new Date(fecha + "T12:00:00").getDay();
    const availableDays = new Set(horarios.map((h) => Number(h.diaSemana)));
    if (!availableDays.has(jsDay)) {
      const atiende = [...availableDays].map((d) => DAY_NAMES[d]).join(", ");
      setDayError(`Este médico no atiende los ${DAY_NAMES[jsDay]}. Atiende: ${atiende}`);
      setSlots([]);
      setNoSlots(false);
      setSelectedSlot(null);
      return;
    }

    setDayError("");
    setLoadingSlots(true);
    setNoSlots(false);

    const excludeQuery = isEditing ? `&excludeId=${item.id}` : "";
    context.api(`/api/citas/medico/${medicoId}/disponibilidad?fecha=${fecha}${excludeQuery}`)
      .then((data) => {
        const slotList = data || [];
        setSlots(slotList);
        setNoSlots(slotList.length === 0);
        const initHora = isEditing && item?.fechaHora ? utcHoraStr(item.fechaHora) : null;
        const match = initHora ? slotList.find((s) => s.hora === initHora) : null;
        setSelectedSlot(match || slotList[0] || null);
      })
      .catch((err) => {
        setSlots([]);
        setNoSlots(false);
        const raw = err instanceof Error ? err.message : String(err ?? "");
        const msg = !raw || raw === "[object Object]"
          ? "No se pudo cargar la disponibilidad. Verifica que el servidor esté actualizado."
          : raw;
        setSlotsError(msg);
        console.error("[CitaForm disponibilidad]", err);
      })
      .finally(() => setLoadingSlots(false));
  }, [fecha, horarios, medicoId]);

  function handleSubmit(e) {
    e.preventDefault();
    if (dayError) {
      context.notify("La fecha seleccionada no está disponible para este médico", "error");
      return;
    }
    if (!selectedSlot) {
      context.notify("Selecciona un horario disponible", "error");
      return;
    }
    const form = new FormData(e.currentTarget);
    const fechaHora = new Date(`${fecha}T${selectedSlot.hora}:00Z`).toISOString();
    onSubmit({
      organizacionId: context.orgId,
      pacienteId: form.get("pacienteId"),
      medicoId,
      consultorioId: autoConsultorioId,
      fechaHora,
      duracionMin: Number(selectedSlot.duracionMin),
      estado: context.role === "PACIENTE" ? undefined : (form.get("estado") || undefined),
      motivo: form.get("motivo") || undefined,
    });
  }

  const medicos = fixedMedicoId
    ? (support.medicos || []).filter((m) => String(m.id) === String(fixedMedicoId))
    : (support.medicos || []);
  const pacientes = support.pacientes || [];
  const consultorios = support.consultorios || [];
  const availableDays = new Set(horarios.map((h) => Number(h.diaSemana)));
  const fechaDisabled = !medicoId || horarios.length === 0;
  const horarioDisabled = !medicoId;
  const TOOLTIP_MEDICO = "Primero selecciona un médico";

  // El consultorio se deriva automáticamente del médico seleccionado
  const selectedMedico = medicos.find((m) => m.id === medicoId);
  const autoConsultorioId = selectedMedico?.consultorioId || item?.consultorioId || "";
  const autoConsultorioNombre = consultorios.find((c) => c.id === autoConsultorioId)?.nombre || "";

  return (
    <form className="form-grid" onSubmit={handleSubmit}>
      {/* Médico */}
      <label className="field">
        <span>Médico *</span>
        <select value={medicoId} onChange={(e) => setMedicoId(e.target.value)} required disabled={Boolean(fixedMedicoId)}>
          <option value="">Selecciona...</option>
          {medicos.map((m) => (
            <option key={m.id} value={m.id}>{m.nombre}</option>
          ))}
        </select>
      </label>

      {/* Días disponibles del médico */}
      <label className="field">
        <span>&nbsp;</span>
        {medicoId && availableDays.size > 0 && (
          <small className="muted">Atiende: {[...availableDays].map((d) => DAY_NAMES[d]).join(", ")}</small>
        )}
        {medicoId && horarios.length === 0 && (
          <small className="error-text">Este médico no tiene horarios configurados.</small>
        )}
      </label>

      {/* Paciente */}
      <label className="field">
        <span>Paciente *</span>
        <select name="pacienteId" defaultValue={item?.pacienteId || ""} required>
          <option value="">Selecciona...</option>
          {pacientes.map((p) => (
            <option key={p.id} value={p.id}>{p.nombre}</option>
          ))}
        </select>
      </label>

      {/* Consultorio — se llena automáticamente desde el médico seleccionado */}
      <label className="field" hidden>
        <span>Consultorio</span>
        <DisabledTooltip active={!medicoId} tooltip={TOOLTIP_MEDICO}>
          <input
            type="text"
            value={autoConsultorioNombre || (medicoId ? "Sin consultorio asignado" : "")}
            placeholder="Se asigna al seleccionar médico"
            disabled
            readOnly
          />
        </DisabledTooltip>
      </label>

      {/* Fecha */}
      <label className="field">
        <span>Fecha *</span>
        <DisabledTooltip active={fechaDisabled} tooltip={TOOLTIP_MEDICO}>
          <input
            type="date"
            value={fecha}
            min={today}
            onChange={(e) => { setFecha(e.target.value); setSelectedSlot(null); setSlotsError(""); }}
            required
            disabled={fechaDisabled}
          />
        </DisabledTooltip>
        {dayError && <small className="error-text">{dayError}</small>}
      </label>

      {/* Horario disponible */}
      <label className="field">
        <span>Horario *</span>
        {loadingSlots ? (
          <select disabled><option>Cargando horarios...</option></select>
        ) : noSlots ? (
          <>
            <select disabled><option>Sin disponibilidad</option></select>
            <small className="error-text">No hay horarios disponibles para esta fecha.</small>
          </>
        ) : slotsError ? (
          <>
            <select disabled><option>Error al cargar</option></select>
            <small className="error-text">{slotsError}</small>
          </>
        ) : slots.length > 0 ? (
          <select
            value={selectedSlot?.hora || ""}
            onChange={(e) => setSelectedSlot(slots.find((s) => s.hora === e.target.value) || null)}
            required
          >
            {slots.map((s) => (
              <option key={s.hora} value={s.hora}>
                {s.hora} – {s.horaFin} ({s.duracionMin} min)
              </option>
            ))}
          </select>
        ) : (
          <DisabledTooltip active={horarioDisabled} tooltip={TOOLTIP_MEDICO}>
            <select disabled>
              <option>{fecha && !dayError ? "Sin horarios configurados" : "Selecciona médico y fecha primero"}</option>
            </select>
          </DisabledTooltip>
        )}
      </label>

      {context.role !== "PACIENTE" && (
        <label className="field">
          <span>Estado</span>
          <select name="estado" defaultValue={item?.estado || "SIN_CONFIRMAR"}>
            {ESTADOS_CITA.map((estado) => (
              <option key={estado} value={estado}>{estado.replace(/_/g, " ")}</option>
            ))}
          </select>
        </label>
      )}

      {/* Motivo */}
      <label className="field full">
        <span>Motivo</span>
        <textarea name="motivo" defaultValue={item?.motivo || ""} />
      </label>

      <div className="actions full">
        <button className="btn" type="button" onClick={onCancel}>
          <X size={16} /> Cancelar
        </button>
        <button
          className="btn primary"
          disabled={!selectedSlot || Boolean(dayError) || noSlots || Boolean(slotsError)}
        >
          <Save size={16} /> Guardar
        </button>
      </div>
    </form>
  );
}

// ── HorarioForm: validación de día único por médico ───────────────────────────

const DIAS_SEMANA = ["Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"];

function HorarioForm({ item, context, support, onCancel, onSubmit, onEditExisting }) {
  const [medicoId, setMedicoId] = React.useState(item?.medicoId || "");
  const [diaSemana, setDiaSemana] = React.useState(
    item?.diaSemana !== undefined && item?.diaSemana !== null ? String(item.diaSemana) : ""
  );
  const [horariosExistentes, setHorariosExistentes] = React.useState([]);

  React.useEffect(() => {
    if (!medicoId) { setHorariosExistentes([]); return; }
    context.api(`/api/horarios/medico/${medicoId}`)
      .then((data) => setHorariosExistentes(data || []))
      .catch(() => setHorariosExistentes([]));
  }, [medicoId]);

  const conflicto = diaSemana !== ""
    ? (horariosExistentes.find(
        (h) => String(h.diaSemana) === diaSemana && (!item?.id || h.id !== item.id)
      ) || null)
    : null;

  const medicos = support.medicos || [];

  const handleSubmit = (e) => {
    e.preventDefault();
    if (conflicto) return;
    const fd = new FormData(e.currentTarget);
    onSubmit({
      medicoId,
      diaSemana: Number(diaSemana),
      horaInicio: fd.get("horaInicio"),
      horaFin: fd.get("horaFin"),
      duracionConsulta: Number(fd.get("duracionConsulta")),
    });
  };

  return (
    <form className="form-grid" onSubmit={handleSubmit}>
      <label className="field">
        <span>Médico *</span>
        <select
          name="medicoId"
          value={medicoId}
          onChange={(e) => { setMedicoId(e.target.value); setDiaSemana(""); }}
          required
        >
          <option value="">Selecciona...</option>
          {medicos.map((m) => (
            <option key={m.id} value={m.id}>{m.nombre}</option>
          ))}
        </select>
      </label>

      <label className="field">
        <span>Día *</span>
        <select
          name="diaSemana"
          value={diaSemana}
          onChange={(e) => setDiaSemana(e.target.value)}
          required
          disabled={!medicoId}
        >
          <option value="">{medicoId ? "Selecciona..." : "Selecciona médico primero"}</option>
          {DIAS_SEMANA.map((d, i) => (
            <option key={i} value={String(i)}>{d}</option>
          ))}
        </select>
      </label>

      {conflicto ? (
        <div className="field full">
          <small className="error-text">
            Este médico ya tiene un horario asignado para {DIAS_SEMANA[Number(diaSemana)]} ({String(conflicto.horaInicio).slice(0, 5)} – {String(conflicto.horaFin).slice(0, 5)}).
          </small>
          {onEditExisting && (
            <button
              type="button"
              className="btn"
              style={{ marginTop: "8px" }}
              onClick={() => onEditExisting(conflicto)}
            >
              Editar horario existente
            </button>
          )}
        </div>
      ) : (
        <>
          <label className="field">
            <span>Hora inicio *</span>
            <input
              name="horaInicio"
              type="time"
              defaultValue={String(item?.horaInicio || "").slice(0, 5)}
              required
            />
          </label>
          <label className="field">
            <span>Hora fin *</span>
            <input
              name="horaFin"
              type="time"
              defaultValue={String(item?.horaFin || "").slice(0, 5)}
              required
            />
          </label>
          <label className="field">
            <span>Duración (min) *</span>
            <select
              name="duracionConsulta"
              defaultValue={item?.duracionConsulta !== undefined ? String(item.duracionConsulta) : ""}
              required
            >
              <option value="">Selecciona...</option>
              {["20", "30", "45", "60"].map((v) => (
                <option key={v} value={v}>{v} min</option>
              ))}
            </select>
          </label>
        </>
      )}

      <div className="actions full">
        <button className="btn" type="button" onClick={onCancel}>
          <X size={16} /> Cancelar
        </button>
        <button className="btn primary" disabled={!medicoId || !diaSemana || Boolean(conflicto)}>
          <Save size={16} /> Guardar
        </button>
      </div>
    </form>
  );
}

// ── ComboboxField: combobox con búsqueda en vivo para formularios ─────────────

function ComboboxField({ label, items, displayFn, value, onSelect, onClear, placeholder, disabled, loading, full = true }) {
  const [search, setSearch] = React.useState("");
  const [open, setOpen] = React.useState(false);
  const [dropPos, setDropPos] = React.useState({ top: 0, left: 0, width: 0 });
  const fieldRef = React.useRef(null);
  const listRef = React.useRef(null);

  React.useEffect(() => {
    function onOutside(e) {
      const inField = fieldRef.current && fieldRef.current.contains(e.target);
      const inList  = listRef.current  && listRef.current.contains(e.target);
      if (!inField && !inList) setOpen(false);
    }
    document.addEventListener("mousedown", onOutside);
    return () => document.removeEventListener("mousedown", onOutside);
  }, []);

  function openDropdown() {
    if (disabled) return;
    if (fieldRef.current) {
      const r = fieldRef.current.getBoundingClientRect();
      setDropPos({ top: r.bottom + 4, left: r.left, width: r.width });
    }
    setOpen(true);
  }

  const filtered = search
    ? items.filter((it) => displayFn(it).toLowerCase().includes(search.toLowerCase()))
    : items;

  function handleSelect(it) {
    setSearch("");
    setOpen(false);
    onSelect(it);
  }

  function handleClear() {
    setSearch("");
    setOpen(false);
    if (onClear) onClear();
  }

  const inputValue = value ? displayFn(value) : search;

  return (
    <label className={`field ${full ? "full" : ""}`} ref={fieldRef}>
      {label && <span>{label}</span>}
      <div className="combobox-input-wrap">
        <input
          type="text"
          placeholder={loading ? "Cargando..." : disabled ? "Selecciona un paciente primero" : (placeholder || "Buscar...")}
          value={inputValue}
          readOnly={Boolean(value)}
          disabled={disabled}
          onChange={(e) => { if (!value) { setSearch(e.target.value); if (!open) openDropdown(); } }}
          onFocus={() => { if (!value && !disabled) openDropdown(); }}
          onClick={() => { if (!value && !disabled && !open) openDropdown(); }}
          onKeyDown={(e) => { if (e.key === "Enter") e.preventDefault(); }}
        />
        {(value || search) && !disabled && (
          <button type="button" className="combobox-clear" onClick={handleClear} aria-label="Limpiar">×</button>
        )}
      </div>
      {open && !value && !disabled && createPortal(
        <ul
          ref={listRef}
          className="combobox-list"
          style={{ position: "fixed", top: dropPos.top, left: dropPos.left, width: dropPos.width, zIndex: 9999 }}
        >
          {filtered.length === 0 ? (
            <li className="combobox-item combobox-empty">
              {loading ? "Cargando..." : search ? "Sin resultados" : "Sin datos disponibles"}
            </li>
          ) : (
            filtered.slice(0, 60).map((it) => (
              <li key={it.id} className="combobox-item" onMouseDown={(e) => { e.preventDefault(); handleSelect(it); }}>
                {displayFn(it)}
              </li>
            ))
          )}
        </ul>,
        document.body
      )}
    </label>
  );
}

// ── DiagnosticoForm: flujo Paciente → Cita → Diagnóstico ─────────────────────

function DiagnosticoForm({ item, context, support, onCancel, onSubmit, initialPaciente = null, initialCita = null }) {
  const isEditing  = Boolean(item?.id);
  const hasContext = Boolean(initialCita); // llamado desde DiagnosticosModule

  const pacientes    = support.pacientes    || [];
  const medicos      = support.medicos      || [];
  const consultorios = support.consultorios || [];

  // Derivar valores iniciales
  const citaInit = React.useMemo(() => {
    if (initialCita) return initialCita;
    if (item?.citaId) return (support.citas || []).find((c) => c.id === item.citaId) || null;
    return null;
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const pacienteInit = React.useMemo(() => {
    if (initialPaciente) return initialPaciente;
    if (citaInit) return pacientes.find((p) => p.id === citaInit.pacienteId) || null;
    return null;
  }, [citaInit]); // eslint-disable-line react-hooks/exhaustive-deps

  const [pacienteSeleccionado, setPacienteSeleccionado] = React.useState(pacienteInit);
  const [citasDelPaciente, setCitasDelPaciente]         = React.useState([]);
  const [citaSeleccionada, setCitaSeleccionada]         = React.useState(citaInit);
  const [loadingCitas, setLoadingCitas]                 = React.useState(false);

  const ESTADO_ORDER = { CONFIRMADA: 0, REAGENDADA: 1, SIN_CONFIRMAR: 2, NO_ASISTIO: 3, CANCELADA: 99 };
  const ESTADO_LABEL = { CONFIRMADA: "Confirmada", REAGENDADA: "Reagendada", SIN_CONFIRMAR: "Sin confirmar", NO_ASISTIO: "No asistió", CANCELADA: "Cancelada" };
  const ESTADO_V     = { CONFIRMADA: "ok", REAGENDADA: "warn", SIN_CONFIRMAR: "warn", NO_ASISTIO: "danger", CANCELADA: "danger" };

  // Cargar citas cuando cambia el paciente (solo modo independiente, no al editar)
  React.useEffect(() => {
    if (hasContext || isEditing || !pacienteSeleccionado) return;
    setLoadingCitas(true);
    setCitaSeleccionada(null);
    context.api(`/api/citas/paciente/${pacienteSeleccionado.id}`)
      .then((data) => {
        const validas = (data || []).filter((c) => c.estado !== "CANCELADA");
        validas.sort((a, b) => (ESTADO_ORDER[a.estado] ?? 50) - (ESTADO_ORDER[b.estado] ?? 50));
        setCitasDelPaciente(validas);
      })
      .catch(() => setCitasDelPaciente([]))
      .finally(() => setLoadingCitas(false));
  }, [pacienteSeleccionado]); // eslint-disable-line react-hooks/exhaustive-deps

  const citaLabel = (c) => {
    const f        = new Date(c.fechaHora);
    const fechaStr = f.toLocaleDateString("es-MX", { day: "2-digit", month: "2-digit", year: "numeric" });
    const horaStr  = f.toLocaleTimeString("es-MX", { hour: "2-digit", minute: "2-digit", hour12: true });
    const nomMed   = medicos.find((m) => m.id === c.medicoId)?.nombre || "—";
    return `${fechaStr}  ·  ${horaStr}  ·  ${nomMed}`;
  };

  const isCancelada = citaSeleccionada?.estado === "CANCELADA";

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!citaSeleccionada) return;
    if (isCancelada) { context.notify("No se pueden registrar diagnósticos para citas canceladas.", "error"); return; }
    const fd = new FormData(e.currentTarget);
    onSubmit({
      citaId:        citaSeleccionada.id,
      codigoCie10:   fd.get("codigoCie10") || undefined,
      tipo:          fd.get("tipo"),
      descripcion:   fd.get("descripcion"),
      observaciones: fd.get("observaciones") || undefined,
    });
  };

  const byId = (list, id) => list.find((x) => x.id === id);

  return (
    <form className="form-grid" onSubmit={handleSubmit}>

      {/* ── Paso 1: paciente (solo modo independiente) ── */}
      {!hasContext && !isEditing && (
        <ComboboxField
          label="Paciente *"
          items={pacientes}
          displayFn={(p) => p.nombre}
          value={pacienteSeleccionado}
          onSelect={(p) => { setPacienteSeleccionado(p); setCitaSeleccionada(null); setCitasDelPaciente([]); }}
          onClear={() => { setPacienteSeleccionado(null); setCitaSeleccionada(null); setCitasDelPaciente([]); }}
          placeholder="Buscar paciente por nombre..."
        />
      )}

      {/* ── Paso 2: cita (solo modo independiente) ── */}
      {!hasContext && !isEditing && (
        pacienteSeleccionado ? (
          citasDelPaciente.length === 0 && !loadingCitas ? (
            <div className="field full">
              <span>Cita *</span>
              <div className="combobox-no-citas">Este paciente no tiene consultas disponibles (confirmadas o reagendadas).</div>
            </div>
          ) : (
            <ComboboxField
              label="Cita *"
              items={citasDelPaciente}
              displayFn={citaLabel}
              value={citaSeleccionada}
              onSelect={(c) => setCitaSeleccionada(c)}
              onClear={() => setCitaSeleccionada(null)}
              placeholder={loadingCitas ? "Cargando citas…" : "Seleccionar consulta…"}
              disabled={loadingCitas}
              loading={loadingCitas}
            />
          )
        ) : (
          <div className="field full">
            <span>Cita *</span>
            <div className="combobox-disabled-hint">Selecciona un paciente primero</div>
          </div>
        )
      )}

      {/* ── Resumen de la consulta ── */}
      {citaSeleccionada && (
        <div className="field full">
          <div className="cita-resumen-card">
            <div className="cita-resumen-card-header">
              <span className="cita-resumen-card-title">Consulta seleccionada</span>
              <span className={`badge ${ESTADO_V[citaSeleccionada.estado] || "info"}`}>
                {ESTADO_LABEL[citaSeleccionada.estado] || citaSeleccionada.estado}
              </span>
            </div>
            <div className="cita-resumen-card-grid">
              <div className="cita-resumen-card-item">
                <span>Paciente</span>
                <strong>{byId(pacientes, citaSeleccionada.pacienteId)?.nombre || "—"}</strong>
              </div>
              <div className="cita-resumen-card-item">
                <span>Médico</span>
                <strong>{byId(medicos, citaSeleccionada.medicoId)?.nombre || "—"}</strong>
              </div>
              <div className="cita-resumen-card-item">
                <span>Fecha</span>
                <strong>{new Date(citaSeleccionada.fechaHora).toLocaleDateString("es-MX", { day: "2-digit", month: "2-digit", year: "numeric" })}</strong>
              </div>
              <div className="cita-resumen-card-item">
                <span>Hora</span>
                <strong>{new Date(citaSeleccionada.fechaHora).toLocaleTimeString("es-MX", { hour: "2-digit", minute: "2-digit", hour12: true })}</strong>
              </div>
              <div className="cita-resumen-card-item">
                <span>Consultorio</span>
                <strong>{byId(consultorios, citaSeleccionada.consultorioId)?.nombre || "—"}</strong>
              </div>
              {citaSeleccionada.motivo && (
                <div className="cita-resumen-card-item full">
                  <span>Motivo</span>
                  <strong>{citaSeleccionada.motivo}</strong>
                </div>
              )}
            </div>
            {isCancelada && (
              <div className="cita-cancelada-warning">⚠ No es posible registrar diagnósticos para citas canceladas.</div>
            )}
          </div>
        </div>
      )}

      {/* ── Campos del diagnóstico ── */}
      {citaSeleccionada && !isCancelada && (
        <>
          <label className="field full">
            <span>Tipo de diagnóstico *</span>
            <select name="tipo" defaultValue={item?.tipo || "PRINCIPAL"} required>
              <option value="PRINCIPAL">Principal</option>
              <option value="SECUNDARIO">Secundario</option>
            </select>
            <div className="tipo-diagnostico-help">
              <div className="tipo-help-item"><strong>Principal:</strong> Diagnóstico principal responsable de la atención médica.</div>
              <div className="tipo-help-item"><strong>Secundario:</strong> Condiciones o diagnósticos adicionales asociados al paciente.</div>
            </div>
          </label>
          <label className="field">
            <span>Código CIE-10</span>
            <input name="codigoCie10" type="text" defaultValue={item?.codigoCie10 || ""} maxLength={10} placeholder="Ej. J06.9" />
          </label>
          <label className="field full">
            <span>Descripción *</span>
            <textarea name="descripcion" defaultValue={item?.descripcion || ""} required placeholder="Describe el diagnóstico clínico…" />
          </label>
          <label className="field full">
            <span>Observaciones</span>
            <textarea name="observaciones" defaultValue={item?.observaciones || ""} placeholder="Observaciones adicionales, tratamiento sugerido, seguimiento…" />
          </label>
        </>
      )}

      <div className="actions full">
        <button className="btn" type="button" onClick={onCancel}><X size={16} /> Cancelar</button>
        <button className="btn primary" disabled={!citaSeleccionada || isCancelada}><Save size={16} /> Guardar</button>
      </div>
    </form>
  );
}

function Chat({ context }) {
  const [messages, setMessages] = React.useState([
    { role: "assistant", text: "Hola, soy el asistente de MedInFlow.\n\n¿Con qué especialidad médica te puedo ayudar hoy?" },
  ]);
  const [loading, setLoading] = React.useState(false);
  const bottomRef = React.useRef(null);

  React.useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, loading]);

  async function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const text = String(form.get("mensaje") || "").trim();
    if (!text) return;
    event.currentTarget.reset();
    setMessages((items) => [...items, { role: "user", text }]);
    setLoading(true);
    try {
      const historial = messages.map((m) => ({
        role: m.role === "ai" ? "assistant" : m.role,
        content: m.text,
      }));
      const result = await context.api("/api/ia/chat", {
        method: "POST",
        body: JSON.stringify({ mensaje: text, historial, organizacion_id: context.orgId }),
      });
      const respuesta = typeof result === "string" ? result : (result?.respuesta ?? "Sin respuesta");
      setMessages((items) => [...items, { role: "assistant", text: respuesta }]);
    } catch (err) {
      console.error("[MedInFlow IA]", err);
      const fallbacks = [
        "Hola, disculpa la interrupción. En este momento estoy atendiendo varias solicitudes a la vez.\n\nPor favor intenta nuevamente en unos segundos, con gusto te ayudo.",
        "Hola, disculpa. En este momento tengo un pequeño problema de conexión.\n\nPor favor intenta nuevamente en un momento, estaré disponible para ayudarte.",
        "Hola, perdona el inconveniente. Parece que estoy experimentando una falla temporal.\n\nIntenta de nuevo en unos instantes, no tardará.",
        "Hola, disculpa la demora. Estoy procesando varias solicitudes al mismo tiempo.\n\nGracias por tu paciencia, en un momento estaré contigo.",
      ];
      const msg = fallbacks[Math.floor(Math.random() * fallbacks.length)];
      setMessages((items) => [...items, { role: "assistant", text: msg }]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <ViewHeader title="Asistente de Citas" subtitle="Recepcionista virtual de MedInFlow." />
      <section className="chat-shell">
        <aside className="chat-list">
          <span className="badge info">MedInFlow IA</span>
          <h3>Conversación activa</h3>
          <p className="muted">Puedo ayudarte a agendar tu cita médica.</p>
        </aside>
        <div className="chat-thread">
          <div className="messages">
            {messages.map((message, index) => (
              <div
                key={index}
                className={`message ${message.role === "assistant" ? "ai" : "user"}`}
                style={{ whiteSpace: "pre-wrap" }}
              >
                {message.text}
              </div>
            ))}
            {loading && (
              <div className="message ai" style={{ whiteSpace: "pre-wrap" }}>
                Pensando...
              </div>
            )}
            <div ref={bottomRef} />
          </div>
          <form className="chat-form" onSubmit={submit}>
            <input name="mensaje" placeholder="Escribe tu mensaje..." autoComplete="off" required />
            <button className="btn primary">Enviar</button>
          </form>
        </div>
      </section>
    </>
  );
}

function SettingsView({ context }) {
  const [value, setValue] = React.useState(context.apiUrl);
  return (
    <>
      <ViewHeader title="Ajustes" subtitle="Configuracion local del cliente web." />
      <Section>
        <form
          className="form-grid"
          onSubmit={(event) => {
            event.preventDefault();
            context.setApiUrl(value);
            context.notify("Ajustes guardados");
          }}
        >
          <label className="field full">
            <span>API Spring Boot</span>
            <input value={value} onChange={(event) => setValue(event.target.value)} required />
          </label>
          <label className="field">
            <span>Rol</span>
            <input value={context.role} disabled />
          </label>
          <label className="field">
            <span>Organizacion</span>
            <input value={context.orgId} disabled />
          </label>
          <label className="field full">
            <span>Token</span>
            <textarea value={context.session.token || ""} disabled />
          </label>
          <div className="actions full">
            <button className="btn primary">Guardar ajustes</button>
          </div>
        </form>
      </Section>
    </>
  );
}

function ViewHeader({ title, subtitle, children }) {
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

function Section({ title, badge, children }) {
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

function Metric({ label, value, variant = "" }) {
  return (
    <article className={`metric ${variant}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function DataTable({ rows, columns, support = {}, onOpen, onEdit, onDelete, onChangeStatus, onToggleActive, togglingId, emptyLabel, emptyHint }) {
  if (!rows?.length) return (
    <div className="empty-state">
      <FileText size={36} strokeWidth={1.25} />
      <p className="empty-state-title">{emptyLabel || "No hay registros para mostrar."}</p>
      {emptyHint && <p className="empty-state-hint">{emptyHint}</p>}
    </div>
  );
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map(([, label]) => (
              <th key={label}>{label}</th>
            ))}
            {(onOpen || onEdit || onDelete || onChangeStatus || onToggleActive) && <th className="right">Acciones</th>}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id} className={onToggleActive && row.activo === false ? "row-inactive" : ""}>
              {columns.map(([key, , type]) => (
                <td key={key}>{formatCell(row[key], type, support)}</td>
              ))}
              {(onOpen || onEdit || onDelete || onChangeStatus || onToggleActive) && (
                <td>
                  <div className="row-actions">
                    {onOpen && (
                      <button className="btn icon" onClick={() => onOpen(row)} data-tooltip="Abrir expediente">
                        <FileText size={15} />
                      </button>
                    )}
                    {onToggleActive && (
                      <button
                        className={`btn icon ${row.activo ? "toggle-on" : "toggle-off"}`}
                        onClick={() => onToggleActive(row)}
                        disabled={togglingId === row.id}
                        data-tooltip={row.activo ? "Desactivar paciente" : "Activar paciente"}
                      >
                        <Power size={15} />
                      </button>
                    )}
                    {onChangeStatus && (
                      <button className="btn icon" onClick={() => onChangeStatus(row)} data-tooltip="Cambiar estado">
                        <Activity size={15} />
                      </button>
                    )}
                    {onEdit && (
                      <button className="btn icon" onClick={() => onEdit(row)} data-tooltip="Editar registro">
                        <Save size={15} />
                      </button>
                    )}
                    {onDelete && (
                      <button className="btn icon danger" onClick={() => onDelete(row.id)} data-tooltip="Eliminar registro">
                        <Trash2 size={15} />
                      </button>
                    )}
                  </div>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Field({ label, name, ...props }) {
  return (
    <label className="field">
      <span>{label}</span>
      <input name={name} {...props} />
    </label>
  );
}

function Loading({ label }) {
  return <div className="loading">{label}</div>;
}

function Empty({ label }) {
  return <div className="empty">{label}</div>;
}

function ErrorBox({ message }) {
  return <div className="error-box">{message}</div>;
}

function Toast({ toast }) {
  if (!toast) return null;
  return <div className={`toast ${toast.kind === "error" ? "error" : ""}`}>{toast.message}</div>;
}

function supportEndpoint(name, context) {
  const map = {
    pacientes: `/api/pacientes/organizacion/${context.orgId}`,
    medicos: `/api/medicos/organizacion/${context.orgId}`,
    consultorios: `/api/consultorios/organizacion/${context.orgId}`,
    citas: context.role === "MEDICO" && context.medicoId
      ? `/api/citas/medico/${context.medicoId}`
      : `/api/citas/organizacion/${context.orgId}`,
    usuarios: `/api/usuarios/organizacion/${context.orgId}`,
  };
  return map[name];
}

function requiredSupport(config, role) {
  const names = new Set();
  [...(config.fields || []), ...(config.filters || [])].forEach((field) => {
    if (field.source && field.source !== "adjuntos") names.add(field.source);
  });
  (config.support || []).forEach((name) => names.add(name));
  (config.columns || []).forEach(([, , type]) => {
    if (type === "patient") names.add("pacientes");
    if (type === "doctor") names.add("medicos");
    if (type === "office") names.add("consultorios");
    if (type === "appointment") names.add("citas");
  });
  return [...names];
}

function formPayload(form, fields, context = {}, support = {}, keepEmpty = false) {
  const data = new FormData(form);
  const payload = {};
  fields.forEach((field) => {
    let value = data.get(field.key);
    if (field.type === "hiddenOrg") value = context.orgId;
    if (field.type === "hiddenUser") value = context.userId;
    if (field.type === "checkbox") value = value === "true";
    if (field.type === "number" && value !== "") value = Number(value);
    if (field.type === "datetime-local" && value) value = new Date(value).toISOString();
    if (!keepEmpty && !field.required) value = requestValue(value);
    if (value !== undefined) payload[field.key] = value;
  });
  return payload;
}

function fieldValue(field, item, context, support = {}) {
  if (field.value) return field.value(item || {}, context, support);
  if (field.type === "hiddenOrg") return context.orgId;
  if (field.type === "hiddenUser") return context.userId;
  if (field.type === "datetime-local") return toDatetimeInput(item[field.key]);
  return item[field.key] ?? "";
}

function formatCell(value, type, support) {
  if (value == null || value === "") return <span className="muted">-</span>;
  if (type === "datetime") return formatDate(value);
  if (type === "money") return `$${Number(value).toLocaleString("es-MX", { minimumFractionDigits: 2 })}`;
  if (type === "bool") return <span className={`badge ${value ? "ok" : "danger"}`}>{value ? "SI" : "NO"}</span>;
  if (type === "status") return <span className={`badge ${statusVariant(value)}`}>{String(value)}</span>;
  if (type === "patient") return nameById("pacientes", value, support);
  if (type === "doctor") return nameById("medicos", value, support);
  if (type === "office") return nameById("consultorios", value, support);
  if (type === "appointment") return nameById("citas", value, support);
  if (type === "day") return ["Dom", "Lun", "Mar", "Mie", "Jue", "Vie", "Sab"][Number(value)] || value;
  if (type === "motivo") return <MotivoCellWithModal text={String(value)} />;
  return String(value).length > 42 ? <span className="mono">{shortId(value)}</span> : String(value);
}

function statusVariant(value) {
  const text = String(value);
  if (["CONFIRMADA", "PAGADO", "PRINCIPAL", "ADMIN"].includes(text)) return "ok";
  if (["SIN_CONFIRMAR", "PENDIENTE", "REAGENDADA", "SECUNDARIO", "MEDICO"].includes(text)) return "warn";
  if (["CANCELADA", "NO_ASISTIO", "FALLIDO"].includes(text)) return "danger";
  return "info";
}

function displayName(row, source, support) {
  if (source === "citas") return `${formatDate(row.fechaHora)} / ${nameById("pacientes", row.pacienteId, support)}`;
  if (source === "usuarios") return row.email || row.id;
  if (source === "adjuntos") return row.nombreArchivo || row.id;
  return row.nombre || row.email || shortId(row.id);
}

function nameById(source, id, support) {
  const row = (support[source] || []).find((item) => String(item.id) === String(id));
  return row ? displayName(row, source, support) : shortId(id);
}

function initials(value) {
  return String(value || "MI")
    .split(/[ @._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

function sessionDisplayName(session) {
  const claims = session?.claims || {};
  return (
    session?.nombre ||
    session?.name ||
    claims.nombre ||
    claims.name ||
    claims.nombreCompleto ||
    session?.email ||
    claims.sub ||
    "Usuario"
  );
}

function patientNamePart(value, index) {
  const parts = String(value || "").trim().split(/\s+/).filter(Boolean);
  if (index === 0) return parts[0] || "";
  if (index === 1) return parts[1] || "";
  return parts.slice(2).join(" ");
}

function shortId(value) {
  const text = String(value || "");
  return text.length > 12 ? `${text.slice(0, 8)}...` : text || "-";
}

function formatDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString("es-MX", { dateStyle: "medium", timeStyle: "short" });
}

function toDatetimeInput(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000);
  return local.toISOString().slice(0, 16);
}

// ── Constantes compartidas de diagnósticos ────────────────────────────────────

const CITA_ESTADO_ORDER = { CONFIRMADA: 0, REAGENDADA: 1, SIN_CONFIRMAR: 2, NO_ASISTIO: 3, CANCELADA: 99 };
const CITA_ESTADO_LABEL = {
  CONFIRMADA: "Confirmada", REAGENDADA: "Reagendada",
  SIN_CONFIRMAR: "Sin confirmar", NO_ASISTIO: "No asistió", CANCELADA: "Cancelada",
};
const CITA_ESTADO_V = {
  CONFIRMADA: "ok", REAGENDADA: "warn", SIN_CONFIRMAR: "warn",
  NO_ASISTIO: "danger", CANCELADA: "danger",
};

function citaLabelCompleto(cita, support) {
  if (!cita) return "";
  const f        = new Date(cita.fechaHora);
  const fechaStr = f.toLocaleDateString("es-MX", { day: "2-digit", month: "2-digit", year: "numeric" });
  const horaStr  = f.toLocaleTimeString("es-MX", { hour: "2-digit", minute: "2-digit", hour12: true });
  const nomMed   = (support.medicos || []).find((m) => m.id === cita.medicoId)?.nombre || "—";
  const estado   = CITA_ESTADO_LABEL[cita.estado] || cita.estado;
  return `${fechaStr}  ·  ${horaStr}  ·  ${nomMed}  ·  ${estado}`;
}

// ── CitaResumenCard ───────────────────────────────────────────────────────────

function CitaResumenCard({ cita, support }) {
  const f           = new Date(cita.fechaHora);
  const paciente    = (support.pacientes    || []).find((p) => p.id === cita.pacienteId);
  const medico      = (support.medicos      || []).find((m) => m.id === cita.medicoId);
  const consultorio = (support.consultorios || []).find((c) => c.id === cita.consultorioId);
  const fechaStr    = f.toLocaleDateString("es-MX", { day: "2-digit", month: "2-digit", year: "numeric" });
  const horaStr     = f.toLocaleTimeString("es-MX", { hour: "2-digit", minute: "2-digit", hour12: true });
  const estadoLabel = CITA_ESTADO_LABEL[cita.estado] || cita.estado;
  const estadoV     = CITA_ESTADO_V[cita.estado] || "info";

  return (
    <div className="cita-resumen-card">
      <div className="cita-resumen-card-header">
        <span className="cita-resumen-card-title">Resumen de la consulta</span>
        <span className={`badge ${estadoV}`}>{estadoLabel}</span>
      </div>
      <div className="cita-resumen-card-grid">
        <div className="cita-resumen-card-item"><span>Paciente</span><strong>{paciente?.nombre || "—"}</strong></div>
        <div className="cita-resumen-card-item"><span>Médico</span><strong>{medico?.nombre || "—"}</strong></div>
        <div className="cita-resumen-card-item"><span>Fecha</span><strong>{fechaStr}</strong></div>
        <div className="cita-resumen-card-item"><span>Hora</span><strong>{horaStr}</strong></div>
        <div className="cita-resumen-card-item"><span>Consultorio</span><strong>{consultorio?.nombre || "—"}</strong></div>
        {cita.motivo && (
          <div className="cita-resumen-card-item full"><span>Motivo</span><strong>{cita.motivo}</strong></div>
        )}
      </div>
    </div>
  );
}

// ── Constantes para estados de estudios/adjuntos ──────────────────────────────

const ESTUDIO_ESTADO_V     = { SOLICITADO: "danger", SUBIDO: "warn", REVISADO: "ok" };
const ESTUDIO_ESTADO_LABEL = { SOLICITADO: "Solicitado", SUBIDO: "Subido", REVISADO: "Revisado" };

// ── DiagnosticosModule: vista completa con flujo guiado ───────────────────────

function DiagnosticosModule({ context }) {
  const config = modules.diagnosticos;

  // Datos de soporte
  const [support, setSupport] = React.useState({ pacientes: [], medicos: [], consultorios: [], citas: [] });

  // Cascada paciente → citas → diagnósticos
  const [pacienteSeleccionado, setPacienteSeleccionado] = React.useState(null);
  const [citasDelPaciente, setCitasDelPaciente]         = React.useState([]);
  const [citaSeleccionada, setCitaSeleccionada]         = React.useState(null);
  const [loadingCitas, setLoadingCitas]                 = React.useState(false);
  const [tipoFilter, setTipoFilter]                     = React.useState("");

  // Tabla de diagnósticos
  const [rows, setRows]               = React.useState([]);
  const [loadingRows, setLoadingRows] = React.useState(false);
  const [tableError, setTableError]   = React.useState("");
  const [refreshKey, setRefreshKey]   = React.useState(0);

  // Formulario y confirmaciones
  const [formState, setFormState]           = React.useState(null);
  const [pendingDeleteId, setPendingDeleteId] = React.useState(null);
  const [deleting, setDeleting]             = React.useState(false);

  // ── Cargar datos de soporte al montar ──
  React.useEffect(() => {
    let active = true;
    async function boot() {
      const [pacientes, medicos, consultorios] = await Promise.all([
        context.list(`/api/pacientes/organizacion/${context.orgId}`, true),
        context.list(`/api/medicos/organizacion/${context.orgId}`, true),
        context.list(`/api/consultorios/organizacion/${context.orgId}`, true),
      ]);
      if (active) setSupport({ pacientes, medicos, consultorios, citas: [] });
    }
    boot();
    return () => { active = false; };
  }, []);

  // ── Cargar citas al seleccionar paciente ──
  React.useEffect(() => {
    if (!pacienteSeleccionado) {
      setCitasDelPaciente([]);
      setCitaSeleccionada(null);
      setRows([]);
      setFormState(null);
      setTipoFilter("");
      return;
    }
    let active = true;
    setLoadingCitas(true);
    setCitaSeleccionada(null);
    setRows([]);
    setFormState(null);
    setTipoFilter("");
    context.api(`/api/citas/paciente/${pacienteSeleccionado.id}`)
      .then((data) => {
        if (!active) return;
        const validas = (data || []).filter((c) => c.estado !== "CANCELADA");
        validas.sort((a, b) => (CITA_ESTADO_ORDER[a.estado] ?? 50) - (CITA_ESTADO_ORDER[b.estado] ?? 50));
        setCitasDelPaciente(validas);
      })
      .catch(() => { if (active) setCitasDelPaciente([]); })
      .finally(() => { if (active) setLoadingCitas(false); });
    return () => { active = false; };
  }, [pacienteSeleccionado]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Cargar diagnósticos cuando cambia cita, tipo o refreshKey ──
  React.useEffect(() => {
    if (!citaSeleccionada) { setRows([]); setTableError(""); return; }
    let active = true;
    setLoadingRows(true);
    setTableError("");
    const path = tipoFilter
      ? `/api/diagnosticos/cita/${citaSeleccionada.id}/tipo/${tipoFilter}`
      : `/api/diagnosticos/cita/${citaSeleccionada.id}`;
    context.list(path, true)
      .then((data) => { if (active) setRows(data); })
      .catch((err)  => { if (active) setTableError(err.message); })
      .finally(()   => { if (active) setLoadingRows(false); });
    return () => { active = false; };
  }, [citaSeleccionada, tipoFilter, refreshKey]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Guardar diagnóstico ──
  async function saveEntity(payload) {
    try {
      const editing = formState?.mode === "edit";
      await context.api(
        editing ? `${config.endpoint}/${formState.item.id}` : config.endpoint,
        { method: editing ? "PUT" : "POST", body: JSON.stringify(payload) }
      );
      context.notify(editing ? "Diagnóstico actualizado" : "Diagnóstico registrado");
      setFormState(null);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      context.notify(err.message, "error");
    }
  }

  // ── Eliminar diagnóstico ──
  async function confirmDelete() {
    setDeleting(true);
    try {
      await context.api(`${config.endpoint}/${pendingDeleteId}`, { method: "DELETE" });
      context.notify("Diagnóstico eliminado");
      setPendingDeleteId(null);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      context.notify(err.message, "error");
    } finally {
      setDeleting(false);
    }
  }

  const hasCitas      = citasDelPaciente.length > 0;
  const supportForm   = { ...support, citas: citasDelPaciente };

  return (
    <>
      <ViewHeader title="Diagnósticos" subtitle="Diagnósticos clínicos asociados a consultas médicas.">
        <button className="btn" onClick={() => setRefreshKey((k) => k + 1)}>
          <RefreshCw size={16} /> Actualizar
        </button>
        <button
          className="btn primary"
          disabled={!citaSeleccionada}
          title={!citaSeleccionada ? "Selecciona una cita para registrar un diagnóstico" : undefined}
          onClick={() => setFormState({ mode: "new", item: {} })}
        >
          Nuevo
        </button>
      </ViewHeader>

      {/* ── Panel de selección guiada ── */}
      <section className="section filter-section diagnosticos-filter-section">

        <div className="diagnosticos-filter">

          {/* ── Paso 1: Paciente ── */}
          <div className={`diagnosticos-filter-step ${!pacienteSeleccionado ? "step-active" : "step-done"}`}>
            <div className="diagnosticos-filter-step-label">
              <span className={`step-badge ${pacienteSeleccionado ? "step-badge-done" : ""}`}>
                {pacienteSeleccionado ? "✓" : "1"}
              </span>
              <div className="diagnosticos-step-texts">
                <span className="diagnosticos-step-title">Buscar paciente</span>
                <span className="diagnosticos-step-subtitle">Nombre, apellidos o nombre completo</span>
              </div>
            </div>
            <ComboboxField
              full={false}
              label=""
              items={support.pacientes}
              displayFn={(p) => p.nombre}
              value={pacienteSeleccionado}
              onSelect={(p) => setPacienteSeleccionado(p)}
              onClear={() => setPacienteSeleccionado(null)}
              placeholder="Escribe para buscar…"
            />
          </div>

          {/* ── Paso 2: Cita ── */}
          <div className={`diagnosticos-filter-step ${
            !pacienteSeleccionado
              ? "step-inactive"
              : !citaSeleccionada
                ? "step-active"
                : "step-done"
          }`}>
            <div className="diagnosticos-filter-step-label">
              <span className={`step-badge ${
                !pacienteSeleccionado
                  ? "step-badge-inactive"
                  : citaSeleccionada
                    ? "step-badge-done"
                    : ""
              }`}>
                {citaSeleccionada ? "✓" : "2"}
              </span>
              <div className="diagnosticos-step-texts">
                <span className="diagnosticos-step-title">Seleccionar consulta</span>
                <span className="diagnosticos-step-subtitle">Fecha, hora y médico de la cita</span>
              </div>
            </div>
            {!pacienteSeleccionado ? (
              <div className="combobox-disabled-hint">
                Selecciona un paciente en el paso anterior
              </div>
            ) : loadingCitas ? (
              <div className="combobox-disabled-hint">Cargando consultas…</div>
            ) : !hasCitas ? (
              <div className="combobox-no-citas">
                Este paciente no tiene consultas disponibles.
              </div>
            ) : (
              <ComboboxField
                full={false}
                label=""
                items={citasDelPaciente}
                displayFn={(c) => citaLabelCompleto(c, support)}
                value={citaSeleccionada}
                onSelect={(c) => { setCitaSeleccionada(c); setFormState(null); }}
                onClear={() => { setCitaSeleccionada(null); setFormState(null); }}
                placeholder="Buscar por fecha o médico…"
              />
            )}
          </div>
        </div>

        {/* Filtro de tipo cuando hay cita */}
        {citaSeleccionada && (
          <div className="diagnosticos-tipo-row">
            <span className="diagnosticos-tipo-label">Filtrar por tipo:</span>
            <select
              className="diagnosticos-tipo-select"
              value={tipoFilter}
              onChange={(e) => setTipoFilter(e.target.value)}
            >
              <option value="">Todos los tipos</option>
              <option value="PRINCIPAL">Principal</option>
              <option value="SECUNDARIO">Secundario</option>
            </select>
          </div>
        )}

        {/* Tarjeta resumen de la consulta seleccionada */}
        {citaSeleccionada && <CitaResumenCard cita={citaSeleccionada} support={support} />}
      </section>

      {/* ── Tabla + Formulario ── */}
      <div className="split">
        <Section
          title={
            !citaSeleccionada
              ? "Selecciona una consulta"
              : loadingRows
                ? "Cargando…"
                : `${rows.length} diagnóstico${rows.length !== 1 ? "s" : ""}`
          }
        >
          {!citaSeleccionada ? (
            <div className="empty-state">
              <FileText size={36} strokeWidth={1.25} />
              <p className="empty-state-title">Sin consulta seleccionada</p>
              <p className="empty-state-hint">Busca un paciente y selecciona una consulta para ver sus diagnósticos.</p>
            </div>
          ) : tableError ? (
            <ErrorBox message={tableError} />
          ) : (
            <DataTable
              rows={rows}
              columns={config.columns}
              support={supportForm}
              onEdit={(row) => setFormState({ mode: "edit", item: row })}
              onDelete={(id) => setPendingDeleteId(id)}
              emptyLabel="Sin diagnósticos para esta consulta."
              emptyHint="Presiona «Nuevo» para registrar el primer diagnóstico."
            />
          )}
        </Section>

        <Section title={formState?.mode === "edit" ? "Editar diagnóstico" : "Nuevo diagnóstico"}>
          {formState ? (
            <DiagnosticoForm
              item={formState.item}
              context={context}
              support={supportForm}
              onCancel={() => setFormState(null)}
              onSubmit={saveEntity}
              initialPaciente={pacienteSeleccionado}
              initialCita={citaSeleccionada}
            />
          ) : (
            <div className="empty-capture">
              <FileText size={36} strokeWidth={1.25} />
              <p className="empty-capture-title">Nada seleccionado</p>
              <p className="empty-capture-hint">
                {citaSeleccionada
                  ? <>{`Elige un diagnóstico para editarlo,`}<br />o pulsa <strong>Nuevo</strong> para crear uno.</>
                  : <>Selecciona una consulta para<br />gestionar sus diagnósticos.</>}
              </p>
            </div>
          )}
        </Section>
      </div>

      {pendingDeleteId && (
        <ConfirmModal
          title="Confirmar eliminación"
          message="¿Estás seguro de eliminar este diagnóstico? Esta acción no se puede deshacer."
          loading={deleting}
          onCancel={() => setPendingDeleteId(null)}
          onConfirm={confirmDelete}
        />
      )}
    </>
  );
}

// ── Helpers para carga de archivos ────────────────────────────────────────────

/** Sube un archivo con XHR para obtener progreso real de la carga (0-100). */
function uploadFileXHR(url, file, token, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const formData = new FormData();
    formData.append("archivo", file);

    xhr.upload.addEventListener("progress", (e) => {
      if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100));
    });

    xhr.addEventListener("load", () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try { resolve(JSON.parse(xhr.responseText)); } catch { resolve(null); }
      } else {
        try {
          const body = JSON.parse(xhr.responseText);
          reject(new Error(body.message || body.mensaje || body.detail || `HTTP ${xhr.status}`));
        } catch {
          reject(new Error(`HTTP ${xhr.status} — Error al subir el archivo`));
        }
      }
    });

    xhr.addEventListener("error", () => reject(new Error("Error de red al subir el archivo")));
    xhr.addEventListener("abort", () => reject(new Error("Carga cancelada")));

    xhr.open("POST", url);
    if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    xhr.send(formData);
  });
}

/** Formatea bytes en B, KB o MB legibles. */
function formatFileSize(bytes) {
  if (!bytes) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Devuelve true si el tipo MIME o la extensión del nombre es imagen. */
function esTipoImagen(mime, nombre) {
  const imgMimes = ["image/jpeg", "image/jpg", "image/png", "image/webp"];
  if (mime && imgMimes.includes(mime.toLowerCase())) return true;
  if (nombre) return ["jpg", "jpeg", "png", "webp"].includes(nombre.split(".").pop().toLowerCase());
  return false;
}

/** Devuelve true si el tipo MIME o la extensión del nombre es PDF. */
function esTipoPdf(mime, nombre) {
  if (mime && mime.toLowerCase() === "application/pdf") return true;
  if (nombre) return nombre.split(".").pop().toLowerCase() === "pdf";
  return false;
}

/**
 * Genera una URL de descarga forzada para archivos almacenados en Supabase Storage.
 * Supabase acepta el query param `?download={nombre}` para que el navegador descargue
 * el archivo en lugar de abrirlo en línea, incluso en URLs de origen cruzado.
 *
 * Si la URL ya contiene parámetros de consulta, el param se añade con "&".
 */
function supabaseUrlDescarga(url, nombreArchivo) {
  if (!url) return url;
  const encoded = encodeURIComponent(nombreArchivo || "archivo");
  const sep = url.includes("?") ? "&" : "?";
  return `${url}${sep}download=${encoded}`;
}

// ── AdjuntosModule ────────────────────────────────────────────────────────────

function AdjuntosModule({ context }) {
  if (context.role === "PACIENTE") return <AdjuntosModulePaciente context={context} />;
  return <AdjuntosModuleMedico context={context} />;
}

// ── AdjuntosModuleMedico (ADMIN / MEDICO) ─────────────────────────────────────

function AdjuntosModuleMedico({ context }) {
  const [support, setSupport] = React.useState({ pacientes: [], medicos: [], consultorios: [] });

  const [pacienteSeleccionado, setPacienteSeleccionado] = React.useState(null);
  const [citasDelPaciente, setCitasDelPaciente]         = React.useState([]);
  const [citaSeleccionada, setCitaSeleccionada]         = React.useState(null);
  const [loadingCitas, setLoadingCitas]                 = React.useState(false);

  const [rows, setRows]               = React.useState([]);
  const [loadingRows, setLoadingRows] = React.useState(false);
  const [tableError, setTableError]   = React.useState("");
  const [refreshKey, setRefreshKey]   = React.useState(0);

  const [showSolicitar, setShowSolicitar] = React.useState(false);
  const [solicTipo, setSolicTipo]         = React.useState("");
  const [soliciting, setSoliciting]       = React.useState(false);

  const [revisingId, setRevisingId]         = React.useState(null);
  const [revisingComment, setRevisingComment] = React.useState("");
  const [revising, setRevising]             = React.useState(false);

  React.useEffect(() => {
    let active = true;
    async function boot() {
      const [pacientes, medicos, consultorios] = await Promise.all([
        context.list(`/api/pacientes/organizacion/${context.orgId}`, true),
        context.list(`/api/medicos/organizacion/${context.orgId}`, true),
        context.list(`/api/consultorios/organizacion/${context.orgId}`, true),
      ]);
      if (active) setSupport({ pacientes, medicos, consultorios });
    }
    boot();
    return () => { active = false; };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  React.useEffect(() => {
    if (!pacienteSeleccionado) {
      setCitasDelPaciente([]);
      setCitaSeleccionada(null);
      setRows([]);
      return;
    }
    let active = true;
    setLoadingCitas(true);
    setCitaSeleccionada(null);
    setRows([]);
    context.api(`/api/citas/paciente/${pacienteSeleccionado.id}`)
      .then((data) => {
        if (!active) return;
        const validas = (data || []).filter((c) => c.estado !== "CANCELADA");
        validas.sort((a, b) => (CITA_ESTADO_ORDER[a.estado] ?? 50) - (CITA_ESTADO_ORDER[b.estado] ?? 50));
        setCitasDelPaciente(validas);
      })
      .catch(() => { if (active) setCitasDelPaciente([]); })
      .finally(() => { if (active) setLoadingCitas(false); });
    return () => { active = false; };
  }, [pacienteSeleccionado]); // eslint-disable-line react-hooks/exhaustive-deps

  React.useEffect(() => {
    if (!citaSeleccionada) { setRows([]); setTableError(""); return; }
    let active = true;
    setLoadingRows(true);
    setTableError("");
    context.list(`/api/adjuntos/cita/${citaSeleccionada.id}`, true)
      .then((data) => { if (active) setRows(data || []); })
      .catch((err)  => { if (active) setTableError(err.message); })
      .finally(()   => { if (active) setLoadingRows(false); });
    return () => { active = false; };
  }, [citaSeleccionada, refreshKey]); // eslint-disable-line react-hooks/exhaustive-deps

  async function handleSolicitar() {
    if (!solicTipo.trim()) { context.notify("Indica el tipo de estudio", "error"); return; }
    setSoliciting(true);
    try {
      await context.api("/api/adjuntos/solicitar", {
        method: "POST",
        body: JSON.stringify({
          organizacionId: context.orgId,
          pacienteId: pacienteSeleccionado.id,
          citaId: citaSeleccionada.id,
          subidoPorId: context.userId,
          tipo: solicTipo.trim(),
        }),
      });
      context.notify("Estudio solicitado");
      setShowSolicitar(false);
      setSolicTipo("");
      setRefreshKey((k) => k + 1);
    } catch (err) {
      context.notify(err.message, "error");
    } finally {
      setSoliciting(false);
    }
  }

  async function handleRevisar() {
    setRevising(true);
    try {
      await context.api(`/api/adjuntos/${revisingId}/revisar`, {
        method: "PATCH",
        body: JSON.stringify({ comentario: revisingComment, estado: "REVISADO" }),
      });
      context.notify("Estudio revisado");
      setRevisingId(null);
      setRevisingComment("");
      setRefreshKey((k) => k + 1);
    } catch (err) {
      context.notify(err.message, "error");
    } finally {
      setRevising(false);
    }
  }

  const hasCitas = citasDelPaciente.length > 0;

  return (
    <>
      <ViewHeader title="Adjuntos y Estudios" subtitle="Seguimiento de estudios médicos solicitados a pacientes.">
        <button className="btn" onClick={() => setRefreshKey((k) => k + 1)}>
          <RefreshCw size={16} /> Actualizar
        </button>
        <button
          className="btn primary"
          disabled={!citaSeleccionada}
          title={!citaSeleccionada ? "Selecciona una cita para solicitar un estudio" : undefined}
          onClick={() => { setShowSolicitar(true); setRevisingId(null); }}
        >
          + Solicitar estudio
        </button>
      </ViewHeader>

      {/* ── Selección guiada ── */}
      <section className="section filter-section diagnosticos-filter-section">
        <div className="diagnosticos-filter">

          {/* Paso 1 */}
          <div className={`diagnosticos-filter-step ${!pacienteSeleccionado ? "step-active" : "step-done"}`}>
            <div className="diagnosticos-filter-step-label">
              <span className={`step-badge ${pacienteSeleccionado ? "step-badge-done" : ""}`}>
                {pacienteSeleccionado ? "✓" : "1"}
              </span>
              <div className="diagnosticos-step-texts">
                <span className="diagnosticos-step-title">Buscar paciente</span>
                <span className="diagnosticos-step-subtitle">Nombre o apellidos</span>
              </div>
            </div>
            <ComboboxField
              full={false}
              label=""
              items={support.pacientes}
              displayFn={(p) => p.nombre}
              value={pacienteSeleccionado}
              onSelect={(p) => setPacienteSeleccionado(p)}
              onClear={() => setPacienteSeleccionado(null)}
              placeholder="Escribe para buscar…"
            />
          </div>

          {/* Paso 2 */}
          <div className={`diagnosticos-filter-step ${
            !pacienteSeleccionado ? "step-inactive"
              : !citaSeleccionada ? "step-active" : "step-done"
          }`}>
            <div className="diagnosticos-filter-step-label">
              <span className={`step-badge ${
                !pacienteSeleccionado ? "step-badge-inactive"
                  : citaSeleccionada ? "step-badge-done" : ""
              }`}>
                {citaSeleccionada ? "✓" : "2"}
              </span>
              <div className="diagnosticos-step-texts">
                <span className="diagnosticos-step-title">Seleccionar consulta</span>
                <span className="diagnosticos-step-subtitle">Fecha, hora y médico de la cita</span>
              </div>
            </div>
            {!pacienteSeleccionado ? (
              <div className="combobox-disabled-hint">Selecciona un paciente primero</div>
            ) : loadingCitas ? (
              <div className="combobox-disabled-hint">Cargando consultas…</div>
            ) : !hasCitas ? (
              <div className="combobox-no-citas">Este paciente no tiene consultas disponibles.</div>
            ) : (
              <ComboboxField
                full={false}
                label=""
                items={citasDelPaciente}
                displayFn={(c) => citaLabelCompleto(c, support)}
                value={citaSeleccionada}
                onSelect={(c) => { setCitaSeleccionada(c); setShowSolicitar(false); setRevisingId(null); }}
                onClear={() => { setCitaSeleccionada(null); setShowSolicitar(false); setRevisingId(null); }}
                placeholder="Buscar por fecha o médico…"
              />
            )}
          </div>
        </div>

        {citaSeleccionada && <CitaResumenCard cita={citaSeleccionada} support={support} />}
      </section>

      {/* ── Tabla + Panel ── */}
      <div className="split">
        <Section
          title={
            !citaSeleccionada ? "Selecciona una consulta"
              : loadingRows ? "Cargando…"
              : `${rows.length} estudio${rows.length !== 1 ? "s" : ""}`
          }
        >
          {!citaSeleccionada ? (
            <div className="empty-state">
              <Upload size={36} strokeWidth={1.25} />
              <p className="empty-state-title">Sin consulta seleccionada</p>
              <p className="empty-state-hint">Busca un paciente y selecciona una consulta para ver sus estudios.</p>
            </div>
          ) : tableError ? (
            <ErrorBox message={tableError} />
          ) : rows.length === 0 ? (
            <div className="empty-state">
              <Upload size={36} strokeWidth={1.25} />
              <p className="empty-state-title">Sin estudios</p>
              <p className="empty-state-hint">Pulsa «Solicitar estudio» para registrar el primero.</p>
            </div>
          ) : (
            <div className="estudios-list">
              {rows.map((e) => (
                <div key={e.id} className={`estudio-row estudio-row-${ESTUDIO_ESTADO_V[e.estado] || "info"}`}>
                  <div className="estudio-row-header">
                    <span className="estudio-tipo">{e.tipo}</span>
                    <span className={`badge ${ESTUDIO_ESTADO_V[e.estado] || "info"}`}>
                      {ESTUDIO_ESTADO_LABEL[e.estado] || e.estado}
                    </span>
                  </div>
                  {e.nombreArchivo && e.urlArchivo && (
                    esTipoImagen(e.mimeType, e.nombreArchivo) ? (
                      <div className="estudio-thumbnail-wrap">
                        <img
                          className="estudio-thumbnail"
                          src={e.urlArchivo}
                          alt={e.nombreArchivo}
                          loading="lazy"
                        />
                        <a
                          href={e.urlArchivo}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="estudio-thumbnail-label"
                        >
                          {e.nombreArchivo}
                        </a>
                      </div>
                    ) : esTipoPdf(e.mimeType, e.nombreArchivo) ? (
                      <div className="estudio-pdf-actions">
                        <FileText size={18} className="pdf-icon" />
                        <span className="pdf-nombre">{e.nombreArchivo}</span>
                        <a
                          href={e.urlArchivo}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="btn small"
                        >
                          Ver
                        </a>
                        <a
                          href={supabaseUrlDescarga(e.urlArchivo, e.nombreArchivo)}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="btn small"
                        >
                          Descargar
                        </a>
                      </div>
                    ) : (
                      <div className="estudio-archivo">
                        <FileText size={13} />
                        <a href={e.urlArchivo} target="_blank" rel="noopener noreferrer">{e.nombreArchivo}</a>
                      </div>
                    )
                  )}
                  {e.nombreArchivo && !e.urlArchivo && (
                    <div className="estudio-archivo">
                      <FileText size={13} />
                      <span>{e.nombreArchivo}</span>
                    </div>
                  )}
                  {e.comentarioRevision && (
                    <div className="estudio-comentario">
                      <span className="estudio-comentario-label">Observación médica:</span>
                      {e.comentarioRevision}
                    </div>
                  )}
                  <div className="estudio-row-footer">
                    <span className="estudio-fecha">{formatDate(e.createdAt)}</span>
                    {e.estado === "SUBIDO" && (
                      <button
                        className="btn small"
                        onClick={() => { setRevisingId(e.id); setRevisingComment(""); setShowSolicitar(false); }}
                      >
                        Revisar
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </Section>

        <Section title={revisingId ? "Revisar resultado" : showSolicitar ? "Solicitar estudio" : "Acciones"}>
          {revisingId ? (
            <div className="estudio-panel">
              <p className="estudio-panel-hint">
                Deja un comentario sobre el resultado del estudio. Quedará visible para el paciente.
              </p>
              <label className="field full">
                <span>Observación / comentario</span>
                <textarea
                  rows={4}
                  value={revisingComment}
                  onChange={(ev) => setRevisingComment(ev.target.value)}
                  placeholder="Ej: Niveles dentro de rango normal. Se recomienda…"
                />
              </label>
              <div className="form-actions">
                <button className="btn" onClick={() => { setRevisingId(null); setRevisingComment(""); }}>
                  Cancelar
                </button>
                <button className="btn primary" disabled={revising} onClick={handleRevisar}>
                  {revising ? "Guardando…" : "Confirmar revisión"}
                </button>
              </div>
            </div>
          ) : showSolicitar ? (
            <div className="estudio-panel">
              <p className="estudio-panel-hint">
                Especifica el tipo de estudio que necesitas del paciente.
              </p>
              <label className="field full">
                <span>Tipo de estudio *</span>
                <input
                  type="text"
                  value={solicTipo}
                  onChange={(ev) => setSolicTipo(ev.target.value)}
                  placeholder="Ej: Hemograma, Rx Tórax, Eco Abdominal…"
                />
              </label>
              <div className="form-actions">
                <button className="btn" onClick={() => { setShowSolicitar(false); setSolicTipo(""); }}>
                  Cancelar
                </button>
                <button className="btn primary" disabled={soliciting} onClick={handleSolicitar}>
                  {soliciting ? "Enviando…" : "Solicitar"}
                </button>
              </div>
            </div>
          ) : (
            <div className="empty-capture">
              <Upload size={36} strokeWidth={1.25} />
              <p className="empty-capture-title">Nada seleccionado</p>
              <p className="empty-capture-hint">
                {citaSeleccionada
                  ? <>{`Pulsa «Revisar» en un estudio subido,`}<br />o crea uno con <strong>Solicitar estudio</strong>.</>
                  : <>Selecciona una consulta para<br />gestionar sus estudios.</>}
              </p>
            </div>
          )}
        </Section>
      </div>
    </>
  );
}

// ── AdjuntosModulePaciente (PACIENTE) ─────────────────────────────────────────

function AdjuntosModulePaciente({ context }) {
  const [rows, setRows]             = React.useState([]);
  const [loading, setLoading]       = React.useState(true);
  const [error, setError]           = React.useState("");
  const [refreshKey, setRefreshKey] = React.useState(0);

  // Estado del formulario de carga
  const [uploadId, setUploadId]         = React.useState(null);
  const [selectedFile, setSelectedFile] = React.useState(null);
  const [isDragOver, setIsDragOver]     = React.useState(false);
  const [progress, setProgress]         = React.useState(0);
  const [uploading, setUploading]       = React.useState(false);
  const [uploadDone, setUploadDone]     = React.useState(false);
  const fileInputRef                    = React.useRef(null);

  React.useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    context.list("/api/adjuntos/mis-estudios", false)
      .then((data) => { if (active) setRows(data || []); })
      .catch((err)  => { if (active) setError(err.message); })
      .finally(()   => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [refreshKey]); // eslint-disable-line react-hooks/exhaustive-deps

  function abrirUpload(estudioId) {
    setUploadId(estudioId);
    setSelectedFile(null);
    setProgress(0);
    setUploadDone(false);
    setIsDragOver(false);
  }

  function cancelarUpload() {
    setUploadId(null);
    setSelectedFile(null);
    setProgress(0);
    setUploadDone(false);
    if (fileInputRef.current) fileInputRef.current.value = "";
  }

  function onFileDrop(ev) {
    ev.preventDefault();
    setIsDragOver(false);
    const f = ev.dataTransfer.files?.[0];
    if (f) setSelectedFile(f);
  }

  function onFileChange(ev) {
    const f = ev.target.files?.[0];
    if (f) setSelectedFile(f);
  }

  async function handleUpload() {
    if (!selectedFile) { context.notify("Selecciona un archivo para continuar", "error"); return; }
    setUploading(true);
    setProgress(0);
    try {
      await uploadFileXHR(
        `${context.apiUrl}/api/adjuntos/${uploadId}/upload`,
        selectedFile,
        context.session?.token,
        setProgress
      );
      setUploadDone(true);
      // Tras 2.5 s mostramos el mensaje de éxito y recargamos la lista
      window.setTimeout(() => {
        cancelarUpload();
        setRefreshKey((k) => k + 1);
      }, 2500);
    } catch (err) {
      context.notify(err.message || "Error al subir el archivo", "error");
    } finally {
      setUploading(false);
    }
  }

  const ORDEN_ESTADO = { SOLICITADO: 0, SUBIDO: 1, REVISADO: 2 };
  const sorted = [...rows].sort((a, b) => (ORDEN_ESTADO[a.estado] ?? 9) - (ORDEN_ESTADO[b.estado] ?? 9));

  return (
    <>
      <ViewHeader title="Mis Estudios" subtitle="Estudios médicos solicitados por tu médico.">
        <button className="btn" onClick={() => setRefreshKey((k) => k + 1)}>
          <RefreshCw size={16} /> Actualizar
        </button>
      </ViewHeader>

      <div className="section estudios-paciente-section">
        {loading ? (
          <div className="empty-state"><p>Cargando estudios…</p></div>
        ) : error ? (
          <ErrorBox message={error} />
        ) : rows.length === 0 ? (
          <div className="empty-state">
            <Upload size={36} strokeWidth={1.25} />
            <p className="empty-state-title">Sin estudios</p>
            <p className="empty-state-hint">Tu médico aún no ha solicitado ningún estudio.</p>
          </div>
        ) : (
          <div className="estudios-paciente-list">
            {sorted.map((e) => {
              return (
              <div key={e.id} className={`estudio-card estudio-card-${ESTUDIO_ESTADO_V[e.estado] || "info"}`}>

                {/* ── Encabezado ── */}
                <div className="estudio-card-header">
                  <div className="estudio-card-title-group">
                    <span className="estudio-tipo">{e.tipo}</span>
                    <span className="estudio-fecha-small">{formatDate(e.createdAt)}</span>
                  </div>
                  <span className={`badge ${ESTUDIO_ESTADO_V[e.estado] || "info"}`}>
                    {ESTUDIO_ESTADO_LABEL[e.estado] || e.estado}
                  </span>
                </div>

                {/* ── Archivo subido ── */}
                {e.nombreArchivo && e.urlArchivo && (
                  <div className="estudio-archivo">
                    <FileText size={13} />
                    <a href={e.urlArchivo} target="_blank" rel="noopener noreferrer">{e.nombreArchivo}</a>
                  </div>
                )}

                {/* ── Comentario del médico ── */}
                {e.comentarioRevision && (
                  <div className="estudio-comentario-paciente">
                    <span className="estudio-comentario-label">Observación médica:</span>
                    <p>{e.comentarioRevision}</p>
                  </div>
                )}

                {/* ── Zona de carga (solo SOLICITADO) ── */}
                {e.estado === "SOLICITADO" && (
                  uploadId === e.id ? (
                    <div className="estudio-upload-form">

                      {uploadDone ? (
                        /* ── Éxito ── */
                        <div className="upload-success">
                          <span className="upload-success-icon">✓</span>
                          <div>
                            <p className="upload-success-title">Archivo cargado correctamente.</p>
                            <p className="upload-success-hint">Pendiente de revisión médica.</p>
                          </div>
                        </div>

                      ) : !selectedFile ? (
                        /* ── Drop zone ── */
                        <>
                          <div
                            className={`dropzone ${isDragOver ? "drag-over" : ""}`}
                            onDragOver={(ev) => { ev.preventDefault(); setIsDragOver(true); }}
                            onDragLeave={() => setIsDragOver(false)}
                            onDrop={onFileDrop}
                            onClick={() => fileInputRef.current?.click()}
                            role="button"
                            tabIndex={0}
                            onKeyDown={(ev) => ev.key === "Enter" && fileInputRef.current?.click()}
                          >
                            <Upload size={28} strokeWidth={1.5} className="dropzone-icon" />
                            <span className="dropzone-title">Arrastra tu archivo aquí</span>
                            <span className="dropzone-or">o</span>
                            <span className="dropzone-btn">Seleccionar archivo</span>
                            <span className="dropzone-hint">PDF · JPG · PNG · WEBP · máx. 10 MB</span>
                          </div>
                          <input
                            ref={fileInputRef}
                            type="file"
                            accept=".pdf,.jpg,.jpeg,.png,.webp"
                            style={{ display: "none" }}
                            onChange={onFileChange}
                          />
                          <button className="btn" onClick={cancelarUpload}>Cancelar</button>
                        </>

                      ) : (
                        /* ── Preview + progreso ── */
                        <>
                          <div className="file-preview">
                            <FileText size={22} className="file-preview-icon" />
                            <div className="file-preview-info">
                              <span className="file-preview-name">{selectedFile.name}</span>
                              <span className="file-preview-meta">
                                {formatFileSize(selectedFile.size)}
                                {selectedFile.type ? ` · ${selectedFile.type}` : ""}
                              </span>
                            </div>
                            {!uploading && (
                              <button
                                className="file-preview-remove"
                                title="Cambiar archivo"
                                onClick={() => {
                                  setSelectedFile(null);
                                  if (fileInputRef.current) fileInputRef.current.value = "";
                                }}
                              >
                                <X size={14} />
                              </button>
                            )}
                          </div>

                          {uploading && (
                            <div className="upload-progress-wrap">
                              <div className="upload-progress-bar">
                                <div
                                  className="upload-progress-fill"
                                  style={{ width: `${progress}%` }}
                                />
                              </div>
                              <span className="upload-progress-pct">{progress}%</span>
                            </div>
                          )}

                          <div className="form-actions">
                            <button className="btn" disabled={uploading} onClick={cancelarUpload}>
                              Cancelar
                            </button>
                            <button
                              className="btn primary"
                              disabled={uploading}
                              onClick={handleUpload}
                            >
                              {uploading ? "Subiendo…" : "Subir resultado"}
                            </button>
                          </div>
                        </>
                      )}
                    </div>

                  ) : (
                    <button
                      className="btn primary small estudio-upload-btn"
                      onClick={() => abrirUpload(e.id)}
                    >
                      <Upload size={14} /> Subir resultado
                    </button>
                  )
                )}
              </div>
              ); // cierra return (
            })} {/* cierra sorted.map */}
          </div>
        )}
      </div>
    </>
  );
}

createRoot(document.getElementById("app")).render(<App />);
