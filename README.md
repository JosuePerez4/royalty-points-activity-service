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

Los ejemplos usan `localhost:8081`. En ejecucion real el puerto lo define
`PORT` o `SERVER_PORT`; si ninguna variable existe, la aplicacion no tiene un
fallback local en `application.yml`.

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

Errores de validacion responden `400` con `ErrorResponse`. Si falta el query
param `cantidad`, `GlobalExceptionHandler` devuelve el mensaje
`Falta el parametro requerido 'cantidad'`.

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
error de validacion y no crea un duplicado. `EventoListener` captura
`IllegalArgumentException` y excepciones inesperadas; al no relanzarlas, el
metodo del listener termina y Spring AMQP considera el mensaje procesado. Esta
aplicacion no configura reintentos ni DLQ propios para esos casos.

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

La estrategia de esquema se controla con `SPRING_JPA_HIBERNATE_DDL_AUTO`.
Usar `update` permite que Hibernate ajuste el esquema al iniciar segun la
entidad, pero ese valor debe definirse explicitamente en el entorno.

## Configuracion local

Requisitos:

- Java 21
- Maven Wrapper incluido (`./mvnw`)
- PostgreSQL accesible
- RabbitMQ accesible

La configuracion de infraestructura es obligatoria y llega por variables de
entorno o por un archivo `.env`. `PuntosLealtadApplication` busca `.env`
subiendo desde `user.dir` hasta ocho niveles y lo carga antes de iniciar Spring;
si una variable ya existe en el sistema operativo o como `System.getProperty`,
no la sobrescribe. En contenedores y proveedores cloud normalmente conviene
definir las variables en el proveedor, no montar un `.env`.

Variables usadas por `application.yml`:

| Variable | Uso |
| --- | --- |
| `SPRING_APPLICATION_NAME` | Nombre de la aplicacion Spring |
| `SPRING_DATASOURCE_URL` | JDBC URL de PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Usuario de PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | Password de PostgreSQL |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | Driver JDBC, normalmente `org.postgresql.Driver` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Estrategia de esquema (`validate`, `update`, etc.) |
| `SPRING_JPA_SHOW_SQL` | Habilita/deshabilita SQL en logs |
| `SPRING_JPA_OPEN_IN_VIEW` | Opcional; default `false` |
| `SPRING_RABBITMQ_HOST` | Host de RabbitMQ |
| `SPRING_RABBITMQ_PORT` | Puerto de RabbitMQ |
| `SPRING_RABBITMQ_USERNAME` | Usuario de RabbitMQ |
| `SPRING_RABBITMQ_PASSWORD` | Password de RabbitMQ |
| `SPRING_RABBITMQ_VIRTUAL_HOST` | Virtual host de RabbitMQ |
| `SPRING_RABBITMQ_SSL_ENABLED` | `true` si el broker exige TLS |
| `PORT` o `SERVER_PORT` | Puerto HTTP |
| `LOGGING_LEVEL_ROOT` | Nivel de log raiz |
| `LOGGING_LEVEL_COM_LEALTAD` | Nivel de log del paquete `com.lealtad` |
| `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AMQP` | Nivel de log de Spring AMQP |

Ejemplo minimo para desarrollo local:

```env
SPRING_APPLICATION_NAME=puntos-lealtad-service
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/lealtad
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
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

Configuracion adicional:

- Perfil `dev`: solo ajusta `spring.jpa.show-sql=false`; datasource y RabbitMQ
  siguen viniendo de `application.yml` mas variables de entorno o `.env`.
- CORS: `CorsConfig` acepta patrones desde `FRONTEND_CORS_PATTERNS` y origenes
  extra desde `FRONTEND_URL`, ambos como CSV. Si no se definen, permite
  `https://*.vercel.app`, `https://*.up.railway.app`, `http://localhost:*`,
  `http://127.0.0.1:*` y el frontend Railway configurado en el codigo.
- RabbitMQ SSL: habilita TLS con `SPRING_RABBITMQ_SSL_ENABLED=true` cuando el
  broker lo requiera.

Para correr:

```bash
./mvnw spring-boot:run
```

Para ejecutar pruebas unitarias:

```bash
./mvnw test
```

> Nota: no copies credenciales reales en documentacion ni en ejemplos. Para
> entornos locales o CI, usa variables de entorno, secretos del proveedor o un
> `.env` fuera del control de versiones.

## Troubleshooting

### El cliente no aparece despues de crearlo

Verifica que el productor publique en:

- Exchange: `cliente-events-exchange`
- Routing key: `cliente.creado`
- Payload JSON compatible con `ClienteCreadoEvent`

Tambien revisa que RabbitMQ este disponible y que la cola
`puntos-lealtad-queue` exista; la aplicacion declara el exchange, la cola y el
binding al iniciar.

Si el listener recibe un evento duplicado o invalido, lo registra en logs y no
relanza la excepcion. Sin una DLQ configurada fuera de este servicio, ese
mensaje no queda pendiente para reintento desde esta aplicacion.

### La aplicacion no arranca por placeholders sin resolver

`application.yml` no incluye defaults para datasource, RabbitMQ, logs ni puerto.
Define todas las variables listadas en "Configuracion local". Para desarrollo,
un `.env` en la raiz del repo es suficiente si no hay variables del sistema con
el mismo nombre.

### Errores de CORS desde el frontend

Agrega el origen exacto o un patron compatible en `FRONTEND_CORS_PATTERNS`, o
usa `FRONTEND_URL` para un origen adicional. Ambos valores aceptan listas
separadas por coma.

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