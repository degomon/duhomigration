-- Table: adempiere.c_bpartner_pic
-- DROP TABLE IF EXISTS adempiere.c_bpartner_pic;
CREATE TABLE IF NOT EXISTS adempiere.c_bpartner_pic(
  c_bpartner_pic_id numeric(10, 0) NOT NULL,
  created timestamp without time zone,
  createdby numeric(10, 0),
  updated timestamp without time zone,
  updatedby numeric(10, 0),
  c_bpartner_pic_uu character varying(36) COLLATE pg_catalog."default",
  c_bpartner_id numeric(10, 0),
  fecha_inicio timestamp without time zone,
  cv_ruta_id numeric(10, 0),
  tipo_operacion character varying COLLATE pg_catalog."default",
  value character varying COLLATE pg_catalog."default", -- value en c_bpartner
  nombrepic character varying COLLATE pg_catalog."default",
  fecha_nacimiento timestamp without time zone,
  c_country_id numeric,
  c_country_residence_id numeric,
  ocupacion character varying COLLATE pg_catalog."default",
  profesion character varying COLLATE pg_catalog."default",
  centro_laboral character varying COLLATE pg_catalog."default",
  descripcion_negocio character varying COLLATE pg_catalog."default",
  medio_identificacion character varying COLLATE pg_catalog."default",
  identificacion character varying COLLATE pg_catalog."default",
  rango_ingreso character varying COLLATE pg_catalog."default",
  origen_fondos character varying COLLATE pg_catalog."default",
  referencia_bancaria character varying COLLATE pg_catalog."default",
  fecha_llenado timestamp without time zone,
  lugar_llenado character varying COLLATE pg_catalog."default",
  fecha_firma timestamp without time zone,
  firma_cliente character varying COLLATE pg_catalog."default",
  elaborado_por character varying COLLATE pg_catalog."default",
  firma_funcionario character varying COLLATE pg_catalog."default",
  memo character varying COLLATE pg_catalog."default",
  cedula_img character varying COLLATE pg_catalog."default",
  persona_img character varying COLLATE pg_catalog."default",
  CONSTRAINT c_bpartner_pic_pkey PRIMARY KEY (c_bpartner_pic_id)
)
TABLESPACE pg_default;

ALTER TABLE IF EXISTS adempiere.c_bpartner_pic OWNER TO adempiere;

