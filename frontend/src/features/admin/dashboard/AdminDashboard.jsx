import React from "react";
import { RefreshCw } from "lucide-react";
import { Empty, ErrorBox, Loading, Metric, Section, ViewHeader, Estado } from "../../../components/ui";
import {
  addDaysKey,
  clinicToday,
  formatMoney,
  formatTime,
  formatTodayLabel,
  statusVariant,
} from "../../../lib/display";
import { RecordatorioWhatsApp, useRecordatorioEstados } from "../../whatsapp/RecordatorioWhatsApp";
import "./AdminDashboard.css";

// Refresco del panel (no de los ingresos) para ver confirmaciones/cancelaciones por WhatsApp
const REFRESCO_MS = 15000;

const PERIODOS = [7, 30];

const ESTADOS = [
  { key: "CONFIRMADA", label: "Confirmadas", color: "var(--laguna-boton)" },
  { key: "SIN_CONFIRMAR", label: "Por confirmar", color: "var(--lima)" },
  { key: "REAGENDADA", label: "Reagendadas", color: "var(--bruma)" },
  { key: "NO_ASISTIO", label: "No asistió", color: "var(--texto-2)" },
  { key: "CANCELADA", label: "Canceladas", color: "var(--cancelada)" },
];

const METODOS = [
  { key: "EFECTIVO", label: "Efectivo" },
  { key: "TARJETA", label: "Tarjeta" },
  { key: "TRANSFERENCIA", label: "Transferencia" },
];

export default function AdminDashboard({ context }) {
  const [periodo, setPeriodo] = React.useState(7);
  const [dashboard, setDashboard] = React.useState(null);
  const [resumen, setResumen] = React.useState(null);
  const [error, setError] = React.useState("");
  const [reloadKey, setReloadKey] = React.useState(0);
  const [tick, setTick] = React.useState(0);

  React.useEffect(() => {
    const id = window.setInterval(() => setTick((t) => t + 1), REFRESCO_MS);
    return () => window.clearInterval(id);
  }, []);

  React.useEffect(() => {
    let active = true;
    setError("");
    context
      .api("/api/admin/dashboard")
      .then((data) => active && setDashboard(data))
      .catch((err) => active && setError(err.message));
    return () => {
      active = false;
    };
  }, [context, reloadKey, tick]);

  const idsCitas = [...(dashboard?.proximasSinConfirmar || []), ...(dashboard?.proximasCitas || [])].map((cita) => cita.id);
  const [recordatorios, recargarRecordatorios] = useRecordatorioEstados(context, idsCitas, tick);
  const alEnviarRecordatorio = () => {
    recargarRecordatorios();
    setTick((t) => t + 1);
  };

  React.useEffect(() => {
    let active = true;
    const hasta = clinicToday();
    const desde = addDaysKey(hasta, -(periodo - 1));
    setResumen(null);
    context
      .api(`/api/cobros/resumen?desde=${desde}&hasta=${hasta}`)
      .then((data) => active && setResumen(data))
      .catch((err) => active && setError(err.message));
    return () => {
      active = false;
    };
  }, [context, periodo, reloadKey]);

  const header = (
    <ViewHeader title="Inicio" subtitle={capitalize(formatTodayLabel())}>
      <div className="admin-period" role="group" aria-label="Periodo de ingresos">
        {PERIODOS.map((dias) => (
          <button
            key={dias}
            type="button"
            className={`admin-period-btn ${periodo === dias ? "active" : ""}`}
            aria-pressed={periodo === dias}
            onClick={() => setPeriodo(dias)}
          >
            {dias} días
          </button>
        ))}
      </div>
      <button className="btn icon" title="Actualizar" onClick={() => setReloadKey((key) => key + 1)}>
        <RefreshCw size={18} />
      </button>
      <button className="btn primary" onClick={() => context.go("modulo/citas")}>
        Nueva cita
      </button>
    </ViewHeader>
  );

  if (error) {
    return (
      <>
        {header}
        <ErrorBox message={error} />
      </>
    );
  }
  if (!dashboard) {
    return (
      <>
        {header}
        <Loading label="Cargando panel..." />
      </>
    );
  }

  const tasas = dashboard.tasas30Dias || { confirmacion: 0, noAsistencia: 0, base: 0 };

  return (
    <>
      {header}

      <section className="metrics admin-metrics">
        <Metric label="Citas hoy" value={dashboard.citasHoyTotal} />
        <Metric label="Ingresos hoy" value={formatMoney(dashboard.ingresosHoy)} variant="secondary" />
        <Metric
          label={`Cobros pendientes (${dashboard.cobrosPendientesCantidad})`}
          value={formatMoney(dashboard.cobrosPendientesMonto)}
          variant="warning"
        />
        <Metric label="Confirmación · 30 días" value={`${tasas.confirmacion}%`} variant="secondary" />
        <Metric label="No asistencia · 30 días" value={`${tasas.noAsistencia}%`} variant="danger" />
      </section>

      <div className="admin-grid">
        <Section title="Citas de hoy por estado" badge={String(dashboard.citasHoyTotal)}>
          <EstadosBar porEstado={dashboard.citasHoyPorEstado || {}} total={dashboard.citasHoyTotal} />
        </Section>

        <Section title={`Ingresos · últimos ${periodo} días`} badge={resumen ? formatMoney(resumen.total) : undefined}>
          {resumen ? (
            <>
              <IngresosPorDia dias={resumen.porDia || []} />
              <MetodosResumen porMetodo={resumen.porMetodo || {}} total={resumen.total} />
            </>
          ) : (
            <Loading label="Cargando ingresos..." />
          )}
        </Section>

        <Section title="Ingresos por médico">
          {resumen ? <IngresosPorMedico medicos={resumen.porMedico || []} /> : <Loading label="Cargando..." />}
        </Section>

        <Section title="Próximas citas por confirmar" badge={String((dashboard.proximasSinConfirmar || []).length)}>
          <ListaCitas
            citas={dashboard.proximasSinConfirmar || []}
            conTelefono
            vacio="No hay citas próximas por confirmar."
            context={context}
            recordatorios={recordatorios}
            onRecordatorio={alEnviarRecordatorio}
          />
        </Section>
      </div>

      <Section title="Próximas citas">
        <ListaCitas
          citas={dashboard.proximasCitas || []}
          vacio="No hay citas próximas."
          context={context}
          recordatorios={recordatorios}
          onRecordatorio={alEnviarRecordatorio}
        />
      </Section>
    </>
  );
}

// ── Barra apilada de estados (CSS) ───────────────────────────────────────────

function EstadosBar({ porEstado, total }) {
  if (!total) return <Empty label="No hay citas programadas para hoy." />;
  return (
    <div className="estados">
      <div className="estados-bar" role="img" aria-label={ESTADOS.map((e) => `${e.label}: ${porEstado[e.key] || 0}`).join(", ")}>
        {ESTADOS.filter((e) => porEstado[e.key]).map((e) => (
          <span
            key={e.key}
            className="estados-seg"
            style={{ flexGrow: porEstado[e.key], background: e.color }}
            title={`${e.label}: ${porEstado[e.key]}`}
          />
        ))}
      </div>
      <ul className="estados-legend">
        {ESTADOS.map((e) => (
          <li key={e.key}>
            <span className="estados-dot" style={{ background: e.color }} />
            <span>{e.label}</span>
            <strong>{porEstado[e.key] || 0}</strong>
          </li>
        ))}
      </ul>
    </div>
  );
}

// ── Ingresos por día (SVG) ───────────────────────────────────────────────────

function IngresosPorDia({ dias }) {
  if (!dias.length) return <Empty label="Sin datos en el periodo." />;
  const width = 640;
  const height = 200;
  const pad = { top: 12, right: 8, bottom: 26, left: 56 };
  const innerW = width - pad.left - pad.right;
  const innerH = height - pad.top - pad.bottom;
  const max = Math.max(...dias.map((d) => Number(d.total)), 0);
  const tope = niceMax(max);
  const slot = innerW / dias.length;
  const barW = Math.max(2, Math.min(36, slot * 0.66));
  const ticks = [0, tope / 2, tope];
  const etiquetaCada = dias.length > 10 ? Math.ceil(dias.length / 6) : 1;

  return (
    <svg className="ingresos-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Ingresos por día">
      {ticks.map((t) => {
        const y = pad.top + innerH - (tope ? (t / tope) * innerH : 0);
        return (
          <g key={t}>
            <line x1={pad.left} x2={width - pad.right} y1={y} y2={y} className="chart-grid" />
            <text x={pad.left - 8} y={y + 4} textAnchor="end" className="chart-label">
              {formatCompact(t)}
            </text>
          </g>
        );
      })}
      {dias.map((d, i) => {
        const valor = Number(d.total);
        const h = tope ? (valor / tope) * innerH : 0;
        const x = pad.left + i * slot + (slot - barW) / 2;
        const y = pad.top + innerH - h;
        return (
          <g key={d.fecha}>
            <rect x={x} y={y} width={barW} height={Math.max(h, valor > 0 ? 1 : 0)} rx="3" className="chart-bar">
              <title>{`${formatDia(d.fecha)}: ${formatMoney(valor)} (${d.cantidad} cobros)`}</title>
            </rect>
            {(i % etiquetaCada === 0 || i === dias.length - 1) && (
              <text x={x + barW / 2} y={height - 8} textAnchor="middle" className="chart-label">
                {formatDia(d.fecha)}
              </text>
            )}
          </g>
        );
      })}
    </svg>
  );
}

function MetodosResumen({ porMetodo, total }) {
  const totalNum = Number(total) || 0;
  return (
    <ul className="metodos">
      {METODOS.map((m) => {
        const valor = Number(porMetodo[m.key] || 0);
        const pct = totalNum ? Math.round((valor / totalNum) * 100) : 0;
        return (
          <li key={m.key}>
            <span>{m.label}</span>
            <strong>{formatMoney(valor)}</strong>
            <small>{pct}%</small>
          </li>
        );
      })}
    </ul>
  );
}

// ── Ingresos por médico (barras horizontales CSS) ───────────────────────────

function IngresosPorMedico({ medicos }) {
  if (!medicos.length) return <Empty label="Sin ingresos en el periodo." />;
  const max = Math.max(...medicos.map((m) => Number(m.total)), 1);
  return (
    <ul className="medicos-bars">
      {medicos.map((m) => (
        <li key={m.medicoId}>
          <div className="medicos-bars-head">
            <span>{m.nombre}</span>
            <strong>{formatMoney(m.total)}</strong>
          </div>
          <div className="medicos-bars-track">
            <span style={{ width: `${(Number(m.total) / max) * 100}%` }} />
          </div>
          <small>{m.cantidad} cobros</small>
        </li>
      ))}
    </ul>
  );
}

// ── Lista de citas ────────────────────────────────────────────────────────────

function ListaCitas({ citas, conTelefono = false, vacio, context, recordatorios, onRecordatorio }) {
  if (!citas.length) return <Empty label={vacio} />;
  return (
    <div className="admin-citas">
      {citas.map((cita) => (
        <article className="admin-cita" key={cita.id}>
          <div className="admin-cita-hora">
            <strong>{formatTime(cita.fechaHora)}</strong>
            <span>{formatDiaCita(cita.fechaHora)}</span>
          </div>
          <div className="admin-cita-info">
            <strong>{cita.pacienteNombre || "Paciente"}</strong>
            <span>
              {cita.medicoNombre || "Médico"}
              {conTelefono && cita.pacienteTelefono ? ` · ${cita.pacienteTelefono}` : ""}
            </span>
            {recordatorios && (
              <RecordatorioWhatsApp context={context} cita={cita} estado={recordatorios[cita.id]} onEnviado={onRecordatorio} />
            )}
          </div>
          <Estado valor={cita.estado} />
        </article>
      ))}
    </div>
  );
}

// ── utilidades ────────────────────────────────────────────────────────────────

function niceMax(value) {
  if (!value) return 0;
  const exp = Math.pow(10, Math.floor(Math.log10(value)));
  const n = value / exp;
  const nice = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
  return nice * exp;
}

function formatCompact(value) {
  if (value >= 1000) return `$${(value / 1000).toLocaleString("es-MX", { maximumFractionDigits: 1 })}k`;
  return `$${Math.round(value)}`;
}

function formatDia(fecha) {
  return new Date(`${fecha}T00:00:00Z`).toLocaleDateString("es-MX", { day: "numeric", month: "short", timeZone: "UTC" });
}

function formatDiaCita(value) {
  return new Date(value).toLocaleDateString("es-MX", { weekday: "short", day: "numeric", month: "short", timeZone: "UTC" });
}

function capitalize(text) {
  return text ? text.charAt(0).toUpperCase() + text.slice(1) : text;
}
