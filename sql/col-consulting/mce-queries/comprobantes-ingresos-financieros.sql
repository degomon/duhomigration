select 
cob.operacion::date as fecha, 
sum(cob.abono) as bruto,
sum(round( (cob.abono / 1.15), 2)) as neto,
sum(round( ( (cob.abono / 1.15) / 0.13), 2)) as intereses,
sum(round( ((cob.abono / 1.15) / 1.02), 2)) as comision,
sum(round( ((cob.abono / 1.15) / 0.15), 2)) as ingresosfinancieros,
-- cp.startdate,
-- cp.enddate,
TO_CHAR(cp.enddate, 'YYYYMM')::int as cobmes_int,
cp.name, 
-- car.fecha as fechacredito,
-- TO_CHAR(car.fecha, 'YYYYMM') as credmes,
-- (TO_CHAR(cp.enddate, 'YYYYMM')::int - TO_CHAR(car.fecha, 'YYYYMM')::int) as intdiff,
case 
    when (TO_CHAR(cp.enddate, 'YYYYMM')::int 
    - TO_CHAR(car.fecha, 'YYYYMM')::int) >= 4 
            then TO_CHAR(cp.enddate - interval '4 month', 'YYYYMM')
    else TO_CHAR(car.fecha, 'YYYYMM') end as credmes_str,
case 
    when (TO_CHAR(cp.enddate, 'YYYYMM')::int 
    - TO_CHAR(car.fecha, 'YYYYMM')::int) >= 4 
            then TO_CHAR(cp.enddate, 'YYYYMM')::int - 4
    else TO_CHAR(car.fecha , 'YYYYMM')::int end as credmes_int
-- TO_CHAR(car.fecha, 'YYYYMM')::int as credmes_int 
from c_period cp
inner join legacy_cobro cob
on cob.operacion::date between cp.startdate::date and cp.enddate::date
inner join c_bpartner bp on cob.c_bpartner_id = bp.c_bpartner_id
inner join legacy_cartera car on cob.id_cartera = car.legacy_cartera_id
where cp.c_period_id = 1000034
-- and bp.c_greeting_id is not null
group by cob.operacion::date,
cp.startdate,
cp.enddate,
cp.name,
TO_CHAR(car.fecha, 'YYYYMM'),
TO_CHAR(car.fecha, 'YYYYMM')::int,
car.fecha
order by cob.operacion::date asc, credmes_int asc
