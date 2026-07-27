import React from "react";
import { ArrowLeft, ExternalLink, RefreshCw, Save } from "lucide-react";
import { Empty, ErrorBox, Loading, Metric, Section, ViewHeader } from "../../../components/ui";
import { formatDate, formatMoney, nameById, statusVariant } from "../../../lib/display";
import "./MedicoConsultaDetalle.css";

export default function MedicoConsultaDetalle({ context, citaId }) {
  const [data, setData] = React.useState(null);
  const [error, setError] = React.useState("");

  const loadDetail = React.useCallback(async () => {
    try {
      setError("");
      const cita = await context.api(`/api/citas/${citaId}`);
      if (context.role === "MEDICO" && String(cita.medicoId) !== String(context.medicoId)) {
        setData(null);
        setError("Esta cita no pertenece al medico de tu usuario.");
        return;
      }

      const [paciente, diagnosticos, adjuntos, pagos, consultorios] = await Promise.all([
        context.api(`/api/pacientes/${cita.pacienteId}`),
        context.list(`/api/diagnosticos/cita/${cita.id}`, true),
        context.list(`/api/adjuntos/cita/${cita.id}`, true),
        context.list(`/api/pagos/cita/${cita.id}`, true),
        context.list(`/api/consultorios/organizacion/${context.orgId}`, true),
      ]);
      setData({ cita, paciente, diagnosticos, adjuntos, pago: pagos[0], consultorios });
    } catch (err) {
      setError(err.message);
    }
  }, [citaId, context]);

  React.useEffect(() => {
    loadDetail();
  }, [loadDetail]);

  async function saveDiagnostico(payload) {
    try {
      await context.api("/api/diagnosticos", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      context.notify("Diagnostico guardado");
      await loadDetail();
    } catch (err) {
      context.notify(err.message, "error");
    }
  }

  if (error) {
    return (
      <>
        <ViewHeader title="Detalle de cita">
          <button className="btn" onClick={() => context.go("medico/agenda")}>
            <ArrowLeft size={16} /> Mi agenda
          </button>
        </ViewHeader>
        <ErrorBox message={error} />
      </>
    );
  }
  if (!data) return <Loading label="Cargando detalle de cita..." />;

  const support = { consultorios: data.consultorios };
  const pagoEstado = data.pago?.estado || "SIN PAGO";

  return (
    <>
      <ViewHeader title="Detalle de cita">
        <button className="btn" onClick={() => context.go("medico/agenda")}>
          <ArrowLeft size={16} /> Mi agenda
        </button>
        <button className="btn primary" onClick={loadDetail}>
          <RefreshCw size={16} /> Actualizar
        </button>
      </ViewHeader>

      <section className="metrics">
        <Metric label="Estado cita" value={data.cita.estado} />
        <Metric label="Diagnosticos" value={data.diagnosticos.length} variant="secondary" />
        <Metric label="Adjuntos" value={data.adjuntos.length} variant="warning" />
        <Metric label="Pago" value={pagoEstado} variant="danger" />
      </section>

      <div className="consulta-grid">
        <section>
          <Section title="Resumen de consulta">
            <div className="consulta-summary">
              <Info label="Fecha" value={formatDate(data.cita.fechaHora)} />
              <Info label="Paciente" value={data.paciente.nombre} />
              <Info label="Consultorio" value={nameById("consultorios", data.cita.consultorioId, support)} />
              <Info label="Duracion" value={`${data.cita.duracionMin} min`} />
              <Info label="Motivo" value={data.cita.motivo || "Sin motivo registrado"} wide />
              <span className={`badge ${statusVariant(data.cita.estado)}`}>{data.cita.estado}</span>
            </div>
          </Section>

          <Section title="Diagnosticos">
            <DiagnosticoList diagnosticos={data.diagnosticos} />
          </Section>

          <Section title="Agregar diagnostico">
            <DiagnosticoForm citaId={data.cita.id} onSubmit={saveDiagnostico} />
          </Section>
        </section>

        <aside>
          <Section title="Paciente">
            <PatientPanel paciente={data.paciente} onOpen={() => context.go(`medico/expediente/${data.paciente.id}`)} />
          </Section>
          <Section title="Pago">
            <PaymentPanel pago={data.pago} />
          </Section>
          <Section title="Adjuntos">
            <AttachmentList adjuntos={data.adjuntos} />
          </Section>
        </aside>
      </div>
    </>
  );
}

function Info({ label, value, wide }) {
  return (
    <div className={`consulta-info ${wide ? "wide" : ""}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function PatientPanel({ paciente, onOpen }) {
  return (
    <div className="consulta-panel">
      <strong>{paciente.nombre}</strong>
      <span>{paciente.email || "Sin correo"}</span>
      <span>{paciente.telefono || "Sin telefono"}</span>
      <small>{paciente.fechaNacimiento ? `Nacimiento: ${paciente.fechaNacimiento}` : "Sin fecha de nacimiento"}</small>
      <button className="btn" onClick={onOpen}>Abrir expediente</button>
    </div>
  );
}

function PaymentPanel({ pago }) {
  if (!pago) return <Empty label="No hay pago registrado para esta cita." />;
  return (
    <div className="consulta-panel">
      <strong>{formatMoney(pago.monto)}</strong>
      <span>{pago.concepto || "Consulta medica"}</span>
      <span>{pago.metodo}</span>
      <small>{pago.referencia || "Sin referencia"}</small>
      <span className={`badge ${statusVariant(pago.estado)}`}>{pago.estado}</span>
    </div>
  );
}

function DiagnosticoList({ diagnosticos }) {
  if (!diagnosticos.length) return <Empty label="No hay diagnosticos registrados." />;
  return (
    <div className="diagnosis-list">
      {diagnosticos.map((diagnostico) => (
        <article className="diagnosis-item" key={diagnostico.id}>
          <div>
            <strong>{diagnostico.codigoCie10 || "Sin CIE-10"}</strong>
            <span>{diagnostico.descripcion}</span>
          </div>
          <span className={`badge ${diagnostico.tipo === "PRINCIPAL" ? "ok" : "info"}`}>{diagnostico.tipo || "SIN TIPO"}</span>
        </article>
      ))}
    </div>
  );
}

function AttachmentList({ adjuntos }) {
  if (!adjuntos.length) return <Empty label="No hay adjuntos para esta cita." />;
  return (
    <div className="attachment-list">
      {adjuntos.map((adjunto) => (
        <a className="attachment-item" href={adjunto.urlArchivo} target="_blank" rel="noreferrer" key={adjunto.id}>
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

function DiagnosticoForm({ citaId, onSubmit }) {
  return (
    <form
      className="diagnosis-form"
      onSubmit={(event) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        onSubmit({
          citaId,
          codigoCie10: data.get("codigoCie10") || null,
          descripcion: data.get("descripcion"),
          tipo: data.get("tipo") || null,
        });
        event.currentTarget.reset();
      }}
    >
      <label className="field">
        <span>Tipo</span>
        <select name="tipo" defaultValue="PRINCIPAL">
          <option value="PRINCIPAL">PRINCIPAL</option>
          <option value="SECUNDARIO">SECUNDARIO</option>
        </select>
      </label>
      <label className="field">
        <span>CIE-10</span>
        <input name="codigoCie10" maxLength="10" placeholder="Opcional" />
      </label>
      <label className="field full">
        <span>Descripcion</span>
        <textarea name="descripcion" rows="5" required />
      </label>
      <div className="actions full">
        <button className="btn primary">
          <Save size={16} /> Guardar diagnostico
        </button>
      </div>
    </form>
  );
}
