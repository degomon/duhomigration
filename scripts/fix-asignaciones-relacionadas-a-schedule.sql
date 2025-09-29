-- =============================================================================================
-- SCRIPT DE CORRECCIÓN Y SANITIZACIÓN (VERSIÓN SET-BASED DE ALTO RENDIMIENTO)
-- =============================================================================================
--
-- PROPÓSITO:
-- Versión optimizada que utiliza operaciones de conjunto (set-based) en lugar de bucles
-- para un rendimiento masivamente superior.
--
-- LÓGICA DE NEGOCIO (POR LOTE DE 1000):
-- 1. Selecciona 1000 registros a procesar y los carga en una tabla temporal.
-- 2. Realiza UPDATEs y DELETEs masivos usando la información de la tabla temporal.
--
-- VERSIÓN: 7.0 (2025-09-27) - Anti-Lock Reorder
-- =============================================================================================

DO $$
DECLARE
    v_rows_affected INTEGER;
BEGIN
    RAISE NOTICE '==============================================================================================';
    RAISE NOTICE 'INICIANDO SCRIPT: Corrección y Saneamiento de Facturas de Interés (Set-Based).';
    RAISE NOTICE '==============================================================================================';

    -- PASO 1: Identificar lote a procesar y pre-calcular montos en una tabla temporal.
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
    RAISE NOTICE '[PASO 1/6] % registros seleccionados para procesar en este lote.', v_rows_affected;

    IF v_rows_affected = 0 THEN
        RAISE NOTICE 'No hay más registros que procesar. Saliendo.';
        DROP TABLE schedules_to_process;
        RETURN;
    END IF;

    -- PASO 2: Identificar los documentos de asignación y pago asociados al lote.
    CREATE TEMP TABLE allocs_to_clean AS
        SELECT DISTINCT al.c_allocationhdr_id, al.c_payment_id
        FROM c_allocationline al
        WHERE al.c_invoice_id IN (SELECT ref_invoice_id FROM schedules_to_process);
    RAISE NOTICE '[PASO 2/6] Documentos de asignación y pago identificados.';

    -- PASO 3: Actualizar estados de Pagos y Facturas (PREVENCIÓN DE BLOQUEO).
    RAISE NOTICE '[PASO 3/6] Actualizando estados de Pagos y Facturas...';
    
    UPDATE c_payment SET isallocated = 'N' WHERE c_payment_id IN (SELECT c_payment_id FROM allocs_to_clean);
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT; RAISE NOTICE ' -> % pagos actualizados a IsAllocated = ''N''.', v_rows_affected;

    UPDATE c_invoice ci SET ispaid = 'N' FROM schedules_to_process stp WHERE ci.c_invoice_id = stp.ref_invoice_id;
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT; RAISE NOTICE ' -> % facturas actualizadas a IsPaid = ''N''.', v_rows_affected;

    -- PASO 4: Actualizar montos en tablas principales.
    RAISE NOTICE '[PASO 4/6] Actualizando montos en tablas principales...';

    UPDATE legacy_schedule ls SET dueamt = stp.nuevo_monto FROM schedules_to_process stp WHERE ls.legacy_schedule_id = stp.legacy_schedule_id;
    UPDATE c_invoiceline cil SET priceactual = stp.nuevo_monto, linenetamt = stp.nuevo_monto, priceentered = stp.nuevo_monto FROM schedules_to_process stp WHERE cil.c_invoice_id = stp.ref_invoice_id;
    UPDATE c_invoice ci SET totallines = stp.nuevo_monto, grandtotal = stp.nuevo_monto FROM schedules_to_process stp WHERE ci.c_invoice_id = stp.ref_invoice_id;
    RAISE NOTICE ' -> Montos actualizados.';

    -- PASO 5: Eliminar asignaciones y su contabilidad.
    RAISE NOTICE '[PASO 5/6] Eliminando asignaciones y contabilidad asociadas...';

    DELETE FROM fact_acct WHERE ad_table_id = 735 AND record_id IN (SELECT c_allocationhdr_id FROM allocs_to_clean);
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT; RAISE NOTICE ' -> % registros eliminados de Fact_Acct.', v_rows_affected;

    DELETE FROM c_allocationline WHERE c_allocationhdr_id IN (SELECT c_allocationhdr_id FROM allocs_to_clean);
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT; RAISE NOTICE ' -> % líneas eliminadas de C_AllocationLine.', v_rows_affected;

    DELETE FROM c_allocationhdr WHERE c_allocationhdr_id IN (SELECT c_allocationhdr_id FROM allocs_to_clean);
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT; RAISE NOTICE ' -> % cabeceras eliminadas de C_AllocationHdr.', v_rows_affected;

    -- PASO 6: Marcar el lote de schedules como validado.
    RAISE NOTICE '[PASO 6/6] Marcando lote como validado...';
    UPDATE legacy_schedule SET isvalid = 'Y' WHERE legacy_schedule_id IN (SELECT legacy_schedule_id FROM schedules_to_process);
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT; RAISE NOTICE ' -> % schedules marcados como IsValid = ''Y''.', v_rows_affected;

    -- Limpieza de tablas temporales
    DROP TABLE schedules_to_process;
    DROP TABLE allocs_to_clean;

    RAISE NOTICE '==============================================================================================';
    RAISE NOTICE 'LOTE FINALIZADO.';
    RAISE NOTICE '==============================================================================================';

END;
$$;
