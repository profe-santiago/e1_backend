import React from "react";
import { CalendarDays, CalendarPlus, CreditCard, FileText, Upload } from "lucide-react";
import { Empty, ErrorBox, Loading, Metric, Section, ViewHeader } from "../../../components/ui";
import { formatDate, formatMoney, formatTime, isFuture, sessionDisplayName, shortId, statusVariant } from "../../../lib/display";
import "./PacienteDashboard.css";

export default function PacienteDashboard({ context }) {
  const [data, setData] = React.useState(null);
  const [error, setError] = React.useState("");

  React.useEffect(() => {
    let active = true;
    async function load() {
      if (!context.pacienteId) {
        setError("Tu usuario paciente no tiene un expediente vinculado. Pide al equipo de la clínica que registre tu paciente con este correo.");
        setData(null);
        return;
      }

      try {
        setError("");
        const [paciente, citas, adjuntos, medicos] = await Promise.all([
          context.api(`/api/pacientes/${context.pacienteId}`),
          context.list(`/api/citas/paciente/${context.pacienteId}`, true),
          context.list(`/api/adjuntos/paciente/${context.pacienteId}`, true),
          context.list(`/api/medicos/organizacion/${context.orgId}/activos`, true),
        ]);
        const pagos = await loadPagos(context, citas);
        const diagnosticos = await loadDiagnosticos(context, citas);
        if (active) setData({ paciente, citas, adjuntos, pagos, diagnosticos, medicos });
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
        <ViewHeader title="Mi portal" subtitle={`Bienvenido, ${context.userName}.`} />
        <ErrorBox message={error} />
      </>
    );
  }
  if (!data) return <Loading label="Cargando portal del paciente..." />;

  const sortedCitas = [...data.citas].sort((a, b) => new Date(a.fechaHora) - new Date(b.fechaHora));
  const nextCita = sortedCitas.find((cita) => isFuture(cita.fechaHora) && !["CANCELADA", "NO_ASISTIO"].includes(cita.estado));
  const pendingPayments = data.pagos.filter((pago) => pago.estado === "PENDIENTE");
  const paidPayments = data.pagos.filter((pago) => pago.estado === "PAGADO");
  const totalPending = pendingPayments.reduce((sum, pago) => sum + Number(pago.monto || 0), 0);

  return (
    <>
      <ViewHeader title="Mi portal" subtitle={`Bienvenido, ${displayPatientName(data.paciente, context)}.`}>
        <button className="btn" onClick={() => context.go("paciente/citas")}>
          <CalendarDays size={16} /> Mis citas
        </button>
        <button className="btn primary" onClick={() => context.go("paciente/agendar")}>
          <CalendarPlus size={16} /> Agendar cita
        </button>
      </ViewHeader>

      <section className="metrics">
        <Metric label="Próxima cita" value={nextCita ? formatDate(nextCita.fechaHora) : "Sin cita"} />
        <Metric label="Pagos pendientes" value={formatMoney(totalPending)} variant="warning" />
        <Metric label="Diagnósticos" value={data.diagnosticos.length} variant="secondary" />
        <Metric label="Adjuntos" value={data.adjuntos.length} variant="danger" />
      </section>

      <div className="patient-grid">
        <section>
          <Section title="Próxima atención">
            {nextCita ? <PatientAppointmentCard cita={nextCita} medicos={data.medicos} /> : <Empty label="No tienes citas próximas registradas." />}
          </Section>
          <Section title="Actividad clínica reciente">
            <PatientDiagnosisList rows={data.diagnosticos.slice(0, 5)} />
          </Section>
        </section>

        <aside>
          <Section title="Pagos" badge={String(pendingPayments.length)}>
            <PatientPaymentList rows={[...pendingPayments, ...paidPayments].slice(0, 4)} />
          </Section>
          <Section title="Documentos">
            <PatientAttachmentList rows={data.adjuntos.slice(0, 5)} />
          </Section>
        </aside>
      </div>
    </>
  );
}

function PatientAppointmentCard({ cita, medicos }) {
  return (
    <article className="patient-next-card">
      <div>
        <CalendarDays size={22} />
        <span>{formatDate(cita.fechaHora)}</span>
      </div>
      <h3>{cita.motivo || "Consulta medica"}</h3>
      <dl>
        <div>
          <dt>Hora</dt>
          <dd>{formatTime(cita.fechaHora)}</dd>
        </div>
        <div>
          <dt>Duración</dt>
          <dd>{cita.duracionMin || 0} min</dd>
        </div>
        <div>
          <dt>Médico</dt>
          <dd>{doctorName(cita.medicoId, medicos)}</dd>
        </div>
      </dl>
      <span className={`badge ${statusVariant(cita.estado)}`}>{cita.estado}</span>
    </article>
  );
}

function PatientDiagnosisList({ rows }) {
  if (!rows.length) return <Empty label="Aun no hay diagnosticos publicados en tu expediente." />;
  return (
    <div className="patient-list">
      {rows.map((row) => (
        <article className="patient-list-row" key={row.id}>
          <FileText size={18} />
          <div>
            <strong>{row.descripcion}</strong>
            <span>{row.codigoCie10 || "Sin CIE-10"} / {row.tipo}</span>
          </div>
        </article>
      ))}
    </div>
  );
}

function PatientPaymentList({ rows }) {
  if (!rows.length) return <Empty label="No tienes pagos registrados." />;
  return (
    <div className="patient-list">
      {rows.map((row) => (
        <article className="patient-list-row" key={row.id}>
          <CreditCard size={18} />
          <div>
            <strong>{formatMoney(row.monto)}</strong>
            <span>{row.concepto || row.metodo || "Pago de cita"}</span>
          </div>
          <span className={`badge ${statusVariant(row.estado)}`}>{row.estado}</span>
        </article>
      ))}
    </div>
  );
}

function PatientAttachmentList({ rows }) {
  if (!rows.length) return <Empty label="No tienes documentos adjuntos." />;
  return (
    <div className="patient-list">
      {rows.map((row) => (
        <article className="patient-list-row" key={row.id}>
          <Upload size={18} />
          <div>
            <strong>{row.nombreArchivo}</strong>
            <span>{row.tipo || "Documento"} / {row.visibilidad}</span>
          </div>
        </article>
      ))}
    </div>
  );
}

async function loadPagos(context, citas) {
  const pagos = await Promise.all(
    citas.map((cita) =>
      context
        .api(`/api/pagos/cita/${cita.id}`)
        .then((pago) => pago)
        .catch(() => null)
    )
  );
  return pagos.filter(Boolean);
}

async function loadDiagnosticos(context, citas) {
  const chunks = await Promise.all(citas.map((cita) => context.list(`/api/diagnosticos/cita/${cita.id}`, true)));
  return chunks.flat();
}

function displayPatientName(paciente, context) {
  return paciente?.nombre || sessionDisplayName(context.session);
}

function doctorName(medicoId, medicos = []) {
  return medicos.find((medico) => String(medico.id) === String(medicoId))?.nombre || shortId(medicoId);
}
