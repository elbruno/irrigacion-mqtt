#!/bin/bash
#
# Script de backup automatizado para PostgreSQL
# Uso: ./backup-irrigacion.sh
# Crontab: 0 2 * * * /path/to/backup-irrigacion.sh >> /var/log/backup-irrigacion.log 2>&1
#

set -e

# Configuración
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="${BACKUP_DIR:-/backups/irrigacion}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
CONTAINER_NAME="${POSTGRES_CONTAINER:-irrigacion-postgres}"
DB_NAME="${POSTGRES_DB:-irrigacion}"
DB_USER="${POSTGRES_USER:-postgres}"

# Crear directorio si no existe
mkdir -p "$BACKUP_DIR"

# Archivo de backup
BACKUP_FILE="$BACKUP_DIR/db_$DATE.sql.gz"

echo "=========================================="
echo "Backup PostgreSQL - Irrigación MQTT"
echo "Fecha: $(date)"
echo "=========================================="

# Verificar que el contenedor existe
if ! docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "ERROR: Contenedor $CONTAINER_NAME no encontrado" >&2
    exit 1
fi

# Verificar que el contenedor está corriendo
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "ERROR: Contenedor $CONTAINER_NAME no está corriendo" >&2
    exit 1
fi

# Crear backup
echo "Iniciando backup de base de datos: $DB_NAME"
if docker exec "$CONTAINER_NAME" pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$BACKUP_FILE"; then
    BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
    echo "✓ Backup completado: $BACKUP_FILE ($BACKUP_SIZE)"
    
    # Limpiar backups antiguos
    echo "Limpiando backups con más de $RETENTION_DAYS días..."
    DELETED=$(find "$BACKUP_DIR" -name "db_*.sql.gz" -mtime +$RETENTION_DAYS -delete -print | wc -l)
    echo "✓ Eliminados $DELETED backups antiguos"
    
    # Listar backups existentes
    echo ""
    echo "Backups disponibles:"
    ls -lh "$BACKUP_DIR"/db_*.sql.gz | tail -5
    
    # Subir a cloud storage (descomentear si necesario)
    # if command -v aws &> /dev/null; then
    #     echo "Subiendo backup a S3..."
    #     aws s3 cp "$BACKUP_FILE" "s3://your-bucket/irrigacion/backups/"
    #     echo "✓ Backup subido a S3"
    # fi
    
    echo ""
    echo "=========================================="
    echo "✓ Backup completado exitosamente"
    echo "=========================================="
    exit 0
else
    echo "ERROR: Backup falló" >&2
    exit 1
fi
