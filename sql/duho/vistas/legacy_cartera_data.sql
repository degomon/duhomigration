CREATE OR REPLACE VIEW adempiere.legacy_cartera_data AS
WITH cartera_prev AS (
  SELECT
    legacy_cartera_id,
    c_bpartner_id,
    fecha,
    dias_cre,
    LAG(fecha::timestamp WITH TIME ZONE + dias_cre::numeric, 1) OVER (PARTITION BY c_bpartner_id ORDER BY fecha) AS fecha_vence_anterior
  FROM
    legacy_cartera
),
abonos_agregados AS (
  SELECT
    id_cartera,
    COALESCE(SUM(abono), 0.00) AS total_abonos,
    MAX(operacion) AS fecha_ultimo_pago
  FROM
    legacy_cobro
  GROUP BY
    id_cartera
)
SELECT
  car.legacy_cartera_id AS recordid,
  car.legacy_cartera_id,
  car.ad_org_id,
  car.masterkey,
  car.dias_cre,
  bp.c_bpartner_id,
  bp.value AS codigo,
  car.fecha,
  car.fecha::date AS fechaflat,
  bp.name AS nombrecliente,
  car.monto,
  car.tasa,
  car.dias_cre AS plazodias,
  ROUND(car.monto * car.tasa, 2) AS interes,
  car.montototal,
  u.name AS usuario,
  bpg.name AS grupotercero,
  car.synced AS sincronizado,
  ru.cv_ruta_id,
  ru.name AS nombreruta,
  org.name AS nombresucursal,
  car.coords,
  SPLIT_PART(car.coords::text, '|'::text, 1) AS latitud,
  SPLIT_PART(car.coords::text, '|'::text, 2) AS longitud,
(car.fecha::timestamp WITH TIME ZONE + car.dias_cre::numeric)::date AS fechavence,
  COALESCE(DATE_PART('day', car.fecha::timestamp WITH TIME ZONE - cp.fecha_vence_anterior), -99999)::int AS diffdias,
  CASE WHEN cp.fecha_vence_anterior IS NULL THEN
    'NUEVO'::text
  WHEN DATE_PART('day', car.fecha::timestamp WITH TIME ZONE - cp.fecha_vence_anterior) <= 30 THEN
    'RENOVACIÓN'::text
  ELSE
    'REACTIVACIÖN'::text
  END AS tipodesembolso,
  custom_fecha_vence(car.fecha, car.dias_cre) AS fecha_vencimiento,
  COALESCE(aa.total_abonos, 0.00) AS total_abonos,
  aa.fecha_ultimo_pago,
  car.montototal - COALESCE(aa.total_abonos, 0.00) AS saldo_calculado
FROM
  legacy_cartera car
  JOIN c_bpartner bp ON car.c_bpartner_id = bp.c_bpartner_id
  JOIN ad_user u ON car.createdby = u.ad_user_id
  JOIN c_bp_group bpg ON bp.c_bp_group_id = bpg.c_bp_group_id
  JOIN cv_ruta ru ON bp.cv_ruta_id = ru.cv_ruta_id
  JOIN ad_org org ON bp.ad_org_trx_id = org.ad_org_id
  LEFT JOIN abonos_agregados aa ON aa.id_cartera = car.legacy_cartera_id
  LEFT JOIN cartera_prev cp ON cp.legacy_cartera_id = car.legacy_cartera_id;

