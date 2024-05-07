CREATE OR REPLACE FUNCTION icc_credito_persona_save_or_update(p_data record -- Record containing credit-person data
)
  RETURNS void
  AS $$
DECLARE
  v_exists boolean;
  v_persona_credito_id uuid;
BEGIN
  -- Check if record exists for the given period and credit ID combination
  SELECT
    icc_credito_persona_id INTO v_persona_credito_id
  FROM
    icc_credito_persona
  WHERE
    c_period_id = p_data.c_period_id
    AND id_credito = p_data.id_credito;

  v_exists := FOUND;
  -- Check if any rows were returned
  IF v_exists THEN
    -- Update existing record
    UPDATE
      icc_credito_persona
    SET
      (id_persona,
        id_tipo_documento) =(p_data.id_persona,
        p_data.id_tipo_documento)
    WHERE
      icc_credito_persona_id = v_persona_credito_id;
  ELSE
    -- Insert new record
    INSERT INTO icc_credito_persona(c_period_id, --
      id_credito, --
      id_persona, --
      id_tipo_documento, --
      id_moneda)
      VALUES (p_data.c_period_id, --
        p_data.id_credito, --
        p_data.id_persona, --
        p_data.id_tipo_documento, --
        p_data.id_moneda);
  END IF;
END;
$$
LANGUAGE plpgsql;

