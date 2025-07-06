/**
 * InteresProcesoDaily
 * Proceso para generar facturas de interés basadas en el plan de pagos (legacy_schedule).
 *
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
import groovy.transform.Field;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.logging.Level;

// ==========================================================================
//    CONFIGURACIÓN
// ==========================================================================
@Field final int INTERES_CHARGE_ID = 1000029;
@Field final int INTERES_DOCTYPE_ID = 1000051; // Interés

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

try {
    logProcess("✅ Iniciando proceso diario de generación de intereses...");

    String sqlGetDate = "";
    if (DB.isOracle()) {
        sqlGetDate = "SELECT TRUNC(SysDate) FROM DUAL";
    } else if (DB.isPostgreSQL()) {
        sqlGetDate = "SELECT now()::date";
    } else {
        sqlGetDate = "SELECT CURRENT_DATE";
    }
    Timestamp today = DB.getSQLValueTSEx(A_TrxName, sqlGetDate);

    if (today == null) {
        throw new AdempiereException("No se pudo obtener la fecha de la base de datos.");
    }

    logProcess("🗓️ Fecha de procesamiento: " + today.toString().substring(0, 10));

    String whereClause = "TRUNC(DueDate) = ? AND (Processed IS NULL OR Processed = 'N') AND IsActive = 'Y'";
    List<GenericPO> schedulesToProcess = new Query(A_Ctx, "legacy_schedule", whereClause, A_TrxName)
        .setParameters([today])
        .list();

    if (schedulesToProcess.isEmpty()) {
        result = "Proceso finalizado. No se encontraron cuotas para procesar en la fecha de hoy.";
        logProcess(result);
        return result;
    }

    logProcess("🔍 Se encontraron ${schedulesToProcess.size()} cuotas para procesar.");

    for (GenericPO schedule in schedulesToProcess) {
        
        // ----- CORRECCIÓN AQUÍ -----
        // Se añade validación para asegurar que el registro tenga una Organización
        if (schedule.getAD_Org_ID() <= 0) {
            logProcess("⚠️ ADVERTENCIA: Se omitió cuota ID ${schedule.get_ID()} porque no tiene una Organización (AD_Org_ID) asignada.");
            skippedCount++;
            continue; // Saltar al siguiente registro
        }
        // ----- FIN DE LA CORRECCIÓN -----
        
        int scheduleID = schedule.get_ID();

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
        interestInvoice.setIsSOTrx(true);
        interestInvoice.setDescription("Factura por interés de cuota ID ${scheduleID} con vencimiento " + schedule.get_Value("DueDate").toString().substring(0, 10));
        interestInvoice.saveEx(A_TrxName);

        MInvoiceLine interestLine = new MInvoiceLine(interestInvoice);
        interestLine.setC_Charge_ID(INTERES_CHARGE_ID);
        interestLine.setQty(BigDecimal.ONE);
        interestLine.setPrice(dueAmt);
        interestLine.saveEx(A_TrxName);

        interestInvoice.processIt(DocAction.ACTION_Complete);
        interestInvoice.saveEx(A_TrxName);

        schedule.set_ValueOfColumn("Processed", "Y");
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