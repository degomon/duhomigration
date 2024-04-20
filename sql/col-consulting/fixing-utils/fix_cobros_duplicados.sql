DO $$
DECLARE 
    -- declare a record type to hold the result of a cursor
    payment record;
BEGIN

for payment in select pay.c_payment_id, pay.payamt
from c_payment pay
where pay.C_DocType_ID=1000050
and not exists (
	select local_id from legacy_cobro where local_id = pay.c_payment_id
) loop
    update c_payment set payamt = 0.00 where c_payment_id = payment.c_payment_id;
    update C_AllocationLine set amount = 0.00 where c_payment_id = payment.c_payment_id;
    raise notice 'Payment % has been set to 0.00', payment.c_payment_id;
end loop;
END $$


--- select * from C_AllocationLine
 -- where  C_AllocationLine_ID=1000628