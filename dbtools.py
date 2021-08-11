from sqlalchemy import create_engine, MetaData, Table
from sqlalchemy.orm import mapper, sessionmaker
import pyodbc

oS = None
dS = None

def loadSession(dbUrl):
    # engine = create_engine("postgresql://adempiere:adempiere@127.0.0.1/duho", echo=False)
    # engine = create_engine("postgresql://adempiere:adempiere**@radiomatik/duho", echo=False)
    engine = create_engine(dbUrl)
    metadata = MetaData(engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    return session
# countries = Table('c_country', metadata, autoload=True)
# mapper(models.Countries, countries)

# dirItems = Table('dir_item', metadata, autoload=True)
# mapper(models.DirItem, dirItems)

# dirItemsRadio = Table('dir_item_radio', metadata, autoload=True)
# mapper(models.DirItemRadio, dirItemsRadio)

def init_oS(dbUrl):
    global oS
    oS = loadSession(dbUrl)

def init_dS(dbUrl):
    global dS
    dS = loadSession(dbUrl)