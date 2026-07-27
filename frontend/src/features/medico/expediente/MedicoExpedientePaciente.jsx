import React from "react";
import { ArrowLeft, ExternalLink, RefreshCw } from "lucide-react";
import { Empty, ErrorBox, Loading, Metric, Section, ViewHeader } from "../../../components/ui";
import { formatDate, formatMoney, statusVariant } from "../../../lib/display";
import "./MedicoExpedientePaciente.css";

export default function MedicoExpedientePaciente({ context, pacienteId }) {
  const [data, setData] = React.useState(null);
  const [error, setError] = React.useState("");

  const loadExpediente = React.useCallback(async () => {
    try {
      setError("");
      const [paciente, citas, adjuntos] = await Promise.all([
        context.api(`/api/pacientes/${pacienteId}`),
        context.list(`/api/citas/paciente/${pacienteId}`, true),
        context.list(`/api/adjuntos/paciente/${pacienteId}`, true),
      ]);

      if (String(paciente.organizacionId) !== String(context.orgId)) {
        setData(null);
        setError("Este paciente no pertenece a tu organizacion.");
        return;
      }

      const citasOrdenadas = [...citas].sort((a, b) => new Date(b.fechaHora) - new Date(a.fechaHora));
      const [diagnosticosPorCita, pagosPorCita] = await Promise.all([
        Promise.all(citasOrdenadas.map(async (cita) => [cita.id, await context.list(`/api/diagnosticos/cita/${cita.id}`, true)])),
        Promise.all(citasOrdenadas.map(async (cita) => [cita.id, await context.list(`/api/pagos/cita/${cita.id}`, true)])),
      ]);

      setData({
        paciente,
        citas: citasOrdenadas,
        adjuntos,
        diagnosticos: diagnosticosPorCita.flatMap(([citaId, items]) => items.map((item) => ({ ...item, citaId }))),
        pagos: pagosPorCita.flatMap(([, items]) => items),
      });
    } catch (err) {
      setError(err.message);
    }
  }, [context, pacienteId]);

  React.useEffect(() => {
    loadExpediente();
  }, [loadExpediente]);

  if (error) {
    return (
      <>
        <ViewHeader title="Expediente del paciente">
          <button className="btn" onClick={() => context.go("modulo/pacientes")}>
            <ArrowLeft size={16} /> Pacientes
          </button>
        </ViewHeader>
        <ErrorBox message={error} />
      </>
    );
  }
  if (!data) return <Loading label="Cargando expediente..." />;

  const ultimaCita = data.citas[0];
  const pagosPendientes = data.pagos.filter((pago) => pago.estado === "PENDIENTE").length;

  return (
    <>
      <ViewHeader title="Expediente del paciente">
        <button className="btn" onClick={() => context.go("modulo/pacientes")}>
          <ArrowLeft size={16} /> Pacientes
        </button>
        <button className="btn primary" onClick={loadExpediente}>
          <RefreshCw size={16} /> Actualizar
        </button>
      </ViewHeader>

      <section className="metrics">
        <Metric label="Citas" value={data.citas.length} />
        <Metric label="Diagnosticos" value={data.diagnosticos.length} variant="secondary" />
        <Metric label="Adjuntos" value={data.adjuntos.length} variant="warning" />
        <Metric label="Pagos pendientes" value={pagosPendientes} variant="danger" />
      </section>

      <div className="expediente-grid">
        <section>
          <Section title="Datos generales">
            <PatientCard paciente={data.paciente} ultimaCita={ultimaCita} />
          </Section>

          <Section title="Historial de citas">
            <AppointmentHistory citas={data.citas} onOpen={(citaId) => context.go(`medico/consulta/${citaId}`)} />
          </Section>

          <Section title="Diagnosticos registrados">
            <DiagnosisHistory diagnosticos={data.diagnosticos} citas={data.citas} />
          </Section>
        </section>

        <aside>
          <Section title="Pagos">
            <PaymentHistory pagos={data.pagos} />
          </Section>
          <Section title="Adjuntos">
            <AttachmentHistory adjuntos={data.adjuntos} />
          </Section>
        </aside>
      </div>
    </>
  );
}

function PatientCard({ paciente, ultimaCita }) {
  return (
    <div className="patient-card">
      <div>
        <span>Paciente</span>
        <strong>{paciente.nombre}</strong>
      </div>
      <div>
        <span>Telefono</span>
        <strong>{paciente.telefono || "-"}</strong>
      </div>
      <div>
        <span>Correo</span>
        <strong>{paciente.email || "-"}</strong>
      </div>
      <div>
        <span>Nacimiento</span>
        <strong>{paciente.fechaNacimiento || "-"}</strong>
      </div>
      <div>
        <span>Sexo</span>
        <strong>{paciente.sexo || "-"}</strong>
      </div>
      <div>
        <span>Ultima cita</span>
        <strong>{ultimaCita ? formatDate(ultimaCita.fechaHora) : "-"}</strong>
      </div>
      <div className="wide">
        <span>Notas</span>
        <strong>{paciente.notas || "Sin notas registradas"}</strong>
      </div>
    </div>
  );
}

function AppointmentHistory({ citas, onOpen }) {
  if (!citas.length) return <Empty label="Este paciente aun no tiene citas registradas." />;
  return (
    <div className="history-list">
      {citas.map((cita) => (
        <article className="history-item" key={cita.id}>
          <div>
            <strong>{formatDate(cita.fechaHora)}</strong>
            <span>{cita.motivo || "Consulta medica"}</span>
          </div>
          <span className={`badge ${statusVariant(cita.estado)}`}>{cita.estado}</span>
          <button className="btn" onClick={() => onOpen(cita.id)}>Consulta</button>
        </article>
      ))}
    </div>
  );
}

function DiagnosisHistory({ diagnosticos, citas }) {
  if (!diagnosticos.length) return <Empty label="No hay diagnosticos en el expediente." />;
  return (
    <div className="history-list">
      {diagnosticos.map((diagnostico) => {
        const cita = citas.find((item) => String(item.id) === String(diagnostico.citaId));
        return (
          <article className="diagnosis-history-item" key={diagnostico.id}>
            <div>
              <strong>{diagnostico.codigoCie10 || "Sin CIE-10"}</strong>
              <span>{diagnostico.descripcion}</span>
              <small>{cita ? formatDate(cita.fechaHora) : "Cita no disponible"}</small>
            </div>
            <span className={`badge ${diagnostico.tipo === "PRINCIPAL" ? "ok" : "info"}`}>{diagnostico.tipo || "SIN TIPO"}</span>
          </article>
        );
      })}
    </div>
  );
}

function PaymentHistory({ pagos }) {
  if (!pagos.length) return <Empty label="No hay pagos registrados." />;
  return (
    <div className="side-list">
      {pagos.map((pago) => (
        <article className="side-item" key={pago.id}>
          <div>
            <strong>{formatMoney(pago.monto)}</strong>
            <span>{pago.concepto || pago.metodo}</span>
          </div>
          <span className={`badge ${statusVariant(pago.estado)}`}>{pago.estado}</span>
        </article>
      ))}
    </div>
  );
}

function AttachmentHistory({ adjuntos }) {
  if (!adjuntos.length) return <Empty label="No hay adjuntos registrados." />;
  return (
    <div className="side-list">
      {adjuntos.map((adjunto) => (
        <a className="side-item link-item" href={adjunto.urlArchivo} target="_blank" rel="noreferrer" key={adjunto.id}>
          <div>
            <strong>{adjunto.nombreArchivo}</strong>
            <span>{adjunto.tipo} / {adjunto.mimeType || "archivo"}</span>
          </div>
          <ExternalLink size={16} />
        </a>
      ))}
    </div>
  );
}
