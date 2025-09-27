-- FUNCTION: adempiere.icc_credito_por_periodo(numeric)
-- DROP FUNCTION IF EXISTS adempiere.icc_credito_por_periodo(numeric);
CREATE OR REPLACE FUNCTION adempiere.icc_credito_por_periodo(period_id numeric)
  RETURNS void
  LANGUAGE 'plpgsql'
  COST 100 VOLATILE PARALLEL UNSAFE
  AS $BODY$
DECLARE
  p RECORD;
BEGIN
  -- Obtener la información del período una sola vez
  SELECT
    *
  FROM
    C_Period
  WHERE
    c_period_ID = period_id INTO p;
  -- Eliminar los registros existentes para el período actual.
  DELETE FROM icc_credito
  WHERE c_period_id = period_id;
  DELETE FROM icc_persona
  WHERE c_period_id = period_id;
  DELETE FROM icc_credito_persona
  WHERE c_period_id = period_id;
  DELETE FROM icc_analista
  WHERE c_period_id = period_id;
  -- Usar Common Table Expressions (CTEs) para un procesamiento basado en conjuntos.
  WITH cartera_activa AS (
    -- 1. Seleccionar la cartera relevante para el período.
    SELECT
      lc.*,
(lc.fecha::date + lc.dias_cre) AS fecha_vencimiento
    FROM
      legacy_cartera lc
    WHERE
      lc.fecha::date <= p.enddate::date
),
abonos_calculados AS (
  -- 2. Calcular todos los abonos y la fecha del último pago por cartera.
  SELECT
    id_cartera,
    COALESCE(SUM(abono), 0.00) AS total_abonos,
    COUNT(*) AS cantidad_cuotas,
    MAX(operacion::date) AS fecha_ultimo_pago
  FROM
    legacy_cobro
  WHERE
    operacion::date <= p.enddate::date
  GROUP BY
    id_cartera
),
movimientos_periodo AS (
  -- 3. Calcular abonos y días efectivos dentro del período
  SELECT
    id_cartera,
    COALESCE(SUM(abono), 0.00) AS abonos_en_periodo
  FROM
    legacy_cobro
  WHERE
    operacion::date BETWEEN p.startdate::date AND p.enddate::date
  GROUP BY
    id_cartera
),
saldos_calculados AS (
  -- 4. Unir la cartera con sus abonos y calcular saldos e intereses.
  SELECT
    ca.*,
    ac.total_abonos,
    ac.cantidad_cuotas,
    ac.total_abonos *(1 - ca.tasa) AS total_abonos_principal,
    ac.total_abonos *(ca.tasa) AS total_abonos_interes,
    ca.dias_cre - ac.cantidad_cuotas AS cuotas_restantes,
    ac.fecha_ultimo_pago,
(ca.montototal - ac.total_abonos) AS saldo_cierre,
(ca.monto * ca.tasa / 365.25) * adempiere.get_dias_efectivos(GREATEST(ca.fecha::date, p.startdate::date), p.enddate::date) AS interes_devengado_en_periodo,
    mp.abonos_en_periodo
  FROM
    cartera_activa ca
    LEFT JOIN abonos_calculados ac ON ca.legacy_cartera_id = ac.id_cartera
    LEFT JOIN movimientos_periodo mp ON ca.legacy_cartera_id = mp.id_cartera
  WHERE (ca.montototal - COALESCE(ac.total_abonos, 0)) > 0
  OR ca.fecha::date BETWEEN p.startdate::date AND p.enddate::date
  OR ac.fecha_ultimo_pago BETWEEN p.startdate::date AND p.enddate::date
),
datos_enriquecidos AS (
  -- 5. Unir con tablas de negocio y de referencia para consolidar toda la información.
  SELECT
    s.*,
    p.enddate::date AS fecha_corte,
(s.fecha_vencimiento < p.enddate::date) AS creditovencido,
(p.enddate::date - s.fecha_vencimiento) AS dias_vencidos,
(s.fecha_vencimiento - p.enddate::date) AS dias_por_vencer,
    -- Saldo de Principal: Es el monto original menos el abono que sobra después de pagar el interés.
    GREATEST(0, s.monto - s.total_abonos_principal) AS saldo_periodo_principal,
    -- Saldo de Interés: Es el interés devengado acumulado menos el abono aplicado a interés acumulado
    GREATEST(0, s.total_abonos - s.total_abonos_principal) AS saldo_periodo_interes,
    -- Porción de los abonos del periodo que se aplican a interés
    COALESCE(s.abonos_en_periodo * s.tasa /(1 + s.tasa), 0.00) AS abono_interes_en_periodo,
    bp.name AS nombre_cliente,
    bp.taxid,
    bp.birthday,
    bp.civilstatus,
    bp.genero,
    bp.cv_ruta_id,
    bp.tipo_riesgo,
    bp.tipo_localizacion,
    COALESCE(bpg.description, '7026101')::int AS id_destino_credito,
    u.name AS nombre_analista,
    bp_user.taxid AS cedula_analista,
    bp_user.genero AS genero_analista,
    ruta.description AS id_municipio,
(
      SELECT
        description
      FROM
        AD_Ref_List
      WHERE
        AD_Reference_ID = 1000013
        AND value = bp.civilstatus) AS id_estado_civil,
(
        SELECT
          description
        FROM
          AD_Ref_List
        WHERE
          AD_Reference_ID = 1000020
          AND value = bp.genero) AS id_genero_cliente,
(
          SELECT
            description
          FROM
            AD_Ref_List
          WHERE
            AD_Reference_ID = 1000020
            AND value = bp_user.genero) AS id_genero_analista,
(
            SELECT
              description
            FROM
              AD_Ref_List
            WHERE
              AD_Reference_ID = 1000022
              AND value = bp.tipo_riesgo) AS id_clasificacion_credito,
(
              SELECT
                description
              FROM
                AD_Ref_List
              WHERE
                AD_Reference_ID = 1000021
                AND value = bp.tipo_localizacion) AS id_tipo_zona
            FROM
              saldos_calculados s
              JOIN c_bpartner bp ON s.c_bpartner_id = bp.c_bpartner_id
              JOIN c_bp_group bpg ON bp.c_bp_group_id = bpg.c_bp_group_id
              JOIN cv_ruta ruta ON bp.cv_ruta_id = ruta.cv_ruta_id
              LEFT JOIN ad_user u ON ruta.ad_user_id = u.ad_user_id
              LEFT JOIN c_bpartner bp_user ON u.c_bpartner_id = bp_user.c_bpartner_id
),
-- 6. Lógica de "UPSERT" (UPDATE/INSERT) para las tablas dimensionales.
-- icc_persona
updated_persona AS (
  UPDATE
    icc_persona persona_to_update
  SET
    fecha_nacimiento = d.birthday,
    id_actividad_economica = d.id_destino_credito,
    id_estado_civil = d.id_estado_civil::int,
    id_genero = d.id_genero_cliente::int,
    id_municipio = d.id_municipio,
    id_persona = d.taxid,
    nombre = d.nombre_cliente
  FROM ( SELECT DISTINCT
      c_bpartner_id,
      birthday,
      id_destino_credito,
      id_estado_civil,
      id_genero_cliente,
      id_municipio,
      taxid,
      nombre_cliente
    FROM
      datos_enriquecidos) d
  WHERE
    persona_to_update.c_period_id = period_id
    AND persona_to_update.c_bpartner_id = d.c_bpartner_id
  RETURNING
    persona_to_update.c_bpartner_id
),
inserted_persona AS (
INSERT INTO icc_persona(c_period_id, c_bpartner_id, fecha_nacimiento, id_actividad_economica, id_estado_civil, id_genero, id_municipio, id_persona, nombre, id_pais, pep, id_tipo_persona_juridica, id_tipo_persona, id_tipo_documento, id_tipo_grupo)
  SELECT DISTINCT
    period_id,
    c_bpartner_id,
    birthday,
    id_destino_credito,
    id_estado_civil::int,
    id_genero_cliente::int,
    id_municipio,
    taxid,
    nombre_cliente,
    158,
    0,
    0,
    1,
    1,
    2
  FROM
    datos_enriquecidos d
  WHERE
    NOT EXISTS (
      SELECT
        1
      FROM
        updated_persona up
      WHERE
        up.c_bpartner_id = d.c_bpartner_id)
),
-- icc_credito_persona
updated_credito_persona AS (
  UPDATE
    icc_credito_persona cp
  SET
    id_persona = d.taxid::varchar,
    id_tipo_documento = 1,
    id_moneda = 2
  FROM
    datos_enriquecidos d
  WHERE
    cp.c_period_id = period_id
    AND cp.id_credito = d.legacy_cartera_id::varchar
  RETURNING
    cp.id_credito
),
inserted_credito_persona AS (
INSERT INTO icc_credito_persona(c_period_id, id_credito, id_persona, id_tipo_documento, id_moneda)
  SELECT
    period_id,
    legacy_cartera_id::varchar,
    taxid::varchar,
    1,
    2
  FROM
    datos_enriquecidos d
  WHERE
    NOT EXISTS (
      SELECT
        1
      FROM
        updated_credito_persona ucp
      WHERE
        ucp.id_credito = d.legacy_cartera_id::varchar)
),
-- icc_analista
updated_analista AS (
  UPDATE
    icc_analista a
  SET
    nombre = d.nombre_analista,
    id_genero = d.id_genero_analista::int
  FROM ( SELECT DISTINCT
      cedula_analista,
      nombre_analista,
      id_genero_analista
    FROM
      datos_enriquecidos
    WHERE
      cedula_analista IS NOT NULL) d
  WHERE
    a.c_period_id = period_id
    AND a.id_analista = d.cedula_analista
  RETURNING
    a.id_analista
),
inserted_analista AS (
INSERT INTO icc_analista(c_period_id, id_analista, nombre, id_genero)
  SELECT DISTINCT
    period_id,
    cedula_analista,
    nombre_analista,
    id_genero_analista::int
  FROM
    datos_enriquecidos d
  WHERE
    d.cedula_analista IS NOT NULL
    AND NOT EXISTS (
      SELECT
        1
      FROM
        updated_analista ua
      WHERE
        ua.id_analista = d.cedula_analista))
  -- 6. Inserción final en la tabla de hechos (icc_credito).
  INSERT INTO adempiere.icc_credito(legacy_cartera_id, c_period_id, cantidad_cuotas, cantidad_prorrogas, comision_acumulada_por_cobrar, cuotas_vencidas, dias_mora_interes, dias_mora_principal, fecha_otorgamiento, fecha_vencimiento_credito, id_clasificacion_credito, id_tipo_credito, id_credito, id_credito_anterior, id_destino_credito, id_garantia, id_linea, id_modalidad_credito, id_moneda, id_municipio, id_oficina, id_origen_recursos, id_periodo_cobro_interes, id_periodo_cobro_principal, id_sindicado, id_situacion_credito, id_tipo_agrupacion_credito, interes_corriente, interes_vencidos, mto_adjudicacion_credito, mto_intereses_por_vencer_030, mto_intereses_por_vencer_060, mto_intereses_por_vencer_090, mto_intereses_por_vencer_120, mto_intereses_por_vencer_180, mto_intereses_por_vencer_360, mto_intereses_por_vencer_mas_360, mto_original, mto_por_vencer_030, mto_por_vencer_060, mto_por_vencer_090, mto_por_vencer_120, mto_por_vencer_180, mto_por_vencer_360, mto_por_vencer_mas_360, mto_prorrogado, mto_saneado, mto_vencido_015, mto_vencido_030, mto_vencido_060, mto_vencido_090, mto_vencido_120, mto_vencido_180, mto_vencido_360, mto_vencido_mas_360, observaciones, otras_comisiones_por_cobrar, provision, saldo, tasa, tasa_costo_efectiva, tasa_contrato, tipo_cambio_oficial, id_sector_economico, id_met_atencion, id_tipo_zona, fecha_ultima_prorroga, plazo_prorroga, fecha_estado, id_estado_credito, cta_contable, id_moneda_linea, mto_en_mora, id_credito_grupo, id_analista)
SELECT
  legacy_cartera_id, -- 01 legacy_cartera_id
  period_id, -- 02 c_period_id
  cantidad_cuotas, -- 03 cantidad_cuotas
  0, -- 04 cantidad_prorrogas
  0.00, -- 05 comision_acumulada_por_cobrar
  CASE WHEN creditovencido THEN
    cuotas_restantes
  ELSE
    0.0
  END, -- 06 cuotas_vencidas (GEM)
  0.00, -- 07 dias_mora_interes
  0.00, -- 08 dias_mora_principal
  fecha, -- 09 fecha_otorgamiento
  fecha_vencimiento, -- 10 fecha_vencimiento_credito
  id_clasificacion_credito::int, -- 11 id_clasificacion_credito
  2, -- 12 id_tipo_credito
  legacy_cartera_id, -- 13 id_credito
  0, -- 14 id_credito_anterior
  id_destino_credito, -- 15 id_destino_credito
  NULL, -- 16 id_garantia
  NULL, -- 17 id_linea
  NULL, -- 18 id_modalidad_credito
  2, -- 19 id_moneda
  id_municipio::int, -- 20 id_municipio
  1, -- 21 id_oficina
  1, -- 22 id_origen_recursos
  1, -- 23 id_periodo_cobro_interes
  1, -- 24 id_periodo_cobro_principal
  NULL, -- 25 id_sindicado
  1, -- 26 id_situacion_credito
  1, -- 27 id_tipo_agrupacion_credito
  GREATEST(0, interes_devengado_en_periodo - abono_interes_en_periodo), -- 28 interes_corriente
IIF(creditovencido, saldo_periodo_interes, 0.00), -- 29 interes_vencidos
0.00, -- 30 mto_adjudicacion_credito
iif(creditovencido
  AND dias_por_vencer BETWEEN 0 AND 30, saldo_periodo_interes, 0.00), -- 31 mto_intereses_por_vencer_030
iif(creditovencido
  AND dias_por_vencer BETWEEN 31 AND 60, saldo_periodo_interes, 0.00), -- 32 mto_intereses_por_vencer_060
iif(creditovencido
  AND dias_por_vencer BETWEEN 61 AND 90, saldo_periodo_interes, 0.00), -- 33 mto_intereses_por_vencer_090
iif(creditovencido
  AND dias_por_vencer BETWEEN 91 AND 120, saldo_periodo_interes, 0.00), -- 34 mto_intereses_por_vencer_120
iif(creditovencido
  AND dias_por_vencer BETWEEN 121 AND 180, saldo_periodo_interes, 0.00), -- 35 mto_intereses_por_vencer_180
iif(creditovencido
  AND dias_por_vencer BETWEEN 181 AND 360, saldo_periodo_interes, 0.00), -- 36 mto_intereses_por_vencer_360
iif(creditovencido
  AND dias_por_vencer > 360, saldo_periodo_interes, 0.00), -- 37 mto_intereses_por_vencer_mas_360
monto, -- 38 mto_original
IIF(dias_por_vencer <= 30, saldo_periodo_principal, 0.00), -- 39 mto_por_vencer_030
IIF(dias_por_vencer BETWEEN 31 AND 60, saldo_periodo_principal, 0.00), -- 40 mto_por_vencer_060
IIF(dias_por_vencer BETWEEN 61 AND 90, saldo_periodo_principal, 0.00), -- 41 mto_por_vencer_090
IIF(dias_por_vencer BETWEEN 91 AND 120, saldo_periodo_principal, 0.00), -- 42 mto_por_vencer_120
IIF(dias_por_vencer BETWEEN 121 AND 180, saldo_periodo_principal, 0.00), -- 43 mto_por_vencer_180
IIF(dias_por_vencer BETWEEN 181 AND 360, saldo_periodo_principal, 0.00), -- 44 mto_por_vencer_360
IIF(dias_por_vencer > 360, saldo_periodo_principal, 0.00), -- 45 mto_por_vencer_mas_360
0.00, -- 46 mto_prorrogado
0.00, -- 47 mto_saneado
IIF(dias_vencidos <= 15, saldo_periodo_principal, 0.00), -- 48 mto_vencido_015
IIF(dias_vencidos BETWEEN 16 AND 30, saldo_periodo_principal, 0.00), -- 49 mto_vencido_030
IIF(dias_vencidos BETWEEN 31 AND 60, saldo_periodo_principal, 0.00), -- 50 mto_vencido_060
IIF(dias_vencidos BETWEEN 61 AND 90, saldo_periodo_principal, 0.00), -- 51 mto_vencido_090
IIF(dias_vencidos BETWEEN 91 AND 120, saldo_periodo_principal, 0.00), -- 52 mto_vencido_120
IIF(dias_vencidos BETWEEN 121 AND 180, saldo_periodo_principal, 0.00), -- 53 mto_vencido_180
IIF(dias_vencidos BETWEEN 181 AND 360, saldo_periodo_principal, 0.00), -- 54 mto_vencido_360
IIF(dias_vencidos > 360, saldo_periodo_principal, 0.00), -- 55 mto_vencido_mas_360
'', -- 56 observaciones
0.00, -- 57 otras_comisiones_por_cobrar
0.00, -- 58 provision
saldo_periodo_principal, -- 59 saldo
tasa, -- 60 tasa
tasa, -- 61 tasa_costo_efectiva
tasa, -- 62 tasa_contrato
0.00, -- 63 tipo_cambio_oficial
4, -- 64 id_sector_economico
1, -- 65 id_met_atencion
id_tipo_zona::int, -- 66 id_tipo_zona
NULL, -- 67 fecha_ultima_prorroga
NULL, -- 68 plazo_prorroga
NULL, -- 69 fecha_estado
IIF((saldo_periodo_principal + saldo_periodo_interes) <= 0, 7, 6), -- 70 id_estado_credito
'113-01-001', -- 71 cta_contable
2, -- 72 id_moneda_linea
0.00, -- 73 mto_en_mora
NULL, -- 74 id_credito_grupo
cedula_analista -- 75 id_analista
FROM
  datos_enriquecidos;
  -- La llamada a este proceso se mantiene al final.
  PERFORM
    icc_recuperaciones_periodo_process(period_id);

END
$BODY$;

