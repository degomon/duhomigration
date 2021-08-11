/**
Proceso para sincronizar legacy_cartera con C_Invoice
20210725 - Org is a parameter now
20210421 - First version
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
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MPayment;
import org.compiere.model.MBankAccount;
import org.compiere.util.Env;
import org.compiere.util.CLogger;
import java.util.logging.Level;
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

int workNumber = 0;
List<GenericPO> legacyCartera = new Query(A_Ctx, "legacy_cartera", " ( synced != 'Y' or synced is null ) and AD_Org_ID = ? and saldo>0", A_TrxName)
        .setParameters([orgid])
		.setOrderBy("created")
		.list();
for(GenericPO car in legacyCartera){
    workNumber = workNumber + 1;
    BigDecimal legacy_cartera_id = car.get_Value("legacy_cartera_id");
    /* if(workNumber==10) 
        break; */
    System.out.println("Trying to migrate LegacyCartera [" + legacy_cartera_id.toString()  + "]");
    StringBuilder sb = new StringBuilder();
    org = MOrg.get(A_Ctx, car.get_ValueAsInt("AD_Org_ID"));
    String orgKey = org.getDescription();
    int currentLocalID = car.get_ValueAsInt("legacy_id");
    
    System.out.println("Getting legacy_id for [" + legacy_cartera_id.toString()  + "]");
    if(currentLocalID<=0){
        // Obtener BP
        Integer id_cliente = car.get_ValueAsInt("id_cliente");
        Integer id_cartera = car.get_ValueAsInt("id_cartera");
        System.out.println("Getting client and cartera for [" + legacy_cartera_id.toString()  + "] Cliente-Org [" + id_cliente.toString() + "-" + orgid.toString() + "]");
        GenericPO legacyCli = new Query(A_Ctx, "legacy_cliente", "id_cliente = ? and AD_Org_ID = ? ", A_TrxName)
            .setParameters([id_cliente, orgid ])
            .first();
        if(legacyCli==null){
            result = "ERROR: No hay cliente para esta Cartera (Legacy Not Found) " + id_cartera;
            return;
        }

        System.out.println("Trying to get docKey [" + legacy_cartera_id.toString()  + "]");
        BigDecimal monto = car.get_Value("monto");
        BigDecimal tasa = car.get_Value("tasa");
        BigDecimal montoTotal = car.get_Value("montototal");
        BigDecimal saldo = car.get_Value("saldo");
        String docKey = orgKey + "-" + id_cartera;
        int Nota_C_DocType_ID = 1000048;  // Financiamiento
        System.out.println("Trying to getBP [" + legacy_cartera_id.toString()  + "]");
        MBPartner bp = new Query(A_Ctx, "C_BPartner", " C_BPartner_ID = ? ", A_TrxName)
            .setParameters([ legacyCli.get_Value("C_BPartner_ID") ])
            .first();
        
        if(bp==null){
            result = "ERROR: No hay cliente para esta Cartera (BP Not Found) " + id_cartera + " Code:: " + legacyCli.toString() ;
            return;
        }
        if(null!=bp && saldo>0){
            System.out.println("BP is not NULL for [" + car.get_ValueAsInt("legacy_cartera_id").toString()  + "]");
        // Invoice + InvoiceLIne
        Integer loc_id = bp.getLocations(false)[0].get_ID() ;
        

        MInvoice invoice = new MInvoice(A_Ctx, 0, A_TrxName);
        invoice.setDescription("");
        invoice.setAD_Org_ID(car.get_ValueAsInt("AD_Org_ID")); 
        invoice.setDocumentNo(docKey);
        invoice.setPOReference(docKey);
        invoice.setDateAcct(car.get_Value("fecha"));
        invoice.setDateInvoiced(car.get_Value("fecha"));
        invoice.setC_DocType_ID(Nota_C_DocType_ID);
        invoice.setC_DocTypeTarget_ID(Nota_C_DocType_ID);
        invoice.setC_Currency_ID(100);
        invoice.setC_BPartner_ID(bp.get_ID());
        invoice.setC_BPartner_Location_ID( loc_id );
        invoice.setC_PaymentTerm_ID(1000000); // Inmediato
        invoice.setC_ConversionType_ID(114); // Spot
        invoice.setSalesRep_ID(1000000); // Admin 
        invoice.setM_PriceList_ID(1000001); // Sales Std Price List
        invoice.setIsSOTrx(false);
        invoice.set_ValueOfColumn("legacy_id", car.get_ValueAsInt("legacy_cartera_id"));
        // invoice.set_ValueOfColumn("legacy_data", car.get_ValueAsInt("legacy_cartera_id"));
        invoice.save(A_TrxName);

        MInvoiceLine iLine = new MInvoiceLine(invoice);
        iLine.setDescription("Monto Principal");
        iLine.setLine(10);
        iLine.setAD_Org_ID(car.getAD_Org_ID());
        iLine.setC_Charge_ID(1000028); // Monto Principal
        iLine.setQtyEntered(BigDecimal.ONE);
        iLine.setQtyInvoiced(BigDecimal.ONE);
        iLine.setC_Tax_ID(1000000); // Standard
        iLine.setPrice(car.get_Value("monto"));
        iLine.setPriceActual(car.get_Value("monto"));
        iLine.save(A_TrxName);

        MInvoiceLine iLineInteres = new MInvoiceLine(invoice);
        iLineInteres.setDescription("interés");
        iLineInteres.setLine(20);
        iLineInteres.setAD_Org_ID(car.getAD_Org_ID());
        iLineInteres.setC_Charge_ID(1000029); // Interés
        iLineInteres.setQtyEntered(BigDecimal.ONE);
        iLineInteres.setQtyInvoiced(BigDecimal.ONE);
        iLineInteres.setC_Tax_ID(1000000); // Standard
        iLineInteres.setPrice(car.get_Value("valorinteres"));
        iLineInteres.setPriceActual(car.get_Value("valorinteres"));
        iLineInteres.save(A_TrxName);

        // Let's create a payment
        MBankAccount bac = new Query(A_Ctx, "C_BankAccount", "AD_Org_ID = ? ", A_TrxName)
        .setParameters([ orgid ])
        .first();

        int Payment_C_DocType_ID = 1000049;
        MPayment mp = new MPayment(A_Ctx, 0, A_TrxName);
        mp.setAD_Org_ID(  org.get_ID() );
        mp.setDescription("Desembolso por Financiamiento " + invoice.getDocumentNo());
        // mp.setDocumentNo(String.valueOf(docKey));
        mp.setC_BPartner_ID(bp.get_ID());
        mp.setDateAcct(car.get_Value("fecha"));
        mp.setDateTrx(car.get_Value("fecha"));
        mp.setC_BankAccount_ID(bac.get_ID()); 
        mp.setTenderType("X");
        mp.setAmount(209, monto );
        mp.setC_Charge_ID(1000030);
        mp.setC_DocType_ID(Payment_C_DocType_ID);
        mp.set_ValueOfColumn("createdby", car.getCreatedBy());
        mp.set_ValueOfColumn("updatedby", car.getUpdatedBy());
        mp.save(A_TrxName);

        // Update current legacy cartera
        car.set_ValueOfColumn("synced", "Y");
        car.set_ValueOfColumn("local_id", invoice.get_ID());
        car.set_ValueOfColumn("C_BPartner_ID", bp.get_ID());
        car.save(A_TrxName);

        log.info("Invoice created: " + invoice.getDocumentNo() + " No: " + workNumber);
        System.out.println("Migrated " + workNumber.toString() + " carteras from " + legacyCartera.size().toString() );

        A_ProcessInfo.addLog(0,null,null,"Cartera parsed: " + car.get_ID() + " OrgName: "  + org.getName()  +  " BPName: " + bp.getName());
        
        }
    }
    
}
result = "Se migraron " + legacyCartera.size() + " datos de LegacyCartera a C_Invoice";