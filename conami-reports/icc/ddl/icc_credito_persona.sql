DROP TABLE IF EXISTS icc_credito_persona;

CREATE TABLE icc_credito_persona(
  icc_credito_persona_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  c_period_id numeric(10, 0),
  id_credito character varying,
  id_persona character varying,
  id_tipo_documento int,
  id_moneda int
);

