CREATE OR REPLACE FUNCTION adempiere.duho_change_cartera_data(
	carteraid numeric,
	nuevomonto numeric,
	nuevointeres numeric,
	nuevoplazo numeric
	)
	RETURNS integer
    LANGUAGE 'plpgsql'
	AS $BODY$
DECLARE
	nuevacuota numeric default 0.00;
	nuevomontototal numeric default 0.00;
	nuevovalorinteres numeric default 0.00;
	cartera record;
	invoice record;
BEGIN
	nuevovalorinteres := round(nuevomonto * nuevointeres,2);
	nuevomontototal := round( nuevomonto + (nuevomonto * nuevointeres), 2 );
	nuevacuota := round( nuevomontototal / nuevoplazo, 2);
	
	select * from legacy_cartera where legacy_cartera_id = carteraid into cartera;
	if(cartera.legacy_cartera_id is not null) then
		raise notice 'Cartera ::=> % | Monto: %, Cuota: %, Interés: %, Total: %', cartera, nuevomonto, nuevacuota, nuevovalorinteres, nuevomontototal;
		-- select * from legacy_cartera where legacy_cartera_id = 10347012
		update legacy_cartera 
			set dias_cre = nuevoplazo, cuota = nuevacuota, 
			monto = nuevomonto, valorinteres = nuevovalorinteres,
			montototal = nuevomontototal, tasa = nuevointeres 
			where legacy_cartera_id = carteraid;
		if(cartera.local_id is not null) then
			select * from c_invoice where c_invoice_id = cartera.local_id into invoice;
			if(invoice.c_invoice_id is not null) then
				-- select * from c_invoiceline where c_invoice_id = 1027383
				-- select * from c_invoice where c_invoice_id = 1027383
				update c_invoiceline set priceactual = nuevomonto, priceentered = nuevomonto, linenetamt = nuevomonto where c_charge_id = 1000028 and c_invoice_id = invoice.c_invoice_id;
				update c_invoiceline set priceactual = nuevovalorinteres, priceentered = nuevovalorinteres, linenetamt = nuevovalorinteres where c_charge_id = 1000029 and c_invoice_id = invoice.c_invoice_id;
				update c_invoice set totallines = nuevomontototal, grandtotal = nuevomontototal, posted = 'N' where c_invoice_id = invoice.c_invoice_id;
				
				-- Actualizar desembolso
				update c_payment 
					set payamt = nuevomonto,
					minilog = coalesce(minilog,'') || '\nUpdated with amt: ' + nuevomonto
					where c_payment_id = cartera.payment_id;  
			end if;
		end if;
		perform duho_updatesaldo_cartera(carteraid);
	end if;
	
END $BODY$;