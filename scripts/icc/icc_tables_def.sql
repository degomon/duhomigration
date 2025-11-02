-- Table definitions for ICC catalogs
CREATE TABLE icc_tipo_persona(
  icc_tipo_persona_id numeric(10) NOT NULL,
  icc_tipo_persona_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_tipo_persona_id),
  UNIQUE (icc_tipo_persona_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_tipo_documento(
  icc_tipo_documento_id numeric(10) NOT NULL,
  icc_tipo_documento_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_tipo_documento_id),
  UNIQUE (icc_tipo_documento_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_pais(
  icc_pais_id numeric(10) NOT NULL,
  icc_pais_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_pais_id),
  UNIQUE (icc_pais_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_tipo_credito(
  icc_tipo_credito_id numeric(10) NOT NULL,
  icc_tipo_credito_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_tipo_credito_id),
  UNIQUE (icc_tipo_credito_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_tipo_agrupacion_credito(
  icc_tipo_agrupacion_credito_id numeric(10) NOT NULL,
  icc_tipo_agrupacion_credito_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_tipo_agrupacion_credito_id),
  UNIQUE (icc_tipo_agrupacion_credito_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_modalidad_credito(
  icc_modalidad_credito_id numeric(10) NOT NULL,
  icc_modalidad_credito_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_modalidad_credito_id),
  UNIQUE (icc_modalidad_credito_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_periodo_cobro_principal(
  icc_periodo_cobro_principal_id numeric(10) NOT NULL,
  icc_periodo_cobro_principal_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_periodo_cobro_principal_id),
  UNIQUE (icc_periodo_cobro_principal_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_periodo_cobro_interes(
  icc_periodo_cobro_interes_id numeric(10) NOT NULL,
  icc_periodo_cobro_interes_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_periodo_cobro_interes_id),
  UNIQUE (icc_periodo_cobro_interes_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_moneda(
  icc_moneda_id numeric(10) NOT NULL,
  icc_moneda_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_moneda_id),
  UNIQUE (icc_moneda_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_estado_credito(
  icc_estado_credito_id numeric(10) NOT NULL,
  icc_estado_credito_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_estado_credito_id),
  UNIQUE (icc_estado_credito_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_situacion_credito(
  icc_situacion_credito_id numeric(10) NOT NULL,
  icc_situacion_credito_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_situacion_credito_id),
  UNIQUE (icc_situacion_credito_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_genero(
  icc_genero_id numeric(10) NOT NULL,
  icc_genero_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_genero_id),
  UNIQUE (icc_genero_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_estado_civil(
  icc_estado_civil_id numeric(10) NOT NULL,
  icc_estado_civil_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_estado_civil_id),
  UNIQUE (icc_estado_civil_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_sector_economico(
  icc_sector_economico_id numeric(10) NOT NULL,
  icc_sector_economico_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_sector_economico_id),
  UNIQUE (icc_sector_economico_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_estado_linea(
  icc_estado_linea_id numeric(10) NOT NULL,
  icc_estado_linea_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_estado_linea_id),
  UNIQUE (icc_estado_linea_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_tipo_linea(
  icc_tipo_linea_id numeric(10) NOT NULL,
  icc_tipo_linea_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_tipo_linea_id),
  UNIQUE (icc_tipo_linea_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_origen_recursos(
  icc_origen_recursos_id numeric(10) NOT NULL,
  icc_origen_recursos_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_origen_recursos_id),
  UNIQUE (icc_origen_recursos_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_clasificacion_credito(
  icc_clasificacion_credito_id numeric(10) NOT NULL,
  icc_clasificacion_credito_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_clasificacion_credito_id),
  UNIQUE (icc_clasificacion_credito_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_garantia(
  icc_garantia_id numeric(10) NOT NULL,
  icc_garantia_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_garantia_id),
  UNIQUE (icc_garantia_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_tipo_grupo(
  icc_tipo_grupo_id numeric(10) NOT NULL,
  icc_tipo_grupo_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_tipo_grupo_id),
  UNIQUE (icc_tipo_grupo_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_met_atencion(
  icc_met_atencion_id numeric(10) NOT NULL,
  icc_met_atencion_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_met_atencion_id),
  UNIQUE (icc_met_atencion_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_nivel_educativo(
  icc_nivel_educativo_id numeric(10) NOT NULL,
  icc_nivel_educativo_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_nivel_educativo_id),
  UNIQUE (icc_nivel_educativo_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_tipo_fondo(
  icc_tipo_fondo_id numeric(10) NOT NULL,
  icc_tipo_fondo_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_tipo_fondo_id),
  UNIQUE (icc_tipo_fondo_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_situacion_obligacion(
  icc_situacion_obligacion_id numeric(10) NOT NULL,
  icc_situacion_obligacion_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_situacion_obligacion_id),
  UNIQUE (icc_situacion_obligacion_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_origen_fondo(
  icc_origen_fondo_id numeric(10) NOT NULL,
  icc_origen_fondo_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_origen_fondo_id),
  UNIQUE (icc_origen_fondo_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_estado_bien(
  icc_estado_bien_id numeric(10) NOT NULL,
  icc_estado_bien_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_estado_bien_id),
  UNIQUE (icc_estado_bien_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_forma_pago(
  icc_forma_pago_id numeric(10) NOT NULL,
  icc_forma_pago_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_forma_pago_id),
  UNIQUE (icc_forma_pago_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_tipo_bien(
  icc_tipo_bien_id numeric(10) NOT NULL,
  icc_tipo_bien_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_tipo_bien_id),
  UNIQUE (icc_tipo_bien_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_tipo_cartera(
  icc_tipo_cartera_id numeric(10) NOT NULL,
  icc_tipo_cartera_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_tipo_cartera_id),
  UNIQUE (icc_tipo_cartera_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_tipo_colocacion(
  icc_tipo_colocacion_id numeric(10) NOT NULL,
  icc_tipo_colocacion_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_tipo_colocacion_id),
  UNIQUE (icc_tipo_colocacion_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_tipo_fondo_colocacion(
  icc_tipo_fondo_colocacion_id numeric(10) NOT NULL,
  icc_tipo_fondo_colocacion_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_tipo_fondo_colocacion_id),
  UNIQUE (icc_tipo_fondo_colocacion_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_tipo_lugar(
  icc_tipo_lugar_id numeric(10) NOT NULL,
  icc_tipo_lugar_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_tipo_lugar_id),
  UNIQUE (icc_tipo_lugar_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_tipo_persona_juridica(
  icc_tipo_persona_juridica_id numeric(10) NOT NULL,
  icc_tipo_persona_juridica_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_tipo_persona_juridica_id),
  UNIQUE (icc_tipo_persona_juridica_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_tipo_trx_obligacion(
  icc_tipo_trx_obligacion_id numeric(10) NOT NULL,
  icc_tipo_trx_obligacion_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_tipo_trx_obligacion_id),
  UNIQUE (icc_tipo_trx_obligacion_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_municipio(
  icc_municipio_id numeric(10) NOT NULL,
  icc_municipio_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_municipio_id),
  UNIQUE (icc_municipio_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_actividad_economica(
  icc_actividad_economica_id numeric(10) NOT NULL,
  icc_actividad_economica_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(512) NOT NULL,
  PRIMARY KEY (icc_actividad_economica_id),
  UNIQUE (icc_actividad_economica_uu)
);

CREATE TABLE icc_nivel_educativo(
  icc_nivel_educativo_id numeric(10) NOT NULL,
  icc_nivel_educativo_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_nivel_educativo_id),
  UNIQUE (icc_nivel_educativo_uu),
  UNIQUE (ad_client_id, codigo)
);

CREATE TABLE icc_condicion_laboral(
  icc_condicion_laboral_id numeric(10) NOT NULL,
  icc_condicion_laboral_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10) NOT NULL,
  ad_org_id numeric(10) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10) NOT NULL,
  codigo varchar(10) NOT NULL,
  valor varchar(255) NOT NULL,
  PRIMARY KEY (icc_condicion_laboral_id),
  UNIQUE (icc_condicion_laboral_uu),
  UNIQUE (ad_client_id, codigo)
);

