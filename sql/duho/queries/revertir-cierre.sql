-- DROP FUNCTION duho_revertir_cierre(numeric,timestamp without time zone)
CREATE OR REPLACE FUNCTION adempiere.duho_revertir_cierre(
	orgid numeric,
	fecha timestamp without time zone
	)
	RETURNS INTEGER
    LANGUAGE 'plpgsql'
	AS $BODY$
BEGIN
update c_payment
set payamt = 0.00::numeric
where c_payment_id in
(select pay.c_payment_id 
 from c_payment pay
inner join c_bankaccount bac on pay.c_bankaccount_id = bac.c_bankaccount_id
inner join c_doctype dt on pay.c_doctype_id = dt.c_doctype_id
 where pay.description ~* 'cierre'
and pay.dateacct::date >= fecha::date
and pay.ad_org_id = orgid
and pay.payamt > 0
and pay.c_charge_id = 1000034 -- NO TOCAR ESTE CARGO, cambiá arriba
 );
 return 1;
END $BODY$;
-- select duho_revertir_cierre(1000019, '20220105'::timestamp without time zone) from c_year where c_year_id = -1
-- update dualint set dummy = duho_revertir_cierre(1000019, '20220103'::timestamp without time zone)
-- update dualint set dummy = 0