/**
Proceso para Importar Saldos de Clientes vía CSV
@Name: ImportSaldosCSV
20230731 - Importar Saldos BP de un archivo CSV
20231029 - verificamos si existe un legacy_cartera ya migrado para el BP
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
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.text.ParseException;
import java.util.UUID;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.TimeZone;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.text.DateFormat;

java.util.Date getDateFromString(String dateString) {
    String[] dateComponents = dateString.split("-");
    if (dateComponents.length != 3) {
        throw new IllegalArgumentException("Invalid date format");
    }

    int day = Integer.parseInt(dateComponents[2]);
    int month = Integer.parseInt(dateComponents[1]) - 1; // Calendar months are 0-based
    int year = Integer.parseInt(dateComponents[0]);

    Calendar calendar = Calendar.getInstance();
    calendar.clear(); // Clear the calendar to reset all fields
    calendar.set(Calendar.YEAR, year);
    calendar.set(Calendar.MONTH, month);
    calendar.set(Calendar.DAY_OF_MONTH, day);

    return calendar.getTime();
}



long getDiffDays(Date startDate, Date endDate) {
    Calendar startCal = Calendar.getInstance();
    startCal.time = startDate;

    Calendar endCal = Calendar.getInstance();
    endCal.time = endDate;

    // Preserve date and month if year is less than 2000, and add 2000 to the year
    if (startCal.get(Calendar.YEAR) < 2000) {
        startCal.set(Calendar.YEAR, startCal.get(Calendar.YEAR) + 2000);
    }
    if (endCal.get(Calendar.YEAR) < 2000) {
        endCal.set(Calendar.YEAR, endCal.get(Calendar.YEAR) + 2000);
    }

    // Convert back to Date objects
    Date adjustedStartDate = startCal.time;
    Date adjustedEndDate = endCal.time;

    LocalDateTime startLocalDateTime = adjustedStartDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    LocalDateTime endLocalDateTime = adjustedEndDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

    return ChronoUnit.DAYS.between(startLocalDateTime, endLocalDateTime);
}

String generateUUIDv4() {
    UUID uuid = UUID.randomUUID();
    return uuid.toString();
}




CLogger log = CLogger.getCLogger(GenericPO.class);
String csvFile = "";
TimeZone customTimeZone = TimeZone.getTimeZone("GMT-06:00");

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
// try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), "UTF8"))) {
    String line;
    int rowNumber = 0;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
    while ((line = br.readLine()) != null) {
        rowNumber++;
        /* String[] values = line.split(COMMA_DELIMITER);
        records.add(Arrays.asList(values)); */
        
        // sdf.setTimeZone(customTimeZone);

        List<String> values = new ArrayList<String>(Arrays.asList(line.split(";")));
        String fechaRawString = values.get(2).toString().trim();
        System.out.println("::::::" + rowNumber + ":::::::: Fecha raw ::::::::::::::" + fechaRawString);
        Date fecha = sdf.parse(fechaRawString);
        // Date fecha = getDateFromString(fechaRawString);
        String tarjeta = values.get(0);
        String nombre = values.get(1);
        BigDecimal monto = new BigDecimal( values.get(3) );
        BigDecimal montototal = new BigDecimal(  values.get(4) );
        BigDecimal saldo = new BigDecimal( values.get(6) );
        Date vencimiento = sdf.parse( values.get(7).toString().trim() );
        long dias_cred = getDiffDays(fecha, vencimiento);
        if(dias_cred<30)
            dias_cred = 30;
        BigDecimal cuota = montototal.divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);

        // int idruta = Integer.parseInt(values.get(7));
        // int idrubro = Integer.parseInt(values.get(10));

        // Ver si el BP existe por Nombre
        String sqlLevId = "select lev_nearest_bp( ? ) ";
        int levId = DB.getSQLValue(A_TrxName, sqlLevId, nombre);

        MBPartner bpSearch = new Query(A_Ctx, "C_BPartner", "C_BPartner_Id = ?", A_TrxName)
        .setParameters([ levId ])
        .first();

        if(null==bpSearch || 0==levId){
            String mensajeNombre = "ERROR: El BP no se encontró: " + nombre  + "  [SKIPPED]";
            A_ProcessInfo.addLog(0,null,null, mensajeNombre );
        }else{
            String mensajeProc = "PROCESANDO: " + nombre  + "  [OK] bpName -> "  +
            bpSearch.getName() + " dias_cre " + dias_cred             + 
            " Fecha: " + sdf.format(fecha) +
            " Vence: " + sdf.format(vencimiento);
            A_ProcessInfo.addLog(0,null,null, mensajeProc );

            String sqlIdCartera = "SELECT coalesce(max(id_cartera)+1, 1000000) as idmax FROM legacy_cartera where ad_org_id  = ? ";
            int idCartera = DB.getSQLValue(A_TrxName, sqlIdCartera, bpSearch.getAD_Org_ID());

            String sqlCountCartera = "SELECT count(*) FROM legacy_cartera where c_bpartner_id = ? and origen = 'migrado' ";
            int countCartera = DB.getSQLValue(A_TrxName, sqlCountCartera, bpSearch.get_ID());

            if(countCartera>0){
                String mensajeNombre = "ERROR: El BP ya tiene un préstamo migrado: " + nombre  + "  [SKIPPED]";
                A_ProcessInfo.addLog(0,null,null, mensajeNombre );
            }else{
                
                // Create Cartera record, using bp data to fulfill some fields
                GenericPO prestamo =  new GenericPO("legacy_cartera", A_Ctx, 0);
                String conceptoStr = "Migrado from tarjeta: " + tarjeta;
                prestamo.set_ValueOfColumn("fecha", new Timestamp(fecha.getTime()));
                prestamo.set_ValueOfColumn("concepto", conceptoStr);
                prestamo.set_ValueOfColumn("id_cartera", new BigDecimal(idCartera));
                prestamo.set_ValueOfColumn("dias_cre", (int) dias_cred);
                prestamo.set_ValueOfColumn("cuota", cuota);
                prestamo.set_ValueOfColumn("monto", monto);
                prestamo.set_ValueOfColumn("valorinteres", montototal.subtract(monto) );
                prestamo.set_ValueOfColumn("montototal", montototal);
                prestamo.set_ValueOfColumn("abono", montototal.subtract(saldo));
                prestamo.set_ValueOfColumn("saldo", saldo);
                prestamo.set_ValueOfColumn("tasa", montototal.subtract(monto).divide(monto, 2, RoundingMode.HALF_UP));
                prestamo.set_ValueOfColumn("creadoel", new Timestamp(new Date().getTime()));
                prestamo.set_ValueOfColumn("ad_org_id", bpSearch.get_ValueAsInt("ad_org_trx_id"));
                prestamo.set_ValueOfColumn("createdby", 0); // buscar id rutero
                prestamo.set_ValueOfColumn("synced", "N"); 
                prestamo.set_ValueOfColumn("c_bpartner_id", bpSearch.get_ID()); 
                prestamo.set_ValueOfColumn("origen", "migrado"); 
                prestamo.set_ValueOfColumn("aprobado", "Y"); 
                prestamo.set_ValueOfColumn("masterkey", generateUUIDv4());  // uuid
                prestamo.set_ValueOfColumn("syncedtocloud", "Y");  // uuid
                prestamo.save(bpSearch.get_TrxName());

            }

            

        }
            
    }
}

result = String.format("File was read %s", csvFile);