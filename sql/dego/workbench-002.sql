SELECT DISTINCT
  bp.c_bpartner_id,
  bp.name, -- car.legacy_cartera_id,
  count(cob.legacy_cobro_id)
FROM
  legacy_cobro cob
  INNER JOIN legacy_cartera car ON cob.id_cartera = car.legacy_cartera_id
  INNER JOIN c_invoice inv ON car.local_id = inv.c_invoice_id
  INNER JOIN c_bpartner bp ON car.c_bpartner_id = bp.c_bpartner_id
WHERE (cob.synced != 'Y'
  OR cob.synced IS NULL)
AND cob.origen = 'native'
AND cob.abono > 0
AND duho_getsaldo_cartera_synced(car.legacy_cartera_id) < 0
GROUP BY
  bp.c_bpartner_id,
  bp.name,
  car.legacy_cartera_id
ORDER BY
  3 DESC
  --
  --
  /**** dsdsds sds*/
  -- select distinct c_bpartner_id  from c_payment where docstatus = 'DR'
  SELECT DISTINCT
    bp.c_bpartner_id,
    bp.name, -- car.legacy_cartera_id,
    count(cob.legacy_cobro_id)
  FROM
    legacy_cobro cob
    INNER JOIN legacy_cartera car ON cob.id_cartera = car.legacy_cartera_id
    INNER JOIN c_invoice inv ON car.local_id = inv.c_invoice_id
    INNER JOIN c_bpartner bp ON car.c_bpartner_id = bp.c_bpartner_id
  WHERE (cob.synced != 'Y'
    OR cob.synced IS NULL)
  AND cob.origen = 'native'
  AND cob.abono > 0
  AND inv.ispaid = 'Y'
GROUP BY
  bp.c_bpartner_id,
  bp.name,
  car.legacy_cartera_id
ORDER BY
  3 DESC
