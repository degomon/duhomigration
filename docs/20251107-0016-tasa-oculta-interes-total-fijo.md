# Implementación de Tasa Oculta para Interés Total Fijo en legacy_schedule

**Fecha**: 2025-11-07 00:16 (GMT-6)  
**Archivo modificado**: `rules/cartera-rules-bridge/cartera-bridge-native-autocomplete.groovy`  
**Métodos modificados**: `crearCuotasPagoFlat` (modificado), `calcularTasaOculta` (nuevo)

## Resumen del Cambio

Se implementó un método para calcular una "tasa diaria oculta" que garantiza que el interés total pagado en un plan de amortización sea exactamente el monto fijo deseado, utilizando el método de interés sobre saldo con cuotas niveladas.

## Problema Identificado

El sistema anterior calculaba la tasa diaria dividiendo la tasa mensual entre 30:
```groovy
BigDecimal tasaDiaria = tasaMensual.divide(BigDecimal.valueOf(30), 10, RoundingMode.HALF_UP)
```

Esta aproximación no garantizaba que el **interés total** pagado al final del período fuera exactamente el valor calculado por la fórmula:
```groovy
BigDecimal interesCalculado = monto.multiply(tasa)
    .multiply(BigDecimal.valueOf(12))
    .multiply(BigDecimal.valueOf(cantidadCuotas))
    .divide(BigDecimal.valueOf(360), 4, RoundingMode.HALF_UP)
```

## Solución Implementada

### Nuevo Método: `calcularTasaOculta`

Se agregó un método que calcula la tasa diaria exacta mediante búsqueda binaria:

```groovy
def calcularTasaOculta = { BigDecimal monto, BigDecimal interesTotal, int numCuotas ->
    // Búsqueda binaria de la tasa diaria que produce el interés total exacto
    // ...
    return tasaDiaria
}
```

**Algoritmo:**
1. Define un rango de búsqueda (0.001 - 0.020)
2. Para cada tasa candidata, simula el plan de amortización completo
3. Calcula el interés total que produciría esa tasa
4. Ajusta el rango de búsqueda hasta encontrar la tasa que produce exactamente el interés deseado
5. Converge cuando la diferencia es menor a 0.0001

**Parámetros:**
- `monto`: Capital del préstamo
- `interesTotal`: Interés total fijo deseado
- `numCuotas`: Número de días/cuotas
- **Retorna**: Tasa diaria oculta con precisión de 10 decimales

### Modificaciones a `crearCuotasPagoFlat`

El método ahora:
1. Calcula el interés total: `interesTotal = montoTotal - monto`
2. Obtiene la tasa oculta: `tasaDiariaOculta = calcularTasaOculta(monto, interesTotal, numCuotas)`
3. Usa esta tasa para calcular el interés diario: `interesDelDia = saldoPendiente × tasaDiariaOculta`
4. Aplica un ajuste especial en el último día para pagar todo el saldo restante

## Ejemplo de Cálculo

**Datos de Entrada:**
- Monto (Capital): 10,000
- Interés Total Fijo: 1,150
- Número de Cuotas: 23 días

**Resultado:**
- Tasa Diaria Oculta calculada: **0.0092699368**
- Cuota Nivelada (días 1-22): 484.7826

**Tabla de Amortización (primeros 5 días y último):**

| Día | Saldo Inicial | DueAmt (Int) | Abono Capital | Cuota    | Saldo Final |
|-----|---------------|--------------|---------------|----------|-------------|
| 1   | 10,000.00     | 92.6994      | 392.0832      | 484.7826 | 9,607.92    |
| 2   | 9,607.92      | 89.0648      | 395.7178      | 484.7826 | 9,212.20    |
| 3   | 9,212.20      | 85.3965      | 399.3861      | 484.7826 | 8,812.81    |
| 4   | 8,812.81      | 81.6942      | 403.0884      | 484.7826 | 8,409.72    |
| 5   | 8,409.72      | 77.9576      | 406.8250      | 484.7826 | 8,002.90    |
| ... | ...           | ...          | ...           | ...      | ...         |
| 23  | 480.33        | 4.4526       | 480.3302      | 484.7828 | 0.00        |

**Verificación:**
- ✓ Total Interés: 1,150.00 (exacto)
- ✓ Total Capital: 10,000.00 (exacto)
- ✓ Total Pagado: 11,150.00 (exacto)
- ✓ Saldo Final: 0.00 (exacto)

## Características Técnicas

### Precisión y Redondeo
- Tasa diaria oculta: 10 decimales (RoundingMode.HALF_UP)
- Interés del día (DueAmt): 4 decimales (RoundingMode.HALF_UP)
- Cuota total: 4 decimales (RoundingMode.HALF_UP)
- Tolerancia de convergencia: 0.0001

### Validaciones
- Valida que monto > 0, interesTotal > 0, numCuotas > 0
- Asegura que el interés diario no sea negativo
- Ajusta el capital si excede el saldo pendiente
- Ajuste especial en el último día para saldar completamente

### Convergencia
- Máximo 1000 iteraciones
- Típicamente converge en 20-30 iteraciones
- Si no converge, retorna la mejor aproximación

## Ventajas de Este Enfoque

1. **Precisión Exacta**: El interés total pagado es exactamente el valor calculado, sin diferencias por redondeo
2. **Flexible**: Funciona para cualquier combinación de monto, interés y número de cuotas
3. **Transparente**: El método de interés sobre saldo es más justo y comprensible
4. **Automático**: No requiere tablas precalculadas ni tasas manuales

## Impacto en el Sistema

### Campos Afectados
- `legacy_schedule.DueAmt`: Ahora calculado con la tasa oculta

### Datos Utilizados
- `legacy_cartera.monto`: Capital del préstamo (requerido)
- `legacy_cartera.dias_cre`: Número de cuotas (requerido)
- Parámetro `montoTotal`: Monto total del préstamo (capital + interés)

### Compatibilidad
- Reemplaza el cálculo anterior basado en `tasa mensual / 30`
- No requiere el campo `tasa` de la tabla `legacy_cartera`
- Mantiene la estructura de `legacy_schedule` sin cambios

## Caso de Uso Real

Para un préstamo típico de 10,000 a 23 días con interés de 1,150:

```groovy
// Antes (aproximado):
tasaDiaria = 0.15 / 30 = 0.005
// Interés total resultante: ~900 (no cumple el objetivo de 1,150)

// Ahora (exacto):
tasaDiariaOculta = calcularTasaOculta(10000, 1150, 23)
// = 0.0092699368
// Interés total resultante: 1,150.00 (exacto)
```

## Notas Técnicas

1. **Método de Amortización**: Interés sobre saldo decreciente con cuotas niveladas
2. **Fórmula de Interés Diario**: `DueAmt = Saldo × Tasa Oculta`
3. **Ajuste del Último Día**: Se paga todo el saldo restante como capital
4. **Domingos**: Se saltan automáticamente (llamada a `esDomingo()`)

## Referencias

- Test de validación: `/tmp/TestHiddenRate.java`
- Documentación previa: `docs/20251106-0922-calculo-interes-diario-plan-pagos.md`
- Fórmulas de referencia: `docs/Formulas-de-Plan-de-pago.xlsx`
