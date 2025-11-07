/**
 * CarteraBridgeNativeAutocomplete
 * Proceso para sincronizar legacy_cartera con C_Invoice y C_Payment de forma masiva.
 * 20251106 - cálculo de interés diario basado en saldo pendiente según fórmula Excel
 * 20250927 - corrección en monto de schedule interés
 * Versión: 20250705 (Final y Corregido)
 */
import org.compiere.model.Query
import org.compiere.model.MOrg
import org.compiere.model.MBPartner
import org.compiere.model.MInvoice
import org.compiere.model.MUser
import org.compiere.model.MInvoiceLine
import org.compiere.model.MPayment
import org.compiere.model.PO
import org.compiere.model.MTable
import org.compiere.util.DB
import org.compiere.util.Env
import org.compiere.util.CLogger
import org.compiere.util.TimeUtil
import org.compiere.process.DocAction
import org.adempiere.exceptions.AdempiereException
import org.adempiere.model.GenericPO
import org.compiere.process.ProcessInfo
import org.compiere.util.Trx // <-- IMPORTACIÓN AÑADIDA

import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Timestamp
import java.util.Date
import java.util.Calendar
import java.util.logging.Level

// ==========================================================================
//    CONFIGURACIÓN
// ==========================================================================
final int NOTA_DOCTYPE_ID = 1000048
final int PAGO_DOCTYPE_ID = 1000049
final int CHARGE_PRINCIPAL_ID = 1000028
final int CHARGE_DESEMBOLSO_ID = 1000030
final int CURRENCY_ID = 209
final int SALES_REP_ID = 1000000
final int PRICE_LIST_ID = 1000001
final int TAX_ID = 1000000

// ==========================================================================
//    CAMPOS Y FUNCIONES HELPER
// ==========================================================================
CLogger log = CLogger.getCLogger('CarteraBridgeDebug')
int processedCount = 0
int skippedCount = 0

def esDomingo = { Date fecha ->
    Calendar cal = Calendar.getInstance()
    cal.setTime(fecha)
    return cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
}

def calcularTasaOculta = { BigDecimal monto, BigDecimal interesTotal, int numCuotas ->
    /**
     * Calcula la tasa diaria "oculta" que produce un interés total fijo exacto
     * usando el método de interés sobre saldo con cuotas niveladas.
     * 
     * @param monto Capital del préstamo
     * @param interesTotal Interés total deseado (fijo)
     * @param numCuotas Número de cuotas/días
     * @return Tasa diaria oculta que produce el interés total exacto
     */
    if (monto.compareTo(BigDecimal.ZERO) <= 0 || interesTotal.compareTo(BigDecimal.ZERO) <= 0 || numCuotas <= 0) {
        return BigDecimal.ZERO
    }
    
    BigDecimal pagoTotal = monto.add(interesTotal)
    BigDecimal cuotaNivelada = pagoTotal.divide(BigDecimal.valueOf(numCuotas), 10, RoundingMode.HALF_UP)
    
    // Búsqueda binaria de la tasa diaria
    // Rango típico: 0.1% a 2% diario (0.001 - 0.020)
    // Cubre la mayoría de escenarios de préstamos a corto plazo
    BigDecimal tasaMin = BigDecimal.valueOf(0.001)
    BigDecimal tasaMax = BigDecimal.valueOf(0.020)
    BigDecimal tolerancia = BigDecimal.valueOf(0.0001)
    int maxIteraciones = 1000
    
    for (int iter = 0; iter < maxIteraciones; iter++) {
        BigDecimal tasaDiaria = tasaMin.add(tasaMax).divide(BigDecimal.valueOf(2), 10, RoundingMode.HALF_UP)
        
        BigDecimal saldo = monto
        BigDecimal totalInteresCalculado = BigDecimal.ZERO
        
        // Calcular interés total con esta tasa (días 1 a numCuotas-1)
        for (int dia = 1; dia < numCuotas; dia++) {
            BigDecimal interes = saldo.multiply(tasaDiaria)
            BigDecimal capital = cuotaNivelada.subtract(interes)
            saldo = saldo.subtract(capital)
            totalInteresCalculado = totalInteresCalculado.add(interes)
        }
        
        // Último día: interés sobre saldo restante
        BigDecimal interesUltimoDia = saldo.multiply(tasaDiaria)
        totalInteresCalculado = totalInteresCalculado.add(interesUltimoDia)
        
        // Verificar convergencia
        BigDecimal diferencia = totalInteresCalculado.subtract(interesTotal).abs()
        if (diferencia.compareTo(tolerancia) < 0) {
            return tasaDiaria
        }
        
        // Ajustar rango de búsqueda
        if (totalInteresCalculado.compareTo(interesTotal) < 0) {
            tasaMin = tasaDiaria
        } else {
            tasaMax = tasaDiaria
        }
    }
    
    // Si no converge, retornar el valor actual
    return tasaMin.add(tasaMax).divide(BigDecimal.valueOf(2), 10, RoundingMode.HALF_UP)
}

def crearCuotasPagoFlat = { ProcessInfo pi, MInvoice invoice, GenericPO cartera, int numCuotas, BigDecimal montoTotal ->
    String trxName = invoice.get_TrxName()
    int carteraID = cartera.get_ValueAsInt('legacy_cartera_ID')
    BigDecimal monto = cartera.get_Value('monto') ?: BigDecimal.ZERO
    
    // Calcular el interés total fijo basado en montoTotal
    BigDecimal interesTotal = montoTotal.subtract(monto)
    
    // Calcular la tasa diaria "oculta" que produce exactamente este interés total
    BigDecimal tasaDiariaOculta = calcularTasaOculta(monto, interesTotal, numCuotas)
    
    pi.addLog(0, null, null, "    -> Creando schedule para Invoice ${invoice.getDocumentNo()}, Cuotas: ${numCuotas}, Interés Total: ${interesTotal}, Tasa Diaria Oculta: ${tasaDiariaOculta} Org de Cartera: ${cartera.getAD_Org_ID()} ")

    DB.executeUpdate('DELETE FROM legacy_schedule WHERE legacy_cartera_ID = ?', [carteraID] as Object[], true, trxName)

    Date fechaBase = cartera.get_Value('fecha')
    Date fechaCuota = TimeUtil.addDays(fechaBase, 1)
    
    // Calcular cuota total fija (capital + interés) para el plan
    BigDecimal cuotaTotal = montoTotal.divide(BigDecimal.valueOf(numCuotas), 4, RoundingMode.HALF_UP)
    BigDecimal saldoPendiente = monto

    for (int i = 0; i < numCuotas; i++) {
        if (esDomingo(fechaCuota)) {
            fechaCuota = TimeUtil.addDays(fechaCuota, 1)
        }

        // Calcular interés diario basado en saldo pendiente usando la tasa oculta
        // Fórmula: Interés del día = Saldo × Tasa diaria oculta
        BigDecimal interesDelDia = saldoPendiente.multiply(tasaDiariaOculta)
        
        // Redondear a 4 decimales
        interesDelDia = interesDelDia.setScale(4, RoundingMode.HALF_UP)
        
        // Asegurar que el interés no sea negativo
        if (interesDelDia.compareTo(BigDecimal.ZERO) < 0) {
            interesDelDia = BigDecimal.ZERO
        }
        
        // Capital pagado en esta cuota
        BigDecimal capitalDelDia = cuotaTotal.subtract(interesDelDia)
        
        // Validar que el capital no sea negativo (puede ocurrir si el interés excede la cuota)
        if (capitalDelDia.compareTo(BigDecimal.ZERO) < 0) {
            // Si el interés excede la cuota total, ajustar: la cuota es solo interés
            interesDelDia = cuotaTotal
            capitalDelDia = BigDecimal.ZERO
        }
        
        // Ajuste especial para el último día: pagar todo el saldo restante
        if (i == numCuotas - 1) {
            // Recalcular interés del último día basado en saldo actual
            interesDelDia = saldoPendiente.multiply(tasaDiariaOculta)
            interesDelDia = interesDelDia.setScale(4, RoundingMode.HALF_UP)
            // Pagar todo el saldo restante como capital
            // Nota: La cuota del último día será interesDelDia + capitalDelDia (saldo completo)
            capitalDelDia = saldoPendiente
        } else {
            // Si el capital a pagar excede el saldo pendiente en días intermedios, ajustar
            if (capitalDelDia.compareTo(saldoPendiente) > 0) {
                capitalDelDia = saldoPendiente
            }
        }
        
        // Actualizar saldo pendiente
        saldoPendiente = saldoPendiente.subtract(capitalDelDia)
        if (saldoPendiente.compareTo(BigDecimal.ZERO) < 0) {
            saldoPendiente = BigDecimal.ZERO
        }

        MTable scheduleTable = MTable.get(A_Ctx, 'legacy_schedule')
        PO cuota = scheduleTable.getPO(0, trxName)
        cuota.setAD_Client_ID(cartera.getAD_Client_ID())
        cuota.set_ValueOfColumn('AD_Org_ID', cartera.getAD_Org_ID())
        // cuota.setAD_Org_ID(cartera.getAD_Org_ID())
    
        cuota.set_ValueOfColumn('C_Invoice_ID', invoice.getC_Invoice_ID())
        cuota.set_ValueOfColumn('legacy_cartera_ID', carteraID)
        cuota.set_ValueOfColumn('DueAmt', interesDelDia)
        cuota.set_ValueOfColumn('DueDate', fechaCuota)
        cuota.set_ValueOfColumn('IsActive', 'Y')
        cuota.saveEx(trxName)

        fechaCuota = TimeUtil.addDays(fechaCuota, 1)
    }
}

// ==========================================================================
//    BLOQUE PRINCIPAL
// ==========================================================================
try {
    A_ProcessInfo.addLog(0, null, null, '✅ Iniciando proceso de sincronización de carteras...')

    List<GenericPO> legacyCartera = new Query(A_Ctx, 'legacy_cartera',
        " (synced != 'Y' or synced is null) and origen = 'native' and aprobado='Y' ", A_TrxName)
        .setOrderBy('created')
        .list()

    if (legacyCartera.isEmpty()) {
        result = 'Proceso finalizado. No se encontraron carteras para procesar.'
        A_ProcessInfo.addLog(0, null, null, result)
        return result
    }

    A_ProcessInfo.addLog(0,null,null,"🔍 Se encontraron ${legacyCartera.size()} carteras para procesar.")

    for (int i = 0; i < legacyCartera.size(); i++) {
        GenericPO car = legacyCartera[i]
        int workNumber = i + 1
        int carteraId = car.get_ID()

        String trxName = Trx.createTrxName('Cartera_' + carteraId)
        Trx trx = Trx.get(trxName, true)

        try {
            if (car.get_ValueAsInt('local_id') > 0) {
                skippedCount++; trx.close(); continue
            }
            MOrg org = MOrg.get(A_Ctx, car.get_ValueAsInt('AD_Org_ID'))
            MBPartner bp = new Query(A_Ctx, 'C_BPartner', 'C_BPartner_ID = ?', trxName).setParameters([car.get_Value('c_bpartner_id')]).first()
            MUser usr = MUser.get(A_Ctx, car.getCreatedBy())
            int bank_account_id = usr != null ? usr.get_ValueAsInt('C_BankAccount_ID') : 0

            if (org == null || bp == null || bp.getLocations(false).length == 0 || usr == null || bank_account_id <= 0) {
                A_ProcessInfo.addLog(0,null,null,"⏭️ [${workNumber}] Se omite Cartera ID ${carteraId}: Datos básicos inválidos (Org, BP, User o Bank).")
                skippedCount++; trx.close(); continue
            }

            BigDecimal monto = car.get_Value('monto')
            BigDecimal tasa = car.get_Value('tasa')
            Date fecha = car.get_Value('fecha')
            int cantidadCuotas = car.get_ValueAsInt('dias_cre')
            if (monto == null || tasa == null || tasa.compareTo(BigDecimal.ZERO) <= 0 || fecha == null || cantidadCuotas <= 0) {
                A_ProcessInfo.addLog(0,null,null,"⏭️ [${workNumber}] Se omite Cartera ID ${carteraId}: Datos del crédito incompletos.")
                skippedCount++; trx.close(); continue
            }
            // Calcular montoTotal usando la fórmula: monto + (monto × tasa × 12 × cantidadCuotas / 360)
            BigDecimal interesCalculado = monto.multiply(tasa)
                .multiply(BigDecimal.valueOf(12))
                .multiply(BigDecimal.valueOf(cantidadCuotas))
                .divide(BigDecimal.valueOf(360), 4, RoundingMode.HALF_UP)
            BigDecimal montoTotal = monto.add(interesCalculado)

            A_ProcessInfo.addLog(0,null,null,"⚙️ [${workNumber}] Procesando Cartera ID ${carteraId}...")

            Integer loc_id = bp.getLocations(false)[0].get_ID()
            int payment_term_id = (cantidadCuotas == 23) ? 1000001 : (cantidadCuotas == 30) ? 1000002 : (cantidadCuotas == 50) ? 1000003 : 1000000

            MInvoice invoice = new MInvoice(A_Ctx, 0, trxName)
            invoice.setAD_Org_ID(org.get_ID()); invoice.setDateAcct(fecha); invoice.setDateInvoiced(fecha)
            invoice.setC_DocTypeTarget_ID(NOTA_DOCTYPE_ID); invoice.setC_BPartner_ID(bp.get_ID()); invoice.setC_BPartner_Location_ID(loc_id)
            invoice.setC_PaymentTerm_ID(payment_term_id); invoice.setSalesRep_ID(SALES_REP_ID); invoice.setM_PriceList_ID(PRICE_LIST_ID)
            invoice.setIsSOTrx(true); invoice.set_ValueOfColumn('legacy_id', car.get_ValueAsInt('legacy_cartera_id')); invoice.saveEx()

            MInvoiceLine iLine = new MInvoiceLine(invoice); iLine.setC_Charge_ID(CHARGE_PRINCIPAL_ID)
            iLine.setQty(BigDecimal.ONE); iLine.setPrice(monto); iLine.setC_Tax_ID(TAX_ID); iLine.saveEx()

            invoice.processIt(DocAction.ACTION_Complete); invoice.saveEx()

            MPayment mp = new MPayment(A_Ctx, 0, trxName)
            mp.setAD_Org_ID(org.get_ID()); mp.setDescription('Desembolso por Financiamiento ' + invoice.getDocumentNo()); mp.setC_BPartner_ID(bp.get_ID())
            mp.setDateAcct(fecha); mp.setDateTrx(fecha); mp.setC_BankAccount_ID(bank_account_id); mp.setTenderType('X'); mp.setPayAmt(monto)
            mp.setC_Currency_ID(CURRENCY_ID); mp.setC_Charge_ID(CHARGE_DESEMBOLSO_ID); mp.setC_DocType_ID(PAGO_DOCTYPE_ID); mp.saveEx()

            mp.processIt(DocAction.ACTION_Complete); mp.saveEx()

            car.set_ValueOfColumn('synced', 'Y');
            car.set_ValueOfColumn('local_id', invoice.get_ID()); 
            car.set_ValueOfColumn('payment_id', mp.get_ID())
            car.set_ValueOfColumn('concepto', 'MODO-02'); // La forma en que se crea la cartera
            car.save(trxName)

            crearCuotasPagoFlat(A_ProcessInfo, invoice, car, cantidadCuotas, montoTotal)

            trx.commit()
            A_ProcessInfo.addLog(0,null,null,"✔️ [${workNumber}] OK: Cartera ${carteraId} procesada.")
            processedCount++
        } catch (Exception e) {
            trx.rollback()
            log.log(Level.SEVERE, "❌ Error procesando Cartera ID ${carteraId}.", e)
            A_ProcessInfo.addLog(0,null,null,"❌ Error en Cartera ID ${carteraId}: " + e.getMessage())
            skippedCount++
        } finally {
            trx.close()
        }
    }

    result = "Proceso finalizado. Procesados: ${processedCount}, Omitidos con error: ${skippedCount}."
} catch (Exception e) {
    result = 'ERROR FATAL: ' + e.getMessage()
    log.log(Level.SEVERE, '❌ Error fatal del script.', e)
    throw new AdempiereException(result, e)
}

A_ProcessInfo.addLog(0,null,null,'🏁 ' + result)
return result
