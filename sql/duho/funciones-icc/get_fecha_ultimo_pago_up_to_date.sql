-- FUNCTION: adempiere.get_fecha_ultimo_pago_up_to_date(numeric, date)
-- DROP FUNCTION IF EXISTS adempiere.get_fecha_ultimo_pago_up_to_date(numeric, date);
/**
 * Obtiene la fecha del último pago realizado para una cartera hasta una fecha específica.
 * 
 * @param cartera_id El ID de la cartera.
 * @param upto La fecha hasta la cual se busca el último pago.
 * @return La fecha del último pago o NULL si no hay pagos.
 */
CREATE OR REPLACE FUNCTION adempiere.get_fecha_ultimo_pago_up_to_date(cartera_id numeric, upto date)
  RETURNS date
  LANGUAGE 'plpgsql'
  COST 100 VOLATILE PARALLEL SAFE
  AS $BODY$
DECLARE
  fullrec record;
  fecha_up date DEFAULT NULL;
BEGIN
  SELECT
    max(cob.operacion::date)
  FROM
    legacy_cobro cob
  WHERE
    cob.operacion::date <= upto
    AND cob.id_cartera = cartera_id INTO fecha_up;

  RETURN fecha_up;
END;
$BODY$;

ALTER FUNCTION adempiere.get_fecha_ultimo_pago_up_to_date(numeric, date) OWNER TO adempiere;

