# Modificación del Proceso de Asignación Automática de Pagos

**Fecha:** 2025-11-05 16:58 UTC  
**Archivo:** `rules/asignacion-rules/asignacion-automatica.groovy`  
**Versión:** 20251105

## Resumen

Se modificó el proceso de asignación automática de pagos para implementar una lógica de priorización que asigna primero los pagos a las facturas de interés y luego a la factura principal.

## Problema

El proceso anterior de asignación automática (`asignacion-automatica.groovy`) asignaba los pagos de clientes (C_Payment) a las facturas pendientes (C_Invoice) en orden cronológico simple, sin distinguir entre:
- Facturas de interés (C_DocType_ID = 1000051)
- Facturas principales/capital (C_DocType_ID = 1000048)

## Solución Implementada

### Nueva Lógica de Priorización

El pago ahora se asigna siguiendo este orden de prioridad:

1. **Primero:** Facturas de interés (C_DocType_ID = 1000051) previas a la fecha del pago
   - Ordenadas de la más antigua a la más actual (DateInvoiced ASC)
   - Solo se consideran las facturas con fecha anterior al pago

2. **Segundo:** Factura principal (C_DocType_ID = 1000048)
   - Después de agotar todas las facturas de interés aplicables

### Cambios Técnicos

#### 1. Nuevas Constantes
```groovy
@Field final int INTERES_DOCTYPE_ID = 1000051; // Tipo de documento: Facturas de Interés
@Field final int NOTA_DOCTYPE_ID = 1000048; // Tipo de documento: Factura Principal (Nota de Débito)
```

#### 2. Función `getPendingInvoices()` Modificada

**Antes:**
```groovy
List<MInvoice> getPendingInvoices(int C_BPartner_ID)
```

**Después:**
```groovy
List<MInvoice> getPendingInvoices(int C_BPartner_ID, Timestamp paymentDate)
```

La función ahora:
- Acepta la fecha del pago como parámetro
- Ejecuta dos queries separadas:
  1. Facturas de interés con `DateInvoiced < paymentDate`
  2. Facturas principales
- Retorna una lista ordenada que respeta la prioridad

#### 3. Función `processSinglePayment()` Actualizada

Ahora pasa la fecha del pago al obtener las facturas pendientes:
```groovy
List<MInvoice> invoices = getPendingInvoices(payment.getC_BPartner_ID(), payment.getDateAcct());
```

## Impacto

### Comportamiento Anterior
Un pago se asignaba cronológicamente a todas las facturas pendientes sin distinción de tipo.

**Ejemplo:**
- Factura Principal: 10/01/2025 - $1000
- Factura Interés 1: 11/01/2025 - $10
- Factura Interés 2: 12/01/2025 - $10
- Pago: 13/01/2025 - $100

El pago se asignaba: $100 a Factura Principal, dejando intereses sin pagar.

### Comportamiento Nuevo
El pago se asigna primero a intereses vencidos antes de la fecha del pago, luego al capital.

**Ejemplo (mismo caso):**
- Pago: 13/01/2025 - $100
- Se asigna: $10 a Interés 1, $10 a Interés 2, $80 a Factura Principal

## Validación

### Criterios de Validación
1. Los pagos deben asignarse primero a facturas de interés anteriores a la fecha del pago
2. Solo después de saldar todos los intereses aplicables, el pago debe asignarse a la factura principal
3. El orden dentro de las facturas de interés debe ser cronológico (más antigua primero)
4. Las facturas de interés posteriores a la fecha del pago no deben ser consideradas

### Pruebas Recomendadas
1. **Caso 1:** Pago con intereses vencidos y capital pendiente
   - Verificar que intereses se pagan primero
   
2. **Caso 2:** Pago solo con capital pendiente (sin intereses vencidos)
   - Verificar que se asigna directo a capital
   
3. **Caso 3:** Pago insuficiente para cubrir todos los intereses
   - Verificar que se asigna en orden cronológico a intereses
   
4. **Caso 4:** Pago con intereses vencidos y posteriores
   - Verificar que solo se consideran intereses anteriores a la fecha del pago

## Notas Técnicas

- El cambio es mínimo e interviene solo en la lógica de ordenamiento de facturas
- No afecta la creación de documentos de asignación (C_AllocationHdr/Line)
- Mantiene la transaccionalidad del proceso original
- Compatible con la estructura existente de datos

### Nota sobre ALLOCATION_DOCTYPE_ID
Existe una coincidencia pre-existente en el código donde:
- `ALLOCATION_DOCTYPE_ID = 1000051` (usado para documentos de asignación/allocation)
- `INTERES_DOCTYPE_ID = 1000051` (usado para facturas de interés)

Ambos usan el mismo ID 1000051. Esto es una condición pre-existente que no fue modificada en este cambio, ya que:
1. El sistema ya estaba funcionando con esta configuración
2. Los contextos de uso son diferentes (allocations vs invoices)
3. El alcance de este cambio es minimal y enfocado en la lógica de priorización de facturas
4. No hay evidencia de que esto esté causando problemas

Si en el futuro se requiere separar estos IDs, se debería hacer una revisión completa del sistema de tipos de documento.

## Referencias

- Archivo modificado: `/rules/asignacion-rules/asignacion-automatica.groovy`
- Documentos relacionados: `CONTEXT.md` - Sección 4.4 (Asignación Automática de Pagos)
- Issue/PR: [Indicar número de issue o PR si aplica]
