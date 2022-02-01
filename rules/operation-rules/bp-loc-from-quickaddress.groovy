/**
groovy:BPLocFromQuickAddress
Crear Location from quick address
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

CLogger log = CLogger.getCLogger(GenericPO.class);
    boolean wasSaved = false; 
    String miniLog = "";
    MBPartner bp = (MBPartner) A_PO;
    String A_TrxName = bp.get_TrxName();
    org = MOrg.get(A_Ctx, bp.get_ValueAsInt("ad_org_trx_id"));
    city = new Query(A_Ctx,"C_City", "name ilike ?", A_TrxName)
				.setParameters([ org.getInfo().getFax() ])
				.first();

    String sqlCountAddress = "select count(*) from c_bpartner_location where c_bpartner_id = ? ";
    int countAddress = DB.getSQLValue(A_TrxName, sqlCountAddress, bp.get_ID());
    String candidate = bp.get_ValueAsString("quickaddress");
    String phone = bp.get_ValueAsString("quickphone");

    if(city!=null && org != null && countAddress<=0 && (null!=candidate && candidate.length()>0) ) {
        MBPartnerLocation bpLoc = new MBPartnerLocation(bp);
        MLocation loc = new MLocation(A_Ctx, 0, A_TrxName);
        loc.setAD_Org_ID(0);
        loc.setC_City_ID(city.get_ID());
        loc.setC_Region_ID(city.getC_Region_ID());
        loc.setC_Country_ID(city.getC_Region().getC_Country_ID());
        loc.setAddress1(candidate);
        loc.save(A_TrxName);

        bpLoc.setAD_Org_ID(0);
        bpLoc.setC_Location_ID(loc.get_ID());
        bpLoc.setName(city.getName());
        bpLoc.setPhone(phone);
        bpLoc.save(A_TrxName);

        wasSaved = true;
    }else{
        miniLog = "City NULL? " + (city==null) + 
        " Org Null ? " + (org==null) +
        " countAddress <= 0 ? " + (countAddress>=0) + 
        " candidate ? " + candidate; 
    }
    if(wasSaved==false)
        log.info("No se agregó localización==> " + miniLog);
    else
        log.info("Localización Added ");
    return "";