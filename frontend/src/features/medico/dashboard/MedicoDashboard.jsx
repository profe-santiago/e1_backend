import React from "react";
import { CalendarDays, Users } from "lucide-react";
import { Empty, ErrorBox, Loading, Metric, Section, ViewHeader } from "../../../components/ui";
import {
  formatCell,
  formatDate,
  formatMoney,
  formatTime,
  formatTodayLabel,
  formatWeekday,
  isFuture,
  isToday,
  nameById,
  sliceTime,
  statusVariant,
} from "../../../lib/display";
import "./MedicoDashboard.css";

export default function MedicoDashboard({ context }) {
  const [data, setData] = React.useState(null);
  const [error, setError] = React.useState("");

  React.useEffect(() => {
    let active = true;
    async function load() {
      if (!context.medicoId) {
        setData(null);
        setError("Tu usuario medico no tiene un medico asociado. Un administrador debe asignar medico_id al usuario.");
        return;
      }

      try {
        setError("");
        const [citas, pacientes, pagos, horarios, consultorios] = await Promise.all([
          context.list(`/api/citas/medico/${context.medicoId}`, true),
          context.list(`/api/pacientes/organizacion/${context.orgId}`, true),
          context.list(`/api/pagos/organizacion/${context.orgId}`, true),
          context.list(`/api/horarios/medico/${context.medicoId}`, true),
          context.list(`/api/consultorios/organizacion/${context.orgId}`, true),
        ]);
        if (active) setData({ citas, pacientes, pagos, horarios, consultorios });
      } catch (err) {
        if (active) setError(err.message);
      }
    }
    load();
    return () => {
      active = false;
    };
  }, [context]);

  if (error) {
    return (
      <>
        <ViewHeader title="Dashboard" subtitle={`Bienvenido, ${context.userName}.`} />
        <ErrorBox message={error} />
      </>
    );
  }
  if (!data) return <Loading label="Cargando dashboard medico..." />;

  const support = { pacientes: data.pacientes, consultorios: data.consultorios };
  const sortedCitas = [...data.citas].sort((a, b) => new Date(a.fechaHora) - new Date(b.fechaHora));
  const todayCitas = sortedCitas.filter((cita) => isToday(cita.fechaHora) && cita.estado !== "CANCELADA");
  const upcomingCitas = sortedCitas.filter((cita) => isFuture(cita.fechaHora) && !["CANCELADA", "NO_ASISTIO"].includes(cita.estado)).slice(0, 5);
  const unconfirmed = sortedCitas.filter((cita) => cita.estado === "SIN_CONFIRMAR").length;
  const citaIds = new Set(data.citas.map((cita) => String(cita.id)));
  const pendingPayments = data.pagos.filter((pago) => citaIds.has(String(pago.citaId)) && pago.estado === "PENDIENTE");
  const patientIds = new Set(data.citas.map((cita) => String(cita.pacienteId)));
  const myPatients = data.pacientes.filter((paciente) => patientIds.has(String(paciente.id)));

  return (
    <>
      <ViewHeader title="Dashboard" subtitle={`Bienvenido, ${context.userName}.`}>
        <button className="btn" onClick={() => context.go("modulo/pacientes")}>
          <Users size={16} /> Pacientes
        </button>
        <button className="btn primary" onClick={() => context.go("medico/agenda")}>
          <CalendarDays size={16} /> Mi agenda
        </button>
      </ViewHeader>

      <section className="metrics">
        <Metric label="Citas hoy" value={todayCitas.length} />
        <Metric label="Proximas citas" value={upcomingCitas.length} variant="secondary" />
        <Metric label="Sin confirmar" value={unconfirmed} variant="warning" />
        <Metric label="Pacientes vinculados" value={myPatients.length} variant="danger" />
      </section>

      <div className="doctor-grid">
        <section>
          <Section title="Agenda de hoy" badge={formatTodayLabel()}>
            <AppointmentList rows={todayCitas} support={support} emptyLabel="No tienes citas programadas para hoy." />
          </Section>
          <Section title="Pacientes recientes">
            <DoctorPatientList pacientes={myPatients.slice(0, 6)} citas={sortedCitas} />
          </Section>
        </section>

        <aside>
          <Section title="Proximas 5 citas">
            <AppointmentList rows={upcomingCitas} support={support} emptyLabel="No hay proximas citas en tu agenda." compact />
          </Section>
          <Section title="Horario semanal">
            <DoctorSchedule horarios={data.horarios} />
          </Section>
          <Section title="Pagos pendientes" badge={String(pendingPayments.length)}>
            <DoctorPendingPayments pagos={pendingPayments.slice(0, 4)} support={{ citas: data.citas, pacientes: data.pacientes }} />
          </Section>
        </aside>
      </div>
    </>
  );
}

function AppointmentList({ rows, support, emptyLabel, compact = false }) {
  if (!rows.length) return <Empty label={emptyLabel} />;
  return (
    <div className={`appointment-list ${compact ? "compact" : ""}`}>
      {rows.map((cita) => (
        <article className="appointment-row" key={cita.id}>
          <div className="appointment-time">
            <strong>{formatTime(cita.fechaHora)}</strong>
            <span>{cita.duracionMin || 0} min</span>
          </div>
          <div className="appointment-main">
            <strong>{nameById("pacientes", cita.pacienteId, support)}</strong>
            <span>{cita.motivo || "Consulta medica"}</span>
            <small>{nameById("consultorios", cita.consultorioId, support)}</small>
          </div>
          <span className={`badge ${statusVariant(cita.estado)}`}>{cita.estado}</span>
        </article>
      ))}
    </div>
  );
}

function DoctorPatientList({ pacientes, citas }) {
  if (!pacientes.length) return <Empty label="Aun no hay pacientes ligados a tus citas." />;
  return (
    <div className="patient-mini-list">
      {pacientes.map((paciente) => {
        const nextCita = citas.find((cita) => String(cita.pacienteId) === String(paciente.id) && isFuture(cita.fechaHora));
        return (
          <article className="patient-mini-row" key={paciente.id}>
            <div>
              <strong>{paciente.nombre}</strong>
              <span>{paciente.email || paciente.telefono || "Sin contacto registrado"}</span>
            </div>
            <small>{nextCita ? formatDate(nextCita.fechaHora) : "Sin cita proxima"}</small>
          </article>
        );
      })}
    </div>
  );
}

function DoctorSchedule({ horarios }) {
  if (!horarios.length) return <Empty label="No hay horario registrado para este medico." />;
  const ordered = [...horarios].sort((a, b) => Number(a.diaSemana) - Number(b.diaSemana) || String(a.horaInicio).localeCompare(String(b.horaInicio)));
  return (
    <div className="schedule-list">
      {ordered.map((horario) => (
        <div className="schedule-row" key={horario.id}>
          <span>{formatWeekday(horario.diaSemana)}</span>
          <strong>
            {sliceTime(horario.horaInicio)} - {sliceTime(horario.horaFin)}
          </strong>
          <small>{horario.duracionConsulta} min</small>
        </div>
      ))}
    </div>
  );
}

function DoctorPendingPayments({ pagos, support }) {
  if (!pagos.length) return <Empty label="No hay pagos pendientes ligados a tus citas." />;
  return (
    <div className="payment-list">
      {pagos.map((pago) => (
        <div className="payment-row" key={pago.id}>
          <div>
            <strong>{formatCell(pago.citaId, "appointment", support)}</strong>
            <span>{pago.concepto || pago.metodo}</span>
          </div>
          <strong>{formatMoney(pago.monto)}</strong>
        </div>
      ))}
    </div>
  );
}
