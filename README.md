# Irrigación MQTT (Repo base)

Repo base para sistema de riego por zonas con ESP32 + MQTT (HiveMQ) + Backend Java + Frontend Vue.

## Estructura
- `docs/` documentación funcional/técnica (MVP)
- `backend/` Spring Boot (esqueleto)
- `frontend/` Vue 3 (esqueleto)
- `esp32/` notas y placeholders de firmware

## ⚠️ Seguridad

### ✅ Mejoras Recientes (Diciembre 2025)
Este proyecto ha implementado mejoras significativas de seguridad:
- ✅ Global exception handler (sin exposición de stack traces)
- ✅ Sanitización de logs (información sensible protegida)
- ✅ Health checks completos (MQTT + PostgreSQL)
- ✅ CORS configurado
- ✅ Validaciones de entrada mejoradas
- ✅ Backup automatizado

Ver [MEJORAS-IMPLEMENTADAS.md](docs/MEJORAS-IMPLEMENTADAS.md) para detalles completos.

### Desarrollo vs Producción
- Las credenciales en `.env` son **SOLO para desarrollo local**
- NUNCA uses estas configuraciones en producción sin cambiarlas
- PostgreSQL: Cambiar `POSTGRES_PASSWORD` en producción
- MQTT: Habilitar TLS y autenticación en producción

### Configuración Inicial

1. **Copiar archivo de variables de entorno:**
   ```bash
   cp .env.example .env
   ```

2. **Editar `.env` con tus credenciales:**
   - Para desarrollo local, puedes usar los valores por defecto
   - Para producción, usa contraseñas fuertes y únicas

3. **El archivo `.env` NO se sube a Git** (está en `.gitignore`)

### Configuración de Producción
- Usa variables de entorno para credenciales
- Habilita TLS en MQTT (puerto 8883)
- Usa contraseñas fuertes (mínimo 16 caracteres)
- Configura CORS con dominios específicos
- Mantén los archivos de configuración de Cloudflare fuera del repo
- Considera usar gestores de secretos (AWS Secrets Manager, Azure Key Vault, etc.)
- Sigue la [Guía de Seguridad](docs/implementacion/guia-seguridad-produccion.md) completa

### Documentación de Seguridad
- [Auditoría de Seguridad](docs/auditoria-seguridad.md) - Estado actual
- [Análisis Completo](docs/analisis-mejoras-seguridad-escalabilidad.md) - 30 puntos de mejora
- [Guía de Producción](docs/implementacion/guia-seguridad-produccion.md) - Paso a paso
- [Mejoras Implementadas](docs/MEJORAS-IMPLEMENTADAS.md) - Resumen de cambios

## Primeros pasos (recomendado)
1. Copiar y configurar el archivo `.env` (ver arriba)
2. Configurar HiveMQ Cloud (cluster + credenciales)
3. Probar publish/subscribe con el **Web Client** de HiveMQ
4. Implementar conexión MQTT en ESP32 y probar comandos manuales
5. Recién después: backend + UI

## Monitoreo y Health Checks
Una vez levantado el sistema con `docker-compose up`, puedes verificar su estado:

```bash
# Health check general
curl http://localhost:8080/actuator/health

# Métricas de la aplicación
curl http://localhost:8080/actuator/metrics

# Ver métricas específicas
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

El health check verifica:
- ✅ Estado general de la aplicación
- ✅ Conexión a PostgreSQL
- ✅ Conexión al broker MQTT

## Requisitos
- Docker Desktop (para desarrollo local)
- Java 17+ (para backend)
- Node 18+ (para frontend)
- VSCode + GitHub Copilot (Agent) + GPT-5.1 Codex

## Notas
- MQTT debe ir **sobre TLS** en producción.
- La UI **no** debe conectarse directo al broker. Va contra el backend (REST/WebSocket).
