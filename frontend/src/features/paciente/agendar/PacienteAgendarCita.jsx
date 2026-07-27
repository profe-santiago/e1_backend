import React from "react";
import { CalendarPlus } from "lucide-react";
import { Empty, ErrorBox, Loading, Section, ViewHeader } from "../../../components/ui";
import "./PacienteAgendarCita.css";

export default function PacienteAgendarCita({ context }) {
  const [medicos, setMedicos] = React.useState(null);
  const [medicoId, setMedicoId] = React.useState("");
  const [fecha, setFecha] = React.useState(todayInput());
  const [duracionMin, setDuracionMin] = React.useState("30");
  const [hora, setHora] = React.useState("");
  const [horas, setHoras] = React.useState([]);
  const [loadingHoras, setLoadingHoras] = React.useState(false);
  const [error, setError] = React.useState("");
  const [submitting, setSubmitting] = React.useState(false);

  React.useEffect(() => {
    let active = true;
    async function loadMedicos() {
      if (!context.pacienteId) {
        setError("Tu usuario paciente no tiene un expediente vinculado.");
        setMedicos([]);
        return;
      }
      try {
        setError("");
        const rows = await context.list(`/api/medicos/organizacion/${context.orgId}/activos`);
        if (active) {
          setMedicos(rows);
          if (rows[0]?.id) setMedicoId(rows[0].id);
        }
      } catch (err) {
        if (active) {
          setError(err.message);
          setMedicos([]);
        }
      }
    }
    loadMedicos();
    return () => {
      active = false;
    };
  }, [context]);

  React.useEffect(() => {
    let active = true;
    async function loadHoras() {
      setHora("");
      setHoras([]);
      if (!medicoId || !fecha) return;
      setLoadingHoras(true);
      try {
        const rows = await context.list(
          `/api/citas/disponibilidad?medicoId=${encodeURIComponent(medicoId)}&fecha=${encodeURIComponent(fecha)}&duracionMin=${encodeURIComponent(duracionMin)}`
        );
        if (active) setHoras(rows);
      } catch (err) {
        if (active) context.notify(err.message, "error");
      } finally {
        if (active) setLoadingHoras(false);
      }
    }
    loadHoras();
    return () => {
      active = false;
    };
  }, [context, medicoId, fecha, duracionMin]);

  async function scheduleAppointment(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const medico = medicos.find((item) => String(item.id) === String(medicoId));
    if (!medico || !hora) {
      context.notify("Selecciona medico, fecha y hora disponible", "error");
      return;
    }

    setSubmitting(true);
    try {
      await context.api("/api/citas", {
        method: "POST",
        body: JSON.stringify({
          organizacionId: context.orgId,
          pacienteId: context.pacienteId,
          medicoId,
          consultorioId: medico.consultorioId,
          fechaHora: new Date(`${fecha}T${hora}:00Z`).toISOString(),
          duracionMin: Number(duracionMin),
          motivo: form.get("motivo"),
        }),
      });
      context.notify("Cita solicitada. Queda pendiente de confirmación.");
      context.go("paciente/citas");
    } catch (err) {
      context.notify(err.message, "error");
    } finally {
      setSubmitting(false);
    }
  }

  if (error) {
    return (
      <>
        <ViewHeader title="Agendar cita" />
        <ErrorBox message={error} />
      </>
    );
  }
  if (!medicos) return <Loading label="Cargando médicos disponibles..." />;

  return (
    <>
      <ViewHeader title="Agendar cita" subtitle="Elige un médico y una hora disponible para solicitar tu cita." />
      <Section title="Solicitud de cita" badge="Paciente">
        {!medicos.length ? (
          <Empty label="No hay médicos activos disponibles para agendar. Revisa que exista al menos un médico activo en tu organización." />
        ) : (
          <form className="patient-schedule-form" onSubmit={scheduleAppointment}>
            <label className="field">
              <span>Médico</span>
              <select value={medicoId} onChange={(event) => setMedicoId(event.target.value)} required disabled={submitting}>
                {medicos.map((medico) => (
                  <option key={medico.id} value={medico.id}>
                    {medico.nombre}{medico.especialidad ? ` / ${medico.especialidad}` : ""}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Fecha</span>
              <input value={fecha} onChange={(event) => setFecha(event.target.value)} type="date" min={todayInput()} required disabled={submitting} />
            </label>
            <label className="field">
              <span>Duración</span>
              <select value={duracionMin} onChange={(event) => setDuracionMin(event.target.value)} required disabled={submitting}>
                <option value="20">20 min</option>
                <option value="30">30 min</option>
                <option value="45">45 min</option>
                <option value="60">60 min</option>
              </select>
            </label>
            <label className="field full">
              <span>Hora disponible</span>
              {loadingHoras ? (
                <div className="slot-state">Buscando horarios...</div>
              ) : horas.length ? (
                <div className="slot-grid">
                  {horas.map((item) => (
                    <button key={item} type="button" className={hora === item ? "active" : ""} onClick={() => setHora(item)} disabled={submitting}>
                      {item}
                    </button>
                  ))}
                </div>
              ) : (
                <div className="slot-state">No hay horas disponibles para esta fecha.</div>
              )}
            </label>
            <label className="field full">
              <span>Motivo</span>
              <textarea name="motivo" placeholder="Describe brevemente el motivo de tu consulta" disabled={submitting} />
            </label>
            <div className="actions full">
              <button className="btn primary" disabled={!hora || submitting}>
                <CalendarPlus size={16} /> {submitting ? "Solicitando..." : "Solicitar cita"}
              </button>
            </div>
          </form>
        )}
      </Section>
    </>
  );
}

function todayInput() {
  const date = new Date();
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000);
  return local.toISOString().slice(0, 10);
}
