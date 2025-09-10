/**
CarteraBridgeNativeSingle
Proceso para sincronizar legacy_cartera con C_Invoice y C_Payment
Solo para legacy_cartera de tipo native
20250525 - Refactorización para provisionar solo monto principal
20240402 - se genera basado en batch
20220201 - payment_id field added
20210616 - First version
**/

import org.compiere.model.MTable
import org.compiere.util.DB
import org.compiere.util.Msg
import org.adempiere.model.GenericPO
import org.compiere.model.Query
import org.compiere.model.MOrg
import org.compiere.model.MBPartner
import org.compiere.model.MBPartnerLocation
import org.compiere.model.MInvoice
import org.compiere.model.MUser
import org.compiere.model.MInvoiceLine
import org.compiere.model.MPayment
import org.compiere.model.MBankAccount
import org.compiere.model.PO
import org.compiere.util.Env
import org.compiere.util.CLogger
import org.compiere.util.TimeUtil
import java.util.logging.Level
import org.compiere.process.ProcessInfoParameter
import org.adempiere.exceptions.AdempiereException
import org.compiere.process.DocAction
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Timestamp
import java.util.Date

CLogger log = CLogger.getCLogger(GenericPO.class)

def esDomingo(Date fecha) {
    Calendar cal = Calendar.getInstance()
    cal.setTime(fecha)
    return cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
}

def crearCuotasPagoFlat = { MInvoice invoice, GenericPO cartera, int numCuotas,
                    BigDecimal tasa, BigDecimal cuotaNivelada, BigDecimal monto,
                    BigDecimal montoTotal, BigDecimal totalInteres ->
    String trxName = invoice.get_TrxName()
    int carteraID = cartera.get_ValueAsInt('legacy_cartera_ID')
    BigDecimal interesARegistrar = totalInteres.divide(BigDecimal.valueOf(numCuotas), 10, RoundingMode.HALF_UP)

    A_ProcessInfo.addLog(0, null, null,
        "Creando cuotas flat para invoice ${invoice.getDocumentNo()}, numCuotas=${numCuotas}, " +
        "cuotaNivelada=${cuotaNivelada}, monto=${monto}, montoTotal=${montoTotal}, totalInteres=${totalInteres}")

    // --- Lógica para borrar cuotas existentes ---
    log.info("Buscando y eliminando cuotas existentes para legacy_cartera_ID=${carteraID}")
    String sqlDelete = 'DELETE FROM legacy_schedule WHERE legacy_cartera_ID = ?'
    int deletedRows = DB.executeUpdate(sqlDelete, [carteraID] as Object[], true, trxName)
    if (deletedRows > 0) {
        log.warning("Se eliminaron ${deletedRows} cuotas antiguas para la cartera ID ${carteraID}.")
    }
    // --- FIN DE LA MODIFICACIÓN ---
    Date fechaBase = cartera.get_Value('fecha')
    Date fechaCuota = TimeUtil.addDays(fechaBase, 1)
    for (int i = 0; i < numCuotas; i++) {
        fechaCuota = TimeUtil.addDays(fechaCuota, 1)
        if (esDomingo(fechaCuota)) {
            fechaCuota = TimeUtil.addDays(fechaCuota, 1)
        }
        MTable scheduleTable = MTable.get(Env.getCtx(), 'legacy_schedule')
        PO cuota = scheduleTable.getPO(0, trxName)

        cuota.setAD_Client_ID(invoice.getAD_Client_ID())
        cuota.setAD_Org_ID(invoice.getAD_Org_ID())
        cuota.set_ValueOfColumn('Created', new Timestamp(System.currentTimeMillis()))
        cuota.set_ValueOfColumn('CreatedBy', Env.getAD_User_ID(Env.getCtx()))
        cuota.set_ValueOfColumn('C_Invoice_ID', invoice.getC_Invoice_ID())
        cuota.set_ValueOfColumn('legacy_cartera_ID', carteraID)

        cuota.set_ValueOfColumn('DueAmt', cuotaNivelada.add(interesARegistrar))
        cuota.set_ValueOfColumn('DueDate', fechaCuota)

        cuota.set_ValueOfColumn('IsValid', 'Y')
        cuota.set_ValueOfColumn('Processed', 'N')
        cuota.set_ValueOfColumn('IsActive', 'Y')
        cuota.saveEx(trxName)

        A_ProcessInfo.addLog(0, null, null,
            "Creando cuota flat ${i + 1} para ddoc ${invoice.getDocumentNo()} " +
            "monto=${cuotaNivelada.add(interesARegistrar)} fechaCuota=${fechaCuota}")
    } // fin del bucle de cuotas
} // fin de crearCuotasPagoFlat

try {
    log.info('Iniciando evaluación del script CarteraBridgeNativeSingle')

    if (A_Ctx == null || A_TrxName == null || A_Parameter == null) {
        result = 'ERROR: Variables de contexto no inicializadas'
        log.severe(result)
    } else {
        ProcessInfoParameter[] para = A_Parameter
        int carteraid = 0
        for (ProcessInfoParameter p : para) {
            String name = p.getParameterName()
            if (p.getParameter() == null) {
                continue
            } else if (name.equals('cartera')) {
                carteraid = p.getParameterAsInt()
            } else {
                log.warning('Parámetro desconocido: ' + name)
            }
        }
        log.info('Parámetro cartera recibido: ' + carteraid)

        if (carteraid == 0) {
            result = 'ERROR: No se proporcionó un ID de cartera válido'
            log.severe(result)
        } else {
            GenericPO car = new Query(A_Ctx, 'legacy_cartera',
                "legacy_cartera_id = ? AND (synced != 'Y' OR synced IS NULL) AND origen = 'native' AND aprobado = 'Y'", A_TrxName)
                .setParameters([carteraid])
                .first()

            if (car == null) {
                result = 'ERROR: No se encontró registro de cartera válido para ID ' + carteraid
                log.severe(result)
            } else {
                MOrg org = MOrg.get(A_Ctx, car.get_ValueAsInt('AD_Org_ID'))
                if (org == null) {
                    result = 'ERROR: No existe la Org ' + car.get_ValueAsInt('AD_Org_ID')
                    log.severe(result)
                } else {
                    log.info('Organización encontrada: ' + org.getName())
                    int currentLocalID = car.get_ValueAsInt('local_id')
                    if (currentLocalID <= 0) {
                        MBPartner bp = new Query(A_Ctx, 'C_BPartner', 'C_BPartner_ID = ? ', A_TrxName)
                                .setParameters([car.get_Value('c_bpartner_id')])
                                .first()
                        int locsize = bp == null ? 0 : bp.getLocations(false).size()

                        if (bp != null && locsize > 0) {
                            Integer loc_id = bp.getLocations(false)[0].get_ID()
                            BigDecimal tasa = car.get_Value('tasa')
                            BigDecimal monto = (BigDecimal) car.get_Value('monto')
                            BigDecimal montoTotal = (BigDecimal) car.get_Value('montototal')
                            BigDecimal totalInteres = (BigDecimal) car.get_Value('valorinteres')
                            int cantidadCuotas = car.get_Value('dias_cre')

                            if (tasa == null || monto == null || cantidadCuotas <= 0) {
                                throw new AdempiereException('Valores inválidos: tasa=' + tasa + ', monto=' + monto + ', dias_cre=' + cantidadCuotas)
                            }

                            // FIX: Añadir validación para el usuario y su cuenta bancaria.
                            MUser usr = MUser.get(A_Ctx, car.getCreatedBy())
                            if (usr == null) {
                                throw new AdempiereException('No se pudo encontrar el usuario creador con ID: ' + car.getCreatedBy())
                            }
                            int bank_account_id = usr.get_ValueAsInt('C_BankAccount_ID')
                            if (bank_account_id <= 0) {
                                throw new AdempiereException("El usuario '" + usr.getName() + "' no tiene una cuenta bancaria (C_BankAccount_ID) configurada.")
                            }

                            int payment_term_id = 1000000 // Inmediato
                            if (cantidadCuotas == 23) payment_term_id = 1000001
                            if (cantidadCuotas == 30) payment_term_id = 1000002
                            if (cantidadCuotas == 50) payment_term_id = 1000003

                            int Nota_C_DocType_ID = 1000048 // Financiamiento
                            MInvoice invoice = new MInvoice(A_Ctx, 0, A_TrxName)
                            invoice.setAD_Org_ID(org.get_ID())
                            invoice.setDateAcct(car.get_Value('fecha'))
                            invoice.setDateInvoiced(car.get_Value('fecha'))
                            invoice.setC_DocTypeTarget_ID(Nota_C_DocType_ID)
                            invoice.setC_BPartner_ID(bp.get_ID())
                            invoice.setC_BPartner_Location_ID(loc_id)
                            invoice.setC_PaymentTerm_ID(payment_term_id)
                            invoice.setSalesRep_ID(1000000)
                            invoice.setM_PriceList_ID(1000001)
                            invoice.setIsSOTrx(true)
                            invoice.set_ValueOfColumn('legacy_id', car.get_ValueAsInt('legacy_cartera_id'))
                            invoice.saveEx(A_TrxName)

                            MInvoiceLine iLine = new MInvoiceLine(invoice)
                            iLine.setC_Charge_ID(1000028) // Monto Principal
                            iLine.setQty(BigDecimal.ONE)
                            iLine.setPrice(monto)
                            iLine.saveEx(A_TrxName)

                            invoice.processIt(DocAction.ACTION_Complete)
                            invoice.saveEx(A_TrxName)

                            // --- Creación de Pago (Desembolso) ---
                            int Payment_C_DocType_ID = 1000049
                            MPayment mp = new MPayment(A_Ctx, 0, A_TrxName)
                            mp.setAD_Org_ID(org.get_ID())
                            mp.setDescription('Desembolso por Financiamiento ' + invoice.getDocumentNo())
                            mp.setC_BPartner_ID(bp.get_ID())
                            mp.setDateAcct(car.get_Value('fecha'))
                            mp.setDateTrx(car.get_Value('fecha'))
                            mp.setC_BankAccount_ID(bank_account_id)
                            mp.setTenderType('X')
                            mp.setPayAmt(monto)
                            mp.setC_Currency_ID(209)
                            mp.setC_Charge_ID(1000030)
                            mp.setC_DocType_ID(Payment_C_DocType_ID)
                            mp.saveEx(A_TrxName)

                            mp.processIt(DocAction.ACTION_Complete)
                            mp.saveEx(A_TrxName)

                            car.set_ValueOfColumn('synced', 'Y')
                            car.set_ValueOfColumn('local_id', invoice.get_ID())
                            car.set_ValueOfColumn('payment_id', mp.get_ID())
                            car.saveEx(A_TrxName)

                            BigDecimal tasaNeta = tasa.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                            BigDecimal tasaPeriodo = tasaNeta.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP)
                            BigDecimal cuotaNivelada = montoTotal.divide(BigDecimal.valueOf(cantidadCuotas), 10, RoundingMode.HALF_UP)
                            BigDecimal tasaAnualFactor = tasaNeta.multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(360), 10, RoundingMode.HALF_UP)

                            crearCuotasPagoFlat(invoice, car, cantidadCuotas, tasa, cuotaNivelada, monto, montoTotal, totalInteres)

                            A_ProcessInfo.addLog(0, null, null, 'Procesada Cartera: ' + car.get_ID()
                                    + ', Invoice: ' + invoice.getDocumentNo()
                                    + ', Payment: ' + mp.getDocumentNo()
                                    + ', BPName: ' + bp.getName())

                            result = 'Se Procesó Cartera: ' + car.get_ID() + ' exitosamente.'
                        } else {
                            result = 'ERROR: No se encontró un BP válido para legacy_cartera ID: ' + car.get_ID()
                            log.severe(result)
                        }
                    } else {
                        result = 'INFO: La cartera ' + car.get_ID() + ' ya fue procesada previamente.'
                        log.info(result)
                    }
                }
            }
        }
    }
} catch (Exception e) {
    result = 'ERROR: Excepción en el proceso: ' + e.getMessage()
    log.log(Level.SEVERE, 'Error fatal en el script.', e)
    throw new AdempiereException(result, e)
}

return result
