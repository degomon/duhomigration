CREATE OR REPLACE FUNCTION icc_legacy_cartera_metadata(cartera_id numeric)
  RETURNS VOID
  AS $$
DECLARE
  car record;
  fecha_cancelacion_data timestamp without time zone;
  fecha_vencimiento_data timestamp without time zone;
  fecha_ultimo_pago_data timestamp without time zone;
  total_abonos_data numeric DEFAULT 0;
  saldo_calculado_data numeric DEFAULT 0;
BEGIN
  -- Combine multiple queries into one
  SELECT
    lc.*,
    COALESCE(SUM(lcob.abono), 0.00) AS total_abonos,
    MAX(lcob.operacion) AS fecha_ultimo_pago,
    lc.montototal - COALESCE(SUM(lcob.abono), 0.00) AS saldo_calculado,
    custom_fecha_vence(lc.fecha, lc.dias_cre) AS fecha_vencimiento INTO car
  FROM
    legacy_cartera lc
  LEFT JOIN legacy_cobro lcob ON lcob.id_cartera = lc.legacy_cartera_id
WHERE
  lc.legacy_cartera_id = cartera_id
GROUP BY
  lc.legacy_cartera_id;

  saldo_calculado_data := car.saldo_calculado;
  fecha_vencimiento_data := car.fecha_vencimiento;
  fecha_ultimo_pago_data := car.fecha_ultimo_pago;

  IF saldo_calculado_data <= 0 THEN
    fecha_cancelacion_data := fecha_ultimo_pago_data;
  ELSE
    fecha_cancelacion_data := NULL;
  END IF;
  -- check if record exists in icc_legacy_cartera_metadata
  IF EXISTS (
    SELECT
      1
    FROM
      icc_legacy_cartera_metadata
    WHERE
      legacy_cartera_id = cartera_id) THEN
  -- update record
  UPDATE
    icc_legacy_cartera_metadata
  SET
    fecha_cancelacion = fecha_cancelacion_data,
    fecha_vencimiento = fecha_vencimiento_data,
    fecha_ultimo_pago = fecha_ultimo_pago_data,
    total_abonos = total_abonos_data,
    saldo_calculado = saldo_calculado_data
  WHERE
    legacy_cartera_id = cartera_id;
ELSE
  -- insert record
  INSERT INTO icc_legacy_cartera_metadata(icc_legacy_cartera_metadata_id, legacy_cartera_id, fecha_cancelacion, fecha_vencimiento, fecha_ultimo_pago, total_abonos, saldo_calculado)
    VALUES (gen_random_uuid(), cartera_id, fecha_cancelacion_data, fecha_vencimiento_data, fecha_ultimo_pago_data, total_abonos_data, saldo_calculado_data);
END IF;

END
$$
LANGUAGE plpgsql;

