/**
Proceso para sincronizar legacy_cobro con C_Payment
20210725 - Resultset en lugar de List, various fixes
20210421 - First version
key: groovy:CobroBridge
name: CobroBridge
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

CLogger log = CLogger.getCLogger(GenericPO.class);
MOrg org = null;
int orgid = 0;
ProcessInfoParameter[] para = A_Parameter;
for (int i = 0; i < para.length; i++) {
    String name = para[i].getParameterName();
    if (para[i].getParameter() == null){
    }
    else if (name.equals("orgid"))
        orgid = para[i].getParameterAsInt();
    else
        log.log(Level.SEVERE, "Unknown Parameter: " + name);
}
org = MOrg.get(A_Ctx, orgid);
if(org==null || orgid==0){
    result = "ERROR: No existe la Org " + orgid;
    return;
}
// Let's create a payment
MBankAccount bac = new Query(A_Ctx, "C_BankAccount", "AD_Org_ID = ? ", A_TrxName)
.setParameters([ orgid ])
.first();
if(bac==null){
    result = "ERROR: No existe la Cuenta Bancaria Org => " + orgid;
    return;
}

String orgKey = org.getDescription();
int workNumber = 0;
String sqlCobros = " select  car.legacy_cartera_id, car.c_bpartner_id as car_bpid, " +
                   " car.local_id as invoice_id, " +
                   "(select documentno from c_invoice where c_invoice_id = car.local_id )as docinvoice, " +
                   " cob.* " +
                   " from legacy_cobro cob "  +
                   " inner join legacy_cartera car   " + 
                   "    on cob.id_cartera = car.id_cartera and cob.ad_org_id = car.ad_org_id " +
                   " where cob.ad_org_id = ? " +
                   "  and car.saldo > 0 and cob.synced = 'N' and cob.abono > 0 order by car.legacy_cartera_id asc ";

String sqlCount = " select count(cob.*) " + 
                " from legacy_cobro cob " +
                " inner join legacy_cartera car  " +
                "    on cob.id_cartera = car.id_cartera and cob.ad_org_id = car.ad_org_id " +
                " where cob.ad_org_id = ? " +
                " and car.saldo > 0 and cob.synced = 'N' and cob.abono > 0 ";
 			    

    int countRecs = DB.getSQLValue(A_TrxName, sqlCount, orgid);
    System.out.println("Cobros a migrar " + countRecs.toString() );
    PreparedStatement psmt = DB.prepareStatement(sqlCobros, A_TrxName);
    psmt.setInt(1, orgid);
    psmt.setMaxRows(50000);
    ResultSet rs = psmt.executeQuery();
    while (rs.next()) {
        workNumber = workNumber+1;
        System.out.println("Migrating Cobro #" + workNumber.toString() + " of " + countRecs.toString() );
        GenericPO cob = new Query(A_Ctx, "legacy_cobro", "legacy_cobro_id = ?", A_TrxName)
                .setParameters(rs.getInt("legacy_cobro_id"))
                .first();
        
        BigDecimal invoiceId = rs.getBigDecimal("invoice_id");
        Integer invoiceIdInt = rs.getInt("invoice_id");
        int bpid = rs.getInt("car_bpid");
        Integer id_cobro = rs.getInt("id_cobro");
        Integer id_cliente = rs.getInt("id_cliente");
        
        String docKey = orgKey + "-" + id_cobro;
        int Payment_C_DocType_ID = 1000050; 
        
        
        if(null!=cob && invoiceId>0) {
            MPayment mp = new MPayment(A_Ctx, 0, A_TrxName);
            mp.setAD_Org_ID(orgid);
            mp.setC_BankAccount_ID(bac.get_ID()); 
            // mp.setDocumentNo(String.valueOf(docKey));
            mp.setDescription("Recibo de Abono por Financiamiento " + rs.getString("docinvoice") + " CobroID: " + rs.getInt("legacy_cobro_id").toString() );
            mp.setC_BPartner_ID(bpid);
            mp.setDateAcct((Timestamp) rs.getTimestamp("operacion"));
            mp.setDateTrx((Timestamp) rs.getTimestamp("operacion"));
            mp.setTenderType("X");
            mp.setAmount(209, (BigDecimal) rs.getBigDecimal("abono"));
            mp.setC_Invoice_ID(invoiceIdInt);
            mp.setC_DocType_ID(Payment_C_DocType_ID);
            mp.save(A_TrxName);
            
            BigDecimal localId = (BigDecimal) mp.get_Value("C_Payment_ID");
            cob.set_ValueOfColumn("local_invoice_id", invoiceId);
            cob.set_ValueOfColumn("c_bpartner_id", bpid);
            cob.set_ValueOfColumn("local_id", localId);
            cob.set_ValueOfColumn("synced", "Y");
            cob.set_ValueOfColumn("id_cartera", rs.getInt("legacy_cartera_id"));
            cob.set_ValueOfColumn("old_cartera", rs.getBigDecimal("id_cartera"));
            cob.save(A_TrxName);

            log.info("Payment created: " + mp.getDocumentNo() + " No: " + workNumber);
        }
        
        A_ProcessInfo.addLog(0,null,null, "(" + workNumber.toString() + ") Cobro parsed: " + id_cobro.toString() + " OrgName: "  + org.getName() + " Legacy_Cobro_ID: " + rs.getInt("legacy_cobro_id").toString() );
    } // iterator close
    rs.close();



result = "Se migraron datos de LegacyCobro a C_Payment";