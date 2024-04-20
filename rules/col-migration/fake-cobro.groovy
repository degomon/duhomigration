/**
MigrateFakeCobro
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
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MPayment;
import org.compiere.model.MBankAccount;
import org.compiere.util.Env;
import org.compiere.util.CLogger;
import java.util.logging.Level;
import org.compiere.process.ProcessInfoParameter;
import org.adempiere.exceptions.AdempiereException;
import java.text.SimpleDateFormat;
import java.sql.Timestamp;
import java.util.UUID

String generateUUIDv4() {
    UUID uuid = UUID.randomUUID()
    return uuid.toString()
}

CLogger log = CLogger.getCLogger(GenericPO.class);
MOrg org = null;
int orgid = 0;
ProcessInfoParameter[] para = A_Parameter;
SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy")
Date recordDate = sdf.parse("24/10/2023")
Timestamp recordDateTS = new Timestamp(recordDate.getTime())
for (int i = 0; i < para.length; i++) {
    String name = para[i].getParameterName();
    if (para[i].getParameter() == null){
    }
    else if (name.equals("orgid"))
        orgid = para[i].getParameterAsInt();
    else
        log.log(Level.SEVERE, "Unknown Parameter: " + name);
}

int workNumber = 0;
List<GenericPO> legacyCartera = new Query(A_Ctx, "legacy_cartera", " ( origen = 'migrado' ) and saldo>0", A_TrxName)
		.setOrderBy("created")
		.list();
for(GenericPO car in legacyCartera){
    workNumber = workNumber + 1;
    BigDecimal legacy_cartera_id = car.get_Value("legacy_cartera_id");

    System.out.println("[" + workNumber + " of "  + legacyCartera.size() + "]" + "Trying to migrate LegacyCartera [" + legacy_cartera_id.toString()  + "]");
    StringBuilder sb = new StringBuilder();
    org = MOrg.get(A_Ctx, car.get_ValueAsInt("AD_Org_ID"));
    String orgKey = org.getDescription();
    int currentLocalID = car.get_ValueAsInt("legacy_id");
    String sqlIdCobro = "SELECT coalesce(max(id_cobro)+1, 1000000) as idmax FROM legacy_cobro where ad_client_id = ?";
    int idCobro = DB.getSQLValue(A_TrxName, sqlIdCobro, 1000000);  
    
    GenericPO cobro = new GenericPO("legacy_cobro", A_Ctx, 0); 
    
    cobro.set_ValueOfColumn("id_cobro", new BigDecimal(idCobro));
    cobro.set_ValueOfColumn("id_cartera", new BigDecimal(car.get_ID()));
    cobro.set_ValueOfColumn("abono", car.get_Value("abono"));
    cobro.set_ValueOfColumn("cuota", car.get_Value("cuota"));
    cobro.set_ValueOfColumn("operacion", recordDateTS);
    cobro.set_ValueOfColumn("creadoel", recordDateTS );
    cobro.set_ValueOfColumn("ad_org_id", car.getAD_Org_ID());
    cobro.set_ValueOfColumn("c_bpartner_id", car.get_Value("c_bpartner_id"));
    cobro.set_ValueOfColumn("masterkey", generateUUIDv4());  
    cobro.set_ValueOfColumn("synced", "Y");  
    cobro.set_ValueOfColumn("origen", "migrado");  
    cobro.set_ValueOfColumn("cobrostatus", "valido");  
    cobro.set_ValueOfColumn("syncedtocloud", "Y"); 
    cobro.save(car.get_TrxName());

    String mensajeProc = "PROCESANDO: " + car.get_ID()  + "  [OK] ";
}
result = "Se migraron " + legacyCartera.size() + " datos de LegacyCartera a Cobro Fake";