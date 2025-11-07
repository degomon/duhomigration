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

WITH cartera_info AS (
    -- Información básica del crédito
    SELECT 
        car.legacy_cartera_id,
        car.c_bpartner_id,
        car.fecha,
        car.montototal,
        car.local_id as invoice_id_capital,
        bp.value as codigo_cliente,
        bp.name as nombre_cliente
    FROM legacy_cartera car
    INNER JOIN c_bpartner bp ON car.c_bpartner_id = bp.c_bpartner_id
    WHERE car.legacy_cartera_id = $P{legacy_cartera_id}
),
facturas_credito AS (
    -- Obtener todas las facturas (CxC) relacionadas a este crédito
    -- Factura de capital (c_doctype_id = 1000048)
    SELECT 
        ci.invoice_id_capital as c_invoice_id,
        1000048 as c_doctype_id,
        'Capital' as tipo_factura
    FROM cartera_info ci
    WHERE ci.invoice_id_capital IS NOT NULL
    
    UNION ALL
    
    -- Facturas de interés (c_doctype_id = 1000051) desde legacy_schedule
    SELECT 
        ls.ref_invoice_id as c_invoice_id,
        1000051 as c_doctype_id,
        'Interés' as tipo_factura
    FROM legacy_schedule ls
    INNER JOIN cartera_info ci ON ls.legacy_cartera_id = ci.legacy_cartera_id
    WHERE ls.ref_invoice_id IS NOT NULL
    AND ls.processed = 'Y'
),
factura_capital AS (
    -- Registro de la factura de capital (débito)
    SELECT 
        ci.legacy_cartera_id,
        ci.fecha::date as fecha_movimiento,
        COALESCE(
            (SELECT documentno FROM c_invoice inv WHERE inv.c_invoice_id = ci.invoice_id_capital),
            'CAPITAL-' || ci.legacy_cartera_id::varchar
        ) as documento,
        'CxC Capital' as concepto,
        ci.montototal as debito,
        0.00::numeric as credito,
        0.00::numeric as asignado_interes,
        0.00::numeric as asignado_principal,
        ci.fecha as created
    FROM cartera_info ci
),
facturas_interes AS (
    -- Registros de facturas de interés (débitos)
    SELECT 
        ls.legacy_cartera_id,
        inv.dateinvoiced::date as fecha_movimiento,
        inv.documentno as documento,
        'CxC Interés' as concepto,
        inv.grandtotal as debito,
        0.00::numeric as credito,
        0.00::numeric as asignado_interes,
        0.00::numeric as asignado_principal,
        inv.created
    FROM legacy_schedule ls
    INNER JOIN cartera_info ci ON ls.legacy_cartera_id = ci.legacy_cartera_id
    INNER JOIN c_invoice inv ON ls.ref_invoice_id = inv.c_invoice_id
    WHERE ls.ref_invoice_id IS NOT NULL
    AND ls.processed = 'Y'
    AND inv.c_doctype_id = 1000051
),
asignaciones_pago AS (
    -- Pre-calcular las asignaciones de pagos SOLO para las facturas de este crédito
    SELECT 
        al.c_payment_id,
        fc.c_doctype_id,
        SUM(al.amount) as monto_asignado
    FROM c_allocationline al
    INNER JOIN facturas_credito fc ON al.c_invoice_id = fc.c_invoice_id
    WHERE al.isactive = 'Y'
    GROUP BY al.c_payment_id, fc.c_doctype_id
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
        -- Monto asignado a intereses (c_doctype_id = 1000051: CxC por Interés)
        COALESCE(asig_int.monto_asignado, 0.00)::numeric as asignado_interes,
        -- Monto asignado a saldo principal (c_doctype_id = 1000048: CxC por Capital)
        COALESCE(asig_prin.monto_asignado, 0.00)::numeric as asignado_principal,
        cob.created
    FROM legacy_cobro cob
    LEFT JOIN asignaciones_pago asig_int 
        ON asig_int.c_payment_id = cob.local_id AND asig_int.c_doctype_id = 1000051
    LEFT JOIN asignaciones_pago asig_prin 
        ON asig_prin.c_payment_id = cob.local_id AND asig_prin.c_doctype_id = 1000048
    WHERE cob.id_cartera = $P{legacy_cartera_id}
    AND cob.abono > 0
),
todos_movimientos AS (
    -- Unir todos los movimientos: capital, intereses y pagos
    SELECT * FROM factura_capital
    UNION ALL
    SELECT * FROM facturas_interes
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
