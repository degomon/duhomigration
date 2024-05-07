CREATE TABLE icc_analista(
  icc_analista_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  c_period_id numeric(10, 0),
  id_analista character varying,
  nombre character varying,
  id_genero int
);

