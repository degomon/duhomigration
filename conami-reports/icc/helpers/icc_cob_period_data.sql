CREATE OR REPLACE FUNCTION icc_cob_period_data(cartera record, cobrec record, p record)
  RETURNS RECORD
  AS $$
DECLARE
  rec record;
  abonos_previos numeric;
  abono_principal numeric DEFAULT 0.00;
  abono_interes numeric DEFAULT 0.00;
  saldo_principal_previo numeric DEFAULT 0.00;
  saldo_interes_previo numeric DEFAULT 0.00;
BEGIN
  SELECT
    coalesce(sum(abono), 0.00) AS abonos_previos
  FROM
    legacy_cobro cobprev
  WHERE
    cobprev.id_cartera = cobrec.id_cartera
    AND cobprev.operacion::date <= cobrec.operacion::date
    AND cobprev.legacy_cobro_id <> cobrec.legacy_cobro_id INTO abonos_previos;
  IF (abonos_previos < cartera.monto) THEN
    -- in this case, there is still principal to pay
    -- cartera.monto is the principal amount
    saldo_principal_previo := cartera.monto - abonos_previos;
    IF (cobrec.abono <= saldo_principal_previo) THEN
      abono_principal := cobrec.abono;
    ELSE
      abono_principal := saldo_principal_previo;
      abono_interes := cobrec.abono - saldo_principal_previo;
    END IF;
  END IF;
  -- when principal is paid, we can start paying interest
  IF (abonos_previos >= cartera.monto) THEN
    abono_principal := 0.00;
    abono_interes := cobrec.abono;
  END IF;

  SELECT
    abono_principal AS abono_principal,
    abono_interes AS abono_interes,
    saldo_principal_previo AS saldo_principal_previo,
    saldo_interes_previo AS saldo_interes_previo INTO rec;
  RETURN rec;
END;
$$
LANGUAGE plpgsql;

