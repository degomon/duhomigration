import dbtools
from sqlalchemy import text

def migrarCobros(ad_org_id):
    #iterar por los Cobros
    org = dbtools.dS.execute("select * from ad_org where ad_org_id = :org_id", { "org_id" : ad_org_id } ).fetchone()
    sql_insert = """ INSERT INTO adempiere.legacy_cobro( \
    legacy_cobro_id, id_cobro, id_cliente, id_cartera, tarjeta, \
    anterior, abono, mora, saldo, cuota, porcentual, operacion, \
    verdadero, procesa, trabajo, quien, operado, rutaimg, \
    creadoel, creadopor, creadoen, modificadoel, modificadopor, modificadoen, \
    id_vendedor, ad_org_id, ad_client_id, isactive, created, createdby, \
    updated, updatedby, synced, origen, syncedtocloud, masterkey) \
    VALUES (:legacy_cobro_id, :id_cobro, :id_cliente, :id_cartera, :tarjeta, \
    :anterior, :abono, :mora, :saldo, :cuota, :porcentual, :operacion, \
    :verdadero, :procesa, :trabajo, :quien, :operado, :rutaimg, \
    :creadoel, :creadopor, :creadoen, :modificadoel, :modificadopor, :modificadoen, \
    :id_vendedor, :ad_org_id, :ad_client_id, 'Y', now(), 0, \
    now(), 0, 'N', 'migrated', 'Y', gen_random_uuid () ); """
    total_cobros = dbtools.oS.execute('select count(*) from cobros where trabajo >= CAST(\'07/25/2021\' as date) ').scalar()
    max_id = dbtools.dS.execute('select coalesce(max(legacy_cobro_id), 10000000) from legacy_cobro').scalar()
    print ("Cobros a verif: " + str(total_cobros)) 
    rcob = dbtools.oS.execute("select * from cobros where trabajo >= CAST('07/25/2021' as date) order by NEWID()").fetchall()
    counter = 0
    legacy_cobro_id = max_id + 1
    for row in rcob:
        counter = counter + 1
        check_sql = "select count(legacy_cobro_id) from legacy_cobro where id_cobro = :id_cobro and ad_org_id = :ad_org_id "
        check_rec = dbtools.dS.execute(text(check_sql), {"id_cobro" : row["id_cobro"], "ad_org_id" : ad_org_id} ).scalar()
        # print ("LegacyID " + str(legacy_cobro_id))
        if (check_rec <= 0): 
            params = {
                "legacy_cobro_id" : legacy_cobro_id, "id_cobro" : row["id_cobro"], "id_cliente" : row["id_cliente"],
                "id_cartera" : row["id_cartera"], "tarjeta" : row["tarjeta"], 
                "anterior" : row["anterior"], "abono": row["abono"], "mora" : row["mora"], 
                "saldo" : row["saldo"], "cuota" : row["cuota"], "porcentual" : row["porcentual"], 
                "operacion" : row["operacion"], 
                "verdadero" : row["verdadero"], "procesa" : row["procesa"], "trabajo" : row["trabajo"], 
                "quien" : row["quien"], "operado" : row["operado"], "rutaimg" : row["rutaimg"], 
                "creadoel" : row["creadoel"], "creadopor" : row["creadopor"], "creadoen" : row["creadoen"], 
                "modificadoel" : row["modificadoel"], "modificadopor" : row["modificadopor"], 
                "modificadoen" : row["modificadoen"], "id_vendedor" : row["id_vendedor"], 
                "ad_org_id" : ad_org_id, "ad_client_id" : org["ad_client_id"]
            }
            dbtools.dS.execute(text(sql_insert), params)
            print (str(counter) + " NewID [" + str(legacy_cobro_id) + "] Insertando cobro " + str(row["id_cobro"]) + " para Org: " + str(ad_org_id) + " de " + str(total_cobros))
            legacy_cobro_id = legacy_cobro_id + 1
        else:
            print (str(counter) + " Omitiendo cobro " + str(row["id_cobro"]) + " para Org: " + str(ad_org_id) + " de " + str(total_cobros))
    dbtools.dS.commit()
    print ("Verificados los Cobros")

def migrarCxC(ad_org_id):
    # iterar por los cobros
    org = dbtools.dS.execute("select * from ad_org where ad_org_id = :org_id", { "org_id" : ad_org_id } ).fetchone()
    sql_insert_cartera = """ INSERT INTO adempiere.legacy_cartera( \
	legacy_cartera_id, id_cartera, id_sucursal, \
	id_cliente, id_vendedor, id_territorio, \
	autorizaok, fecha, dias_cre, concepto, \
	cuota, monto, valorinteres, \
	montototal, abono, saldo, \
	tasa, procesa, trabajo, \
	tipo, quien, atraso, \
	incobrable, id_actividad, proceso, \
	creadoel, creadopor, creadoen, \
	modificadoel, modificadopor, modificadoen, \
	ad_org_id, ad_client_id, createdby, created, \
    updatedby, updated, origen, aprobado, \
    masterkey, syncedtocloud) VALUES ( \
	:legacy_cartera_id, :id_cartera, :id_sucursal, \
	:id_cliente, :id_vendedor, :id_territorio, \
	:autorizaok, :fecha, :dias_cre, :concepto, \
	:cuota, :monto, :valorinteres, \
	:montototal, :abono, :saldo, \
	:tasa, :procesa, :trabajo, \
	:tipo, :quien, :atraso, \
	:incobrable, :id_actividad, :proceso, \
	:creadoel, :creadopor, :creadoen, \
	coalesce(:modificadoel, now()), coalesce(:modificadopor,''), coalesce( :modificadoen, '') , \
	:ad_org_id, :ad_client_id, 0, now(), 0, now(), 'migrated','Y', \
    gen_random_uuid (), 'Y'    ); """
    total_carteras = dbtools.oS.execute('select count(*) from cartera   ').scalar()
    max_id = dbtools.dS.execute('select coalesce(max(legacy_cartera_id), 10000000) from legacy_cartera').scalar()
    print ("Cartera a verif: " + str(total_carteras)) 
    rcart = dbtools.oS.execute("select * from cartera  order by NEWID()").fetchall()
    counter = 0
    legacy_cartera_id = max_id + 1
    for row in rcart:
        counter = counter + 1
        check_sql = "select count(legacy_cartera_id) from legacy_cartera where id_cartera = :id_cartera and ad_org_id = :ad_org_id "
        check_rec = dbtools.dS.execute(text(check_sql), {"id_cartera" : row["id_cartera"], "ad_org_id" : ad_org_id} ).scalar()
        # print ("LegacyID " + str(legacy_cobro_id))
        if (check_rec <= 0): 
            params = {
                "legacy_cartera_id" : legacy_cartera_id, "id_cartera" : row["id_cartera"], "id_sucursal" : row["id_cartera"],
                "id_cliente" : row["id_cliente"], "id_vendedor" : row["id_vendedor"], "id_territorio" : row["id_territorio"],
                "autorizaok" : row["autorizaok"], "fecha" : row["fecha"], "dias_cre" : row["dias_cre"], "concepto" : row["concepto"],
                "cuota" : row["cuota"], "monto" : row["monto"], "valorinteres" : row["valorinteres"],
                "montototal" : row["montototal"], "abono" : row["abono"], "saldo" : row["saldo"],
                "tasa" : row["tasa"], "procesa" : row["procesa"], "trabajo" : row["trabajo"],
                "tipo" : row["tipo"], "quien" : row["quien"], "atraso" : row["atraso"],
                "incobrable" : row["incobrable"], "id_actividad" : row["id_actividad"], "proceso" : row["proceso"],
                "creadoel" : row["creadoel"], "creadopor" : row["creadopor"], "creadoen" : row["creadoen"],
                "modificadoel" : row["modificadoel"], "modificadopor" : row["modificadopor"], "modificadoen" : row["modificadoen"],
                "ad_org_id" : ad_org_id, "ad_client_id" : org["ad_client_id"]
            }
            dbtools.dS.execute(text(sql_insert_cartera), params)
            print (str(counter) + " NewID [" + str(legacy_cartera_id) + "] Insertando CxC " + str(row["id_cartera"]) + " para Org: " + str(ad_org_id) + " de " + str(total_carteras))
            legacy_cartera_id = legacy_cartera_id + 1
        else:
            print (str(counter) + " Omitiendo cartera " + str(row["id_cartera"]) + " para Org: " + str(ad_org_id) + " de " + str(total_carteras))
    dbtools.dS.commit()
    print ("Migradas las Carteras")

def migrarClientes(ad_org_id):
    org = dbtools.dS.execute("select * from ad_org where ad_org_id = :org_id", { "org_id" : ad_org_id } ).fetchone()
    sql_insert = (" INSERT INTO adempiere.legacy_cliente( " +
	" legacy_cliente_id, id_cliente, id_sucursal, " +
	" nombre, domicilio, telefono, " +
	" contacto, telfconta, id_vendedor, " + 
	" id_territorio, activo, procesa, " +
	" cargos, abonos, saldo, cedula, " +
	" id_actividad, genero, creadoel, " +
	" creadopor, creadoen, modificadoel, " +
	" modificadopor, modificadoen, ad_org_id, "+
    " createdby, created, updatedby, updated, ad_client_id," +
    " actividad, codigo, origen) " +
	" VALUES (  :legacy_cliente_id, :id_cliente, :id_sucursal, " +
	" :nombre, :domicilio, :telefono, " +
	" :contacto, :telfconta, :id_vendedor,  " +
	" :id_territorio, :activo, :procesa, " +
	" :cargos, :abonos, :saldo, :cedula, " +
	" :id_actividad, :genero, :creadoel, " +
	" :creadopor, :creadoen, :modificadoel, " +
	" :modificadopor, :modificadoen, :ad_org_id, " +
    " 0, now(), 0, now(), :ad_client_id, " +
    " :actividad, :codigo, 'migrated' ); ")
    total_clientes = dbtools.oS.execute('select count(*) from cliente').scalar()
    max_id = dbtools.dS.execute('select coalesce(max(legacy_cliente_id), 10000000) from legacy_cliente').scalar()
    print ("Clientes a verificar: " + str(total_clientes)) 
    rcli = dbtools.oS.execute("select cli.*, "
    + "(select nombre from actividad where codigo = cli.id_actividad) as actividadname "
    + " from cliente cli order by NEWID()").fetchall()
    counter = 0
    legacy_cliente_id = max_id + 1
    for row in rcli:
        counter = counter + 1
        check_sql = "select count(legacy_cliente_id) from legacy_cliente where id_cliente = :id_cliente and ad_org_id = :ad_org_id "
        check_rec = dbtools.dS.execute(text(check_sql), {"id_cliente" : row["id_cliente"], "ad_org_id" : ad_org_id} ).scalar()
        if (check_rec <= 0): 
            params = {
                "legacy_cliente_id" : legacy_cliente_id, "id_cliente" : row["id_cliente"], "id_sucursal" : row["id_sucursal"], 
                "nombre" : row["nombre"], "domicilio" : row["domicilio"], "telefono" : row["telefono"], 
                "contacto" : row["contacto"], "telfconta" : row["telfconta"], "id_vendedor" : row["id_vendedor"], 
                "id_territorio" : row["id_territorio"], "activo" : row["activo"], "procesa" : row["procesa"], 
                "cargos" : row["cargos"], "abonos" : row["abonos"], "saldo" : row["saldo"], "cedula" : row["cedula"], 
                "id_actividad" : row["id_actividad"], "genero" : row["genero"], "creadoel" : row["creadoel"], 
                "creadopor" : row["creadopor"], "creadoen" : row["creadopor"], "modificadoel" : row["modificadoel"], 
                "modificadopor" : row["modificadopor"], "modificadoen" : row["modificadoen"], "ad_org_id" : ad_org_id, 
                "ad_client_id" : org["ad_client_id"], "actividad" : row["actividadname"], "codigo" : row["codigo"]
            }
            dbtools.dS.execute(text(sql_insert), params)
            print (str(counter) + " NewID [" + str(legacy_cliente_id) + "] Insertando Legacy_Cliente " + str(row["id_cliente"]) + " para Org: " + str(ad_org_id) + " de " + str(total_clientes))
            legacy_cliente_id = legacy_cliente_id + 1
    dbtools.dS.commit()
    print ("Verificados los Clientes")

def migrarTerritorios(ad_org_id):
    org = dbtools.dS.execute("select * from ad_org where ad_org_id = :org_id", { "org_id" : ad_org_id } ).fetchone()
    sql_insert = ("INSERT INTO adempiere.legacy_territorio( " 
	+ " legacy_territorio_id, id_territorio, codigo, " 
    + " zona, trabajo, procesa, id_sucursal, creadoel, "
    + " creadopor, creadoen, modificadoel, modificadopor, "
    + " modificadoen, ad_client_id, ad_org_id, isactive, "
    + " created, createdby, updated, updatedby, origen ) "
	+ " VALUES ( :legacy_territorio_id, :id_territorio, :codigo, "
    + " :zona, :trabajo, :procesa, :id_sucursal, :creadoel, "
    + " :creadopor, :creadoen, :modificadoel, :modificadopor, "
    + " :modificadoen, :ad_client_id, :ad_org_id, 'Y', "
    + " now(), 0, now(), 0, 'migrated' ); ")
    total_territorios = dbtools.oS.execute('select count(*) from territorio').scalar()
    max_id = dbtools.dS.execute('select coalesce(max(legacy_territorio_id), 10000000) from legacy_territorio').scalar()
    print ("Territorios a verificar: " + str(total_territorios)) 
    rterr = dbtools.oS.execute("select * from territorio").fetchall()
    counter = 0
    legacy_territorio_id = max_id + 1
    for row in rterr:
        counter = counter + 1
        check_sql = "select count(legacy_territorio_id) from legacy_territorio where id_territorio = :id_territorio and ad_org_id = :ad_org_id "
        check_rec = dbtools.dS.execute(text(check_sql), {"id_territorio" : row["id_territorio"], "ad_org_id" : ad_org_id} ).scalar()
        if (check_rec <= 0):
            params = {
                "legacy_territorio_id" : legacy_territorio_id, "id_territorio" : row["id_territorio"], "codigo" : row["codigo"], 
                "zona" : row["zona"], "trabajo" : row["trabajo"], "procesa" : row["procesa"], "id_sucursal" : row["id_sucursal"],
                "creadoel" : row["creadoel"], "creadopor" : row["creadopor"], "creadoen" : row["creadoen"], 
                "modificadoel" : row["modificadoel"], "modificadopor" : row["modificadopor"], 
                "modificadoen" : row["modificadoen"], "ad_client_id" : org["ad_client_id"], "ad_org_id": ad_org_id
            }
            dbtools.dS.execute(text(sql_insert), params)
            print (str(counter) + " NewID [" + str(legacy_territorio_id) + "] Insertando CxC " + str(row["id_territorio"]) + " para Org: " + str(ad_org_id) + " de " + str(total_territorios))
            legacy_territorio_id = legacy_territorio_id + 1
    dbtools.dS.commit()
    print ("Verificados los Territorios")