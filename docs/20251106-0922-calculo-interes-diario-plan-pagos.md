# Modificación del Cálculo de Interés Diario en Plan de Pagos

**Fecha**: 2025-11-06  
**Archivo modificado**: `rules/cartera-rules-bridge/cartera-bridge-native-autocomplete.groovy`  
**Método modificado**: `crearCuotasPagoFlat`

## Resumen del Cambio

Se modificó el método `crearCuotasPagoFlat` para calcular el interés diario (`DueAmt`) basándose en el saldo pendiente de capital, en lugar de dividir el interés total equitativamente entre todas las cuotas.

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
BigDecimal tasa = cartera.get_Value('tasa') ?: BigDecimal.ZERO
BigDecimal monto = cartera.get_Value('monto')
BigDecimal tasaDiaria = tasa.divide(BigDecimal.valueOf(360), 10, RoundingMode.HALF_UP)
BigDecimal cuotaTotal = montoTotal.divide(BigDecimal.valueOf(numCuotas), 4, RoundingMode.HALF_UP)
BigDecimal saldoPendiente = monto

for (int i = 0; i < numCuotas; i++) {
    // Calcular interés del día basado en saldo pendiente
    BigDecimal interesDelDia = saldoPendiente.multiply(tasaDiaria).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
    
    // Capital pagado en esta cuota
    BigDecimal capitalDelDia = cuotaTotal.subtract(interesDelDia)
    
    // Actualizar saldo pendiente
    saldoPendiente = saldoPendiente.subtract(capitalDelDia)
    
    cuota.set_ValueOfColumn('DueAmt', interesDelDia)
}
```

## Fórmula Aplicada

Basada en la hoja "Plan de pago interes diario" del archivo `docs/Formulas-de-Plan-de-pago.xlsx`:

**Interés diario = Saldo pendiente × (Tasa anual / 360) / 100 × Días**

Donde:
- **Saldo pendiente**: Capital que aún se debe al inicio del período
- **Tasa anual**: Tasa de interés anual almacenada en `legacy_cartera.tasa` (ej: 180 para 180%)
- **Tasa diaria**: `Tasa anual / 360`
- **Días**: 1 (para pagos diarios)

## Ejemplo de Cálculo

Para un préstamo de C$ 10,000 a 180% anual por 6 días:

| Día | Saldo Inicial | Interés Diario | Capital Pagado | Cuota Total | Saldo Final |
|-----|---------------|----------------|----------------|-------------|-------------|
| 1   | 10,000.00     | 50.0000        | 1,666.67       | 1,716.67    | 8,333.33    |
| 2   | 8,333.33      | 41.6667        | 1,675.00       | 1,716.67    | 6,658.33    |
| 3   | 6,658.33      | 33.2917        | 1,683.38       | 1,716.67    | 4,974.95    |
| 4   | 4,974.95      | 24.8747        | 1,691.80       | 1,716.67    | 3,283.15    |
| 5   | 3,283.15      | 16.4158        | 1,700.25       | 1,716.67    | 1,582.90    |
| 6   | 1,582.90      | 7.9145         | 1,708.76       | 1,716.67    | (125.86)    |

**Observación**: El interés disminuye con cada pago porque se calcula sobre un saldo cada vez menor.

## Validación

Los cálculos fueron validados contra los valores en el archivo Excel `docs/Formulas-de-Plan-de-pago.xlsx`, hoja "Plan de pago interes diario", y coinciden exactamente.

## Impacto

### Campos Afectados
- `legacy_schedule.DueAmt`: Ahora contiene el interés calculado diariamente sobre saldo pendiente

### Datos Utilizados
- `legacy_cartera.tasa`: Tasa de interés anual (requerido)
- `legacy_cartera.monto`: Capital del préstamo
- `legacy_cartera.montototal`: Monto total a pagar (capital + interés)
- `legacy_cartera.dias_cre`: Número de cuotas/días

### Comportamiento con Datos Faltantes
- Si `tasa` es `null`, se usa `BigDecimal.ZERO` como valor por defecto (interés = 0)

## Notas Técnicas

1. **Precisión**: Se usa precisión de 10 decimales para `tasaDiaria` y 4 decimales para `interesDelDia`
2. **Redondeo**: Se aplica `RoundingMode.HALF_UP` en todas las divisiones
3. **Cuota Total Fija**: La cuota total permanece constante, pero la distribución entre interés y capital varía
4. **Saldo Pendiente**: Se actualiza después de cada cuota restando el capital pagado

## Referencias

- Archivo Excel: `docs/Formulas-de-Plan-de-pago.xlsx`
- Hoja de referencia: "Plan de pago interes diario"
- Fórmula de interés (Columna J): `=+L37*$C$7*H38` (Saldo anterior × Tasa diaria × Días)
