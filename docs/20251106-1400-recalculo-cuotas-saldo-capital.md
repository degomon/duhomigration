# Recálculo de Cuotas Basado en Saldo Capital en InteresProcesoDaily

**Fecha**: 2025-11-06 14:00 GMT-6  
**Archivo modificado**: `rules/intereses-rules/interes-proceso-daily.groovy`  
**Versión**: 20251106

## Resumen del Cambio

Se modificó el proceso `InteresProcesoDaily` para recalcular dinámicamente las cuotas pendientes basándose en el **saldo capital real** antes de generar las facturas de interés diarias. Este cambio garantiza que el cálculo de intereses refleje el capital realmente pendiente de pago, tomando en cuenta los abonos realizados.

## Problema Identificado

### Implementación Anterior

El proceso simplemente tomaba las cuotas de la tabla `legacy_schedule` y generaba facturas de interés (`c_invoice`) directamente, sin verificar si el saldo capital había cambiado por pagos anticipados o abonos realizados.

**Limitaciones**:
- No consideraba el saldo capital real al momento de generar la factura
- Las cuotas de interés no se ajustaban según el capital realmente pendiente
- No reflejaba pagos o abonos que redujeran el capital del préstamo

## Solución Implementada

### Nuevo Flujo del Proceso

El proceso ahora se ejecuta en **dos fases**:

#### FASE 1: Recálculo de Cuotas Basadas en Saldo Capital

1. **Obtener cuotas pendientes del día** usando `getPendingScheduleIDs()`
2. **Identificar carteras únicas** que tienen cuotas pendientes
3. **Para cada cartera**:
   - Obtener el `legacy_cartera_id` desde `legacy_schedule`
   - Obtener la factura de capital desde `legacy_cartera.local_id`
   - Calcular el saldo capital usando `invoiceopentodate(capitalInvoiceID, NULL, fecha)`
   - Recalcular todas las cuotas pendientes usando el nuevo método `recalcularCuotasDesdeCapital()`

#### FASE 2: Generación de Facturas

1. **Volver a obtener** las cuotas pendientes (ahora con montos actualizados)
2. **Generar facturas** de interés con los valores recalculados

### Nuevos Métodos Implementados

#### 1. `getSaldoCapital(int capitalInvoiceID, Timestamp processDate)`

Obtiene el saldo capital pendiente de una cartera a una fecha específica.

**Parámetros**:
- `capitalInvoiceID`: ID de la factura de capital (`legacy_cartera.local_id`)
- `processDate`: Fecha a la que se calcula el saldo

**Retorna**: `BigDecimal` con el saldo capital pendiente

**Implementación**:
```groovy
String sql = "SELECT invoiceopentodate(?, NULL, ?)";
BigDecimal saldo = DB.getSQLValueBD(A_TrxName, sql, capitalInvoiceID, processDate);
```

**Función de Base de Datos**: 
- Utiliza `invoiceopentodate()` de PostgreSQL/iDempiere
- Calcula el saldo de una factura considerando todos los pagos y asignaciones hasta la fecha especificada

#### 2. `recalcularCuotasDesdeCapital(int carteraID, BigDecimal saldoCapital, Timestamp processDate)`

Recalcula todas las cuotas pendientes de una cartera basándose en el saldo capital actual.

**Parámetros**:
- `carteraID`: ID de la cartera (`legacy_cartera_id`)
- `saldoCapital`: Saldo de capital pendiente a la fecha
- `processDate`: Fecha del proceso

**Retorna**: `boolean` indicando éxito o falla

**Lógica de Recálculo**:

1. **Obtener datos de la cartera**:
   ```groovy
   BigDecimal tasaMensual = cartera.get_Value('tasa')
   BigDecimal tasaDiaria = tasaMensual / 30
   ```

2. **Obtener cuotas pendientes** ordenadas por fecha de vencimiento:
   ```sql
   SELECT legacy_schedule_id, DueDate, DueAmt 
   FROM legacy_schedule 
   WHERE legacy_cartera_id = ? 
     AND (Processed IS NULL OR Processed = 'N') 
     AND IsActive = 'Y'
     AND DueDate >= ?
   ORDER BY DueDate ASC
   ```

3. **Calcular cuota fija** basada en el saldo remanente:
   ```groovy
   BigDecimal interesEstimado = saldoCapital * tasaDiaria * numCuotas
   BigDecimal montoTotalEstimado = saldoCapital + interesEstimado
   BigDecimal cuotaTotal = montoTotalEstimado / numCuotas
   ```

4. **Recalcular cada cuota** con la fórmula de interés diario:
   ```groovy
   for (cada cuota pendiente) {
       interesDelDia = saldoPendiente * tasaDiaria
       capitalDelDia = cuotaTotal - interesDelDia
       saldoPendiente = saldoPendiente - capitalDelDia
       
       // Actualizar DueAmt en base de datos
       UPDATE legacy_schedule SET DueAmt = interesDelDia 
       WHERE legacy_schedule_id = ?
   }
   ```

**Características**:
- Similar al método `crearCuotasPagoFlat` de `cartera-bridge-native-autocomplete.groovy`
- Calcula interés sobre saldo decreciente (amortización)
- Actualiza `legacy_schedule.DueAmt` con el nuevo interés calculado
- Mantiene cuota total fija, pero varía la distribución entre capital e interés

## Relación entre Tablas

```
legacy_cartera
├── legacy_cartera_id (PK)
├── local_id → c_invoice_id (Factura de Capital)
└── tasa (Tasa mensual de interés)

legacy_schedule
├── legacy_schedule_id (PK)
├── legacy_cartera_id (FK → legacy_cartera)
├── DueDate (Fecha de vencimiento)
├── DueAmt (Monto de interés - SE RECALCULA)
└── Processed (Indica si ya se generó factura)

c_invoice (Factura de Capital)
└── Pagos/Asignaciones → Afectan el saldo calculado por invoiceopentodate()
```

## Fórmula de Cálculo

### Saldo Capital
```
saldoCapital = invoiceopentodate(capitalInvoiceID, NULL, fecha)
```

Esta función retorna el saldo pendiente de la factura de capital considerando:
- Monto original de la factura
- Pagos aplicados hasta la fecha
- Asignaciones de crédito/débito
- Ajustes contables

### Interés Diario
```
interesDelDia = saldoPendiente × tasaDiaria
donde:
  tasaDiaria = tasaMensual / 30
  saldoPendiente = saldo capital al inicio del día
```

### Distribución de Cuota
```
cuotaTotal = (saldoCapital + interesEstimado) / numCuotas
capitalDelDia = cuotaTotal - interesDelDia
nuevoSaldo = saldoPendiente - capitalDelDia
```

## Ejemplo Práctico

**Escenario**: Préstamo de C$ 6,000 al 15% mensual, plazo 30 días

**Sin abonos extras**:
- Saldo capital inicial: C$ 6,000
- Cuotas restantes: 30
- Primera cuota: interés C$ 30.00 (6,000 × 0.15 / 30)

**Con abono de C$ 2,000 el día 10**:
- Saldo capital al día 11: C$ 4,000
- Cuotas restantes: 20
- Se recalculan las 20 cuotas basadas en C$ 4,000
- Primera cuota recalculada: interés C$ 20.00 (4,000 × 0.15 / 30)

## Campos Modificados

### Campo Recalculado
- `legacy_schedule.DueAmt`: Se actualiza con el interés recalculado basado en saldo capital

### Campos Utilizados para Cálculo
- `legacy_cartera.local_id`: ID de factura de capital
- `legacy_cartera.tasa`: Tasa mensual de interés
- `legacy_schedule.legacy_cartera_id`: Relación con la cartera
- `legacy_schedule.DueDate`: Fecha de vencimiento
- `legacy_schedule.Processed`: Estado de procesamiento

### Nuevo Campo de Seguimiento
- `@Field int recalculatedCount`: Contador de carteras recalculadas

## Mensajes de Log

El proceso genera los siguientes mensajes informativos:

```
📊 Iniciando recálculo de cuotas basado en saldo capital...
📋 Se encontraron X carteras únicas para recalcular.
💰 Cartera ID X: Factura capital ID Y, Saldo: Z
♻️ Recalculando N cuotas para cartera ID X. Saldo capital: Y, Tasa diaria: Z
✅ Recalculadas N cuotas para cartera ID X.
✅ Recálculo completado. Carteras recalculadas: X
🔄 Obteniendo cuotas actualizadas para generar facturas...
```

## Manejo de Casos Especiales

### Cartera sin Factura de Capital
```groovy
if (capitalInvoiceID <= 0) {
    logProcess("⚠️ ADVERTENCIA: Cartera ID sin factura de capital");
    continue; // Se omite el recálculo
}
```

### Saldo Capital Cero o Negativo
```groovy
if (saldoCapital.compareTo(BigDecimal.ZERO) <= 0) {
    logProcess("ℹ️ Cartera sin saldo capital pendiente. No se recalcula.");
}
```

### Sin Cuotas Pendientes
```groovy
if (cuotasPendientes.isEmpty()) {
    logProcess("ℹ️ No hay cuotas pendientes para recalcular");
    return true;
}
```

### Interés Excede Cuota Total
```groovy
if (capitalDelDia < 0) {
    interesDelDia = cuotaTotal;
    capitalDelDia = BigDecimal.ZERO;
}
```

## Resultado Final del Proceso

El mensaje final incluye tres métricas:

```
Proceso finalizado. Carteras recalculadas: X, Facturas creadas: Y, Omitidas: Z
```

- **Carteras recalculadas**: Número de carteras que tuvieron recálculo de cuotas
- **Facturas creadas**: Número de facturas de interés generadas exitosamente
- **Omitidas**: Número de cuotas que no se procesaron (ya facturadas, errores, etc.)

## Impacto y Consideraciones

### Ventajas
1. **Precisión**: El interés refleja el capital realmente pendiente
2. **Flexibilidad**: Permite pagos anticipados sin generar interés sobre capital ya pagado
3. **Transparencia**: Los clientes pagan interés solo sobre el saldo que deben
4. **Auditoría**: Los logs muestran claramente qué se recalculó y por qué

### Precauciones
1. El recálculo se ejecuta en **cada ejecución** del proceso diario
2. Solo recalcula cuotas **pendientes** (Processed = 'N' o NULL)
3. Solo considera cuotas con `DueDate >= processDate`
4. Requiere que `legacy_cartera.local_id` esté correctamente configurado
5. Depende de que la función `invoiceopentodate()` esté disponible en la base de datos

### Performance
- El proceso ahora ejecuta dos consultas por cartera única en lugar de una
- Se agrega una llamada a `invoiceopentodate()` por cada cartera
- El tiempo de ejecución aumenta proporcionalmente al número de carteras únicas con cuotas pendientes
- Se recomienda monitorear el tiempo de ejecución con muchas carteras activas

## Notas Técnicas

1. **Precisión Decimal**: 
   - Tasa diaria: 10 decimales
   - Interés del día: 4 decimales
   - Redondeo: `RoundingMode.HALF_UP`

2. **Transacciones**:
   - Todas las operaciones usan `A_TrxName` para consistencia
   - Los UPDATE se ejecutan con `DB.executeUpdate()`

3. **Orden de Procesamiento**:
   - Cuotas pendientes se ordenan por `DueDate ASC`
   - Esto asegura que el recálculo sea progresivo y consistente

4. **Compatibilidad**:
   - Compatible con PostgreSQL (función `invoiceopentodate()`)
   - El código ya manejaba Oracle/PostgreSQL en otras áreas

## Referencias

- **Función de base de datos**: `sql/db-structure/idempiere-db-structure-20251001.sql` líneas 5547-5642
- **Método de referencia**: `crearCuotasPagoFlat` en `rules/cartera-rules-bridge/cartera-bridge-native-autocomplete.groovy`
- **Documentación relacionada**: `docs/20251106-0922-calculo-interes-diario-plan-pagos.md`

## Ejemplos de Uso

### Ejecución Manual con Fecha Específica
El proceso acepta un parámetro opcional `fecha` para procesar cuotas de una fecha específica:
```
Parámetro: fecha = 2025-11-15
```

### Ejecución Automática Diaria
Sin parámetros, el proceso usa la fecha actual del sistema:
```
Fecha obtenida: now()::date (PostgreSQL) o TRUNC(SysDate) (Oracle)
```

## Próximos Pasos y Mejoras Futuras

1. **Métricas adicionales**: Agregar suma total de interés recalculado vs. original
2. **Histórico**: Considerar guardar el interés original antes del recálculo
3. **Optimización**: Evaluar recálculo solo cuando hay pagos nuevos
4. **Validación**: Agregar checks para detectar inconsistencias en el saldo
5. **Reportes**: Crear reporte de diferencias pre/post recálculo

## Autor y Fecha

- **Implementado**: 2025-11-06
- **Versión del archivo**: 20251106
- **Changelog**: Líneas 4-5 del archivo `interes-proceso-daily.groovy`
