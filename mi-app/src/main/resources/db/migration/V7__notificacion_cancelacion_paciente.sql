-- Agregar CANCELACION_PACIENTE al check constraint de notificacion.tipo
ALTER TABLE notificacion DROP CONSTRAINT IF EXISTS notificacion_tipo_check;
ALTER TABLE notificacion ADD CONSTRAINT notificacion_tipo_check
    CHECK (tipo IN ('RECORDATORIO_48H', 'RECORDATORIO_24H', 'ADJUNTO', 'CANCELACION_PACIENTE'));
