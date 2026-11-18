-- Bitácora de mensajes de WhatsApp (salientes y entrantes), común a todos los proveedores
-- (simulado, twilio, meta, intoglobe). Alimenta el panel de conversaciones y la resolución
-- de respuestas del paciente.
CREATE TABLE IF NOT EXISTS whatsapp_mensaje (
    id               UUID         DEFAULT gen_random_uuid() NOT NULL,
    organizacion_id  UUID         NOT NULL,
    cita_id          UUID,
    telefono         VARCHAR(20)  NOT NULL,           -- 10 dígitos MX
    direccion        VARCHAR(10)  NOT NULL,
    texto            TEXT         NOT NULL,
    wamid            VARCHAR(128),                    -- id del proveedor (wamid de Meta, SID SM... de Twilio)
    estado           VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    CONSTRAINT whatsapp_mensaje_pkey PRIMARY KEY (id),
    CONSTRAINT whatsapp_mensaje_organizacion_id_fkey
        FOREIGN KEY (organizacion_id) REFERENCES organizacion (id),
    CONSTRAINT whatsapp_mensaje_cita_id_fkey
        FOREIGN KEY (cita_id) REFERENCES cita (id) ON DELETE SET NULL,
    CONSTRAINT whatsapp_mensaje_direccion_check
        CHECK (direccion IN ('SALIENTE', 'ENTRANTE')),
    CONSTRAINT whatsapp_mensaje_estado_check
        CHECK (estado IN ('ENVIADO', 'FALLIDO', 'RECIBIDO', 'ENTREGADO', 'LEIDO'))
);

-- Idempotencia: los proveedores reintentan webhooks con el mismo id de mensaje
CREATE UNIQUE INDEX IF NOT EXISTS ux_whatsapp_mensaje_wamid
    ON whatsapp_mensaje (wamid) WHERE wamid IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_whatsapp_mensaje_org_tel
    ON whatsapp_mensaje (organizacion_id, telefono, created_at);
CREATE INDEX IF NOT EXISTS ix_whatsapp_mensaje_cita
    ON whatsapp_mensaje (cita_id);

-- Campana de notificaciones: fecha de creación y marca de leída
ALTER TABLE notificacion ADD COLUMN IF NOT EXISTS creada_en TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL;
ALTER TABLE notificacion ADD COLUMN IF NOT EXISTS leida BOOLEAN DEFAULT false NOT NULL;

-- Al borrar una cita se borran sus notificaciones (antes el FK impedía borrar citas con
-- recordatorios enviados y DELETE /api/citas/{id} terminaba en 500)
ALTER TABLE notificacion DROP CONSTRAINT IF EXISTS notificacion_cita_id_fkey;
ALTER TABLE notificacion ADD CONSTRAINT notificacion_cita_id_fkey
    FOREIGN KEY (cita_id) REFERENCES cita (id) ON DELETE CASCADE;

-- Recordatorio enviado a mano desde el panel
ALTER TABLE notificacion DROP CONSTRAINT IF EXISTS notificacion_tipo_check;
ALTER TABLE notificacion ADD CONSTRAINT notificacion_tipo_check
    CHECK (tipo IN ('RECORDATORIO_48H', 'RECORDATORIO_24H', 'RECORDATORIO_MANUAL', 'ADJUNTO', 'CANCELACION_PACIENTE'));
