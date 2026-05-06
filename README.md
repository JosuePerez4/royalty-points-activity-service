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

El servicio escucha en el puerto `8081` por defecto.

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

La aplicacion usa `spring.jpa.hibernate.ddl-auto=update`, por lo que Hibernate
actualiza el esquema al iniciar segun la entidad.

## Configuracion local

Requisitos:

- Java 21
- Maven Wrapper incluido (`./mvnw`)
- PostgreSQL accesible
- RabbitMQ accesible en `localhost:5672` o configurado mediante propiedades

Configuracion:

- El servicio no define valores por defecto para infraestructura en
  `application.yml`; lee las propiedades desde variables de entorno o desde un
  archivo `.env` local.
- `PuntosLealtadApplication` busca `.env` desde el directorio de ejecucion hacia
  arriba (hasta 8 niveles) y solo copia claves que no existan ya en el sistema.
- En contenedores o PaaS, define las variables en el proveedor de despliegue en
  vez de depender de `.env`.
- Perfil adicional disponible: `application-dev.yml`; solo cambia `show-sql` y
  sigue usando las mismas variables de infraestructura.

### Variables de entorno

Variables requeridas por `application.yml`:

| Variable | Uso |
| --- | --- |
| `SPRING_APPLICATION_NAME` | Nombre de la aplicacion Spring. |
| `SPRING_DATASOURCE_URL` | JDBC URL de PostgreSQL. |
| `SPRING_DATASOURCE_USERNAME` | Usuario de PostgreSQL. |
| `SPRING_DATASOURCE_PASSWORD` | Contrasena de PostgreSQL. |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | Driver JDBC, normalmente `org.postgresql.Driver`. |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Estrategia de esquema, por ejemplo `update`. |
| `SPRING_JPA_SHOW_SQL` | `true` o `false` para imprimir SQL. |
| `SPRING_RABBITMQ_HOST` | Host RabbitMQ. |
| `SPRING_RABBITMQ_PORT` | Puerto RabbitMQ. |
| `SPRING_RABBITMQ_USERNAME` | Usuario RabbitMQ. |
| `SPRING_RABBITMQ_PASSWORD` | Contrasena RabbitMQ. |
| `SPRING_RABBITMQ_VIRTUAL_HOST` | Virtual host RabbitMQ, por ejemplo `/`. |
| `SPRING_RABBITMQ_SSL_ENABLED` | `true` o `false` para TLS con RabbitMQ. |
| `SERVER_PORT` o `PORT` | Puerto HTTP. `PORT` tiene prioridad si existe. |
| `LOGGING_LEVEL_ROOT` | Nivel de logs raiz, por ejemplo `INFO`. |
| `LOGGING_LEVEL_COM_LEALTAD` | Nivel de logs del paquete del servicio. |
| `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AMQP` | Nivel de logs AMQP/Spring Rabbit. |

`SPRING_JPA_OPEN_IN_VIEW` es opcional y usa `false` si no se define.

Ejemplo local de `.env`:

```dotenv
SPRING_APPLICATION_NAME=puntos-lealtad-service
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/puntos_lealtad
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
LOGGING_LEVEL_COM_LEALTAD=INFO
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AMQP=INFO
```

### CORS para clientes web

`CorsConfig` habilita CORS para todos los endpoints. Acepta patrones desde
`FRONTEND_CORS_PATTERNS` y origenes adicionales desde `FRONTEND_URL`, ambos como
listas separadas por comas.

Valores por defecto:

- Patrones: `https://*.vercel.app`, `https://*.up.railway.app`,
  `http://localhost:*`, `http://127.0.0.1:*`
- Origen adicional: `https://taller3-frontend-microservicios-production.up.railway.app`

Ejemplo para un frontend especifico:

```dotenv
FRONTEND_CORS_PATTERNS=https://*.vercel.app,http://localhost:*
FRONTEND_URL=https://mi-frontend.example.com
```

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

### La aplicacion no arranca por placeholders sin resolver

Si Spring reporta que no puede resolver una propiedad como
`${SPRING_DATASOURCE_URL}` o `${SPRING_RABBITMQ_HOST}`, falta definir una de las
variables requeridas por `application.yml`. En local, crea un `.env` en el
repositorio o en un directorio padre; en despliegues, configura las variables en
el proveedor. Las variables del sistema operativo tienen prioridad sobre `.env`.

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