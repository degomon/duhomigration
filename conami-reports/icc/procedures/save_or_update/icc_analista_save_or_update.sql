CREATE OR REPLACE FUNCTION icc_analista_save_or_update(p_data record -- Record containing analyst data
)
  RETURNS void
  AS $$
DECLARE
  v_exists boolean;
  v_analista_id uuid;
BEGIN
  -- Check if record exists for the given period and analyst ID combination
  SELECT
    icc_analista_id INTO v_analista_id
  FROM
    icc_analista
  WHERE
    c_period_id = p_data.c_period_id
    AND id_analista = p_data.id_analista;

  v_exists := FOUND;
  -- Check if any rows were returned
  IF v_exists THEN
    -- Update existing record
    UPDATE
      icc_analista
    SET
      (nombre,
        id_genero) =(p_data.nombre,
        p_data.id_genero)
    WHERE
      icc_analista_id = v_analista_id;
  ELSE
    -- Insert new record
    INSERT INTO icc_analista(c_period_id, id_analista, nombre, id_genero)
      VALUES (p_data.c_period_id, p_data.id_analista, p_data.nombre, p_data.id_genero);
  END IF;
END;
$$
LANGUAGE plpgsql;

