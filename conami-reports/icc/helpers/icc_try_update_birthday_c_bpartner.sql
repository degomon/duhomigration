DO $$
DECLARE
  bp record;
  birthdate date;
BEGIN
  FOR bp IN
  SELECT
    *
  FROM
    c_bpartner
  WHERE
    birthday IS NULL LOOP
      birthdate := icc_extract_birthdate(bp.taxid);

      IF (birthdate IS NOT NULL) THEN
        RAISE NOTICE 'Updating c_bpartner_id: % with birthdate %', bp.name, birthdate;

        UPDATE
          c_bpartner
        SET
          birthday = birthdate
        WHERE
          c_bpartner_id = bp.c_bpartner_id;

      END IF;

    END LOOP;

END;
$$
LANGUAGE plpgsql;

