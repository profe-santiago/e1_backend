-- Canal "enlace": token de un solo uso por recordatorio para confirmar o cancelar la cita desde
-- una página pública (/c/{token}). Solo se guarda el hash SHA-256 del token, nunca el token.
CREATE TABLE IF NOT EXISTS cita_enlace (
    id               UUID         DEFAULT gen_random_uuid() NOT NULL,
    organizacion_id  UUID         NOT NULL,
    cita_id          UUID         NOT NULL,
    token_hash       VARCHAR(64)  NOT NULL,
    expira_en        TIMESTAMP WITH TIME ZONE NOT NULL,   -- hora de la cita (convención de reloj en UTC)
    usado_en         TIMESTAMP WITH TIME ZONE,
    accion           VARCHAR(20),                         -- CONFIRMAR | CANCELAR | REEMPLAZADO
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    CONSTRAINT cita_enlace_pkey PRIMARY KEY (id),
    CONSTRAINT cita_enlace_organizacion_id_fkey
        FOREIGN KEY (organizacion_id) REFERENCES organizacion (id),
    CONSTRAINT cita_enlace_cita_id_fkey
        FOREIGN KEY (cita_id) REFERENCES cita (id) ON DELETE CASCADE,
    CONSTRAINT cita_enlace_accion_check
        CHECK (accion IS NULL OR accion IN ('CONFIRMAR', 'CANCELAR', 'REEMPLAZADO'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_cita_enlace_token_hash ON cita_enlace (token_hash);
CREATE INDEX IF NOT EXISTS ix_cita_enlace_cita ON cita_enlace (cita_id);

-- Campana: la confirmación desde el enlace también genera notificación
ALTER TABLE notificacion DROP CONSTRAINT IF EXISTS notificacion_tipo_check;
ALTER TABLE notificacion ADD CONSTRAINT notificacion_tipo_check
    CHECK (tipo IN ('RECORDATORIO_48H', 'RECORDATORIO_24H', 'RECORDATORIO_MANUAL', 'ADJUNTO',
                    'CANCELACION_PACIENTE', 'CONFIRMACION_PACIENTE'));
