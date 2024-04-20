DO $$
DECLARE
  legacy_record record;
  record_count integer DEFAULT 1;
BEGIN
  FOR legacy_record IN
  SELECT
    *
  FROM
    legacy_cartera
  WHERE
    monto > 0
    -- and saldo>0
  ORDER BY
    legacy_cartera_id ASC
  LIMIT 10000 offset 20000 LOOP
    IF (record_count % 100 = 0) THEN
      RAISE NOTICE '[%] legacy_cartera_id: %', record_count, legacy_record.legacy_cartera_id;
    END IF;
    record_count := record_count + 1;
    PERFORM
      icc_legacy_cartera_metadata(legacy_record.legacy_cartera_id);
  END LOOP;
END
$$;

--
/*
select car.montototal, carm.* 
from icc_legacy_cartera_metadata carm inner join legacy_cartera car 
on carm.legacy_cartera_id = car.legacy_cartera_id

select count(*)
from icc_legacy_cartera_metadata

select c_period_id, name, * from c_period order by enddate desc limit 12
select icc_credito_por_periodo(1000038)
 */
