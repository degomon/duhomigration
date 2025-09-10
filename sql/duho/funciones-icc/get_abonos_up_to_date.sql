-- FUNCTION: adempiere.get_abonos_up_to_date(numeric, date)
-- DROP FUNCTION IF EXISTS adempiere.get_abonos_up_to_date(numeric, date);
CREATE OR REPLACE FUNCTION adempiere.get_abonos_up_to_date(cartera_id numeric, upto date)
  RETURNS numeric
  LANGUAGE 'plpgsql'
  COST 100 VOLATILE PARALLEL SAFE
  AS $BODY$
DECLARE
  fullrec record;
  sumatoria numeric DEFAULT 0.00;
BEGIN
  SELECT
    sum(cob.abono)
  FROM
    legacy_cobro cob
  WHERE
    cob.operacion::date <= upto
    AND cob.id_cartera = cartera_id INTO sumatoria;

  RETURN coalesce(sumatoria, 0.00);
END;
$BODY$;

ALTER FUNCTION adempiere.get_abonos_up_to_date(numeric, date) OWNER TO adempiere;

