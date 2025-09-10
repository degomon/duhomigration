-- View: adempiere.legacy_cartera_data
-- DROP VIEW adempiere.legacy_cartera_data;
CREATE OR REPLACE VIEW adempiere.legacy_cartera_data AS
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
  round(car.monto * car.tasa, 2) AS interes,
  car.montototal,
  u.name AS usuario,
  bpg.name AS grupotercero,
  car.synced AS sincronizado,
  ru.cv_ruta_id,
  ru.name AS nombreruta,
  org.name AS nombresucursal,
  car.coords,
  split_part(car.coords::text, '|'::text, 1) AS latitud,
  split_part(car.coords::text, '|'::text, 2) AS longitud,
(car.fecha::timestamp with time zone + car.dias_cre::numeric)::date AS fechavence,
  COALESCE((
    SELECT
      date_part('day'::text, car.fecha::timestamp with time zone -(cartype.fecha::timestamp with time zone + cartype.dias_cre::numeric)) AS date_part FROM legacy_cartera cartype
    WHERE
      cartype.c_bpartner_id = car.c_bpartner_id
      AND cartype.fecha < car.fecha ORDER BY cartype.fecha DESC LIMIT 1), '-99999'::integer::double precision)::integer AS diffdias,
  CASE WHEN COALESCE((
    SELECT
      date_part('day'::text, car.fecha::timestamp with time zone -(cartype.fecha::timestamp with time zone + cartype.dias_cre::numeric)) AS date_part FROM legacy_cartera cartype
    WHERE
      cartype.c_bpartner_id = car.c_bpartner_id
      AND cartype.fecha < car.fecha ORDER BY cartype.fecha DESC LIMIT 1), '-99999'::integer::double precision)::integer = '-99999'::integer THEN
    'NUEVO'::text
  WHEN COALESCE((
    SELECT
      date_part('day'::text, car.fecha::timestamp with time zone -(cartype.fecha::timestamp with time zone + cartype.dias_cre::numeric)) AS date_part FROM legacy_cartera cartype
    WHERE
      cartype.c_bpartner_id = car.c_bpartner_id
      AND cartype.fecha < car.fecha ORDER BY cartype.fecha DESC LIMIT 1), '-99999'::integer::double precision)::integer <= 30 THEN
    'RENOVACIÓN'::text
  ELSE
    'REACTIVACIÖN'::text
  END AS tipodesembolso,
  custom_fecha_vence(car.fecha, car.dias_cre) AS fecha_vencimiento,
  COALESCE(sum(lcob.abono), 0.00) AS total_abonos,
  max(lcob.operacion) AS fecha_ultimo_pago,
  car.montototal - COALESCE(sum(lcob.abono), 0.00) AS saldo_calculado
FROM
  legacy_cartera car
  JOIN c_bpartner bp ON car.c_bpartner_id = bp.c_bpartner_id
  JOIN ad_user u ON car.createdby = u.ad_user_id
  JOIN c_bp_group bpg ON bp.c_bp_group_id = bpg.c_bp_group_id
  JOIN cv_ruta ru ON bp.cv_ruta_id = ru.cv_ruta_id
  JOIN ad_org org ON bp.ad_org_trx_id = org.ad_org_id
  LEFT JOIN legacy_cobro lcob ON lcob.id_cartera = car.legacy_cartera_id
GROUP BY
  car.legacy_cartera_id,
  bp.c_bpartner_id,
  bp.value,
  u.name,
  bpg.name,
  ru.cv_ruta_id,
  org.name;

ALTER TABLE adempiere.legacy_cartera_data OWNER TO adempiere;

