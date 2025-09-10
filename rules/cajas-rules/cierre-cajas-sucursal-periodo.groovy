/**
Proceso para Cerrar Cajas en Sucursal por Periodo
20210904 - Solo tomar en cuenta envíos a sucursal como "Enviados"
20210818 - Verificar que no falten docs por procesar
20210811 - Solo docs en estado CO-CL
20210803 - First version
key: groovy:CierreCajasSucursalPeriodo
name: CierreCajasSucursalPeriodo
class: @script:groovy:CierreCajasSucursalPeriodo
**/

import org.compiere.model.MTable;
import org.compiere.util.DB;
import org.compiere.util.Msg;
import org.adempiere.model.GenericPO;
import org.compiere.model.Query;
import org.compiere.model.MOrg;
import org.compiere.model.MBPartner;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MPayment;
import org.compiere.model.MBankAccount;
import org.compiere.util.Env;
import org.compiere.util.CLogger;
import java.util.logging.Level;
import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.compiere.process.ProcessInfoParameter;
import org.adempiere.exceptions.AdempiereException;
import java.util.Date;
import java.text.SimpleDateFormat;
import org.compiere.process.DocAction;

CLogger log = CLogger.getCLogger(GenericPO.class);
MOrg org = null;
int orgid = 0;
int bpid = 0;
Date fecha;
Timestamp fechaInicialTS;
Timestamp fechaFinalTS;

SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/YYYY");
ProcessInfoParameter[] para = A_Parameter;
for (int i = 0; i < para.length; i++) {
    String name = para[i].getParameterName();
    if (para[i].getParameter() == null){
    }
    else if (name.equals("bpid"))
        bpid = para[i].getParameterAsInt();
    else if (name.equals("orgid"))
        orgid = para[i].getParameterAsInt();
    else if (name.equals("fechainicial"))
         fechaInicialTS = para[i].getParameterAsTimestamp();
    else if (name.equals("fechafinal"))
         fechaFinalTS = para[i].getParameterAsTimestamp();
    else
        log.log(Level.SEVERE, "Unknown Parameter: " + name);
}
org = MOrg.get(A_Ctx, orgid);
if(org==null || orgid==0){
    result = "ERROR: No existe la Org " + orgid;
    return;
}

String sqlFechas = "select distinct pay.dateacct::date as fecha " +
    " from c_payment pay " +
    " where pay.payamt > 0 " +
    " and pay.dateacct::date between ?::date and ?::date " +
    " and pay.ad_org_id = ? " +
    " order by pay.dateacct::date asc";

if(fechaInicialTS==null){
    result = "ERROR: Debe proporcionar una Fecha Inicial";
    return;
}
if(fechaFinalTS==null){
    result = "ERROR: Debe proporcionar una Fecha Final";
    return;
}

PreparedStatement psmt = DB.prepareStatement(sqlFechas, A_TrxName);
psmt.setTimestamp(1,fechaInicialTS);
psmt.setTimestamp(2,fechaFinalTS);
psmt.setInt(3, orgid);
psmt.setMaxRows(50000);
ResultSet rs = psmt.executeQuery();
while (rs.next()) {
    Date fechaActual = rs.getDate("fecha");
    Timestamp fechaActualTS = rs.getTimestamp("fecha");
    A_ProcessInfo.addLog(0,null,null, "Procesando cierre para Fecha::: ${fechaActual.toString()} Org::: ${org.getName()}" );

    String sqlCarteraNonSync = "select count(*) from legacy_cartera where montototal>0 and synced = 'N' and ad_org_id = ? and fecha::date <= ? and origen = 'native' ";
    String sqlCobrosNonSync = "select count(*) from legacy_cobro where abono>0 and synced = 'N' and ad_org_id = ? and operacion::date <= ? and origen = 'native' ";
    String sqlPaymentsDraft = "select count(*) from c_payment where docstatus = 'DR' and ad_org_id = ? and dateacct::date <= ? ";
    String sqlInvoicesDraft = "select count(*) from c_invoice where docstatus = 'DR' and ad_org_id = ? and dateacct::date <= ? ";
    int carteraNonSync = DB.getSQLValue(A_TrxName, sqlCarteraNonSync, [orgid, fechaActualTS] );
    int cobrosNonSync = DB.getSQLValue(A_TrxName, sqlCobrosNonSync, [orgid, fechaActualTS]);
    int paymentsDraft = DB.getSQLValue(A_TrxName, sqlPaymentsDraft, [orgid, fechaActualTS]);
    int invoicesDraft = DB.getSQLValue(A_TrxName, sqlInvoicesDraft, [orgid, fechaActualTS]);
    int totalNonSync = carteraNonSync + cobrosNonSync + paymentsDraft + invoicesDraft;
    System.out.println("Total Unsync: " + totalNonSync);

    if(totalNonSync>0){
        String resultStr = "ERROR: Hay registros pendientes de Procesar " + 
            " Desembolsos: " + carteraNonSync +
            " Cobros: " + cobrosNonSync +
            " Pagos (C_Payment): " + paymentsDraft +
            " Invoices: " + invoicesDraft +
            " Debe procesarlos antes de poder Cerrar Caja." +
            " Sucursal: " + org.getName();

        result = resultStr;
        return resultStr;
    }
    System.out.println("Before Query Exec ");
    String orgKey = org.getDescription();
    int workNumber = 0;
    String sqlCajas  = " with sortedpayments as ( " +
    " select bac.c_bankaccount_id, bac.value as codigocaja, bac.name as nombrecaja, pay.documentno, " + 
    "  ch.c_charge_id, " +
    " ch.name as cargo, " +
    " dt.docbasetype, " +
    " case when dt.docbasetype = 'ARR' then 'Ingreso' else 'Egreso' end as tipomov, " +
    " pay.payamt as monto, " +
    " case when dt.docbasetype = 'ARR' and pay.c_charge_id = 1000034 then pay.payamt else 0.00 end as basediaria, " +
    " case when dt.docbasetype = 'APP' and pay.c_charge_id = 1000030 then pay.payamt else 0.00 end as desembolsos, " +
    " case when dt.docbasetype = 'ARR' and pay.c_doctype_id in (1000050) then pay.payamt else 0.00 end as cobros, " +
    " case when dt.docbasetype = 'APP' and pay.c_charge_id not in (1000034,1000030) then pay.payamt else 0.00 end as gastos, " +
    " case when dt.docbasetype = 'ARR' and pay.c_charge_id = 1000034 then pay.payamt else 0.00 end as recibidos, " +
    " case when ( dt.docbasetype = 'APP' and pay.c_charge_id = 1000034 ) then pay.payamt else 0.00 end as enviados, " +
    " case when ( dt.docbasetype = 'APP' and pay.c_charge_id = 1000034 and bac.description ~* 'sucursal') then pay.payamt else 0.00 end as enviados_central, " +
    " bac.description as tipocaja " +
    " from c_bankaccount bac " +
    " inner join c_payment pay on pay.c_bankaccount_id = bac.c_bankaccount_id " +
    " inner join c_doctype dt on pay.c_doctype_id = dt.c_doctype_id " +
    " left join c_charge ch on pay.c_charge_id = ch.c_charge_id " +
    " where pay.dateacct::date = ? " +
    " and pay.ad_org_id = ? " +
    " and pay.docstatus in ('CO', 'CL') " +
    " ) " +
    " select sp.c_bankaccount_id, sp.codigocaja, sp.nombrecaja, " +
    " sum(sp.basediaria) as basediaria, " +
    " sum(sp.desembolsos) as desembolsos, " +
    " sum(sp.cobros) as cobros, " + 
    " sum(sp.gastos) as gastos, " +
    " sum(sp.basediaria - sp.desembolsos + sp.cobros - sp.gastos) as efectivo, " +
    " sum(sp.recibidos) as recibidos, " +
    " sum(sp.enviados) as enviados, " +
    " sum(sp.enviados_central) as enviados_central, " +
    " sum(sp.basediaria - sp.desembolsos + sp.cobros - sp.gastos) - sum(sp.enviados) as saldocaja, " +
    " (select bactrans.c_bankaccount_id from c_bankaccount bactrans " +
    "  	where bactrans.ad_org_id = org.ad_org_id and bactrans.description ~* 'sucursal') as cajadestinoid, " +
    " (select bactrans.value || '-' || bactrans.name from c_bankaccount bactrans " +
    " 	where bactrans.ad_org_id = org.ad_org_id and bactrans.description ~* 'sucursal') as cajadestinoname " + 
    " from sortedpayments sp " +
    " inner join c_bankaccount bac on sp.c_bankaccount_id = bac.c_bankaccount_id " +
    " inner join ad_org org on bac.ad_org_id = org.ad_org_id " +
    " where sp.tipocaja ~* 'cobrador' " + 
    " group by sp.c_bankaccount_id, sp.codigocaja, sp.nombrecaja, org.ad_org_id, sp.tipocaja ";

        
    PreparedStatement psmtCierre = DB.prepareStatement(sqlCajas, A_TrxName);
    psmtCierre.setTimestamp(1,fechaActualTS);
    psmtCierre.setInt(2, orgid);
    psmtCierre.setMaxRows(50000);
    PreparedStatement psmtCheck = DB.prepareStatement(sqlCajas, A_TrxName);
    psmtCheck.setTimestamp(1,fechaActualTS);
    psmtCheck.setInt(2, orgid);
    psmtCheck.setMaxRows(50000);

    ResultSet rsCierre = psmtCierre.executeQuery();
    ResultSet rsCheck = psmtCheck.executeQuery();

    // Iterate over the rsCheck ResultSet
    while (rsCheck.next()) {
        def saldoCaja = rsCheck.getObject(12);
        def nombreCaja = rsCheck.getObject(3);
        A_ProcessInfo.addLog(0, null, null, "Caja: $nombreCaja, Saldo: $saldoCaja");
        if (saldoCaja < 0) {
            A_ProcessInfo.addLog(0, null, null, "Saldo de $nombreCaja  es menor que cero! [$saldoCaja]");
            return "Saldo de $nombreCaja  es menor que cero! [$saldoCaja]. No se procesarán más cierres de caja hasta arreglar el saldo negativo.";
        }
    }

    while (rsCierre.next()) {
        System.out.println("Cerrando Caja -> " + rsCierre.getString("codigocaja") );
        BigDecimal efectivo = (BigDecimal) rsCierre.getBigDecimal("efectivo");
        BigDecimal enviados = (BigDecimal) rsCierre.getBigDecimal("enviados");
        BigDecimal montoPago = (BigDecimal) rsCierre.getBigDecimal("saldocaja");
        BigDecimal enviadosCentral = (BigDecimal) rsCierre.getBigDecimal("enviados_central");
        workNumber = workNumber+1;
        System.out.println("Closing Caja #" + workNumber.toString() + " - " + rsCierre.getString("codigocaja") + " Efectivo: " + efectivo );
        int dtOrigen = 1000009;
        int dtDestino = 1000008; 
        int cargoTransferencia = 1000034;
        
        if(montoPago.compareTo(BigDecimal.ZERO)>0 && enviadosCentral.compareTo(BigDecimal.ZERO)<=0 ) {
            // APP en Caja Origen
            MPayment mpOrigen = new MPayment(A_Ctx, 0, A_TrxName);
            mpOrigen.setAD_Org_ID(orgid);
            mpOrigen.setC_BankAccount_ID(rsCierre.getInt("c_bankaccount_id")); 
            mpOrigen.setDescription("Cierre de Caja " + rsCierre.getString("codigocaja") + "-" + rsCierre.getString("nombrecaja") + " al: " + sdf.format(fechaActual) );
            mpOrigen.setC_BPartner_ID(bpid);
            mpOrigen.setDateAcct(fechaActualTS);
            mpOrigen.setDateTrx(fechaActualTS);
            mpOrigen.setTenderType("A");
            mpOrigen.setAmount(209, montoPago);
            mpOrigen.setC_Charge_ID(cargoTransferencia);
            mpOrigen.setC_DocType_ID(dtOrigen);
            // mp.setDocumentNo(String.valueOf(docKey));
            mpOrigen.save(A_TrxName);
            mpOrigen.processIt(DocAction.ACTION_Complete);

            MPayment mpDestino = new MPayment(A_Ctx, 0, A_TrxName);
            mpDestino.setAD_Org_ID(orgid);
            mpDestino.setC_BankAccount_ID(rsCierre.getInt("cajadestinoid")); 
            mpDestino.setDescription("Cierre de Caja " + rsCierre.getString("codigocaja") + "-" + rsCierre.getString("nombrecaja") + " al: " + sdf.format(fechaActual) );
            mpDestino.setC_BPartner_ID(bpid);
            mpDestino.setDateAcct(fechaActualTS);
            mpDestino.setDateTrx(fechaActualTS);
            mpDestino.setTenderType("A");
            mpDestino.setAmount(209, montoPago);
            mpDestino.setC_Charge_ID(cargoTransferencia);
            mpDestino.setC_DocType_ID(dtDestino);
            mpDestino.save(A_TrxName);
            mpDestino.processIt(DocAction.ACTION_Complete);

            log.info("Origen: " + mpOrigen.getDocumentNo() + "Destino: " +  mpDestino.getDocumentNo() + " No: " + workNumber);
            A_ProcessInfo.addLog(0,null,null, "Origen: " + mpOrigen.getDocumentNo() + " Destino: " +  mpDestino.getDocumentNo() + " No: " + workNumber + " Monto: " +  montoPago.toString() );
        } else if(montoPago.compareTo(BigDecimal.ZERO)>0)
            A_ProcessInfo.addLog(0,null,null, "Caja tiene saldo negativo :::: " + rsCierre.getString("codigocaja") + "-" + rsCierre.getString("nombrecaja") );
        else 
            A_ProcessInfo.addLog(0,null,null, "Caja ya tiene un cierre :::: " + rsCierre.getString("codigocaja") + "-" + rsCierre.getString("nombrecaja") );
        
    } // iterator close
    rsCierre.close();
}
rs.close();
return "Se procesaron las fechas satisfactoriamente";