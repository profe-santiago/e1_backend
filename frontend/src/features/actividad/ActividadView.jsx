import React from "react";
import {
  Ban,
  CalendarDays,
  Check,
  CheckCheck,
  Clock,
  RotateCw,
  Inbox,
  Link2,
  RefreshCw,
  Send,
  TriangleAlert,
  X,
} from "lucide-react";
import { Destello, ErrorBox, etiquetaEstado } from "../../components/ui";
import { CLINIC_TIME_ZONE, UTC, citaDateKey, clinicToday, formatCitaHora12, formatHoraInstante } from "../../lib/display";
import { useCanalWhatsApp, useEnviarRecordatorio, useRecordatorioEstados } from "../whatsapp/RecordatorioWhatsApp";
import "./Actividad.css";

const REFRESCO_MS = 30000;
/** Evento compartido con la campana: "algo cambió en las notificaciones" (leídas o nuevas). */
export const EVENTO_NOTIFICACIONES = "medinflow:notificaciones";

const FILTROS = [
  { key: "TODAS", label: "Todo", conteo: "todas" },
  { key: "SIN_LEER", label: "Sin leer", conteo: "sinLeer" },
  { key: "CONFIRMACIONES", label: "Confirmaciones", conteo: "confirmaciones" },
  { key: "CANCELACIONES", label: "Cancelaciones", conteo: "cancelaciones" },
  { key: "RECORDATORIOS", label: "Recordatorios", conteo: "recordatorios" },
];

const VACIOS = {
  TODAS: ["Aún no hay actividad", "Cuando envíes recordatorios o un paciente responda, lo verás aquí."],
  SIN_LEER: ["Todo al día", "No tienes avisos sin leer."],
  CONFIRMACIONES: ["Sin confirmaciones", "Ningún paciente ha confirmado en los últimos 30 días."],
  CANCELACIONES: ["Sin cancelaciones", "Ningún paciente canceló en los últimos 30 días."],
  RECORDATORIOS: ["Sin recordatorios", "Aún no se han enviado recordatorios en los últimos 30 días."],
};

/** Estilo de cada bloque de la tira de horas: color + icono (nunca solo color). */
const TIRA = {
  CONFIRMADA: { clase: "confirmada", Icono: Check },
  SIN_CONFIRMAR: { clase: "pendiente", Icono: Clock },
  CANCELADA: { clase: "cancelada", Icono: X },
  NO_ASISTIO: { clase: "noasistio", Icono: Ban },
  REAGENDADA: { clase: "reagendada", Icono: RefreshCw },
};

export function avisarCambioNotificaciones() {
  window.dispatchEvent(new CustomEvent(EVENTO_NOTIFICACIONES));
}

/**
 * "Actividad": resumen del próximo día como tira de horas, panel "Por confirmar" para enviar
 * recordatorios y "Lo que ha pasado" como línea de tiempo. MEDICO ve sus citas; ADMIN toda la clínica.
 */
export default function ActividadView({ context }) {
  const esAdmin = context.role === "ADMIN";
  const [filtro, setFiltro] = React.useState("TODAS");
  const [medicoId, setMedicoId] = React.useState("");
  const [medicos, setMedicos] = React.useState([]);
  const [datos, setDatos] = React.useState(null);
  const [manana, setManana] = React.useState(null);
  const [error, setError] = React.useState("");
  const [cargando, setCargando] = React.useState(true);
  const [tick, setTick] = React.useState(0);
  const panelRef = React.useRef(null);

  const recargar = React.useCallback(() => setTick((t) => t + 1), []);
  const consulta = medicoId ? `medicoId=${encodeURIComponent(medicoId)}` : "";

  React.useEffect(() => {
    if (!esAdmin || !context.orgId) return;
    context.api(`/api/medicos/organizacion/${context.orgId}`)
      .then((data) => setMedicos((data || []).filter((m) => m.activo !== false)))
      .catch(() => setMedicos([]));
  }, [context, esAdmin]);

  React.useEffect(() => {
    let activo = true;
    setCargando(true);
    const params = [filtro !== "TODAS" ? `filtro=${filtro}` : "", consulta].filter(Boolean).join("&");
    Promise.all([
      context.api(`/api/notificaciones/actividad${params ? `?${params}` : ""}`),
      context.api(`/api/notificaciones/actividad/manana${consulta ? `?${consulta}` : ""}`),
    ])
      .then(([actividad, proximo]) => {
        if (!activo) return;
        setDatos(actividad || { items: [], conteos: {} });
        setManana(proximo);
        setError("");
      })
      .catch((err) => activo && setError(err.message))
      .finally(() => activo && setCargando(false));
    return () => {
      activo = false;
    };
  }, [context, filtro, consulta, tick]);

  // Refresco periódico, al volver a la pestaña (p. ej. después de enviar por WhatsApp) y cuando la
  // campana detecta cambios (respuesta nueva o avisos marcados como leídos).
  React.useEffect(() => {
    const id = window.setInterval(recargar, REFRESCO_MS);
    const alVolver = () => document.visibilityState === "visible" && recargar();
    document.addEventListener("visibilitychange", alVolver);
    window.addEventListener(EVENTO_NOTIFICACIONES, recargar);
    return () => {
      window.clearInterval(id);
      document.removeEventListener("visibilitychange", alVolver);
      window.removeEventListener(EVENTO_NOTIFICACIONES, recargar);
    };
  }, [recargar]);

  async function abrir(item) {
    try {
      if (!item.leida) {
        await context.api(`/api/notificaciones/${item.id}/leida`, { method: "PATCH" });
        avisarCambioNotificaciones();
      }
    } catch {
      // Si no se pudo marcar, igual se navega a la cita
    }
    if (!item.citaId) return;
    if (esAdmin) context.go(`modulo/citas?cita=${item.citaId}`);
    else context.go(`medico/agenda/${citaDateKey(item.citaFechaHora)}/${item.citaId}`);
  }

  async function marcarTodas() {
    try {
      const r = await context.api(`/api/notificaciones/actividad/leidas${consulta ? `?${consulta}` : ""}`, { method: "POST" });
      context.notify(r?.marcadas ? `${r.marcadas} avisos marcados como leídos` : "No había avisos sin leer");
      avisarCambioNotificaciones();
      recargar();
    } catch (err) {
      context.notify(err.message, "error");
    }
  }

  const conteos = datos?.conteos || {};
  const grupos = React.useMemo(() => agruparPorDia(datos?.items || []), [datos]);
  const irAlPanel = () => {
    panelRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    panelRef.current?.focus({ preventScroll: true });
  };

  return (
    <div className="act">
      <header className="act-encabezado">
        <div>
          <span className="act-eyebrow">{capitalizar(fechaLarga(clinicToday()))}</span>
          <h1>{esAdmin ? "Actividad de la clínica" : `${saludo()}, ${context.userName}`}</h1>
        </div>
        <div className="act-encabezado-acciones">
          {esAdmin && (
            <select className="act-medico" value={medicoId} onChange={(e) => setMedicoId(e.target.value)} aria-label="Filtrar por médico">
              <option value="">Todos los médicos</option>
              {medicos.map((m) => (
                <option key={m.id} value={m.id}>{m.nombre}</option>
              ))}
            </select>
          )}
          <button className="act-icono-btn" type="button" title="Actualizar" aria-label="Actualizar" onClick={recargar}>
            <RefreshCw size={20} />
          </button>
        </div>
      </header>

      {error && <ErrorBox message={error} />}

      <div className="act-fila">
        <ProximoDia datos={manana} cargando={cargando && !manana} esAdmin={esAdmin} onRecordar={irAlPanel} />
        {manana?.fecha && (
          <PorConfirmar context={context} datos={manana} esAdmin={esAdmin} panelRef={panelRef} onCambio={recargar} />
        )}
      </div>

      <section className="act-historia" aria-label="Lo que ha pasado">
        <div className="act-historia-cabeza">
          <h2>Lo que ha pasado</h2>
          <div className="act-filtros" role="group" aria-label="Filtrar actividad">
            {FILTROS.map((f) => (
              <button
                key={f.key}
                type="button"
                aria-pressed={filtro === f.key}
                className={`act-filtro ${filtro === f.key ? "activo" : ""}`}
                onClick={() => setFiltro(f.key)}
              >
                {f.label}
                {conteos[f.conteo] > 0 && (
                  <span className={`act-cuenta ${f.key === "SIN_LEER" ? "nuevo" : ""}`}>{conteos[f.conteo]}</span>
                )}
              </button>
            ))}
          </div>
        </div>
        {conteos.sinLeer > 0 && (
          <button type="button" className="act-marcar" onClick={marcarTodas}>
            <CheckCheck size={16} /> Marcar todo como leído
          </button>
        )}

        {cargando && !datos ? (
          <EsqueletoLista />
        ) : grupos.length === 0 ? (
          <EstadoVacio icono={filtro === "SIN_LEER" ? CheckCheck : Inbox} titulo={VACIOS[filtro][0]} texto={VACIOS[filtro][1]} />
        ) : (
          grupos.map((g) => (
            <div className="act-grupo" key={g.clave}>
              <h3>{g.titulo}</h3>
              <ol className="act-linea">
                {g.items.map((item) => (
                  <li key={item.id}>
                    <AvisoActividad item={item} esAdmin={esAdmin} onAbrir={() => abrir(item)} />
                  </li>
                ))}
              </ol>
            </div>
          ))
        )}
        {datos && <p className="act-pie">Últimos 30 días · máximo 200 avisos</p>}
      </section>
    </div>
  );
}

// ── Resumen del próximo día: tira de horas ─────────────────────────────────────

function ProximoDia({ datos, cargando, esAdmin, onRecordar }) {
  if (cargando) {
    return (
      <section className="act-manana" aria-busy="true" aria-label="Cargando el resumen de mañana">
        <div className="act-skel titulo" />
        <div className="act-tira">
          {[0, 1, 2, 3, 4, 5, 6].map((i) => <div className="act-skel bloque" key={i} />)}
        </div>
      </section>
    );
  }
  if (!datos) return null;
  if (!datos.fecha) {
    return (
      <section className="act-manana vacio">
        <span className="act-vacio-icono"><CalendarDays size={28} /></span>
        <div>
          <h2>Sin citas en los próximos 7 días</h2>
          <p>{esAdmin ? "La clínica no tiene citas agendadas esta semana." : "Disfruta la semana: no tienes citas agendadas."}</p>
        </div>
      </section>
    );
  }
  const n = datos.sinConfirmar;
  const citas = datos.citas || [];
  const primera = (datos.pendientes || [])[0];
  return (
    <section className="act-manana" aria-label={datos.esManana ? "Mañana" : "Próximo día con citas"}>
      <div className="act-manana-cabeza">
        <div>
          <span className="act-subtitulo">
            {datos.esManana ? "Mañana" : "Próximo día con citas"} · {fechaLarga(datos.fecha)}
          </span>
          <h2>
            {datos.total} {datos.total === 1 ? "cita" : "citas"}
            {n > 0 ? `, ${n === 1 ? "falta 1" : `faltan ${n}`} por confirmar` : ", todas confirmadas"}
          </h2>
        </div>
        <ul className="act-leyenda" aria-label="Resumen por estado">
          <li><span className="act-muestra confirmada" />{datos.confirmadas} {datos.confirmadas === 1 ? "confirmada" : "confirmadas"}</li>
          <li><span className="act-muestra pendiente" />{n} por confirmar</li>
          <li><span className="act-muestra cancelada" />{datos.canceladas} {datos.canceladas === 1 ? "cancelada" : "canceladas"}</li>
        </ul>
      </div>

      <ol className="act-tira" aria-label="Citas del día por hora">
        {citas.map((c) => {
          const { clase, Icono } = TIRA[c.estado] || TIRA.CONFIRMADA;
          const hora = formatCitaHora12(c.fechaHora);
          return (
            <li key={c.citaId} className="act-tira-item" aria-label={`${hora}, ${c.pacienteNombre || "Paciente"}: ${etiquetaEstado(c.estado)}`}>
              <span className={`act-tira-bloque ${clase}`} title={`${hora} · ${etiquetaEstado(c.estado)}`}>
                <Icono size={14} strokeWidth={2.6} aria-hidden="true" />
                {hora.replace(/ [ap]\.m\.$/, "")}
              </span>
              <span className="act-tira-nombre">{nombreCorto(c.pacienteNombre)}</span>
            </li>
          );
        })}
      </ol>

      {n > 0 && primera ? (
        <div className="act-aviso-primero">
          <span>
            El primer paciente por confirmar llega a las <b>{formatCitaHora12(primera.fechaHora)}</b>
            {/* La hora ya termina en "a.m."/"p.m.": solo se agrega punto si la frase sigue con el médico */}
            {esAdmin && primera.medicoNombre ? ` con ${primera.medicoNombre}.` : ""}
          </span>
          <button type="button" className="act-boton primario" onClick={onRecordar}>
            {n === 1 ? "Recordar al paciente" : `Recordar a los ${n}`}
            <Destello />
          </button>
        </div>
      ) : (
        <div className="act-aviso-primero listo">
          <Check size={18} /> Todas las citas de ese día ya están confirmadas.
        </div>
      )}
    </section>
  );
}

// ── Panel "Por confirmar" (Tinta) ──────────────────────────────────────────────

/**
 * Canal "enlace": un clic por paciente (cada clic abre una pestaña de wa.me; el navegador bloquea
 * varias pestañas abiertas a la vez). Otros canales: "Enviar a todos" en secuencia, sin reenviar.
 * Lo ya enviado se muestra como "Enlace enviado"/"Enviado" con "Reenviar" explícito.
 */
function PorConfirmar({ context, datos, esAdmin, panelRef, onCambio }) {
  const canal = useCanalWhatsApp(context);
  const esEnlace = canal === "enlace";
  const enviarRecordatorio = useEnviarRecordatorio(context);
  const pendientes = React.useMemo(() => datos.pendientes || [], [datos]);
  const [tick, setTick] = React.useState(0);
  const ids = React.useMemo(() => pendientes.map((p) => p.citaId), [pendientes]);
  const [estados] = useRecordatorioEstados(context, ids, tick);
  const [enviando, setEnviando] = React.useState(null);
  const [resumen, setResumen] = React.useState(null);

  const yaEnviada = (p) => ["ENVIADO", "RESPONDIO"].includes(estados[p.citaId]?.estado);
  const enviables = pendientes.filter((p) => p.telefonoValido);
  const hechas = enviables.filter(yaEnviada).length;
  const siguiente = enviables.find((p) => !yaEnviada(p));

  const tras = () => {
    setTick((t) => t + 1);
    onCambio();
  };

  async function enviar(p, reenviar = false) {
    setEnviando(p.citaId);
    await enviarRecordatorio(p.citaId, reenviar);
    setEnviando(null);
    tras();
  }

  async function enviarTodos() {
    setEnviando("todos");
    const r = { enviados: 0, yaEnviados: 0, fallidos: 0 };
    for (const p of enviables.filter((x) => !yaEnviada(x))) {
      try {
        await context.api(`/api/admin/recordatorios/${p.citaId}/enviar`, { method: "POST" });
        r.enviados += 1;
      } catch (err) {
        if (/ya se envi/i.test(err.message || "")) r.yaEnviados += 1;
        else r.fallidos += 1;
      }
    }
    setResumen(r);
    setEnviando(null);
    tras();
  }

  return (
    <section className="act-panel" ref={panelRef} tabIndex={-1} aria-label="Por confirmar">
      <div className="act-panel-cabeza">
        <h2>Por confirmar</h2>
        {enviables.length > 0 && <span>{hechas} de {enviables.length} enviados</span>}
      </div>

      {pendientes.length === 0 ? (
        <p className="act-panel-listo"><Check size={18} /> No hay citas por confirmar ese día.</p>
      ) : (
        <>
          <div
            className="act-progreso"
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={enviables.length}
            aria-valuenow={hechas}
            aria-label="Recordatorios enviados"
          >
            <span style={{ width: `${enviables.length ? (hechas / enviables.length) * 100 : 0}%` }} />
          </div>

          <ul className="act-panel-lista">
            {pendientes.map((p) => {
              const enviada = yaEnviada(p);
              const respondio = estados[p.citaId]?.estado === "RESPONDIO";
              return (
                <li key={p.citaId} className={siguiente?.citaId === p.citaId ? "siguiente" : ""}>
                  <span className="act-panel-hora">{formatCitaHora12(p.fechaHora)}</span>
                  <span className="act-panel-nombre">
                    <span className="act-panel-nombre-texto" title={p.pacienteNombre || undefined}>{p.pacienteNombre || "Paciente"}</span>
                    {esAdmin && p.medicoNombre && <small>{p.medicoNombre}</small>}
                    {!p.telefonoValido && (
                      <small className="act-panel-estado"><TriangleAlert size={13} aria-hidden="true" /> Sin teléfono</small>
                    )}
                    {p.telefonoValido && enviada && (
                      <small className="act-panel-estado">
                        <Check size={13} strokeWidth={2.8} aria-hidden="true" />
                        {respondio ? "Respondió" : estados[p.citaId]?.canal === "enlace" ? "Enlace enviado" : "Enviado"}
                      </small>
                    )}
                  </span>
                  {!p.telefonoValido ? null : enviada ? (
                    !respondio && (
                      <button
                        type="button"
                        className="act-panel-reenviar"
                        aria-label={`Reenviar a ${p.pacienteNombre || "paciente"}`}
                        title="Reenviar"
                        disabled={enviando === p.citaId}
                        onClick={() => enviar(p, true)}
                      >
                        <RotateCw size={18} aria-hidden="true" />
                      </button>
                    )
                  ) : (
                    <button type="button" className="act-panel-enviar" disabled={!!enviando} onClick={() => enviar(p)}>
                      {enviando === p.citaId ? "Enviando..." : "Enviar"}
                    </button>
                  )}
                </li>
              );
            })}
          </ul>

          {canal && !esEnlace && siguiente && (
            <button type="button" className="act-panel-todos" disabled={!!enviando} onClick={enviarTodos}>
              <Send size={16} /> {enviando === "todos" ? "Enviando..." : "Enviar a todos los pendientes"}
            </button>
          )}
          {resumen && (
            <p className="act-panel-nota">
              {resumen.enviados} enviados · {resumen.yaEnviados} ya tenían recordatorio · {resumen.fallidos} fallidos
            </p>
          )}
          <p className="act-panel-nota">
            {esEnlace
              ? "Cada clic abre WhatsApp con el mensaje y el enlace del paciente."
              : "Los recordatorios se envían por el canal configurado."}
          </p>
        </>
      )}
    </section>
  );
}

// ── "Lo que ha pasado": línea de tiempo ────────────────────────────────────────

function AvisoActividad({ item, esAdmin, onAbrir }) {
  const { Icono, variante, quien, texto } = describir(item);
  const canal = item.origen === "ENLACE" ? "Desde el enlace"
    : item.origen === "WHATSAPP" ? "Por WhatsApp"
    : item.canalEnvio === "enlace" ? "Enlace por WhatsApp" : "WhatsApp";
  return (
    <button type="button" className={`act-aviso ${item.leida ? "" : "nuevo"}`} onClick={onAbrir}>
      <span className="act-aviso-riel" aria-hidden="true">
        <span className={`act-aviso-icono ${variante}`}><Icono size={20} strokeWidth={2.4} /></span>
        <span className="act-aviso-linea" />
      </span>
      <span className="act-aviso-texto">
        <span className="act-aviso-frase">
          <b>{quien}</b> {texto}
        </span>
        <span className="act-aviso-detalle">
          <time dateTime={item.creadaEn} title={new Date(item.creadaEn).toLocaleString("es-MX")}>{haceCuanto(item.creadaEn)}</time>
          {" · "}{canal}
          {esAdmin && item.medicoNombre ? ` · ${item.medicoNombre}` : ""}
        </span>
      </span>
      {!item.leida && <span className="act-nuevo">Nuevo</span>}
    </button>
  );
}

function describir(item) {
  const corto = nombreCorto(item.pacienteNombre);
  const completo = item.pacienteNombre || "El paciente";
  const cuando = item.citaFechaHora ? `${relativoCita(item.citaFechaHora)} a las ${formatCitaHora12(item.citaFechaHora)}` : "";
  if (item.categoria === "CONFIRMACION") {
    return { Icono: Check, variante: "confirmacion", quien: corto, texto: `confirmó su cita ${cuando}` };
  }
  if (item.categoria === "CANCELACION") {
    return { Icono: X, variante: "cancelacion", quien: corto, texto: `canceló su cita ${cuando}` };
  }
  if (item.estado === "FALLIDO") {
    return { Icono: TriangleAlert, variante: "fallido", quien: completo, texto: `no recibió el recordatorio de su cita ${cuando} (falló el envío)` };
  }
  return {
    Icono: item.canalEnvio === "enlace" ? Link2 : Send,
    variante: "recordatorio",
    quien: completo,
    texto: item.canalEnvio === "enlace" ? `recibió el enlace de su cita ${cuando}` : `recibió el recordatorio de su cita ${cuando}`,
  };
}

// ── Carga y vacío ──────────────────────────────────────────────────────────────

function EsqueletoLista() {
  return (
    <div className="act-esqueleto" aria-busy="true" aria-label="Cargando actividad">
      {[0, 1, 2, 3, 4].map((i) => (
        <div className="act-skel-fila" key={i}>
          <span className="act-skel cuadro" />
          <span className="act-skel linea" />
        </div>
      ))}
    </div>
  );
}

function EstadoVacio({ icono: Icono, titulo, texto }) {
  return (
    <div className="act-vacio">
      <span className="act-vacio-icono"><Icono size={28} /></span>
      <strong>{titulo}</strong>
      <p>{texto}</p>
    </div>
  );
}

// ── Fechas y textos ─────────────────────────────────────────────────────────────

const DIA_MS = 86400000;

function saludo() {
  const h = Number(new Intl.DateTimeFormat("en-US", { hour: "numeric", hourCycle: "h23", timeZone: CLINIC_TIME_ZONE }).format(new Date()));
  return h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches";
}

/** Fecha "YYYY-MM-DD" de un instante real en la zona de la clínica. */
function claveDiaClinica(instante) {
  return new Intl.DateTimeFormat("en-CA", { timeZone: CLINIC_TIME_ZONE, year: "numeric", month: "2-digit", day: "2-digit" })
    .format(new Date(instante));
}

function diasEntre(desdeKey, hastaKey) {
  return Math.round((Date.parse(`${hastaKey}T00:00:00Z`) - Date.parse(`${desdeKey}T00:00:00Z`)) / DIA_MS);
}

function agruparPorDia(items) {
  const hoy = clinicToday();
  const grupos = [];
  for (const item of items) {
    const clave = claveDiaClinica(item.creadaEn);
    let grupo = grupos[grupos.length - 1];
    if (!grupo || grupo.clave !== clave) {
      const d = diasEntre(clave, hoy);
      const titulo = d === 0 ? "Hoy" : d === 1 ? `Ayer · ${fechaLarga(clave)}` : capitalizar(fechaLarga(clave));
      grupo = { clave, titulo, items: [] };
      grupos.push(grupo);
    }
    grupo.items.push(item);
  }
  return grupos;
}

/** "de hoy" / "de mañana" / "de ayer" / "del jueves" / "del 3 de diciembre" (convención de reloj en UTC). */
function relativoCita(fechaHora) {
  const d = diasEntre(clinicToday(), citaDateKey(fechaHora));
  if (d === 0) return "de hoy";
  if (d === 1) return "de mañana";
  if (d === -1) return "de ayer";
  const fecha = new Date(fechaHora);
  if (d > 1 && d < 7) return `del ${fecha.toLocaleDateString("es-MX", { weekday: "long", timeZone: UTC })}`;
  return `del ${fecha.toLocaleDateString("es-MX", { day: "numeric", month: "long", timeZone: UTC })}`;
}

/** "lunes 23 de noviembre" a partir de "YYYY-MM-DD". */
function fechaLarga(clave) {
  return new Date(`${clave}T12:00:00Z`).toLocaleDateString("es-MX", { weekday: "long", day: "numeric", month: "long", timeZone: UTC });
}

function haceCuanto(instante) {
  const min = Math.floor((Date.now() - new Date(instante).getTime()) / 60000);
  if (min < 1) return "Ahora";
  if (min < 60) return `Hace ${min} min`;
  if (min < 24 * 60 && claveDiaClinica(instante) === clinicToday()) return `Hace ${Math.floor(min / 60)} h`;
  return formatHoraInstante(instante, CLINIC_TIME_ZONE);
}

/** "Ana Sofía Ramírez Mendoza" → "Ana Sofía" (dos primeras palabras). */
function nombreCorto(nombre) {
  if (!nombre) return "Un paciente";
  return nombre.trim().split(/\s+/).slice(0, 2).join(" ");
}

function capitalizar(texto) {
  return texto ? texto[0].toUpperCase() + texto.slice(1) : texto;
}
