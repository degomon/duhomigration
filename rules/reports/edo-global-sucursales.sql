/**
@author: dgonzalez
Reporte para conocer el Estado Global de las sucursales
20210816 -> reconstruimos para poder Cajas de Cobradores
**/
select org.ad_org_id,
org.name as sucursal,
bac.value as codcaja,
bac.name as nombrecaja,
coalesce( sum (pay.payamt)
FILTER (where pay.dateacct::date < '20210908'::date and dt.docbasetype = 'ARR'),0.00 )
-
coalesce( sum (pay.payamt)
FILTER (where pay.dateacct::date < '20210908'::date and dt.docbasetype = 'APP'), 0.00 ) as inicial,

coalesce( sum (pay.payamt)
FILTER (where pay.dateacct::date between '20210908'::date and '20210908'::date
and cht.description ~* 'transfer'
and dt.docbasetype = 'ARR'), 0.00) as ingresos_transfer,

coalesce( sum (pay.payamt)
FILTER (where pay.dateacct::date between '20210908'::date and '20210908'::date
and (cht.description ~* 'inout' or pay.C_DocType_ID=1000050) -- Cargo para Recibos de Cliente
and dt.docbasetype = 'ARR'), 0.00) as cobros,

coalesce( sum (pay.payamt)
FILTER (where pay.dateacct::date between '20210908'::date and '20210908'::date
and cht.description ~* 'transfer'
and dt.docbasetype = 'APP'), 0.00) as egresos_transfer,

coalesce( sum (pay.payamt)
FILTER (where pay.dateacct::date between '20210908'::date and '20210908'::date
and ( pay.c_doctype_id = 1000009 and pay.c_charge_id not in (  1000034 ) )
and dt.docbasetype = 'APP'), 0.00) as gastos,

coalesce( sum (pay.payamt)
FILTER (where pay.dateacct::date between '20210908'::date and '20210908'::date
and pay.c_doctype_id = 1000049
and dt.docbasetype = 'APP'), 0.00) as desembolsos,

coalesce( sum (pay.payamt)
FILTER (where pay.dateacct::date <= '20210908'::date and dt.docbasetype = 'ARR'),0.00 )
-
coalesce( sum (pay.payamt)
FILTER (where pay.dateacct::date <= '20210908'::date and dt.docbasetype = 'APP'), 0.00 ) as final


from ad_org org
inner join c_payment pay on org.ad_org_id = pay.ad_org_id
inner join c_doctype dt on pay.c_doctype_id = dt.c_doctype_id
inner join c_bankaccount bac on pay.c_bankaccount_id = bac.c_bankaccount_id
left join c_charge ch on pay.c_charge_id = ch.c_charge_id
left join c_chargetype cht on ch.c_chargetype_id = cht.c_chargetype_id
where org.ad_client_id = 1000000
and pay.docstatus in ('CO', 'CL')
and pay.dateacct::date <= '20210908'::date
and bac.c_bankaccount_id > 1000000
group by bac.value, bac.name, org.ad_org_id, org.name
order by org.name, bac.value