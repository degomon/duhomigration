/**
GastoBridgeNativeAutocomplete
Proceso para sincronizar legacy_gasto con C_Payment con Autocomplete
Solo para legacy_gasto de tipo native
20210812 - First version
Rule: groovy:GastoBridgeNativeAutocomplete
Class: @script:groovy:GastoBridgeNativeAutocomplete
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
List<GenericPO> legacyGastoList = new Query(A_Ctx, "legacy_gasto",
         " (synced != 'Y' or synced is null) and origen = 'native' ", A_TrxName)
		.setOrderBy("created")
		.list();
System.out.println("Ejecución de Procesa Gasto (GastoBridgeNativeAutocomplete)::" + (new Date().toString() ) );
for(GenericPO gas in legacyGastoList){
    workNumber = workNumber + 1;
    System.out.println(sdf.format(new Date()) + " -> [" + workNumber.toString() + "] Procesa Gasto===>>> [" + workNumber + " de " + legacyGastoList.size() + "] ==> "+ gas.get_ValueAsInt("legacy_gasto_id") + " Fecha==> " + gas.get_Value("fecha").toString() );
    /* if(workNumber==3) 
        break; */
    org = MOrg.get(A_Ctx, gas.get_ValueAsInt("AD_Org_ID"));
    String orgKey = org.getDescription();
    int currentLocalID = gas.get_ValueAsInt("local_id");

    if(currentLocalID<=0){
        // Obtener BP
        MBPartner bp = new Query(A_Ctx, "C_BPartner", "C_BPartner_ID = ? ", A_TrxName)
            .setParameters([ gas.get_Value("c_bpartner_id") ])
            .first();
        if(null!=bp){
            BigDecimal monto = (BigDecimal) gas.get_Value("monto");
            MUser usr = MUser.get(A_Ctx, gas.getCreatedBy());
            int bank_account_id = usr.get_ValueAsInt("C_BankAccount_ID");
            // Let's create a payment
            int Payment_C_DocType_ID = 1000009; // AP Payment -> Gasto 
            MPayment mp = new MPayment(A_Ctx, 0, A_TrxName);
            mp.setAD_Org_ID(  org.get_ID() );
            mp.setDescription("Gasto en Ruta | " + usr.getName());
            mp.setC_BPartner_ID(bp.get_ID());
            mp.setDateAcct(gas.get_Value("fecha"));
            mp.setDateTrx(gas.get_Value("fecha"));
            mp.setC_BankAccount_ID(bank_account_id); 
            mp.setTenderType("X");
            mp.setAmount(209, monto );
            mp.setC_DocType_ID(Payment_C_DocType_ID);
            mp.set_ValueOfColumn("createdby", gas.getCreatedBy());
            mp.set_ValueOfColumn("updatedby", gas.getUpdatedBy());
            mp.setC_Charge_ID(gas.get_Value("C_Charge_ID"));
            mp.save(A_TrxName); 
            mp.processIt(DocAction.ACTION_Complete);
            // Update current legacy cartera
            // car.set_ValueOfColumn("ad_org_id", org.get_ID());
            // BigDecimal localId = (BigDecimal) mp.get_ValueOfColumn("C_Payment_ID");
            BigDecimal localId = (BigDecimal) mp.get_Value("C_Payment_ID");
            gas.set_ValueOfColumn("synced", "Y");
            gas.set_ValueOfColumn("local_id", localId);
            gas.save(A_TrxName);

            // log.info("Invoice updated: " + inv.getDocumentNo() + " No: " + workNumber);
            String infoData = ( new StringBuilder()
            .append("No.: " + workNumber )
            .append("Gasto: " + gas.get_ID() )
            .append(" Payment: " + mp.getDocumentNo()) 
            .append(" OrgName: "  + org.getName())  
            .append(" BPName: " + bp.getName())
            .toString() );

            A_ProcessInfo.addLog(0,null,null,infoData);
            System.out.print(infoData + "\n");
        
        }
    }
    
}
// PO.clearCrossTenantSafe();
result = "Se migraron " + legacyGastoList.size() + " datos de LegacyCobro a C_Payment";