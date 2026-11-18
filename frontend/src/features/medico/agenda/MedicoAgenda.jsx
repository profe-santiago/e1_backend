import React from "react";
import { CalendarPlus, ChevronLeft, ChevronRight, Edit3, FileText, RefreshCw, Save, X } from "lucide-react";
import { Empty, ErrorBox, Loading, Metric, Section, ViewHeader, Estado, etiquetaEstado } from "../../../components/ui";
import {
  addDaysKey,
  citaDateKey,
  citaTimeKey,
  clinicToday,
  formatDate,
  formatTime,
  isFuture,
  nameById,
  statusVariant,
} from "../../../lib/display";
import { RecordatorioWhatsApp, useRecordatorioEstados } from "../../whatsapp/RecordatorioWhatsApp";
import "./MedicoAgenda.css";

// La agenda se refresca sola para que aparezca el cambio cuando el paciente responde por WhatsApp
const REFRESCO_MS = 15000;

const STATUS_OPTIONS = ["", "SIN_CONFIRMAR", "CONFIRMADA", "CANCELADA", "REAGENDADA", "NO_ASISTIO"];

export default function MedicoAgenda({ context, fechaInicial, citaResaltada }) {
  const [data, setData] = React.useState(null);
  const [error, setError] = React.useState("");
  const [selectedDate, setSelectedDate] = React.useState(() =>
    /^\d{4}-\d{2}-\d{2}$/.test(fechaInicial || "") ? fechaInicial : todayInput());
  const [statusFilter, setStatusFilter] = React.useState("");
  const [creating, setCreating] = React.useState(false);
  const [editing, setEditing] = React.useState(null);
  const [tick, setTick] = React.useState(0);

  const loadAgenda = React.useCallback(async () => {
    if (!context.medicoId) {
      setData(null);
      setError("Tu usuario médico no tiene un médico asociado.");
      return;
    }

    try {
      setError("");
      const [citas, pacientes, consultorios] = await Promise.all([
        context.list(`/api/citas/medico/${context.medicoId}`, true),
        context.list(`/api/pacientes/organizacion/${context.orgId}`, true),
        context.list(`/api/consultorios/organizacion/${context.orgId}`, true),
      ]);
      setData({ citas: sortAppointments(citas), pacientes, consultorios });
    } catch (err) {
      setError(err.message);
    }
  }, [context]);

  React.useEffect(() => {
    loadAgenda();
  }, [loadAgenda]);

  // Refresco silencioso: solo citas (pacientes y consultorios casi no cambian)
  React.useEffect(() => {
    if (!context.medicoId) return undefined;
    const id = window.setInterval(async () => {
      try {
        const citas = await context.list(`/api/citas/medico/${context.medicoId}`, true);
        setData((prev) => (prev ? { ...prev, citas: sortAppointments(citas) } : prev));
        setTick((t) => t + 1);
      } catch {
        // se reintenta en el siguiente ciclo
      }
    }, REFRESCO_MS);
    return () => window.clearInterval(id);
  }, [context]);

  const citasConRecordatorio = data
    ? data.citas.filter((cita) => sameDate(cita.fechaHora, selectedDate) || isFuture(cita.fechaHora)).map((cita) => cita.id)
    : [];
  const [recordatorios, recargarRecordatorios] = useRecordatorioEstados(context, citasConRecordatorio, tick);

  async function createAppointment(payload) {
    try {
      await context.api("/api/citas", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      context.notify("Cita creada");
      setCreating(false);
      setEditing(null);
      await loadAgenda();
    } catch (err) {
      context.notify(err.message, "error");
    }
  }

  async function updateAppointment(id, payload) {
    try {
      await context.api(`/api/citas/${id}`, {
        method: "PUT",
        body: JSON.stringify(payload),
      });
      context.notify("Cita actualizada");
      setCreating(false);
      setEditing(null);
      await loadAgenda();
    } catch (err) {
      context.notify(err.message, "error");
    }
  }

  if (error) {
    return (
      <>
        <ViewHeader title="Mi agenda" />
        <ErrorBox message={error} />
      </>
    );
  }
  if (!data) return <Loading label="Cargando agenda..." />;

  const support = { pacientes: data.pacientes, consultorios: data.consultorios };
  const citasDelDia = data.citas.filter((cita) => sameDate(cita.fechaHora, selectedDate));
  const visibles = statusFilter ? citasDelDia.filter((cita) => cita.estado === statusFilter) : citasDelDia;
  const proximas = sortAppointments(data.citas.filter((cita) => isFuture(cita.fechaHora))).slice(0, 6);
  const confirmadas = citasDelDia.filter((cita) => cita.estado === "CONFIRMADA").length;
  const pendientes = citasDelDia.filter((cita) => cita.estado === "SIN_CONFIRMAR").length;
  const minutos = citasDelDia.reduce((total, cita) => total + Number(cita.duracionMin || 0), 0);

  return (
    <>
      <ViewHeader title="Agenda">
        <button className="btn" onClick={loadAgenda}>
          <RefreshCw size={16} /> Actualizar
        </button>
        <button className="btn primary" onClick={() => { setCreating(true); setEditing(null); }}>
          <CalendarPlus size={16} /> Nueva cita
        </button>
      </ViewHeader>

      <section className="metrics">
        <Metric label="Citas del día" value={citasDelDia.length} />
        <Metric label="Confirmadas" value={confirmadas} variant="secondary" />
        <Metric label="Por confirmar" value={pendientes} variant="warning" />
        <Metric label="Minutos agendados" value={minutos} />
      </section>

      <section className="section agenda-controls">
        <button className="btn icon" onClick={() => setSelectedDate(shiftDate(selectedDate, -1))} title="Día anterior">
          <ChevronLeft size={18} />
        </button>
        <label className="field">
          <span>Día</span>
          <input type="date" value={selectedDate} onChange={(event) => setSelectedDate(event.target.value)} />
        </label>
        <button className="btn icon" onClick={() => setSelectedDate(shiftDate(selectedDate, 1))} title="Día siguiente">
          <ChevronRight size={18} />
        </button>
        <label className="field">
          <span>Estado</span>
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
            <option value="">Todos</option>
            {STATUS_OPTIONS.filter(Boolean).map((estado) => (
              <option key={estado} value={estado}>{etiquetaEstado(estado)}</option>
            ))}
          </select>
        </label>
      </section>

      <div className="agenda-grid">
        <section>
          <Section title="Día seleccionado" badge={formatAgendaDate(selectedDate)}>
            <AgendaTimeline
              citas={visibles}
              support={support}
              context={context}
              recordatorios={recordatorios}
              onRecordatorio={recargarRecordatorios}
              onOpen={(citaId) => context.go(`medico/consulta/${citaId}`)}
              onEdit={(cita) => { setEditing(cita); setCreating(false); }}
              resaltada={citaResaltada}
            />
          </Section>
        </section>

        <aside>
          {(creating || editing) && (
            <Section title={editing ? "Editar cita" : "Nueva cita"}>
              <AgendaForm
                context={context}
                pacientes={data.pacientes}
                consultorios={data.consultorios}
                selectedDate={selectedDate}
                cita={editing}
                onCancel={() => { setCreating(false); setEditing(null); }}
                onSubmit={(payload) => editing ? updateAppointment(editing.id, payload) : createAppointment(payload)}
              />
            </Section>
          )}
          <Section title="Próximas citas">
            <UpcomingList
              citas={proximas}
              support={support}
              context={context}
              recordatorios={recordatorios}
              onRecordatorio={recargarRecordatorios}
              onOpen={(citaId) => context.go(`medico/consulta/${citaId}`)}
              onEdit={(cita) => { setEditing(cita); setCreating(false); }}
            />
          </Section>
        </aside>
      </div>
    </>
  );
}

function AgendaTimeline({ citas, support, context, recordatorios, onRecordatorio, onOpen, onEdit, resaltada }) {
  const ref = React.useRef(null);
  React.useEffect(() => {
    ref.current?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [resaltada, citas.length]);
  if (!citas.length) return <Empty label="No hay citas para este día." />;
  return (
    <div className="agenda-timeline">
      {citas.map((cita) => (
        <article
          className={`agenda-item ${cita.id === resaltada ? "resaltada" : ""}`}
          key={cita.id}
          ref={cita.id === resaltada ? ref : undefined}
        >
          <div className="agenda-time">
            <strong>{formatTime(cita.fechaHora)}</strong>
            <span>{cita.duracionMin} min</span>
          </div>
          <div className="agenda-detail">
            <strong>{nameById("pacientes", cita.pacienteId, support)}</strong>
            <span>{cita.motivo || "Consulta médica"}</span>
            <small>{nameById("consultorios", cita.consultorioId, support)}</small>
            <RecordatorioWhatsApp context={context} cita={cita} estado={recordatorios[cita.id]} onEnviado={onRecordatorio} />
          </div>
          <div className="agenda-actions">
            <Estado valor={cita.estado} />
            <button className="btn icon" onClick={() => onEdit(cita)} title="Editar cita">
              <Edit3 size={16} />
            </button>
            <button className="btn icon" onClick={() => onOpen(cita.id)} title="Abrir consulta">
              <FileText size={16} />
            </button>
          </div>
        </article>
      ))}
    </div>
  );
}

function UpcomingList({ citas, support, context, recordatorios, onRecordatorio, onOpen, onEdit }) {
  if (!citas.length) return <Empty label="No hay citas próximas." />;
  return (
    <div className="agenda-upcoming">
      {citas.map((cita) => (
        <article className="upcoming-item" key={cita.id}>
          <div>
            <strong>{nameById("pacientes", cita.pacienteId, support)}</strong>
            <span>{formatDate(cita.fechaHora)}</span>
            <RecordatorioWhatsApp context={context} cita={cita} estado={recordatorios[cita.id]} onEnviado={onRecordatorio} compacto />
          </div>
          <div className="agenda-actions">
            <Estado valor={cita.estado} />
            <button className="btn icon" onClick={() => onEdit(cita)} title="Editar cita">
              <Edit3 size={16} />
            </button>
            <button className="btn icon" onClick={() => onOpen(cita.id)} title="Abrir consulta">
              <FileText size={16} />
            </button>
          </div>
        </article>
      ))}
    </div>
  );
}

function AgendaForm({ context, pacientes, consultorios, selectedDate, cita, onCancel, onSubmit }) {
  const canCreate = pacientes.length > 0 && consultorios.length > 0;
  const fecha = cita ? toDateInput(cita.fechaHora) : selectedDate;
  const hora = cita ? toTimeInput(cita.fechaHora) : "09:00";
  const isEditing = Boolean(cita);

  return (
    <form
      className="agenda-form"
      onSubmit={(event) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        onSubmit({
          organizacionId: context.orgId,
          medicoId: context.medicoId,
          pacienteId: data.get("pacienteId"),
          consultorioId: data.get("consultorioId"),
          // Hora de reloj de la clínica guardada tal cual en UTC (10:00 → 10:00Z)
          fechaHora: new Date(`${data.get("fecha")}T${data.get("hora")}:00Z`).toISOString(),
          duracionMin: Number(data.get("duracionMin")),
          estado: data.get("estado") || "SIN_CONFIRMAR",
          motivo: data.get("motivo") || null,
        });
      }}
    >
      {!canCreate && <ErrorBox message="Necesitas al menos un paciente y un consultorio para crear una cita." />}
      <label className="field full">
        <span>Paciente</span>
        <select name="pacienteId" required disabled={!pacientes.length} defaultValue={cita?.pacienteId || ""}>
          {pacientes.map((paciente) => (
            <option key={paciente.id} value={paciente.id}>{paciente.nombre}</option>
          ))}
        </select>
      </label>
      <label className="field full">
        <span>Consultorio</span>
        <select name="consultorioId" required disabled={!consultorios.length} defaultValue={cita?.consultorioId || ""}>
          {consultorios.map((consultorio) => (
            <option key={consultorio.id} value={consultorio.id}>{consultorio.nombre}</option>
          ))}
        </select>
      </label>
      <label className="field">
        <span>Fecha</span>
        <input name="fecha" type="date" defaultValue={fecha} required />
      </label>
      <label className="field">
        <span>Hora</span>
        <input name="hora" type="time" defaultValue={hora} required />
      </label>
      <label className="field">
        <span>Duración</span>
        <select name="duracionMin" defaultValue={String(cita?.duracionMin || 30)} required>
          <option value="20">20 min</option>
          <option value="30">30 min</option>
          <option value="45">45 min</option>
          <option value="60">60 min</option>
        </select>
      </label>
      <label className="field">
        <span>Estado</span>
        <select name="estado" defaultValue={cita?.estado || "SIN_CONFIRMAR"} required>
          {STATUS_OPTIONS.filter(Boolean).map((estado) => (
            <option key={estado} value={estado}>{etiquetaEstado(estado)}</option>
          ))}
        </select>
      </label>
      <label className="field full">
        <span>Motivo</span>
        <textarea name="motivo" rows="4" defaultValue={cita?.motivo || ""} />
      </label>
      <div className="actions full">
        <button className="btn" type="button" onClick={onCancel}>
          <X size={16} /> Cancelar
        </button>
        <button className="btn primary" disabled={!canCreate}>
          <Save size={16} /> {isEditing ? "Actualizar" : "Guardar"}
        </button>
      </div>
    </form>
  );
}

function sortAppointments(citas) {
  return [...citas].sort((a, b) => new Date(a.fechaHora) - new Date(b.fechaHora));
}

// "Hoy" = fecha de la clínica; las citas se comparan por su fecha UTC (convención de reloj en UTC)
function todayInput() {
  return clinicToday();
}

function toDateInput(value) {
  return citaDateKey(value);
}

function toTimeInput(value) {
  return citaTimeKey(value) || "09:00";
}

function sameDate(value, selectedDate) {
  return toDateInput(value) === selectedDate;
}

function shiftDate(value, days) {
  return addDaysKey(value, days);
}

function formatAgendaDate(value) {
  const date = new Date(`${value}T12:00:00Z`);
  return date.toLocaleDateString("es-MX", { weekday: "long", day: "numeric", month: "long", timeZone: "UTC" });
}
