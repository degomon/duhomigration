/**
CarteraBridgeNativeSingle
Proceso para sincronizar legacy_cartera con C_Invoice y C_Payment
Solo para legacy_cartera de tipo native
20210616 - First version
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
import java.util.logging.Level;
import org.compiere.process.ProcessInfoParameter;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.process.DocAction;

CLogger log = CLogger.getCLogger(GenericPO.class);
// PO.setCrossTenantSafe();

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

int workNumber = 0;
List<GenericPO> legacyCartera = new Query(A_Ctx, "legacy_cartera",
         " (synced != 'Y' or synced is null) and ad_org_id = ? and origen = 'native' and aprobado='Y' ", A_TrxName)
		.setParameters([ orgid ])
        .setOrderBy("created")
		.list();

for(GenericPO car in legacyCartera){
    workNumber = workNumber + 1;
    System.out.println("Entra a " + workNumber + " BPID:> " + car.get_Value("c_bpartner_id"));
    /* if(workNumber==4) 
        break; */
    StringBuilder sb = new StringBuilder();
    org = MOrg.get(A_Ctx, car.get_ValueAsInt("AD_Org_ID"));
    String orgKey = org.getDescription();
    int currentLocalID = car.get_ValueAsInt("local_id");
    if(currentLocalID<=0){
        // Obtener BP
        int Nota_C_DocType_ID = 1000048;  // Financiamiento
        MBPartner bp = new Query(A_Ctx, "C_BPartner", "C_BPartner_ID = ? ", A_TrxName)
            .setParameters([ car.get_Value("c_bpartner_id") ])
            .first();
        int locsize = bp==null ? 0 : bp.getLocations(false).size();
        if(null!=bp && locsize>0){
        // Invoice + InvoiceLine
        Integer loc_id = bp.getLocations(false)[0].get_ID() ;
        BigDecimal tasa = car.get_Value("tasa");
        BigDecimal monto = (BigDecimal) car.get_Value("monto");
        BigDecimal montototal = (BigDecimal) car.get_Value("montototal");
        BigDecimal interes = montototal.subtract(monto);
        MUser usr = MUser.get(A_Ctx, car.getCreatedBy());
		int bank_account_id = usr.get_ValueAsInt("C_BankAccount_ID");
        
        int diascre = car.get_Value("dias_cre");
        int payment_term_id = 1000000; // Inmediato
        if(diascre==23)
            payment_term_id = 1000001;
        if(diascre==30)
            payment_term_id = 1000002;
        if(diascre==50)
            payment_term_id = 1000003;
        MInvoice invoice = new MInvoice(A_Ctx, 0, A_TrxName);
        invoice.setDescription("");
        invoice.setAD_Org_ID( org.get_ID() ); 
        // invoice.setDocumentNo(docKey); -> Let it be automatic
        // invoice.setPOReference(docKey);
        invoice.setDateAcct(car.get_Value("fecha"));
        invoice.setDateInvoiced(car.get_Value("fecha"));
        invoice.setC_DocType_ID(Nota_C_DocType_ID);
        invoice.setC_DocTypeTarget_ID(Nota_C_DocType_ID);
        invoice.setC_Currency_ID(209);
        invoice.setC_BPartner_ID(bp.get_ID());
        invoice.setC_BPartner_Location_ID( loc_id );
        invoice.setC_PaymentTerm_ID(payment_term_id);
        invoice.setC_ConversionType_ID(114); // Spot
        invoice.setSalesRep_ID(1000000); // Admin 
        invoice.setM_PriceList_ID(1000001); // Sales Std Price List
        invoice.setIsSOTrx(true);
        invoice.set_ValueOfColumn("legacy_id", car.get_ValueAsInt("legacy_cartera_id"));
        invoice.set_ValueOfColumn("createdby", car.getCreatedBy());
		invoice.set_ValueOfColumn("updatedby", car.getUpdatedBy());
        // invoice.set_ValueOfColumn("legacy_data", car.get_ValueAsInt("legacy_cartera_id"));
        invoice.save(A_TrxName);

        MInvoiceLine iLine = new MInvoiceLine(invoice);
        iLine.setDescription("Monto Principal");
        iLine.setLine(10);
        iLine.setAD_Org_ID( org.get_ID() );
        iLine.setC_Charge_ID(1000028); // Monto Principal
        iLine.setQtyEntered(BigDecimal.ONE);
        iLine.setQtyInvoiced(BigDecimal.ONE);
        iLine.setC_Tax_ID(1000000); // Standard
        iLine.setPrice( monto );
        iLine.setPriceActual( monto );
        iLine.set_ValueOfColumn("createdby", car.getCreatedBy());
		iLine.set_ValueOfColumn("updatedby", car.getUpdatedBy());
        iLine.save(A_TrxName);

        MInvoiceLine iLineInteres = new MInvoiceLine(invoice);
        iLineInteres.setDescription("Interés");
        iLineInteres.setLine(20);
        iLineInteres.setAD_Org_ID( org.get_ID() );
        iLineInteres.setC_Charge_ID(1000029); // Interés
        iLineInteres.setQtyEntered(BigDecimal.ONE);
        iLineInteres.setQtyInvoiced(BigDecimal.ONE);
        iLineInteres.setC_Tax_ID(1000000); // Standard
        iLineInteres.setPrice( interes );
        iLineInteres.setPriceActual( interes );
        iLineInteres.set_ValueOfColumn("createdby", car.getCreatedBy());
		iLineInteres.set_ValueOfColumn("updatedby", car.getUpdatedBy());
        iLineInteres.save(A_TrxName);
        
        invoice.processIt(DocAction.ACTION_Complete);

        // Let's create a payment
        int Payment_C_DocType_ID = 1000049;
        MPayment mp = new MPayment(A_Ctx, 0, A_TrxName);
        mp.setAD_Org_ID(  org.get_ID() );
        mp.setDescription("Desembolso por Financiamiento " + invoice.getDocumentNo());
        // mp.setDocumentNo(String.valueOf(docKey));
        mp.setC_BPartner_ID(bp.get_ID());
        mp.setDateAcct(car.get_Value("fecha"));
        mp.setDateTrx(car.get_Value("fecha"));
        mp.setC_BankAccount_ID(bank_account_id); 
        mp.setTenderType("X");
        mp.setAmount(209, monto );
        mp.setC_Charge_ID(1000030);
        mp.setC_DocType_ID(Payment_C_DocType_ID);
        mp.set_ValueOfColumn("createdby", car.getCreatedBy());
		mp.set_ValueOfColumn("updatedby", car.getUpdatedBy());
        mp.save(A_TrxName);
        mp.processIt(DocAction.ACTION_Complete);

        // Update current legacy cartera
        // car.set_ValueOfColumn("ad_org_id", org.get_ID());
        car.set_ValueOfColumn("synced", "Y");
        car.set_ValueOfColumn("local_id", invoice.get_ID());
        car.set_ValueOfColumn("C_BPartner_ID", bp.get_ID());
        car.set_ValueOfColumn("payment_id", mp.get_ID());
        // car.setAD_Org_ID( org.get_ID() );
        car.save(A_TrxName);

        log.info("Invoice created: " + invoice.getDocumentNo() + " No: " + workNumber);

        A_ProcessInfo.addLog(0,null,null,"No. " + workNumber.toString()
        + " Cartera: " + car.get_ID() 
        + " Invoice: "  + invoice.getDocumentNo() 
        + " Payment: " + mp.getDocumentNo() 
        + " OrgName: "  + org.getName()  
        +  " BPName: " + bp.getName());
        
        }
    }
    
}
// PO.clearCrossTenantSafe();
result = "Se migraron " + legacyCartera.size() + " datos de LegacyCartera a C_Invoice";