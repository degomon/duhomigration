/**
20210822 - Minor fixes
20210725 - Org es ahora un parámetro
20210601 - Datos adicionales en C_BPartner
20210421 - MBParterLocation also included
20210418 - Bridge from legacy_cliente to C_BPartner
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
import org.adempiere.exceptions.AdempiereException;

CLogger log = CLogger.getCLogger(GenericPO.class);
MOrg org = null;
MCity city = null;
int orgid = 0;

ProcessInfoParameter[] para = A_Parameter;
for (int i = 0; i < para.length; i++) {
    String name = para[i].getParameterName();
    if (para[i].getParameter() == null){
    }
    else if (name.equals("orgid"))
        orgid = para[i].getParameterAsInt();
    else
        log.log(Level.SEVERE, "Unknown Parameter: " + name);
}

org = MOrg.get(A_Ctx, orgid);
if(org==null || orgid==0){
    result = "ERROR: No existe la Org " + orgid;
    return;
}


List<GenericPO> legacyClientes = new Query(A_Ctx, "legacy_cliente", " (synced != 'Y' or synced is null) and AD_Org_ID = ? ", A_TrxName)
        .setParameters([orgid])
		.setOrderBy("created")
		.list();
System.out.println("Legacy Clientes Selected ");
int i = 0;
for(GenericPO cli in legacyClientes){
    i = i+1;
    System.out.println("Migrate / Procesar Cliente [" + i.toString() +  "] from  " + legacyClientes.size() );
    String sb = "";
    city = new Query(A_Ctx,"C_City", "name ilike ?", A_TrxName)
				.setParameters([ org.getInfo().getFax() ])
				.first();

    // Cambiar vendedor o territorio, de acuerdo a implementación de cada DB origen
    GenericPO ruta = new Query(A_Ctx,"cv_ruta", "AD_Org_ID = ? and id_territorio = ? ", A_TrxName)
        .setParameters( [cli.getAD_Org_ID(), cli.get_ValueAsInt("id_vendedor")] )
        .first();    
    
    if(null==city)
        throw new AdempiereException("Ciudad no válida para Org:: " + org.getInfo().getFax());
    if(null==ruta)
        throw new AdempiereException("Ruta no es válida para Territorio: " + cli.get_ValueAsInt("id_vendedor").toString() );
    String orgKey = org.getDescription();
    String bpName = cli.get_ValueAsString("nombre");
    String bpValue = cli.get_ValueAsString("codigo");
    String bpCodeCandidate = orgKey + "-" + cli.get_ValueAsString("codigo");
    currentLocalID = cli.get_ValueAsInt("local_id");
    
    // Ver si el BP existe por Cédula
    MBPartner bp = new Query(A_Ctx, "C_BPartner", "taxid = ?", A_TrxName)
    .setParameters([cli.get_ValueAsString("cedula")])
    .first();

    // Ver si BP existe por key y nombre
    if(bp==null) 
        bp = new Query(A_Ctx, "C_BPartner", "value = ?", A_TrxName)
        .setParameters([bpCodeCandidate])
        .first();
    
    // Si existe, actualizar local_id
    if(null==bp){
        System.out.println("Trying to Insert [" + i.toString() +  "] ");
        // A_ProcessInfo.addLog(0,null,null,"BP SE CREA " + cli.get_ValueAsString("nombre"));
        sb = sb + "| BP SE CREA";
        bp = new MBPartner(A_Ctx, 0, A_TrxName);
        bp.setValue(bpCodeCandidate);
        bp.setName(cli.get_ValueAsString("nombre"));
        bp.setName2(org.getDescription());
        bp.setReferenceNo(cli.get_ValueAsString("id_cliente"));
        bp.setDescription(cli.get_ValueAsString("domicilio"));
        bp.setTaxID(cli.get_ValueAsString("cedula"));
        bp.setDUNS(cli.get_ValueAsString("telefono"));
        bp.setIsCustomer(true);
        bp.setAD_Org_ID(0);
        bp.setC_BPartner_ID(1000000);
        bp.setSO_Description(cli.get_ValueAsString("actividad"));
        bp.setReferenceNo(cli.get_ValueAsString("id_cliente"));
        bp.setPOReference(cli.get_ValueAsString("codigo"));
        bp.set_ValueOfColumn("legacy_id", (BigDecimal) cli.get_ValueAsInt("legacy_cliente_id"));
        bp.set_ValueOfColumn("quickaddress", cli.get_ValueAsString("domicilio"));
        bp.set_ValueOfColumn("quickphone", cli.get_ValueAsString("telefono"));
        bp.set_ValueOfColumn("ad_org_trx_id", cli.getAD_Org_ID());
        if(null==ruta)
            System.out.println("Ruta es Null ID [" + i.toString() +  "] ");
        else
            System.out.println("Ruta NO ES Null ID [" + i.toString() +  "] " + ruta.toString() );
        bp.set_ValueOfColumn("cv_ruta_id", ruta.get_ID());
        bp.set_ValueOfColumn("cv_region_id", ruta.get_ValueAsInt("cv_region_id"));
        bp.saveEx(A_TrxName);

        System.out.println("BP SAVED " + bp.getName());
        if(bp==null)
            System.out.println("BP IS NULL " + bpCodeCandidate);
        cli.set_ValueOfColumn("local_id", bp.get_ID());
        cli.set_ValueOfColumn("C_BPartner_ID", bp.get_ID());
        cli.set_ValueOfColumn("synced", "Y");
        cli.save(A_TrxName);
    }else{
        System.out.println("Trying to Update [" + i.toString() +  "] ");
        // A_ProcessInfo.addLog(0,null,null,"BP SE ACTUALIZA " + cli.get_ValueAsString("nombre"));
        sb = sb + "| BP UPDATED";
        bp.setSO_Description(cli.get_ValueAsString("actividad") + "-BPUPDATED");
        bp.setReferenceNo(cli.get_ValueAsString("id_cliente"));
        bp.setPOReference(cli.get_ValueAsString("codigo"));
        bp.set_ValueOfColumn("legacy_id", (BigDecimal) cli.get_ValueAsInt("legacy_cliente_id"));
        bp.set_ValueOfColumn("quickaddress", cli.get_ValueAsString("domicilio"));
        bp.set_ValueOfColumn("quickphone", cli.get_ValueAsString("telefono"));
        bp.set_ValueOfColumn("ad_org_trx_id", cli.getAD_Org_ID());
        bp.set_ValueOfColumn("cv_ruta_id", ruta.get_ID());
        bp.set_ValueOfColumn("cv_region_id", ruta.get_ValueAsInt("cv_region_id"));
        bp.saveEx(A_TrxName);

        System.out.println("BP UPDATED " + bp.getName());

        cli.set_ValueOfColumn("local_id", bp.get_ID());
        cli.set_ValueOfColumn("C_BPartner_ID", bp.get_ID());
        cli.set_ValueOfColumn("synced", "Y");
        cli.save(A_TrxName);
    }

    // Check if address exists
    String sqlCountAddress = "select count(*) from c_bpartner_location where c_bpartner_id = ? ";
    int countAddress = DB.getSQLValue(A_TrxName, sqlCountAddress, bp.get_ID());
    if(countAddress<=0) {
        sb = sb + "| ADDRESS ADDED";
        MBPartnerLocation bpLoc = new MBPartnerLocation(bp);
        MLocation loc = new MLocation(A_Ctx, 0, A_TrxName);
        loc.setAD_Org_ID(0);
        loc.setC_City_ID(city.get_ID());
        loc.setC_Region_ID(city.getC_Region_ID());
        loc.setC_Country_ID(city.getC_Region().getC_Country_ID());
        loc.setAddress1(cli.get_ValueAsString("domicilio"));
        loc.save(A_TrxName);

        bpLoc.setAD_Org_ID(0);
        bpLoc.setC_Location_ID(loc.get_ID());
        bpLoc.setName(city.getName());
        bpLoc.setPhone(cli.get_ValueAsString("telefono"));
        bpLoc.save(A_TrxName);
    }else
        sb = sb + "| ADDRESS EXISTE"; 
    
    A_ProcessInfo.addLog(0,null,null,"OrgName: "  + org.getName()  +  " Cli Nombre: " + cli.get_ValueAsString("nombre") + sb);
    log.severe(sb.toString());
    System.out.println("Migrado " + i.toString() + " de " + legacyClientes.size().toString() );
}
result = "Se recorrieron " + legacyClientes.size() + " para migrar";