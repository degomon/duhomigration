-- Table: adempiere.aat_source

-- DROP TABLE adempiere.aat_source;

CREATE TABLE adempiere.aat_source
(
    aat_source_id numeric(10,0) NOT NULL,
    ad_client_id numeric(10,0) NOT NULL,
    ad_org_id numeric(10,0) NOT NULL,
    isactive character(1) COLLATE pg_catalog."default" NOT NULL DEFAULT 'Y'::bpchar,
    created timestamp without time zone NOT NULL DEFAULT now(),
    createdby numeric(10,0) NOT NULL,
    updated timestamp without time zone NOT NULL DEFAULT now(),
    updatedby numeric(10,0) NOT NULL,
    name character varying(255) COLLATE pg_catalog."default" NOT NULL,
    description character varying(255) COLLATE pg_catalog."default",
    feedurl character varying(255) COLLATE pg_catalog."default" NOT NULL,
    langkey character varying(10) COLLATE pg_catalog."default" NOT NULL,
    markupbegin character varying(255) COLLATE pg_catalog."default",
    markupend character varying(255) COLLATE pg_catalog."default",
    imgmarkupbegin character varying(255) COLLATE pg_catalog."default",
    imgmarkupend character varying(255) COLLATE pg_catalog."default",
    bodysearch character varying COLLATE pg_catalog."default",
    bodyreplace character varying COLLATE pg_catalog."default",
    titlesearch character varying COLLATE pg_catalog."default",
    titlereplace character varying COLLATE pg_catalog."default",
    autotranslato character varying(255) COLLATE pg_catalog."default",
    aat_source_uu character varying(36) COLLATE pg_catalog."default" DEFAULT NULL::character varying,
    CONSTRAINT aat_source_pkey PRIMARY KEY (aat_source_id)
)

TABLESPACE pg_default;

ALTER TABLE adempiere.aat_source
    OWNER to adempiere;
-- Index: aat_source_uu_idx

-- DROP INDEX adempiere.aat_source_uu_idx;

CREATE UNIQUE INDEX aat_source_uu_idx
    ON adempiere.aat_source USING btree
    (aat_source_uu COLLATE pg_catalog."default" ASC NULLS LAST)
    TABLESPACE pg_default;