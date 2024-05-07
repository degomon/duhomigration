-- FUNCTION: adempiere.icc_recuperaciones_periodo_process(numeric)
-- DROP FUNCTION IF EXISTS adempiere.icc_recuperaciones_periodo_process(numeric);
CREATE OR REPLACE FUNCTION adempiere.icc_recuperaciones_periodo_process(p_period_id numeric)
  RETURNS void
  LANGUAGE 'plpgsql'
  COST 100 VOLATILE PARALLEL UNSAFE
  AS $BODY$
DECLARE
  cobrec record;
  cartera record;
  p record;
  abonosrec record;
  -- Use the defined custom type
  cantidad_pagos_count int DEFAULT 0;
  mto_principal_cor numeric DEFAULT 0.00;
  mto_interes_cor numeric DEFAULT 0.00;
BEGIN
  -- Select details for the period
  SELECT
    *
  FROM
    c_period
  WHERE
    c_period_id = p_period_id INTO p;
  -- Check if a period record was found
  IF FOUND THEN
    FOR cobrec IN
    SELECT
      *
    FROM
      legacy_cobro cob
    WHERE
      cob.operacion::date <= p.enddate::date
      AND cob.operacion::date >= p.startdate::date
      AND cob.abono > 0 LOOP
        SELECT
          *
        FROM
          legacy_cartera
        WHERE
          legacy_cartera_id = cobrec.id_cartera INTO cartera;
        SELECT
          *
        FROM
          icc_cob_period_data(cartera, cobrec, p) INTO abonosrec
LIMIT 1;
        cantidad_pagos_count := cantidad_pagos_count + 1;
        mto_principal_cor := mto_principal_cor + abonosrec.abono_principal;
        mto_interes_cor := mto_interes_cor + abonosrec.abono_interes;
      END LOOP;
    -- Save or update the data
    PERFORM
      icc_recuperaciones_save_or_update(p.c_period_id, cantidad_pagos_count, mto_principal_cor, mto_interes_cor, 0.00, -- mto_principal_dol (assuming no value provided)
        0.00, -- mto_interes_dol (assuming no value provided)
        1, -- id_forma_pago (assuming efectivo)
        2, -- id_lugar_pago (assuming recolector)
        1 -- id_tipo_cartera (assuming normal)
);
  ELSE
    RAISE NOTICE 'No record found for period_id: %', p_period_id;
  END IF;
END;
$BODY$;

ALTER FUNCTION adempiere.icc_recuperaciones_periodo_process(numeric) OWNER TO adempiere;

