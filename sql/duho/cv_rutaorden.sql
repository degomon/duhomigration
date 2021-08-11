-- Table: adempiere.cv_rutaorden

-- DROP TABLE adempiere.cv_rutaorden;

CREATE TABLE adempiere.cv_rutaorden
(
    cv_rutaorden_id numeric(10,0) NOT NULL,
    cv_ruta_id numeric,
    c_bpartner_id numeric,
    ad_client_id numeric(10,0) NOT NULL,
    ad_org_id numeric(10,0) NOT NULL,
    seqno integer,
    isactive character(1) COLLATE pg_catalog."default" NOT NULL DEFAULT 'Y'::bpchar,
    created timestamp without time zone NOT NULL DEFAULT now(),
    createdby numeric(10,0) NOT NULL,
    updated timestamp without time zone NOT NULL DEFAULT now(),
    updatedby numeric(10,0) NOT NULL,
    description character varying(255) COLLATE pg_catalog."default",
    cv_rutaorden_uu character varying(36) COLLATE pg_catalog."default" DEFAULT NULL::character varying,
    CONSTRAINT cv_rutaorden_pkey PRIMARY KEY (cv_rutaorden_id)
)

TABLESPACE pg_default;

ALTER TABLE adempiere.cv_rutaorden
    OWNER to adempiere;
-- Index: cv_rutaorden_uu_idx

-- DROP INDEX adempiere.cv_rutaorden_uu_idx;

CREATE UNIQUE INDEX cv_rutaorden_uu_idx
    ON adempiere.cv_rutaorden USING btree
    (cv_rutaorden_uu COLLATE pg_catalog."default" ASC NULLS LAST)
    TABLESPACE pg_default;