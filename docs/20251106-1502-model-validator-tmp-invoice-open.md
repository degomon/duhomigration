# Model Validator para Actualización de tmp_invoice_open

**Fecha**: 2025-11-06  
**Hora**: 15:02 GMT-6  
**Versión**: 20251106  
**Archivo creado**: `rules/validators/c_invoice/populate-tmp-invoice-open.groovy`

## Propósito

Crear un model validator (validador de modelo) para iDempiere que se ejecute automáticamente después de guardar un registro de `C_Invoice` (factura), para mantener actualizada la tabla `tmp_invoice_open` con el saldo pendiente de cada factura.

## Contexto

La tabla `tmp_invoice_open` se utiliza en el sistema para optimizar consultas relacionadas con facturas pendientes de pago. Específicamente, es usada en el proceso de asignación automática de pagos (`asignacion-automatica.groovy`) para verificar rápidamente si un socio de negocio tiene facturas pendientes antes de procesar un pago.

### Consulta en asignacion-automatica.groovy:
```sql
AND EXISTS (
    select 1 
    from tmp_invoice_open io 
    where io.c_bpartner_id = pay.c_bpartner_id 
        and io.dateinvoiced <= pay.dateacct
)
```

## Solución Implementada

### Estructura de Model Validator

Los model validators en iDempiere son scripts Groovy que se ejecutan en respuesta a eventos específicos del ciclo de vida de un registro. En este caso:

**Eventos Capturados:**
- `TYPE_AFTER_NEW` (4): Después de crear una nueva factura
- `TYPE_AFTER_CHANGE` (2): Después de modificar una factura existente

**Variables Disponibles en el Contexto:**
- `po`: El objeto persistente (instancia de MInvoice)
- `type`: Tipo de evento (TYPE_AFTER_NEW = 4, TYPE_AFTER_CHANGE = 2)
- `trxName`: Se obtiene del objeto persistente mediante `inv.get_TrxName()`

### Lógica Implementada

1. **Validación del Contexto:**
   - Verifica que el script se ejecute en un contexto válido de model validator (variable `po` presente)
   - Verifica que el evento sea `TYPE_AFTER_NEW` o `TYPE_AFTER_CHANGE`

2. **Obtención de Datos:**
   - Obtiene el objeto `MInvoice` directamente desde la variable `po`
   - Extrae el `C_Invoice_ID` mediante `inv.get_ID()`
   - Verifica que sea una factura de cliente (`IsSOTrx='Y'`)
   - Obtiene `C_BPartner_ID` y `DateInvoiced` del objeto
   - Obtiene el nombre de la transacción mediante `inv.get_TrxName()`

3. **Cálculo del Saldo Abierto:**
   ```groovy
   String selectSql = "SELECT invoiceopen(?, NULL::numeric) as openamt"
   ```
   - Llama a la función de PostgreSQL `invoiceopen(c_invoice_id, NULL::numeric)`
   - Esta función calcula el saldo pendiente de la factura (GrandTotal - Suma de Asignaciones)
   - Retorna el monto en la moneda de la factura

4. **Actualización de tmp_invoice_open:**
   ```sql
   INSERT INTO tmp_invoice_open (c_invoice_id, c_bpartner_id, dateinvoiced, openamt)
   VALUES (?, ?, ?, ?)
   ON CONFLICT (c_invoice_id) 
   DO UPDATE SET 
       c_bpartner_id = EXCLUDED.c_bpartner_id,
       dateinvoiced = EXCLUDED.dateinvoiced,
       openamt = EXCLUDED.openamt
   ```
   - Usa **UPSERT** (INSERT ... ON CONFLICT ... DO UPDATE)
   - Si el registro existe (mismo `c_invoice_id`), lo actualiza
   - Si no existe, lo inserta
   - Operación atómica que evita condiciones de carrera

### Estructura de la Tabla tmp_invoice_open

Basándose en el uso del sistema, se infiere la siguiente estructura:

```sql
CREATE TABLE tmp_invoice_open (
    c_invoice_id numeric(10,0) PRIMARY KEY,
    c_bpartner_id numeric(10,0),
    dateinvoiced timestamp without time zone,
    openamt numeric
)
```

**Campos:**
- `c_invoice_id`: ID de la factura (clave primaria para el UPSERT)
- `c_bpartner_id`: ID del socio de negocio (cliente)
- `dateinvoiced`: Fecha de la factura
- `openamt`: Saldo pendiente calculado por `invoiceopen()`

## Uso de PreparedStatement

Siguiendo la convención establecida en sesiones anteriores (ver `20251106-0021-preparedstatement-tmp-payment-omitidos.md`), se utiliza `PreparedStatement` directamente para todas las operaciones de base de datos:

### Ventajas:
1. **Control directo** sobre la ejecución SQL
2. **Seguridad contra SQL injection** mediante binding de parámetros
3. **Respeto al contexto transaccional** (`A_TrxName`)
4. **Gestión apropiada de recursos** con bloques try-catch-finally

### Ejemplo de Uso:
```groovy
PreparedStatement pstmt = null
try {
    pstmt = DB.prepareStatement(sql, A_TrxName)
    pstmt.setInt(1, invoiceId)
    pstmt.setInt(2, bpartnerId)
    pstmt.setTimestamp(3, dateInvoiced)
    pstmt.setBigDecimal(4, openAmt)
    int result = pstmt.executeUpdate()
} catch (SQLException e) {
    log.log(Level.SEVERE, "Error message", e)
    return "Error: " + e.getMessage()
} finally {
    DB.close(pstmt)
}
```

## Manejo de Errores

El validator implementa un manejo robusto de errores:

1. **Validación de Contexto**: Retorna error si no está en contexto válido
2. **Validación de IDs**: Verifica que los IDs sean válidos (> 0)
3. **Filtro de Tipo de Factura**: Solo procesa facturas de cliente
4. **Manejo de NULL**: Si `invoiceopen()` retorna NULL, usa BigDecimal.ZERO
5. **Logging Detallado**: Registra errores con nivel apropiado (WARNING, SEVERE)
6. **Mensajes de Error**: Retorna mensajes descriptivos al usuario

## Comportamiento Esperado

### Escenarios:

1. **Nueva Factura Creada (TYPE_AFTER_NEW)**:
   - Se calcula el `openamt` (normalmente igual al GrandTotal si no hay pagos)
   - Se inserta un nuevo registro en `tmp_invoice_open`

2. **Factura Modificada (TYPE_AFTER_CHANGE)**:
   - Se recalcula el `openamt` (puede haber cambiado por asignaciones)
   - Se actualiza el registro existente en `tmp_invoice_open`

3. **Factura de Compra (IsSOTrx='N')**:
   - Se omite el procesamiento (solo se procesan facturas de cliente)

4. **Error en invoiceopen()**:
   - Se registra el error en el log
   - Se retorna mensaje de error al usuario
   - La transacción puede ser revertida

## Integración con el Sistema

Este validator se integra automáticamente con:

1. **asignacion-automatica.groovy**: 
   - El proceso de asignación ahora tendrá datos actualizados en tiempo real
   - Ya no depende de procesos batch para actualizar `tmp_invoice_open`

2. **Flujo de Facturación**:
   - Cada vez que se crea/modifica una factura, `tmp_invoice_open` se actualiza
   - Incluye facturas de capital e interés (ambas se procesan)

3. **Transacciones**:
   - El validator respeta el contexto transaccional
   - Si la transacción se revierte, los cambios en `tmp_invoice_open` también se revierten

## Configuración en iDempiere

Para activar este model validator en iDempiere:

1. Ir a **System Administrator** → **General Rules** → **Model Validator**
2. Crear un nuevo registro con:
   - **Name**: PopulateTmpInvoiceOpen
   - **Entity Type**: [según configuración]
   - **Table**: C_Invoice
   - **Script**: Seleccionar el archivo `populate-tmp-invoice-open.groovy`
   - **Active**: ✓
3. Configurar los eventos:
   - **After New**: ✓
   - **After Change**: ✓

## Testing

### Pruebas Manuales Sugeridas:

1. **Crear Nueva Factura**:
   ```sql
   -- Verificar que se creó el registro
   SELECT * FROM tmp_invoice_open WHERE c_invoice_id = [nuevo_id];
   ```

2. **Modificar Factura Existente**:
   ```sql
   -- Verificar que se actualizó el openamt
   SELECT openamt FROM tmp_invoice_open WHERE c_invoice_id = [id];
   SELECT invoiceopen([id], NULL::numeric);
   -- Ambos valores deben coincidir
   ```

3. **Asignar Pago a Factura**:
   ```sql
   -- Después de la asignación, verificar que openamt se redujo
   SELECT openamt FROM tmp_invoice_open WHERE c_invoice_id = [id];
   ```

### Verificación de Logs:

```groovy
// Los logs se registran con este patrón
log.info("Successfully updated tmp_invoice_open for invoice ${invoiceId}...")
log.warning("Invalid C_Invoice_ID: ${invoiceId}")
log.severe("Error calling invoiceopen function...")
```

## Limitaciones y Consideraciones

1. **Base de Datos**: 
   - Requiere PostgreSQL (usa ON CONFLICT)
   - Función `invoiceopen()` debe existir en el schema

2. **Performance**:
   - Se ejecuta en cada guardado de factura
   - Para volúmenes muy altos, considerar optimizaciones

3. **Tabla tmp_invoice_open**:
   - Debe tener índice único en `c_invoice_id`
   - Debe existir antes de activar el validator

4. **Solo Facturas de Cliente**:
   - No procesa facturas de proveedor (IsSOTrx='N')

## Impacto

### Positivo:
- ✓ Datos siempre actualizados en tiempo real
- ✓ Elimina necesidad de procesos batch
- ✓ Mejora performance de asignación automática
- ✓ Código robusto con manejo de errores

### A Considerar:
- Overhead adicional en cada guardado de factura
- Depende de la existencia de la tabla tmp_invoice_open

## Referencias

- **iDempiere Wiki**: Script ModelValidator (https://wiki.idempiere.org/en/Script_ModelValidator)
- **Función invoiceopen**: Ver `/sql/db-structure/adempiere-db-structure-only.sql`
- **Sesión Anterior**: `20251106-0021-preparedstatement-tmp-payment-omitidos.md`
- **Contexto del Sistema**: `CONTEXT.md`

## Archivos Modificados/Creados

- ✓ **Nuevo**: `rules/validators/c_invoice/populate-tmp-invoice-open.groovy`
- ✓ **Nuevo**: `docs/20251106-1502-model-validator-tmp-invoice-open.md` (este archivo)

## Próximos Pasos

1. Revisar/crear la tabla `tmp_invoice_open` si no existe
2. Configurar el model validator en iDempiere
3. Realizar pruebas en ambiente de desarrollo
4. Monitorear logs para detectar errores
5. Validar performance con volumen real de facturas
