/**
 * Revisión visual (solo lectura): capturas de Login, Inicio del admin, Actividad, Agenda del médico y /c/{token}
 * en 1920x1080, 1440x810, 1366x768 y 1280x720. Mide además desbordes horizontales y elementos encimados.
 *
 *   node revisar_capturas.mjs [carpeta]      (por defecto ../../demo-video/revision)
 *
 * /c/{token} se muestra con datos SIMULADOS (se intercepta la API en el navegador): no se crea ningún enlace real.
 * La contraseña del seed se lee de DEMO_PASSWORD o de SeedDataLoader.java y nunca se imprime.
 */
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const AQUI = path.dirname(fileURLToPath(import.meta.url));
const RAIZ = path.resolve(AQUI, "..", "..");
const OUT = path.resolve(process.argv[2] || path.join(RAIZ, "demo-video", "revision"));
const BASE = "http://localhost:5173";
const PW = process.env.DEMO_PASSWORD
  || /PASSWORD\s*=\s*"([^"]+)"/.exec(fs.readFileSync(path.join(RAIZ, "mi-app/src/main/java/com/clinica/mi_app/seed/SeedDataLoader.java"), "utf8"))[1];
const TAMANOS = [[1920, 1080], [1440, 810], [1366, 768], [1280, 720]];
/** /c/{token} también en celular (como lo ve la paciente). */
const TAMANO_CELULAR = [390, 844];

const CITA_SIMULADA = {
  pacienteNombre: "Ana", clinica: "Clínica Santa Lucía", medico: "Dra. Mariana López Ruiz", especialidad: "Medicina General",
  consultorio: "Consultorio 1 · Planta baja", direccion: "Av. Independencia 812, Centro, Oaxaca de Juárez",
  fechaHora: "2026-11-19T09:30:00Z", estado: "SIN_CONFIRMAR", resultado: null, duracionMin: 30, telefonoClinica: "9515550100",
};

/** Desborde horizontal, elementos fuera de pantalla y pares de hermanos encimados en filas de cita. */
async function medir(page) {
  return page.evaluate(() => {
    const vw = document.documentElement.clientWidth;
    const problemas = [];
    if (document.documentElement.scrollWidth > vw + 1) problemas.push(`scroll horizontal de página (${document.documentElement.scrollWidth}px)`);
    const filas = document.querySelectorAll(".agenda-item, .upcoming-item, .admin-cita, .appointment-row, .patient-mini-row, .act-panel-lista li, .act-tira-item, .metric, .sidebar-usuario");
    filas.forEach((f) => {
      const hijos = [...f.children].filter((h) => getComputedStyle(h).display !== "none" && h.getBoundingClientRect().width > 0);
      for (let i = 0; i < hijos.length; i++) {
        for (let j = i + 1; j < hijos.length; j++) {
          const a = hijos[i].getBoundingClientRect(), b = hijos[j].getBoundingClientRect();
          const x = Math.min(a.right, b.right) - Math.max(a.left, b.left);
          const y = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
          if (x > 2 && y > 2) problemas.push(`encimados en .${f.className.split(" ")[0]}: ${hijos[i].className || hijos[i].tagName} / ${hijos[j].className || hijos[j].tagName}`);
        }
      }
      [...f.querySelectorAll("*")].forEach((el) => {
        if (el.scrollWidth > el.clientWidth + 1 && getComputedStyle(el).overflow === "visible" && el.clientWidth > 0 && el.children.length === 0) {
          problemas.push(`texto más ancho que su caja en .${f.className.split(" ")[0]}: "${el.textContent.trim().slice(0, 30)}"`);
        }
      });
    });
    // Elementos visibles fuera de la ventana (en contenedores sin scroll)
    document.querySelectorAll(".cc-auth *, .pase *").forEach((el) => {
      const r = el.getBoundingClientRect();
      if (r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== "hidden" && !el.closest(".cc-auth-corazon, .pase-corazon")) {
        if (r.right > vw + 1 || r.left < -1) problemas.push(`fuera de pantalla: ${el.className || el.tagName}`);
      }
    });
    return [...new Set(problemas)].slice(0, 15);
  });
}

async function entrar(context, email) {
  const p = await context.newPage();
  await p.goto(BASE + "/login");
  await p.fill('input[name="email"]', email);
  await p.fill('input[name="password"]', PW);
  await p.click("button:has-text('Entrar')");
  await p.locator(".sidebar").waitFor({ timeout: 20_000 });
  return p;
}

fs.mkdirSync(OUT, { recursive: true });
const browser = await chromium.launch();
const resumen = [];
for (const [w, h] of TAMANOS) {
  const tam = `${w}x${h}`;
  const ctx = await browser.newContext({ viewport: { width: w, height: h }, locale: "es-MX", timezoneId: "America/Mexico_City" });
  const capturar = async (page, nombre, completa = false) => {
    await page.waitForTimeout(1800);
    const archivo = path.join(OUT, `${tam}-${nombre}.png`);
    await page.screenshot({ path: archivo, fullPage: completa });
    const problemas = await medir(page);
    resumen.push({ tam, nombre, problemas });
    console.log(`${tam} ${nombre}: ${problemas.length ? problemas.join(" | ") : "sin problemas medidos"}`);
  };

  // Login
  const login = await ctx.newPage();
  await login.goto(BASE + "/login");
  await capturar(login, "1-login");
  await login.close();

  // Inicio del admin
  const admin = await entrar(ctx, "admin@clinicasantalucia.mx");
  await admin.waitForTimeout(4500); // deja que desaparezca el aviso "Sesión iniciada"
  await capturar(admin, "2-inicio-admin", true);
  await admin.close();
  await ctx.clearCookies();
  await ctx.addInitScript(() => localStorage.removeItem("medinflow.session"));

  // Actividad y agenda de la médica
  const ctx2 = await browser.newContext({ viewport: { width: w, height: h }, locale: "es-MX", timezoneId: "America/Mexico_City" });
  const med = await entrar(ctx2, "mariana.lopez@clinicasantalucia.mx");
  await med.goto(BASE + "/modulo/notificaciones");
  await med.locator(".act-tira").waitFor({ timeout: 20_000 }).catch(() => {});
  await capturar(med, "3-actividad");
  const manana = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Mexico_City" }).format(new Date(Date.now() + 86400000));
  await med.goto(`${BASE}/medico/agenda/${manana}`);
  await med.locator(".agenda-grid").waitFor({ timeout: 20_000 }).catch(() => {});
  await capturar(med, "4-agenda-medico", true);
  await ctx2.close();

  // /c/{token} con datos simulados
  const ctx3 = await browser.newContext({ viewport: { width: w, height: h }, locale: "es-MX" });
  await ctx3.route(/\/api\/public\/citas\/.*/, (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(CITA_SIMULADA) }));
  const pase = await ctx3.newPage();
  await pase.goto(BASE + "/c/AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde");
  await pase.locator(".pase-boleto").waitFor({ timeout: 10_000 }).catch(() => {});
  await capturar(pase, "5-pase-cita");
  await ctx3.close();
  await ctx.close();
}
// /c/{token} en celular
{
  const [w, h] = TAMANO_CELULAR;
  const ctx = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, locale: "es-MX" });
  await ctx.route(/\/api\/public\/citas\/.*/, (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(CITA_SIMULADA) }));
  const pase = await ctx.newPage();
  await pase.goto(BASE + "/c/AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde");
  await pase.locator(".pase-boleto").waitFor({ timeout: 10_000 }).catch(() => {});
  await pase.waitForTimeout(1500);
  await pase.screenshot({ path: path.join(OUT, `${w}x${h}-5-pase-cita.png`), fullPage: true });
  const problemas = await medir(pase);
  resumen.push({ tam: `${w}x${h}`, nombre: "5-pase-cita", problemas });
  console.log(`${w}x${h} 5-pase-cita: ${problemas.length ? problemas.join(" | ") : "sin problemas medidos"}`);
  await ctx.close();
}
await browser.close();
const conProblemas = resumen.filter((r) => r.problemas.length);
console.log(`\nCapturas en: ${OUT}`);
console.log(conProblemas.length ? `${conProblemas.length} capturas con problemas medidos.` : "Ninguna captura con problemas medidos.");
