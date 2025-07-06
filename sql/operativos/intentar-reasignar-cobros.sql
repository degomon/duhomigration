/* 
select distinct bp.c_bpartner_id, bp.name, -- car.legacy_cartera_id,
count(cob.legacy_cobro_id)
from legacy_cobro cob
 inner join legacy_cartera car on cob.id_cartera = car.legacy_cartera_id
 inner join c_invoice inv on car.local_id = inv.c_invoice_id
 inner join c_bpartner bp on car.c_bpartner_id = bp.c_bpartner_id
where (cob.synced != 'Y' or cob.synced is null) and cob.origen = 'native' and cob.abono>0 
and duho_getsaldo_cartera_synced(car.legacy_cartera_id) < 0
 group by bp.c_bpartner_id, bp.name, car.legacy_cartera_id
order by 3 desc


-- select * from legacy_cartera where saldo > 0 and c_bpartner_id is null
 */
-- FUNCTION: adempiere.duho_balancecheck()
-- DROP FUNCTION IF EXISTS adempiere.duho_balancecheck();
DO $$
DECLARE
  bptofix record;
  cobrec record;
  asignado numeric;
  candidate record;
BEGIN
  DROP TABLE IF EXISTS temp_cartera;
  CREATE TEMP TABLE temp_cartera(
    legacy_cartera_id numeric,
    fecha timestamp without time zone,
    saldoinicial numeric,
    saldoasignado numeric,
    saldovirtual numeric
  );

  FOR bptofix IN WITH bptocheck AS (
    SELECT DISTINCT
      bp.c_bpartner_id,
      bp.name, -- car.legacy_cartera_id,
      count(cob.legacy_cobro_id)
    FROM
      legacy_cobro cob
      -- inner join legacy_cartera car on cob.id_cartera = car.legacy_cartera_id
      -- inner join c_invoice inv on car.local_id = inv.c_invoice_id
      INNER JOIN c_bpartner bp ON cob.c_bpartner_id = bp.c_bpartner_id
    WHERE (cob.synced != 'Y'
      OR cob.synced IS NULL)
    AND cob.origen = 'native'
    AND cob.abono > 0
    AND (cob.id_cartera IS NULL
      OR duho_getsaldo_cartera_synced(cob.id_cartera) < 0)
  GROUP BY
    bp.c_bpartner_id,
    bp.name
  ORDER BY
    3 DESC
)
SELECT
  *
FROM
  bptocheck bpt
  -- where bpt.saldo<>bpt.totalopenbalance
  LOOP
    RAISE NOTICE 'BP % should be fixed  ', bptofix.name;
    INSERT INTO temp_cartera
    SELECT
      carbp.legacy_cartera_id,
      carbp.fecha,
      duho_getsaldo_cartera_synced(carbp.legacy_cartera_id),
      0.00,
      duho_getsaldo_cartera_synced(carbp.legacy_cartera_id)
    FROM
      legacy_cartera carbp
    WHERE
      carbp.c_bpartner_id = bptofix.c_bpartner_id
      AND duho_getsaldo_cartera_synced(carbp.legacy_cartera_id) > 0;
    FOR cobrec IN
    SELECT
      *
    FROM
      legacy_cobro cob
    WHERE
      c_bpartner_id = bptofix.c_bpartner_id
      AND (cob.synced != 'Y'
        OR cob.synced IS NULL)
      AND cob.origen = 'native'
      AND cob.abono > 0
    ORDER BY
      operacion ASC LOOP
        SELECT
          *
        FROM
          temp_cartera
        WHERE
          saldovirtual > 0
          AND saldovirtual > cobrec.abono
        ORDER BY
          fecha ASC
        LIMIT 1 INTO candidate;
        IF (candidate.legacy_cartera_id IS NOT NULL) THEN
          UPDATE
            temp_cartera
          SET
            saldovirtual = saldovirtual - cobrec.abono
          WHERE
            legacy_cartera_id = candidate.legacy_cartera_id;
          UPDATE
            legacy_cobro
          SET
            id_cartera = candidate.legacy_cartera_id
          WHERE
            legacy_cobro_id = cobrec.legacy_cobro_id;
          RAISE NOTICE 'Cob: % redireccionando a % ', cobrec.legacy_cobro_id, candidate.legacy_cartera_id;
        END IF;
      END LOOP;
    TRUNCATE TABLE temp_cartera;
  END LOOP;
END;
$$;

