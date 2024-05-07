CREATE OR REPLACE FUNCTION icc_colocaciones_save_or_update(p_record record)
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
        icc_colocaciones
      WHERE
        c_period_id = p_record.c_period_id) INTO v_exists;

  IF v_exists THEN
    -- Update existing record
    UPDATE
      icc_colocaciones
    SET
      cantidad_creditos = p_record.cantidad_creditos,
      mto_prestamo_cor = p_record.mto_prestamo_cor,
      mto_prestamos_dol = p_record.mto_prestamos_dol,
      mto_desembolsado_cor = p_record.mto_desembolsado_cor,
      mto_desembolsado_dol = p_record.mto_desembolsado_dol,
      id_tipo_fondo = p_record.id_tipo_fondo,
      otro_fondo = p_record.otro_fondo,
      id_forma_entrega = p_record.id_forma_entrega,
      id_tipo_lugar = p_record.id_tipo_lugar
    WHERE
      c_period_id = p_record.c_period_id;
  ELSE
    -- Insert new record
    INSERT INTO icc_colocaciones(c_period_id, cantidad_creditos, mto_prestamo_cor, mto_prestamos_dol, mto_desembolsado_cor, --
      mto_desembolsado_dol, id_tipo_fondo, otro_fondo, id_forma_entrega, id_tipo_lugar) --
      VALUES (p_record.c_period_id, p_record.cantidad_creditos, p_record.mto_prestamo_cor, --
        p_record.mto_prestamos_dol, p_record.mto_desembolsado_cor, p_record.mto_desembolsado_dol, --
        p_record.id_tipo_fondo, p_record.otro_fondo, p_record.id_forma_entrega, p_record.id_tipo_lugar);
  END IF;

END;
$$
LANGUAGE plpgsql;

