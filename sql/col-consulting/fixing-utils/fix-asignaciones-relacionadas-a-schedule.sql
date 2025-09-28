-- =============================================================================================
-- SCRIPT DE CORRECCIÓN Y SANITIZACIÓN DE FACTURAS DE INTERÉS Y SUS ASIGNACIONES
-- =============================================================================================
--
-- PROPÓSITO:
-- Este script realiza un proceso completo de corrección para las facturas de interés generadas
-- a partir de `legacy_schedule`.
--
-- RIESGOS:
-- - MUY ALTO: Realiza operaciones masivas de UPDATE y DELETE en tablas transaccionales y
--   contables. Es imprescindible realizar un backup completo antes de la ejecución.
-- - Ejecutar en un entorno de pruebas exhaustivamente antes de pasar a producción.
--
-- LÓGICA DE NEGOCIO:
-- 1. Itera sobre cada `legacy_schedule` que tiene una factura de interés asociada (`ref_invoice_id`).
-- 2. Calcula el monto correcto de la cuota de interés (`legacy_cartera.valorinteres / legacy_cartera.dias_cre`).
-- 3. Actualiza los montos en `legacy_schedule`, `c_invoiceline` y `c_invoice`.
-- 4. Busca y elimina cualquier asignación (`c_allocation*`) y su contabilidad (`fact_acct`) asociada a la factura corregida.
-- 5. Actualiza el estado de la factura (`ispaid = 'N'`) y de los pagos involucrados (`isallocated = 'N'`).
--
-- VERSIÓN: 3.0 (2025-09-27)
-- =============================================================================================

DO $$
DECLARE
    -- Cursor para iterar sobre los schedules que tienen una factura de interés para corregir.
    cur_schedules_to_fix CURSOR FOR
        SELECT
            ls.legacy_schedule_id,
            ls.ref_invoice_id, -- El C_Invoice_ID de la factura de interés
            lc.valorinteres,
            lc.dias_cre
        FROM
            legacy_schedule ls
        JOIN
            legacy_cartera lc ON ls.legacy_cartera_id = lc.legacy_cartera_id
        WHERE
            ls.ref_invoice_id IS NOT NULL;

    -- Variables para el bucle
    v_schedule_id   INTEGER;
    v_invoice_id    INTEGER;
    v_valorinteres  NUMERIC;
    v_dias_cre      NUMERIC;
    v_nuevo_monto   NUMERIC;

    -- Arrays para almacenar IDs de registros afectados en cada iteración
    v_alloc_hdr_ids INTEGER[];
    v_payment_ids   INTEGER[];

    -- Contadores para logs
    v_rows_affected INTEGER;
    v_total_schedules_processed INTEGER := 0;

BEGIN
    RAISE NOTICE '==============================================================================================';
    RAISE NOTICE 'INICIANDO SCRIPT: Corrección y Saneamiento de Facturas de Interés.';
    RAISE NOTICE '==============================================================================================';

    OPEN cur_schedules_to_fix;

    LOOP
        FETCH cur_schedules_to_fix INTO v_schedule_id, v_invoice_id, v_valorinteres, v_dias_cre;
        EXIT WHEN NOT FOUND;

        v_total_schedules_processed := v_total_schedules_processed + 1;
        RAISE NOTICE '----------------------------------------------------------------------------------------------';
        RAISE NOTICE 'Procesando Schedule ID: %, Factura ID: %', v_schedule_id, v_invoice_id;

        -- PASO 1: Calcular el nuevo monto.
        IF v_dias_cre IS NULL OR v_dias_cre = 0 THEN
            RAISE WARNING ' -> OMITIDO: legacy_cartera.dias_cre es 0 o NULO. No se puede calcular el nuevo monto.';
            CONTINUE;
        END IF;
        
        v_nuevo_monto := round(v_valorinteres / v_dias_cre, 2);
        RAISE NOTICE ' -> Nuevo monto calculado: %', v_nuevo_monto;

        -- PASO 2: Actualizar montos en tablas relacionadas.
        RAISE NOTICE ' -> Actualizando montos...';
        
        UPDATE legacy_schedule SET dueamt = v_nuevo_monto WHERE legacy_schedule_id = v_schedule_id;
        UPDATE c_invoiceline SET priceactual = v_nuevo_monto, linenetamt = v_nuevo_monto, priceentered = v_nuevo_monto WHERE c_invoice_id = v_invoice_id;
        UPDATE c_invoice SET totallines = v_nuevo_monto, grandtotal = v_nuevo_monto, ispaid = 'N' WHERE c_invoice_id = v_invoice_id;
        RAISE NOTICE '    Tablas legacy_schedule, c_invoiceline y c_invoice actualizadas.';

        -- PASO 3: Identificar y eliminar asignaciones existentes para esta factura.
        v_alloc_hdr_ids := NULL;
        v_payment_ids := NULL;

        SELECT array_agg(DISTINCT c_allocationhdr_id), array_agg(DISTINCT c_payment_id)
        INTO v_alloc_hdr_ids, v_payment_ids
        FROM c_allocationline
        WHERE c_invoice_id = v_invoice_id;

        IF v_alloc_hdr_ids IS NULL OR array_length(v_alloc_hdr_ids, 1) IS NULL THEN
            RAISE NOTICE ' -> No se encontraron asignaciones para esta factura. No se requiere limpieza.';
        ELSE
            RAISE NOTICE ' -> Encontradas % asignaciones para eliminar.', array_length(v_alloc_hdr_ids, 1);

            -- Eliminar de Fact_Acct
            DELETE FROM fact_acct WHERE ad_table_id = 735 AND record_id = ANY(v_alloc_hdr_ids);
            GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
            RAISE NOTICE '    % registros eliminados de Fact_Acct.', v_rows_affected;

            -- Eliminar de C_AllocationLine
            DELETE FROM c_allocationline WHERE c_allocationhdr_id = ANY(v_alloc_hdr_ids);
            GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
            RAISE NOTICE '    % líneas eliminadas de C_AllocationLine.', v_rows_affected;

            -- Eliminar de C_AllocationHdr
            DELETE FROM c_allocationhdr WHERE c_allocationhdr_id = ANY(v_alloc_hdr_ids);
            GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
            RAISE NOTICE '    % cabeceras eliminadas de C_AllocationHdr.', v_rows_affected;

            -- Actualizar C_Payment
            IF v_payment_ids IS NOT NULL AND array_length(v_payment_ids, 1) > 0 THEN
                UPDATE c_payment SET isallocated = 'N' WHERE c_payment_id = ANY(v_payment_ids);
                GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
                RAISE NOTICE '    % pagos actualizados a IsAllocated = ''N''', v_rows_affected;
            END IF;
        END IF;

    END LOOP;

    CLOSE cur_schedules_to_fix;

    RAISE NOTICE '==============================================================================================';
    RAISE NOTICE 'SCRIPT FINALIZADO. Total de schedules procesados: %', v_total_schedules_processed;
    RAISE NOTICE '==============================================================================================';

END;
$$;