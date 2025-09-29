-- =============================================================================================
-- SCRIPT DE CORRECCIÓN (SOLO UPDATES) - VERSIÓN SEGURA ANTI-BLOQUEO
-- =============================================================================================
--
-- PROPÓSITO:
-- Este script corrige los montos de las facturas de interés y actualiza los estados de
-- los documentos relacionados. NO REALIZA ELIMINACIONES para evitar bloqueos en la BD.
--
-- VERSIÓN: 8.0 (2025-09-27) - Updates-Only
-- =============================================================================================

DO $$
DECLARE
    v_rows_affected INTEGER;
BEGIN
    RAISE NOTICE '==============================================================================================';
    RAISE NOTICE 'INICIANDO SCRIPT: Corrección de Datos (SOLO UPDATES).';
    RAISE NOTICE '==============================================================================================';

    -- PASO 1: Identificar lote a procesar y pre-calcular montos.
    CREATE TEMP TABLE schedules_to_process AS
        SELECT
            ls.legacy_schedule_id,
            ls.ref_invoice_id,
            round(lc.valorinteres / lc.dias_cre, 2) as nuevo_monto
        FROM
            legacy_schedule ls
        JOIN
            legacy_cartera lc ON ls.legacy_cartera_id = lc.legacy_cartera_id
        WHERE
            ls.ref_invoice_id IS NOT NULL
            AND (ls.isvalid IS NULL OR ls.isvalid <> 'Y')
            AND lc.dias_cre IS NOT NULL AND lc.dias_cre > 0
        LIMIT 1000;

    GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
    RAISE NOTICE '[PASO 1/3] % registros seleccionados para actualizar en este lote.', v_rows_affected;

    IF v_rows_affected = 0 THEN
        RAISE NOTICE 'No hay más registros que actualizar. Saliendo.';
        DROP TABLE schedules_to_process;
        RETURN;
    END IF;

    -- PASO 2: Identificar los pagos asociados para actualizar su estado.
    CREATE TEMP TABLE payments_to_update AS
        SELECT DISTINCT al.c_payment_id
        FROM c_allocationline al
        WHERE al.c_invoice_id IN (SELECT ref_invoice_id FROM schedules_to_process);

    -- PASO 3: Ejecutar todas las actualizaciones.
    RAISE NOTICE '[PASO 2/3] Ejecutando actualizaciones de montos y estados...';

    UPDATE c_payment SET isallocated = 'N' WHERE c_payment_id IN (SELECT c_payment_id FROM payments_to_update);
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT; RAISE NOTICE ' -> % pagos actualizados a IsAllocated = ''N''.', v_rows_affected;

    UPDATE c_invoice ci SET totallines = stp.nuevo_monto, grandtotal = stp.nuevo_monto, ispaid = 'N' FROM schedules_to_process stp WHERE ci.c_invoice_id = stp.ref_invoice_id;
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT; RAISE NOTICE ' -> % facturas actualizadas con nuevos montos y estado IsPaid=''N''.', v_rows_affected;

    UPDATE c_invoiceline cil SET priceactual = stp.nuevo_monto, linenetamt = stp.nuevo_monto, priceentered = stp.nuevo_monto FROM schedules_to_process stp WHERE cil.c_invoice_id = stp.ref_invoice_id;
    UPDATE legacy_schedule ls SET dueamt = stp.nuevo_monto, isvalid = 'Y' FROM schedules_to_process stp WHERE ls.legacy_schedule_id = stp.legacy_schedule_id;
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT; RAISE NOTICE ' -> % schedules actualizados con nuevo monto y marcados como IsValid=''Y''.', v_rows_affected;

    -- Limpieza de tablas temporales
    DROP TABLE schedules_to_process;
    DROP TABLE payments_to_update;

    RAISE NOTICE '[PASO 3/3] Todas las actualizaciones se completaron.';
    RAISE NOTICE '==============================================================================================';
    RAISE NOTICE 'LOTE DE ACTUALIZACIONES FINALIZADO.';
    RAISE NOTICE '==============================================================================================';

END;
$$;
