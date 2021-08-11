-- Table: adempiere.legacy_cliente

-- DROP TABLE adempiere.legacy_cliente;

CREATE TABLE adempiere.legacy_cliente
(
    legacy_cliente_id bigint NOT NULL,
    id_cliente bigint NOT NULL,
    id_sucursal bigint NOT NULL,
    nombre character varying not null,
    domicilio character varying not null,
    telefono character varying ,
    contacto character varying ,
    telfconta character varying ,
    id_vendedor bigint NOT NULL,
    id_territorio bigint NOT NULL,
    activo character varying,
    procesa character varying,
    cargos numeric,
    abonos numeric,
    saldo numeric,
    cedula character varying,
    id_actividad bigint,
    genero character varying,
    creadoel timestamp without time zone,
    creadopor character varying,
    creadoen character varying,
    modificadoel timestamp without time zone,
    modificadopor character varying,
    modificadoen  character varying,

    ad_org_id numeric NOT NULL,
    ad_client_id numeric(10,0) NOT NULL,
    isactive character(1) COLLATE pg_catalog."default" NOT NULL DEFAULT 'Y'::bpchar,
    created timestamp without time zone NOT NULL DEFAULT now(),
    createdby numeric(10,0) NOT NULL,
    updated timestamp without time zone NOT NULL DEFAULT now(),
    updatedby numeric(10,0) NOT NULL,
    CONSTRAINT legacy_cliente_id_pk PRIMARY KEY (legacy_cliente_id)
)

TABLESPACE pg_default;

ALTER TABLE adempiere.legacy_cliente
    OWNER to adempiere;