-- drop table if exists icc_recuperaciones;
CREATE TABLE icc_recuperaciones(
  icc_recuperaciones_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  c_period_id numeric(10, 0),
  cantidad_pagos int,
  mto_principal_cor numeric,
  mto_interes_cor numeric,
  mto_principal_dol numeric,
  mto_interes_dol numeric,
  id_forma_pago int,
  id_lugar_pago int,
  id_tipo_cartera int
);

