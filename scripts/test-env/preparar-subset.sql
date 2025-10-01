/*********************************************************************************
 * SCRIPT DE PURGA DE DATOS POR ORGANIZACIÓN (PARA ENTORNO DE PRUEBAS)
 *
 * PROPÓSITO:
 * Elimina todos los registros de las tablas transaccionales más importantes
 * que NO pertenecen a las organizaciones especificadas.
 *
 * ESTRATEGIA:
 * 1. Desactivar triggers para un rendimiento máximo.
 * 2. Borrar datos en el orden correcto para evitar errores de integridad.
 * 3. Reactivar triggers para devolver la base de datos a su estado normal.
 *
 *********************************************************************************/
DO $$
DECLARE
  -- Lista de IDs de organización que quieres CONSERVAR.
  -- Todos los datos de otras organizaciones serán eliminados.
  orgs_a_conservar integer[] := ARRAY[1000022, 1000026, 1000019];
  v_rows_affected bigint;
BEGIN
  RAISE NOTICE '======================================================================';
  RAISE NOTICE 'INICIANDO PROCESO DE PURGA DE DATOS. ¡SOLO PARA BASES DE PRUEBAS!';
  RAISE NOTICE 'Se conservarán los datos de las organizaciones: %', orgs_a_conservar;
  RAISE NOTICE '======================================================================';
  -- PASO 1: Desactivar triggers para todas las tablas involucradas
  RAISE NOTICE '[PASO 1/3] Desactivando triggers...';
  ALTER TABLE adempiere.fact_acct DISABLE TRIGGER USER;
  ALTER TABLE adempiere.c_allocationline DISABLE TRIGGER USER;
  ALTER TABLE adempiere.c_allocationhdr DISABLE TRIGGER USER;
  ALTER TABLE adempiere.c_invoiceline DISABLE TRIGGER USER;
  ALTER TABLE adempiere.c_invoice DISABLE TRIGGER USER;
  ALTER TABLE adempiere.c_orderline DISABLE TRIGGER USER;
  ALTER TABLE adempiere.c_order DISABLE TRIGGER USER;
  ALTER TABLE adempiere.m_inoutline DISABLE TRIGGER USER;
  ALTER TABLE adempiere.m_inout DISABLE TRIGGER USER;
  ALTER TABLE adempiere.c_cashline DISABLE TRIGGER USER;
  ALTER TABLE adempiere.c_cash DISABLE TRIGGER USER;
  ALTER TABLE adempiere.gl_journalline DISABLE TRIGGER USER;
  ALTER TABLE adempiere.gl_journal DISABLE TRIGGER USER;
  ALTER TABLE adempiere.c_payment DISABLE TRIGGER USER;
  ALTER TABLE adempiere.legacy_cobro DISABLE TRIGGER USER;
  ALTER TABLE adempiere.legacy_cartera DISABLE TRIGGER USER;
  ALTER TABLE adempiere.c_bpartner DISABLE TRIGGER USER;
  RAISE NOTICE ' -> Triggers desactivados.';
  -- PASO 2: Ejecutar eliminaciones en orden de dependencia (hijas -> padres)
  RAISE NOTICE '[PASO 2/3] Ejecutando eliminaciones...';
  -- Contabilidad
  DELETE FROM adempiere.fact_acct
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de fact_acct.', v_rows_affected;
  -- Asignaciones
  DELETE FROM adempiere.c_allocationline
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de c_allocationline.', v_rows_affected;
  DELETE FROM adempiere.c_allocationhdr
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de c_allocationhdr.', v_rows_affected;
  -- Facturas
  DELETE FROM adempiere.c_invoiceline
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de c_invoiceline.', v_rows_affected;
  DELETE FROM adempiere.c_invoice
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de c_invoice.', v_rows_affected;
  -- Pedidos
  DELETE FROM adempiere.c_orderline
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de c_orderline.', v_rows_affected;
  DELETE FROM adempiere.c_order
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de c_order.', v_rows_affected;
  -- Albaranes
  DELETE FROM adempiere.m_inoutline
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de m_inoutline.', v_rows_affected;
  DELETE FROM adempiere.m_inout
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de m_inout.', v_rows_affected;
  -- Caja
  DELETE FROM adempiere.c_cashline
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de c_cashline.', v_rows_affected;
  DELETE FROM adempiere.c_cash
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de c_cash.', v_rows_affected;
  -- Diario Contable
  DELETE FROM adempiere.gl_journalline
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de gl_journalline.', v_rows_affected;
  DELETE FROM adempiere.gl_journal
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de gl_journal.', v_rows_affected;
  -- Pagos y Tablas Legacy
  DELETE FROM adempiere.c_payment
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de c_payment.', v_rows_affected;
  DELETE FROM adempiere.legacy_cobro
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de legacy_cobro.', v_rows_affected;
  DELETE FROM adempiere.legacy_cartera
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de legacy_cartera.', v_rows_affected;
  -- Terceros (Clientes/Proveedores)
  DELETE FROM adempiere.c_bpartner
  WHERE ad_org_id <> ALL (orgs_a_conservar);
  GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
  RAISE NOTICE ' -> % registros eliminados de c_bpartner.', v_rows_affected;
  -- PASO 3: Reactivar triggers para restaurar la lógica de negocio
  RAISE NOTICE '[PASO 3/3] Reactivando triggers...';
  ALTER TABLE adempiere.fact_acct ENABLE TRIGGER USER;
  ALTER TABLE adempiere.c_allocationline ENABLE TRIGGER USER;
  ALTER TABLE adempiere.c_allocationhdr ENABLE TRIGGER USER;
  ALTER TABLE adempiere.c_invoiceline ENABLE TRIGGER USER;
  ALTER TABLE adempiere.c_invoice ENABLE TRIGGER USER;
  ALTER TABLE adempiere.c_orderline ENABLE TRIGGER USER;
  ALTER TABLE adempiere.c_order ENABLE TRIGGER USER;
  ALTER TABLE adempiere.m_inoutline ENABLE TRIGGER USER;
  ALTER TABLE adempiere.m_inout ENABLE TRIGGER USER;
  ALTER TABLE adpiere.c_cashline ENABLE TRIGGER USER;
  ALTER TABLE adempiere.c_cash ENABLE TRIGGER USER;
  ALTER TABLE adempiere.gl_journalline ENABLE TRIGGER USER;
  ALTER TABLE adempiere.gl_journal ENABLE TRIGGER USER;
  ALTER TABLE adempiere.c_payment ENABLE TRIGGER USER;
  ALTER TABLE adempiere.legacy_cobro ENABLE TRIGGER USER;
  ALTER TABLE adempiere.legacy_cartera ENABLE TRIGGER USER;
  ALTER TABLE adempiere.c_bpartner ENABLE TRIGGER USER;
  RAISE NOTICE ' -> Triggers reactivados.';

  RAISE NOTICE '======================================================================';
  RAISE NOTICE 'PROCESO DE PURGA FINALIZADO.';
  RAISE NOTICE '======================================================================';

EXCEPTION
  -- Bloque de seguridad: Si algo falla, asegúrate de reactivar los triggers.
  WHEN OTHERS THEN
    RAISE NOTICE '¡ERROR! Se encontró un problema: %. Reactivando triggers por seguridad...', SQLERRM;
    ALTER TABLE adempiere.fact_acct ENABLE TRIGGER USER;
    ALTER TABLE adempiere.c_allocationline ENABLE TRIGGER USER;
    ALTER TABLE adempiere.c_allocationhdr ENABLE TRIGGER USER;
    ALTER TABLE adempiere.c_invoiceline ENABLE TRIGGER USER;
    ALTER TABLE adempiere.c_invoice ENABLE TRIGGER USER;
    ALTER TABLE adempiere.c_orderline ENABLE TRIGGER USER;
    ALTER TABLE adempiere.c_order ENABLE TRIGGER USER;
    ALTER TABLE adempiere.m_inoutline ENABLE TRIGGER USER;
    ALTER TABLE adempiere.m_inout ENABLE TRIGGER USER;
    ALTER TABLE adpiere.c_cashline ENABLE TRIGGER USER;
    ALTER TABLE adempiere.c_cash ENABLE TRIGGER USER;
    ALTER TABLE adempiere.gl_journalline ENABLE TRIGGER USER;
    ALTER TABLE adempiere.gl_journal ENABLE TRIGGER USER;
    ALTER TABLE adempiere.c_payment ENABLE TRIGGER USER;
    ALTER TABLE adempiere.legacy_cobro ENABLE TRIGGER USER;
    ALTER TABLE adempiere.legacy_cartera ENABLE TRIGGER USER;
    ALTER TABLE adempiere.c_bpartner ENABLE TRIGGER USER;
    RAISE;
END;

$$;

