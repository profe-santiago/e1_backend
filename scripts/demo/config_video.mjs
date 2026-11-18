/**
 * Configuración común de TODOS los videos de la demo (grabar_demo.mjs y los recorridos que se agreguen).
 *
 * Viewport 1280×720 con deviceScaleFactor 1.5 → el video sale a 1920×1080 nítido y la app se ve a tamaño
 * cómodo (no "alejada" como con Ctrl −). En una pantalla 1920×1080 con escalado de Windows al 150 %
 * (1280×720 lógicos), el modo kiosco hace que la ventana ocupe exactamente la pantalla.
 */
export const VIDEO = {
  viewport: { width: 1280, height: 720 },
  deviceScaleFactor: 1.5,
  recordVideo: { width: 1920, height: 1080 },
};

/** Después de cada clic el cursor se aparta 60–80 px abajo a la derecha para no tapar el texto que cambió. */
export const APARTAR_CURSOR = { min: 60, max: 80 };
