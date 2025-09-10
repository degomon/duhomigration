--
-- Función: adempiere.get_dias_efectivos
-- Esta función calcula el número de días transcurridos entre dos fechas,
-- incluyendo la fecha final, pero excluyendo todos los domingos.
--
-- Parámetros:
--   p_fecha_inicial: La fecha de inicio del rango.
--   p_fecha_final: La fecha de fin del rango.
--
-- Retorna:
--   Un entero que representa el total de días no domingos en el rango.
--
CREATE OR REPLACE FUNCTION adempiere.get_dias_efectivos(p_fecha_inicial date, p_fecha_final date)
  RETURNS integer
  AS $$
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
$$
LANGUAGE plpgsql
IMMUTABLE;

COMMENT ON FUNCTION adempiere.get_dias_efectivos(DATE, DATE) IS 'Calcula el número de días transcurridos entre dos fechas, excluyendo los domingos.';

-- Ejemplo de uso
-- SELECT adempiere.get_dias_efectivos('2025-08-11', '2025-08-17');
-- El resultado debería ser 6, ya que la semana tiene 7 días y el domingo ('2025-08-17') se excluye.
