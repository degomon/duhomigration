/**
Proceso para Importar Clientes vía CSV
@Name: ImportBPFromCSV
20230727 - Importar BP de un archivo CSV
20231029 - improvement in message when cedula already exists
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
import java.util.Scanner;



CLogger log = CLogger.getCLogger(GenericPO.class);
String csvFile = "";
ProcessInfoParameter[] para = A_Parameter;
for (int i = 0; i < para.length; i++) {
    String name = para[i].getParameterName();
    
    if (para[i].getParameter() == null){

    }
    else if (name.equals("csvfile"))
        csvFile = para[i].getParameterAsString();
    else
        log.log(Level.SEVERE, "Unknown Parameter: " + name);
}   

List<List<String>> records = new ArrayList<>();
try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
    String line;
    while ((line = br.readLine()) != null) {
        /* String[] values = line.split(COMMA_DELIMITER);
        records.add(Arrays.asList(values)); */
        List<String> values = new ArrayList<String>(Arrays.asList(line.split(";")));
            String cedula = values.get(2);
            String nombre = values.get(3);
            String direccion = values.get(4);
            String telefono = values.get(5);
            int idruta = Integer.parseInt(values.get(7));
            int idrubro = Integer.parseInt(values.get(10));

            GenericPO ruta = new Query(A_Ctx, "cv_ruta", "cv_ruta_id=?", A_TrxName)
            .setParameters([idruta])
            .first();

            MCity city = new Query(A_Ctx,"C_City", "name ilike ?", A_TrxName)
    				.setParameters([ ruta.get_ValueAsString("description") ])
    				.first();

            // Ver si el BP existe por Cédula
            MBPartner bpCed = new Query(A_Ctx, "C_BPartner", "taxid = ?", A_TrxName)
            .setParameters([ cedula ])
            .first();

            if(null!=bpCed){
                String messageCedula = "ERROR: La cédula " + cedula + " ya está registrada con el nombre: " + bpCed.getName()  + 
                " por tanto " + nombre + "  [SKIPPED]";
                A_ProcessInfo.addLog(0,null,null, messageCedula );
            }else{

                // Create BP 
                MBPartner bp = new MBPartner(A_Ctx, 0, A_TrxName);
                // bp.setValue(bpValue);
                bp.setName(nombre);
                bp.setTaxID(cedula);
                bp.setIsCustomer(true);
                bp.setAD_Org_ID(0);
                bp.set_ValueOfColumn("quickaddress", direccion);
                bp.set_ValueOfColumn("quickphone", telefono );
                bp.set_ValueOfColumn("ad_org_trx_id", ruta.getAD_Org_ID());
                bp.set_ValueOfColumn("cv_ruta_id", idruta);
                bp.setC_BP_Group_ID(idrubro);
                // bp.setSO_CreditLimit(monto);
                bp.save(A_TrxName);

                // Create Location
                MBPartnerLocation bpLoc = new MBPartnerLocation(bp);
                MLocation loc = new MLocation(A_Ctx, 0, A_TrxName);
                loc.setAD_Org_ID(0);
                loc.setC_City_ID(city.get_ID());
                loc.setC_Region_ID(city.getC_Region_ID());
                loc.setC_Country_ID(city.getC_Region().getC_Country_ID());
                loc.setAddress1(direccion);
                loc.save(A_TrxName);

                bpLoc.setAD_Org_ID(0);
                bpLoc.setC_Location_ID(loc.get_ID());
                bpLoc.setName(city.getName());
                bpLoc.setPhone(telefono);
                bpLoc.save(A_TrxName);

                String message = String.format("BP: %s - %s - %s [ADDED]", values.get(2), values.get(3), city.getName() );
                A_ProcessInfo.addLog(0,null,null, message );
            }

            
    }
}

result = String.format("File was read %s", csvFile);