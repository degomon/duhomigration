/**
Proceso para cancelar Cobro desde Legacy_Cobro
@Name: CancelarCobroLegacy
groovy:CancelarCobroLegacy
20210708 - first version
**/

import org.compiere.model.MTable;
import org.compiere.util.DB;
import org.compiere.util.Msg;
import org.adempiere.model.GenericPO;
import org.compiere.model.Query;
import org.compiere.model.MOrg;
import org.compiere.model.MBPartner;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MLocation;
import org.compiere.model.MPayment;
import org.compiere.util.Env;
import org.compiere.util.CLogger;
import java.util.logging.Level;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.DocAction;

CLogger log = CLogger.getCLogger(GenericPO.class);

MOrg org = null;
int cobroid = 0;
int localid = 0;

ProcessInfoParameter[] para = A_Parameter;
for (int i = 0; i < para.length; i++) {
    String name = para[i].getParameterName();
    if (para[i].getParameter() == null){
    }
    else if (name.equals("cobroid"))
        cobroid = para[i].getParameterAsInt();
    else
        log.log(Level.SEVERE, "Unknown Parameter: " + name);
}

GenericPO cobroPO = new Query(A_Ctx, "legacy_cobro", "legacy_cobro_id=?", A_TrxName)
		.setParameters([cobroid])
        .first();

if(cobroPO==null || cobroid==0){
    result = "ERROR: No existe el Cobro " + cobroid;
    return;
}

String resultString = "";
org = MOrg.get(A_Ctx, cobroPO.getAD_Org_ID() );
localid = cobroPO.get_ValueAsInt("local_id");
cobroPO.set_ValueOfColumn("abono", BigDecimal.ZERO);
cobroPO.save(A_TrxName);
resultString = "Cobro en CERO. " + cobroid.toString();
if(!(localid==null || localid==0)){
    // Reversar Cobro C_Payment
    MPayment mp = new Query(Env.getCtx(),MPayment.Table_Name," C_Payment_ID = ?", A_TrxName)
				.setParameters( [ localid ])
				.firstOnly();
    if(null!=mp){
        String docNo = mp.getDocumentNo();
        if("CO".equals(mp.getDocStatus())) {
            mp.processIt(DocAction.ACTION_Reverse_Correct);
            resultString = resultString + " | Cobro revertido " + docNo ;
            A_ProcessInfo.addLog(0,null,null,"Cobro revertido " + docNo);
        }
        
        if("DR".equals(mp.getDocStatus())) {
            mp.delete(true, A_TrxName);
            resultString = resultString + " | Cobro eliminado " + docNo ;
            A_ProcessInfo.addLog(0,null,null,"Cobro eliminado " + docNo );
        }
    }
}

result = resultString;