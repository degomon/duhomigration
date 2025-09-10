/**
 * InteresProcesoPagosAnticipados
 * Proceso para procesar legacy_schedule de carteras con pagos anticipados,
 * generando facturas de interés solo hasta el monto cubierto por cobros pendientes.
 * Versión: 20250815 - Cálculo de Deuda Real de Intereses
 */

import org.compiere.model.Query;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Trx;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.GenericPO;
import groovy.transform.Field;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.logging.Level;

// ==========================================================================
//    CONFIGURACIÓN
// ==========================================================================
@Field final int INTERES_CHARGE_ID = 1000047; // Comisión - Anticipado
@Field final int INTERES_DOCTYPE_ID = 1000051; // Interés

// ==========================================================================
//    CAMPOS Y FUNCIONES HELPER
// ==========================================================================
@Field CLogger log = CLogger.getCLogger(GenericPO.class);
@Field int carterasProcesadas = 0;
@Field int carterasOmitidas = 0;
@Field int facturasCreadas = 0;
@Field int cuotasOmitidas = 0;

def logProcess(String message) {
    A_ProcessInfo.addLog(0, null, null, message);
    log.info(message);
}

/**
 * Calcula la deuda total de intereses para una cartera.
 * Suma las cuotas pendientes de facturar y los saldos de facturas de interés ya emitidas.
 * @param cartera El registro de legacy_cartera.
 * @param trxName El nombre de la transacción.
 * @return La deuda total de intereses como BigDecimal.
 */
BigDecimal calcularDeudaTotalInteres(GenericPO cartera, String trxName) {
    int carteraID = cartera.get_ID();
    
    // Parte 1: Sumar el valor de las cuotas que aún no se han facturado.
    String sqlCuotasPendientes = "SELECT COALESCE(SUM(DueAmt), 0) FROM legacy_schedule WHERE legacy_cartera_ID = ? AND (Processed IS NULL OR Processed = 'N')";
    BigDecimal totalCuotasFuturas = DB.getSQLValueBD(trxName, sqlCuotasPendientes, carteraID);

    // Parte 2: Sumar el saldo pendiente de las facturas de interés que ya se crearon.
    String sqlSaldosFacturas = """
        SELECT COALESCE(SUM(invoiceOpen(i.C_Invoice_ID, 0)), 0)
        FROM C_Invoice i
        INNER JOIN legacy_schedule s ON CAST(i.legacy_data AS INTEGER) = s.legacy_schedule_ID
        WHERE s.legacy_cartera_ID = ?
        AND i.IsPaid = 'N' AND i.DocStatus = 'CO'
    """;
    BigDecimal totalSaldosFacturados = DB.getSQLValueBD(trxName, sqlSaldosFacturas, carteraID);
    
    BigDecimal deudaTotal = totalCuotasFuturas.add(totalSaldosFacturados);
    logProcess("    -> Cálculo Deuda Interés para Cartera ${carteraID}: Cuotas futuras (${totalCuotasFuturas}) + Saldos facturados (${totalSaldosFacturados}) = ${deudaTotal}");
    
    return deudaTotal;
}


// ==========================================================================
//    BLOQUE PRINCIPAL
// ==========================================================================
try {
    logProcess("✅ Iniciando proceso de Pagos Anticipados...");

    String sqlGetDate = (DB.isOracle() ? "SELECT TRUNC(SysDate) FROM DUAL" : "SELECT now()::date");
    Timestamp today = DB.getSQLValueTSEx(A_TrxName, sqlGetDate);
    if (today == null) {
        throw new AdempiereException("No se pudo obtener la fecha de la base de datos.");
    }
    logProcess("🗓️ Fecha de procesamiento para las nuevas facturas: ${today.toString().substring(0, 10)}");

    String carterasParam = null;
    for (ProcessInfoParameter p : A_Parameter) {
        if (p.getParameterName().equals("carteras")) {
            carterasParam = p.getParameterAsString();
            break;
        }
    }

    if (carterasParam == null || carterasParam.trim().isEmpty()) {
        result = "Proceso finalizado. No se proporcionó ninguna cartera en el parámetro 'carteras'.";
        logProcess(result);
        return result;
    }

    String[] carteraIDs = carterasParam.split(",");
    logProcess("🔍 Se procesarán ${carteraIDs.length} carteras.");

    for (String idStr in carteraIDs) {
        int carteraID = 0;
        try {
            carteraID = Integer.parseInt(idStr.trim());
        } catch (NumberFormatException e) {
            logProcess("⚠️ ADVERTENCIA: Se omitió el valor '${idStr}' porque no es un ID numérico válido.");
            carterasOmitidas++;
            continue;
        }
        
        String trxNameLoop = Trx.createTrxName("Anticipado_" + carteraID);
        Trx trx = Trx.get(trxNameLoop, true);

        try {
            GenericPO cartera = new Query(A_Ctx, "legacy_cartera", "legacy_cartera_id = ?", trxNameLoop)
                .setParameters(carteraID).first();

            if (cartera == null) {
                logProcess("⚠️ ADVERTENCIA: No se encontró la cartera con ID: ${carteraID}");
                carterasOmitidas++;
                trx.rollback();
                continue;
            }
            
            String sqlCobros = "SELECT COALESCE(SUM(abono), 0) FROM legacy_cobro WHERE id_cartera = ? AND (synced IS NULL OR synced = 'N')";
            BigDecimal totalPorCubrir = DB.getSQLValueBD(trxNameLoop, sqlCobros, carteraID);
            
            if (totalPorCubrir.compareTo(BigDecimal.ZERO) <= 0) {
                logProcess("ℹ️ INFO: Cartera ID ${carteraID} no tiene cobros pendientes por sincronizar. Se omite.");
                carterasOmitidas++;
                trx.rollback();
                continue;
            }
            logProcess("    -> Monto a cubrir por pagos anticipados: ${totalPorCubrir}");

            // --- VALIDACIÓN DE SUFICIENCIA MEJORADA ---
            BigDecimal deudaTotalInteres = calcularDeudaTotalInteres(cartera, trxNameLoop);

            if (deudaTotalInteres.compareTo(totalPorCubrir) < 0) {
                throw new AdempiereException("La deuda total de interés (${deudaTotalInteres}) es menor al total por cubrir (${totalPorCubrir}) para la Cartera ID ${carteraID}.");
            }
            
            String where = "legacy_cartera_ID = ? AND (Processed IS NULL OR Processed = 'N')";
            List<GenericPO> schedules = new Query(A_Ctx, "legacy_schedule", where, trxNameLoop)
                .setParameters(cartera.get_ID()).setOrderBy("DueDate").list();
            
            logProcess("⚙️ Procesando ${schedules.size()} cuota(s) pendiente(s) para la Cartera ID ${carteraID}...");
            MInvoice originalInvoice = new MInvoice(A_Ctx, cartera.get_ValueAsInt("local_id"), trxNameLoop);
            BigDecimal cubierto = BigDecimal.ZERO;

            for (GenericPO schedule in schedules) {
                if (cubierto.compareTo(totalPorCubrir) >= 0) {
                    logProcess("   -> Límite de ${totalPorCubrir} alcanzado. Deteniendo la generación de facturas para esta cartera.");
                    break;
                }

                int scheduleID = schedule.get_ID();
                String checkSQL = "SELECT COUNT(*) FROM C_Invoice WHERE legacy_data = ? AND DocStatus IN ('CO', 'CL')";
                int existingInvoiceCount = DB.getSQLValue(trxNameLoop, checkSQL, scheduleID.toString());

                if (existingInvoiceCount > 0) {
                    logProcess("⏭️ AVISO: Ya existe una factura para la cuota ID ${scheduleID}. Se omite.");
                    schedule.set_ValueOfColumn("Processed", "Y");
                    schedule.saveEx(trxNameLoop);
                    cuotasOmitidas++;
                    continue;
                }

                BigDecimal dueAmt = schedule.get_Value("DueAmt");

                MInvoice interestInvoice = new MInvoice(A_Ctx, 0, trxNameLoop);
                interestInvoice.setAD_Org_ID(schedule.getAD_Org_ID());
                interestInvoice.setC_DocTypeTarget_ID(INTERES_DOCTYPE_ID);
                interestInvoice.setDateInvoiced(today);
                interestInvoice.setDateAcct(today);
                interestInvoice.setC_BPartner_ID(originalInvoice.getC_BPartner_ID());
                interestInvoice.setC_BPartner_Location_ID(originalInvoice.getC_BPartner_Location_ID());
                interestInvoice.set_ValueOfColumn("legacy_data", scheduleID.toString());
                interestInvoice.setIsSOTrx(true);
                interestInvoice.setDescription("Factura por pago anticipado de cuota ID ${scheduleID}");
                interestInvoice.saveEx();

                MInvoiceLine interestLine = new MInvoiceLine(interestInvoice);
                interestLine.setC_Charge_ID(INTERES_CHARGE_ID);
                interestLine.setQty(BigDecimal.ONE);
                interestLine.setPrice(dueAmt);
                interestLine.saveEx();

                interestInvoice.processIt(DocAction.ACTION_Complete);
                interestInvoice.saveEx();

                schedule.set_ValueOfColumn("Processed", "Y");
                schedule.saveEx();
                
                cubierto = cubierto.add(dueAmt);
                logProcess("  -> ✔️ Factura ${interestInvoice.getDocumentNo()} creada. Monto cubierto actual: ${cubierto}");
                facturasCreadas++;
            }
            
            trx.commit();
            carterasProcesadas++;

        } catch (Exception e_inner) {
            trx.rollback();
            log.log(Level.SEVERE, "❌ Error procesando Cartera ID ${carteraID}.", e_inner);
            logProcess("❌ Error en Cartera ID ${carteraID}: " + e_inner.getMessage());
            carterasOmitidas++;
        } finally {
            trx.close();
        }
    }

    result = "Proceso finalizado. Carteras procesadas: ${carterasProcesadas}, Omitidas: ${carterasOmitidas}. Total facturas: ${facturasCreadas}. Cuotas duplicadas: ${cuotasOmitidas}.";

} catch (Exception e) {
    log.log(Level.SEVERE, "❌ Error fatal en el proceso.", e);
    result = "ERROR: " + e.getMessage();
    throw new AdempiereException(result, e);
}

logProcess("🏁 " + result);
return result;