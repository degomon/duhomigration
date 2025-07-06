/**
 * CarteraBridgeNativeAutocomplete
 * Proceso para sincronizar legacy_cartera con C_Invoice y C_Payment de forma masiva.
 * Solo para legacy_cartera de tipo native.
 * 20250526 - Refactorizado para usar funciones y registrar solo monto principal (basado en CarteraBridgeNativeSingle).
 * 20220201 - payment_id field added
 * 20210812 - First version
 * Rule: groovy:CarteraBridgeNativeAutocomplete
 * Class: @script:groovy:CarteraBridgeNativeAutocomplete
**/

import org.compiere.model.MTable;
import org.compiere.util.DB;
import org.compiere.util.Msg;
import org.adempiere.model.GenericPO;
import org.compiere.model.Query;
import org.compiere.model.MOrg;
import org.compiere.model.MBPartner;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MInvoice;
import org.compiere.model.MUser;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MPayment;
import org.compiere.model.MBankAccount;
import org.compiere.model.PO;
import org.compiere.util.Env;
import org.compiere.util.CLogger;
import org.compiere.util.TimeUtil;
import java.util.logging.Level;
import org.compiere.process.ProcessInfoParameter;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.process.DocAction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Calendar;

CLogger log = CLogger.getCLogger(GenericPO.class);

//==========================================================================
//    FUNCIONES AUXILIARES (extraídas de CarteraBridgeNativeSingle)
//==========================================================================

/**
 * Verifica si una fecha cae en domingo.
 * @param fecha La fecha a verificar.
 * @return true si es domingo, false en caso contrario.
 */
def esDomingo(Date fecha) {
    Calendar cal = Calendar.getInstance();
    cal.setTime(fecha);
    return cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
}

/**
 * Crea el plan de pagos (schedule) para una factura de financiamiento.
 * Elimina cualquier plan existente para esa cartera antes de crear el nuevo.
 * @param invoice La factura recién creada.
 * @param cartera El registro de legacy_cartera.
 * @param numCuotas El número de cuotas a crear.
 * @param tasa La tasa de interés (informativo, no se usa en cálculo flat).
 * @param cuotaNivelada El monto de la cuota nivelada (capital).
 * @param monto El monto principal del crédito.
 * @param montoTotal El monto total a pagar (capital + interés).
 * @param totalInteres El monto total de interés.
 */
def crearCuotasPagoFlat = { MInvoice invoice, GenericPO cartera, int numCuotas, 
                    BigDecimal tasa, BigDecimal cuotaNivelada, BigDecimal monto, 
                    BigDecimal montoTotal, BigDecimal totalInteres ->
    String trxName = invoice.get_TrxName();
    int carteraID = cartera.get_ValueAsInt("legacy_cartera_ID");
    
    // El interés se divide entre las cuotas para registrarlo en el schedule
    BigDecimal interesARegistrar = totalInteres.divide(BigDecimal.valueOf(numCuotas), 10, RoundingMode.HALF_UP);

    A_ProcessInfo.addLog(0, null, null, 
        "Creando schedule para invoice ${invoice.getDocumentNo()}, numCuotas=${numCuotas}, " +
        "montoTotal=${montoTotal}, totalInteres=${totalInteres}");

    // --- Lógica para borrar cuotas existentes ---
    log.info("Buscando y eliminando cuotas existentes para legacy_cartera_ID=${carteraID}");
    String sqlDelete = "DELETE FROM legacy_schedule WHERE legacy_cartera_ID = ?";
    int deletedRows = DB.executeUpdate(sqlDelete, [carteraID] as Object[], true, trxName);
    if (deletedRows > 0) {
        log.warning("Se eliminaron ${deletedRows} cuotas antiguas para la cartera ID ${carteraID}.");
    }
    
    Date fechaBase = cartera.get_Value("fecha");
    Date fechaCuota = TimeUtil.addDays(fechaBase, 1);
    for (int i = 0; i < numCuotas; i++) {
        
        fechaCuota = TimeUtil.addDays(fechaCuota, 1);
        if( esDomingo(fechaCuota)) {
            fechaCuota = TimeUtil.addDays(fechaCuota, 1);
        }
        
        MTable scheduleTable = MTable.get(Env.getCtx(), "legacy_schedule");
        PO cuota = scheduleTable.getPO(0, trxName);
        
        cuota.set_ValueOfColumn("AD_Client_ID", invoice.getAD_Client_ID());
        cuota.set_ValueOfColumn("AD_Org_ID", invoice.getAD_Org_ID());
        cuota.set_ValueOfColumn("C_Invoice_ID", invoice.getC_Invoice_ID());
        cuota.set_ValueOfColumn("legacy_cartera_ID", carteraID); 
        cuota.set_ValueOfColumn("DueAmt", cuotaNivelada.add(interesARegistrar));
        cuota.set_ValueOfColumn("DueDate", fechaCuota);
        cuota.set_ValueOfColumn("IsActive", "Y");
        cuota.saveEx(trxName);
        
    } // fin del bucle de cuotas
} // fin de crearCuotasPagoFlat

//==========================================================================
//    LÓGICA PRINCIPAL DEL PROCESO
//==========================================================================

try {
    log.info("Iniciando el proceso masivo CarteraBridgeNativeAutocomplete");

    List<GenericPO> legacyCartera = new Query(A_Ctx, "legacy_cartera",
        " (synced != 'Y' or synced is null) and origen = 'native' and aprobado='Y' ", A_TrxName)
        .setOrderBy("created")
        .list();

    if (legacyCartera.isEmpty()) {
        result = "No se encontraron registros de cartera pendientes por procesar.";
        log.info(result);
        return result;
    }

    log.info("Se encontraron ${legacyCartera.size()} registros de cartera para procesar.");
    int workNumber = 0;

    for (GenericPO car in legacyCartera) {
        workNumber++;
        log.info("Procesando [${workNumber} de ${legacyCartera.size()}] Cartera ID: ${car.get_ID()}");

        // Validamos que la cartera no haya sido procesada en una corrida anterior
        int currentLocalID = car.get_ValueAsInt("local_id");
        if (currentLocalID > 0) {
            log.warning("Cartera ID ${car.get_ID()} ya fue procesada (local_id=${currentLocalID}). Se omite.");
            continue;
        }

        MOrg org = MOrg.get(A_Ctx, car.get_ValueAsInt("AD_Org_ID"));
        if (org == null) {
            log.severe("Error en Cartera ID ${car.get_ID()}: No existe la Org ${car.get_ValueAsInt("AD_Org_ID")}. Se omite.");
            continue;
        }

        MBPartner bp = new Query(A_Ctx, "C_BPartner", "C_BPartner_ID = ?", A_TrxName)
            .setParameters([car.get_Value("c_bpartner_id")])
            .first();
            
        if (bp == null || bp.getLocations(false).size() == 0) {
            log.severe("Error en Cartera ID ${car.get_ID()}: No se encontró un Socio de Negocio válido o no tiene dirección. Se omite.");
            continue;
        }
        
        // --- Variables del Crédito ---
        Integer loc_id = bp.getLocations(false)[0].get_ID();
        BigDecimal tasa = car.get_Value("tasa");
        BigDecimal monto = (BigDecimal) car.get_Value("monto");
        BigDecimal montoTotal = (BigDecimal) car.get_Value("montototal");
        BigDecimal totalInteres = (BigDecimal) car.get_Value("valorinteres");
        int cantidadCuotas = car.get_Value("dias_cre");
      
        if (tasa == null || monto == null || montoTotal == null || totalInteres == null || cantidadCuotas <= 0) {
            log.severe("Error en Cartera ID ${car.get_ID()}: Valores de crédito inválidos (monto, tasa, etc.). Se omite.");
            continue;
        }

        // --- Validación de Usuario y Cuenta Bancaria ---
        MUser usr = MUser.get(A_Ctx, car.getCreatedBy());
        if (usr == null) {
            log.severe("Error en Cartera ID ${car.get_ID()}: No se pudo encontrar el usuario creador con ID: ${car.getCreatedBy()}. Se omite.");
            continue;
        }
        int bank_account_id = usr.get_ValueAsInt("C_BankAccount_ID");
        if (bank_account_id <= 0) {
            log.severe("Error en Cartera ID ${car.get_ID()}: El usuario '${usr.getName()}' no tiene una cuenta bancaria configurada. Se omite.");
            continue;
        }
        
        // --- Creación de Factura (Nota de Débito por Financiamiento) ---
        int payment_term_id = 1000000; // Inmediato
        if (cantidadCuotas == 23) payment_term_id = 1000001;
        if (cantidadCuotas == 30) payment_term_id = 1000002;
        if (cantidadCuotas == 50) payment_term_id = 1000003;

        int Nota_C_DocType_ID = 1000048; // Financiamiento
        MInvoice invoice = new MInvoice(A_Ctx, 0, A_TrxName);
        invoice.setAD_Org_ID(org.get_ID());
        invoice.setDateAcct(car.get_Value("fecha"));
        invoice.setDateInvoiced(car.get_Value("fecha"));
        invoice.setC_DocTypeTarget_ID(Nota_C_DocType_ID);
        invoice.setC_BPartner_ID(bp.get_ID());
        invoice.setC_BPartner_Location_ID(loc_id);
        invoice.setC_PaymentTerm_ID(payment_term_id);
        invoice.setSalesRep_ID(1000000); 
        invoice.setM_PriceList_ID(1000001);
        invoice.setIsSOTrx(true);
        invoice.set_ValueOfColumn("legacy_id", car.get_ValueAsInt("legacy_cartera_id"));
        invoice.saveEx(A_TrxName);

        // --- Línea de Factura ÚNICA para el Monto Principal ---
        MInvoiceLine iLine = new MInvoiceLine(invoice);
        iLine.setC_Charge_ID(1000028); // Cargo: Monto Principal
        iLine.setQty(BigDecimal.ONE);
        iLine.setPrice(monto);
        iLine.saveEx(A_TrxName);

        invoice.processIt(DocAction.ACTION_Complete);
        invoice.saveEx(A_TrxName);

        // --- Creación de Pago (Desembolso) ---
        int Payment_C_DocType_ID = 1000049;
        MPayment mp = new MPayment(A_Ctx, 0, A_TrxName);
        mp.setAD_Org_ID(org.get_ID());
        mp.setDescription("Desembolso por Financiamiento " + invoice.getDocumentNo());
        mp.setC_BPartner_ID(bp.get_ID());
        mp.setDateAcct(car.get_Value("fecha"));
        mp.setDateTrx(car.get_Value("fecha"));
        mp.setC_BankAccount_ID(bank_account_id); 
        mp.setTenderType("X"); // Tipo de Entrega: Cheque
        mp.setPayAmt(monto);
        mp.setC_Currency_ID(209);
        mp.setC_Charge_ID(1000030); // Cargo: Desembolso Crédito
        mp.setC_DocType_ID(Payment_C_DocType_ID);
        mp.saveEx(A_TrxName);
        
        mp.processIt(DocAction.ACTION_Complete);
        mp.saveEx(A_TrxName);

        // --- Actualización del registro de Cartera ---
        car.set_ValueOfColumn("synced", "Y");
        car.set_ValueOfColumn("local_id", invoice.get_ID());
        car.set_ValueOfColumn("payment_id", mp.get_ID());
        car.saveEx(A_TrxName);

        // --- Creación del Plan de Pagos (Schedule) ---
        BigDecimal cuotaNivelada = montoTotal.divide(BigDecimal.valueOf(cantidadCuotas), 10, RoundingMode.HALF_UP)
                                      .subtract(totalInteres.divide(BigDecimal.valueOf(cantidadCuotas), 10, RoundingMode.HALF_UP));
        
        crearCuotasPagoFlat(invoice, car, cantidadCuotas, tasa, cuotaNivelada, monto, montoTotal, totalInteres);

        // --- Log del resultado de esta iteración ---
        A_ProcessInfo.addLog(0, null, null, "OK [${workNumber}]: Cartera ${car.get_ID()}, Factura ${invoice.getDocumentNo()}, Pago ${mp.getDocumentNo()}, Socio ${bp.getName()}");
    }

    result = "Proceso finalizado. Se procesaron ${workNumber} de ${legacyCartera.size()} registros encontrados.";

} catch (Exception e) {
    result = "ERROR: Excepción fatal en el proceso masivo: " + e.getMessage();
    log.log(Level.SEVERE, "Error en CarteraBridgeNativeAutocomplete.", e);
    //throw new AdempiereException(result, e); // Descomentar para detener el proceso por completo en caso de error.
}

return result;