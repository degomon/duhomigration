CREATE TABLE icc_persona(
  icc_persona_id numeric(10, 0) PRIMARY KEY NOT NULL,
  c_period_id numeric(10, 0),
  c_bpartner_id numeric(10, 0),
  fecha_nacimiento timestamp without time zone,
  id_actividad_economica int,
  id_cedula_residencia character varying,
  id_estado_civil int,
  id_genero int,
  id_municipio character varying,
  id_persona character varying,
  id_tipo_persona int,
  id_tipo_documento int,
  id_tipo_grupo int,
  nombre character varying,
  id_pais int,
  pep int,
  id_tipo_persona_juridica int
);

