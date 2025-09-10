-- FUNCTION: adempiere.get_interes_up_to_date(numeric, date)
-- DROP FUNCTION IF EXISTS adempiere.get_interes_up_to_date(numeric, date);
CREATE OR REPLACE FUNCTION adempiere.get_interes_up_to_date(p_cartera_id numeric, p_upto_date date)
  RETURNS numeric
  LANGUAGE 'plpgsql'
  COST 100 VOLATILE PARALLEL UNSAFE
  AS $BODY$
DECLARE
  credito_rec RECORD;
  dias_transcurridos integer;
  dias_efectivos integer;
  interes_calculado numeric;
  interes_cobrado numeric;
BEGIN
  -- 1. Buscar el registro del crédito en la tabla legacy_cartera.
  SELECT
    fecha,
    dias_cre,
    tasa,
    cuota INTO credito_rec
  FROM
    adempiere.legacy_cartera
  WHERE
    legacy_cartera_id = p_cartera_id;
  -- Si no se encuentra el crédito, no se puede calcular el interés.
  IF NOT FOUND THEN
    RETURN 0.00;
  END IF;
  -- 2. Calcular los días transcurridos desde el inicio del crédito hasta la fecha de corte.
  -- Se asegura que el cálculo no sea negativo si la fecha de corte es anterior a la de inicio.
  dias_transcurridos := GREATEST(0, get_dias_efectivos(credito_rec.fecha::date, p_upto_date));
  -- 3. Determinar los días efectivos para el cálculo.
  -- Se usa LEAST para tomar el valor menor entre los días transcurridos y el plazo total del crédito.
  -- Esto implementa la regla: el interés no se sigue acumulando después de la fecha de vencimiento.
  dias_efectivos := LEAST(dias_transcurridos, credito_rec.dias_cre);
  -- 4. Calcular el interés devengado multiplicando los días efectivos por la cuota diaria.
  interes_calculado := dias_efectivos * credito_rec.cuota * credito_rec.tasa;
  interes_cobrado := adempiere.get_abonos_up_to_date(p_cartera_id, p_upto_date) * credito_rec.tasa;
  -- 5. Devolver el interés calculado, asegurándose de retornar 0.00 si el resultado es nulo.
  RETURN COALESCE(GREATEST(interes_calculado, interes_cobrado), 0.00);

END;
$BODY$;

ALTER FUNCTION adempiere.get_interes_up_to_date(numeric, date) OWNER TO adempiere;

COMMENT ON FUNCTION adempiere.get_interes_up_to_date(numeric, date) IS 'Calcula el interés total devengado para un crédito desde su fecha de inicio hasta una fecha de corte específica. El cálculo se topa al plazo total del crédito (dias_cre).';

