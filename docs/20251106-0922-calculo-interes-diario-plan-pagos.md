# Modificación del Cálculo de Interés Diario en Plan de Pagos

**Fecha**: 2025-11-06  
**Archivo modificado**: `rules/cartera-rules-bridge/cartera-bridge-native-autocomplete.groovy`  
**Método modificado**: `crearCuotasPagoFlat`

## Resumen del Cambio

Se modificó el método `crearCuotasPagoFlat` para calcular el interés diario (`DueAmt`) basándose en el saldo pendiente de capital, usando la **tasa mensual** almacenada en `legacy_cartera.tasa`.

## Aclaración Importante sobre la Tasa

**ACTUALIZACIÓN 2025-11-06**: La tasa almacenada en `legacy_cartera.tasa` es una **tasa mensual** en formato decimal:
- `tasa = 0.15` significa **15% mensual** (no anual)
- `tasa = 0.25` significa **25% mensual** (no anual)

Según el archivo `docs/ejemplo-legacy-cartera.csv`, los valores típicos son:
- 0.15 (15% mensual) - usado en 46 de 50 registros
- 0.25 (25% mensual) - usado en 4 de 50 registros

## Problema Identificado

### Implementación Anterior

```groovy
BigDecimal interesTotal = cartera.get_Value('valorinteres')
BigDecimal cuotaInteres = interesTotal.divide(BigDecimal.valueOf(numCuotas), 4, RoundingMode.HALF_UP)
cuota.set_ValueOfColumn('DueAmt', cuotaInteres)
```

El interés se dividía equitativamente: `cuotaInteres = interesTotal / numCuotas`

**Problema**: Este enfoque no refleja correctamente el interés sobre el saldo pendiente. En un plan de pagos real, el interés debe calcularse sobre el saldo que aún se debe, no de forma lineal.

## Solución Implementada

### Nueva Implementación

```groovy
BigDecimal tasaMensual = cartera.get_Value('tasa') ?: BigDecimal.ZERO
BigDecimal monto = cartera.get_Value('monto') ?: BigDecimal.ZERO
// La tasa almacenada es mensual (ej: 0.15 = 15% mensual)
// Para obtener tasa diaria: tasa mensual / 30
BigDecimal tasaDiaria = tasaMensual.divide(BigDecimal.valueOf(30), 10, RoundingMode.HALF_UP)
BigDecimal cuotaTotal = montoTotal.divide(BigDecimal.valueOf(numCuotas), 4, RoundingMode.HALF_UP)
BigDecimal saldoPendiente = monto

for (int i = 0; i < numCuotas; i++) {
    // Calcular interés del día basado en saldo pendiente
    BigDecimal interesDelDia = saldoPendiente.multiply(tasaDiaria)
    interesDelDia = interesDelDia.setScale(4, RoundingMode.HALF_UP)
    
    // Capital pagado en esta cuota
    BigDecimal capitalDelDia = cuotaTotal.subtract(interesDelDia)
    
    // Actualizar saldo pendiente
    saldoPendiente = saldoPendiente.subtract(capitalDelDia)
    
    cuota.set_ValueOfColumn('DueAmt', interesDelDia)
}
```

## Fórmula Aplicada

**Interés diario = Saldo pendiente × (Tasa mensual / 30)**

Donde:
- **Saldo pendiente**: Capital que aún se debe al inicio del período
- **Tasa mensual**: Tasa de interés mensual almacenada en `legacy_cartera.tasa` (ej: 0.15 para 15%)
- **Tasa diaria**: `Tasa mensual / 30`

## Ejemplo de Cálculo

Para un préstamo de C$ 6,000 al 15% mensual por 30 días:

| Día | Saldo Inicial | Interés Diario | Capital Pagado | Cuota Total | Saldo Final |
|-----|---------------|----------------|----------------|-------------|-------------|
| 1   | 6,000.00      | 30.0000        | 200.00         | 230.00      | 5,800.00    |
| 2   | 5,800.00      | 29.0000        | 201.00         | 230.00      | 5,599.00    |
| 3   | 5,599.00      | 27.9950        | 202.00         | 230.00      | 5,397.00    |
| 4   | 5,397.00      | 26.9850        | 203.02         | 230.00      | 5,193.98    |
| 5   | 5,193.98      | 25.9699        | 204.03         | 230.00      | 4,989.95    |

**Cálculo de la tasa diaria:**
- Tasa mensual: 0.15 (15%)
- Tasa diaria: 0.15 / 30 = 0.005 (0.5% diario)

**Primer pago:**
- Interés = 6,000 × 0.005 = 30.00
- Capital = 230.00 - 30.00 = 200.00
- Nuevo saldo = 6,000 - 200 = 5,800.00

**Observación**: El interés disminuye con cada pago porque se calcula sobre un saldo cada vez menor.

## Nota sobre el Interés Total

El campo `valorinteres` en `legacy_cartera` representa el interés calculado como una tasa flat sobre el monto original:
```
valorinteres = monto × tasa
```

Sin embargo, el **interés real acumulado** en el plan de pagos será diferente (menor) porque:
1. Se calcula sobre el saldo decreciente
2. Se amortiza capital con cada pago

Por ejemplo, para C$ 6,000 al 15% mensual por 30 días:
- Interés flat (valorinteres): 900.00
- Interés acumulado real: ~444.00 (con amortización diaria)

Esto es correcto y refleja el funcionamiento real de un préstamo con pagos diarios donde el saldo disminuye.

## Validación

Los cálculos han sido validados contra datos reales del archivo `docs/ejemplo-legacy-cartera.csv` que contiene 50 registros de `legacy_cartera` con tasas mensuales de 0.15 y 0.25.

## Impacto

### Campos Afectados
- `legacy_schedule.DueAmt`: Ahora contiene el interés calculado diariamente sobre saldo pendiente

### Datos Utilizados
- `legacy_cartera.tasa`: Tasa de interés mensual en formato decimal (requerido)
- `legacy_cartera.monto`: Capital del préstamo
- `legacy_cartera.montototal`: Monto total a pagar (capital + interés)
- `legacy_cartera.dias_cre`: Número de cuotas/días

### Comportamiento con Datos Faltantes
- Si `tasa` es `null`, se usa `BigDecimal.ZERO` como valor por defecto (interés = 0)
- Si `monto` es `null`, se usa `BigDecimal.ZERO` como valor por defecto

## Notas Técnicas

1. **Precisión**: Se usa precisión de 10 decimales para `tasaDiaria` y 4 decimales para `interesDelDia`
2. **Redondeo**: Se aplica `RoundingMode.HALF_UP` en todas las divisiones
3. **Cuota Total Fija**: La cuota total permanece constante, pero la distribución entre interés y capital varía
4. **Saldo Pendiente**: Se actualiza después de cada cuota restando el capital pagado
5. **Tasa Mensual**: La tasa se divide entre 30 (no 360) para obtener la tasa diaria

## Referencias

- Archivo de datos: `docs/ejemplo-legacy-cartera.csv`
- Archivo Excel: `docs/Formulas-de-Plan-de-pago.xlsx`
- Hoja de referencia: "Plan de pago interes diario"
