-- V5: Resetear TODOS los adjuntos en estado SUBIDO para re-prueba con Supabase.
--
-- Contexto:
--   La migración V4 solo reseteaba PDFs con URLs de Cloudinary image/upload.
--   Con el cambio de almacenamiento a Supabase Storage es necesario resetear
--   cualquier adjunto que tenga una URL antigua (Cloudinary o cualquier otra)
--   para que el paciente pueda volver a subir el archivo usando el nuevo flujo.
--
-- Los adjuntos en estado REVISADO no se tocan (decisión clínica preservada).

UPDATE adjunto
SET
    url_archivo          = NULL,
    nombre_archivo       = NULL,
    mime_type            = NULL,
    estado               = 'SOLICITADO',
    comentario_revision  = NULL,
    revisado_en          = NULL
WHERE
    estado = 'SUBIDO';
