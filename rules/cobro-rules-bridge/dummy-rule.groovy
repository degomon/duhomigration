/**
CobroBridgeNativeAutocomplete
Proceso para sincronizar legacy_cobro con C_Payment con Autocomplete
Solo para legacy_cobro de tipo native
20240810 - Incluir tiempo de ejecución
20231030 - Fix para hacer skip cuando c_bankaccount_id no existe
20220212 - Fix para tomar solo saldo sincronizado como ref.
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
import groovy.time.TimeCategory;
import groovy.time.TimeDuration;

CLogger log = CLogger.getCLogger(GenericPO.class);
// PO.setCrossTenantSafe();
Date start = new Date();
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

A_ProcessInfo.addLog(0, null, null, "Inicia (CCobroBridgeNativeAutocomplete)::" + (new Date().toString() ) );
System.out.println("Inicia Procesa Cobro (CCobroBridgeNativeAutocomplete)::" + (new Date().toString() ) );

result = "OK Folks";