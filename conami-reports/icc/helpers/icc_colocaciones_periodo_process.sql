DO $$
DECLARE
  cartera record;
  p record;
  cantidad_creditos numeric DEFAULT 0;
  mto_prestamo_cor numeric DEFAULT 0.00;
  mto_prestamos_dol numeric DEFAULT 0.00;
  mto_desembolsado_cor numeric DEFAULT 0.00;
  mto_desembolsado_dol numeric DEFAULT 0.00;
  colocacionrec record;
BEGIN
  SELECT
    *
  FROM
    c_period
  WHERE
    c_period_id = 1000038 INTO p;
  FOR cartera IN
  SELECT
    *
  FROM
    legacy_cartera
  WHERE
    fecha <= p.enddate::date
    AND fecha >= p.startdate::date LOOP
      cantidad_creditos := cantidad_creditos + 1;
      mto_prestamo_cor := mto_prestamo_cor + cartera.montototal;
      mto_desembolsado_cor := mto_desembolsado_cor + cartera.monto;

      SELECT
        p.c_period_id AS c_period_id,
        cantidad_creditos AS cantidad_creditos,
        mto_prestamo_cor AS mto_prestamo_cor,
        mto_prestamos_dol AS mto_prestamos_dol,
        mto_desembolsado_cor AS mto_desembolsado_cor,
        mto_desembolsado_dol AS mto_desembolsado_dol,
        1 AS id_tipo_fondo,
        '' AS otro_fondo,
        1 AS id_forma_entrega,
        2 AS id_tipo_lugar INTO colocacionrec;
      PERFORM
        icc_colocaciones_save_or_update(colocacionrec);
    END LOOP;
END;
$$
LANGUAGE plpgsql;

