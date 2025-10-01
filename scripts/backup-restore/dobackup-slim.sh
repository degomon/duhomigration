#!/bin/bash

# Directory where backups will be stored
BACKUP_DIR="/opt/pgbackups"

# PostgreSQL credentials
PG_USER="adempiere"
PG_DB="duho-prod"
PG_HOST="173.208.141.202" # Change if different
PG_PORT="5432"      # Change if different
PARALLEL_JOBS=8     # Number of parallel jobs for pg_dump

# Function to perform backup for a single database with parallel jobs
backup_database() {
    local db_name=$1

    # Create directory structure based on current date and time
    local current_date=$(date +"%Y/%m/%d_%H%M%S")
    local backup_path="${BACKUP_DIR}/${current_date}"

    echo "Creating backup directory: $backup_path"
    mkdir -p "$backup_path"

    # Perform parallel backup, excluding data from multiple adempiere tables
    echo "Starting SLIM backup for: $db_name (excluding data from log and pinstance tables)"
    pg_dump -U "$PG_USER" -h "$PG_HOST" -p "$PG_PORT" -d "$db_name" \
            -F d -j "$PARALLEL_JOBS" -Z 0 \
            --exclude-table-data='adempiere.ad_changelog' \
            --exclude-table-data='adempiere.ad_pinstance' \
            --exclude-table-data='adempiere.ad_pinstance_para' \
            --exclude-table-data='adempiere.ad_pinstance_log' \
            -f "$backup_path" -v

    if [ $? -eq 0 ]; then
        echo "Backup completed for database: $db_name"
    else
        echo "Backup failed for database: $db_name"
        return 1
    fi

    # Compress the backup directory using tar and pigz with '-slim' suffix
    local final_filename="${backup_path}-slim.tar.gz"
    echo "Compressing backup directory with pigz to: $final_filename"
    tar -c -C "$(dirname "$backup_path")" "$(basename "$backup_path")" | pigz > "$final_filename"

    if [ $? -eq 0 ]; then
        echo "Compression completed: $final_filename"
        # Remove the uncompressed backup directory
        rm -rf "$backup_path"
        echo "Removed uncompressed backup directory: $backup_path"
    else
        echo "Compression failed for: $backup_path"
        return 1
    fi
}

# Run backup for multiple databases
run_backups() {
    declare -a databases=("duho-prod") # Add your list of databases here

    for db in "${databases[@]}"; do
        echo "Starting SLIM backup job for: $db"
        backup_database "$db"
    done

    echo "All backup jobs completed."
}

# Start the backup process
run_backups