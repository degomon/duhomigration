DROP TABLE IF EXISTS icc_colocaciones CASCADE;

CREATE TABLE icc_colocaciones(
  icc_colocaciones_id uuid NOT NULL DEFAULT gen_random_uuid(),
  c_period_id numeric(10, 0),
  cantidad_creditos integer,
  mto_prestamo_cor numeric,
  mto_prestamos_dol numeric,
  mto_desembolsado_coi numeric,
  mto_desembolsado_dol numeric,
  id_tipo_fondo integer,
  otro_fondo character varying,
  id_forma_entrega integer,
  id_tipo_lugar integer,
  CONSTRAINT icc_colocaciones_pkey PRIMARY KEY (icc_colocaciones_id)
);

