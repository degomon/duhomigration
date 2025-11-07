# Filtro de Fecha Opcional en getUnallocatedPayments

**Fecha**: 2025-11-07 04:24 (GMT-6)  
**Archivo**: `rules/asignacion-rules/asignacion-automatica.groovy`  
**Versión**: 20251107-2

## Resumen

Se agregó un parámetro opcional de fecha al método `getUnallocatedPayments` para permitir filtrar pagos por su campo `datetrx` cuando se proporciona el parámetro de fecha al proceso.

## Problema

El método `getUnallocatedPayments` no tenía capacidad para filtrar pagos por fecha. Cuando el usuario proporcionaba un parámetro de fecha al proceso de asignación automática, todos los pagos no asignados eran procesados sin importar su fecha de transacción.

## Solución Implementada

### Cambios en el Método `getUnallocatedPayments`

1. **Nueva Firma del Método**:
   ```groovy
   List<MPayment> getUnallocatedPayments(Timestamp filterDate = null)
   ```
   - Se agregó el parámetro opcional `filterDate` con valor por defecto `null`
   - Cuando es `null`, el comportamiento es idéntico a la versión anterior

2. **Filtro SQL Condicional**:
   ```groovy
   if (filterDate != null) {
       sql += "            AND pay.datetrx::date = ?::date\n";
   }
   ```
   - Se agrega una condición adicional al WHERE cuando `filterDate` no es nulo
   - Filtra pagos donde `pay.datetrx` coincide con la fecha proporcionada
   - Usa casting de PostgreSQL (`::date`) para comparar solo la porción de fecha

3. **Parámetro PreparedStatement**:
   ```groovy
   if (filterDate != null) {
       pstmt.setTimestamp(1, filterDate);
   }
   ```
   - Se establece el parámetro en el PreparedStatement de forma segura

### Cambios en el Bloque Principal

Se actualizó la llamada al método para pasar el parámetro `fechaParam`:
```groovy
List<MPayment> payments = getUnallocatedPayments(fechaParam);
```

- `fechaParam` es `null` cuando el usuario no proporciona el parámetro "fecha" al proceso
- `fechaParam` contiene el Timestamp cuando el usuario proporciona el parámetro "fecha"

## Comportamiento

### Sin Parámetro de Fecha
Cuando el proceso se ejecuta sin el parámetro `fecha`, el método funciona exactamente como antes:
- Retorna todos los pagos no asignados que cumplen los criterios existentes
- No se aplica filtro por `datetrx`

### Con Parámetro de Fecha
Cuando el proceso se ejecuta con el parámetro `fecha` especificado:
- Retorna solo los pagos no asignados cuya fecha de transacción (`datetrx`) coincide con la fecha proporcionada
- Permite reprocesar o procesar pagos de fechas específicas

## Casos de Uso

1. **Procesamiento Regular (sin fecha)**: Procesar todos los pagos pendientes de asignación
2. **Reprocesamiento de Fecha Específica**: Ejecutar el proceso con una fecha pasada para asignar pagos que no fueron procesados correctamente
3. **Procesamiento de Cierre**: Procesar solo los pagos de un día específico para operaciones de cierre contable

## Compatibilidad

- **Backward Compatible**: El cambio es completamente compatible con el código existente
- **Sin Efectos Secundarios**: Cuando no se proporciona fecha, el comportamiento es idéntico a la versión anterior
- **SQL Seguro**: Usa PreparedStatement con parámetros para prevenir SQL injection

## Pruebas Realizadas

- ✅ Revisión de código automatizada
- ✅ Verificación de sintaxis Groovy
- ✅ Análisis de seguridad CodeQL (sin vulnerabilidades)
- ✅ Validación de lógica de negocio

## Notas Técnicas

- La comparación de fechas usa `::date` para ignorar la porción de tiempo
- El orden de los pagos sigue siendo por `DateAcct ASC`
- El límite de registros (`RECORD_LIMIT`) se aplica después del filtro de fecha
