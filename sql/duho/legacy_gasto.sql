-- Table: adempiere.legacy_gasto

-- DROP TABLE adempiere.legacy_gasto;

CREATE TABLE adempiere.legacy_gasto
(
    legacy_gasto_id numeric(10,0) NOT NULL,
    id_gasto integer,
    codigo character varying,
    fecha timestamp without time zone NOT NULL,
    masterkey character varying COLLATE pg_catalog."default" NOT NULL,
    creadoel timestamp without time zone,
    monto numeric NOT NULL,
    syncedtocloud character varying COLLATE pg_catalog."default",
    origen character varying COLLATE pg_catalog."default",
    concepto character varying COLLATE pg_catalog."default",
    c_bpartner_id numeric NOT NULL,
    ad_client_id numeric(10,0) NOT NULL,
    ad_org_id numeric(10,0) NOT NULL,
    isactive character(1) COLLATE pg_catalog."default" NOT NULL DEFAULT 'Y'::bpchar,
    created timestamp without time zone NOT NULL DEFAULT now(),
    createdby numeric(10,0) NOT NULL,
    updated timestamp without time zone NOT NULL DEFAULT now(),
    updatedby numeric(10,0) NOT NULL,
    legacy_gasto_uu character varying(36) COLLATE pg_catalog."default" DEFAULT NULL::character varying,
    CONSTRAINT legacy_gasto_pkey PRIMARY KEY (legacy_gasto_id)
)

TABLESPACE pg_default;

ALTER TABLE adempiere.legacy_gasto
    OWNER to adempiere;
-- Index: legacy_gasto_uu_idx

-- DROP INDEX adempiere.legacy_gasto_uu_idx;

CREATE UNIQUE INDEX legacy_gasto_uu_idx
    ON adempiere.legacy_gasto USING btree
    (legacy_gasto_uu COLLATE pg_catalog."default" ASC NULLS LAST)
    TABLESPACE pg_default;