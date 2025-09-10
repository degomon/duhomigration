-- FUNCTION: adempiere.get_dias_efectivos(date, date)
-- DROP FUNCTION IF EXISTS adempiere.get_dias_efectivos(date, date);
CREATE OR REPLACE FUNCTION adempiere.get_dias_efectivos(p_fecha_inicial date, p_fecha_final date)
  RETURNS integer
  LANGUAGE 'plpgsql'
  COST 100 IMMUTABLE PARALLEL UNSAFE
  AS $BODY$
DECLARE
  v_dias_efectivos integer;
BEGIN
  -- Utiliza GENERATE_SERIES para crear una serie de días entre las fechas
  -- y luego cuenta solo aquellos que no son domingo (DOW = 0 en PostgreSQL).
  SELECT
    COUNT(*)::int INTO v_dias_efectivos
  FROM
    GENERATE_SERIES(p_fecha_inicial, p_fecha_final, '1 day'::interval) AS t(day)
WHERE
  EXTRACT(DOW FROM t.day) <> 0;
  -- Excluye los domingos (Sunday = 0)
  RETURN v_dias_efectivos;
END;
$BODY$;

ALTER FUNCTION adempiere.get_dias_efectivos(date, date) OWNER TO adempiere;

COMMENT ON FUNCTION adempiere.get_dias_efectivos(date, date) IS 'Calcula el número de días transcurridos entre dos fechas, excluyendo los domingos.';

