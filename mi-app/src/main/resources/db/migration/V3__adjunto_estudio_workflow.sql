-- V3: Evolución del módulo Adjuntos hacia seguimiento de estudios médicos

ALTER TABLE adjunto
    ALTER COLUMN nombre_archivo DROP NOT NULL,
    ALTER COLUMN url_archivo    DROP NOT NULL,
    ADD COLUMN estado              VARCHAR(20)  NOT NULL DEFAULT 'SUBIDO',
    ADD COLUMN comentario_revision TEXT,
    ADD COLUMN revisado_en         TIMESTAMPTZ,
    ADD CONSTRAINT adjunto_estado_check
        CHECK (estado IN ('SOLICITADO', 'SUBIDO', 'REVISADO'));

-- Los registros existentes quedan en estado SUBIDO (valor por defecto aplicado arriba)
