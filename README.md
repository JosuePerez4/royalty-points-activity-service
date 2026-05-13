# royalty-points-activity-service

Microservicio Spring Boot para administrar puntos de lealtad por cliente. Expone
una API REST para consultar, acumular y redimir puntos, y consume eventos de
creacion de clientes desde RabbitMQ para inicializar el saldo de puntos.

## Responsabilidades

- Crear un registro de puntos cuando llega un evento `cliente.creado`.
- Mantener el saldo de puntos de cada cliente en PostgreSQL.
- Calcular el nivel de lealtad a partir del saldo actual.
- Exponer consultas y operaciones de acumulacion/redencion por HTTP.

## Arquitectura y flujo

```text
Servicio de clientes
  -> RabbitMQ exchange: cliente-events-exchange
  -> routing key: cliente.creado
  -> queue: puntos-lealtad-queue
  -> EventoListener
  -> ClientePuntosService
  -> tabla cliente_puntos
```

Componentes principales:

- `ClientePuntosController`: endpoints bajo `/api/puntos-lealtad`.
- `EventoListener`: consumidor RabbitMQ de la cola `puntos-lealtad-queue`.
- `ClientePuntosService`: reglas de negocio de creacion, consulta, acumulacion,
  redencion y cambio de nivel.
- `ClientePuntosRepository`: acceso JPA a `cliente_puntos`.
- `GlobalExceptionHandler`: formato de errores para 400, 404 y 500.

## Contrato HTTP

Los ejemplos asumen que el servicio se configuro con `SERVER_PORT=8081` o
`PORT=8081`. El puerto no tiene un valor fijo en `application.yml`; debe venir
de variables de entorno.

### Health check

```bash
curl http://localhost:8081/api/puntos-lealtad/health
```

Respuesta:

```json
{
  "servicio": "puntos-lealtad-service",
  "estado": "activo",
  "mensaje": "Servicio de Puntos de Lealtad est\u00e1 activo \u2705"
}
```

### Consultar cliente

```bash
curl http://localhost:8081/api/puntos-lealtad/clientes/CLI-001
```

Respuesta `200`:

```json
{
  "clienteId": "CLI-001",
  "nombre": "Juan Perez",
  "puntos": 600,
  "nivelLealtad": "SILVER",
  "estado": "ACTIVO",
  "fechaCreacion": "2026-04-29T00:00:00"
}
```

Si el cliente no existe, responde `404` con:

```json
{
  "codigo": 404,
  "mensaje": "Cliente con ID 'CLI-999' no encontrado",
  "timestamp": "2026-04-29T00:00:00",
  "path": "/api/puntos-lealtad/clientes/CLI-999"
}
```

### Listar clientes

```bash
curl http://localhost:8081/api/puntos-lealtad/clientes
```

Devuelve una lista de objetos con el mismo formato de `PuntosResponse`.

### Filtrar por nivel

```bash
curl http://localhost:8081/api/puntos-lealtad/niveles/SILVER/clientes
```

Niveles esperados:

| Nivel | Rango de puntos |
| --- | --- |
| `BRONZE` | 0 a 499 |
| `SILVER` | 500 a 999 |
| `GOLD` | 1000 o mas |

El path acepta minusculas o mayusculas; internamente se consulta el nivel en
mayusculas.

### Acumular puntos

```bash
curl -X POST "http://localhost:8081/api/puntos-lealtad/clientes/CLI-001/acumular?cantidad=250"
```

Restricciones:

- `cantidad` es obligatoria.
- Debe ser mayor a `0`.
- El cliente debe existir.
- El nivel se recalcula antes de persistir y devolver el saldo actualizado.

### Redimir puntos

```bash
curl -X POST "http://localhost:8081/api/puntos-lealtad/clientes/CLI-001/redimir?cantidad=100"
```

Restricciones:

- `cantidad` es obligatoria.
- Debe ser mayor a `0`.
- No puede superar el saldo disponible.
- El cliente debe existir.
- El nivel se recalcula antes de persistir y devolver el saldo actualizado.

Errores de validacion responden `400` con `ErrorResponse`.

## Evento `cliente.creado`

RabbitMQ se configura en `RabbitMQConfig` con:

- Exchange durable: `cliente-events-exchange`
- Cola durable: `puntos-lealtad-queue`
- Routing key: `cliente.creado`
- Conversor JSON: `Jackson2JsonMessageConverter`

Payload esperado por `ClienteCreadoEvent`:

```json
{
  "cliente_id": "CLI-001",
  "nombre": "Juan Perez",
  "email": "juan@example.com",
  "telefono": "555-1234",
  "timestamp": "2026-04-29T00:00:00",
  "evento_id": "EVT-001"
}
```

Al procesarlo, el servicio crea un registro con:

- `puntos`: `0`
- `estado`: `ACTIVO`
- `nivelLealtad`: `BRONZE`

Si ya existe un registro para el mismo `cliente_id`, el listener registra el
error de validacion y no crea un duplicado.

## Modelo de datos

Entidad: `ClientePuntos`

Tabla: `cliente_puntos`

Campos relevantes:

| Campo | Descripcion |
| --- | --- |
| `id` | UUID generado por JPA |
| `cliente_id` | Identificador unico del cliente |
| `nombre` | Nombre recibido en el evento |
| `email` | Email recibido en el evento |
| `puntos` | Saldo actual de puntos |
| `estado` | Estado del registro, inicia en `ACTIVO` |
| `nivel_lealtad` | `BRONZE`, `SILVER` o `GOLD` |
| `fecha_creacion` | Fecha asignada en `@PrePersist` |
| `fecha_ultima_actualizacion` | Fecha asignada en `@PrePersist` y `@PreUpdate` |

La estrategia de esquema se controla con `SPRING_JPA_HIBERNATE_DDL_AUTO`. En
desarrollo suele usarse `update`; para ambientes compartidos define este valor
explicitamente segun la politica de migraciones del entorno.

## Configuracion local

Requisitos:

- Java 21
- Maven Wrapper incluido (`./mvnw`)
- PostgreSQL accesible
- RabbitMQ accesible

### Variables de entorno requeridas

`application.yml` no define defaults para infraestructura. La aplicacion lee
variables del sistema operativo y, si existen, tambien carga un archivo `.env`
antes de iniciar Spring Boot. La carga de `.env` busca desde `user.dir` hacia
directorios padres hasta encontrar el archivo, no falla si no existe y no
sobrescribe variables ya definidas por el sistema operativo o como propiedades
JVM.

| Variable | Uso |
| --- | --- |
| `SPRING_APPLICATION_NAME` | Nombre de la aplicacion Spring |
| `SPRING_DATASOURCE_URL` | URL JDBC de PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Usuario de PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | Password de PostgreSQL |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | Driver JDBC, normalmente `org.postgresql.Driver` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Estrategia Hibernate, por ejemplo `update` |
| `SPRING_JPA_SHOW_SQL` | `true` o `false` para imprimir SQL |
| `SPRING_RABBITMQ_HOST` | Host RabbitMQ |
| `SPRING_RABBITMQ_PORT` | Puerto RabbitMQ |
| `SPRING_RABBITMQ_USERNAME` | Usuario RabbitMQ |
| `SPRING_RABBITMQ_PASSWORD` | Password RabbitMQ |
| `SPRING_RABBITMQ_VIRTUAL_HOST` | Virtual host RabbitMQ |
| `SPRING_RABBITMQ_SSL_ENABLED` | `true` si RabbitMQ requiere TLS |
| `SERVER_PORT` o `PORT` | Puerto HTTP. `PORT` tiene prioridad cuando existe |
| `LOGGING_LEVEL_ROOT` | Nivel de log raiz |
| `LOGGING_LEVEL_COM_LEALTAD` | Nivel de log para `com.lealtad` |
| `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AMQP` | Nivel de log para Spring AMQP |

`SPRING_JPA_OPEN_IN_VIEW` es opcional y por defecto queda en `false`.

Ejemplo minimo para desarrollo local:

```dotenv
SPRING_APPLICATION_NAME=puntos-lealtad-service
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/puntos
SPRING_DATASOURCE_USERNAME=puntos_user
SPRING_DATASOURCE_PASSWORD=change-me
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_JPA_SHOW_SQL=false
SPRING_RABBITMQ_HOST=localhost
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=guest
SPRING_RABBITMQ_PASSWORD=guest
SPRING_RABBITMQ_VIRTUAL_HOST=/
SPRING_RABBITMQ_SSL_ENABLED=false
SERVER_PORT=8081
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_LEALTAD=DEBUG
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AMQP=INFO
```

El perfil `application-dev.yml` solo cambia `spring.jpa.show-sql=false`; no
provee datasource, RabbitMQ, puerto ni logs. Esas variables siguen siendo
obligatorias.

### CORS

`CorsConfig` permite todos los paths (`/**`) y los metodos `GET`, `POST`, `PUT`,
`PATCH`, `DELETE` y `OPTIONS`. Los origenes permitidos se construyen desde:

- `FRONTEND_CORS_PATTERNS`, CSV de patrones. Si no se define, usa
  `https://*.vercel.app,https://*.up.railway.app,http://localhost:*,http://127.0.0.1:*`.
- `FRONTEND_URL`, CSV de origenes adicionales o un origen exacto. Si no se
  define, agrega `https://taller3-frontend-microservicios-production.up.railway.app`.

No declares `FRONTEND_CORS_PATTERNS` dentro de `application.yml`; el archivo
incluye una nota porque esa forma puede causar referencias circulares.

Para correr:

```bash
./mvnw spring-boot:run
```

Para ejecutar pruebas unitarias:

```bash
./mvnw test
```

> Nota: no copies credenciales reales en documentacion ni en ejemplos. Para
> entornos locales o CI, sobrescribe `spring.datasource.*` y `spring.rabbitmq.*`
> con propiedades de Spring Boot, variables de entorno o archivos de perfil
> fuera del control de versiones cuando aplique.

## Troubleshooting

### El cliente no aparece despues de crearlo

Verifica que el productor publique en:

- Exchange: `cliente-events-exchange`
- Routing key: `cliente.creado`
- Payload JSON compatible con `ClienteCreadoEvent`

Tambien revisa que RabbitMQ este disponible y que la cola
`puntos-lealtad-queue` exista; la aplicacion declara el exchange, la cola y el
binding al iniciar.

### Respuestas 404 al consultar puntos

El endpoint consulta por `clienteId`, no por el UUID interno de la tabla. Un 404
significa que no existe un registro con ese `cliente_id` en `cliente_puntos`.

### Respuestas 400 al acumular o redimir

Las operaciones rechazan cantidades nulas, cero o negativas. La redencion
tambien falla si la cantidad solicitada excede los puntos disponibles.

### El nivel no coincide con el saldo

El nivel se recalcula solo cuando se acumulan o redimen puntos desde
`ClientePuntosService`. Si se modifican registros directamente en la base de
datos, el servicio no recalcula automaticamente esos cambios externos.

### La aplicacion no arranca por placeholders sin resolver

Revisa que todas las variables requeridas de `application.yml` existan en el
entorno o en `.env`. En plataformas de contenedores no asumas que `.env` esta
presente; define las variables en el proveedor. `SPRING_JPA_OPEN_IN_VIEW` es la
unica variable de esa seccion con fallback (`false`).

### El puerto local no es 8081

El puerto sale de `PORT` o `SERVER_PORT`. Si `PORT` existe, Spring lo usa antes
que `SERVER_PORT` por la expresion `${PORT:${SERVER_PORT}}`.

### El navegador bloquea llamadas por CORS

Confirma que el frontend coincida con alguno de los patrones en
`FRONTEND_CORS_PATTERNS` o con un origen exacto agregado por `FRONTEND_URL`.
Ambas variables aceptan valores separados por coma; los espacios alrededor se
recortan antes de registrar los origenes.
