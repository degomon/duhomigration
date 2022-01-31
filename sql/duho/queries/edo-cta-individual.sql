select bp.c_bpartner_id, bp.value as codigo, bp.name as tercero,
'INI-DEB'::character varying as documentno,
'SALDO INICIAL (DÉBITOS)'::character varying as concepto,
( '20210101'::date - interval '1 day')::date as fecha,
sum(car.montototal) as debito,
0.00::numeric as credito,
now() as created
from c_bpartner bp
inner join legacy_cartera car on bp.c_bpartner_id = car.c_bpartner_id
where bp.c_bpartner_id = 1017751
and car.fecha::date < '20210101'::date
group by bp.c_bpartner_id, bp.value, bp.name
union
select bp.c_bpartner_id, bp.value as codigo, bp.name as tercero,
'INI-CRED'::character varying as documentno,
'SALDO INICIAL (CRÉDITOS) '::character varying as concepto,
( '20210101'::date - interval '1 day')::date as fecha,
0.00::numeric as debito, sum(cob.abono) as credito,
now() as created
from c_bpartner bp
inner join legacy_cobro cob on bp.c_bpartner_id = cob.c_bpartner_id
where bp.c_bpartner_id = 1017751
and cob.operacion::date < '20210101'::date
group by bp.c_bpartner_id, bp.value, bp.name

union

select bp.c_bpartner_id, bp.value as codigo, bp.name as tercero,
coalesce( (select documentno from c_invoice inv where inv.c_invoice_id = car.local_id), car.id_cartera::character varying) as documentno,
'Desembolso'::character varying as concepto,
car.fecha::date as fecha,
car.montototal as debito, 0.00::numeric as credito,
car.created as created
from c_bpartner bp
inner join legacy_cartera car on bp.c_bpartner_id = car.c_bpartner_id
where bp.c_bpartner_id = 1017751
and car.fecha::date between '20210101'::date and now()::date

union

select bp.c_bpartner_id, bp.value as codigo, bp.name as tercero,
coalesce( (select documentno from c_payment pay where pay.c_payment_id = local_id), id_cobro::character varying) as documentno,
'Cobro'::character varying as concepto,
cob.operacion::date as fecha,
0.00::numeric as debito, cob.abono as credito,
cob.created
from c_bpartner bp
inner join legacy_cobro cob on bp.c_bpartner_id = cob.c_bpartner_id
where bp.c_bpartner_id = 1017751
and cob.operacion::date between '20210101'::date and now()::date

order by fecha::date, created