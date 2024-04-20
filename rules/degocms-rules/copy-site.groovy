/**
Proceso para copiar configuraciones y bloques de un sitio a otro
a partir de un sitio inicial
@Name: CopySite
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
import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

CLogger log = CLogger.getCLogger(GenericPO.class);

MOrg org = null;
MCity city = null;
int seedid = 0;
int targetid = 0;

ProcessInfoParameter[] para = A_Parameter;
for (int i = 0; i < para.length; i++) {
    String name = para[i].getParameterName();
    
    if (para[i].getParameter() == null){

    }
    else if (name.equals("seedid"))
        seedid = para[i].getParameterAsInt();
	else if (name.equals("targetid"))
        targetid = para[i].getParameterAsInt();
    else
        log.log(Level.SEVERE, "Unknown Parameter: " + name);
}

GenericPO seedSite = new Query(A_Ctx, "aab_site", "aab_site_id=?", A_TrxName)
		.setParameters([seedid])
        .first();

if(seedSite==null || seedid==0){
    result = "ERROR: No seed site " + seedid;
    return;
}

GenericPO targetSite = new Query(A_Ctx, "aab_site", "aab_site_id=?", A_TrxName)
		.setParameters([targetid])
        .first();

if(targetSite==null || targetid==0){
    result = "ERROR: No target site " + targetid;
    return;
}


String sqlBlocks = "select b.aab_site_block_id, " +
	" b.blockcode, " +
	" b.blockvalue " +
    " from aab_site_block b " +
    " where b.aab_site_id = ? ";
	
PreparedStatement psmtBlock = DB.prepareStatement(sqlBlocks, A_TrxName);
psmtBlock.setInt(1, seedid);
psmtBlock.setMaxRows(50000);
ResultSet rsBlock = psmtBlock.executeQuery();
while (rsBlock.next()) {
	GenericPO newBlock = new GenericPO("aab_site_block", A_Ctx, 0);
	newBlock.set_ValueOfColumn("blockcode", rsBlock.getString("blockcode"));
	newBlock.set_ValueOfColumn("blockvalue", rsBlock.getString("blockvalue"));
	newBlock.set_ValueOfColumn("aab_site_id", BigDecimal.valueOf(targetid));
	newBlock.save(A_TrxName);
	A_ProcessInfo.addLog(0,null,null, "Bloque copiado: " +  rsBlock.getString("blockcode") );
}
rsBlock.close();

String sqlConfs = "select b.aab_site_conf_id, " +
	" b.keycode, " +
	" b.keyvaluestr " +
    " from aab_site_conf b " +
    " where b.aab_site_id = ? ";
	
PreparedStatement psmtConf = DB.prepareStatement(sqlConfs, A_TrxName);
psmtConf.setInt(1, seedid);
psmtConf.setMaxRows(50000);
ResultSet rsConf = psmtConf.executeQuery();
while (rsConf.next()) {
	GenericPO newConf = new GenericPO("aab_site_conf", A_Ctx, 0);
	newConf.set_ValueOfColumn("keycode", rsConf.getString("keycode"));
	newConf.set_ValueOfColumn("keyvaluestr", rsConf.getString("keyvaluestr"));
	newConf.set_ValueOfColumn("aab_site_id", BigDecimal.valueOf(targetid));
	newConf.save(A_TrxName);
	A_ProcessInfo.addLog(0,null,null, "Conf copiado: " +  rsConf.getString("keycode") );
	
}
rsConf.close();


result = "Configs copiadas ";