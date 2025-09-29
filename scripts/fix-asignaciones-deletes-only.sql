-- =============================================================================================
-- SCRIPT DE LIMPIEZA DE ASIGNACIONES (SOLO DELETES) - Versión 2.0
-- =============================================================================================
--
-- PROPÓSITO:
-- Este script elimina en lotes todas las asignaciones vinculadas a facturas de interés
-- (aquellas cuyo ID se encuentra en `legacy_schedule.ref_invoice_id`).
--
-- NOTA:
-- Este script puede ejecutarse repetidamente. Cada ejecución procesará el siguiente lote
-- disponible hasta que no queden asignaciones por eliminar.
-- Aún podría causar bloqueos si se ejecuta bajo alta carga transaccional.
--
-- VERSIÓN: 2.0 (2025-09-27) - Lógica Simplificada
-- =============================================================================================

DO $$
DECLARE
    v_rows_affected INTEGER;
BEGIN
    RAISE NOTICE '==============================================================================================';
    RAISE NOTICE 'INICIANDO SCRIPT: Limpieza de Asignaciones de Facturas de Interés.';
    RAISE NOTICE '==============================================================================================';

    -- PASO 1: Identificar un lote de hasta 1000 cabeceras de asignación vinculadas a facturas de interés.
    CREATE TEMP TABLE alloc_hdrs_to_delete AS
        SELECT DISTINCT al.c_allocationhdr_id
        FROM c_allocationline al
        WHERE EXISTS (
            SELECT 1 FROM legacy_schedule ls WHERE ls.ref_invoice_id = al.c_invoice_id
        )
        LIMIT 1000;

    GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
    RAISE NOTICE '[PASO 1/4] % cabeceras de asignación seleccionadas para eliminar en este lote.', v_rows_affected;

    IF v_rows_affected = 0 THEN
        RAISE NOTICE 'No hay más asignaciones que eliminar. Saliendo.';
        DROP TABLE alloc_hdrs_to_delete;
        RETURN;
    END IF;

    -- PASO 2: Identificar los pagos que serán afectados por la eliminación.
    CREATE TEMP TABLE payments_to_update AS
        SELECT DISTINCT al.c_payment_id
        FROM c_allocationline al
        WHERE al.c_allocationhdr_id IN (SELECT c_allocationhdr_id FROM alloc_hdrs_to_delete);
    RAISE NOTICE '[PASO 2/4] Identificando pagos para actualizar...';

    -- PASO 3: Ejecutar las eliminaciones en el orden correcto.
    RAISE NOTICE '[PASO 3/4] Ejecutando eliminaciones...';

    DELETE FROM fact_acct WHERE ad_table_id = 735 AND record_id IN (SELECT c_allocationhdr_id FROM alloc_hdrs_to_delete);
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT; RAISE NOTICE ' -> % registros eliminados de Fact_Acct.', v_rows_affected;

    DELETE FROM c_allocationline WHERE c_allocationhdr_id IN (SELECT c_allocationhdr_id FROM alloc_hdrs_to_delete);
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT; RAISE NOTICE ' -> % líneas eliminadas de C_AllocationLine.', v_rows_affected;

    DELETE FROM c_allocationhdr WHERE c_allocationhdr_id IN (SELECT c_allocationhdr_id FROM alloc_hdrs_to_delete);
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT; RAISE NOTICE ' -> % cabeceras eliminadas de C_AllocationHdr.', v_rows_affected;

    -- PASO 4: Actualizar el estado de los pagos afectados.
    RAISE NOTICE '[PASO 4/4] Actualizando estado de pagos...';
    UPDATE c_payment SET isallocated = 'N' WHERE c_payment_id IN (SELECT c_payment_id FROM payments_to_update);
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT; RAISE NOTICE ' -> % pagos actualizados a IsAllocated = ''N''.', v_rows_affected;

    -- Limpieza de tablas temporales
    DROP TABLE alloc_hdrs_to_delete;
    DROP TABLE payments_to_update;

    RAISE NOTICE '==============================================================================================';
    RAISE NOTICE 'LOTE DE ELIMINACIÓN FINALIZADO.';
    RAISE NOTICE '==============================================================================================';

END;
$$;

