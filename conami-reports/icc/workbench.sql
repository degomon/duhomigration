-- select count(*) from legacy_cartera
-- retrieve legacy_cartera table fields (postgres)
SELECT
  column_name,
  data_type
FROM
  information_schema.columns
WHERE
  table_name = 'legacy_cartera';

-- select count(*) from icc_credito
SELECT
  PASSWORD,
  name
FROM
  ad_user
LIMIT 100 -- legacy_cartera_meta
fecha_cancelacion -- select * from legacy_cartera
-- iterate for each result of legacy_cartera and print
-- value of legacy_cartera_id field
CREATE OR REPLACE FUNCTION iIF(condition boolean,
-- IF condition
true_result anyelement,
-- THEN
false_result anyelement -- ELSE
)
  RETURNS anyelement
  AS $f$
  SELECT
    CASE WHEN condition THEN
      true_result
    ELSE
      false_result
    END
$f$
LANGUAGE SQL
IMMUTABLE;

3702 15



update ad_user set c_bpartner_id = 1041598 where ad_user_id = 1000014

select * from ad_user where name in ('pantasma', 'YEINER')

-- select * from ad_user order by ad_user_id asc limit 900