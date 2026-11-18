import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

export default defineConfig({
  root: fileURLToPath(new URL(".", import.meta.url)),
  plugins: [react()],
  appType: "spa",
  // Solo en desarrollo: permite usar el mismo origen (localhost:5173) como URL de la API.
  // No afecta el uso normal con VITE_API_URL=http://localhost:8080.
  server: {
    // Túnel de cloudflared (docker compose --profile tunnel): su dominio cambia en cada arranque
    allowedHosts: [".trycloudflare.com"],
    proxy: {
      "/api": { target: process.env.VITE_PROXY_TARGET || "http://localhost:8080", changeOrigin: true },
    },
  },
  preview: {
    allowedHosts: ["all"],
  },
});