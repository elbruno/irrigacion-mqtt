# Guía de Seguridad para Producción

**Fecha:** 14 de diciembre de 2025  
**Versión:** 1.0  
**Propósito:** Configuración de seguridad para despliegue en producción

---

## 📋 Checklist de Seguridad Pre-Producción

### 1. Gestión de Credenciales ✅

- [ ] Crear archivo `.env` de producción (NO commitear)
- [ ] Generar contraseñas seguras (mínimo 16 caracteres, mezcla de mayúsculas, minúsculas, números y símbolos)
- [ ] Configurar variables de entorno en el servidor/contenedor
- [ ] Eliminar credenciales hardcodeadas del código
- [ ] Verificar que `.env` está en `.gitignore`

```bash
# Generar contraseña segura
openssl rand -base64 32

# Ejemplo de .env de producción
POSTGRES_PASSWORD=X9k2$mP7qR#vL4nZ8wY3sT6bH
APP_MQTT_PASSWORD=A5p9$zQ2wE#rT7yU3iO6pL
```

### 2. MQTT sobre TLS 🔐

**Problema Actual:** MQTT sin cifrar (puerto 1883)  
**Solución:** Configurar TLS/SSL

```bash
# .env producción
APP_MQTT_HOST=your-hivemq-cluster.cloud.hivemq.com
APP_MQTT_PORT=8883
APP_MQTT_TLS=true
APP_MQTT_USERNAME=your-mqtt-user
APP_MQTT_PASSWORD=your-secure-password
```

**Validación:**
```bash
# Verificar conexión TLS
openssl s_client -connect your-hivemq-cluster.cloud.hivemq.com:8883
```

### 3. PostgreSQL con SSL ⚠️

**Recomendación:** Habilitar SSL para conexión a base de datos

```yaml
# application.yml (producción)
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/irrigacion?sslmode=require
```

**Docker Compose (opcional para BD local con SSL):**
```yaml
postgres:
  image: postgres:15
  environment:
    POSTGRES_SSL_MODE: require
  volumes:
    - ./certs:/var/lib/postgresql/certs:ro
  command: >
    postgres
    -c ssl=on
    -c ssl_cert_file=/var/lib/postgresql/certs/server.crt
    -c ssl_key_file=/var/lib/postgresql/certs/server.key
```

### 4. CORS Restrictivo 🌐

**Problema Actual:** CORS abierto a localhost  
**Solución:** Restringir a dominios específicos

```bash
# .env producción
CORS_ALLOWED_ORIGINS=https://tu-dominio.com,https://app.tu-dominio.com
```

### 5. Rate Limiting ⏱️

**Estado:** Preparado en código (RateLimitException)  
**TODO:** Implementar en próxima iteración

**Recomendación temporal:** Usar nginx/reverse proxy con rate limiting

```nginx
# nginx.conf
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;

location /api/ {
    limit_req zone=api_limit burst=20;
    proxy_pass http://backend:8080;
}
```

### 6. Autenticación y Autorización 🔑

**Estado Actual:** ⚠️ Sin autenticación  
**Roadmap:** Sprint 1 (próximas 2 semanas)

**Workaround temporal:** Usar reverse proxy con autenticación básica

```nginx
# nginx.conf
location /api/ {
    auth_basic "Restricted Access";
    auth_basic_user_file /etc/nginx/.htpasswd;
    proxy_pass http://backend:8080;
}
```

**Generar htpasswd:**
```bash
htpasswd -c .htpasswd admin
```

**⚠️ IMPORTANTE:** Esto es temporal. Implementar JWT pronto.

### 7. Logs Seguros 📝

**Implementado:** ✅
- Sanitización de logs en `MqttGateway`
- Exception handling que no expone stack traces
- Structured logging preparado

**Configuración recomendada:**
```yaml
# logback-spring.xml
logging:
  level:
    com.example.irrigacion: INFO
    org.springframework: WARN
  file:
    name: /var/log/irrigacion/app.log
    max-size: 10MB
    max-history: 30
```

### 8. Firewall y Puertos 🔒

**Puertos que deben estar expuestos:**
- 8080: Backend API (detrás de reverse proxy)
- 5432: PostgreSQL (SOLO si acceso externo necesario, preferiblemente NO)
- 1883/8883: MQTT (solo para ESP32, NO público)

**Configuración recomendada:**
```bash
# ufw (Ubuntu)
ufw default deny incoming
ufw default allow outgoing
ufw allow 80/tcp    # HTTP (nginx)
ufw allow 443/tcp   # HTTPS (nginx)
ufw enable

# PostgreSQL: NO exponer públicamente
# Backend: Solo accesible desde nginx
# MQTT: Accesible solo con credenciales
```

### 9. Health Checks y Monitoreo 📊

**Implementado:** ✅
- `/actuator/health` - Health check general
- `/actuator/metrics` - Métricas de aplicación
- `MqttHealthIndicator` - Verifica conexión MQTT

**Docker compose con healthcheck:**
```yaml
backend:
  healthcheck:
    test: ["CMD", "wget", "--spider", "http://localhost:8080/actuator/health"]
    interval: 30s
    timeout: 5s
    retries: 3
```

**Prometheus (recomendado):**
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'irrigacion-backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['backend:8080']
```

### 10. Backups Automatizados 💾

**Script de backup:**
```bash
#!/bin/bash
# /usr/local/bin/backup-irrigacion.sh

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups/irrigacion"
BACKUP_FILE="$BACKUP_DIR/db_$DATE.sql.gz"

# Crear directorio si no existe
mkdir -p $BACKUP_DIR

# Backup de PostgreSQL
docker exec irrigacion-postgres pg_dump -U postgres irrigacion | gzip > "$BACKUP_FILE"

# Verificar éxito
if [ $? -eq 0 ]; then
    echo "Backup completado: $BACKUP_FILE"
    
    # Retener últimos 30 días
    find $BACKUP_DIR -name "db_*.sql.gz" -mtime +30 -delete
    
    # Subir a S3/Cloud Storage (opcional)
    # aws s3 cp "$BACKUP_FILE" s3://my-backups/irrigacion/
else
    echo "ERROR: Backup falló" >&2
    exit 1
fi
```

**Programar con crontab:**
```bash
# Ejecutar diariamente a las 2 AM
0 2 * * * /usr/local/bin/backup-irrigacion.sh >> /var/log/backup-irrigacion.log 2>&1
```

---

## 🚀 Despliegue Seguro Paso a Paso

### Paso 1: Preparar Servidor

```bash
# Actualizar sistema
sudo apt update && sudo apt upgrade -y

# Instalar Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Instalar Docker Compose
sudo apt install docker-compose-plugin -y

# Configurar firewall
sudo ufw enable
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

### Paso 2: Clonar Repositorio

```bash
cd /opt
git clone https://github.com/tu-usuario/irrigacion-mqtt.git
cd irrigacion-mqtt
```

### Paso 3: Configurar Variables de Entorno

```bash
# Copiar template
cp .env.example .env

# Editar con credenciales de producción
nano .env

# Permisos restrictivos
chmod 600 .env
```

### Paso 4: Configurar MQTT (HiveMQ Cloud)

1. Ir a https://console.hivemq.cloud/
2. Crear cluster
3. Configurar credenciales
4. Actualizar `.env`:
```bash
APP_MQTT_HOST=tu-cluster.hivemq.cloud
APP_MQTT_PORT=8883
APP_MQTT_TLS=true
APP_MQTT_USERNAME=tu-usuario
APP_MQTT_PASSWORD=tu-password
```

### Paso 5: Iniciar Servicios

```bash
# Construir imágenes
docker-compose build

# Iniciar servicios
docker-compose up -d

# Verificar logs
docker-compose logs -f backend

# Verificar health
curl http://localhost:8080/actuator/health
```

### Paso 6: Configurar Nginx (Reverse Proxy)

```bash
# Instalar nginx
sudo apt install nginx -y

# Configurar site
sudo nano /etc/nginx/sites-available/irrigacion
```

```nginx
upstream backend {
    server localhost:8080;
}

server {
    listen 80;
    server_name tu-dominio.com;

    # Redirigir a HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name tu-dominio.com;

    ssl_certificate /etc/letsencrypt/live/tu-dominio.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/tu-dominio.com/privkey.pem;

    # Configuración SSL segura
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    # Rate limiting
    limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;

    # Headers de seguridad
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    location /api/ {
        limit_req zone=api_limit burst=20;
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /actuator/health {
        proxy_pass http://backend;
        allow 127.0.0.1;
        deny all;
    }
}
```

```bash
# Habilitar site
sudo ln -s /etc/nginx/sites-available/irrigacion /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### Paso 7: Obtener Certificado SSL

```bash
# Instalar certbot
sudo apt install certbot python3-certbot-nginx -y

# Obtener certificado
sudo certbot --nginx -d tu-dominio.com

# Auto-renovación (ya configurada por certbot)
sudo certbot renew --dry-run
```

### Paso 8: Configurar Backups

```bash
# Copiar script de backup
sudo cp docs/implementacion/backup-irrigacion.sh /usr/local/bin/
sudo chmod +x /usr/local/bin/backup-irrigacion.sh

# Crear directorio de backups
sudo mkdir -p /backups/irrigacion

# Programar crontab
sudo crontab -e
# Añadir: 0 2 * * * /usr/local/bin/backup-irrigacion.sh >> /var/log/backup-irrigacion.log 2>&1
```

### Paso 9: Monitoreo (Opcional pero Recomendado)

```bash
# Docker compose con Prometheus + Grafana
docker-compose -f docker-compose.monitoring.yml up -d

# Acceder a:
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000 (admin/admin)
```

---

## 🔍 Verificación Post-Despliegue

### Checklist de Validación

- [ ] Backend responde en `/actuator/health`
- [ ] MQTT conectado (verificar logs)
- [ ] PostgreSQL accesible desde backend
- [ ] CORS configurado correctamente
- [ ] HTTPS funcionando
- [ ] Rate limiting activo
- [ ] Backups programados
- [ ] Logs rotando correctamente
- [ ] Métricas accesibles en `/actuator/metrics`
- [ ] ESP32 puede conectarse y recibir comandos

### Comandos de Verificación

```bash
# Health check
curl https://tu-dominio.com/actuator/health

# Verificar logs
docker-compose logs -f backend | grep ERROR

# Verificar MQTT
docker-compose logs mqtt | tail -20

# Verificar PostgreSQL
docker exec irrigacion-postgres psql -U postgres -c "SELECT version();"

# Test rate limiting (debe fallar después de N requests)
for i in {1..50}; do curl https://tu-dominio.com/api/nodos/test/agendas; done

# Verificar backup
ls -lh /backups/irrigacion/
```

---

## 📊 Métricas a Monitorear

### Críticas
- **Uptime del backend** (debe ser >99.5%)
- **Conexión MQTT** (debe estar siempre conectado)
- **Errores HTTP 5xx** (debe ser <0.1%)
- **Latencia API** (p95 <500ms)

### Importantes
- **Requests por segundo** (capacity planning)
- **Uso de memoria** (detectar leaks)
- **Pool de conexiones DB** (ajustar si necesario)
- **Tamaño de logs** (rotar antes de llenar disco)

### Útiles
- **Rate limit hits** (detectar abusos)
- **Comandos MQTT enviados** (actividad del sistema)
- **Agendas activas** (uso del sistema)

---

## 🚨 Plan de Respuesta a Incidentes

### Problema: Backend no responde

```bash
# 1. Verificar estado
docker-compose ps

# 2. Ver logs
docker-compose logs backend --tail=100

# 3. Reiniciar servicio
docker-compose restart backend

# 4. Si persiste, rebuild
docker-compose down
docker-compose build --no-cache backend
docker-compose up -d
```

### Problema: MQTT desconectado

```bash
# 1. Verificar logs
docker-compose logs backend | grep MQTT

# 2. Verificar credenciales en .env
cat .env | grep MQTT

# 3. Test conexión a broker
nc -zv tu-cluster.hivemq.cloud 8883

# 4. Reiniciar backend
docker-compose restart backend
```

### Problema: Base de datos corrupta

```bash
# 1. Restaurar desde backup
gunzip -c /backups/irrigacion/db_YYYYMMDD_HHMMSS.sql.gz | \
  docker exec -i irrigacion-postgres psql -U postgres irrigacion

# 2. Verificar integridad
docker exec irrigacion-postgres psql -U postgres -c "SELECT COUNT(*) FROM agenda;"

# 3. Reiniciar servicios
docker-compose restart backend
```

---

## 📚 Referencias

- [OWASP Top 10 2021](https://owasp.org/Top10/)
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- [HiveMQ Security](https://www.hivemq.com/mqtt-security-fundamentals/)
- [Docker Security Best Practices](https://docs.docker.com/engine/security/)
- [Nginx Security](https://docs.nginx.com/nginx/admin-guide/security-controls/)

---

## 🎯 Próximos Pasos de Seguridad

### Sprint 1 (Próximas 2 semanas)
1. ✅ Implementar JWT Authentication
2. ✅ Añadir autorización por nodo (multi-tenancy)
3. ✅ Implementar rate limiting en código
4. ✅ Añadir audit trail

### Sprint 2 (Siguientes 2 semanas)
5. ⚠️ Implementar circuit breakers
6. ⚠️ Añadir structured logging (JSON)
7. ⚠️ Configurar Prometheus + Grafana
8. ⚠️ Implementar alerting

### Sprint 3 (Futuro)
9. 💡 OAuth2/OIDC integration
10. 💡 API versioning
11. 💡 Request tracing distribuido
12. 💡 Secrets management (Vault)

---

**Última actualización:** 14 de diciembre de 2025  
**Mantenido por:** Equipo de DevOps/Seguridad  
**Contacto:** seguridad@tu-empresa.com
