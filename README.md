# Smart Campus Request Router

Solución de integración basada en mensajería que recibe solicitudes estudiantiles
desde una cola común, las transforma a un formato canónico interno y las enruta
hacia distintas colas según el tipo de solicitud. Implementa los patrones
**Message Translator** y **Content-Based Router** con **Apache Camel** y
**RabbitMQ**.

Taller Semana 11 — Integración de Sistemas (UDLA).

## Integrantes del grupo

- (Completar) — Arquitecto de integración
- (Completar) — Desarrollador Camel
- (Completar) — Responsable RabbitMQ
- (Completar) — Responsable de documentación

## Descripción del problema de integración

Todas las solicitudes (formulario web, app móvil, plataforma administrativa)
llegan inicialmente a la cola `campus.requests.in` en un **formato externo**.
No todas deben ser procesadas por el mismo sistema: según el campo `request_type`,
cada mensaje debe llegar a una cola distinta. Además, el formato externo no
coincide con el **formato interno** que la institución desea usar. Por eso la
solución debe: recibir, transformar a un modelo canónico, enrutar por contenido
y enviar a revisión manual lo no reconocido o inválido.

## Diagrama del flujo

```
campus.requests.in
        |
        v
[ Message Translator ]   (CanonicalRequestTranslator)
        |
        v
[ Content-Based Router ] (choice() según "type")
        |
        |--> campus.admissions.queue   (ADMISSION)
        |--> campus.payments.queue     (PAYMENT)
        |--> campus.support.queue      (SUPPORT)
        |--> campus.academic.queue     (ACADEMIC)
        |--> campus.manual-review.queue (otro / inválido)
```

## Tecnologías utilizadas

- Java 17
- Spring Boot 3.3.5
- Apache Camel 4.8.1 (`camel-spring-rabbitmq`, `camel-jackson`)
- RabbitMQ 3 (imagen `rabbitmq:3-management`)
- Docker / Docker Compose
- Maven

## Estructura del proyecto

```
smart-campus-request-router/
 ├── pom.xml
 ├── docker-compose.yml
 ├── Start.bat                       # arranque automatico en Windows (1 clic)
 ├── README.md
 ├── docs/
 │   └── diagrama_flujo.png
 ├── scripts/
 │   ├── setup-rabbitmq.sh           # exchange/colas/bindings (bash + curl)
 │   ├── publish-messages.sh         # publicador (bash + jq)
 │   └── publish-messages.ps1        # publicador (PowerShell, sin jq)
 └── src/
     └── main/
         ├── java/ec/udla/integracion/campus/
         │   ├── SmartCampusApplication.java     # arranque Spring Boot
         │   ├── CampusRequestRoute.java         # ruta Camel (Content-Based Router)
         │   ├── CanonicalRequestTranslator.java # Message Translator
         │   ├── CampusChannels.java             # nombres de exchange/colas + URIs
         │   └── model/
         │       ├── CanonicalRequest.java       # modelo canonico (record)
         │       └── RequestType.java            # tipos reconocidos -> cola destino
         └── resources/
             └── application.properties
```

### Diseño de las clases

- `CanonicalRequest` (record): modelo canónico tipado; su orden de campos define el
  JSON de salida. Anida `Student`.
- `RequestType` (enum): único lugar donde vive el mapeo `tipo → cola destino`. Tanto
  el router como (potencialmente) nuevos tipos se apoyan en él.
- `CampusChannels`: centraliza el nombre del exchange, las colas y la construcción de
  las URIs de Camel, evitando *strings* mágicos repartidos por la ruta.
- `CanonicalRequestTranslator`: valida los campos obligatorios y traduce; si falta
  algún campo, marca el mensaje como `INVALID` indicando **qué** campos faltan.

## Arranque rápido en Windows (1 clic)

Quien solo quiera ver la solución funcionando puede ejecutar el script `Start.bat`
(doble clic o desde una terminal):

```bat
Start.bat
```

`Start.bat` automatiza todo: verifica las herramientas (Docker, Java 17, Maven) e
intenta instalarlas con `winget` si faltan, asegura que el motor de Docker esté
activo, levanta RabbitMQ, crea el exchange/colas/bindings con `curl`, compila el
proyecto y arranca la aplicación. No requiere `bash` ni `jq`. Luego, en **otra**
terminal, se publican los mensajes de prueba:

```bat
powershell -ExecutionPolicy Bypass -File scripts\publish-messages.ps1
```

Los pasos manuales equivalentes (multiplataforma) se describen a continuación.

## 1. Ejecutar RabbitMQ

```bash
docker compose up -d
docker ps
```

Consola de administración: http://localhost:15672 (usuario `guest`, contraseña `guest`).

## 2. Configurar exchange, colas y bindings

```bash
chmod +x scripts/setup-rabbitmq.sh
./scripts/setup-rabbitmq.sh
```

Crea el exchange directo `campus.exchange`, las seis colas y un binding por cada
cola cuya routing key es igual al nombre de la cola.

## 3. Ejecutar la aplicación

El `pom.xml` genera un **JAR ejecutable** (fat jar), por lo que la forma recomendada
y más portable de arrancar es:

```bash
mvn clean package
java -jar target/smart-campus-request-router.jar
```

Alternativa con el plugin de Maven:

```bash
mvn spring-boot:run
```

> Nota: si la ruta absoluta del proyecto contiene espacios o acentos, `mvn spring-boot:run`
> puede fallar al construir el classpath del proceso hijo en Windows. En ese caso usar
> `java -jar` (recibe el JAR como un único argumento y no se ve afectado).

La ruta de Camel queda escuchando en `campus.requests.in`. No cerrar esta terminal
durante las pruebas.

## 4. Publicar mensajes de prueba

Con `jq` instalado, en una segunda terminal:

```bash
chmod +x scripts/publish-messages.sh
./scripts/publish-messages.sh
```

En Windows, sin `jq` ni `bash`, usar el publicador en PowerShell:

```bat
powershell -ExecutionPolicy Bypass -File scripts\publish-messages.ps1
```

O publicar manualmente desde RabbitMQ Management UI:
Exchanges → `campus.exchange` → Publish message → routing key `campus.requests.in`
→ pegar el JSON en Payload → Publish.

## Tabla de reglas de enrutamiento

| Valor de `request_type` | Cola destino                  |
|-------------------------|-------------------------------|
| ADMISSION               | campus.admissions.queue       |
| PAYMENT                 | campus.payments.queue         |
| SUPPORT                 | campus.support.queue          |
| ACADEMIC                | campus.academic.queue         |
| Otro valor              | campus.manual-review.queue    |
| Mensaje inválido        | campus.manual-review.queue    |

## Message Translator

Patrón implementado en `CanonicalRequestTranslator` (invocado con
`.process(canonicalRequestTranslator)`). Convierte el mensaje externo al modelo
canónico interno:

| Campo original     | Campo canónico        |
|--------------------|-----------------------|
| request_id         | requestId             |
| student_name       | student.fullName      |
| student_document   | student.document      |
| request_type       | type                  |
| channel            | sourceChannel         |
| created_at         | createdAt             |

Si faltan campos obligatorios, el mensaje se marca como `INVALID` y se conserva el
original para diagnóstico.

## Content-Based Router

Patrón implementado en `CampusRequestRoute` mediante `choice() / when() / otherwise()`,
usando la propiedad `requestType` del Exchange como criterio de decisión. El
productor solo conoce la cola de entrada; la lógica de enrutamiento vive en la ruta
de integración.

## Modelo canónico

Formato interno común que desacopla a los productores de los consumidores. Ejemplo:

```json
{
  "requestId": "REQ-1001",
  "student": { "fullName": "Ana Pérez", "document": "1712345678" },
  "type": "ADMISSION",
  "sourceChannel": "web",
  "createdAt": "2026-06-10T10:30:00"
}
```

## Investigación previa

### Content-Based Router
1. Resuelve enviar cada mensaje al destino correcto sin que el productor decida.
2. El productor no debe conocer todos los destinos para evitar acoplamiento; si
   cambian las colas, no hay que modificar a los emisores.
3. Criterio de decisión: el campo `type` (derivado de `request_type`).

### Message Translator
1. Resuelve la incompatibilidad de formatos entre sistemas.
2. Cada sistema modela la misma información de forma distinta por razones históricas,
   tecnológicas o de diseño.
3. Transformación concreta: del formato externo (`request_id`, `student_name`, …)
   al modelo canónico (`requestId`, `student.fullName`, …).

### Canonical Data Model
1. Es un formato interno común que todos los sistemas entienden.
2. Reduce el acoplamiento: cada sistema se traduce hacia/desde el canónico, no
   hacia cada contraparte (evita el crecimiento N×N de transformaciones).
3. El modelo canónico de este taller es el JSON con `requestId`, `student`,
   `type`, `sourceChannel` y `createdAt`.

### RabbitMQ
1. Una cola es un buffer que almacena mensajes hasta que un consumidor los procesa.
2. El exchange recibe los mensajes y, según la routing key y los bindings, decide a
   qué cola(s) van; la cola los almacena.
3. Se verifica en la pestaña Queues and Streams de la Management UI, revisando el
   contador de mensajes y con "Get messages".

## Soportar un nuevo tipo (SCHOLARSHIP)

Actualmente `SCHOLARSHIP` cae en `otherwise()` → `campus.manual-review.queue`. Gracias
a que el mapeo `tipo → cola` vive en el enum `RequestType`, soportarlo formalmente es
un cambio acotado:
1. Crear la cola `campus.scholarship.queue` y su binding en RabbitMQ (routing key igual
   al nombre de la cola).
2. Agregar la constante `SCHOLARSHIP("campus.scholarship.queue")` en `RequestType`.
3. Agregar la rama correspondiente en el Content-Based Router de `CampusRequestRoute`
   (`.when(routedAs(RequestType.SCHOLARSHIP))`).
4. Agregar un mensaje de prueba de tipo `SCHOLARSHIP`.

## Apagar la solución

```bash
# Detener la aplicación: CTRL + C
docker compose down       # apagar RabbitMQ
docker compose down -v    # apagar y borrar datos persistidos
```

## Problemas encontrados y reflexión técnica

Ver el informe técnico (documento Word) con las evidencias, los problemas
encontrados y las respuestas a las preguntas de reflexión obligatorias.
