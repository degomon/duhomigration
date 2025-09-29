-- =============================================================================
-- ÍNDICES PARA OPTIMIZAR EL SCRIPT DE CORRECCIÓN DE ASIGNACIONES
-- =============================================================================
--
-- PROPÓSITO:
-- Este script crea los índices recomendados para acelerar las operaciones
-- de JOIN y WHERE del script `fix-asignaciones-relacionadas-a-schedule.sql`.
-- Deben ejecutarse una sola vez en la base de datos.
--
-- =============================================================================

-- Para acelerar la búsqueda inicial de schedules
CREATE INDEX IF NOT EXISTS legacy_schedule_ref_invoice_id_idx ON legacy_schedule (ref_invoice_id);
CREATE INDEX IF NOT EXISTS legacy_schedule_isvalid_idx ON legacy_schedule (isvalid);
CREATE INDEX IF NOT EXISTS legacy_schedule_legacy_cartera_id_idx ON legacy_schedule (legacy_cartera_id);

-- Para acelerar las búsquedas de asignaciones
CREATE INDEX IF NOT EXISTS c_allocationline_c_invoice_id_idx ON c_allocationline (c_invoice_id);
CREATE INDEX IF NOT EXISTS c_allocationline_c_allocationhdr_id_idx ON c_allocationline (c_allocationhdr_id);

-- Para acelerar la limpieza de la contabilidad
CREATE INDEX IF NOT EXISTS fact_acct_ad_table_id_record_id_idx ON fact_acct (ad_table_id, record_id);


RAISE NOTICE 'Creación de índices finalizada.';

-- =============================================================================
-- SECCIÓN DE LIMPIEZA (DROP INDEX)
-- =============================================================================
--
-- PROPÓSITO:
-- Descomentar y ejecutar esta sección ÚNICAMENTE después de que el script de
-- corrección principal haya procesado todos los lotes.
-- Elimina los índices para devolver la base de datos a su estado original.
--
-- =============================================================================
/* -- Descomentar para ejecutar

DROP INDEX IF EXISTS legacy_schedule_ref_invoice_id_idx;
DROP INDEX IF EXISTS legacy_schedule_isvalid_idx;
DROP INDEX IF EXISTS legacy_schedule_legacy_cartera_id_idx;
DROP INDEX IF EXISTS c_allocationline_c_invoice_id_idx;
DROP INDEX IF EXISTS c_allocationline_c_allocationhdr_id_idx;
DROP INDEX IF EXISTS fact_acct_ad_table_id_record_id_idx;

RAISE NOTICE 'Limpieza de índices finalizada.';

*/
