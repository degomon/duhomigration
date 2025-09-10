/**
 * CobroBridgeNativeAutocomplete
 * Proceso para sincronizar legacy_cobro con C_Payment, con lógica de asignación inteligente.
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
@Field final int PAYMENT_DOCTYPE_ID = 1000050; // Recibo de Clientes
@Field final int ALLOCATION_DOCTYPE_ID = 1000051; // Asignación de Cobros
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

List<MInvoice> getFacturasDeInteresPendientes(GenericPO cartera) {
    List<MInvoice> facturas = new ArrayList<MInvoice>();
    String sql = """
        SELECT i.* FROM C_Invoice i
        INNER JOIN legacy_schedule s ON CAST(i.legacy_data AS INTEGER) = s.legacy_schedule_ID
        WHERE s.legacy_cartera_ID = ?
        AND i.IsSOTrx='Y' AND i.IsPaid='N' AND i.DocStatus='CO'
        ORDER BY i.DateInvoiced ASC
    """;
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
        pstmt = DB.prepareStatement(sql, g_TrxName);
        pstmt.setInt(1, cartera.get_ID());
        rs = pstmt.executeQuery();
        while (rs.next()) {
            facturas.add(new MInvoice(g_Ctx, rs, g_TrxName));
        }
    } catch (Exception e) {
        throw new AdempiereException("Error al buscar facturas de interés", e);
    } finally {
        DB.close(rs, pstmt);
    }
    return facturas;
}

MPayment crearPago(GenericPO cobro, MUser user, MOrg org) {
    BigDecimal montoAbono = cobro.get_Value("abono");
    MPayment mp = new MPayment(g_Ctx, 0, g_TrxName);
    mp.setAD_Org_ID(org.get_ID());
    mp.setDescription("Recibo de Abono (ID Cobro: ${cobro.get_ID()})");
    mp.setC_BPartner_ID(cobro.get_ValueAsInt("c_bpartner_id"));
    mp.setDateAcct(cobro.get_Value("operacion"));
    mp.setDateTrx(cobro.get_Value("operacion"));
    mp.setC_BankAccount_ID(user.get_ValueAsInt("C_BankAccount_ID"));
    mp.setTenderType("X");
    mp.setPayAmt(montoAbono);
    mp.setC_Currency_ID(CURRENCY_ID);
    mp.setC_DocType_ID(PAYMENT_DOCTYPE_ID);
    mp.processIt(DocAction.ACTION_Complete);
    mp.saveEx();
    return mp;
}

void crearAsignacion(MPayment pago, GenericPO cartera, MInvoice facturaPrincipal, List<MInvoice> facturasInteres) {
    BigDecimal saldoTotalPendiente = facturaPrincipal.getOpenAmt();
    facturasInteres.each { saldoTotalPendiente += it.getOpenAmt(); };
    
    if (pago.getPayAmt().compareTo(saldoTotalPendiente) > 0) {
        cartera.set_ValueOfColumn("anticipado", "POTENCIAL");
        cartera.saveEx(g_TrxName);
        logProcess("⚠️ Marcamos cartera (ID: ${cartera.get_ID()}) como posible pago anticipado.");
        throw new AdempiereException("El monto del pago (${pago.getPayAmt()}) excede el saldo total pendiente (${saldoTotalPendiente}).");
    }

    BigDecimal montoTotalPago = pago.getPayAmt();
    BigDecimal tasa = cartera.get_Value("tasa") ?: BigDecimal.ZERO;
    BigDecimal montoInteres = montoTotalPago.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
    BigDecimal montoCapital = montoTotalPago.subtract(montoInteres);
    
    logProcess("    -> INFO: Lógica de prorrateo. Capital: ${montoCapital}, Interés: ${montoInteres}.");

    MAllocationHdr allocHdr = new MAllocationHdr(g_Ctx, 0, g_TrxName);
    allocHdr.setAD_Org_ID(pago.getAD_Org_ID());
    allocHdr.setDateAcct(g_Today);
    allocHdr.setDateTrx(g_Today);
    allocHdr.setC_Currency_ID(pago.getC_Currency_ID());
    allocHdr.setC_DocType_ID(ALLOCATION_DOCTYPE_ID);
    allocHdr.setDescription("Asignación con prorrateo (Capital: ${montoCapital}, Interés: ${montoInteres}) para Pago ${pago.getDocumentNo()}");
    allocHdr.saveEx();

    BigDecimal capitalPendiente = montoCapital;
    BigDecimal saldoPrincipal = facturaPrincipal.getOpenAmt();
    if (saldoPrincipal.compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal montoAAsignar = capitalPendiente.min(saldoPrincipal);
        if (montoAAsignar.compareTo(BigDecimal.ZERO) > 0) {
            MAllocationLine capLine = new MAllocationLine(allocHdr);
            capLine.setC_Payment_ID(pago.get_ID());
            capLine.setC_Invoice_ID(facturaPrincipal.get_ID());
            capLine.setAmount(montoAAsignar);
            capLine.saveEx();
            capitalPendiente = capitalPendiente.subtract(montoAAsignar);
        }
    }

    BigDecimal interesPendiente = montoInteres;
    if (!facturasInteres.isEmpty()) {
        for (MInvoice factInt in facturasInteres) {
            if (interesPendiente.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal saldoInteres = factInt.getOpenAmt();
            if (saldoInteres.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal montoAAsignar = interesPendiente.min(saldoInteres);
                if (montoAAsignar.compareTo(BigDecimal.ZERO) > 0) {
                    MAllocationLine intLine = new MAllocationLine(allocHdr);
                    intLine.setC_Payment_ID(pago.get_ID());
                    intLine.setC_Invoice_ID(factInt.get_ID());
                    intLine.setAmount(montoAAsignar);
                    intLine.saveEx();
                    interesPendiente = interesPendiente.subtract(montoAAsignar);
                }
            }
        }
    }
    
    if (capitalPendiente.compareTo(BigDecimal.ZERO) > 0) {
        for (MInvoice factInt in facturasInteres) {
            if (capitalPendiente.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal saldoInteres = factInt.getOpenAmt();
            if (saldoInteres.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal montoAAsignar = capitalPendiente.min(saldoInteres);
                if (montoAAsignar.compareTo(BigDecimal.ZERO) > 0) {
                    MAllocationLine remLine = new MAllocationLine(allocHdr);
                    remLine.setC_Payment_ID(pago.get_ID());
                    remLine.setC_Invoice_ID(factInt.get_ID());
                    remLine.setAmount(montoAAsignar);
                    remLine.saveEx();
                    capitalPendiente = capitalPendiente.subtract(montoAAsignar);
                }
            }
        }
    }
    
    if (interesPendiente.compareTo(BigDecimal.ZERO) > 0) {
        if (facturaPrincipal.getOpenAmt().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal montoAAsignar = interesPendiente.min(facturaPrincipal.getOpenAmt());
            if (montoAAsignar.compareTo(BigDecimal.ZERO) > 0) {
                MAllocationLine remLine = new MAllocationLine(allocHdr);
                remLine.setC_Payment_ID(pago.get_ID());
                remLine.setC_Invoice_ID(facturaPrincipal.get_ID());
                remLine.setAmount(montoAAsignar);
                remLine.saveEx();
            }
        }
    }
    
    if (!allocHdr.processIt(DocAction.ACTION_Complete)) {
        throw new AdempiereException("Error al completar la asignación: " + allocHdr.getProcessMsg());
    }
    allocHdr.saveEx();
    logProcess("    -> Asignación ${allocHdr.getDocumentNo()} creada y completada.");
}

boolean procesarCobroIndividual(GenericPO cobro, int workNumber) {
    int cobroID = cobro.get_ID();
    if (cobro.get_ValueAsInt("local_id") > 0) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: ya tiene un local_id.");
        return false;
    }
    MBPartner bp = new Query(g_Ctx, "C_BPartner", "C_BPartner_ID = ?", g_TrxName).setParameters([cobro.get_Value("c_bpartner_id")]).first();
    if (bp == null || bp.getLocations(false).length == 0) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: Socio de Negocio no válido.");
        return false;
    }
    MUser usr = MUser.get(g_Ctx, cobro.getCreatedBy());
    if (usr == null) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: Usuario no encontrado.");
        return false;
    }
    if (usr.get_ValueAsInt("C_BankAccount_ID") <= 0) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: Usuario '${usr.getName()}' no tiene cuenta bancaria.");
        return false;
    }
    GenericPO car = new Query(g_Ctx, "legacy_cartera", "legacy_cartera_id = ?", g_TrxName).setParameters([cobro.get_ValueAsInt("id_cartera")]).first();
    if (car == null) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: Cartera asociada no encontrada.");
        return false;
    }
    
    // --- LÓGICA AÑADIDA: PASO 1 ---
    // Guardar el estado inicial de la cartera
    boolean esAnticipadoPotencial = "POTENCIAL".equals(car.get_ValueAsString("anticipado"));

    int invoiceID = car.get_ValueAsInt("local_id");
    if (invoiceID <= 0) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: Cartera sin factura principal asociada.");
        return false;
    }
    MInvoice facturaPrincipal = new MInvoice(g_Ctx, invoiceID, g_TrxName);
    if (facturaPrincipal.get_ID() == 0) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: No se encontró factura principal ID ${invoiceID}.");
        return false;
    }

    List<MInvoice> facturasInteres = getFacturasDeInteresPendientes(car);
    if (facturaPrincipal.isPaid() && facturasInteres.isEmpty()) {
        logProcess("⏭️ [${workNumber}] Se omite Cobro ID ${cobroID}: Factura principal ${facturaPrincipal.getDocumentNo()} ya pagada y no hay facturas de interés");
        cobro.set_ValueOfColumn("synced", "Y"); cobro.set_ValueOfColumn("Description", "Omitido por factura ya pagada."); cobro.saveEx();
        return false;
    }

    logProcess("⚙️ [${workNumber}] Procesando Cobro ID ${cobroID} para ${bp.getName()}...");
    MOrg org = MOrg.get(g_Ctx, cobro.getAD_Org_ID());
    MPayment pago = crearPago(cobro, usr, org);
    crearAsignacion(pago, car, facturaPrincipal, facturasInteres);
    cobro.set_ValueOfColumn("synced", "Y");
    cobro.set_ValueOfColumn("local_id", new BigDecimal(pago.get_ID()));
    cobro.saveEx();

    // --- LÓGICA AÑADIDA: PASO 2 ---
    // Si era 'POTENCIAL' y todo salió bien, la marcamos como 'PROCESADO'
    if (esAnticipadoPotencial) {
        car.set_ValueOfColumn("anticipado", "PROCESADO");
        car.saveEx();
        logProcess("    -> INFO: Cartera ${car.get_ID()} marcada como 'PROCESADO' por pago anticipado.");
    }
    
    logProcess("✔️ [${workNumber}] OK: Cobro ID ${cobroID} procesado como Pago ${pago.getDocumentNo()}.");
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

    logProcess("✅ Iniciando proceso de sincronización de cobros (Modo Optimizado)...");
    
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