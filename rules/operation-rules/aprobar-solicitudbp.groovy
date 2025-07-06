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

MOrg org = null;
MCity city = null;
int solicitudid = 0;

ProcessInfoParameter[] para = A_Parameter;
for (int i = 0; i < para.length; i++) {
    String name = para[i].getParameterName();
    
    if (para[i].getParameter() == null){

    }
    else if (name.equals("solicitudid"))
        solicitudid = para[i].getParameterAsInt();
    else
        log.log(Level.SEVERE, "Unknown Parameter: " + name);
}

GenericPO solicitudBP = new Query(A_Ctx, "cv_solicitudbp", "cv_solicitudbp_id=?", A_TrxName)
		.setParameters([solicitudid])
        .first();

if(solicitudBP==null || solicitudid==0){
    result = "ERROR: No existe la SolicitudBP " + solicitudid;
    return;
}


org = MOrg.get(A_Ctx, solicitudBP.getAD_Org_ID() );

if (null==org){
    result = "ERROR: Organización no es válida";
    return;
}
city = new Query(A_Ctx,"C_City", "name ilike ?", A_TrxName)
    				.setParameters([ org.getInfo().getFax() ])
    				.first(); 
if(city==null){
    result = "ERROR: No se encontró una Ciudad asociada a la Sucursal";
    return "ERROR: No se encontró una Ciudad asociada a la Sucursal";;
}
System.out.println("City was found: " + city.getName());
String cedula = solicitudBP.get_ValueAsString("cedula").replace(" ", "").replace("-", "");
String nombre = solicitudBP.get_ValueAsString("nombre");
String direccion = solicitudBP.get_ValueAsString("direccion");
String telefono = solicitudBP.get_ValueAsString("telefono");
String masterkey = solicitudBP.get_ValueAsString("masterkey");
String description = solicitudBP.get_ValueAsString("description");
int idruta = solicitudBP.get_ValueAsInt("idruta");
int idrubro = solicitudBP.get_ValueAsInt("idrubro");
BigDecimal monto = (BigDecimal) solicitudBP.get_Value("monto");
String procesado = solicitudBP.get_ValueAsString("synced");
String genero = solicitudBP.get_ValueAsString("genero");
String tipoLocalizacion = solicitudBP.get_ValueAsString("tipo_localizacion");

// Ver si el BP existe por Cédula
MBPartner bpCed = new Query(A_Ctx, "C_BPartner", "taxid = ?", A_TrxName)
.setParameters([solicitudBP.get_ValueAsString("cedula")])
.first();

System.out.println("Procesado: " + procesado);

if(null!=bpCed){
    result = "ERROR: La cédula " + cedula + " ya está registrada con el nombre: " + bpCed.getName();
    return "ERROR: La cédula " + cedula + " ya está registrada con el nombre: " + bpCed.getName();
}
 
if("Y".equals(procesado)){
    result = "ERROR: Esta solicitud ya fue procesada anteriormente.";
    return "ERROR: Esta solicitud ya fue procesada anteriormente.";
}



// Create BP 
MBPartner bp = new MBPartner(A_Ctx, 0, A_TrxName);
// bp.setValue(bpValue);
bp.setName(nombre);
bp.setDescription(description);
bp.setTaxID(cedula);
bp.setIsCustomer(true);
bp.setAD_Org_ID(0);
bp.set_ValueOfColumn("quickaddress", direccion);
bp.set_ValueOfColumn("quickphone", telefono );
bp.set_ValueOfColumn("ad_org_trx_id", solicitudBP.getAD_Org_ID());
bp.set_ValueOfColumn("cv_ruta_id", idruta);
if(null!=genero && genero.length()>0){
    log.log(Level.INFO, ">> Genero: " + genero);
    bp.set_ValueOfColumn("genero", genero);
}
    
if(null!=tipoLocalizacion && tipoLocalizacion.length()>0){
    log.log(Level.INFO, ">> Tipo Localizacion: " + tipoLocalizacion);
    bp.set_ValueOfColumn("tipo_localizacion", tipoLocalizacion);
}
    
bp.setC_BP_Group_ID(idrubro);
bp.setSO_CreditLimit(monto);
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

if(bp!=null) {
    solicitudBP.set_ValueOfColumn("synced", "Y");
    solicitudBP.save(A_TrxName);
}

result = "Se creó satisfactoriamente el Nuevo Tercero: " + bp.getValue() + " Nombre: " + nombre;