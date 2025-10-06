-- 20230609 - en dsResumen se cambia valor de días para alerta vencimiento a 15
WITH rawsaldos AS (
  SELECT
    car.legacy_cartera_id AS recid,
    car.fecha::date AS fecha,
    car.dias_cre AS plazo,
    add_business_day(car.fecha::date, car.dias_cre) AS fechavence,
    CASE WHEN ($ P {fechafinal}::date - add_business_day(car.fecha::date, car.dias_cre)) <= 0 THEN
      0
    ELSE
      ($ P {fechafinal}::date - add_business_day(car.fecha::date, car.dias_cre))
    END AS diasvencimiento,
    bp.c_bpartner_id,
    bp.value AS codigo,
    bp.name AS tercero,
    coalesce((
      SELECT
        documentno
      FROM c_invoice inv
      WHERE
        inv.c_invoice_id = car.local_id), car.id_cartera::character varying) AS documentno,
    car.montototal AS debito,
    coalesce(sum(cob.abono), 0.00)::numeric AS credito,
    car.montototal - coalesce(sum(cob.abono), 0.00)::numeric AS saldo,
    coalesce((
      SELECT
        max(cobq.operacion::date)
      FROM legacy_cobro cobq
      WHERE
        cobq.id_cartera = car.legacy_cartera_id
        AND cobq.abono > 0
        AND cobq.operacion::date <= $ P {fechafinal}::date), car.fecha::date) AS fechaultimoabono,
    ru.name AS ruta
  FROM
    legacy_cartera car
    INNER JOIN c_bpartner bp ON car.c_bpartner_id = bp.c_bpartner_id
    INNER JOIN cv_ruta ru ON bp.cv_ruta_id = ru.cv_ruta_id
    LEFT JOIN legacy_cobro cob ON car.legacy_cartera_id = cob.id_cartera
  WHERE
    bp.ad_org_trx_id = $ P {idsucursal}
    AND car.fecha::date <= $ P {fechafinal}::date
  GROUP BY
    car.legacy_cartera_id,
    bp.c_bpartner_id,
    bp.value,
    bp.name,
    ru.name)
SELECT
  rs.*,
  $ P {fechafinal}::date - rs.fechaultimoabono AS sinabonar,
(
    SELECT
      name
    FROM
      ad_org
    WHERE
      ad_org_id = $ P {idsucursal}) AS nombresucursal
    FROM
      rawsaldos rs
    WHERE
      rs.saldo <> 0
