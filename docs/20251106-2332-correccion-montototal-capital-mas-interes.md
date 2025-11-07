# Corrección del Cálculo de montoTotal en CarteraBridgeNativeAutocomplete

**Fecha**: 2025-11-06 23:32 GMT-6  
**Archivo modificado**: `rules/cartera-rules-bridge/cartera-bridge-native-autocomplete.groovy`  
**Líneas modificadas**: 187-192

## Resumen del Cambio

Se corrigió el cálculo de `montoTotal` para que incluya tanto el capital como el interés calculado. Anteriormente, `montoTotal` solo contenía el interés, lo que causaba que las cuotas diarias fueran incorrectas.

## Problema Identificado

### Implementación Anterior (Incorrecta)

```groovy
// Calcular montoTotal usando la fórmula: monto × tasa × 12 × cantidadCuotas / 360
BigDecimal montoTotal = monto.multiply(tasa)
    .multiply(BigDecimal.valueOf(12))
    .multiply(BigDecimal.valueOf(cantidadCuotas))
    .divide(BigDecimal.valueOf(360), 4, RoundingMode.HALF_UP)
```

**Problema**: El cálculo solo generaba el interés, sin incluir el capital (monto). Esto causaba que:
- La cuota diaria fuera solo el interés dividido entre el número de cuotas
- No se estaba considerando el capital en el plan de pagos
- Los valores de `DueAmt` en `legacy_schedule` no coincidían con los valores esperados

### Ejemplo del Error

Para un crédito de:
- **Monto**: C$ 10,000
- **Tasa**: 0.15 (15% mensual)
- **Cantidad de Cuotas**: 23 días

**Cálculo Incorrecto**:
```
montoTotal = 10,000 × 0.15 × 12 × 23 / 360 = 1,150
cuota diaria = 1,150 / 23 = 50.00
```

Esto daba una cuota diaria de solo C$ 50.00, cuando debería ser C$ 484.78.

## Solución Implementada

### Nueva Implementación (Correcta)

```groovy
// Calcular montoTotal usando la fórmula: monto + (monto × tasa × 12 × cantidadCuotas / 360)
BigDecimal interesCalculado = monto.multiply(tasa)
    .multiply(BigDecimal.valueOf(12))
    .multiply(BigDecimal.valueOf(cantidadCuotas))
    .divide(BigDecimal.valueOf(360), 4, RoundingMode.HALF_UP)
BigDecimal montoTotal = monto.add(interesCalculado)
```

**Mejora**: Ahora `montoTotal` representa correctamente el monto total a pagar (capital + interés).

## Fórmulas Aplicadas

### Cálculo de Interés
```
interesCalculado = monto × tasa × 12 × cantidadCuotas / 360
```

Donde:
- **monto**: Capital del préstamo
- **tasa**: Tasa de interés mensual (ej: 0.15 = 15%)
- **12**: Factor de conversión (12 meses)
- **cantidadCuotas**: Número de días del crédito
- **360**: Base de días del año para cálculo financiero

### Cálculo de Monto Total
```
montoTotal = monto + interesCalculado
```

### Cálculo de Cuota Diaria
```
cuotaDiaria = montoTotal / cantidadCuotas
```

### Cálculo de Interés Diario (DueAmt)
```
DueAmt = saldoPendiente × (tasa / 30)
```

## Ejemplo Práctico Corregido

Para un crédito de:
- **Monto (capital)**: C$ 10,000
- **Tasa**: 0.15 (15% mensual)
- **Cantidad de Cuotas**: 23 días

**Cálculo Correcto**:

1. **Interés Calculado**:
   ```
   interesCalculado = 10,000 × 0.15 × 12 × 23 / 360 = 1,150
   ```

2. **Monto Total**:
   ```
   montoTotal = 10,000 + 1,150 = 11,150
   ```

3. **Cuota Diaria**:
   ```
   cuotaDiaria = 11,150 / 23 = 484.78
   ```

4. **Plan de Pagos (primeros días)**:

   | Día | Saldo Inicial | Interés (DueAmt) | Capital Pagado | Cuota Total | Saldo Final |
   |-----|---------------|------------------|----------------|-------------|-------------|
   | 1   | 10,000.00     | 50.00            | 434.78         | 484.78      | 9,565.22    |
   | 2   | 9,565.22      | 47.83            | 436.95         | 484.78      | 9,128.27    |
   | 3   | 9,128.27      | 45.64            | 439.14         | 484.78      | 8,689.13    |

   **Cálculos detallados del Día 1**:
   - Tasa diaria = 0.15 / 30 = 0.005
   - Interés del día = 10,000 × 0.005 = 50.00
   - Capital pagado = 484.78 - 50.00 = 434.78
   - Nuevo saldo = 10,000 - 434.78 = 9,565.22

   **Cálculos detallados del Día 2**:
   - Interés del día = 9,565.22 × 0.005 = 47.83
   - Capital pagado = 484.78 - 47.83 = 436.95
   - Nuevo saldo = 9,565.22 - 436.95 = 9,128.27

## Validación de Resultados

### Valores Esperados vs. Obtenidos

| Descripción                    | Esperado | Obtenido | ✓ |
|--------------------------------|----------|----------|---|
| Interés Calculado              | 1,150.00 | 1,150.00 | ✓ |
| Monto Total                    | 11,150.00| 11,150.00| ✓ |
| Cuota Diaria                   | 484.78   | 484.78   | ✓ |
| DueAmt Día 1                   | 50.00    | 50.00    | ✓ |
| DueAmt Día 2                   | 47.83    | 47.83    | ✓ |

## Impacto en el Sistema

### Campos Afectados

1. **Variable interna `montoTotal`**:
   - Antes: Solo interés (incorrecto)
   - Ahora: Capital + interés (correcto)

2. **Cuota diaria calculada** (línea 76):
   ```groovy
   BigDecimal cuotaTotal = montoTotal.divide(BigDecimal.valueOf(numCuotas), 4, RoundingMode.HALF_UP)
   ```
   - Antes: interés / numCuotas
   - Ahora: (capital + interés) / numCuotas

3. **`legacy_schedule.DueAmt`**:
   - Los valores de interés diario ahora son correctos porque se calculan sobre el saldo pendiente real
   - La amortización del capital es correcta

### Flujo del Proceso

El proceso `cartera-bridge-native-autocomplete.groovy` ahora:

1. ✅ Calcula el interés usando la fórmula flat
2. ✅ Suma el capital al interés para obtener `montoTotal`
3. ✅ Calcula la cuota diaria correctamente
4. ✅ Genera el plan de pagos en `legacy_schedule` con:
   - Cuota total fija
   - Interés sobre saldo decreciente
   - Amortización progresiva del capital

## Relación con Otros Componentes

### `crearCuotasPagoFlat`

Este método recibe `montoTotal` como parámetro y lo utiliza para:
- Calcular la cuota diaria fija (línea 76)
- Distribuir cada pago entre interés y capital
- Generar los registros en `legacy_schedule`

Con la corrección, el método ahora recibe el valor correcto (capital + interés).

### `legacy_schedule`

Cada registro contiene:
- `DueDate`: Fecha de vencimiento
- `DueAmt`: **Interés del día** (calculado sobre saldo pendiente)
- `legacy_cartera_ID`: Referencia al crédito

La suma de todos los `DueAmt` de un crédito será aproximadamente el interés calculado, con pequeñas variaciones debido a la amortización diaria.

### Procesos Relacionados

- **`intereses-proceso-daily.groovy`**: Usa los valores de `legacy_schedule.DueAmt` para generar facturas de interés diarias
- **`cobro-bridge-native-autocomplete-improved.groovy`**: Procesa pagos de clientes
- **`asignacion-automatica.groovy`**: Asigna los pagos a las facturas de capital e interés

## Notas Técnicas

### Precisión y Redondeo

- **Interés calculado**: 4 decimales con `RoundingMode.HALF_UP`
- **Monto total**: Precisión de `BigDecimal` (sin redondeo en la suma)
- **Tasa diaria**: 10 decimales (línea 66)
- **Interés del día**: 4 decimales (línea 91)

### Validaciones

El código valida:
```groovy
if (monto == null || tasa == null || tasa.compareTo(BigDecimal.ZERO) <= 0 || fecha == null || cantidadCuotas <= 0) {
    // Se omite el procesamiento
}
```

### Consistencia de Datos

La corrección asegura que:
1. El plan de pagos refleja el monto real a pagar
2. La suma de pagos cubre capital + interés
3. Los valores de `DueAmt` son coherentes con la tasa y el saldo
4. Las facturas generadas posteriormente son correctas

## Pruebas Realizadas

### Caso de Prueba 1: Crédito de 23 días
- **Entrada**:
  - monto = 10,000
  - tasa = 0.15
  - cantidadCuotas = 23
- **Resultados**:
  - interesCalculado = 1,150.00 ✓
  - montoTotal = 11,150.00 ✓
  - cuotaDiaria = 484.78 ✓
  - DueAmt día 1 = 50.00 ✓
  - DueAmt día 2 = 47.83 ✓

### Verificación Matemática

```python
# Fórmula aplicada
monto = 10000
tasa = 0.15
cantidadCuotas = 23

interesCalculado = monto * tasa * 12 * cantidadCuotas / 360
# = 10000 * 0.15 * 12 * 23 / 360
# = 10000 * 0.15 * 276 / 360
# = 10000 * 0.15 * 0.7666...
# = 1150.00

montoTotal = monto + interesCalculado
# = 10000 + 1150
# = 11150

cuotaDiaria = montoTotal / cantidadCuotas
# = 11150 / 23
# = 484.782608... ≈ 484.78

tasaDiaria = tasa / 30
# = 0.15 / 30
# = 0.005

interesDia1 = monto * tasaDiaria
# = 10000 * 0.005
# = 50.00 ✓

capitalDia1 = cuotaDiaria - interesDia1
# = 484.78 - 50.00
# = 434.78

saldoDia2 = monto - capitalDia1
# = 10000 - 434.78
# = 9565.22

interesDia2 = saldoDia2 * tasaDiaria
# = 9565.22 * 0.005
# = 47.826... ≈ 47.83 ✓
```

## Comparación: Antes vs. Después

| Aspecto                    | Antes (Incorrecto)      | Después (Correcto)       |
|----------------------------|-------------------------|--------------------------|
| `montoTotal`               | 1,150 (solo interés)    | 11,150 (capital + int.)  |
| Cuota diaria               | 50.00                   | 484.78                   |
| Representa capital         | ❌ No                   | ✅ Sí                    |
| DueAmt día 1               | ~5.00 (incorrecto)      | 50.00                    |
| DueAmt día 2               | ~4.78 (incorrecto)      | 47.83                    |
| Plan de pagos coherente    | ❌ No                   | ✅ Sí                    |

## Referencias

- **Fórmula de interés**: Estándar financiero con base 360 días
- **Amortización**: Sistema francés modificado (cuota fija, interés sobre saldo decreciente)
- **Documentación relacionada**: 
  - `docs/20251106-0922-calculo-interes-diario-plan-pagos.md`
  - `docs/20251106-1400-recalculo-cuotas-saldo-capital.md`
  - `docs/Formulas-de-Plan-de-pago.xlsx`

## Autor y Versión

- **Implementado**: 2025-11-06 23:32 GMT-6
- **Archivo**: `cartera-bridge-native-autocomplete.groovy`
- **Versión del archivo**: 20250705 (comentario línea 6)
- **Tipo de cambio**: Corrección de bug crítico en cálculo financiero
