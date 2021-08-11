-- Table: adempiere.cv_solicitudbp

-- DROP TABLE adempiere.cv_solicitudbp;

CREATE TABLE adempiere.cv_solicitudbp
(
    cv_solicitudbp_id numeric(10,0) NOT NULL,
    ad_client_id numeric(10,0) NOT NULL,
    ad_org_id numeric(10,0) NOT NULL,
    isactive character(1) COLLATE pg_catalog."default" NOT NULL DEFAULT 'Y'::bpchar,
    created timestamp without time zone NOT NULL DEFAULT now(),
    createdby numeric(10,0) NOT NULL,
    updated timestamp without time zone NOT NULL DEFAULT now(),
    updatedby numeric(10,0) NOT NULL,
    cedula character varying COLLATE pg_catalog."default",
    nombre character varying(60) COLLATE pg_catalog."default" NOT NULL,
    direccion character varying COLLATE pg_catalog."default",
    telefono character varying COLLATE pg_catalog."default",
    idruta numeric,
    idrubro numeric,
    syncedtocloud character varying COLLATE pg_catalog."default",
    synced character varying COLLATE pg_catalog."default",
    masterkey character varying COLLATE pg_catalog."default",
    monto numeric,
    cv_solicitudbp_uu character varying(36) COLLATE pg_catalog."default" DEFAULT NULL::character varying,
    CONSTRAINT cv_solicitudbp_pkey PRIMARY KEY (cv_solicitudbp_id)
)

TABLESPACE pg_default;

ALTER TABLE adempiere.cv_solicitudbp
    OWNER to adempiere;
-- Index: cv_solicitudbp_uu_idx

-- DROP INDEX adempiere.cv_solicitudbp_uu_idx;

CREATE UNIQUE INDEX cv_solicitudbp_uu_idx
    ON adempiere.cv_solicitudbp USING btree
    (cv_solicitudbp_uu COLLATE pg_catalog."default" ASC NULLS LAST)
    TABLESPACE pg_default;