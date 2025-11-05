-- ---------------------------------------------------------------------------
-- icc_prospecto_def.sql
--
-- Definición de la tabla de Prospecto (antes Persona) basada en la estructura proporcionada,
-- compatible con la convención de iDempiere.
-- ---------------------------------------------------------------------------
CREATE TABLE icc_prospecto(
  icc_prospecto_id numeric(10, 0) NOT NULL,
  icc_prospecto_uu varchar(36) DEFAULT uuid_generate_v4() NOT NULL,
  ad_client_id numeric(10, 0) NOT NULL,
  ad_org_id numeric(10, 0) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp without time zone NOT NULL DEFAULT now(),
  createdby numeric(10, 0) NOT NULL,
  updated timestamp without time zone NOT NULL DEFAULT now(),
  updatedby numeric(10, 0) NOT NULL,
  -- Columnas específicas de la tabla 'Prospecto' (del Campo Backend)
  c_bpartner_id numeric(10, 0) NOT NULL, -- Asumo NUMERIC(10) para INT PK, no AUTO_INCREMENT en iDempiere
  id_tipo_persona numeric(10, 0) NOT NULL, -- Referencia a catálogo (Cambiado de TINYINT)
  primer_nombre varchar(50) NOT NULL,
  segundo_nombre varchar(50), -- Opcional
  primer_apellido varchar(50) NOT NULL,
  segundo_apellido varchar(50), -- Opcional
  id_tipo_documento numeric(10, 0) NOT NULL, -- Referencia a catálogo (Cambiado de TINYINT)
  numero_documento varchar(20) NOT NULL,
  fecha_emision_documento date,
  fecha_vencimiento_documento date,
  pais_emisor_documento numeric(10, 0), -- Referencia a catálogo (Cambiado de SMALLINT)
  nombre varchar(120) NOT NULL, -- Nombre completo
  id_genero numeric(10, 0), -- Referencia a catálogo (Cambiado de TINYINT)
  id_estado_civil numeric(10, 0), -- Referencia a catálogo (Cambiado de TINYINT)
  fecha_nacimiento date,
  pais_nacimiento numeric(10, 0), -- Referencia a catálogo (Cambiado de SMALLINT)
  id_pais numeric(10, 0), -- Nacionalidad (referencia a catálogo) (Cambiado de SMALLINT)
  nombre_publico varchar(80), -- Alias o nombre social
  direccion_completa varchar(255) NOT NULL,
  telefono_principal varchar(20),
  telefono_secundario varchar(20),
  correo_electronico varchar(100),
  ocupacion varchar(100) NOT NULL,
  ingresos_mensuales DECIMAL(12, 2),
  id_nivel_educativo numeric(10, 0), -- Referencia a catálogo (Cambiado de SMALLINT)
  id_condicion_laboral numeric(10, 0), -- Referencia a catálogo (Cambiado de SMALLINT)
  id_departamento numeric(10, 0), -- Referencia a catálogo (Cambiado de SMALLINT)
  id_municipio numeric(10, 0), -- Referencia a catálogo (Cambiado de SMALLINT)
  id_tipo_zona numeric(10, 0),
  fecha_registro timestamp without time zone NOT NULL DEFAULT now(), -- Ajustado a timestamp with/without time zone NOT NULL DEFAULT now()
  -- Claves primarias y únicas
  CONSTRAINT icc_prospecto_pk PRIMARY KEY (icc_prospecto_id),
  CONSTRAINT icc_prospecto_uu_uq UNIQUE (icc_prospecto_uu),
  CONSTRAINT icc_prospecto_doc_uq UNIQUE (ad_client_id, id_tipo_documento, numero_documento) -- Suponiendo que la combinación es única por cliente
);

