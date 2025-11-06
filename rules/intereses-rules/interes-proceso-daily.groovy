/**
 * InteresProcesoDaily
 * Proceso para generar facturas de interés basadas en el plan de pagos (legacy_schedule).
 * 20251106 - agregado recálculo de cuotas basado en saldo capital antes de generar facturas
 * 20250928 - agregamos ref_invoice_id a legacy_schedule para referencia futura
 * Versión: 20251106
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
@Field int recalculatedCount = 0;

def logProcess(String message) {
    A_ProcessInfo.addLog(0, null, null, message);
    log.info(message);
}

/**
 * Obtiene el saldo capital de una cartera a una fecha específica
 * utilizando la función invoiceopentodate de la base de datos.
 * 
 * @param capitalInvoiceID ID de la factura de capital (legacy_cartera.local_id)
 * @param processDate Fecha a la que se calcula el saldo
 * @return Saldo capital pendiente a la fecha indicada
 */
BigDecimal getSaldoCapital(int capitalInvoiceID, Timestamp processDate) {
    if (capitalInvoiceID <= 0) {
        return BigDecimal.ZERO;
    }
    
    if (processDate == null) {
        throw new AdempiereException("processDate no puede ser null al calcular saldo capital.");
    }
    
    String sql = "SELECT invoiceopentodate(?, NULL, ?)";
    BigDecimal saldo = DB.getSQLValueBD(A_TrxName, sql, capitalInvoiceID, processDate);
    
    if (saldo == null) {
        saldo = BigDecimal.ZERO;
    }
    
    return saldo;
}

/**
 * Recalcula las cuotas pendientes de una cartera basándose en el saldo capital actual.
 * Similar al método crearCuotasPagoFlat pero calculando desde el saldo capital remanente.
 * 
 * @param carteraID ID de la cartera (legacy_cartera_id)
 * @param saldoCapital Saldo de capital pendiente a la fecha del proceso
 * @param processDate Fecha del proceso
 * @return true si se recalculó exitosamente, false en caso contrario
 */
boolean recalcularCuotasDesdeCapital(int carteraID, BigDecimal saldoCapital, Timestamp processDate) {
    try {
        // Obtener información de la cartera
        GenericPO cartera = new Query(A_Ctx, "legacy_cartera", "legacy_cartera_id = ?", A_TrxName)
            .setParameters(carteraID)
            .first();
        
        if (cartera == null) {
            logProcess("⚠️ ADVERTENCIA: No se encontró la cartera con ID ${carteraID}.");
            return false;
        }
        
        // Obtener la tasa mensual y calcular tasa diaria
        BigDecimal tasaMensual = cartera.get_Value('tasa') ?: BigDecimal.ZERO;
        BigDecimal tasaDiaria = tasaMensual.divide(BigDecimal.valueOf(30), 10, java.math.RoundingMode.HALF_UP);
        
        // Obtener cuotas pendientes de esta cartera ordenadas por fecha de vencimiento
        String sqlPendientes = """
            SELECT legacy_schedule_id, DueDate, DueAmt 
            FROM legacy_schedule 
            WHERE legacy_cartera_id = ? 
              AND (Processed IS NULL OR Processed = 'N') 
              AND IsActive = 'Y'
              AND DueDate >= ?
            ORDER BY DueDate ASC
        """;
        
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        List<Map<String, Object>> cuotasPendientes = new ArrayList<>();
        
        try {
            pstmt = DB.prepareStatement(sqlPendientes, A_TrxName);
            pstmt.setInt(1, carteraID);
            pstmt.setTimestamp(2, processDate);
            rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> cuota = new HashMap<>();
                cuota.put("id", rs.getInt("legacy_schedule_id"));
                cuota.put("dueDate", rs.getTimestamp("DueDate"));
                cuota.put("dueAmt", rs.getBigDecimal("DueAmt"));
                cuotasPendientes.add(cuota);
            }
        } finally {
            DB.close(rs, pstmt);
        }
        
        if (cuotasPendientes.isEmpty()) {
            logProcess("ℹ️ No hay cuotas pendientes para recalcular en cartera ID ${carteraID}.");
            return true;
        }
        
        int numCuotas = cuotasPendientes.size();
        
        // Calcular monto total de cuotas pendientes (capital + interés estimado)
        // NOTA: Esta es una estimación simplificada para calcular la cuota fija.
        // El interés real se calcula precisamente en el bucle sobre saldo decreciente.
        // Estimación: interés ≈ saldoCapital × tasaDiaria × numCuotas
        // Esta aproximación es suficiente porque el interés se ajusta en cada iteración.
        BigDecimal interesEstimado = saldoCapital.multiply(tasaDiaria).multiply(BigDecimal.valueOf(numCuotas));
        BigDecimal montoTotalEstimado = saldoCapital.add(interesEstimado);
        
        // Calcular cuota fija
        BigDecimal cuotaTotal = montoTotalEstimado.divide(BigDecimal.valueOf(numCuotas), 4, java.math.RoundingMode.HALF_UP);
        BigDecimal saldoPendiente = saldoCapital;
        
        logProcess("♻️ Recalculando ${numCuotas} cuotas para cartera ID ${carteraID}. Saldo capital: ${saldoCapital}, Tasa diaria: ${tasaDiaria}");
        
        // Recalcular cada cuota
        for (Map<String, Object> cuotaData : cuotasPendientes) {
            int scheduleID = (int) cuotaData.get("id");
            
            // Calcular interés del día basado en saldo pendiente
            BigDecimal interesDelDia = saldoPendiente.multiply(tasaDiaria);
            interesDelDia = interesDelDia.setScale(4, java.math.RoundingMode.HALF_UP);
            
            // Asegurar que el interés no sea negativo
            if (interesDelDia.compareTo(BigDecimal.ZERO) < 0) {
                interesDelDia = BigDecimal.ZERO;
            }
            
            // Capital pagado en esta cuota
            BigDecimal capitalDelDia = cuotaTotal.subtract(interesDelDia);
            
            // Validar que el capital no sea negativo
            if (capitalDelDia.compareTo(BigDecimal.ZERO) < 0) {
                // Si el interés excede la cuota total, ajustar
                interesDelDia = cuotaTotal;
                capitalDelDia = BigDecimal.ZERO;
            }
            
            // Si el capital a pagar excede el saldo pendiente, ajustar para última cuota
            if (capitalDelDia.compareTo(saldoPendiente) > 0) {
                capitalDelDia = saldoPendiente;
            }
            
            // Actualizar saldo pendiente
            saldoPendiente = saldoPendiente.subtract(capitalDelDia);
            if (saldoPendiente.compareTo(BigDecimal.ZERO) < 0) {
                saldoPendiente = BigDecimal.ZERO;
            }
            
            // Actualizar la cuota en la base de datos
            String updateSQL = "UPDATE legacy_schedule SET DueAmt = ? WHERE legacy_schedule_id = ?";
            DB.executeUpdate(updateSQL, new Object[]{interesDelDia, scheduleID}, false, A_TrxName);
        }
        
        logProcess("✅ Recalculadas ${numCuotas} cuotas para cartera ID ${carteraID}.");
        recalculatedCount++;
        return true;
        
    } catch (Exception e) {
        log.log(Level.SEVERE, "Error recalculando cuotas para cartera ID ${carteraID}.", e);
        logProcess("❌ ERROR recalculando cuotas para cartera ID ${carteraID}: " + e.getMessage());
        return false;
    }
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
            log.log(Level.SEVERE, "Unknown Parameter: " + name);
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
    
    // ==========================================================================
    //    PASO 1: RECALCULAR CUOTAS BASADAS EN SALDO CAPITAL
    // ==========================================================================
    logProcess("📊 Iniciando recálculo de cuotas basado en saldo capital...");
    
    // Obtener carteras únicas que tienen cuotas pendientes hoy
    Set<Integer> carterasARecalcular = new HashSet<>();
    for (int scheduleID in scheduleIDs) {
        GenericPO schedule = new Query(A_Ctx, "legacy_schedule", "legacy_schedule_id = ?", A_TrxName)
            .setParameters(scheduleID)
            .first();
        
        if (schedule != null) {
            int carteraID = schedule.get_ValueAsInt("legacy_cartera_id");
            if (carteraID > 0) {
                carterasARecalcular.add(carteraID);
            }
        }
    }
    
    logProcess("📋 Se encontraron ${carterasARecalcular.size()} carteras únicas para recalcular.");
    
    // Recalcular cada cartera
    for (int carteraID : carterasARecalcular) {
        // Obtener la factura de capital desde legacy_cartera.local_id
        GenericPO cartera = new Query(A_Ctx, "legacy_cartera", "legacy_cartera_id = ?", A_TrxName)
            .setParameters(carteraID)
            .first();
        
        if (cartera == null) {
            logProcess("⏭️ Se omite cartera ID ${carteraID}: no se pudo cargar.");
            continue;
        }
        
        int capitalInvoiceID = cartera.get_ValueAsInt("local_id");
        
        if (capitalInvoiceID <= 0) {
            logProcess("⚠️ ADVERTENCIA: Cartera ID ${carteraID} no tiene factura de capital (local_id no válido).");
            continue;
        }
        
        // Calcular saldo capital usando invoiceopentodate
        BigDecimal saldoCapital = getSaldoCapital(capitalInvoiceID, today);
        
        logProcess("💰 Cartera ID ${carteraID}: Factura capital ID ${capitalInvoiceID}, Saldo: ${saldoCapital}");
        
        // Recalcular cuotas si hay saldo pendiente
        if (saldoCapital.compareTo(BigDecimal.ZERO) > 0) {
            recalcularCuotasDesdeCapital(carteraID, saldoCapital, today);
        } else {
            logProcess("ℹ️ Cartera ID ${carteraID} no tiene saldo capital pendiente. No se recalcula.");
        }
    }
    
    logProcess("✅ Recálculo completado. Carteras recalculadas: ${recalculatedCount}");
    
    // ==========================================================================
    //    PASO 2: OBTENER CUOTAS PENDIENTES ACTUALIZADAS Y GENERAR FACTURAS
    // ==========================================================================
    logProcess("🔄 Obteniendo cuotas actualizadas para generar facturas...");
    
    // Volver a obtener las cuotas pendientes después del recálculo
    scheduleIDs = getPendingScheduleIDs(today);
    
    if (scheduleIDs.isEmpty()) {
        result = "Proceso finalizado. No se encontraron cuotas para procesar después del recálculo.";
        logProcess(result);
        return result;
    }
    
    logProcess("🔍 Se encontraron ${scheduleIDs.size()} cuotas actualizadas para generar facturas.");

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

    result = "Proceso finalizado. Carteras recalculadas: ${recalculatedCount}, Facturas creadas: ${processedCount}, Omitidas: ${skippedCount}.";

} catch (Exception e) {
    log.log(Level.SEVERE, "Error fatal durante la generación de facturas de interés.", e);
    result = "❌ ERROR: " + e.getMessage();
    throw new AdempiereException(result, e);
}

logProcess("🏁 " + result);
return result;