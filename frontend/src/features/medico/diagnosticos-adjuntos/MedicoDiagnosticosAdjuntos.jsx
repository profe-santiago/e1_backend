import React from "react";
import { ExternalLink, RefreshCw, Save } from "lucide-react";
import { Empty, ErrorBox, Loading, Metric, Section, ViewHeader } from "../../../components/ui";
import { formatDate } from "../../../lib/display";
import "./MedicoDiagnosticosAdjuntos.css";

export default function MedicoDiagnosticosAdjuntos({ context }) {
  const [base, setBase] = React.useState(null);
  const [clinical, setClinical] = React.useState({ diagnosticos: [], adjuntos: [] });
  const [selectedPacienteId, setSelectedPacienteId] = React.useState("");
  const [selectedCitaId, setSelectedCitaId] = React.useState("");
  const [error, setError] = React.useState("");
  const [loadingClinical, setLoadingClinical] = React.useState(false);

  const loadBase = React.useCallback(async () => {
    if (!context.medicoId) {
      setBase(null);
      setError("Tu usuario medico no tiene un medico asociado.");
      return;
    }

    try {
      setError("");
      const [citas, pacientes] = await Promise.all([
        context.list(`/api/citas/medico/${context.medicoId}`, true),
        context.list(`/api/pacientes/organizacion/${context.orgId}`, true),
      ]);
      const sortedCitas = [...citas].sort((a, b) => new Date(b.fechaHora) - new Date(a.fechaHora));
      const patientIds = new Set(sortedCitas.map((cita) => String(cita.pacienteId)));
      const linkedPatients = pacientes.filter((paciente) => patientIds.has(String(paciente.id)));
      setBase({ citas: sortedCitas, pacientes: linkedPatients.length ? linkedPatients : pacientes });

      if (!selectedPacienteId) {
        const firstPacienteId = linkedPatients[0]?.id || pacientes[0]?.id || "";
        setSelectedPacienteId(firstPacienteId);
      }
    } catch (err) {
      setError(err.message);
    }
  }, [context, selectedPacienteId]);

  React.useEffect(() => {
    loadBase();
  }, [loadBase]);

  const citasPaciente = React.useMemo(() => {
    if (!base || !selectedPacienteId) return [];
    return base.citas.filter((cita) => String(cita.pacienteId) === String(selectedPacienteId));
  }, [base, selectedPacienteId]);

  React.useEffect(() => {
    if (!selectedPacienteId) {
      setSelectedCitaId("");
      return;
    }
    if (selectedCitaId && citasPaciente.some((cita) => String(cita.id) === String(selectedCitaId))) return;
    setSelectedCitaId(citasPaciente[0]?.id || "");
  }, [citasPaciente, selectedCitaId, selectedPacienteId]);

  const loadClinical = React.useCallback(async () => {
    if (!base || !selectedPacienteId) return;
    setLoadingClinical(true);
    try {
      const citasParaDiagnostico = selectedCitaId ? citasPaciente.filter((cita) => String(cita.id) === String(selectedCitaId)) : citasPaciente;
      const [diagnosticosPorCita, adjuntos] = await Promise.all([
        Promise.all(citasParaDiagnostico.map(async (cita) => [cita.id, await context.list(`/api/diagnosticos/cita/${cita.id}`, true)])),
        context.list(selectedCitaId ? `/api/adjuntos/cita/${selectedCitaId}` : `/api/adjuntos/paciente/${selectedPacienteId}`, true),
      ]);
      setClinical({
        diagnosticos: diagnosticosPorCita.flatMap(([citaId, rows]) => rows.map((row) => ({ ...row, citaId }))),
        adjuntos,
      });
    } catch (err) {
      context.notify(err.message, "error");
    } finally {
      setLoadingClinical(false);
    }
  }, [base, citasPaciente, context, selectedCitaId, selectedPacienteId]);

  React.useEffect(() => {
    loadClinical();
  }, [loadClinical]);

  async function saveDiagnostico(payload) {
    try {
      await context.api("/api/diagnosticos", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      context.notify("Diagnostico guardado");
      await loadClinical();
    } catch (err) {
      context.notify(err.message, "error");
    }
  }

  async function saveAdjunto(payload) {
    try {
      await context.api("/api/adjuntos", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      context.notify("Adjunto guardado");
      await loadClinical();
    } catch (err) {
      context.notify(err.message, "error");
    }
  }

  if (error) {
    return (
      <>
        <ViewHeader title="Diagnosticos y Adjuntos" />
        <ErrorBox message={error} />
      </>
    );
  }
  if (!base) return <Loading label="Cargando datos clinicos..." />;

  const selectedPaciente = base.pacientes.find((paciente) => String(paciente.id) === String(selectedPacienteId));

  return (
    <>
      <ViewHeader title="Diagnosticos y Adjuntos">
        <button className="btn primary" onClick={loadBase}>
          <RefreshCw size={16} /> Actualizar
        </button>
      </ViewHeader>

      <section className="metrics">
        <Metric label="Pacientes" value={base.pacientes.length} />
        <Metric label="Citas del paciente" value={citasPaciente.length} variant="secondary" />
        <Metric label="Diagnosticos" value={clinical.diagnosticos.length} variant="warning" />
        <Metric label="Adjuntos" value={clinical.adjuntos.length} variant="danger" />
      </section>

      <section className="section clinical-controls">
        <label className="field">
          <span>Paciente</span>
          <select value={selectedPacienteId} onChange={(event) => setSelectedPacienteId(event.target.value)}>
            {base.pacientes.map((paciente) => (
              <option key={paciente.id} value={paciente.id}>{paciente.nombre}</option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Cita</span>
          <select value={selectedCitaId} onChange={(event) => setSelectedCitaId(event.target.value)}>
            <option value="">Todo el expediente</option>
            {citasPaciente.map((cita) => (
              <option key={cita.id} value={cita.id}>{formatDate(cita.fechaHora)} / {cita.estado}</option>
            ))}
          </select>
        </label>
      </section>

      <div className="clinical-grid">
        <section>
          <Section title={selectedPaciente ? selectedPaciente.nombre : "Paciente"}>
            <PatientSummary paciente={selectedPaciente} />
          </Section>
          <Section title={loadingClinical ? "Cargando diagnosticos..." : "Diagnosticos"}>
            <DiagnosisList diagnosticos={clinical.diagnosticos} citas={base.citas} />
          </Section>
          <Section title="Nuevo diagnostico">
            <DiagnosisForm citaId={selectedCitaId} onSubmit={saveDiagnostico} />
          </Section>
        </section>

        <aside>
          <Section title={loadingClinical ? "Cargando adjuntos..." : "Adjuntos"}>
            <AttachmentList adjuntos={clinical.adjuntos} />
          </Section>
          <Section title="Nuevo adjunto">
            <AttachmentForm
              context={context}
              pacienteId={selectedPacienteId}
              citaId={selectedCitaId}
              citas={citasPaciente}
              onSubmit={saveAdjunto}
            />
          </Section>
        </aside>
      </div>
    </>
  );
}

function PatientSummary({ paciente }) {
  if (!paciente) return <Empty label="Selecciona un paciente." />;
  return (
    <div className="clinical-patient">
      <div>
        <span>Correo</span>
        <strong>{paciente.email || "-"}</strong>
      </div>
      <div>
        <span>Telefono</span>
        <strong>{paciente.telefono || "-"}</strong>
      </div>
      <div>
        <span>Nacimiento</span>
        <strong>{paciente.fechaNacimiento || "-"}</strong>
      </div>
      <div>
        <span>Sexo</span>
        <strong>{paciente.sexo || "-"}</strong>
      </div>
    </div>
  );
}

function DiagnosisList({ diagnosticos, citas }) {
  if (!diagnosticos.length) return <Empty label="No hay diagnosticos para esta seleccion." />;
  return (
    <div className="clinical-list">
      {diagnosticos.map((diagnostico) => {
        const cita = citas.find((item) => String(item.id) === String(diagnostico.citaId));
        return (
          <article className="clinical-item" key={diagnostico.id}>
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

function AttachmentList({ adjuntos }) {
  if (!adjuntos.length) return <Empty label="No hay adjuntos para esta seleccion." />;
  return (
    <div className="clinical-list">
      {adjuntos.map((adjunto) => (
        <a className="clinical-item clinical-link" href={adjunto.urlArchivo} target="_blank" rel="noreferrer" key={adjunto.id}>
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

function DiagnosisForm({ citaId, onSubmit }) {
  return (
    <form
      className="clinical-form"
      onSubmit={(event) => {
        event.preventDefault();
        if (!citaId) return;
        const data = new FormData(event.currentTarget);
        onSubmit({
          citaId,
          codigoCie10: data.get("codigoCie10") || null,
          tipo: data.get("tipo") || null,
          descripcion: data.get("descripcion"),
        });
        event.currentTarget.reset();
      }}
    >
      {!citaId && <ErrorBox message="Selecciona una cita para registrar diagnostico." />}
      <label className="field">
        <span>Tipo</span>
        <select name="tipo" defaultValue="PRINCIPAL" disabled={!citaId}>
          <option value="PRINCIPAL">PRINCIPAL</option>
          <option value="SECUNDARIO">SECUNDARIO</option>
        </select>
      </label>
      <label className="field">
        <span>CIE-10</span>
        <input name="codigoCie10" maxLength="10" placeholder="Opcional" disabled={!citaId} />
      </label>
      <label className="field full">
        <span>Descripcion</span>
        <textarea name="descripcion" rows="5" required disabled={!citaId} />
      </label>
      <div className="actions full">
        <button className="btn primary" disabled={!citaId}>
          <Save size={16} /> Guardar diagnostico
        </button>
      </div>
    </form>
  );
}

function AttachmentForm({ context, pacienteId, citaId, citas, onSubmit }) {
  return (
    <form
      className="clinical-form"
      onSubmit={(event) => {
        event.preventDefault();
        if (!pacienteId) return;
        const data = new FormData(event.currentTarget);
        onSubmit({
          organizacionId: context.orgId,
          pacienteId,
          citaId: data.get("citaId") || null,
          subidoPorId: context.userId,
          tipo: data.get("tipo"),
          nombreArchivo: data.get("nombreArchivo"),
          urlArchivo: data.get("urlArchivo"),
          mimeType: data.get("mimeType") || null,
          notificar: data.get("notificar") === "true",
        });
        event.currentTarget.reset();
      }}
    >
      <label className="field full">
        <span>Cita relacionada</span>
        <select name="citaId" defaultValue={citaId || ""}>
          <option value="">Solo expediente</option>
          {citas.map((cita) => (
            <option key={cita.id} value={cita.id}>{formatDate(cita.fechaHora)}</option>
          ))}
        </select>
      </label>
      <label className="field">
        <span>Tipo</span>
        <input name="tipo" placeholder="LAB, RECETA, IMAGEN..." required maxLength="30" />
      </label>
      <label className="field">
        <span>MIME</span>
        <input name="mimeType" placeholder="application/pdf" maxLength="80" />
      </label>
      <label className="field full">
        <span>Nombre del archivo</span>
        <input name="nombreArchivo" required maxLength="200" />
      </label>
      <label className="field full">
        <span>URL del archivo</span>
        <input name="urlArchivo" required />
      </label>
      <label className="field checkbox-field full">
        <input name="notificar" type="checkbox" value="true" />
        <span>Notificar al paciente</span>
      </label>
      <div className="actions full">
        <button className="btn primary" disabled={!pacienteId}>
          <Save size={16} /> Guardar adjunto
        </button>
      </div>
    </form>
  );
}
