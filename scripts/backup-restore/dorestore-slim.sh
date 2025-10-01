#!/bin/bash

# --- Configuración ---
# Directorio raíz donde se almacenan los backups
BACKUP_DIR="/opt/pgbackups"

# Detalles de la base de datos de DESTINO (local)
DB_NAME="duho-slim-latest"
DB_USER="adempiere"         # Usuario para conectar a PostgreSQL local (suele ser 'postgres')
DB_HOST="localhost"
DB_PORT="5432"
OWNER_USER="adempiere"     # Usuario que será el dueño de la nueva BDD
PARALLEL_JOBS=8            # Número de trabajos para pg_restore (ajustar según los núcleos de tu CPU)

# --- Fin de la Configuración ---

set -e # Termina el script inmediatamente si un comando falla

echo "--- Iniciando proceso de restauración automática ---"

# 1. Encontrar el último backup 'slim'
echo "🔍 Buscando el último backup tipo 'slim' en $BACKUP_DIR..."
LATEST_BACKUP=$(find "$BACKUP_DIR" -type f -name "*-slim.tar.gz" | sort -r | head -n 1)

if [ -z "$LATEST_BACKUP" ]; then
    echo "❌ Error: No se encontró ningún archivo de backup 'slim' (*-slim.tar.gz)."
    exit 1
fi

echo "✅ Backup encontrado: $LATEST_BACKUP"

# 2. Eliminar y volver a crear la base de datos de destino
echo "🔥 Preparando base de datos de destino: '$DB_NAME'..."
dropdb --if-exists -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" "$DB_NAME"
createdb -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -O "$OWNER_USER" -T template0 "$DB_NAME"
echo "✅ Base de datos '$DB_NAME' creada y lista."

# 3. Crear un directorio temporal para la restauración
TEMP_RESTORE_DIR=$(mktemp -d /tmp/pgrestore-XXXXXX)
# Asegurar que el directorio temporal se borre al salir
trap 'echo "🧹 Limpiando archivos temporales..."; rm -rf "$TEMP_RESTORE_DIR"' EXIT

echo "📦 Descomprimiendo backup en directorio temporal: $TEMP_RESTORE_DIR"
# Descomprime el .tar.gz en el directorio temporal
tar -xzf "$LATEST_BACKUP" -C "$TEMP_RESTORE_DIR"

# El respaldo fue guardado en un subdirectorio, necesitamos su ruta completa
DECOMPRESSED_PATH=$(find "$TEMP_RESTORE_DIR" -mindepth 1 -maxdepth 1 -type d)
echo "📂 Datos listos en: $DECOMPRESSED_PATH"

# 4. Restaurar la base de datos
echo "🚀 Iniciando restauración con $PARALLEL_JOBS trabajos en paralelo..."
pg_restore --host="$DB_HOST" --port="$DB_PORT" --username="$DB_USER" --dbname="$DB_NAME" \
           --jobs="$PARALLEL_JOBS" --verbose "$DECOMPRESSED_PATH"

if [ $? -eq 0 ]; then
    echo "🎉 ¡Restauración completada exitosamente!"
else
    echo "❌ Error durante la ejecución de pg_restore."
    exit 1
fi

echo "--- Proceso finalizado ---"