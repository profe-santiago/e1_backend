CREATE TABLE configuracion_tenant (
    id                UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    organizacion_id   UUID NOT NULL REFERENCES organizacion(id),
    notif_api_key     TEXT,
    storage_api_key   TEXT,
    payment_api_key   TEXT,
    audit_api_key     TEXT,
    whatsapp_conectado  BOOLEAN DEFAULT FALSE NOT NULL,
    whatsapp_numero   VARCHAR(20),
    UNIQUE (organizacion_id)
);

ALTER TABLE organizacion ADD COLUMN subscription_id VARCHAR(100);
