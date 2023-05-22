-- Table: adempiere.notification

-- DROP TABLE adempiere.notification;

CREATE TABLE adempiere.notification
(
    notification_id numeric(10,0) NOT NULL,
    ad_client_id numeric(10,0) NOT NULL,
    ad_org_id numeric(10,0) NOT NULL,
    isactive character(1) COLLATE pg_catalog."default" NOT NULL DEFAULT 'Y'::bpchar,
    created timestamp without time zone NOT NULL DEFAULT now(),
    createdby numeric(10,0) NOT NULL,
    updated timestamp without time zone NOT NULL DEFAULT now(),
    updatedby numeric(10,0) NOT NULL,
    messagebody character varying COLLATE pg_catalog."default" NOT NULL,
    destination character varying COLLATE pg_catalog."default" NOT NULL,
    currentstatus character varying COLLATE pg_catalog."default" NOT NULL,
    generatedby character varying COLLATE pg_catalog."default",
    attempts integer,
    result character varying COLLATE pg_catalog."default",
    sentfrom character varying COLLATE pg_catalog."default",
    notificationtype character varying COLLATE pg_catalog."default",
    notificationsource character varying COLLATE pg_catalog."default",
    lastattempt timestamp without time zone,
    notification_uu character varying(36) COLLATE pg_catalog."default" DEFAULT NULL::character varying,
    CONSTRAINT notification_pkey PRIMARY KEY (notification_id)
)

TABLESPACE pg_default;

ALTER TABLE adempiere.notification
    OWNER to adempiere;
-- Index: notification_uu_idx

-- DROP INDEX adempiere.notification_uu_idx;

CREATE UNIQUE INDEX notification_uu_idx
    ON adempiere.notification USING btree
    (notification_uu COLLATE pg_catalog."default" ASC NULLS LAST)
    TABLESPACE pg_default;