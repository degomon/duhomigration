DO $$
DECLARE
  allocrec record;
BEGIN
  FOR allocrec IN WITH dtw AS (
    SELECT
      c_payment_id,
      c_invoice_id,
      count(*) AS cta
    FROM
      c_allocationline
    GROUP BY
      c_payment_id,
      c_invoice_id
    HAVING
      count(*) > 1
    ORDER BY
      3 DESC
)
  SELECT
    pay.c_payment_id,
    pay.datetrx,
    pay.documentno,
    d.cta
  FROM
    c_payment pay
    INNER JOIN dtw d ON pay.c_payment_id = d.c_payment_id LOOP
    RAISE NOTICE 'should update data for %', allocrec.c_payment_id;
    WITH OrderedLines AS (
      SELECT
        C_AllocationLine_ID,
        ROW_NUMBER() OVER (ORDER BY C_AllocationLine_ID) AS rn
      FROM
        C_AllocationLine
      WHERE
        C_Payment_ID = allocrec.c_payment_id)
    UPDATE
      C_AllocationLine
    SET
      amount = 0.00
    WHERE
      C_AllocationLine_ID IN (
        SELECT
          C_AllocationLine_ID
        FROM
          OrderedLines
        WHERE
          rn > 1);
  END LOOP;
END
$$
-- select * from C_AllocationLine where C_AllocationLine_ID=1413328
-- update C_AllocationLine set amount = 0.00 where C_Payment_ID = 1413328
/*
WITH OrderedLines AS (
 SELECT 
 C_AllocationLine_ID,
 ROW_NUMBER() OVER (ORDER BY C_AllocationLine_ID) AS rn
 FROM 
 C_AllocationLine
 WHERE 
 C_Payment_ID = 1413328
)
UPDATE C_AllocationLine
SET amount = 0.00
WHERE 
 C_AllocationLine_ID IN (
 SELECT C_AllocationLine_ID 
 FROM OrderedLines
 WHERE rn > 1
 );
 */
