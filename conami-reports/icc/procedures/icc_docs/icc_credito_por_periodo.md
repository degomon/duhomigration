# Documentación del Proceso de Cálculo: `icc_credito`

## 1. Objetivo de la Función `icc_credito_por_periodo`

El propósito principal de la función `adempiere.icc_credito_por_periodo` es procesar la información de la cartera de créditos para un período contable específico. La función realiza los siguientes pasos clave:

1.  **Selecciona** todos los créditos que estuvieron activos durante el período.
2.  **Calcula** los saldos de capital e interés para cada crédito a la fecha de cierre del período.
3.  **Puebla** un conjunto de tablas de reporte (`icc_persona`, `icc_analista`, `icc_credito_persona` y, finalmente, `icc_credito`) con los datos consolidados.

La tabla final, `icc_credito`, sirve como la fuente de datos principal para la generación de informes financieros y de cartera.

---

## 2. Entidades Involucradas

Para realizar sus cálculos, la función se apoya en varias tablas y funciones clave de la base de datos:

* **`C_Period`**: Tabla que define los períodos contables. Se usa para obtener la fecha de inicio (`startdate`) y fin (`enddate`) del proceso.
* **`legacy_cartera`**: Es la tabla principal (o tabla de hechos) que contiene el registro de cada crédito otorgado. De aquí se obtienen el **monto principal**, la **tasa**, el **plazo** (`dias_cre`), y la **fecha de otorgamiento**.
* **`legacy_cobro`**: Contiene el registro de cada pago o abono realizado a un crédito. Es fundamental para saber el **monto total pagado** por el cliente.
* **`c_bpartner`**: Almacena la información de los socios de negocio (clientes, empleados, etc.). Se usa para obtener datos demográficos del cliente y del analista de crédito.
* **`adempiere.get_dias_efectivos(fecha_inicio, fecha_fin)`**: Una función auxiliar que calcula el número de días transcurridos entre dos fechas, excluyendo los domingos. Es crucial para el cálculo de intereses.
* **`icc_credito`**: La tabla de destino final donde se almacena el resultado consolidado de cada crédito para el período procesado.

---

## 3. Lógica de Cálculo Principal

El corazón de la función es un gran query que utiliza Expresiones de Tabla Comunes (CTEs o `WITH`) para procesar los datos en conjunto, lo cual es mucho más eficiente que procesar cada crédito individualmente.

La lógica se divide en los siguientes pasos:

### Paso 1: Cálculo del Interés Devengado (`interes_devengado`)

Este es el interés que un crédito ha generado teóricamente hasta la fecha de corte del período. La fórmula es:


interes_devengado = (Monto Principal * Tasa / Plazo en Días) * Días Efectivos Transcurridos


Donde:
* **`Monto Principal`**: `legacy_cartera.monto`
* **`Tasa`**: `legacy_cartera.tasa`
* **`Plazo en Días`**: `legacy_cartera.dias_cre`
* **`Días Efectivos Transcurridos`**: Se calcula con `get_dias_efectivos` desde la fecha de otorgamiento hasta la fecha de fin del período, asegurando que no exceda el plazo total del crédito.

### Paso 2: Prorrateo de Abonos y Cálculo de Saldos

Una vez que tenemos el interés que se debe (`interes_devengado`) y el total que el cliente ha pagado (`total_abonos`), se aplica la regla de negocio **"Interés primero, capital después"**.

* **`saldo_periodo_interes`**: Es el interés que queda pendiente después de que los abonos han cubierto todo lo posible.
    * **Fórmula:** `GREATEST(0, interes_devengado - total_abonos)`

* **`saldo_periodo_principal`**: Es el capital que queda pendiente. Se calcula restando al monto original la porción del abono que sobró *después* de haber pagado el interés.
    * **Fórmula:** `GREATEST(0, monto - GREATEST(0, total_abonos - interes_devengado))`

Estos dos saldos son los que finalmente se guardan en la tabla `icc_credito` en las columnas `interes_corriente` y `saldo`, respectivamente.

---

## 4. Ejemplos Prácticos

### Ejemplo 1: Crédito Cancelado en su Totalidad (`legacy_cartera_id = 10599341`)

* **Monto Principal (`monto`)**: 6,000
* **Tasa (`tasa`)**: 0.15
* **Plazo (`dias_cre`)**: 30
* **Total Pagado (`total_abonos`)**: 6,900

**Cálculos:**

1.  **`interes_devengado`**: El crédito se canceló, por lo que el interés devengado es el interés total del crédito.
    * `interes_devengado` = `6000 * 0.15` = **900**
    * *(Nota: En este caso, la fórmula completa también daría 900, ya que los días efectivos serían 30).*

2.  **`saldo_periodo_interes`**:
    * `GREATEST(0, 900 - 6900)` = `GREATEST(0, -6000)` = **0**
    * *(El pago de 6900 cubre por completo los 900 de interés).*

3.  **`saldo_periodo_principal`**:
    * Abono restante para capital: `GREATEST(0, 6900 - 900)` = `6000`
    * Saldo de capital: `GREATEST(0, 6000 - 6000)` = **0**

**Resultado Final:** El saldo de capital e interés es **CERO**, lo cual es correcto.

### Ejemplo 2: Crédito Activo con Pagos Parciales (`legacy_cartera_id = 10606107`)

* **Período**: Julio 2025 (Fecha de corte: `2025-07-31`)
* **Fecha Otorgamiento**: `2025-07-01`
* **Monto Principal (`monto`)**: 3,000
* **Tasa (`tasa`)**: 0.15
* **Plazo (`dias_cre`)**: 30
* **Total Pagado (`total_abonos`)**: 2,750

**Cálculos:**

1.  **`Días Efectivos Transcurridos`**:
    * Días desde `2025-07-01` hasta `2025-07-31` (excluyendo domingos) = **27 días**.

2.  **`interes_devengado`**:
    * Interés Diario: `(3000 * 0.15 / 30)` = `15`
    * `interes_devengado` = `15 * 27` = **405**

3.  **`saldo_periodo_interes`**:
    * `GREATEST(0, 405 - 2750)` = `GREATEST(0, -2345)` = **0**
    * *(El pago de 2750 cubre por completo los 405 de interés generados hasta la fecha).*

4.  **`saldo_periodo_principal`**:
    * Abono restante para capital: `GREATEST(0, 2750 - 405)` = `2345`
    * Saldo de capital: `GREATEST(0, 3000 - 2345)` = **655**

**Resultado Final:** Para el 31 de Julio, el crédito tiene un saldo de capital de **655** y un saldo de interés de **0**.
