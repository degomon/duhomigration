-- Table: adempiere.legacy_territorio

-- DROP TABLE adempiere.legacy_territorio;

CREATE TABLE adempiere.legacy_territorio
(
    legacy_territorio_id numeric(10,0) NOT NULL,
    
    id_territorio integer,
    codigo character varying COLLATE pg_catalog."default" NOT NULL,
    zona character varying COLLATE pg_catalog."default",
    trabajo timestamp without time zone,
    procesa character varying,
    id_sucursal integer,
    creadoel timestamp without time zone,
    creadopor character varying,
    creadoen character varying,
    modificadoel timestamp without time zone,
    modificadopor character varying,
    modificadoen  character varying,

    ad_client_id numeric(10,0) NOT NULL,
    ad_org_id numeric(10,0) NOT NULL,
    isactive character(1) COLLATE pg_catalog."default" NOT NULL DEFAULT 'Y'::bpchar,
    created timestamp without time zone NOT NULL DEFAULT now(),
    createdby numeric(10,0) NOT NULL,
    updated timestamp without time zone NOT NULL DEFAULT now(),
    updatedby numeric(10,0) NOT NULL,
    legacy_territorio_uu character varying(36) COLLATE pg_catalog."default" DEFAULT NULL::character varying,
    CONSTRAINT legacy_territorio_pkey PRIMARY KEY (legacy_territorio_id)
)

TABLESPACE pg_default;

ALTER TABLE adempiere.legacy_territorio
    OWNER to adempiere;
-- Index: legacy_territorio_uu_idx

-- DROP INDEX adempiere.legacy_territorio_uu_idx;

CREATE UNIQUE INDEX legacy_territorio_uu_idx
    ON adempiere.legacy_territorio USING btree
    (legacy_territorio_uu COLLATE pg_catalog."default" ASC NULLS LAST)
    TABLESPACE pg_default;