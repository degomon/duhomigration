/**
 * InteresProcesoDaily
 * Proceso para generar facturas de interés basadas en el plan de pagos (legacy_schedule).
 * 20250928 - agregamos ref_invoice_id a legacy_schedule para referencia futura
 * Versión: 20250705 (Corregido)
 */

import org.compiere.model.Query;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.process.DocAction;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.GenericPO;
import org.compiere.process.ProcessInfoParameter;
import groovy.transform.Field;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.logging.Level;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// ==========================================================================
//    CONFIGURACIÓN
// ==========================================================================
@Field final int INTERES_CHARGE_ID = 1000029;
@Field final int INTERES_DOCTYPE_ID = 1000051; // Interés
@Field final int CURRENCY_ID = 209
@Field final int SALES_REP_ID = 1000000
@Field final int PRICE_LIST_ID = 1000001
@Field final int TAX_ID = 1000000


// ==========================================================================
//    CAMPOS Y FUNCIONES HELPER
// ==========================================================================
@Field CLogger log = CLogger.getCLogger(GenericPO.class);
@Field int processedCount = 0;
@Field int skippedCount = 0;

def logProcess(String message) {
    A_ProcessInfo.addLog(0, null, null, message);
    log.info(message);
}

List<Integer> getPendingScheduleIDs(Timestamp processDate) {
    List<Integer> idList = new ArrayList<Integer>();
    String baseSql = "SELECT legacy_schedule_id FROM legacy_schedule WHERE TRUNC(DueDate) = ? AND (Processed IS NULL OR Processed = 'N') AND IsActive = 'Y' ORDER BY Created DESC";
    String sql;
    final int RECORD_LIMIT = 100;

    if (DB.isOracle()) {
        sql = "SELECT * FROM (" + baseSql + ") WHERE ROWNUM <= " + RECORD_LIMIT;
    } else { // Assumes PostgreSQL, MySQL, or other DBs supporting LIMIT
        sql = baseSql + " LIMIT " + RECORD_LIMIT;
    }

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
        pstmt = DB.prepareStatement(sql, null); // Read-only query, no transaction name needed
        pstmt.setTimestamp(1, processDate);
        rs = pstmt.executeQuery();
        while (rs.next()) {
            idList.add(rs.getInt(1));
        }
    } catch (Exception e) {
        throw new AdempiereException("Error getting pending schedule IDs.", e);
    } finally {
        DB.close(rs, pstmt);
    }
    return idList;
}

try {
    logProcess("✅ Iniciando proceso diario de generación de intereses...");

    // ==========================================================================
    //    MANEJO DE PARÁMETROS
    // ==========================================================================
    Timestamp fechaParam = null;
    ProcessInfoParameter[] para = A_Parameter;
    for (int i = 0; i < para.length; i++) {
        String name = para[i].getParameterName();
        if (para[i].getParameter() == null) {
            // Parámetro vacío, se ignora
        } else if (name.equals("fecha")) {
            fechaParam = para[i].getParameterAsTimestamp();
        } else {
            log.log(Level.WARNING, "Parámetro desconocido: " + name);
        }
    }

    // Determinar la fecha de procesamiento
    Timestamp today = fechaParam;
    if (today == null) {
        // Si no se proporcionó el parámetro fecha, usar la fecha actual
        String sqlGetDate = "";
        if (DB.isOracle()) {
            sqlGetDate = "SELECT TRUNC(SysDate) FROM DUAL";
        } else if (DB.isPostgreSQL()) {
            sqlGetDate = "SELECT now()::date";
        } else {
            sqlGetDate = "SELECT CURRENT_DATE";
        }
        today = DB.getSQLValueTSEx(A_TrxName, sqlGetDate);

        if (today == null) {
            throw new AdempiereException("No se pudo obtener la fecha de la base de datos.");
        }
    }

    logProcess("🗓️ Fecha de procesamiento: " + today.toString().substring(0, 10));

    List<Integer> scheduleIDs = getPendingScheduleIDs(today);

    if (scheduleIDs.isEmpty()) {
        result = "Proceso finalizado. No se encontraron cuotas para procesar en la fecha de hoy.";
        logProcess(result);
        return result;
    }

    logProcess("🔍 Se encontraron ${scheduleIDs.size()} cuotas para procesar.");

    for (int scheduleID in scheduleIDs) {
        GenericPO schedule = new Query(A_Ctx, "legacy_schedule", "legacy_schedule_id = ?", A_TrxName)
            .setParameters(scheduleID)
            .first();

        if (schedule == null) {
            logProcess("⏭️ Se omite cuota ID ${scheduleID}: no se pudo cargar (posiblemente eliminada).");
            skippedCount++;
            continue;
        }

        String checkSQL = "SELECT COUNT(*) FROM C_Invoice WHERE legacy_data = ? AND DocStatus IN ('CO', 'CL')";
        int existingInvoiceCount = DB.getSQLValue(A_TrxName, checkSQL, scheduleID.toString());

        if (existingInvoiceCount > 0) {
            logProcess("⏭️ AVISO: Ya existe una factura para la cuota ID ${scheduleID}. Se omite la creación.");
            schedule.set_ValueOfColumn("Processed", "Y");
            schedule.saveEx(A_TrxName);
            skippedCount++;
            continue;
        }
        
        int originalInvoiceID = schedule.get_ValueAsInt("C_Invoice_ID");
        MInvoice originalInvoice = new MInvoice(A_Ctx, originalInvoiceID, A_TrxName);

        if (originalInvoice == null || originalInvoice.get_ID() == 0) {
            logProcess("⚠️ ADVERTENCIA: Se omitió cuota ID ${scheduleID} porque no se encontró la factura original con ID ${originalInvoiceID}.");
            skippedCount++;
            continue;
        }

        BigDecimal dueAmt = schedule.get_Value("DueAmt");
        int bPartnerID = originalInvoice.getC_BPartner_ID();
        
        logProcess("⚙️ Procesando cuota ID ${scheduleID} por un monto de ${dueAmt} para el Socio de Negocio ID ${bPartnerID}.");

        MInvoice interestInvoice = new MInvoice(A_Ctx, 0, A_TrxName);
        interestInvoice.setAD_Org_ID(schedule.getAD_Org_ID());
        interestInvoice.setC_DocTypeTarget_ID(INTERES_DOCTYPE_ID);
        interestInvoice.setDateInvoiced(today);
        interestInvoice.setDateAcct(today);
        interestInvoice.setC_BPartner_ID(bPartnerID);
        interestInvoice.setC_BPartner_Location_ID(originalInvoice.getC_BPartner_Location_ID());
        interestInvoice.set_ValueOfColumn("legacy_data", scheduleID.toString());
        interestInvoice.setSalesRep_ID(SALES_REP_ID); 
        interestInvoice.setM_PriceList_ID(PRICE_LIST_ID)
        interestInvoice.setIsSOTrx(true);
        interestInvoice.setDescription("Factura por interés de cuota ID ${scheduleID} con vencimiento " + schedule.get_Value("DueDate").toString().substring(0, 10));
        interestInvoice.saveEx(A_TrxName);

        MInvoiceLine interestLine = new MInvoiceLine(interestInvoice);
        interestLine.setC_Charge_ID(INTERES_CHARGE_ID);
        interestLine.setQty(BigDecimal.ONE);
        interestLine.setPrice(dueAmt);
        interestLine.setC_Tax_ID(TAX_ID);
        interestLine.saveEx(A_TrxName);

        interestInvoice.processIt(DocAction.ACTION_Complete);
        interestInvoice.saveEx(A_TrxName);

        schedule.set_ValueOfColumn("Processed", "Y");
        schedule.set_ValueOfColumn("Ref_Invoice_ID", interestInvoice.get_ID());
        schedule.saveEx(A_TrxName);

        logProcess("✔️ OK: Factura ${interestInvoice.getDocumentNo()} creada para la cuota ID ${scheduleID}.");
        processedCount++;
    }

    result = "Proceso finalizado. Facturas creadas: ${processedCount}, Omitidas: ${skippedCount}.";

} catch (Exception e) {
    log.log(Level.SEVERE, "Error fatal durante la generación de facturas de interés.", e);
    result = "❌ ERROR: " + e.getMessage();
    throw new AdempiereException(result, e);
}

logProcess("🏁 " + result);
return result;