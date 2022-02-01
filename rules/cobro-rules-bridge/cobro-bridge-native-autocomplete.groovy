/**
CobroBridgeNativeAutocomplete
Proceso para sincronizar legacy_cobro con C_Payment con Autocomplete
Solo para legacy_cobro de tipo native
20210927 - Si cartera está sobregirada, omitir Cobro
20210920 - Bypassear Cobros a Carteras no procesadas
20210812 - First version
Rule: groovy:CobroBridgeNativeAutocomplete
Class: @script:groovy:CobroBridgeNativeAutocomplete
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
import org.compiere.model.PO;
import org.compiere.util.Env;
import org.compiere.util.CLogger;
import java.util.logging.Level;
import org.compiere.process.ProcessInfoParameter;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.process.DocAction;
import java.text.SimpleDateFormat;

CLogger log = CLogger.getCLogger(GenericPO.class);
// PO.setCrossTenantSafe();
SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/YYYY HH:mm:ss");
MOrg org = null;
ProcessInfoParameter[] para = A_Parameter;
for (int i = 0; i < para.length; i++) {
    String name = para[i].getParameterName();
    if (para[i].getParameter() == null){
    }
    else
        log.log(Level.SEVERE, "Unknown Parameter: " + name);
}

int workNumber = 0;
List<GenericPO> legacyCobroList = new Query(A_Ctx, "legacy_cobro",
         " (synced != 'Y' or synced is null) and origen = 'native' and abono>0 ", A_TrxName)
		.setOrderBy("created")
		.list();
System.out.println("Ejecución de Procesa Cobro (CCobroBridgeNativeAutocomplete)::" + (new Date().toString() ) );
for(GenericPO cob in legacyCobroList){
    workNumber = workNumber + 1;
    System.out.println(sdf.format(new Date()) + " -> [" + workNumber.toString() + "] Procesa Cobro===>>> [" + workNumber + " de " + legacyCobroList.size() + "] ==> "+ cob.get_ValueAsInt("legacy_cobro_id") + " Fecha==> " + cob.get_Value("operacion").toString() );
    if(workNumber==350) 
        break;
    
    org = MOrg.get(A_Ctx, cob.get_ValueAsInt("AD_Org_ID"));
    String orgKey = org.getDescription();
    int currentLocalID = cob.get_ValueAsInt("local_id");
    if(currentLocalID<=0){
        // Obtener BP
        MBPartner bp = new Query(A_Ctx, "C_BPartner", "C_BPartner_ID = ? ", A_TrxName)
            .setParameters([ cob.get_Value("c_bpartner_id") ])
            .first();
        int locsize = bp==null ? 0 : bp.getLocations(false).size();
        if(null!=bp && locsize>0){
            // Invoice + InvoiceLine
            Integer loc_id = bp.getLocations(false)[0].get_ID() ;
            BigDecimal monto = (BigDecimal) cob.get_Value("abono");
            MUser usr = MUser.get(A_Ctx, cob.getCreatedBy());
            int bank_account_id = usr.get_ValueAsInt("C_BankAccount_ID");
            GenericPO car = new Query(A_Ctx, "legacy_cartera", " legacy_cartera_id = ? ", A_TrxName)
                .setParameters([ cob.get_ValueAsInt("id_cartera") ])
                .first();
            MInvoice inv = new Query(A_Ctx, "C_Invoice", " c_invoice_id = ? ", A_TrxName)
                .setParameters([ car.get_Value("local_id") ])
                .first();
            if(car!=null && null!=inv && car.get_Value("saldo")>=0){
                // Let's create a payment
                int Payment_C_DocType_ID = 1000050; // Recibo de Clientes
                MPayment mp = new MPayment(A_Ctx, 0, A_TrxName);
                mp.setAD_Org_ID(  org.get_ID() );
                mp.setDescription("Recibo de Abono por Financiamiento " + inv.getDocumentNo());
                mp.setC_BPartner_ID(bp.get_ID());
                mp.setDateAcct(cob.get_Value("operacion"));
                mp.setDateTrx(cob.get_Value("operacion"));
                mp.setC_BankAccount_ID(bank_account_id); 
                mp.setTenderType("X");
                mp.setAmount(209, monto );
                mp.setC_DocType_ID(Payment_C_DocType_ID);
                mp.set_ValueOfColumn("createdby", cob.getCreatedBy());
                mp.set_ValueOfColumn("updatedby", cob.getUpdatedBy());
                mp.setC_Invoice_ID(inv.get_ID());
                mp.save(A_TrxName); 
                mp.processIt(DocAction.ACTION_Complete);

                // Update current legacy cartera
                // car.set_ValueOfColumn("ad_org_id", org.get_ID());
                // BigDecimal localId = (BigDecimal) mp.get_ValueOfColumn("C_Payment_ID");
                BigDecimal localId = (BigDecimal) mp.get_Value("C_Payment_ID");
                cob.set_ValueOfColumn("synced", "Y");
                cob.set_ValueOfColumn("local_id", localId);
                cob.save(A_TrxName);

                // log.info("Invoice updated: " + inv.getDocumentNo() + " No: " + workNumber);
                String infoData = ( new StringBuilder()
                .append("No.: " + workNumber )
                .append("Cartera: " + car.get_ID() )
                .append(" Invoice: "  + inv.getDocumentNo())
                .append(" Payment: " + mp.getDocumentNo()) 
                .append(" OrgName: "  + org.getName())  
                .append(" BPName: " + bp.getName())
                .toString() );

                A_ProcessInfo.addLog(0,null,null,infoData);
                System.out.print(infoData + "\n");
            }else{
                System.out.println(sdf.format(new Date()) + " -> [" + workNumber.toString() + "] Procesa Cobro===>>> [" + workNumber + " de " + legacyCobroList.size() + "] ==> "+ cob.get_ValueAsInt("legacy_cobro_id") + " Fecha==> " + cob.get_Value("operacion").toString() + " ::: DESEMBOLSO NO HA SIDO PROCESADO" + org.getValue() );
                if(car!=null) 
                    System.out.print(" - Id Car::" + car.get_Value("legacy_cartera_id") + ":: Saldo :: " + car.get_Value("saldo") + " BPName: " + bp.getName() );
            }
        }
    }
    
}
// PO.clearCrossTenantSafe();
result = "Se migraron " + legacyCobroList.size() + " datos de LegacyCobro a C_Payment";