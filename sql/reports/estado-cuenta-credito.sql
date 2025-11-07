/**
 * Estado de Cuenta de Crédito
 * 
 * Descripción: Reporte que muestra el estado de cuenta detallado de un crédito específico
 * Parámetro: legacy_cartera_id - ID del crédito a consultar
 * 
 * Muestra:
 * - Fecha del movimiento
 * - Documento
 * - Débito (desembolsos)
 * - Crédito (pagos)
 * - Para pagos (c_payment): distribución entre facturas de interés y saldo principal
 */

WITH cartera_info AS (
    -- Información básica del crédito
    SELECT 
        car.legacy_cartera_id,
        car.c_bpartner_id,
        car.fecha,
        car.montototal,
        car.local_id as invoice_id,
        bp.value as codigo_cliente,
        bp.name as nombre_cliente
    FROM legacy_cartera car
    INNER JOIN c_bpartner bp ON car.c_bpartner_id = bp.c_bpartner_id
    WHERE car.legacy_cartera_id = $P{legacy_cartera_id}
),
desembolso AS (
    -- Registro del desembolso inicial (débito)
    SELECT 
        ci.legacy_cartera_id,
        ci.fecha::date as fecha_movimiento,
        COALESCE(
            (SELECT documentno FROM c_invoice inv WHERE inv.c_invoice_id = ci.invoice_id),
            'DESEMB-' || ci.legacy_cartera_id::varchar
        ) as documento,
        'Desembolso' as concepto,
        ci.montototal as debito,
        0.00::numeric as credito,
        0.00::numeric as asignado_interes,
        0.00::numeric as asignado_principal,
        ci.fecha as created
    FROM cartera_info ci
),
asignaciones_pago AS (
    -- Pre-calcular las asignaciones de pagos por tipo de cargo para optimizar el query
    SELECT 
        al.c_payment_id,
        invl.c_charge_id,
        SUM(al.amount) as monto_asignado
    FROM c_allocationline al
    INNER JOIN c_invoice inv ON al.c_invoice_id = inv.c_invoice_id
    INNER JOIN c_invoiceline invl ON inv.c_invoice_id = invl.c_invoice_id
    WHERE al.isactive = 'Y'
    AND invl.c_charge_id IN (1000028, 1000029)  -- 1000028: Capital/Principal, 1000029: Intereses
    GROUP BY al.c_payment_id, invl.c_charge_id
),
pagos AS (
    -- Pagos realizados al crédito (créditos)
    SELECT 
        cob.id_cartera as legacy_cartera_id,
        cob.operacion::date as fecha_movimiento,
        COALESCE(
            (SELECT documentno FROM c_payment pay WHERE pay.c_payment_id = cob.local_id),
            'COBRO-' || cob.id_cobro::varchar
        ) as documento,
        'Pago/Cobro' as concepto,
        0.00::numeric as debito,
        cob.abono as credito,
        -- Monto asignado a intereses (c_charge_id = 1000029: Cargo por Intereses)
        COALESCE(
            (SELECT monto_asignado FROM asignaciones_pago 
             WHERE c_payment_id = cob.local_id AND c_charge_id = 1000029),
            0.00
        )::numeric as asignado_interes,
        -- Monto asignado a saldo principal (c_charge_id = 1000028: Cargo por Capital/Principal)
        COALESCE(
            (SELECT monto_asignado FROM asignaciones_pago 
             WHERE c_payment_id = cob.local_id AND c_charge_id = 1000028),
            0.00
        )::numeric as asignado_principal,
        cob.created
    FROM legacy_cobro cob
    WHERE cob.id_cartera = $P{legacy_cartera_id}
    AND cob.abono > 0
),
todos_movimientos AS (
    -- Unir desembolsos y pagos
    SELECT * FROM desembolso
    UNION ALL
    SELECT * FROM pagos
)
-- Resultado final con totales acumulados
SELECT 
    tm.fecha_movimiento,
    tm.documento,
    tm.concepto,
    tm.debito,
    tm.credito,
    tm.asignado_interes,
    tm.asignado_principal,
    -- Saldo acumulado
    SUM(tm.debito - tm.credito) OVER (ORDER BY tm.fecha_movimiento, tm.created) as saldo,
    ci.codigo_cliente,
    ci.nombre_cliente
FROM todos_movimientos tm
CROSS JOIN cartera_info ci
ORDER BY tm.fecha_movimiento, tm.created;
