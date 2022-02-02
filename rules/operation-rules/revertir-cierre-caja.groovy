/**
Proceso para Revertir Cierre de Caja para una Sucursal desde una Fecha x
@Name: RevertirCierreCaja
groovy:RevertirCierreCaja
@script:groovy:RevertirCierreCaja
20220201f - first version
**/

import org.compiere.model.MTable;
import org.compiere.util.DB;
import org.compiere.util.Msg;
import org.adempiere.model.GenericPO;
import org.compiere.model.Query;
import org.compiere.model.MOrg;
import org.compiere.util.Env;
import org.compiere.util.CLogger;
import java.util.logging.Level;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.DocAction;
import java.sql.Timestamp;
import java.util.Date;

CLogger log = CLogger.getCLogger(GenericPO.class);

MOrg org = null;
int orgid = 0;
Timestamp fechaTS;

ProcessInfoParameter[] para = A_Parameter;
for (int i = 0; i < para.length; i++) {
    String name = para[i].getParameterName();
    if (para[i].getParameter() == null){
    }
    else if (name.equals("orgid"))
        orgid = para[i].getParameterAsInt();
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
Date fecha = new Date(fechaTS.getTime());
if(fechaTS==null || fecha==null){
    result = "ERROR: Debe proporcionar una Fecha";
    return;
}

String resultString = "";
String sqlRevertirCierre = "update dualint set dummy = duho_revertir_cierre(?,?) ";
Object[] params = [orgid, fechaTS];
DB.executeUpdate(sqlRevertirCierre, params, false, A_TrxName);
resultString = "Se revirtió satisfactoriamente la caja desde la Fecha solicitada";

result = resultString;