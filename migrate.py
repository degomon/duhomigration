import dbtools
import sys
import argparse
import duho
import pyodbc
import time

# engine = create_engine("postgresql://adempiere:adempiere@127.0.0.1/duho", echo=False)
# destino = create_engine("mssql+pyodbc://sa:Orgenetnom1981**@localhost/BoacoCV?driver=ODBC Driver 17 for SQL Server&&MARS_Connection=Yes", echo=False)
# Argumentos, los obtenemios
parser = argparse.ArgumentParser(description='Migración de Datos Duho SQL a Postgres Idempiere')
parser.add_argument('dborigen', type=str, help='Url DB Origen (requerido)')
parser.add_argument('dbdestino', type=str, help='Url DB Destino (requerido)')
parser.add_argument('ad_org_id', type=int, help='Org Id')
args = parser.parse_args()

print("DB Origen: " + args.dborigen)
print("DB Destino: " + args.dbdestino)
print("Org Id: " + str(args.ad_org_id))
# print (pyodbc.drivers())
start_time = time.time()

dbtools.init_oS(args.dborigen)
# dbtools.init_oS(origenStr)
dbtools.init_dS(args.dbdestino)
duho.migrarClientes(args.ad_org_id)
# duho.migrarCxC(args.ad_org_id)
# duho.migrarTerritorios(args.ad_org_id)
# duho.migrarCobros(args.ad_org_id)
print("--- %s seconds ---" % (time.time() - start_time))
print("Sessions loaded OK")