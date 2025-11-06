# Mejora en la Inserción a tmp_payment_omitidos usando PreparedStatement

**Fecha**: 2025-11-06  
**Versión**: 20251106  
**Archivo afectado**: `rules/asignacion-rules/asignacion-automatica.groovy`

## Problema

El proceso de asignación automática de pagos presentaba problemas al insertar registros en la tabla `tmp_payment_omitidos`. Esta tabla se utiliza para registrar pagos que no tienen facturas pendientes para asignar, evitando que se reprocesen múltiples veces en el mismo día.

### Estructura de la Tabla

```sql
CREATE TABLE IF NOT EXISTS adempiere.tmp_payment_omitidos
(
    c_bpartner_id numeric(10,0),
    c_payment_id numeric(10,0),
    dateommited timestamp without time zone
)

CREATE UNIQUE INDEX IF NOT EXISTS tmp_payment_omitidos_uidx
    ON adempiere.tmp_payment_omitidos USING btree
    (c_payment_id ASC NULLS LAST)
```

La tabla tiene un **índice ÚNICO** en `c_payment_id`, lo que puede causar violaciones de constraint si se intenta insertar el mismo pago más de una vez.

## Solución Implementada

Se reemplazó el uso de `DB.executeUpdate` con una implementación directa de `PreparedStatement`, como fue solicitado en el issue.

### Cambios Realizados

1. **Uso de PreparedStatement directo**:
   - Reemplazado `DB.executeUpdate(sql, params, false, trxName)`
   - Por `PreparedStatement` con `DB.prepareStatement(sql, trxName)`

2. **Manejo de duplicados con ON CONFLICT**:
   ```sql
   INSERT INTO tmp_payment_omitidos (c_bpartner_id, c_payment_id, dateommited) 
   VALUES (?, ?, now()) 
   ON CONFLICT (c_payment_id) DO NOTHING
   ```
   - Utiliza la cláusula `ON CONFLICT` de PostgreSQL
   - Si el `c_payment_id` ya existe, se ignora silenciosamente la inserción
   - No genera error ni interrúmpe el proceso

3. **Manejo de excepciones específicas**:
   - Captura `SQLException` específicamente (no `Exception` genérica)
   - Registra advertencias en el log sin detener el proceso
   - Asegura el cierre del PreparedStatement en el bloque `finally`

### Código Antes

```groovy
String insertSql = "INSERT INTO tmp_payment_omitidos (c_bpartner_id, c_payment_id, dateommited) VALUES (?, ?, now())";
Object[] params = [payment.getC_BPartner_ID(), payment.get_ID()] as Object[];
int result = DB.executeUpdate(insertSql, params, false, g_TrxName);
logProcess("    -> Pago registrado en tmp_payment_omitidos (${result} rows affected).");
```

### Código Después

```groovy
String insertSql = "INSERT INTO tmp_payment_omitidos (c_bpartner_id, c_payment_id, dateommited) VALUES (?, ?, now()) ON CONFLICT (c_payment_id) DO NOTHING";
PreparedStatement pstmt = null;
try {
    pstmt = DB.prepareStatement(insertSql, g_TrxName);
    pstmt.setInt(1, payment.getC_BPartner_ID());
    pstmt.setInt(2, payment.get_ID());
    int result = pstmt.executeUpdate();
    logProcess("    -> Pago registrado en tmp_payment_omitidos (${result} rows affected).");
} catch (SQLException e) {
    log.log(Level.WARNING, "Error al insertar en tmp_payment_omitidos para pago ID ${payment.get_ID()}", e);
    logProcess("    -> Advertencia: No se pudo registrar el pago en tmp_payment_omitidos: " + e.getMessage());
} finally {
    DB.close(pstmt);
}
```

## Ventajas de la Solución

1. **Control Directo**: El uso de PreparedStatement proporciona control directo sobre la ejecución SQL y el binding de parámetros

2. **Manejo Robusto de Duplicados**: La cláusula `ON CONFLICT` evita errores cuando se intenta insertar el mismo pago múltiples veces

3. **Seguridad Transaccional**: El PreparedStatement respeta el contexto transaccional (`g_TrxName`), asegurando la integridad de los datos

4. **Gestión de Recursos**: El bloque `try-catch-finally` garantiza que los recursos se liberen correctamente incluso si ocurre un error

5. **Diagnóstico Mejorado**: Las excepciones específicas (`SQLException`) permiten mejor diagnóstico de problemas de base de datos

## Comportamiento Esperado

- **Primera inserción**: `result = 1` (1 fila insertada)
- **Intento de duplicado**: `result = 0` (0 filas insertadas, sin error)
- **Error de base de datos**: Se registra advertencia, el proceso continúa

## Impacto

- **Sin cambios de comportamiento funcional**: El proceso sigue funcionando igual
- **Mayor robustez**: Maneja duplicados sin fallar
- **Mejor diagnóstico**: Logs más informativos en caso de errores
- **Código más seguro**: Gestión apropiada de recursos y excepciones

## Testing

No se agregaron tests automatizados ya que:
1. El repositorio no tiene infraestructura de testing para Groovy
2. Los scripts se ejecutan dentro del contexto de iDempiere/Adempiere ERP
3. Las pruebas requerirían una base de datos completa con datos de prueba

## Commits

- `cb597ae`: Implementación inicial con PreparedStatement y ON CONFLICT
- `3c59231`: Mejora del manejo de excepciones (SQLException específica)
