DO $$
DECLARE
  -- declare a record type to hold the result of a cursor
  payment record;
BEGIN
  FOR payment IN
  SELECT
    pay.c_payment_id,
    pay.payamt,
    pay.datetrx
  FROM
    c_payment pay
  WHERE
    pay.C_DocType_ID = 1000050
    AND pay.payamt != 0
    AND NOT EXISTS (
      SELECT
        local_id
      FROM
        legacy_cobro
      WHERE
        local_id = pay.c_payment_id)
      LOOP
        UPDATE
          c_payment
        SET
          payamt = 0.00
        WHERE
          c_payment_id = payment.c_payment_id;
        UPDATE
          C_AllocationLine
        SET
          amount = 0.00
        WHERE
          c_payment_id = payment.c_payment_id;
        RAISE NOTICE 'Payment % - Date % has been set to 0.00', payment.c_payment_id, payment.datetrx;
      END LOOP;
END
$$
--- select * from C_AllocationLine
-- where  C_AllocationLine_ID=1000628
