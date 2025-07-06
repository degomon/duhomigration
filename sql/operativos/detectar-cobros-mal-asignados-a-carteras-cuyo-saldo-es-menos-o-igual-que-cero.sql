/* 
select distinct bp.c_bpartner_id, bp.name, -- car.legacy_cartera_id,
count(cob.legacy_cobro_id)
from legacy_cobro cob
 inner join legacy_cartera car on cob.id_cartera = car.legacy_cartera_id
 inner join c_bpartner bp on car.c_bpartner_id = bp.c_bpartner_id
where (cob.synced != 'Y' or cob.synced is null) and cob.origen = 'native' and cob.abono>0 
and car.saldo <= 0
 group by bp.c_bpartner_id, bp.name, car.legacy_cartera_id
order by 3 desc


-- select * from legacy_cartera where saldo > 0 and c_bpartner_id is null
 */
-- FUNCTION: adempiere.duho_balancecheck()
-- DROP FUNCTION IF EXISTS adempiere.duho_balancecheck();
DO $$
DECLARE
  bptofix record;
  carrec record;
BEGIN
  FOR bptofix IN WITH bptocheck AS (
    SELECT DISTINCT
      bp.name,
      bp.c_bpartner_id,
      car.legacy_cartera_id,
      count(cob.legacy_cobro_id)
    FROM
      legacy_cobro cob
      INNER JOIN legacy_cartera car ON cob.id_cartera = car.legacy_cartera_id
      INNER JOIN c_bpartner bp ON cob.c_bpartner_id = bp.c_bpartner_id
    WHERE (cob.synced != 'Y'
      OR cob.synced IS NULL)
    AND cob.origen = 'native'
    AND cob.abono > 0
    AND car.saldo <= 0
  GROUP BY
    bp.name,
    bp.c_bpartner_id,
    car.legacy_cartera_id
  ORDER BY
    3 DESC
)
SELECT
  *
FROM
  bptocheck bpt
  -- where bpt.saldo<>bpt.totalopenbalance
  LOOP
    -- raise notice 'BP % should be fixed  ', bptofix.name;
    SELECT
      *
    FROM
      legacy_cartera
    WHERE
      saldo > 0
      AND c_bpartner_id = bptofix.c_bpartner_id
    ORDER BY
      fecha ASC
    LIMIT 1
    -- select * from legacy_cartera where saldo>0 and c_bpartner_id = 1001287 order by fecha asc limit 1
    INTO carrec;
    IF (carrec.legacy_cartera_id IS NULL) THEN
      RAISE NOTICE '% BP % can be fixed % ', bptofix.c_bpartner_id, bptofix.name, ' NO ';
    ELSE
      UPDATE
        legacy_cobro
      SET
        id_cartera = carrec.legacy_cartera_id
      WHERE
        c_bpartner_id = bptofix.c_bpartner_id
        AND synced = 'N';
      RAISE NOTICE '% BP % can be fixed % ', bptofix.c_bpartner_id, bptofix.name, ' YES ';
    END IF;
  END LOOP;
END;
$$;

