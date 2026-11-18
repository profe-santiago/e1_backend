/**
 * Grabación de la demo de CliniClick con Playwright (Chromium visible, video 1920x1080).
 *
 * Uso (desde scripts/demo, con el backend en :8080 y Vite en :5173):
 *   npm install                      # una vez
 *   node grabar_demo.mjs             # toma real (cita de Ana mañana 11:00, buscada por API)
 *   node grabar_demo.mjs --ensayo    # ensayo con la cita de Ana de mañana a las 12:00, sin exigir la confirmación
 *   node grabar_demo.mjs --verificar # revisa selectores en modo headless, SIN grabar y SIN dar clic en "Enviar"
 *   node grabar_demo.mjs --cita <id> # otra cita para la toma real
 *   node grabar_demo.mjs --hora 9:30 # la cita de Ana de mañana a esa hora (24 h; por defecto 11:00, ensayo 12:00)
 *
 * La contraseña del seed se toma de DEMO_PASSWORD o de SeedDataLoader.java. Nunca se imprime.
 * El script NO envía nada por WhatsApp: abre WhatsApp de escritorio (whatsapp://send) con el mensaje listo y
 * tú das Enter. Si el protocolo falla, copia el mensaje al portapapeles como respaldo.
 */
import { chromium } from "playwright";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import readline from "node:readline";
import { fileURLToPath } from "node:url";
import { APARTAR_CURSOR, VIDEO } from "./config_video.mjs";

// ── Configuración ──────────────────────────────────────────────────────────────
const AQUI = path.dirname(fileURLToPath(import.meta.url));
const RAIZ = path.resolve(AQUI, "..", "..");
const SALIDA = path.join(RAIZ, "demo-video");
const BASE = "http://localhost:5173";
const API_DIRECTA = "http://localhost:8080";
const ADMIN = "admin@clinicasantalucia.mx";
const MEDICA = "mariana.lopez@clinicasantalucia.mx";
const PACIENTE = "Ana Sofía Ramírez Mendoza";
const MEDICA_NOMBRE = "Dra. Mariana López Ruiz";
const ZONA = "America/Mexico_City";

const args = process.argv.slice(2);
const ENSAYO = args.includes("--ensayo");
const VERIFICAR = args.includes("--verificar");
const citaArg = args.includes("--cita") ? args[args.indexOf("--cita") + 1] : null;
/** Hora de la cita de Ana mañana: --hora 9:30 | 09:30 (por defecto 11:00; en --ensayo, 12:00). */
const HORA = normalizarHora(args.includes("--hora") ? args[args.indexOf("--hora") + 1] : ENSAYO ? "12:00" : "11:00");

function normalizarHora(valor) {
  const m = /^(\d{1,2}):(\d{2})$/.exec(String(valor || "").trim());
  if (!m || Number(m[1]) > 23 || Number(m[2]) > 59) {
    console.error(`Hora inválida: "${valor}". Usa el formato de 24 h, por ejemplo --hora 9:30 o --hora 16:00.`);
    process.exit(1);
  }
  return `${m[1].padStart(2, "0")}:${m[2]}`;
}
const ESPERA_MAX_MS = ENSAYO ? 20_000 : 5 * 60_000;

const PASSWORD = process.env.DEMO_PASSWORD || leerPasswordSeed();

// ── Utilidades ─────────────────────────────────────────────────────────────────
const dormir = (ms) => new Promise((r) => setTimeout(r, ms));
const pausa = (min = 1000, max = 2000) => dormir(min + Math.random() * (max - min));
const log = (...m) => console.log(...m);

function leerPasswordSeed() {
  const archivo = path.join(RAIZ, "mi-app/src/main/java/com/clinica/mi_app/seed/SeedDataLoader.java");
  const m = /PASSWORD\s*=\s*"([^"]+)"/.exec(fs.readFileSync(archivo, "utf8"));
  if (!m) throw new Error("No encontré la contraseña del seed; define DEMO_PASSWORD.");
  return m[1];
}

async function api(metodo, ruta, { token, body } = {}) {
  const r = await fetch(API_DIRECTA + ruta, {
    method: metodo,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const texto = await r.text();
  let datos = null;
  try { datos = texto ? JSON.parse(texto) : null; } catch { datos = texto; }
  if (!r.ok) throw new Error(`${metodo} ${ruta} → HTTP ${r.status}`);
  return datos;
}

const login = (email) => api("POST", "/api/auth/login", { body: { email, password: PASSWORD } });

/** "YYYY-MM-DD" de mañana en la zona de la clínica. */
function mananaClinica() {
  const hoy = new Intl.DateTimeFormat("en-CA", { timeZone: ZONA }).format(new Date());
  const d = new Date(`${hoy}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
}

function copiarAlPortapapeles(texto) {
  const tmp = path.join(os.tmpdir(), `cliniclick-mensaje-${process.pid}.txt`);
  fs.writeFileSync(tmp, texto, "utf8");
  const r = spawnSync("powershell", ["-NoProfile", "-Command",
    `Get-Content -Raw -Encoding UTF8 -LiteralPath '${tmp}' | Set-Clipboard`], { stdio: "ignore" });
  fs.rmSync(tmp, { force: true });
  return r.status === 0;
}

/**
 * Abre una URL con el protocolo registrado en Windows (whatsapp://...). Equivale a `cmd /c start "" "<url>"`,
 * pero con Start-Process y la URL en una variable de entorno: en cmd, el texto codificado (%20...%) podría
 * interpretarse como variables y romper el mensaje.
 */
function abrirProtocolo(url) {
  const r = spawnSync("powershell", ["-NoProfile", "-Command",
    "try { Start-Process -FilePath $env:URL_DEMO -ErrorAction Stop; exit 0 } catch { exit 1 }"],
    { stdio: "ignore", env: { ...process.env, URL_DEMO: url } });
  return r.status === 0;
}

/**
 * Configuración común de video (config_video.mjs): viewport 1280×720 × 1.5 → video 1920×1080 nítido, con la app
 * a tamaño cómodo. En modo kiosco (por defecto) la ventana ocupa la pantalla sin pestañas ni barra de direcciones;
 * con --ventana se abre en una ventana normal del mismo tamaño.
 */
const KIOSCO = !args.includes("--ventana");
const VENTANA = { ancho: VIDEO.viewport.width, alto: VIDEO.viewport.height, escala: VIDEO.deviceScaleFactor };
/** Marco aproximado de Chromium en modo ventana (en kiosco no hay marco). */
const MARCO = KIOSCO ? { ancho: 0, alto: 0 } : { ancho: 16, alto: 88 };

function esperarEnter(mensaje) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    rl.question(mensaje, () => { rl.close(); resolve(); });
  });
}

function hayFfmpeg() {
  return spawnSync("ffmpeg", ["-version"], { stdio: "ignore", shell: true }).status === 0;
}

function convertirAMp4(webm) {
  const mp4 = webm.replace(/\.webm$/, ".mp4");
  const r = spawnSync("ffmpeg", ["-y", "-loglevel", "error", "-i", webm, "-c:v", "libx264", "-preset", "veryfast",
    "-r", "30", "-pix_fmt", "yuv420p", "-movflags", "+faststart", mp4], { stdio: "inherit", shell: false });
  return r.status === 0 ? mp4 : null;
}

// ── Cursor visible y movimientos "humanos" ─────────────────────────────────────
const CURSOR = `
(() => {
  if (window.__cursorDemo) return; window.__cursorDemo = true;
  const poner = () => {
    const c = document.createElement("div");
    c.id = "cursor-demo";
    // 14 px y semitransparente para no tapar el texto
    c.style.cssText = "position:fixed;left:0;top:0;width:14px;height:14px;margin:-7px 0 0 -7px;border-radius:50%;" +
      "background:rgba(208,255,113,.45);border:1.5px solid rgba(32,55,59,.8);box-shadow:0 0 0 1.5px rgba(255,255,255,.7);pointer-events:none;opacity:.85;" +
      "z-index:2147483647;transition:transform .12s ease, background .12s ease;transform:translate(-100px,-100px)";
    document.documentElement.appendChild(c);
    let x = -100, y = -100;
    addEventListener("mousemove", (e) => { x = e.clientX; y = e.clientY; c.style.transform = "translate(" + x + "px," + y + "px)"; }, true);
    addEventListener("mousedown", () => { c.style.transform = "translate(" + x + "px," + y + "px) scale(.7)"; c.style.background = "rgba(34,134,130,.7)"; }, true);
    addEventListener("mouseup", () => { c.style.transform = "translate(" + x + "px," + y + "px) scale(1)"; c.style.background = "rgba(208,255,113,.55)"; }, true);
  };
  if (document.readyState === "loading") addEventListener("DOMContentLoaded", poner); else poner();
})();`;

async function irA(page, locator) {
  await locator.waitFor({ state: "visible", timeout: 15_000 });
  await locator.scrollIntoViewIfNeeded();
  const caja = await locator.boundingBox();
  if (!caja) throw new Error("El elemento no tiene posición en pantalla");
  // Apunta al borde izquierdo del elemento (no al centro del texto) para no taparlo
  const x = caja.x + Math.min(14, caja.width / 3);
  const y = caja.y + caja.height / 2;
  await page.mouse.move(x, y, { steps: 28 });
  await dormir(350);
}

async function clic(page, locator) {
  await irA(page, locator);
  const caja = await locator.boundingBox();
  await locator.click();
  await apartarCursor(page, caja);
}

/** Tras el clic, aparta el cursor 60–80 px abajo a la derecha del elemento para no tapar el texto que cambió. */
async function apartarCursor(page, caja) {
  if (!caja) return;
  const { min, max } = APARTAR_CURSOR;
  const d = () => min + Math.random() * (max - min);
  const vp = page.viewportSize() || VIDEO.viewport;
  const x = Math.min(vp.width - 8, caja.x + caja.width + d());
  const y = Math.min(vp.height - 8, caja.y + caja.height + d());
  await page.mouse.move(x, y, { steps: 14 });
}

/**
 * Escribe como persona y VERIFICA el resultado: limpia el campo, escribe con pressSequentially y compara el value
 * con lo esperado; si no coincide (p. ej. una tecla de más), lo corrige antes de seguir. Con `secreto` solo se
 * compara la longitud en los mensajes (nunca se imprime el valor).
 */
async function escribir(page, locator, texto, { secreto = false } = {}) {
  await clic(page, locator);
  for (let intento = 1; intento <= 3; intento++) {
    await locator.fill("");
    await locator.pressSequentially(texto, { delay: 70 + Math.random() * 40 });
    const valor = await locator.inputValue();
    if (valor === texto) return;
    log(secreto
      ? `  (corrijo el campo: tenía ${valor.length} caracteres y se esperaban ${texto.length})`
      : `  (corrijo el campo: decía "${valor}" y se esperaba "${texto}")`);
    await dormir(400);
  }
  await locator.fill(texto);
  if ((await locator.inputValue()) !== texto) throw new Error(secreto ? "No se pudo escribir la contraseña" : `No se pudo escribir "${texto}"`);
}

// ── Pasos ──────────────────────────────────────────────────────────────────────
let pasoActual = "preparación";
async function paso(nombre, fn) {
  pasoActual = nombre;
  log(`\n▶ ${nombre}`);
  return fn();
}

async function iniciarSesion(page, email) {
  await page.goto(BASE + "/login");
  await page.locator('input[name="email"]').waitFor();
  await dormir(VERIFICAR ? 300 : 4000);                          // pantalla quieta
  await escribir(page, page.locator('input[name="email"]'), email);
  await pausa();
  await escribir(page, page.locator('input[name="password"]'), PASSWORD, { secreto: true });
  await pausa();
  await clic(page, page.getByRole("button", { name: /^Entrar/ }));
  await page.locator(".sidebar").waitFor({ timeout: 20_000 });
}

async function cerrarSesion(page) {
  await clic(page, page.locator(".nav-footer .nav-item", { hasText: "Salir" }));
  await page.locator('input[name="email"]').waitFor();
}

function filaDeAna(page, horaTexto) {
  return page.locator(".act-panel-lista li", { hasText: PACIENTE }).filter({ hasText: horaTexto });
}

async function main() {
  fs.mkdirSync(SALIDA, { recursive: true });

  // ── Checklist previo ──
  log(`\nCliniClick · ${VERIFICAR ? "verificación (sin grabar)" : ENSAYO ? "ENSAYO" : "toma real"}`);
  const vite = await fetch(BASE + "/").then((r) => r.ok).catch(() => false);
  let admin, medica, cita;
  try {
    admin = await login(ADMIN);
    medica = await login(MEDICA);
  } catch {
    admin = null;
  }
  const backend = !!admin;
  if (backend) cita = await buscarCita(admin).catch((e) => ({ error: e.message }));
  const citaOk = !!cita && !cita.error && cita.estado === "SIN_CONFIRMAR";

  log("\nChecklist:");
  log(`  [${backend ? "x" : " "}] Backend arriba (${API_DIRECTA})`);
  log(`  [${vite ? "x" : " "}] Vite arriba (${BASE})`);
  log(`  [${citaOk ? "x" : " "}] Cita validada: ${cita?.error || (cita ? `${PACIENTE} · ${cita.fechaHora} · ${cita.estado} · recordatorio: ${cita.recordatorio} · ${cita.id}` : "—")}`);
  if (!backend || !vite || (!citaOk && !VERIFICAR)) {
    log("\nNo se cumple el checklist; no se grabó nada.");
    process.exitCode = 1;
    return;
  }
  const horaTexto = citaOk ? horaDeCita(cita) : null;
  if (!VERIFICAR) await esperarEnter("\nPrepara los celulares y presiona Enter para empezar a grabar... ");

  // ── Navegador ──
  const v = VENTANA;
  log(`\nVentana: ${KIOSCO ? "pantalla completa (kiosco)" : "normal"} · ${v.ancho}×${v.alto} · escala ${v.escala} → video 1920×1080`);
  if (KIOSCO && !VERIFICAR) log("  Para salir antes de tiempo: Ctrl+C aquí en la terminal (o Alt+F4 en la ventana).");
  const browser = await chromium.launch({
    headless: VERIFICAR,
    // Sin la barra "Chrome está siendo controlado por software de prueba automatizado"
    ignoreDefaultArgs: ["--enable-automation"],
    args: [
      `--window-size=${v.ancho + MARCO.ancho},${v.alto + MARCO.alto}`,
      "--window-position=0,0",
      ...(KIOSCO && !VERIFICAR ? ["--kiosk"] : []),
    ],
  });
  const context = await browser.newContext({
    viewport: { width: v.ancho, height: v.alto },
    deviceScaleFactor: v.escala,
    locale: "es-MX",
    timezoneId: ZONA,
    ...(VERIFICAR ? {} : { recordVideo: { dir: SALIDA, size: VIDEO.recordVideo } }),
  });
  await context.addInitScript(CURSOR);

  // wa.me nunca se carga: se registra la URL y se responde una página vacía
  let urlWa = null;
  await context.route(/https:\/\/(wa\.me|api\.whatsapp\.com)\/.*/, (route) => {
    urlWa = urlWa || route.request().url();
    return route.fulfill({ status: 200, contentType: "text/html", body: "<html><body></body></html>" });
  });

  const page = await context.newPage();
  const paginasExtra = [];
  context.on("page", (p) => { if (p !== page) paginasExtra.push(p); });

  let fallo = null;
  let reporteEnsayo = null;
  try {
    await paso("1. Login como administración", () => iniciarSesion(page, ADMIN));

    await paso("2. Inicio de la administración: ingresos", async () => {
      await page.locator(".admin-metrics").waitFor({ timeout: 20_000 });
      await dormir(VERIFICAR ? 300 : 6000);
      const barra = page.locator(".chart-bar").nth(5);
      if (await barra.count()) await irA(page, barra);
      else log("  (no hay barras en la gráfica de ingresos; sigo)");
      await pausa();
      await clic(page, page.locator(".admin-period-btn", { hasText: "30 días" }));
      await dormir(VERIFICAR ? 300 : 4000);
    });

    await paso("3. Login como la Dra. Mariana y Actividad", async () => {
      await cerrarSesion(page);
      await iniciarSesion(page, MEDICA);
      await pausa();
      await clic(page, page.locator(".nav-item", { hasText: "Actividad" }));
      await page.locator(".act-tira").waitFor({ timeout: 20_000 });
      await dormir(VERIFICAR ? 300 : 5000);
      const bloques = page.locator(".act-tira-bloque");
      const n = await bloques.count();
      for (let i = 0; i < n; i += Math.max(1, Math.floor(n / 5))) await irA(page, bloques.nth(i));
      await pausa();
      await irA(page, page.locator(".act-panel-cabeza"));
      if (horaTexto) await irA(page, filaDeAna(page, horaTexto));
      await pausa();
    });

    if (VERIFICAR) {
      await paso("Verificación: botón Enviar, campana y avisos (sin clic en Enviar)", async () => {
        if (horaTexto) await filaDeAna(page, horaTexto).locator(".act-panel-enviar").waitFor({ timeout: 10_000 });
        else log("  (sin cita validada: no reviso el botón Enviar de Ana)");
        await clic(page, page.locator(".bell-btn"));
        await page.locator(".bell-panel").waitFor();
        await page.keyboard.press("Escape");
        await page.locator(".act-aviso").first().waitFor({ timeout: 10_000 });
      });
    } else {
      let mensaje = null;
      await paso("4. Enviar el recordatorio de Ana", async () => {
        const boton = filaDeAna(page, horaTexto).locator(".act-panel-enviar");
        await irA(page, boton);
        const popup = context.waitForEvent("page", { timeout: 15_000 }).catch(() => null);
        const cajaBoton = await boton.boundingBox();
        await boton.click();
        await apartarCursor(page, cajaBoton);
        const pestana = await popup;
        for (let i = 0; i < 50 && !urlWa; i++) await dormir(200);
        if (!urlWa) throw new Error("No se abrió la pestaña de wa.me");
        if (pestana) await pestana.close().catch(() => {});
        const wa = new URL(urlWa);
        mensaje = wa.searchParams.get("text") || "";
        const telefono = wa.pathname.replace(/\D/g, "");          // "52" + 10 dígitos
        log("\n──────── Mensaje para WhatsApp ────────\n" + mensaje + "\n───────────────────────────────────────");
        const escritorio = `whatsapp://send?phone=${telefono}&text=${encodeURIComponent(mensaje)}`;
        if (abrirProtocolo(escritorio)) {
          log("✓ Abrí WhatsApp de escritorio con el chat y el mensaje listos: solo da Enter.");
        } else {
          log("⚠ No se pudo abrir whatsapp:// (¿WhatsApp de escritorio instalado?).");
          log(copiarAlPortapapeles(mensaje) ? "  Respaldo: el mensaje quedó en el portapapeles." : "  Tampoco se pudo copiar al portapapeles.");
        }
        await dormir(2000);
        await page.bringToFront().catch(() => {});               // si no regresa el foco, el video sigue grabando
        await page.locator(".act-panel-cabeza span", { hasText: /^[1-9]\d* de \d+ enviados$/ }).waitFor({ timeout: 15_000 });
        await irA(page, page.locator(".act-panel-cabeza span"));
        await dormir(3000);
      });

      const confirmada = await paso("5. Esperando la confirmación desde el celular", async () => {
        log(`\n👉 Da Enter en WhatsApp para enviar el mensaje y confirma desde el celular de Ana. (espero hasta ${ESPERA_MAX_MS / 1000} s)`);
        const fin = Date.now() + ESPERA_MAX_MS;
        while (Date.now() < fin) {
          const c = await api("GET", `/api/citas/${cita.id}`, { token: medica.token }).catch(() => null);
          if (c?.estado === "CONFIRMADA") { log("✓ La cita ya está CONFIRMADA."); return true; }
          if (c && c.estado !== "SIN_CONFIRMAR") { log(`La cita pasó a ${c.estado}.`); return false; }
          await dormir(2000);
        }
        log(ENSAYO ? "(ensayo) No llegó la confirmación; sigo con el final." : "⚠ Se agotó la espera de 5 min; sigo con el final.");
        return false;
      });

      await paso("6. Aviso, campana y agenda", async () => {
        const patron = confirmada ? /confirmó su cita/ : /recibió el (enlace|recordatorio)/;
        const aviso = page.locator(".act-aviso", { hasText: "Ana Sofía" }).filter({ hasText: patron }).first();
        for (let i = 0; i < 12 && !(await aviso.count()); i++) {
          await clic(page, page.locator('.act-icono-btn[aria-label="Actualizar"]'));
          await dormir(2500);
        }
        await clic(page, page.locator(".bell-btn"));
        await page.locator(".bell-panel").waitFor();
        await dormir(4000);
        await page.keyboard.press("Escape");
        await pausa();
        await clic(page, aviso);
        await page.locator(".agenda-item.resaltada").waitFor({ timeout: 15_000 });
        await irA(page, page.locator(".agenda-item.resaltada"));
        await dormir(5000);
      });

      if (ENSAYO) {
        await page.goto(BASE + "/modulo/notificaciones");
        const cabeza = page.locator(".act-panel-cabeza span");
        await cabeza.waitFor({ timeout: 10_000 }).catch(() => {});
        const c = await api("GET", `/api/citas/${cita.id}`, { token: medica.token }).catch(() => null);
        reporteEnsayo = { panel: (await cabeza.textContent().catch(() => null)) || "—", estado: c?.estado || "—" };
      }

      await paso("7. Fin", () => dormir(3000));
    }
  } catch (err) {
    fallo = err;
    const captura = path.join(SALIDA, `fallo-${marca()}.png`);
    await page.screenshot({ path: captura, fullPage: false }).catch(() => {});
    log(`\n✗ Falló en el paso «${pasoActual}»: ${err.message}`);
    log(`  Captura: ${captura}`);
  }

  // ── Cierre: el video se escribe al cerrar el contexto ──
  const video = page.video();
  const extras = paginasExtra.map((p) => p.video()).filter(Boolean);
  await context.close();
  await browser.close();
  if (VERIFICAR) {
    log(fallo ? "\nVerificación con errores." : "\n✓ Verificación completa: todos los selectores existen.");
    process.exitCode = fallo ? 1 : 0;
    return;
  }
  for (const v of extras) fs.rmSync(await v.path(), { force: true });      // video de la pestaña wa.me
  const original = await video.path();
  const final = path.join(SALIDA, `demo-cliniclick-${marca()}${ENSAYO ? "-ensayo" : ""}${fallo ? "-incompleto" : ""}.webm`);
  fs.renameSync(original, final);
  log(`\n🎬 Video: ${final}`);
  if (hayFfmpeg()) {
    const mp4 = convertirAMp4(final);
    log(mp4 ? `🎬 MP4:   ${mp4}` : "⚠ No se pudo convertir a MP4.");
  } else {
    log("ℹ ffmpeg no está instalado: solo quedó el .webm. Instálalo y vuelve a correr el script, o conviértelo con:");
    log(`  ffmpeg -i "${final}" -c:v libx264 -preset veryfast -r 30 -pix_fmt yuv420p "${final.replace(/\.webm$/, ".mp4")}"`);
  }
  if (reporteEnsayo) {
    log(`\nEnsayo · panel "Por confirmar": ${reporteEnsayo.panel}`);
    log(`Ensayo · cita de las ${HORA} (${cita.id}): ${reporteEnsayo.estado}`);
    log("Nota: este envío queda registrado; en la toma real el panel empezará con ese envío ya contado.");
  }
  if (fallo) process.exitCode = 1;
}

/**
 * Cita de la toma, buscada por API: la de Ana con la Dra. Mariana de MAÑANA a las 11:00 (toma real) o a las 12:00
 * (--ensayo), SIN_CONFIRMAR. En la toma real además debe estar SIN recordatorio, para que el panel empiece limpio.
 * Con --cita <id> se usa esa cita, con las mismas validaciones.
 */
async function buscarCita(admin) {
  const hora = HORA;
  const org = admin.organizacionId;
  const pacientes = await api("GET", `/api/pacientes/organizacion/${org}`, { token: admin.token });
  const ana = pacientes.find((p) => p.nombre === PACIENTE);
  const medicos = await api("GET", `/api/medicos/organizacion/${org}`, { token: admin.token });
  const mariana = medicos.find((m) => m.nombre === MEDICA_NOMBRE);
  if (!ana || !mariana) throw new Error("No encontré a la paciente o a la médica");

  let c;
  if (citaArg) {
    c = await api("GET", `/api/citas/${citaArg}`, { token: admin.token });
    if (c.pacienteId !== ana.id) throw new Error(`La cita ${citaArg} no es de ${PACIENTE}`);
    if (c.medicoId !== mariana.id) throw new Error(`La cita ${citaArg} no es con ${MEDICA_NOMBRE}`);
  } else {
    const clave = `${mananaClinica()}T${hora}`;
    const citas = await api("GET", `/api/citas/paciente/${ana.id}`, { token: admin.token });
    const candidatas = citas.filter((x) => x.medicoId === mariana.id && String(x.fechaHora).startsWith(clave) && x.estado !== "CANCELADA");
    if (!candidatas.length) {
      throw new Error(`No hay cita de ${PACIENTE} mañana a las ${hora} con ${MEDICA_NOMBRE}. ` +
        `Créala con: python scripts/demo_ana.py${ENSAYO ? "" : " --limpiar"} --hora ${hora}`);
    }
    if (candidatas.length > 1) throw new Error(`Hay ${candidatas.length} citas de ${PACIENTE} mañana a las ${hora}; usa --cita <id>`);
    c = candidatas[0];
  }
  if (c.estado !== "SIN_CONFIRMAR") throw new Error(`La cita ${c.id} está ${c.estado}; debe estar SIN_CONFIRMAR`);

  const estados = await api("GET", `/api/whatsapp/recordatorios/estado?citaIds=${c.id}`, { token: admin.token });
  const rec = estados?.[c.id];
  if (rec && !ENSAYO) {
    throw new Error(`La cita ${c.id} ya tiene un recordatorio (${rec.estado}); el panel no empezaría en 0. ` +
      `Recrea los datos con: python scripts/demo_ana.py --limpiar --hora ${hora}`);
  }
  return { ...c, recordatorio: rec ? rec.estado : "ninguno" };
}

/** "11:00 a.m." como lo muestra la app (convención de reloj en UTC). */
function horaDeCita(c) {
  const d = new Date(c.fechaHora);
  const h = d.getUTCHours();
  return `${h % 12 === 0 ? 12 : h % 12}:${String(d.getUTCMinutes()).padStart(2, "0")} ${h < 12 ? "a.m." : "p.m."}`;
}

function marca() {
  const p = Object.fromEntries(new Intl.DateTimeFormat("en-CA", {
    timeZone: ZONA, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(new Date()).map((x) => [x.type, x.value]));
  return `${p.year}-${p.month}-${p.day}-${p.hour}${p.minute}`;
}

main().catch((err) => {
  console.error(`\n✗ Error en «${pasoActual}»: ${err.message}`);
  process.exitCode = 1;
});
