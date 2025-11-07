/**
 * Estado de Cuenta de Crédito
 * 
 * Descripción: Reporte que muestra el estado de cuenta detallado de un crédito específico
 * Parámetro: legacy_cartera_id - ID del crédito a consultar
 * 
 * Muestra:
 * - Fecha del movimiento
 * - Documento
 * - Débito (desembolsos de capital y facturas de interés)
 * - Crédito (pagos)
 * - Para pagos (c_payment): distribución entre facturas de interés y saldo principal
 */
WITH tenant AS (
  SELECT
    conf.value::json ->> 'logo-url' AS logourl,
    conf.value::json ->> 'company-name' AS companyname
  FROM
    ad_pinstance pin
    INNER JOIN ad_client cli ON pin.ad_client_id = cli.ad_client_id
    INNER JOIN ad_sysconfig conf ON cli.ad_client_id = conf.ad_client_id
      AND conf.name = 'DEVM_CONF'
  WHERE
    pin.ad_pinstance_id = $ P {AD_PINSTANCE_ID}
),
cartera_info AS (
  -- Información básica del crédito
  SELECT
    car.legacy_cartera_id,
    car.c_bpartner_id,
    car.fecha,
    car.montototal,
    car.local_id AS invoice_id_capital,
    bp.value AS codigo_cliente,
    bp.name AS nombre_cliente
  FROM
    legacy_cartera car
    INNER JOIN c_bpartner bp ON car.c_bpartner_id = bp.c_bpartner_id
  WHERE
    car.legacy_cartera_id = $ P {RECORD_ID}
),
facturas_credito AS (
  -- Obtener todas las facturas (CxC) relacionadas a este crédito
  -- Factura de capital (c_doctype_id = 1000048)
  SELECT
    ci.invoice_id_capital AS c_invoice_id,
    1000048 AS c_doctype_id,
    'Capital' AS tipo_factura
  FROM
    cartera_info ci
  WHERE
    ci.invoice_id_capital IS NOT NULL
  UNION ALL
  -- Facturas de interés (c_doctype_id = 1000051) desde legacy_schedule
  SELECT
    ls.ref_invoice_id AS c_invoice_id,
    1000051 AS c_doctype_id,
    'Interés' AS tipo_factura
  FROM
    legacy_schedule ls
    INNER JOIN cartera_info ci ON ls.legacy_cartera_id = ci.legacy_cartera_id
  WHERE
    ls.ref_invoice_id IS NOT NULL
    AND ls.processed = 'Y'
),
factura_capital AS (
  -- Registro de la factura de capital (débito)
  SELECT
    ci.legacy_cartera_id,
    COALESCE(inv.dateinvoiced::date, ci.fecha::date) AS fecha_movimiento,
    COALESCE(inv.documentno, 'CAPITAL-' || ci.legacy_cartera_id::varchar) AS documento,
    'CxC Capital' AS concepto,
    COALESCE(inv.grandtotal, ci.montototal) AS debito,
    0.00::numeric AS credito,
    0.00::numeric AS asignado_interes,
    0.00::numeric AS asignado_principal,
    COALESCE(inv.created, ci.fecha) AS created,
    'capital' AS tipo_movimiento
  FROM
    cartera_info ci
    LEFT JOIN c_invoice inv ON ci.invoice_id_capital = inv.c_invoice_id
),
facturas_interes AS (
  -- Registros de facturas de interés (débitos)
  SELECT
    ls.legacy_cartera_id,
    inv.dateinvoiced::date AS fecha_movimiento,
    inv.documentno AS documento,
    'CxC Interés' AS concepto,
    inv.grandtotal AS debito,
    0.00::numeric AS credito,
    0.00::numeric AS asignado_interes,
    0.00::numeric AS asignado_principal,
    inv.created,
    'interes' AS tipo_movimiento
  FROM
    legacy_schedule ls
    INNER JOIN cartera_info ci ON ls.legacy_cartera_id = ci.legacy_cartera_id
    INNER JOIN c_invoice inv ON ls.ref_invoice_id = inv.c_invoice_id
  WHERE
    ls.ref_invoice_id IS NOT NULL
    AND ls.processed = 'Y'
    AND inv.c_doctype_id = 1000051
),
asignaciones_pago AS (
  -- Pre-calcular las asignaciones de pagos SOLO para las facturas de este crédito
  SELECT
    al.c_payment_id,
    fc.c_doctype_id,
    SUM(al.amount) AS monto_asignado
  FROM
    c_allocationline al
    INNER JOIN facturas_credito fc ON al.c_invoice_id = fc.c_invoice_id
  WHERE
    al.isactive = 'Y'
  GROUP BY
    al.c_payment_id,
    fc.c_doctype_id
),
pagos AS (
  -- Pagos realizados al crédito (créditos)
  SELECT
    cob.id_cartera AS legacy_cartera_id,
    cob.operacion::date AS fecha_movimiento,
    COALESCE((
      SELECT
        documentno
      FROM c_payment pay
      WHERE
        pay.c_payment_id = cob.local_id), 'COBRO-' || cob.id_cobro::varchar) AS documento,
    'Pago/Cobro' AS concepto,
    0.00::numeric AS debito,
    cob.abono AS credito,
    -- Monto asignado a intereses (c_doctype_id = 1000051: CxC por Interés)
    COALESCE(asig_int.monto_asignado, 0.00)::numeric AS asignado_interes,
    -- Monto asignado a saldo principal (c_doctype_id = 1000048: CxC por Capital)
    COALESCE(asig_prin.monto_asignado, 0.00)::numeric AS asignado_principal,
    cob.created,
    'pago' AS tipo_movimiento
  FROM
    legacy_cobro cob
    LEFT JOIN asignaciones_pago asig_int ON asig_int.c_payment_id = cob.local_id
      AND asig_int.c_doctype_id = 1000051
    LEFT JOIN asignaciones_pago asig_prin ON asig_prin.c_payment_id = cob.local_id
      AND asig_prin.c_doctype_id = 1000048
  WHERE
    cob.id_cartera = $ P {RECORD_ID}
    AND cob.abono > 0
),
todos_movimientos AS (
  -- Unir todos los movimientos: capital, intereses y pagos
  SELECT
    *
  FROM
    factura_capital
  UNION ALL
  SELECT
    *
  FROM
    facturas_interes
  UNION ALL
  SELECT
    *
  FROM
    pagos)
  -- Resultado final con totales acumulados
  SELECT
    tm.fecha_movimiento,
    tm.documento,
    tm.concepto,
    tm.debito,
    tm.credito,
    tm.asignado_interes,
    tm.asignado_principal,
    -- Saldo acumulado total
    SUM(tm.debito - tm.credito) OVER (ORDER BY tm.fecha_movimiento, tm.created) AS saldo,
    -- Saldo acumulado de capital
    SUM(
      CASE WHEN tm.tipo_movimiento = 'capital' THEN
        tm.debito
      WHEN tm.tipo_movimiento = 'pago' THEN
        - tm.asignado_principal
      ELSE
        0.00::numeric
      END) OVER (ORDER BY tm.fecha_movimiento, tm.created) AS saldo_capital,
    -- Saldo acumulado de interés
    SUM(
      CASE WHEN tm.tipo_movimiento = 'interes' THEN
        tm.debito
      WHEN tm.tipo_movimiento = 'pago' THEN
        - tm.asignado_interes
      ELSE
        0.00::numeric
      END) OVER (ORDER BY tm.fecha_movimiento, tm.created) AS saldo_interes,
    ci.codigo_cliente,
    ci.nombre_cliente,
    te.logourl,
    te.companyname
  FROM
    todos_movimientos tm
  CROSS JOIN cartera_info ci
  CROSS JOIN tenant te
ORDER BY
  tm.fecha_movimiento,
  tm.created;

