#!/bin/bash

# ==============================================================================
# SCRIPT DE EJECUCIÓN EN LOTES PARA CORRECCIÓN DE SCHEDULES
# ==============================================================================
#
# PROPÓSITO:
# Este script ejecuta repetidamente el SQL de corrección hasta que no queden
# registros por procesar. Se detiene automáticamente cuando una ejecución
# procesa 0 registros.
#
# USO:
# 1. Rellena las variables de conexión a la base de datos (DB_USER, DB_PASSWORD, etc.).
# 2. Otorga permisos de ejecución al script: chmod +x run_fix_schedule_batches.sh
# 3. Ejecuta el script: ./run_fix_schedule_batches.sh
#
# ==============================================================================

# --- CONFIGURACIÓN: Por favor, rellena estas variables ---
DB_USER="adempiere"
DB_PASSWORD="adempiere**"
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="duho-preprod"
SQL_FILE="fix-asignaciones-relacionadas-a-schedule.sql"

# Variable para contar las ejecuciones
batch_number=1

# Bucle principal
while true; do
    echo "=================================================================="
    echo "Iniciando lote de procesamiento #$batch_number..."
    echo "=================================================================="

    # Ejecutar el script psql y capturar toda su salida (stdout y stderr)
    # Se antepone PGPASSWORD para pasar la contraseña de forma segura para esta ejecución.
    # El flag -v ON_ERROR_STOP=1 asegura que el script se detenga si hay un error de SQL.
    output=$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -f "$SQL_FILE" 2>&1)

    # Mostrar la salida completa del script SQL para auditoría
    echo "$output"

    # Extraer el número de registros procesados de la última línea del log
    processed_count=$(echo "$output" | grep "Total de schedules procesados en este lote:" | awk '{print $NF}')

    # Si no se encuentra la línea (p.ej. por un error), asumimos 0 para detener el bucle.
    processed_count=${processed_count:-0}

    echo ""
    echo "Resumen del lote #$batch_number: $processed_count registros procesados."

    # Condición de salida: si se procesaron 0 registros, hemos terminado.
    if [ "$processed_count" -eq 0 ]; then
        echo ""
        echo "Proceso finalizado. No hay más registros que procesar."
        break
    fi

    batch_number=$((batch_number + 1))
    # Opcional: añadir una pausa entre lotes si es necesario
    # sleep 2
done
