/**
Process for Autocomplete Draft Payments
20210617 - First version
key: groovy:AutoCompleteDraftPayments
name: AutoCompleteDraftPayments

This process autocompletes payments in Draft State.
@params: C_DocType_ID => Document Type to Process
**/

import org.compiere.util.DB;
import org.compiere.util.Msg;
import org.compiere.model.Query;
import org.compiere.util.Env;
import org.compiere.util.CLogger;
import java.util.logging.Level;
import org.compiere.model.MPayment;
import org.compiere.model.MOrg;
import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfoParameter;
import org.adempiere.exceptions.AdempiereException;

CLogger log = CLogger.getCLogger(MPayment.class);
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


List<MPayment> draftPayments = new Query(A_Ctx, "C_Payment",
         " AD_Org_ID = ? AND DocStatus = 'DR' AND 'Y' = duho_isperiodopen( DateAcct, C_DocType_ID ) ", A_TrxName)
         .setParameters([ orgid ])
		.setOrderBy("created")
		.list();
int i = 0;
for(MPayment mp in draftPayments){
    i = i+1;
    String quickLog = new StringBuffer("====> Completando Pago (" + org.getName() + ") ")
        .append(i.toString() + " de " + draftPayments.size().toString() + " <===== ")
        .toString();
    System.out.print(quickLog );
    mp.processIt(DocAction.ACTION_Complete);
    String bpName = (mp.getC_BPartner()).getName() ;
    String currentMessage = new StringBuffer("Completed: " + mp.getDocumentNo())
            .append ( " Date: " + mp.getDateAcct().toString() )
            .append ( " TipoDoc: " + mp.getC_DocType().getName() )
            .append ( " Tercero: " +  bpName ); 
    A_ProcessInfo.addLog(0,null,null, currentMessage.toString() );
}

return "";