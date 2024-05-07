CREATE OR REPLACE FUNCTION icc_persona_save_or_update(p_data record -- Record containing person data
)
  RETURNS void
  AS $$
DECLARE
  v_exists boolean;
  v_persona_id uuid;
BEGIN
  -- Check if record exists for the given period and partner IDs
  SELECT
    icc_persona_id INTO v_persona_id
  FROM
    icc_persona
  WHERE
    c_period_id = p_data.c_period_id
    AND c_bpartner_id = p_data.c_bpartner_id;

  v_exists := FOUND;
  -- Check if any rows were returned
  IF v_exists THEN
    -- Update existing record
    UPDATE
      icc_persona
    SET
      (c_period_id,
        c_bpartner_id,
        fecha_nacimiento,
        id_actividad_economica,
        id_cedula_residencia,
        id_estado_civil,
        id_genero,
        id_municipio,
        id_persona,
        id_tipo_persona,
        id_tipo_documento,
        id_tipo_grupo,
        nombre,
        id_pais,
        pep,
        id_tipo_persona_juridica) =(p_data.c_period_id,
        p_data.c_bpartner_id,
        p_data.fecha_nacimiento,
        p_data.id_actividad_economica,
        p_data.id_cedula_residencia,
        p_data.id_estado_civil,
        p_data.id_genero,
        p_data.id_municipio,
        p_data.id_persona,
        p_data.id_tipo_persona,
        p_data.id_tipo_documento,
        p_data.id_tipo_grupo,
        p_data.nombre,
        p_data.id_pais,
        p_data.pep,
        p_data.id_tipo_persona_juridica)
    WHERE
      icc_persona_id = v_persona_id;
  ELSE
    -- Insert new record
    INSERT INTO icc_persona(c_period_id, c_bpartner_id, fecha_nacimiento, id_actividad_economica, id_cedula_residencia, id_estado_civil, id_genero, id_municipio, id_persona, id_tipo_persona, id_tipo_documento, id_tipo_grupo, nombre, id_pais, pep, id_tipo_persona_juridica)
      VALUES (p_data.c_period_id, p_data.c_bpartner_id, p_data.fecha_nacimiento, p_data.id_actividad_economica, p_data.id_cedula_residencia, p_data.id_estado_civil, p_data.id_genero, p_data.id_municipio, p_data.id_persona, p_data.id_tipo_persona, p_data.id_tipo_documento, p_data.id_tipo_grupo, p_data.nombre, p_data.id_pais, p_data.pep, p_data.id_tipo_persona_juridica);
  END IF;
END;
$$
LANGUAGE plpgsql;

