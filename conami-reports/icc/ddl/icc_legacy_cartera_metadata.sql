drop table if exists icc_legacy_cartera_metadata;
-- create table named icc_legacy_cartera_metadata scaffold postgres ddl
create TABLE IF NOT EXISTS icc_legacy_cartera_metadata (
    icc_legacy_cartera_metadata_id character VARYING(36) PRIMARY KEY,
    legacy_cartera_id NUMERIC NOT NULL,
    fecha_cancelacion TIMESTAMP WITHOUT TIME ZONE,
    fecha_vencimiento TIMESTAMP WITHOUT TIME ZONE,
    fecha_ultimo_pago TIMESTAMP WITHOUT TIME ZONE,
    total_abonos NUMERIC DEFAULT 0,
    saldo_calculado NUMERIC DEFAULT 0
);

-- select * from icc_legacy_cartera_metadata;