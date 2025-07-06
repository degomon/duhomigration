/**
 * CobroBridgeNativeAutocomplete
 * Proceso para sincronizar legacy_cobro con C_Payment, con lógica de asignación inteligente.
 *
 * Versión: 20250705 (Final con Fallback)
 * Lógica de Asignación:
 * 1. Si encuentra facturas de interés para la cartera, divide el pago en Capital e Interés.
 * - El Capital se asigna a la factura principal.
 * - El Interés se asigna en cascada a las facturas de interés pendientes.
 * - Si sobra interés, el remanente también se asigna a la factura principal.
 * 2. Si NO encuentra facturas de interés (modo de compatibilidad), asigna el 100% del pago a la factura principal.
 *
 * 20240810 - Incluir tiempo de ejecución
 * 20231030 - Fix para hacer skip cuando c_bankaccount_id no existe
 * 20220212 - Fix para tomar solo saldo sincronizado como ref.
 * 20210927 - Si cartera está sobregirada, omitir Cobro
 * 20210920 - Bypassear Cobros a Carteras no procesadas
 * 20210812 - First version
 * Rule: groovy:CobroBridgeNativeAutocomplete
 * Class: @script:groovy:CobroBridgeNativeAutocomplete
**/
import org.compiere.model.Query
import org.compiere.model.MOrg
import org.compiere.model.MBPartner
import org.compiere.model.MInvoice
import org.compiere.model.MUser
import org.compiere.model.MPayment
import org.compiere.model.MAllocationHdr
import org.compiere.model.MAllocationLine
import org.compiere.util.DB
import org.compiere.util.Env
import org.compiere.util.CLogger
import org.compiere.process.DocAction
import org.adempiere.exceptions.AdempiereException
import org.adempiere.model.GenericPO

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.logging.Level
import groovy.time.TimeCategory
import groovy.time.TimeDuration
import groovy.transform.Field

// ==========================================================================
//    CONFIGURACIÓN
// ==========================================================================
final int PAYMENT_DOCTYPE_ID = 1000050; // Recibo de Clientes
final int ALLOCATION_DOCTYPE_ID = 1000051; // Asignación de Cobros
final int RECORD_LIMIT = 0; // Límite de registros a procesar. 0 para sin límite.

// ==========================================================================
//    CAMPOS Y FUNCIONES HELPER
// ==========================================================================
@Field CLogger log = CLogger.getCLogger(GenericPO.class)
@Field int processedCount = 0
@Field int skippedCount = 0

def logProcess(String message) {
    A_ProcessInfo.addLog(0, null, null, message)
    log.info(message)
}

List<MInvoice> getFacturasDeInteresPendientes(GenericPO cartera) {
    String sql = """
        SELECT i.* FROM C_Invoice i
        INNER JOIN legacy_schedule s ON CAST(i.legacy_data AS INTEGER) = s.legacy_schedule_ID
        WHERE s.legacy_cartera_ID = ?
        AND i.IsSOTrx='Y' AND i.IsPaid='N' AND i.DocStatus='CO'
        ORDER BY i.DateInvoiced ASC
    """
    List<MInvoice> facturas = new Query(A_Ctx, MInvoice.Table_Name, sql, A_TrxName)
        .setParameters([cartera.get_ID()])
        .list()
    return facturas
}

MPayment crearPago(GenericPO cobro, MUser user, MOrg org) {
    BigDecimal montoAbono = cob.get_Value("abono")
    MPayment mp = new MPayment(A_Ctx, 0, A_TrxName)
    mp.setAD_Org_ID(org.get_ID())
    mp.setDescription("Recibo de Abono (ID Cobro: ${cobro.get_ID()})")
    mp.setC_BPartner_ID(cobro.get_ValueAsInt("c_bpartner_id"))
    mp.setDateAcct(cobro.get_Value("operacion"))
    mp.setDateTrx(cob.get_Value("operacion"))
    mp.setC_BankAccount_ID(user.get_ValueAsInt("C_BankAccount_ID"))
    mp.setTenderType("X")
    mp.setPayAmt(montoAbono)
    mp.setC_Currency_ID(209)
    mp.setC_DocType_ID(PAYMENT_DOCTYPE_ID)
    mp.processIt(DocAction.ACTION_Complete)
    mp.saveEx(A_TrxName)
    return mp
}

void crearAsignacion(MPayment pago, GenericPO cartera) {
    MInvoice facturaPrincipal = new MInvoice(A_Ctx, cartera.get_ValueAsInt("local_id"), A_TrxName)
    List<MInvoice> facturasInteres = getFacturasDeInteresPendientes(cartera)
    
    MAllocationHdr allocHdr = new MAllocationHdr(A_Ctx, 0, A_TrxName)
    allocHdr.setAD_Org_ID(pago.getAD_Org_ID())
    allocHdr.setDateAcct(pago.getDateAcct())
    allocHdr.setDateTrx(pago.getDateTrx())
    allocHdr.setC_Currency_ID(pago.getC_Currency_ID())
    
    if (facturasInteres.isEmpty()) {
        logProcess("    -> INFO: No se encontraron facturas de interés. Se asignará el 100% a la factura principal.")
        allocHdr.setDescription("Asignación total para Pago ${pago.getDocumentNo()}")
        allocHdr.saveEx(A_TrxName)

        MAllocationLine totalLine = new MAllocationLine(allocHdr)
        totalLine.setC_Payment_ID(pago.get_ID())
        totalLine.setC_Invoice_ID(facturaPrincipal.get_ID())
        totalLine.setAmount(pago.getPayAmt())
        totalLine.saveEx(A_TrxName)
    
    } else {
        BigDecimal montoTotalPago = pago.getPayAmt()
        BigDecimal tasa = cartera.get_Value("tasa") ?: BigDecimal.ZERO
        BigDecimal montoInteres = montoTotalPago.multiply(tasa).setScale(2, RoundingMode.HALF_UP)
        BigDecimal montoCapital = montoTotalPago.subtract(montoInteres)

        logProcess("    -> INFO: Lógica dividida. Capital: ${montoCapital}, Interés: ${montoInteres}.")
        allocHdr.setDescription("Asignación dividida para Pago ${pago.getDocumentNo()}")
        allocHdr.saveEx(A_TrxName)

        if (montoCapital.compareTo(BigDecimal.ZERO) > 0) {
            MAllocationLine capLine = new MAllocationLine(allocHdr)
            capLine.setC_Payment_ID(pago.get_ID())
            capLine.setC_Invoice_ID(facturaPrincipal.get_ID())
            capLine.setAmount(montoCapital)
            capLine.saveEx(A_TrxName)
        }

        BigDecimal interesRestante = montoInteres
        if (interesRestante.compareTo(BigDecimal.ZERO) > 0) {
            for (MInvoice factInt in facturasInteres) {
                if (interesRestante.compareTo(BigDecimal.ZERO) <= 0) break
                BigDecimal montoAAsignar = interesRestante.min(factInt.getOpenAmt())
                
                MAllocationLine intLine = new MAllocationLine(allocHdr)
                intLine.setC_Payment_ID(pago.get_ID())
                intLine.setC_Invoice_ID(factInt.get_ID())
                intLine.setAmount(montoAAsignar)
                intLine.saveEx(A_TrxName)
                
                interesRestante = interesRestante.subtract(montoAAsignar)
            }
        }
        
        if (interesRestante.compareTo(BigDecimal.ZERO) > 0) {
            logProcess("    -> INFO: Se pagaron todas las facturas de interés. Remanente de ${interesRestante} se asignará al principal.")
            MAllocationLine remanenteLine = new MAllocationLine(allocHdr)
            remanenteLine.setC_Payment_ID(pago.get_ID())
            remanenteLine.setC_Invoice_ID(facturaPrincipal.get_ID())
            remanenteLine.setAmount(interesRestante)
            remanenteLine.saveEx(A_TrxName)
        }
    }

    allocHdr.processIt(DocAction.ACTION_Complete)
    allocHdr.saveEx(A_TrxName)
    logProcess("    -> Asignación ${allocHdr.getDocumentNo()} creada y completada.")
}

boolean procesarCobroIndividual(GenericPO cobro, int workNumber) {
    int cobroID = cobro.get_ID()

    if (cobro.get_ValueAsInt("local_id") > 0) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: ya tiene un local_id.")
        return false
    }

    MBPartner bp = new Query(A_Ctx, "C_BPartner", "C_BPartner_ID = ?", A_TrxName)
        .setParameters([cobro.get_Value("c_bpartner_id")]).first()
    
    if (bp == null || bp.getLocations(false).length == 0) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: Socio de Negocio no encontrado o sin dirección.")
        return false
    }

    MUser usr = MUser.get(A_Ctx, cobro.getCreatedBy())
    if (usr.get_ValueAsInt("C_BankAccount_ID") <= 0) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: Usuario '${usr.getName()}' no tiene cuenta bancaria.")
        return false
    }

    GenericPO car = new Query(A_Ctx, "legacy_cartera", "legacy_cartera_id = ?", A_TrxName)
        .setParameters([cobro.get_ValueAsInt("id_cartera")]).first()
    if (car == null) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: Cartera asociada no encontrada.")
        return false
    }

    logProcess("⚙️ [${workNumber}] Procesando Cobro ID ${cobroID} para ${bp.getName()}...")
    
    MOrg org = MOrg.get(A_Ctx, cobro.getAD_Org_ID())
    MPayment pago = crearPago(cobro, usr, org)
    
    crearAsignacion(pago, car)
    
    cobro.set_ValueOfColumn("synced", "Y")
    cobro.set_ValueOfColumn("local_id", pago.get_ID())
    cobro.saveEx(A_TrxName)

    logProcess("✔️ [${workNumber}] OK: Cobro ID ${cobroID} procesado como Pago ${pago.getDocumentNo()}.")
    return true
}

// ==========================================================================
//    BLOQUE PRINCIPAL (ORQUESTADOR)
// ==========================================================================
Date start = new Date()

try {
    logProcess("✅ Iniciando proceso de sincronización de cobros...")

    List<GenericPO> legacyCobroList = new Query(A_Ctx, "legacy_cobro",
        " (synced != 'Y' or synced is null) and origen = 'native' and abono > 0 ", A_TrxName)
        .setOrderBy("operacion")
        .list()

    if (legacyCobroList.isEmpty()) {
        result = "Proceso finalizado. No se encontraron cobros pendientes."
        logProcess(result)
        return result
    }

    logProcess("🔍 Se encontraron ${legacyCobroList.size()} cobros para procesar.")

    for (int i = 0; i < legacyCobroList.size(); i++) {
        if (RECORD_LIMIT > 0 && i >= RECORD_LIMIT) {
            logProcess("alcanzado el límite de procesamiento de ${RECORD_LIMIT} registros. El proceso se completará.")
            break
        }
        
        if (procesarCobroIndividual(legacyCobroList[i], i + 1)) {
            processedCount++
        } else {
            skippedCount++
        }
    }

} catch (Exception e) {
    log.log(Level.SEVERE, "❌ Error fatal durante la sincronización de cobros.", e)
    result = "ERROR: " + e.getMessage()
    throw new AdempiereException(result, e)
}

Date stop = new Date()
TimeDuration td = TimeCategory.minus(stop, start)
result = "Proceso finalizado en ${td}. Procesados: ${processedCount}, Omitidos: ${skippedCount}."
logProcess("🏁 " + result)

return result