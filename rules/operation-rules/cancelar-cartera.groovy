/**
Proceso para cancelar Cobro desde Legacy_Cobro
@Name: CancelarCartera
groovy:CancelarCartera
20211012 - first version
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
int carteraid = 0;
int localid = 0;
BigDecimal carteraid;

ProcessInfoParameter[] para = A_Parameter;
for (int i = 0; i < para.length; i++) {
    String name = para[i].getParameterName();
    if (para[i].getParameter() == null){
    }
    else if (name.equals("cartera"))
        carteraid = para[i].getParameterAsInt();
    else if (name.equals("tasa"))
        tasa = para[i].getParameterAsInt();
    else
        log.log(Level.SEVERE, "Unknown Parameter: " + name);
}

GenericPO carteraPO = new Query(A_Ctx, "legacy_cartera", "legacy_cartera_id=?", A_TrxName)
		.setParameters([carteraid])
        .first();

if(carteraPO==null || carteraid==0){
    result = "ERROR: No existe el Desembolso " + carteraid;
    return;
}

String resultString = "";
org = MOrg.get(A_Ctx, carteraPO.getAD_Org_ID() );
localid = carteraPO.get_ValueAsInt("local_id");
carteraid = (BigDecimal) carteraPO.get_Value("id_cartera");
carteraPO.set_ValueOfColumn("abono", BigDecimal.ZERO);
carteraPO.save(A_TrxName);
resultString = "Cobro en CERO. " + carteraid.toString();
if(!(localid==null || localid==0)){
    // Reversar Cobro C_Payment
    MPayment mp = new Query(Env.getCtx(),MPayment.Table_Name," C_Payment_ID = ?", A_TrxName)
				.setParameters( [ localid ])
				.firstOnly();
    if(null!=mp){
        String docNo = mp.getDocumentNo();
        if("CO".equals(mp.getDocStatus())) {
            mp.processIt(DocAction.ACTION_Reverse_Correct);
            mp.setDocStatus("VO");
            mp.save();
            resultString = resultString + " | Cobro revertido " + docNo ;
            A_ProcessInfo.addLog(0,null,null,"Cobro revertido " + docNo);
            // => Poner status ANULADO (check Documentation)
        }
        
        if("DR".equals(mp.getDocStatus())) {
            mp.delete(true, A_TrxName);
            resultString = resultString + " | Cobro eliminado " + docNo ;
            A_ProcessInfo.addLog(0,null,null,"Cobro eliminado " + docNo );
        }
        // String sqlUpdateSaldoCartera = "select duho_updatesaldo_cartera(" + carteraid.toString() +  ")";
        // DB.executeUpdate(sqlUpdateSaldoCartera, A_TrxName);
    }
}

result = resultString;