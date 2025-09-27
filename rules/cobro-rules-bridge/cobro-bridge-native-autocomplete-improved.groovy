/**
 * CobroBridgeNativeAutocomplete
 * Proceso para sincronizar legacy_cobro con C_Payment.
 * 
 * 20250927 - Se elimina la lógica de asignación automática. Este script ahora solo crea el C_Payment.
 *            Un proceso separado (AsignacionAutomatica) se encargará de asignar los pagos.
 * 
 * Versión: 20250927
 * 20250815 - Actualiza estado de cartera con pago anticipado de 'POTENCIAL' a 'PROCESADO'.
 * 20250815 - Asignar fecha de hoy a MAllocationHdr para evitar errores de fecha.
 * 20250808 - verificamos si la factura principal ya está pagada antes de procesar el cobro y si hay facturas de interés pendientes.
 * Versión: 20250711 (Optimización de Memoria)
 * Lógica:
 * - Carga inicial solo de IDs para reducir el consumo de memoria.
 * - Procesa cada registro individualmente cargándolo dentro del bucle.
 */
import org.compiere.model.Query;
import org.compiere.model.MOrg;
import org.compiere.model.MBPartner;
import org.compiere.model.MUser;
import org.compiere.model.MPayment;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.CLogger;
import org.compiere.process.DocAction;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.GenericPO;
import org.compiere.util.Trx;

import java.math.BigDecimal;
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
@Field final int PAYMENT_DOCTYPE_ID = 1000050; // Recibo de Clientes
@Field final int RECORD_LIMIT = 250; // Límite de registros a procesar. 0 para sin límite.
@Field final int CURRENCY_ID = 209;

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

List<Integer> getPendingCobroIDs() {
    List<Integer> idList = new ArrayList<Integer>();
    String sql = "SELECT legacy_cobro_id FROM legacy_cobro WHERE (synced = 'N') AND origen = 'native' AND abono > 0 ORDER BY operacion";
    
    if (RECORD_LIMIT > 0) {
        sql = sql + " LIMIT " + RECORD_LIMIT;
    }

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
        pstmt = DB.prepareStatement(sql, null);
        rs = pstmt.executeQuery();
        while (rs.next()) {
            idList.add(rs.getInt(1));
        }
    } catch (Exception e) {
        throw new AdempiereException("Error al obtener IDs de cobros pendientes", e);
    } finally {
        DB.close(rs, pstmt);
    }
    return idList;
}

MPayment crearPago(GenericPO cobro, MUser user, MOrg org) {
    BigDecimal montoAbono = cobro.get_Value("abono");
    MPayment mp = new MPayment(g_Ctx, 0, g_TrxName);
    mp.setAD_Org_ID(org.get_ID());
    mp.setDescription("Recibo Abono ID ${cobro.get_ID()}");
    mp.setC_BPartner_ID(cobro.get_ValueAsInt("c_bpartner_id"));
    mp.setDateAcct(cobro.get_Value("operacion"));
    mp.setDateTrx(cobro.get_Value("operacion"));
    mp.setC_BankAccount_ID(user.get_ValueAsInt("C_BankAccount_ID"));
    mp.setTenderType("X");
    mp.setPayAmt(montoAbono);
    mp.setC_Currency_ID(CURRENCY_ID);
    mp.setC_DocType_ID(PAYMENT_DOCTYPE_ID);
    
    if (!mp.processIt(DocAction.ACTION_Complete)) {
        throw new AdempiereException("Error al procesar el pago: " + mp.getProcessMsg());
    }
    mp.saveEx();
    return mp;
}

boolean procesarCobroIndividual(GenericPO cobro, int workNumber) {
    int cobroID = cobro.get_ID();
    if (cobro.get_ValueAsInt("local_id") > 0) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: ya tiene un local_id.");
        return false;
    }
    MBPartner bp = new Query(g_Ctx, "C_BPartner", "C_BPartner_ID = ?", g_TrxName).setParameters([cobro.get_Value("c_bpartner_id")]).first();
    if (bp == null) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: Socio de Negocio no encontrado.");
        return false;
    }
    MUser usr = MUser.get(g_Ctx, cobro.getCreatedBy());
    if (usr == null) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: Usuario no encontrado.");
        return false;
    }
    if (usr.get_ValueAsInt("C_BankAccount_ID") <= 0) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: Usuario '${usr.getName()}' no tiene cuenta bancaria asignada.");
        return false;
    }
    
    // La validación de cartera y facturas ya no es necesaria aquí.
    // Se asume que si el cobro existe, es válido para crear un pago.

    logProcess("⚙️ [${workNumber}] Procesando Cobro ID ${cobroID} para ${bp.getName()}...");
    MOrg org = MOrg.get(g_Ctx, cobro.getAD_Org_ID());
    
    MPayment pago = crearPago(cobro, usr, org);
    
    cobro.set_ValueOfColumn("synced", "Y");
    cobro.set_ValueOfColumn("local_id", new BigDecimal(pago.get_ID()));
    cobro.saveEx();
    
    logProcess("✔️ [${workNumber}] OK: Cobro ID ${cobroID} procesado. Creado Pago ${pago.getDocumentNo()}.");
    return true;
}

// ==========================================================================
//    BLOQUE PRINCIPAL (ORQUESTADOR)
// ==========================================================================
Date start = new Date();
try {
    this.g_Ctx = A_Ctx;
    this.g_TrxName = A_TrxName;

    String sqlGetDate = (DB.isOracle() ? "SELECT TRUNC(SysDate) FROM DUAL" : "SELECT now()::date");
    this.g_Today = DB.getSQLValueTSEx(g_TrxName, sqlGetDate);
    if (g_Today == null) {
        throw new AdempiereException("No se pudo obtener la fecha de la base de datos.");
    }

    logProcess("✅ Iniciando proceso de sincronización de cobros (Solo Creación de Pagos)...");
    
    List<Integer> cobroIDs = getPendingCobroIDs();

    if (cobroIDs.isEmpty()) {
        result = "Proceso finalizado. No se encontraron cobros pendientes.";
        logProcess(result);
        return result;
    }
    logProcess("🔍 Se encontraron ${cobroIDs.size()} cobros para procesar.");

    for (int i = 0; i < cobroIDs.size(); i++) {
        int cobroID = cobroIDs[i];
        
        String trxNameLoop = Trx.createTrxName("Cobro_" + cobroID);
        Trx trx = Trx.get(trxNameLoop, true);
        this.g_TrxName = trxNameLoop;

        try {
            GenericPO cobro = new Query(g_Ctx, "legacy_cobro", "legacy_cobro_id = ?", g_TrxName).setParameters(cobroID).first();
            if (cobro == null) {
                logProcess("⏭️ [${i + 1}] Se omite Cobro ID ${cobroID}: no se pudo cargar (posiblemente eliminado).");
                skippedCount++;
                trx.rollback();
                continue;
            }

            if (procesarCobroIndividual(cobro, i + 1)) {
                trx.commit();
                processedCount++;
            } else {
                trx.rollback();
                skippedCount++;
            }
        } catch (Exception e_inner) {
            trx.rollback();
            log.log(Level.SEVERE, "❌ Error procesando Cobro ID ${cobroID}.", e_inner);
            logProcess("❌ Error en Cobro ID ${cobroID}: " + e_inner.getMessage());
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
