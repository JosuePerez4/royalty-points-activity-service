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

`PuntosResponse` expone los campos `clienteId`, `nombre`, `puntos`,
`nivelLealtad`, `estado` y `fechaCreacion`. Aunque la entidad persiste
`fecha_ultima_actualizacion`, ese campo no forma parte de las respuestas HTTP.
Las fechas se serializan como texto ISO-8601 porque `JacksonConfig` registra
`JavaTimeModule` y desactiva los timestamps numericos.

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

### Formato de errores

`GlobalExceptionHandler` devuelve siempre un `ErrorResponse` con:

| Campo | Descripcion |
| --- | --- |
| `codigo` | Codigo HTTP numerico |
| `mensaje` | Detalle del error o mensaje generico |
| `timestamp` | Momento en que se construyo la respuesta |
| `path` | URI solicitada |

Codigos esperados:

| Codigo | Caso |
| --- | --- |
| `400` | Argumentos invalidos, como cantidades nulas, cero, negativas o redenciones superiores al saldo |
| `404` | No existe registro para el `clienteId` solicitado |
| `500` | Error no controlado; el mensaje expuesto es siempre `Error interno del servidor` |

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

### Operacion del consumidor

`EventoListener` consume mensajes con `@RabbitListener` sobre
`puntos-lealtad-queue`. El listener captura tanto `IllegalArgumentException`
como errores inesperados, registra el problema y no relanza la excepcion. En la
configuracion actual no hay DLQ ni politica de reintentos declarada en
`RabbitMQConfig`; si un evento falla por validacion o por error interno, la
evidencia operativa queda en los logs de la aplicacion.

Para investigar eventos que no generan registros, revisa primero:

1. Que el mensaje llegue a `puntos-lealtad-queue` con routing key
   `cliente.creado`.
2. Que el JSON pueda mapearse a `ClienteCreadoEvent`.
3. Que no exista ya un registro con el mismo `cliente_id`.
4. Los logs de `EventoListener` y `ClientePuntosService`, configurados con
   nivel `DEBUG` para `com.lealtad` en `application.yml`.

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

- Java 17
- Maven Wrapper incluido (`./mvnw`)
- PostgreSQL accesible
- RabbitMQ accesible en `localhost:5672` o configurado mediante propiedades

Configuracion por defecto:

- Aplicacion: `puntos-lealtad-service`
- Puerto HTTP: `8081`
- RabbitMQ: host `localhost`, puerto `5672`, usuario `guest`
- Perfil adicional disponible: `application-dev.yml`

Propiedades principales:

| Propiedad | Uso |
| --- | --- |
| `server.port` | Puerto HTTP del servicio |
| `spring.datasource.*` | Conexion PostgreSQL usada por JPA |
| `spring.jpa.hibernate.ddl-auto` | Modo de actualizacion de esquema de Hibernate |
| `spring.rabbitmq.*` | Conexion al broker RabbitMQ |
| `logging.level.com.lealtad` | Verbosidad de logs del codigo de la aplicacion |
| `logging.level.org.springframework.amqp` | Verbosidad de logs AMQP |

Para activar el perfil `dev`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Tambien se puede usar una variable de entorno:

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

En los archivos actuales, el perfil `dev` mantiene la misma configuracion de
datasource y cambia `spring.jpa.show-sql` a `false`.

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

Ejemplos de overrides por variable de entorno:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/lealtad \
SPRING_DATASOURCE_USERNAME=lealtad \
SPRING_DATASOURCE_PASSWORD=lealtad \
SPRING_RABBITMQ_HOST=localhost \
./mvnw spring-boot:run
```

## Desarrollo y pruebas

El artefacto Maven es `puntos-lealtad-service` y usa Spring Boot 3.2.5 con
Java 17. La suite actual contiene pruebas unitarias de `ClientePuntosService`
con repositorio mockeado (`ClientePuntosServiceTest`).

Las pruebas cubren reglas de negocio como creacion inicial, duplicados,
consulta, acumulacion, redencion, cambios de nivel y listado. No hay pruebas de
controlador HTTP, listener RabbitMQ ni integracion con PostgreSQL en el arbol
actual del repositorio.

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