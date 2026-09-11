# SaaS de reservas de pádel

Reservas online para las canchas de Necochea, pensado para clubes que hoy toman
todo por WhatsApp. Multi-tenant: una sola instancia atiende a varios clubes.

## Cómo correrlo

Hace falta Java 21, Maven y Node (lo usa Vaadin para armar el bundle del panel;
la primera corrida tarda unos minutos y después queda cacheado). No hace falta Docker ni instalar PostgreSQL: el perfil
`dev` levanta un PostgreSQL embebido real en el puerto 54329.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Arranca con tres clubes de ejemplo cargados:

- Buscar cancha en todos: `http://localhost:8080/buscar`
- App del jugador: `http://localhost:8080/club/club-necochea`
- Panel del club: `http://localhost:8080/admin` — `dueno@clubnecochea.test` / `padel1234`

Los otros dos son `costa-verde` y `el-muelle`, con horarios, duración de turno y
precios distintos. Cada uno tiene su dueño (`dueno@costaverde.test`,
`dueno@elmuelle.test`), todos con la misma clave.

El `mvn package -Pproduction` compila el bundle del jugador dentro del jar, así que
el artefacto final sirve las dos aplicaciones sin un servidor de estáticos aparte.
Para probar en desarrollo, ver más abajo.

WhatsApp está en stand by y apagado por defecto (`WHATSAPP_PROVIDER=off`): no manda
nada, ni siquiera al log. Para verlo simulado en consola, como antes, corré con
`WHATSAPP_PROVIDER=log`.

La base se crea vacía en cada arranque y los clubes de ejemplo se vuelven a cargar. Si
preferís tu PostgreSQL local, corré sin el perfil `dev` y definí `DB_URL`, `DB_USER`
y `DB_PASSWORD`.

```bash
mvn test
```

Los tests corren contra un PostgreSQL real y efímero. No es un capricho: la garantía
más importante del sistema es una restricción de exclusión de PostgreSQL, y contra
una base en memoria no existiría.

## Probarlo a mano

### Opción A: todo en un puerto

La app del jugador se sirve desde `target/classes/static`, que produce el build de
`player-app`. Después de un `mvn clean` hay que volver a generarlo, o `/club/…`
devuelve 404.

```bash
cd player-app && npm install && npm run build && cd .. && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Opción B: con recarga en caliente del frontend

Dos terminales. Vite sirve la app en el 5174 y delega `/api` en Spring.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev "-Dspring-boot.run.arguments=--app.base-url=http://localhost:5174"
```

```bash
cd player-app && npm run dev
```

El `app.base-url` no es opcional acá: con el valor por defecto, los links que viajan
por WhatsApp apuntan al 8080 y te sacan de la versión con recarga en caliente.

### El recorrido

| Dónde | Qué |
|---|---|
| `/` | Landing comercial para el dueño de club: mockups en HTML/CSS, sin datos reales |
| `/buscar` | Búsqueda en todos los clubes. Filtrá por día y rango horario, y tocá un turno |
| `/club/club-necochea` | Grilla del jugador. Reservá un turno con "Pagar en el club" — queda confirmado al toque, sin paso de WhatsApp |
| `/manage/{token}` | Portal del jugador: detalle y cancelación |
| `/admin` | Panel del club — `dueno@clubnecochea.test` / `padel1234` |

Cosas que vale la pena probar porque es donde están las reglas:

- **Reservar dos veces el mismo horario y cancha.** La segunda tiene que dar 409 y
  la grilla refrescarse sola.
- **Cancelar un turno de hoy a la tarde.** Con el límite de 12 horas, la app no te
  deja y te ofrece escribirle al club. Un turno de pasado mañana sí se cancela.
- **En el panel**, tocá un hueco de la agenda para cargar un turno que "entró por
  teléfono", y después cobralo en mostrador.
- **Turnos fijos**: creá uno y fijate que bloquee ese horario en la grilla pública.
- **Buscar "hoy a la noche"** en `/buscar` con la franja Noche: tienen que aparecer
  turnos de más de un club, mezclados por horario. Tocá uno y fijate que el club
  abre con ese turno ya elegido, en el paso de datos.

El pago con seña no se puede probar así: el club de ejemplo no tiene MercadoPago
cargado, y por eso la app solo ofrece "Pagar en el club". Para ejercitarlo hacen
falta credenciales de prueba de MercadoPago cargadas desde el panel, en
Configuración → Cobros online.

La base se recrea vacía en cada arranque, así que para volver a foja cero alcanza
con reiniciar.

## Decisiones que conviene conocer antes de tocar el código

**El doble booking lo impide la base, no la aplicación.** `booking` tiene una
restricción de exclusión GiST sobre `(cancha, rango horario)`. La validación en
memoria existe solo para darle al jugador un mensaje entendible; dos requests
simultáneos la pasan los dos y es la base la que rechaza al segundo. Si algún día
migra a otro motor, esto es lo primero que hay que resolver.

**El club se resuelve antes de abrir la transacción.** Hibernate fija el
identificador de tenant al crear la sesión, así que establecerlo más tarde no cambia
el filtro y las consultas salen vacías. Lo hace `TenantContextFilter`, que además
limpia el `ThreadLocal` al terminar cada request: Tomcat reutiliza los hilos, y sin
ese borrado un club terminaría viendo la agenda de otro.

**La búsqueda global recorre los clubes de a uno.** `/buscar` no puede resolverse con
una sola consulta: el filtro por club lo agrega Hibernate al abrir la sesión, así que
`CourtSearchService` itera los clubes activos y entra a cada uno con
`TenantContext.callAs`, delegando en el motor de disponibilidad de siempre. Por eso esa
clase no lleva `@Transactional`: con una transacción ya abierta, cambiar el club no
cambia el filtro y no vuelve nada. Cuesta unas cinco consultas por club, y se paga a
cambio de no tener dos implementaciones de las reglas de disponibilidad. El segmento
`search` está reservado en `TenantContextFilter` para que no se lea como el slug de un
club.

**Las horas de reloj de pared no llevan zona horaria.** Los instantes van en
`timestamptz`; los horarios de apertura y las franjas de tarifa van en `TIME` y se
guardan tal cual. Activar `hibernate.jdbc.time_zone` convierte también esas columnas
y corre los horarios del club (lo cubre `WallClockPersistenceTest`).

**Los WhatsApp salen después del commit.** Se publican como eventos y se envían en
`AFTER_COMMIT`. Si la reserva choca y se revierte, el jugador no recibe un aviso de
un turno que no existe.

**El token de MercadoPago viaja por request.** El estático global del SDK es de toda
la JVM: con dos clubes cobrando en paralelo terminaría acreditándole a uno la seña
del otro.

**El panel no usa el locale es-AR en los selectores de hora.** Con es-AR, el
TimePicker de Vaadin formatea en 12 horas con "a. m." y después no puede releer lo
que él mismo escribió: las 18:00 quedaban como 06:00 y un cierre 23:30 como 11:30.
El dueño abría la configuración, tocaba Guardar sin cambiar nada y le movía el
horario al club. Usa es-ES, que formatea en 24 horas.

**La raíz es del jugador, no del panel.** El link que el club comparte por WhatsApp
es la grilla pública, así que Vaadin se mapea bajo `/admin` y las rutas de la SPA
(`/club/…`, `/manage/…`, `/confirm/…`) las reenvía `SpaForwardingController` al
index. Sin ese reenvío, entrar directo a un link de WhatsApp o refrescar la página
daría 404 — y esos links son justamente los que recibe el jugador.

**Los tokens de cuenta se guardan como huella, no en claro.** El de sesión, el de
reseteo de contraseña y el del link que confirma un alta son credenciales: quien los
tenga entra como ese jugador. Se guarda su SHA-256, así que una copia de la base
—un backup mal guardado, un dump pedido para depurar algo— no alcanza para entrar
como nadie. SHA-256 y no bcrypt, que es lo que usan las contraseñas de al lado: estos
tokens son 32 bytes de `SecureRandom`, no algo que una persona elige, y la búsqueda
es por igualdad —una sal al azar obligaría a recorrer la tabla comparando de a una—.
Los tokens de reserva (`management_token` y sus hermanos) siguen en claro: viajan por
WhatsApp y hay que poder resolverlos desde la URL, y ese cambio es más grande. Lo
cubre `TokenStorageTest`.

**La identidad del jugador es la cuenta, no el teléfono.** El historial de
`/account` sale de `booking.player_account_id`, que se completa cuando la reserva
se hace con sesión iniciada. Antes cruzaba clubes emparejando el teléfono de la
cuenta con el del cliente, y el teléfono no lo verifica nadie: el alta lo acepta
tal cual y sólo se confirma el email. Alcanzaba con registrarse poniendo el número
de otro para recibir su agenda entera —con el `management_token` de cada turno, que
es con lo que se cancela—. El teléfono es un dato de contacto que cualquiera puede
escribir. Lo cubre `PlayerHistoryIsolationTest`.

**Que un club no vea los datos de otro está probado, no supuesto.**
`TenantIsolationTest` corre las llamadas de siempre con el club equivocado
instalado y verifica que no devuelvan una sola fila ajena, ni siquiera teniendo el
id exacto en la mano. Importa porque hay dos consultas nativas que esquivan el
filtro de Hibernate a propósito, y el test está para que la tercera no pase
inadvertida.

**Las visitas se miden en casa.** No hay Google Analytics ni nada parecido: el CSP
de la app del jugador sólo admite scripts del propio origen y la política de
privacidad promete que no hay rastreo de terceros. Además la conversión ya vive en
esta base, así que el embudo sale de un JOIN y no de una integración. Ver
[Estadísticas de visitas](#estadísticas-de-visitas).

**Los teléfonos argentinos se fuerzan a celular.** Escrito sin el 15 ni el 9,
`2262 415000` es indistinguible de una línea fija. Sin esa corrección, el mismo
jugador queda como dos clientes según cómo haya tipeado, con su historial y su marca
de confianza partidos.

## Estadísticas de visitas

La app del jugador anota el recorrido de cada visitante en `page_event`. Hasta acá
el sistema sabía contar reservas —el final del recorrido— y nada de lo que pasa
antes: quién entró, qué buscó, en qué pantalla se fue.

La app es una SPA, así que los logs del servidor no sirven para esto: ve una sola
petición por visita y no se entera de las rutas que el jugador recorre después.

### Qué se registra

| Evento | Cuándo | Lo propio que guarda |
|---|---|---|
| `VIEW` | Cada cambio de ruta | La ruta, el club si lo hay |
| `SEARCH` | Cada búsqueda global | Día, franja, clubes filtrados y cuántos turnos volvieron |
| `SEARCH_RESULT_CLICK` | Toca un resultado | Club de destino, horario y en qué lugar de la lista estaba |
| `CLUB_STEP` | Avanza en el flujo de reserva | Paso (1 día, 2 hora, 3 datos) |
| `SLOT_CLICK` | Elige un horario | El horario |
| `CHECKOUT_SUBMIT` | Manda el formulario | Forma de pago |
| `BOOKING_CREATED` | Reservó | Id de la reserva real |
| `BOOKING_FAILED` | El checkout falló | Código del error |
| `LINK_EXPIRED` | Vino de la búsqueda y el turno ya estaba tomado | Día y hora que se perdió |
| `WAITLIST_JOINED` | Se anotó en un horario lleno | El horario |

No hay evento de "no reservó": es la ausencia de `BOOKING_CREATED` en la sesión.
Nadie avisa que se va de una página. Lo que sí se separa es el abandono de
`BOOKING_FAILED`, `LINK_EXPIRED` y `WAITLIST_JOINED`, que son tres formas de
"quiso y no pudo": mezclados dan una conversión pesimista y sin diagnóstico.

### Qué no se guarda

La sesión es un UUID anónimo en `sessionStorage` que muere al cerrar la pestaña:
une los pasos de un recorrido, no reconoce a nadie entre visitas. Sin cookies, sin
terceros y sin IP. Las rutas se guardan normalizadas (`/manage/:token`), porque ese
token es la credencial del turno. La bitácora se borra sola a los 12 meses
(`PageEventRetentionJob`), y `ANALYTICS_ENABLED=false` apaga el registro sin tocar
la app.

### Preguntarle a la tabla

El embudo de un club, último mes:

```sql
SELECT count(DISTINCT session_id) FILTER (WHERE name = 'VIEW')            AS entraron,
       count(DISTINCT session_id) FILTER (WHERE name = 'SLOT_CLICK')      AS eligieron_turno,
       count(DISTINCT session_id) FILTER (WHERE name = 'CHECKOUT_SUBMIT') AS cargaron_datos,
       count(DISTINCT session_id) FILTER (WHERE name = 'BOOKING_CREATED') AS reservaron
FROM page_event
WHERE club_id = (SELECT id FROM tenant WHERE slug = 'club-necochea')
  AND created_at > now() - interval '30 days';
```

De dónde llegaron los que entraron a cada club, y cuántos de cada origen
reservaron:

```sql
WITH entrada AS (
    SELECT DISTINCT ON (session_id) session_id, path AS ruta_de_entrada, referrer_host
    FROM page_event
    ORDER BY session_id, seq
),
visita_club AS (
    SELECT session_id,
           club_id,
           bool_or(name = 'SEARCH_RESULT_CLICK') AS toco_un_resultado,
           bool_or(from_search)                  AS link_con_horario,
           bool_or(name = 'BOOKING_CREATED')     AS reservo
    FROM page_event
    WHERE club_id IS NOT NULL AND created_at > now() - interval '30 days'
    GROUP BY session_id, club_id
)
SELECT t.slug,
       CASE
           WHEN v.toco_un_resultado OR v.link_con_horario THEN 'búsqueda global'
           WHEN e.ruta_de_entrada = '/club/:slug'         THEN 'link directo del club'
           ELSE 'entró por la portada'
       END                                   AS origen,
       coalesce(e.referrer_host, 'directo')  AS de_donde,
       count(*)                              AS visitas,
       count(*) FILTER (WHERE v.reservo)     AS reservaron
FROM visita_club v
JOIN entrada e USING (session_id)
JOIN tenant t ON t.id = v.club_id
GROUP BY t.slug, origen, de_donde
ORDER BY visitas DESC;
```

Búsquedas que no encontraron nada: demanda que hoy se pierde, y el argumento para
sumar al club que falta.

```sql
SELECT search_date, time_from, time_to, count(*) AS busquedas
FROM page_event
WHERE name = 'SEARCH' AND results = 0 AND created_at > now() - interval '30 days'
GROUP BY search_date, time_from, time_to
ORDER BY busquedas DESC
LIMIT 20;
```

Y el cruce que ningún servicio externo puede hacer, porque la reserva vive acá: de
los que llegaron por la búsqueda global, cuántos terminaron pagando.

```sql
SELECT e.from_search, b.status, count(*)
FROM page_event e
JOIN booking b ON b.id = e.booking_id
WHERE e.name = 'BOOKING_CREATED'
GROUP BY e.from_search, b.status;
```

## Estado

Las tres piezas funcionando de punta a punta. 226 tests.

- Motor de disponibilidad, precios por franja y por cancha
- Reserva con seña (MercadoPago) y de palabra, confirmada al instante por defecto
  (o por WhatsApp si el club lo pide y WhatsApp está activo — hoy en stand by)
- Cancelación con ventana horaria y alerta de devolución manual
- Turnos fijos con horizonte móvil y excepciones por semana
- Vencimientos automáticos y cierre de turnos jugados (también al cobrar el saldo
  completo en el mostrador)
- Webhook de MercadoPago con validación de firma e idempotencia
- Panel del club en Vaadin 25: agenda de canchas por horario con carga manual de
  turnos, cobro en mostrador, ausentes y bajas; jugadores con marca de confianza;
  turnos fijos; alertas; configuración de horarios, tarifas y cobros; usuario de
  mostrador con acceso limitado (sin cobros online ni estadísticas)
- App del jugador en React: grilla por horario con precios, checkout de dos campos,
  cuenta con email/contraseña o Google, portal de gestión con cancelación, y
  Términos de uso / Política de privacidad
- Búsqueda de canchas entre todos los clubes por día y rango horario, con el turno
  elegido preseleccionado al entrar al club

Lo que falta para salir a producción no es código: número de WhatsApp habilitado,
plantillas aprobadas por Meta y las credenciales de MercadoPago de cada club.

## Configuración de producción

| Variable | Para qué |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL |
| `APP_BASE_URL` | URL pública; arma los links de WhatsApp y el retorno de MercadoPago |
| `APP_ENCRYPTION_KEY` | AES en Base64 (`openssl rand -base64 32`). Cifra los tokens de MercadoPago. **Si se pierde, quedan ilegibles.** |
| `MAIL_PROVIDER` | **Obligatoria** (sin default): `smtp` para mandar de verdad, o `log` para verlo en consola. Sin definirla la app no arranca — a propósito: los emails de esta app llevan links de reset de contraseña y verificación, y esos no pueden imprimirse en el log de un despliegue real. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `MAIL_FROM` | Credenciales del proveedor SMTP, si `MAIL_PROVIDER=smtp` |
| `WHATSAPP_PROVIDER` | `off` (default, no manda nada — WhatsApp está en stand by), `log` (desarrollo, lo imprime) o `twilio` (lo manda de verdad) |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `WHATSAPP_FROM` | Credenciales del proveedor |
| `app.whatsapp.templates.*` | Content SID de cada plantilla aprobada por Meta |

Sobre las plantillas: WhatsApp solo permite texto libre dentro de las 24 horas
posteriores a un mensaje del jugador. Todos los avisos de este sistema los inicia el
negocio, así que **sin plantillas aprobadas no llegan**. Cargarlas es parte del alta
en producción, no un detalle opcional.

Cada club necesita además, desde su panel, su access token de producción de
MercadoPago y la clave secreta de webhooks. La URL a configurar en MercadoPago es
`{APP_BASE_URL}/api/webhooks/mercadopago/{slug}`.

### Desplegar con Docker

El `Dockerfile` es multi-stage: compila con Maven (perfil `production`, incluye
el bundle de Vaadin y el build de `player-app`) y corre con solo un JRE. Sirve
para cualquier PaaS que construya directo desde un `Dockerfile` (Railway,
Render, Fly.io) — no tiene nada específico de ninguno, las variables de
entorno se cargan desde el dashboard del proveedor elegido.

Para probar la imagen en local antes de desplegarla (`docker-compose.yml`
levanta un Postgres descartable al lado):

```bash
APP_ENCRYPTION_KEY=$(openssl rand -base64 32) docker compose up --build
```

### Backups

El PostgreSQL administrado del proveedor elegido normalmente incluye backups
automáticos — confirmar que están activos para el plan que se contrate, la
retención varía. Como escape manual:

```bash
# Backup
pg_dump "$DB_URL" > backup-$(date +%F).sql

# Restore
psql "$DB_URL" < backup-2026-09-08.sql
```
