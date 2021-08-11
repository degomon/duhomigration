-- Table: adempiere.aat_post

-- DROP TABLE adempiere.aat_post;

CREATE TABLE adempiere.aat_post
(
    aat_post_id numeric(10,0) NOT NULL,
    aat_source_id numeric(10,0) NOT NULL,
    ad_client_id numeric(10,0) NOT NULL,
    ad_org_id numeric(10,0) NOT NULL,
    isactive character(1) COLLATE pg_catalog."default" NOT NULL DEFAULT 'Y'::bpchar,
    created timestamp without time zone NOT NULL DEFAULT now(),
    createdby numeric(10,0) NOT NULL,
    updated timestamp without time zone NOT NULL DEFAULT now(),
    updatedby numeric(10,0) NOT NULL,
    
    harvestdate timestamp without time zone NOT NULL DEFAULT now(),
    translationdate timestamp without time zone DEFAULT now(),
    postdate timestamp without time zone DEFAULT now(),
    posttitle character varying COLLATE pg_catalog."default" NOT NULL,
    postbody character varying COLLATE pg_catalog."default",
    imgsrc character varying COLLATE pg_catalog."default" ,
    videosrc character varying COLLATE pg_catalog."default",
    langkey character varying COLLATE pg_catalog."default" NOT NULL,
    translated character varying(10) COLLATE pg_catalog."default",
    originalurl character varying COLLATE pg_catalog."default",
    poststatus character varying COLLATE pg_catalog."default" NOT NULL,
    aat_post_uu character varying(36) COLLATE pg_catalog."default" DEFAULT NULL::character varying,

    meta001 character varying COLLATE pg_catalog."default",
    meta002 character varying COLLATE pg_catalog."default",
    meta003 character varying COLLATE pg_catalog."default",
    meta004 character varying COLLATE pg_catalog."default",
    meta005 character varying COLLATE pg_catalog."default",
    CONSTRAINT aat_post_pkey PRIMARY KEY (aat_post_id)
)

TABLESPACE pg_default;

ALTER TABLE adempiere.aat_post
    OWNER to adempiere;
-- Index: aat_post_uu_idx

-- DROP INDEX adempiere.aat_post_uu_idx;

CREATE UNIQUE INDEX aat_post_uu_idx
    ON adempiere.aat_post USING btree
    (aat_post_uu COLLATE pg_catalog."default" ASC NULLS LAST)
    TABLESPACE pg_default;