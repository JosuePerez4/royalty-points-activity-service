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
.env / variables de entorno
  -> PuntosLealtadApplication
  -> Spring Boot

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
- `CorsConfig`: CORS global para clientes web autorizados.
- `JacksonConfig`: serializacion JSON de fechas `LocalDateTime` como texto ISO-8601.
- `GlobalExceptionHandler`: formato de errores para 400, 404 y 500.

## Contrato HTTP

El puerto HTTP sale de `PORT` o `SERVER_PORT`. Los ejemplos usan `8081` como
valor local sugerido, no como valor embebido en la aplicacion.

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

`PuntosResponse` expone `clienteId`, `nombre`, `puntos`, `nivelLealtad`,
`estado` y `fechaCreacion`. El `email` se persiste en la tabla, pero no se
devuelve por HTTP; `telefono` se acepta en el evento entrante y no se persiste.
Las fechas se serializan como cadenas ISO-8601, por ejemplo
`2026-04-29T00:00:00`.

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
mayusculas. Un nivel inexistente no genera `400`: devuelve `200` con una lista
vacia si no hay registros coincidentes.

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

Errores de validacion responden `400` con `ErrorResponse`. Si falta el query
param `cantidad`, Spring lo convierte en `400` con mensaje
`Falta el parámetro requerido 'cantidad'`. Excepciones no controladas responden
`500` con mensaje generico `Error interno del servidor`; el detalle queda en
logs.

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
- `email`: valor recibido en el evento

Si ya existe un registro para el mismo `cliente_id`, el listener registra el
error de validacion y no crea un duplicado. El listener tambien captura errores
inesperados y los registra; no hay beans de retry ni DLQ definidos en este
servicio, por lo que la operacion queda como log-only desde este consumidor.

## Modelo de datos

Entidad: `ClientePuntos`

Tabla: `cliente_puntos`

Campos relevantes:

| Campo | Descripcion |
| --- | --- |
| `id` | UUID generado por JPA |
| `cliente_id` | Identificador unico del cliente |
| `nombre` | Nombre recibido en el evento |
| `email` | Email recibido en el evento; persistido, no expuesto en `PuntosResponse` |
| `puntos` | Saldo actual de puntos |
| `estado` | Estado del registro, inicia en `ACTIVO` |
| `nivel_lealtad` | `BRONZE`, `SILVER` o `GOLD` |
| `fecha_creacion` | Fecha asignada en `@PrePersist` |
| `fecha_ultima_actualizacion` | Fecha asignada en `@PrePersist` y `@PreUpdate` |

La aplicacion usa `spring.jpa.hibernate.ddl-auto=update`, por lo que Hibernate
actualiza el esquema al iniciar segun la entidad.

## Configuracion local

Requisitos:

- Java 21
- Maven Wrapper incluido (`bash ./mvnw`; el archivo esta versionado sin bit
  ejecutable)
- PostgreSQL accesible
- RabbitMQ accesible

La aplicacion no define valores por defecto para infraestructura en
`application.yml`. `PuntosLealtadApplication` busca un archivo `.env` desde el
directorio actual y hasta 8 directorios padre; si encuentra variables, las carga
como propiedades del sistema solo cuando no existen ya como variables del SO o
propiedades JVM. En contenedores y CI, define las variables directamente en el
proveedor.

Variables requeridas:

| Variable | Uso |
| --- | --- |
| `SPRING_APPLICATION_NAME` | Nombre de la aplicacion Spring |
| `SPRING_DATASOURCE_URL` | URL JDBC de PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Usuario de PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | Password de PostgreSQL |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | Driver JDBC, normalmente `org.postgresql.Driver` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Estrategia de esquema, por ejemplo `update` |
| `SPRING_JPA_SHOW_SQL` | `true` o `false` para logging SQL |
| `SPRING_RABBITMQ_HOST` | Host RabbitMQ |
| `SPRING_RABBITMQ_PORT` | Puerto RabbitMQ |
| `SPRING_RABBITMQ_USERNAME` | Usuario RabbitMQ |
| `SPRING_RABBITMQ_PASSWORD` | Password RabbitMQ |
| `SPRING_RABBITMQ_VIRTUAL_HOST` | Virtual host RabbitMQ |
| `SPRING_RABBITMQ_SSL_ENABLED` | `true` o `false` |
| `PORT` o `SERVER_PORT` | Puerto HTTP |
| `LOGGING_LEVEL_ROOT` | Nivel root de logs |
| `LOGGING_LEVEL_COM_LEALTAD` | Nivel de logs para el paquete del servicio |
| `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AMQP` | Nivel de logs AMQP |

Ejemplo local:

```bash
cp .env.example .env
bash ./mvnw spring-boot:run
```

Perfil adicional:

```bash
bash ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

El perfil `dev` solo fuerza `spring.jpa.show-sql=false`; datasource, RabbitMQ y
puerto siguen viniendo de variables de entorno o `.env`.

## CORS

`CorsConfig` aplica a `/**` y permite metodos `GET`, `POST`, `PUT`, `PATCH`,
`DELETE` y `OPTIONS`, con cualquier header y `maxAge` de 3600 segundos.

Origenes configurables:

| Variable | Valor por defecto |
| --- | --- |
| `FRONTEND_CORS_PATTERNS` | `https://*.vercel.app,https://*.up.railway.app,http://localhost:*,http://127.0.0.1:*` |
| `FRONTEND_URL` | `https://taller3-frontend-microservicios-production.up.railway.app` |

Ambas variables aceptan listas separadas por coma; el servicio combina los
valores antes de registrarlos como `allowedOriginPatterns`.

## Pruebas

Para ejecutar pruebas unitarias:

```bash
bash ./mvnw test
```

El suite actual cubre `ClientePuntosService` con Mockito: creacion, duplicados,
acumulacion, redencion, cambios de nivel y consultas. No hay pruebas de
integracion HTTP ni RabbitMQ en este repositorio.

> Nota: no copies credenciales reales en documentacion ni en ejemplos. Para
> entornos locales o CI, usa variables de entorno o un `.env` local fuera del
> control de versiones.

## Troubleshooting

### La aplicacion no arranca por placeholders sin resolver

`application.yml` referencia variables como `SPRING_DATASOURCE_URL`,
`SPRING_RABBITMQ_HOST` y `LOGGING_LEVEL_ROOT` sin valores por defecto. Verifica
que existan en el entorno o en `.env`. Si una variable ya existe en el SO o como
propiedad JVM, el loader de `.env` no la sobrescribe.

### El navegador bloquea la API por CORS

Agrega el origen del frontend en `FRONTEND_CORS_PATTERNS` o `FRONTEND_URL`.
Ambas variables aceptan valores separados por coma y se aplican sobre todos los
endpoints (`/**`).

### El cliente no aparece despues de crearlo

Verifica que el productor publique en:

- Exchange: `cliente-events-exchange`
- Routing key: `cliente.creado`
- Payload JSON compatible con `ClienteCreadoEvent`

Tambien revisa que RabbitMQ este disponible y que la cola
`puntos-lealtad-queue` exista; la aplicacion declara el exchange, la cola y el
binding al iniciar.

Si el log muestra un error de validacion o un error inesperado dentro de
`EventoListener`, este servicio solo lo registra y no define retry ni DLQ.

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