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
  saldo_actual numeric;
BEGIN
  -- 1. Buscar el registro del crédito en la tabla legacy_cartera.
  SELECT
    fecha,
    dias_cre,
    tasa,
    cuota,
    montototal,
    monto INTO credito_rec
  FROM
    adempiere.legacy_cartera
  WHERE
    legacy_cartera_id = p_cartera_id;
  -- Si no se encuentra el crédito, no se puede calcular el interés.
  IF NOT FOUND THEN
    RETURN 0.00;
  END IF;
  -- 2. Calcular el saldo actual del crédito para determinar si fue cancelado.
  saldo_actual := adempiere.get_saldo_up_to_date(p_cartera_id, p_upto_date);
  -- 3. Determinar los días efectivos para el cálculo.
  IF saldo_actual <= 0 THEN
    -- Si el crédito está cancelado, usar el plazo completo.
    dias_efectivos := credito_rec.dias_cre;
  ELSE
    -- Si el crédito está activo, calcular los días transcurridos hasta la fecha de corte.
    dias_transcurridos := GREATEST(0, adempiere.get_dias_efectivos(credito_rec.fecha::date, p_upto_date));
    -- Usar el menor valor entre los días transcurridos y el plazo total.
    dias_efectivos := LEAST(dias_transcurridos, credito_rec.dias_cre);
  END IF;
  -- 4. Calcular el interés devengado multiplicando los días efectivos por la cuota diaria de interés.
  interes_calculado := dias_efectivos *(credito_rec.monto * credito_rec.tasa / dias_efectivos);
  -- 5. Devolver el interés calculado, asegurándose de retornar 0.00 si el resultado es nulo.
  RETURN COALESCE(interes_calculado, 0.00);

END;
$BODY$;

ALTER FUNCTION adempiere.get_interes_up_to_date(numeric, date) OWNER TO adempiere;

COMMENT ON FUNCTION adempiere.get_interes_up_to_date(numeric, date) IS 'Calcula el interés total devengado para un crédito desde su fecha de inicio hasta una fecha de corte específica. El cálculo se topa al plazo total del crédito (dias_cre).';

