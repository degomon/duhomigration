/**
20210602 - Bridge from legacy_territorio to cv_ruta
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

CLogger log = CLogger.getCLogger(GenericPO.class);

MOrg org = null;
List<GenericPO> legacyTerritorios = new Query(A_Ctx, "legacy_territorio", " synced != 'Y' or synced is null ", A_TrxName)
		.setOrderBy("created")
		.list();
for(GenericPO terr in legacyTerritorios){
    String sb = "";
    org = MOrg.get(A_Ctx, terr.get_ValueAsInt("AD_Org_ID"));
    GenericPO region = new Query(A_Ctx,"cv_region", "AD_Org_ID = ?", A_TrxName)
        .setParameters(org.getAD_Org_ID())
        .first();
    GenericPO rutaCheck = new Query(A_Ctx, "cv_ruta"," id_territorio = ? and ad_org_id = ?", A_TrxName)
        .setParameters([terr.get_ValueAsInt("id_territorio"), org.getAD_Org_ID()])
        .first();

    // Si existe, actualizar local_id
    if(null==rutaCheck){
        // A_ProcessInfo.addLog(0,null,null,"BP SE CREA " + terr.get_ValueAsString("nombre"));
        sb = sb + "| Territorio SE CREA";
        GenericPO rutaNueva = new GenericPO("cv_ruta", A_Ctx, 0);
        rutaNueva.set_ValueOfColumn("cv_region_id", region.get_ID());
        rutaNueva.set_ValueOfColumn("name", terr.get_ValueAsString("zona"));
        rutaNueva.setAD_Org_ID(terr.getAD_Org_ID());
        rutaNueva.set_ValueOfColumn("description", terr.get_ValueAsString("codigo"));
        rutaNueva.set_ValueOfColumn("id_territorio", terr.get_ValueAsInt("id_territorio"));
        rutaNueva.set_ValueOfColumn("legacy_id", BigDecimal.valueOf(terr.get_ID()));
        rutaNueva.save(A_TrxName);

        terr.set_ValueOfColumn("cv_ruta_id", rutaNueva.get_ID());
        terr.set_ValueOfColumn("synced", "Y");
        terr.save(A_TrxName);
    }
    A_ProcessInfo.addLog(0,null,null,"OrgName: "  + org.getName()  +  " Terr Nombre: " + terr.get_ValueAsString("zona") + sb);
    log.severe(sb.toString());
}
result = "Se recorrieron " + legacyTerritorios.size() + " para migrar";