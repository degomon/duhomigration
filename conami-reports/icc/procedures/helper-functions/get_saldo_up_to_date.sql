-- FUNCTION: adempiere.get_saldo_up_to_date(numeric, date)
-- DROP FUNCTION IF EXISTS adempiere.get_saldo_up_to_date(numeric, date);
CREATE OR REPLACE FUNCTION adempiere.get_saldo_up_to_date(cartera_id numeric, upto date)
  RETURNS numeric
  LANGUAGE 'plpgsql'
  COST 100 VOLATILE PARALLEL SAFE
  AS $BODY$
DECLARE
  car record;
  sumatoria numeric DEFAULT 0.00;
BEGIN
  SELECT
    *
  FROM
    legacy_cartera cardata
  WHERE
    cardata.legacy_cartera_id = cartera_id INTO car;
  SELECT
    sum(cob.abono)
  FROM
    legacy_cobro cob
  WHERE
    cob.operacion::date <= upto
    AND cob.id_cartera = cartera_id INTO sumatoria;

  RETURN car.montototal - coalesce(sumatoria, 0.00);
END;
$BODY$;

ALTER FUNCTION adempiere.get_saldo_up_to_date(numeric, date) OWNER TO adempiere;

