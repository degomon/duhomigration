SELECT
  bp.c_bpartner_id,
  bp.name,
  pay.documentno,
  pay.docstatus,
  pay.payamt,
  pay.datetrx,
(
    SELECT
      local_id
    FROM
      legacy_cobro
    WHERE
      local_id = pay.c_payment_id) AS cobroid,
(
    SELECT
      abono
    FROM
      legacy_cobro
    WHERE
      local_id = pay.c_payment_id) AS abono,
  pay.payamt -(
    SELECT
      abono
    FROM
      legacy_cobro
    WHERE
      local_id = pay.c_payment_id) AS diff
  -- ,	(select abono from legacy_cobro where operacion::date = pay.datetrx::date and c_bpartner_id = pay.c_bpartner_id) as candidate
FROM
  c_payment pay
  INNER JOIN c_bpartner bp ON pay.c_bpartner_id = bp.c_bpartner_id
WHERE
  pay.c_doctype_id = 1000050
  AND pay.payamt > 0
  AND pay.c_bpartner_id = 1001322
  -- and not exists (select 1 from legacy_cobro cob where cob.local_id = pay.c_payment_id)
ORDER BY
  bp.name,
  pay.datetrx ASC
