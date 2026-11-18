/**
 * FASE 1 del recorrido — Inventario visual (SOLO LECTURA: navega y toma capturas, no guarda nada).
 * Entra con los tres roles a 1280×720 (config_video.mjs), captura cada pantalla del menú y las internas
 * principales, y detecta automáticamente: valores crudos (ENUMS, UUIDs), palabras sin acento, tablas vacías y
 * desbordes. Capturas en demo-video/inventario/ y reporte en demo-video/inventario/reporte.json.
 *
 *   node inventario.mjs
 *   node inventario.mjs --solo pagos,citas,ajustes   → solo las pantallas cuyo nombre contenga alguno de esos textos,
 *                                                      en demo-video/inventario/despues/ (no borra las anteriores)
 *
 * Nunca guarda una captura en la que se vea un token de sesión (JWT).
 */
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VIDEO } from "./config_video.mjs";

const AQUI = path.dirname(fileURLToPath(import.meta.url));
const RAIZ = path.resolve(AQUI, "..", "..");
const SOLO = (() => {
  const i = process.argv.indexOf("--solo");
  return i > -1 ? String(process.argv[i + 1] || "").split(",").map((x) => x.trim()).filter(Boolean) : null;
})();
const OUT = path.join(RAIZ, "demo-video", "inventario", ...(SOLO ? ["despues"] : []));
const BASE = "http://localhost:5173";
const API = "http://localhost:8080";
const PW = process.env.DEMO_PASSWORD
  || /PASSWORD\s*=\s*"([^"]+)"/.exec(fs.readFileSync(path.join(RAIZ, "mi-app/src/main/java/com/clinica/mi_app/seed/SeedDataLoader.java"), "utf8"))[1];
const ROLES = {
  admin: "admin@clinicasantalucia.mx",
  medica: "mariana.lopez@clinicasantalucia.mx",
  paciente: "ana.ramirez@correo.mx",
};

async function api(ruta, token) {
  const r = await fetch(API + ruta, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  return r.ok ? r.json() : null;
}
async function loginApi(email) {
  const r = await fetch(API + "/api/auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password: PW }) });
  return r.json();
}

/** Revisión automática del texto visible de la pantalla. */
async function revisar(page) {
  return page.evaluate(() => {
    const visibles = [];
    const walker = document.createTreeWalker(document.querySelector("#app") || document.body, NodeFilter.SHOW_TEXT);
    while (walker.nextNode()) {
      const n = walker.currentNode;
      const t = n.textContent.trim();
      if (!t || !n.parentElement) continue;
      const el = n.parentElement;
      if (el.closest("script,style,option[hidden]")) continue;
      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.height === 0 || getComputedStyle(el).visibility === "hidden") continue;
      visibles.push(t);
    }
    const texto = visibles.join(" · ");
    const enums = [...new Set((texto.match(/\b[A-Z]{3,}(?:_[A-Z0-9]+)+\b|\b(?:ADMIN|MEDICO|PACIENTE|PAGADO|PENDIENTE|CONFIRMADA|CANCELADA|SIN_CONFIRMAR|EFECTIVO|TARJETA|TRANSFERENCIA|PRINCIPAL|SECUNDARIO|SOLICITADO|SUBIDO|REVISADO|ENVIADO|FALLIDO|WHATSAPP|CLINICA|SOLO|ENTERPRISE)\b/g) || []))];
    const uuids = [...new Set(texto.match(/\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi) || [])].length;
    const sinAcento = [...new Set((texto.match(/\b(?:medico|medicos|medica|clinica|diagnostico|diagnosticos|telefono|direccion|organizacion|configuracion|proxima|proximas|sesion|electronico|contrasena|duracion|descripcion|codigo|numero|ultima|ultimo|informacion|accion|metodo|dia|dias|aun|seleccion|pagina|cedula|genero|valido|invalido|busqueda|categoria|historico)\b/gi) || []))];
    const vacias = [...document.querySelectorAll(".empty")].filter((e) => e.getBoundingClientRect().height > 0).map((e) => e.textContent.trim()).slice(0, 4);
    const filasTabla = document.querySelectorAll("tbody tr").length;
    const scrollH = document.documentElement.scrollWidth > document.documentElement.clientWidth + 1;
    const errores = [...document.querySelectorAll(".error-box")].map((e) => e.textContent.trim()).slice(0, 3);
    return { enums, uuids, sinAcento, vacias, filasTabla, scrollH, errores, titulo: document.querySelector(".view-header h2, .act-encabezado h1, h1, h2")?.textContent.trim() };
  });
}

fs.mkdirSync(OUT, { recursive: true });
const reporte = [];
const browser = await chromium.launch();
let n = 0;

async function capturar(page, rol, nombre) {
  n += 1;
  if (SOLO && !SOLO.some((t) => `${rol}-${nombre}`.includes(t))) return;
  await page.waitForTimeout(2200);
  const archivo = `${String(n).padStart(2, "0")}-${rol}-${nombre}.png`;
  const textoVisible = await page.evaluate(() => document.body.innerText + " " + [...document.querySelectorAll("input,textarea")].map((e) => e.value).join(" "));
  if (/eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/.test(textoVisible)) {
    console.log(`${archivo}  NO SE GUARDÓ: la pantalla muestra un token de sesión`);
    return;
  }
  await page.screenshot({ path: path.join(OUT, archivo) });
  const info = await revisar(page);
  reporte.push({ archivo, rol, pantalla: nombre, url: page.url().replace(BASE, ""), ...info });
  const marcas = [
    info.enums.length && `enums: ${info.enums.join(", ")}`,
    info.uuids && `${info.uuids} UUID visibles`,
    info.sinAcento.length && `sin acento: ${info.sinAcento.join(", ")}`,
    info.vacias.length && `vacío: ${info.vacias.join(" | ")}`,
    info.errores.length && `error: ${info.errores.join(" | ")}`,
    info.scrollH && "scroll horizontal",
  ].filter(Boolean);
  console.log(`${archivo}  ${marcas.join(" · ") || "sin hallazgos automáticos"}`);
}

async function entrar(rol) {
  const ctx = await browser.newContext({
    viewport: VIDEO.viewport, deviceScaleFactor: VIDEO.deviceScaleFactor, locale: "es-MX", timezoneId: "America/Mexico_City",
  });
  const page = await ctx.newPage();
  await page.goto(BASE + "/login");
  await page.fill('input[name="email"]', ROLES[rol]);
  await page.fill('input[name="password"]', PW);
  await page.click("button:has-text('Entrar')");
  await page.locator(".sidebar").waitFor({ timeout: 20_000 });
  await page.waitForTimeout(4500); // que desaparezca "Sesión iniciada"
  return { ctx, page };
}

/** Recorre cada ítem del menú lateral (en orden) y lo captura. */
async function recorrerMenu(page, rol) {
  const etiquetas = await page.locator(".sidebar .nav-item span:first-of-type").allTextContents();
  for (const etiqueta of etiquetas) {
    // Ajustes ya no muestra el token (solo con VITE_DEBUG=true); capturar() lo vuelve a comprobar
    if (etiqueta === "Salir") continue;
    await page.locator(".sidebar .nav-item", { hasText: etiqueta }).first().click();
    await capturar(page, rol, `menu-${etiqueta.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/\W+/g, "-")}`);
  }
}

// Login (sin sesión)
{
  const ctx = await browser.newContext({ viewport: VIDEO.viewport, deviceScaleFactor: VIDEO.deviceScaleFactor, locale: "es-MX" });
  const p = await ctx.newPage();
  await p.goto(BASE + "/login");
  await capturar(p, "publico", "login");
  await p.goto(BASE + "/registro");
  await capturar(p, "publico", "registro-paciente");
  await ctx.close();
}

// IDs para las pantallas internas (solo lectura)
const admin = await loginApi(ROLES.admin);
const pacientes = await api(`/api/pacientes/organizacion/${admin.organizacionId}`, admin.token);
const ana = pacientes.find((p) => p.nombre === "Ana Sofía Ramírez Mendoza");
const citasAna = await api(`/api/citas/paciente/${ana.id}`, admin.token);
const citaPasadaAna = citasAna.filter((c) => c.estado === "CONFIRMADA" && new Date(c.fechaHora) < new Date()).sort((a, b) => b.fechaHora.localeCompare(a.fechaHora))[0];
const citaAna = citaPasadaAna || citasAna[0];

// Administración
{
  const { ctx, page } = await entrar("admin");
  await recorrerMenu(page, "admin");
  await page.goto(BASE + "/modulo/citas");
  await page.locator("tbody tr").first().waitFor({ timeout: 15_000 }).catch(() => {});
  const editar = page.locator('button[data-tooltip="Editar registro"]').first();
  if (await editar.count()) {
    await editar.click();
    await capturar(page, "admin", "cita-edicion");
  }
  await page.goto(BASE + "/modulo/citas");
  const estado = page.locator('button[data-tooltip="Cambiar estado"]').first();
  await page.locator("tbody tr").first().waitFor({ timeout: 15_000 }).catch(() => {});
  if (await estado.count()) {
    await estado.click();
    await page.locator(".modal select, dialog select, [role=dialog] select").first().waitFor({ timeout: 5000 }).catch(() => {});
    await capturar(page, "admin", "cita-cambiar-estado");
    // Opciones del select (la captura no muestra la lista desplegada)
    const opciones = await page.locator(".modal select option, dialog select option, [role=dialog] select option").allTextContents();
    if (opciones.length) console.log(`   opciones de estado: ${opciones.join(" | ")}`);
  }
  await ctx.close();
}

// Médica
{
  const { ctx, page } = await entrar("medica");
  await recorrerMenu(page, "medica");
  if (citaAna) {
    await page.goto(`${BASE}/medico/consulta/${citaAna.id}`);
    await capturar(page, "medica", "consulta-de-cita");
  }
  await page.goto(`${BASE}/medico/expediente/${ana.id}`);
  await capturar(page, "medica", "expediente-paciente");
  await ctx.close();
}

// Paciente
{
  const { ctx, page } = await entrar("paciente");
  await recorrerMenu(page, "paciente");
  await ctx.close();
}

await browser.close();
fs.writeFileSync(path.join(OUT, "reporte.json"), JSON.stringify(reporte, null, 2));
console.log(`\n${reporte.length} capturas en ${OUT}`);
