/**
 * AsignacionAutomatica
 * =====================
 * Proceso para asignar automáticamente pagos de clientes (C_Payment) que no han sido asignados
 * a sus facturas pendientes (C_Invoice).
 *
 * Versión: 20250927
 *
 * Lógica de Negocio:
 * 1.  Busca pagos de clientes (recibos) que estén completados pero no asignados.
 * 2.  Para cada pago, busca las facturas más antiguas y pendientes del mismo socio de negocio.
 * 3.  Crea una Asignación (Allocation) para vincular el pago con una o más facturas hasta
 *     que el monto del pago se consuma.
 * 4.  Procesa cada pago en una transacción separada para asegurar la integridad de los datos.
 */
import org.compiere.model.Query;
import org.compiere.model.MOrg;
import org.compiere.model.MBPartner;
import org.compiere.model.MInvoice;
import org.compiere.model.MUser;
import org.compiere.model.MPayment;
import org.compiere.model.MAllocationHdr;
import org.compiere.model.MAllocationLine;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.CLogger;
import org.compiere.process.DocAction;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.GenericPO;
import org.compiere.util.Trx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Level;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import groovy.time.TimeCategory;
import groovy.time.TimeDuration;
import groovy.transform.Field;

// ==========================================================================
//    CONFIGURACIÓN
// ==========================================================================
@Field final int ALLOCATION_DOCTYPE_ID = 1000051; // Asignación de Cobros
@Field final int RECORD_LIMIT = 10; // Límite de pagos a procesar en cada ejecución. 0 para sin límite.

// ==========================================================================
//    CAMPOS Y CONTEXTO GLOBAL
// ==========================================================================
@Field CLogger log = CLogger.getCLogger(GenericPO.class);
@Field int processedCount = 0;
@Field int skippedCount = 0;
@Field Properties g_Ctx;
@Field String g_TrxName;
@Field Timestamp g_Today;

// ==========================================================================
//    FUNCIONES HELPER
// ==========================================================================
def logProcess(String message) {
    A_ProcessInfo.addLog(0, null, null, message);
    log.info(message);
    System.out.println(message);
}

/**
 * Obtiene una lista de pagos completados que no han sido asignados, usando un query SQL directo
 * para mayor eficiencia y precisión, como fue solicitado.
 */
List<MPayment> getUnallocatedPayments() {
    List<MPayment> payments = new ArrayList<MPayment>();
    String sql = """
        SELECT pay.*
        FROM C_Payment pay
        LEFT JOIN C_AllocationLine cal ON pay.C_Payment_ID = cal.C_Payment_ID
        WHERE pay.C_DocType_ID = 1000050
            AND pay.DocStatus = 'CO'
            AND pay.PayAmt > 0
            AND pay.C_Invoice_ID IS NULL
            AND cal.C_Payment_ID IS NULL
        ORDER BY pay.DateAcct ASC
    """;

    if (RECORD_LIMIT > 0) {
        sql += " LIMIT " + RECORD_LIMIT;
    }

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
        pstmt = DB.prepareStatement(sql, g_TrxName);
        rs = pstmt.executeQuery();
        while (rs.next()) {
            // Instanciar el MPayment directamente desde el ResultSet
            payments.add(new MPayment(g_Ctx, rs, g_TrxName));
        }
    } catch (Exception e) {
        log.log(Level.SEVERE, "Error ejecutando query de pagos no asignados", e);
        throw new AdempiereException("Error al obtener pagos pendientes.", e);
    } finally {
        DB.close(rs, pstmt);
    }
    
    return payments;
}
/**
 * Obtiene las facturas pendientes de un socio de negocio, ordenadas por la más antigua.
 */
List<MInvoice> getPendingInvoices(int C_BPartner_ID) {
    String whereClause = "IsSOTrx='Y' AND IsPaid='N' AND DocStatus='CO' AND C_BPartner_ID=?";
    
    List<MInvoice> invoices = new Query(g_Ctx, MInvoice.Table_Name, whereClause, g_TrxName)
        .setParameters(C_BPartner_ID)
        .setOrderBy(MInvoice.COLUMNNAME_DateInvoiced + " ASC")
        .list();
        
    return invoices;
}

/**
 * Procesa la asignación para un único pago.
 */
boolean processSinglePayment(MPayment payment, int workNumber) {
    logProcess("⚙️ [${workNumber}] Procesando Pago ${payment.getDocumentNo()} de ${payment.getC_BPartner().getName()} por ${payment.getPayAmt()}...");

    List<MInvoice> invoices = getPendingInvoices(payment.getC_BPartner_ID());

    if (invoices.isEmpty()) {
        logProcess("⏭️ Se omite Pago ${payment.getDocumentNo()}: No se encontraron facturas pendientes para este Socio de Negocio.");
        // Opcional: Marcar el pago de alguna forma para no volver a consultarlo.
        // payment.setDescription(payment.getDescription() + " | Sin facturas pendientes en " + g_Today)
        // payment.saveEx();
        return false;
    }

    BigDecimal amountToAllocate = payment.getPayAmt();
    
    MAllocationHdr allocHdr = new MAllocationHdr(g_Ctx, 0, g_TrxName);
    allocHdr.setAD_Org_ID(payment.getAD_Org_ID());
    allocHdr.setDateAcct(g_Today);
    allocHdr.setDateTrx(g_Today);
    allocHdr.setC_Currency_ID(payment.getC_Currency_ID());
    allocHdr.setC_DocType_ID(ALLOCATION_DOCTYPE_ID);
    allocHdr.setDescription("Asignación automática para Pago ${payment.getDocumentNo()}");
    allocHdr.saveEx();

    logProcess("    -> Creada cabecera de asignación temporal.");

    for (MInvoice invoice in invoices) {
        if (amountToAllocate.compareTo(BigDecimal.ZERO) <= 0) {
            break; // El monto del pago ya se ha asignado por completo.
        }

        BigDecimal openAmt = invoice.getOpenAmt();
        if (openAmt.compareTo(BigDecimal.ZERO) <= 0) {
            continue; // La factura no tiene saldo pendiente.
        }

        BigDecimal allocatedAmt = amountToAllocate.min(openAmt);

        MAllocationLine allocLine = new MAllocationLine(allocHdr);
        allocLine.setC_Payment_ID(payment.get_ID());
        allocLine.setC_Invoice_ID(invoice.get_ID());
        allocLine.setAmount(allocatedAmt);
        allocLine.saveEx();

        amountToAllocate = amountToAllocate.subtract(allocatedAmt);
        logProcess("    -> Asignando ${allocatedAmt} a Factura ${invoice.getDocumentNo()}. Saldo restante del pago: ${amountToAllocate}");
    }

    if (!allocHdr.processIt(DocAction.ACTION_Complete)) {
        throw new AdempiereException("Error al completar la asignación: " + allocHdr.getProcessMsg());
    }
    allocHdr.saveEx();
    
    // Opcional: Actualizar descripción del pago
    // payment.setDescription(payment.getDescription().replace(" | Pendiente de asignación", "") + " | Asignado Automáticamente");
    payment.saveEx();

    logProcess("    -> Asignación ${allocHdr.getDocumentNo()} creada y completada.");
    return true;
}


// ==========================================================================
//    BLOQUE PRINCIPAL (ORQUESTADOR)
// ==========================================================================
Date start = new Date();
try {
    this.g_Ctx = A_Ctx;
    this.g_TrxName = A_TrxName; // Transacción global inicial

    String sqlGetDate = (DB.isOracle() ? "SELECT TRUNC(SysDate) FROM DUAL" : "SELECT now()::date");
    this.g_Today = DB.getSQLValueTSEx(g_TrxName, sqlGetDate);
    if (g_Today == null) {
        throw new AdempiereException("No se pudo obtener la fecha de la base de datos.");
    }

    logProcess("✅ Iniciando proceso de Asignación Automática de Pagos...");
    
    List<MPayment> payments = getUnallocatedPayments();

    if (payments.isEmpty()) {
        result = "Proceso finalizado. No se encontraron pagos pendientes de asignación.";
        logProcess(result);
        return result;
    }
    logProcess("🔍 Se encontraron ${payments.size()} pagos para procesar.");

    for (int i = 0; i < payments.size(); i++) {
        MPayment payment = payments.get(i);
        
        String trxNameLoop = Trx.createTrxName("Alloc_" + payment.getDocumentNo());
        Trx trx = Trx.get(trxNameLoop, true);
        this.g_TrxName = trxNameLoop;

        try {
            // Recargamos el pago dentro de la nueva transacción
            MPayment paymentInTrx = new MPayment(g_Ctx, payment.get_ID(), g_TrxName);

            if (processSinglePayment(paymentInTrx, i + 1)) {
                trx.commit();
                processedCount++;
            } else {
                trx.rollback();
                skippedCount++;
            }
        } catch (Exception e_inner) {
            trx.rollback();
            log.log(Level.SEVERE, "❌ Error procesando Pago ID ${payment.get_ID()}.", e_inner);
            logProcess("❌ Error en Pago ${payment.getDocumentNo()}: " + e_inner.getMessage());
            skippedCount++;
        } finally {
            trx.close();
        }
    }
} catch (Exception e) {
    log.log(Level.SEVERE, "❌ Error fatal del script.", e);
    result = "ERROR: " + e.getMessage();
    throw new AdempiereException(result, e);
}

Date stop = new Date();
TimeDuration td = TimeCategory.minus(stop, start);
result = "Proceso finalizado en ${td}. Procesados: ${processedCount}, Omitidos: ${skippedCount}.";
logProcess("🏁 " + result);
return result;
