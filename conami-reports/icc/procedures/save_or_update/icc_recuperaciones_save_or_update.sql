CREATE OR REPLACE FUNCTION icc_recuperaciones_save_or_update(p_c_period_id numeric(10, 0), p_cantidad_pagos int, p_mto_principal_cor numeric, p_mto_interes_cor numeric, p_mto_principal_dol numeric, p_mto_interes_dol numeric, p_id_forma_pago integer, p_id_lugar_pago integer, p_id_tipo_cartera integer)
  RETURNS void
  AS $$
DECLARE
  v_exists boolean := FALSE;
BEGIN
  -- Check if a record with the same c_period_id already exists
  SELECT
    EXISTS (
      SELECT
        1
      FROM
        icc_recuperaciones
      WHERE
        c_period_id = p_c_period_id) INTO v_exists;

  IF v_exists THEN
    -- Update existing record
    UPDATE
      icc_recuperaciones
    SET
      cantidad_pagos = p_cantidad_pagos,
      mto_principal_cor = p_mto_principal_cor,
      mto_interes_cor = p_mto_interes_cor,
      mto_principal_dol = p_mto_principal_dol,
      mto_interes_dol = p_mto_interes_dol,
      id_forma_pago = p_id_forma_pago,
      id_lugar_pago = p_id_lugar_pago,
      id_tipo_cartera = p_id_tipo_cartera
    WHERE
      c_period_id = p_c_period_id;
  ELSE
    -- Insert new record
    INSERT INTO icc_recuperaciones(c_period_id, cantidad_pagos, mto_principal_cor, mto_interes_cor, mto_principal_dol, mto_interes_dol, id_forma_pago, id_lugar_pago, id_tipo_cartera)
      VALUES (p_c_period_id, p_cantidad_pagos, p_mto_principal_cor, p_mto_interes_cor, p_mto_principal_dol, p_mto_interes_dol, p_id_forma_pago, p_id_lugar_pago, p_id_tipo_cartera);
  END IF;

END;
$$
LANGUAGE plpgsql;

