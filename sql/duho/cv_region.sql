-- Table: adempiere.cv_region

-- DROP TABLE adempiere.cv_region;

CREATE TABLE adempiere.cv_region
(
    cv_region_id numeric(10,0) NOT NULL,
    ad_client_id numeric(10,0) NOT NULL,
    ad_org_id numeric(10,0) NOT NULL,
    isactive character(1) COLLATE pg_catalog."default" NOT NULL DEFAULT 'Y'::bpchar,
    created timestamp without time zone NOT NULL DEFAULT now(),
    createdby numeric(10,0) NOT NULL,
    updated timestamp without time zone NOT NULL DEFAULT now(),
    updatedby numeric(10,0) NOT NULL,
    name character varying(60) COLLATE pg_catalog."default" NOT NULL,
    description character varying(255) COLLATE pg_catalog."default",
    help character varying(2000) COLLATE pg_catalog."default",
    cv_region_uu character varying(36) COLLATE pg_catalog."default" DEFAULT NULL::character varying,
    CONSTRAINT cv_region_pkey PRIMARY KEY (cv_region_id)
)

TABLESPACE pg_default;

ALTER TABLE adempiere.cv_region
    OWNER to adempiere;
-- Index: cv_region_uu_idx

-- DROP INDEX adempiere.cv_region_uu_idx;

CREATE UNIQUE INDEX cv_region_uu_idx
    ON adempiere.cv_region USING btree
    (cv_region_uu COLLATE pg_catalog."default" ASC NULLS LAST)
    TABLESPACE pg_default;