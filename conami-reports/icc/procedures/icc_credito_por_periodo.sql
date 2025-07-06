-- FUNCTION: adempiere.icc_credito_por_periodo(numeric)
-- DROP FUNCTION IF EXISTS adempiere.icc_credito_por_periodo(numeric);
CREATE OR REPLACE FUNCTION adempiere.icc_credito_por_periodo(period_id numeric)
  RETURNS void
  LANGUAGE 'plpgsql'
  COST 100 VOLATILE PARALLEL UNSAFE
  AS $BODY$
DECLARE
  car RECORD;
  p RECORD;
  cobrec RECORD;
  bprec RECORD;
  personarec RECORD;
  credito_personarec RECORD;
  analista_rec RECORD;
  dias_por_vencer int;
  dias_vencidos int;
  saldo_periodo_principal numeric DEFAULT 0;
  saldo_periodo_interes numeric DEFAULT 0;
  saldo_periodo_total numeric DEFAULT 0;
  creditovencido boolean DEFAULT FALSE;
BEGIN
  -- CREATE TEMPORARY tables TO store the results OF the WITH queries
  DROP TABLE IF EXISTS riesgos;
  CREATE TEMP TABLE riesgos AS (
    SELECT
      value AS kname,
      description AS kdesc
    FROM
      AD_Ref_List
    WHERE
      AD_Reference_ID = 1000022
    );

  DROP TABLE IF EXISTS tipozona;
  CREATE TEMP TABLE tipozona AS (
    SELECT
      value AS kname,
      description AS kdesc
    FROM
      AD_Ref_List
    WHERE
      AD_Reference_ID = 1000021
    );

  DROP TABLE IF EXISTS estadosciviles;
  CREATE TEMP TABLE estadosciviles AS (
    SELECT
      value AS kname,
      description AS kdesc
    FROM
      AD_Ref_List
    WHERE
      AD_Reference_ID = 1000013
    );

  DROP TABLE IF EXISTS generos;
  CREATE TEMP TABLE generos AS (
    SELECT
      value AS kname,
      description AS kdesc
    FROM
      AD_Ref_List
    WHERE
      AD_Reference_ID = 1000020
    );

  DROP TABLE IF EXISTS analistas;
  CREATE TEMP TABLE analistas AS (
    SELECT
      ru.name AS nombreruta,
      ru.cv_ruta_id,
      coalesce(bp.taxid, '-'
      ) AS cedula,
      bp.name AS nombre_analista,
(
      SELECT
        kdesc
      FROM
        generos
      WHERE
        kname = bp.genero ) ::int AS id_genero
      FROM
        cv_ruta ru
      LEFT JOIN ad_user u ON ru.ad_user_id = u.ad_user_id
      LEFT JOIN c_bpartner bp ON u.c_bpartner_id = bp.c_bpartner_id
  );

  SELECT
    *
  FROM
    C_Period
  WHERE
    c_period_ID = period_id INTO p;
  DELETE FROM icc_credito
  WHERE c_period_id = period_id;


  /* inicia ciclo for */
  FOR car IN
  SELECT
    *
  FROM
    legacy_cartera_data cardata
  WHERE
  /* cardata.fecha::date BETWEEN p.startdate::date AND p.enddate::date
   OR fecha_ultimo_pago BETWEEN p.startdate::date AND p.enddate::date
   OR (fecha_ultimo_pago IS NULL AND cardata.fecha::date <= p.enddate::date) */
  cardata.fecha::date <= p.enddate::date
  AND ((cardata.montototal - get_abonos_up_to_date(cardata.legacy_cartera_id, p.enddate::date)) > 0
    OR cardata.fecha::date BETWEEN p.startdate::date AND p.enddate::date
    OR fecha_ultimo_pago BETWEEN p.startdate::date AND p.enddate::date)
  -- LIMIT 100 -- make it faster
  LOOP
    DECLARE cuotas_vencidas_hoy int DEFAULT 0;
    creditovencido := FALSE;
    --- verificar si el crédito está vencido
    IF (car.fecha_vencimiento::date < p.enddate::date) THEN
      -- dsds
      creditovencido := TRUE;
    END IF;

    SELECT
      coalesce(count(cob.*), 0.00) AS cantidad_cuotas,
      coalesce(sum(cob.abono), 0.00) AS total_abonos
    FROM
      legacy_cobro cob
    WHERE
      cob.id_cartera = car.legacy_cartera_id
      AND cob.operacion::date <= p.enddate::date
      AND cob.abono > 0 INTO cobrec;
    dias_por_vencer :=(car.fecha_vencimiento::date - p.enddate::date);
    dias_vencidos :=(p.enddate::date - car.fecha_vencimiento::date);

    saldo_periodo_principal := iif(car.monto < cobrec.total_abonos, 0.00,(car.monto - cobrec.total_abonos));
    saldo_periodo_interes := iif(saldo_periodo_principal > 0, car.interes, car.montototal - cobrec.total_abonos);
    saldo_periodo_total := car.montototal - cobrec.total_abonos;
    SELECT
      bp.*,
      coalesce(bpg.description, '7026101')::int AS id_destino_credito
    FROM
      c_bpartner bp
      INNER JOIN c_bp_group bpg ON bp.c_bp_group_id = bpg.c_bp_group_id
    WHERE
      c_bpartner_id = car.c_bpartner_id INTO bprec;
    ------------------------------------
    -- select and insert into personarec
    ------------------------------------
    SELECT
      period_id AS c_period_id,
      bprec.c_bpartner_id AS c_bpartner_id,
      bprec.birthday AS fecha_nacimiento,
      bprec.id_destino_credito AS id_actividad_economica,
      -- bprec.taxid AS id_cedula_residencia,
      NULL AS id_cedula_residencia,
(
        SELECT
          kdesc
        FROM
          estadosciviles
        WHERE
          kname = bprec.civilstatus)::int AS id_estado_civil,
(
        SELECT
          kdesc
        FROM
          generos
        WHERE
          kname = bprec.genero)::int AS id_genero,
(
        SELECT
          description
        FROM
          cv_ruta
        WHERE
          cv_ruta_id = bprec.cv_ruta_id
        LIMIT 1) AS id_municipio,
    bprec.taxid AS id_persona, -- cédula
    1 AS id_tipo_persona, -- asumiento que es natural nacional
    1 AS id_tipo_documento, -- cédula identidad nica
    2 AS id_tipo_grupo, -- id_tipo_grupo (económico)
    bprec.name AS nombre,
    158 AS id_pais, -- Nicaragua
    0 AS pep, -- pep
    0 AS id_tipo_persona_juridica -- id_tipo_persona_juridica no aplica
    INTO personarec;
    PERFORM
      icc_persona_save_or_update(personarec);
    ------------------------------------
    -- insert into credito_personarec
    ------------------------------------
    SELECT
      period_id AS c_period_id,
      car.legacy_cartera_id::character varying AS id_credito,
      bprec.taxid::character varying AS id_persona,
      1 AS id_tipo_documento,
      2 AS id_moneda --
      INTO credito_personarec;

    PERFORM
      icc_credito_persona_save_or_update(credito_personarec);
    ------------------------------------
    -- insert into analista_rec
    ------------------------------------
    SELECT
      period_id AS c_period_id,
(
        SELECT
          cedula
        FROM
          analistas
        WHERE
          cv_ruta_id = bprec.cv_ruta_id) AS id_analista,
(
        SELECT
          nombre_analista
        FROM
          analistas
        WHERE
          cv_ruta_id = bprec.cv_ruta_id) AS nombre,
(
        SELECT
          id_genero
        FROM
          analistas
        WHERE
          cv_ruta_id = bprec.cv_ruta_id) AS id_genero INTO analista_rec;

    PERFORM
      icc_analista_save_or_update(analista_rec);
    ----------------
    -- let's save it
    INSERT INTO adempiere.icc_credito(legacy_cartera_id, --01
      c_period_id, --02
      cantidad_cuotas, --03
      cantidad_prorrogas, --04
      comision_acumulada_por_cobrar, --05
      cuotas_vencidas, --06
      dias_mora_interes, --07
      dias_mora_principal, --08
      fecha_otorgamiento, --09
      fecha_vencimiento_credito, --10
      id_clasificacion_credito, --11
      id_tipo_credito, --12
      id_credito, --13
      id_credito_anterior, --14
      id_destino_credito, --15
      id_garantia, --16
      id_linea, --17
      id_modalidad_credito, --18
      id_moneda, --19
      id_municipio, --20
      id_oficina, --21
      id_origen_recursos, --22
      id_periodo_cobro_interes, --23
      id_periodo_cobro_principal, --24
      id_sindicado, --25
      id_situacion_credito, --26
      id_tipo_agrupacion_credito, --27
      interes_corriente, --28
      interes_vencidos, --29
      mto_adjudicacion_credito, --30
      mto_intereses_por_vencer_030, --31
      mto_intereses_por_vencer_060, --32
      mto_intereses_por_vencer_090, --33
      mto_intereses_por_vencer_120, --34
      mto_intereses_por_vencer_180, --35
      mto_intereses_por_vencer_360, --36
      mto_intereses_por_vencer_mas_360, --37
      mto_original, --38
      mto_por_vencer_030, --39
      mto_por_vencer_060, --40
      mto_por_vencer_090, --41
      mto_por_vencer_120, --42
      mto_por_vencer_180, --43
      mto_por_vencer_360, --44
      mto_por_vencer_mas_360, --45
      mto_prorrogado, --46
      mto_saneado, --47
      mto_vencido_015, --48
      mto_vencido_030, --49
      mto_vencido_060, --50
      mto_vencido_090, --51
      mto_vencido_120, --52
      mto_vencido_180, --53
      mto_vencido_360, --54
      mto_vencido_mas_360, --55
      observaciones, --56
      otras_comisiones_por_cobrar, --57
      provision, --58
      saldo, --59
      tasa, --60
      tasa_costo_efectiva, --61
      tasa_contrato, --62
      tipo_cambio_oficial, --63
      id_sector_economico, --64
      id_met_atencion, --65
      id_tipo_zona, --66
      fecha_ultima_prorroga, --67
      plazo_prorroga, --68
      fecha_estado, --69
      id_estado_credito, --70
      cta_contable, --71
      id_moneda_linea, --72
      mto_en_mora, --73
      id_credito_grupo, --74
      id_analista --75
)
      VALUES (car.legacy_cartera_id, --01 legacy_cartera_id
        p.c_period_ID, --02 c_period_id
        cobrec.cantidad_cuotas, --03 cantidad_cuotas
        0, --04 cantidad_prorrogas
        0.00, --05 comision_acumulada_por_cobrar
        0.00, --06 cuotas_vencidas
        0.00, --07 dias_mora_interes
        0.00, --08 dias_mora_principal
        car.fecha, --09 fecha_otorgamiento
        car.fecha_vencimiento, --10 fecha_vencimiento_credito
(
          SELECT
            kdesc
          FROM riesgos
          WHERE
            kname = bprec.tipo_riesgo LIMIT 1)::int, --11 id_clasificacion_credito
        2, --12 id_tipo_credito
        car.legacy_cartera_id, --13 id_credito
        0, --14 id_credito_anterior
        bprec.id_destino_credito, --15 id_destino_credito
        0, --16 id_garantia
        NULL, --17 id_linea
        3, --18 id_modalidad_credito
        2, --19 id_moneda
(
          SELECT
            description
          FROM cv_ruta
          WHERE
            cv_ruta_id = bprec.cv_ruta_id LIMIT 1)::int, --20 id_municipio
        1, --21 id_oficina
        1, --22 id_origen_recursos
        1, --23 id_periodo_cobro_interes
        1, --24 id_periodo_cobro_principal
        0, --25 id_sindicado
        1, --26 id_situacion_credito
        1, --27 id_tipo_agrupacion_credito
        IIF(creditovencido, 0.00, saldo_periodo_interes), --28 interes_corriente
        0.00, -- IIF(creditovencido, saldo_periodo_interes, 0.00), --29 interes_vencidos
        0.00, --30 mto_adjudicacion_credito
        iif((creditovencido
          AND dias_por_vencer BETWEEN 0 AND 30), saldo_periodo_interes, 0.00), --31 mto_intereses_por_vencer_030
        iif((creditovencido
          AND dias_por_vencer BETWEEN 31 AND 60), saldo_periodo_interes, 0.00), --32 mto_intereses_por_vencer_060
        iif((creditovencido
          AND dias_por_vencer BETWEEN 61 AND 90), saldo_periodo_interes, 0.00), --33 mto_intereses_por_vencer_090
        iif((creditovencido
          AND dias_por_vencer BETWEEN 91 AND 120), saldo_periodo_interes, 0.00), --34 mto_intereses_por_vencer_120
        iif((creditovencido
          AND dias_por_vencer BETWEEN 121 AND 180), saldo_periodo_interes, 0.00), --35 mto_intereses_por_vencer_180
        iif((creditovencido
          AND dias_por_vencer BETWEEN 181 AND 360), saldo_periodo_interes, 0.00), --36 mto_intereses_por_vencer_360
        iif((creditovencido
          AND dias_por_vencer > 360), saldo_periodo_interes, 0.00), --37 mto_intereses_por_vencer_mas_360
        /* 0.00, -- iif((creditovencido            AND dias_por_vencer BETWEEN 0 AND 30), saldo_periodo_interes, 0.00), --31 mto_intereses_por_vencer_030
         0.00, -- iif((creditovencido             AND dias_por_vencer BETWEEN 31 AND 60), saldo_periodo_interes, 0.00), --32 mto_intereses_por_vencer_060
         0.00, -- iif((creditovencido             AND dias_por_vencer BETWEEN 61 AND 90), saldo_periodo_interes, 0.00), --33 mto_intereses_por_vencer_090
         0.00, -- iif((creditovencido             AND dias_por_vencer BETWEEN 91 AND 120), saldo_periodo_interes, 0.00), --34 mto_intereses_por_vencer_120
         0.00, -- iif((creditovencido            AND dias_por_vencer BETWEEN 121 AND 180), saldo_periodo_interes, 0.00), --35 mto_intereses_por_vencer_180
         0.00, -- iif((creditovencido             AND dias_por_vencer BETWEEN 181 AND 360), saldo_periodo_interes, 0.00), --36 mto_intereses_por_vencer_360
         0.00, -- iif((creditovencido            AND dias_por_vencer > 360), saldo_periodo_interes, 0.00), --37 mto_intereses_por_vencer_mas_360 */
        car.monto, --38 mto_original
        IIF(dias_por_vencer <= 30, saldo_periodo_principal, 0.00), --39 mto_por_vencer_030
        IIF(dias_por_vencer > 30
          AND dias_por_vencer <= 60, saldo_periodo_principal, 0.00), --40 mto_por_vencer_060
        IIF(dias_por_vencer > 60
          AND dias_por_vencer <= 90, saldo_periodo_principal, 0.00), --41 mto_por_vencer_090
        IIF(dias_por_vencer > 90
          AND dias_por_vencer <= 120, saldo_periodo_principal, 0.00), --42 mto_por_vencer_120
        IIF(dias_por_vencer > 120
          AND dias_por_vencer <= 180, saldo_periodo_principal, 0.00), --43 mto_por_vencer_180
        IIF(dias_por_vencer > 180
          AND dias_por_vencer <= 360, saldo_periodo_principal, 0.00), --44 mto_por_vencer_360
        IIF(dias_por_vencer > 360, saldo_periodo_principal, 0.00), --45 mto_por_vencer_mas_360
        0.00, --46 mto_prorrogado
        0.00, --47 mto_saneado
        IIF(dias_vencidos <= 15, saldo_periodo_principal, 0.00), --48 mto_vencido_015
        IIF(dias_vencidos > 15
          AND dias_vencidos <= 30, saldo_periodo_principal, 0.00), --49 mto_vencido_030
        IIF(dias_vencidos > 30
          AND dias_vencidos <= 60, saldo_periodo_principal, 0.00), --50 mto_vencido_060
        IIF(dias_vencidos > 60
          AND dias_vencidos <= 90, saldo_periodo_principal, 0.00), --51 mto_vencido_090
        IIF(dias_vencidos > 90
          AND dias_vencidos <= 120, saldo_periodo_principal, 0.00), --52 mto_vencido_120
        IIF(dias_vencidos > 120
          AND dias_vencidos <= 180, saldo_periodo_principal, 0.00), --53 mto_vencido_180
        IIF(dias_vencidos > 180
          AND dias_vencidos <= 360, saldo_periodo_principal, 0.00), --54 mto_vencido_360
        IIF(dias_vencidos > 360, saldo_periodo_principal, 0.00), --55 mto_vencido_mas_360
        '', --56 observaciones
        0.00, --57 otras_comisiones_por_cobrar
        0.00, --58 provision
        saldo_periodo_total *(1 - car.tasa), --59 saldo
        car.tasa, --60 tasa
        car.tasa, --61 tasa_costo_efectiva
        car.tasa, --62 tasa_contrato
        0.00, --63 tipo_cambio_oficial
        4, --64 id_sector_economico
        1, --65 id_met_atencion
(
          SELECT
            kdesc
          FROM tipozona
          WHERE
            kname = bprec.tipo_localizacion)::int, --66 id_tipo_zona
        NULL, --67 fecha_ultima_prorroga
        NULL, --68 plazo_prorroga
        NULL, --69 fecha_estado
        IIF(saldo_periodo_total = 0, 7, 6), --70 id_estado_credito
        '113-01-001', --71 cta_contable
        2, --72 id_moneda_linea
        0.00, --73 mto_en_mora
        NULL, --74 id_credito_grupo
(
          SELECT
            cedula
          FROM analistas
          WHERE
            cv_ruta_id = bprec.cv_ruta_id) --75 id_analista
);

  END LOOP;


  /*** Start the rest of the process for month end ***/
  PERFORM
    icc_recuperaciones_periodo_process(period_id);
END
$BODY$;

ALTER FUNCTION adempiere.icc_credito_por_periodo(numeric) OWNER TO adempiere;

