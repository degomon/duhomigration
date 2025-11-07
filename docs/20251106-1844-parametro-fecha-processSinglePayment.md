# Modificación del Método processSinglePayment para Recibir Parámetro de Fecha

**Fecha:** 2025-11-06 18:44 GMT-6  
**Archivo:** `rules/asignacion-rules/asignacion-automatica.groovy`  
**Versión:** 20251107-1

## Resumen

Se modificó el método `processSinglePayment` para aceptar un parámetro de fecha opcional que se utiliza para establecer tanto `allocHdr.setDateAcct` como `allocHdr.setDateTrx` en el documento de asignación (Allocation).

## Problema

El método `processSinglePayment` estaba utilizando directamente la variable global `g_Today` para establecer las fechas de contabilidad (`DateAcct`) y transacción (`DateTrx`) del documento de asignación. Esto limitaba la flexibilidad del método y hacía que las fechas de asignación dependieran siempre de la variable global sin posibilidad de especificar una fecha diferente cuando fuera necesario.

## Solución Implementada

### Cambios Técnicos

#### 1. Modificación de la Firma del Método

**Antes:**
```groovy
boolean processSinglePayment(MPayment payment, int workNumber)
```

**Después:**
```groovy
boolean processSinglePayment(MPayment payment, int workNumber, Timestamp allocationDate = null)
```

El método ahora acepta un tercer parámetro opcional `allocationDate`:
- Si se proporciona, se utiliza esa fecha para las asignaciones
- Si es `null` (valor por defecto), se utiliza `g_Today` como antes
- Esto mantiene la compatibilidad hacia atrás

#### 2. Implementación de la Lógica de Fecha

**Código agregado:**
```groovy
// Usar la fecha proporcionada o g_Today como default
Timestamp dateForAllocation = allocationDate != null ? allocationDate : g_Today;

MAllocationHdr allocHdr = new MAllocationHdr(g_Ctx, 0, g_TrxName);
allocHdr.setAD_Org_ID(payment.getAD_Org_ID());
allocHdr.setDateAcct(dateForAllocation);
allocHdr.setDateTrx(dateForAllocation);
```

Se introduce una variable local `dateForAllocation` que determina la fecha a usar:
- Si `allocationDate` no es null, usa ese valor
- Si es null, usa `g_Today`

#### 3. Actualización del Llamado al Método

**Código en el bucle principal:**
```groovy
if (processSinglePayment(paymentInTrx, i + 1, g_Today)) {
```

Se actualiza la llamada al método para pasar explícitamente `g_Today` como el parámetro de fecha. Esto hace que el código sea más claro y explícito sobre qué fecha se está utilizando.

## Impacto

### Comportamiento Actual
El comportamiento del sistema **no cambia** para el uso actual:
- La fecha de asignación sigue siendo `g_Today` (la fecha de procesamiento)
- El flujo de trabajo existente continúa funcionando exactamente igual

### Beneficios
1. **Flexibilidad**: El método ahora puede aceptar una fecha específica cuando sea necesario
2. **Claridad**: El código es más explícito sobre qué fecha se está usando
3. **Mantenibilidad**: Facilita futuras modificaciones que requieran usar fechas diferentes
4. **Compatibilidad**: El parámetro opcional con valor por defecto mantiene la compatibilidad

## Validación

### Escenarios de Prueba

1. **Escenario 1: Uso normal (fecha no especificada)**
   - Input: `processSinglePayment(payment, 1)`
   - Comportamiento: Usa `g_Today` como antes
   - Resultado esperado: ✅ Las asignaciones se crean con la fecha del día

2. **Escenario 2: Uso normal (fecha especificada explícitamente)**
   - Input: `processSinglePayment(payment, 1, g_Today)`
   - Comportamiento: Usa explícitamente `g_Today`
   - Resultado esperado: ✅ Las asignaciones se crean con la fecha del día

3. **Escenario 3: Fecha personalizada**
   - Input: `processSinglePayment(payment, 1, customDate)`
   - Comportamiento: Usa `customDate`
   - Resultado esperado: ✅ Las asignaciones se crean con la fecha personalizada

### Verificación en Código

Para verificar que el cambio funciona correctamente, se deben revisar:
1. Los documentos `C_AllocationHdr` generados tienen `DateAcct` y `DateTrx` correctos
2. Las transacciones se completan exitosamente
3. No hay regresiones en el procesamiento normal de pagos

## Notas Técnicas

### Alcance Mínimo
Esta modificación es quirúrgica y enfocada:
- Solo afecta al método `processSinglePayment` y su llamada
- No modifica la lógica de negocio existente
- No afecta otros métodos o funcionalidades
- El número de líneas cambiadas es mínimo (4 líneas de cambio sustantivo)

### Parámetro Opcional en Groovy
Groovy soporta parámetros con valores por defecto directamente en la firma del método:
```groovy
Timestamp allocationDate = null
```
Esto permite llamar al método con 2 o 3 parámetros, manteniendo compatibilidad.

### Coherencia con el Sistema
Este cambio es coherente con el enfoque del sistema donde:
- `g_Today` es la fecha de procesamiento general
- Los documentos individuales pueden necesitar fechas específicas
- La flexibilidad en fechas permite manejar casos especiales sin modificar la lógica global

## Referencias

- Archivo modificado: `/rules/asignacion-rules/asignacion-automatica.groovy`
- Documentos relacionados: `CONTEXT.md` - Sección 4.4 (Asignación Automática de Pagos)
- Versión anterior: 20251106-2
- Versión actual: 20251107-1

## Líneas Modificadas

Total de líneas modificadas: 13 líneas
- Actualización de versión y changelog: 2 líneas
- Modificación de firma del método con documentación: 5 líneas
- Implementación de lógica de fecha: 4 líneas
- Actualización de llamada al método: 1 línea
- Cambios de espaciado/formato: 1 línea
