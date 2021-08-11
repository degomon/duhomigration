-- FUNCTION: adempiere.duho_balancecheck()

-- DROP FUNCTION adempiere.duho_balancecheck();

CREATE OR REPLACE FUNCTION adempiere.duho_balancecheck(
	)
    RETURNS void
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE PARALLEL UNSAFE
AS $BODY$
DECLARE 
 bptofix record;
BEGIN   
for bptofix in 
with bptocheck as (
	select bp.name, bp.c_bpartner_id,
(select coalesce(sum(montototal),0.00) from legacy_cartera carint where carint.c_bpartner_id = bp.c_bpartner_id )
-
(select coalesce(sum(abono),0.00) from legacy_cobro cobint where cobint.c_bpartner_id = bp.c_bpartner_id )
as saldo,
bp.totalopenbalance
from c_bpartner bp
where exists (select 1 from legacy_cartera lgc where lgc.c_bpartner_id = bp.c_bpartner_id)
	)
select * from bptocheck bpt
where bpt.saldo<>bpt.totalopenbalance loop
	-- raise notice 'BP % should be fixed with saldo: % ', bptofix.name, bptofix.saldo;
	update c_bpartner set totalopenbalance = bptofix.saldo, updated = now()
	where c_bpartner_id = bptofix.c_bpartner_id;
end loop;
END;
$BODY$;

ALTER FUNCTION adempiere.duho_balancecheck()
    OWNER TO adempiere;
