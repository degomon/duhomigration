/**
Proceso para aprobar una Solicitud de Cliente vía web
a partir de cv_solicitudbp
@Name: AprobarSolicitudBP
20240219 - Bypasss genero y tipo localizacion si no existen
20240219 - Se incorporan campos genero y tipo localizacion
20210817 - Verificar que cédula no exista
20210730 - Bridge from legacy_cliente to C_BPartner
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
import org.compiere.model.MCity;
import org.compiere.util.Env;
import org.compiere.util.CLogger;
import java.util.logging.Level;
import org.compiere.process.ProcessInfoParameter;

CLogger log = CLogger.getCLogger(GenericPO.class);

int period_id = 0;

ProcessInfoParameter[] para = A_Parameter;
for (int i = 0; i < para.length; i++) {
    String name = para[i].getParameterName();
    
    if (para[i].getParameter() == null){

    }
    else if (name.equals("period_id"))
        period_id = para[i].getParameterAsInt();
    else
        log.log(Level.SEVERE, "Unknown Parameter: " + name);
}

// make sure period_id is > 0
if(period_id==0){
    result = "ERROR: No existe el Periodo " + period_id;
    return;
}

// execute the sql function icc_credito_por_periodo
String sql = "select icc_credito_por_periodo(?)";
Object[] params = [period_id];
DB.executeUpdate(sql, params, false, A_TrxName);
resultString = "Se ejecut´o satisfactoriamente el cierre de mes CONAMI";
return resultString;