/**
Proceso para Cerra Cajas en Sucursal
20210904 - Solo tomar en cuenta envíos a sucursal como "Enviados"
20210818 - Verificar que no falten docs por procesar
20210811 - Solo docs en estado CO-CL
20210803 - First version
key: groovy:CierreCajasSucursal
name: CierreCajasSucursal
class: @script:groovy:CierreCajasSucursal
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
Timestamp fechaTS;
SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/YYYY");
ProcessInfoParameter[] para = A_Parameter;
for (int i = 0; i < para.length; i++) {
    String name = para[i].getParameterName();
    if (para[i].getParameter() == null){
    }
    else if (name.equals("orgid"))
        orgid = para[i].getParameterAsInt();
    else if (name.equals("bpid"))
        bpid = para[i].getParameterAsInt();
    else if (name.equals("fecha"))
         fechaTS = para[i].getParameterAsTimestamp();
    else
        log.log(Level.SEVERE, "Unknown Parameter: " + name);
}
org = MOrg.get(A_Ctx, orgid);
if(org==null || orgid==0){
    result = "ERROR: No existe la Org " + orgid;
    return;
}
fecha = new Date(fechaTS.getTime());
if(fechaTS==null || fecha==null){
    result = "ERROR: Debe proporcionar una Fecha";
    return;
}

String sqlCarteraNonSync = "select count(*) from legacy_cartera where montototal>0 and synced = 'N' and ad_org_id = ? and fecha::date <= ? and origen = 'native' ";
String sqlCobrosNonSync = "select count(*) from legacy_cobro where abono>0 and synced = 'N' and ad_org_id = ? and operacion::date <= ? and origen = 'native' ";
String sqlPaymentsDraft = "select count(*) from c_payment where docstatus = 'DR' and ad_org_id = ? and dateacct::date <= ? ";
String sqlInvoicesDraft = "select count(*) from c_invoice where docstatus = 'DR' and ad_org_id = ? and dateacct::date <= ? ";
int carteraNonSync = DB.getSQLValue(A_TrxName, sqlCarteraNonSync, [orgid, fechaTS] );
int cobrosNonSync = DB.getSQLValue(A_TrxName, sqlCobrosNonSync, [orgid, fechaTS]);
int paymentsDraft = DB.getSQLValue(A_TrxName, sqlPaymentsDraft, [orgid, fechaTS]);
int invoicesDraft = DB.getSQLValue(A_TrxName, sqlInvoicesDraft, [orgid, fechaTS]);
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
" case when dt.docbasetype = 'ARR' and pay.c_invoice_id is not null then pay.payamt else 0.00 end as cobros, " +
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

    
    PreparedStatement psmt = DB.prepareStatement(sqlCajas, A_TrxName);
    psmt.setTimestamp(1,fechaTS);
    psmt.setInt(2, orgid);
    psmt.setMaxRows(50000);
    ResultSet rs = psmt.executeQuery();
    while (rs.next()) {
        System.out.println("Cerrando Caja -> " + rs.getString("codigocaja") );
        BigDecimal efectivo = (BigDecimal) rs.getBigDecimal("efectivo");
        BigDecimal enviados = (BigDecimal) rs.getBigDecimal("enviados");
        BigDecimal montoPago = (BigDecimal) rs.getBigDecimal("saldocaja");
        BigDecimal enviadosCentral = (BigDecimal) rs.getBigDecimal("enviados_central");
        workNumber = workNumber+1;
        System.out.println("Closing Caja #" + workNumber.toString() + " - " + rs.getString("codigocaja") + " Efectivo: " + efectivo );
        int dtOrigen = 1000009;
        int dtDestino = 1000008; 
        int cargoTransferencia = 1000034;
        
        if(montoPago.compareTo(BigDecimal.ZERO)>0 && enviadosCentral.compareTo(BigDecimal.ZERO)<=0 ) {
            // APP en Caja Origen
            MPayment mpOrigen = new MPayment(A_Ctx, 0, A_TrxName);
            mpOrigen.setAD_Org_ID(orgid);
            mpOrigen.setC_BankAccount_ID(rs.getInt("c_bankaccount_id")); 
            mpOrigen.setDescription("Cierre de Caja " + rs.getString("codigocaja") + "-" + rs.getString("nombrecaja") + " al: " + sdf.format(fecha) );
            mpOrigen.setC_BPartner_ID(bpid);
            mpOrigen.setDateAcct(fechaTS);
            mpOrigen.setDateTrx(fechaTS);
            mpOrigen.setTenderType("A");
            mpOrigen.setAmount(209, montoPago);
            mpOrigen.setC_Charge_ID(cargoTransferencia);
            mpOrigen.setC_DocType_ID(dtOrigen);
            // mp.setDocumentNo(String.valueOf(docKey));
            mpOrigen.save(A_TrxName);
            mpOrigen.processIt(DocAction.ACTION_Complete);

            MPayment mpDestino = new MPayment(A_Ctx, 0, A_TrxName);
            mpDestino.setAD_Org_ID(orgid);
            mpDestino.setC_BankAccount_ID(rs.getInt("cajadestinoid")); 
            mpDestino.setDescription("Cierre de Caja " + rs.getString("codigocaja") + "-" + rs.getString("nombrecaja") + " al: " + sdf.format(fecha) );
            mpDestino.setC_BPartner_ID(bpid);
            mpDestino.setDateAcct(fechaTS);
            mpDestino.setDateTrx(fechaTS);
            mpDestino.setTenderType("A");
            mpDestino.setAmount(209, montoPago);
            mpDestino.setC_Charge_ID(cargoTransferencia);
            mpDestino.setC_DocType_ID(dtDestino);
            mpDestino.save(A_TrxName);
            mpDestino.processIt(DocAction.ACTION_Complete);

            log.info("Origen: " + mpOrigen.getDocumentNo() + "Destino: " +  mpDestino.getDocumentNo() + " No: " + workNumber);
            A_ProcessInfo.addLog(0,null,null, "Origen: " + mpOrigen.getDocumentNo() + " Destino: " +  mpDestino.getDocumentNo() + " No: " + workNumber + " Monto: " +  montoPago.toString() );
        } else
            A_ProcessInfo.addLog(0,null,null, "Caja ya tiene un cierre " + rs.getString("codigocaja") + "-" + rs.getString("nombrecaja") );
        
    } // iterator close
    rs.close();

result = "Se ejecutó el proceso de Cierre de Caja satisfactoriamente para Sucursal " + org.getName();