-- V4: Resetear adjuntos de tipo PDF que fueron subidos con resource_type incorrecto.
--
-- Contexto:
--   Los PDFs subidos antes del fix usaban resource_type="auto" en Cloudinary,
--   lo que los clasificó como "image" y generó URLs del tipo:
--     https://res.cloudinary.com/.../image/upload/...pdf
--   Esas URLs no sirven el PDF correctamente.
--   Los PDFs subidos después del fix usan resource_type="raw" y generan:
--     https://res.cloudinary.com/.../raw/upload/...pdf  ← correcto
--
-- Solución: volver al estado SOLICITADO los registros afectados.
--   El paciente recibirá el botón "Subir resultado" de nuevo y podrá
--   subir el archivo usando el flujo corregido (raw/upload).
--
-- Impacto: SOLO afecta PDFs con URL image/upload en estado SUBIDO.
--          Los ya REVISADOS no se tocan (decisión clínica preservada).

UPDATE adjunto
SET
    url_archivo        = NULL,
    nombre_archivo     = NULL,
    mime_type          = NULL,
    estado             = 'SOLICITADO',
    comentario_revision = NULL,
    revisado_en        = NULL
WHERE
    estado        = 'SUBIDO'
    AND mime_type = 'application/pdf'
    AND url_archivo LIKE '%/image/upload/%';
