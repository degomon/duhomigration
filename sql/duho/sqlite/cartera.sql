CREATE TABLE cartera (
	idcartera INTEGER,
	c_bpartner_id INTEGER,
	monto NUMERIC,
	interes NUMERIC,
	montototal NUMERIC,
	diascre INTEGER,
	cuota NUMERIC,
	abono NUMERIC,
	saldo NUMERIC,
	legacy_cartera_id INTEGER,
	aprobado TEXT,
	syncedtocloud TEXT,
	origen TEXT, fecha INTEGER, creadoel INTEGER, org INTEGER, masterkey TEXT,
	CONSTRAINT cartera_PK PRIMARY KEY (masterkey)
);

CREATE INDEX cartera_legacy_cartera_id_IDX ON cartera (legacy_cartera_id);
