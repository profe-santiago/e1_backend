import React from "react";
import { CalendarDays } from "lucide-react";
import { Empty, ErrorBox, Loading, Section, ViewHeader } from "../../../components/ui";
import { formatDate, formatTime, isFuture, shortId, statusVariant } from "../../../lib/display";
import "./PacienteCitas.css";

export default function PacienteCitas({ context }) {
  const [rows, setRows] = React.useState(null);
  const [medicos, setMedicos] = React.useState([]);
  const [filter, setFilter] = React.useState("todas");
  const [error, setError] = React.useState("");

  React.useEffect(() => {
    let active = true;
    async function load() {
      if (!context.pacienteId) {
        setError("Tu usuario paciente no tiene un expediente vinculado.");
        setRows([]);
        return;
      }
      try {
        setError("");
        const [citas, activeMedicos] = await Promise.all([
          context.list(`/api/citas/paciente/${context.pacienteId}`),
          context.list(`/api/medicos/organizacion/${context.orgId}/activos`, true),
        ]);
        if (active) {
          setRows(sortAppointments(citas));
          setMedicos(activeMedicos);
        }
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
        <ViewHeader title="Mis citas" />
        <ErrorBox message={error} />
      </>
    );
  }
  if (!rows) return <Loading label="Cargando tus citas..." />;

  const visibleRows = rows.filter((cita) => {
    if (filter === "proximas") return isFuture(cita.fechaHora) && !["CANCELADA", "NO_ASISTIO"].includes(cita.estado);
    if (filter === "pasadas") return !isFuture(cita.fechaHora);
    return true;
  });

  return (
    <>
      <ViewHeader title="Mis citas" subtitle="Consulta tu agenda y el historial de atenciones." />
      <section className="patient-tabs" aria-label="Filtros de citas">
        <button className={filter === "todas" ? "active" : ""} onClick={() => setFilter("todas")}>Todas</button>
        <button className={filter === "proximas" ? "active" : ""} onClick={() => setFilter("proximas")}>Próximas</button>
        <button className={filter === "pasadas" ? "active" : ""} onClick={() => setFilter("pasadas")}>Pasadas</button>
      </section>
      <Section title={`${visibleRows.length} citas`}>
        {visibleRows.length ? (
          <div className="patient-appointment-list">
            {visibleRows.map((cita) => (
              <article className="patient-appointment-card" key={cita.id}>
                <div className="patient-appointment-date">
                  <CalendarDays size={20} />
                  <strong>{formatTime(cita.fechaHora)}</strong>
                  <span>{formatDate(cita.fechaHora)}</span>
                </div>
                <div className="patient-appointment-info">
                  <strong>{cita.motivo || "Consulta medica"}</strong>
                  <span>Médico {doctorName(cita.medicoId, medicos)}</span>
                  <small>{cita.duracionMin || 0} min</small>
                </div>
                <span className={`badge ${statusVariant(cita.estado)}`}>{cita.estado}</span>
              </article>
            ))}
          </div>
        ) : (
          <Empty label="No hay citas para este filtro." />
        )}
      </Section>
    </>
  );
}

function sortAppointments(citas) {
  return [...citas].sort((a, b) => new Date(b.fechaHora) - new Date(a.fechaHora));
}

function doctorName(medicoId, medicos = []) {
  return medicos.find((medico) => String(medico.id) === String(medicoId))?.nombre || shortId(medicoId);
}
