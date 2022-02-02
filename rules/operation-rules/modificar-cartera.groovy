/**
Proceso para modificar una Cartera existente
@Name: ModificarCartera
groovy:ModificarCartera
20220201f - first version
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
int localid = 0;
BigDecimal carteraid;
BigDecimal nuevoMonto;
BigDecimal nuevoPlazo;
BigDecimal nuevaTasa;

ProcessInfoParameter[] para = A_Parameter;
for (int i = 0; i < para.length; i++) {
    String name = para[i].getParameterName();
    if (para[i].getParameter() == null){
    }
    else if (name.equals("cartera"))
        carteraid = para[i].getParameterAsInt();
    else if (name.equals("nuevomonto"))
        nuevoMonto = para[i].getParameterAsBigDecimal();
    else if (name.equals("nuevoplazo"))
        nuevoPlazo = para[i].getParameterAsBigDecimal();
        else if (name.equals("nuevatasa"))
        nuevaTasa = para[i].getParameterAsBigDecimal();
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
String sqlUpdateCartera = "update dualint set dummy =  duho_change_cartera_data(?,?,?,?) ";
Object[] params = [carteraid, nuevoMonto, nuevaTasa, nuevoPlazo];
DB.executeUpdate(sqlUpdateCartera, params, false, A_TrxName);
resultString = "Se modificó el Préstamo satisfactoriamente";

result = resultString;