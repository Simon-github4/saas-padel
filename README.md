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

Arranca con un club de ejemplo cargado:

- App del jugador: `http://localhost:8080/club/club-necochea`
- Panel del club: `http://localhost:8080/admin` — `dueno@clubnecochea.test` / `padel1234`

La app del jugador se sirve desde `target/classes/static`, que produce el build de
`player-app`. Para trabajar sobre ella con recarga en caliente:

```bash
cd player-app && npm install && npm run dev
```

Vite queda en el 5173 y delega `/api` en Spring. El `mvn package -Pproduction`
compila el bundle del jugador dentro del jar, así que el artefacto final sirve las
dos aplicaciones sin necesidad de un servidor de estáticos aparte.

Los WhatsApp no se envían: el adaptador de desarrollo los escribe en la consola con
los links completos, listos para pegar en el navegador.

La base se crea vacía en cada arranque y el club de ejemplo se vuelve a cargar. Si
preferís tu PostgreSQL local, corré sin el perfil `dev` y definí `DB_URL`, `DB_USER`
y `DB_PASSWORD`.

```bash
mvn test
```

Los tests corren contra un PostgreSQL real y efímero. No es un capricho: la garantía
más importante del sistema es una restricción de exclusión de PostgreSQL, y contra
una base en memoria no existiría.

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

**Los teléfonos argentinos se fuerzan a celular.** Escrito sin el 15 ni el 9,
`2262 415000` es indistinguible de una línea fija. Sin esa corrección, el mismo
jugador queda como dos clientes según cómo haya tipeado, con su historial y su marca
de confianza partidos.

## Estado

Las tres piezas funcionando de punta a punta. 86 tests.

- Motor de disponibilidad, precios por franja y por cancha
- Reserva con seña (MercadoPago) y de palabra (confirmación por WhatsApp)
- Cancelación con ventana horaria y alerta de devolución manual
- Turnos fijos con horizonte móvil y excepciones por semana
- Vencimientos automáticos y cierre de turnos jugados
- Webhook de MercadoPago con validación de firma e idempotencia
- Panel del club en Vaadin 25: agenda de canchas por horario con carga manual de
  turnos, cobro en mostrador, ausentes y bajas; jugadores con marca de confianza;
  turnos fijos; alertas; configuración de horarios, tarifas y cobros
- App del jugador en React: grilla por horario con precios, checkout de dos campos,
  confirmación desde el link de WhatsApp y portal de gestión con cancelación

Lo que falta para salir a producción no es código: número de WhatsApp habilitado,
plantillas aprobadas por Meta y las credenciales de MercadoPago de cada club.

## Configuración de producción

| Variable | Para qué |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL |
| `APP_BASE_URL` | URL pública; arma los links de WhatsApp y el retorno de MercadoPago |
| `APP_ENCRYPTION_KEY` | AES en Base64 (`openssl rand -base64 32`). Cifra los tokens de MercadoPago. **Si se pierde, quedan ilegibles.** |
| `WHATSAPP_PROVIDER` | `twilio` en producción, `log` en desarrollo |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `WHATSAPP_FROM` | Credenciales del proveedor |
| `app.whatsapp.templates.*` | Content SID de cada plantilla aprobada por Meta |

Sobre las plantillas: WhatsApp solo permite texto libre dentro de las 24 horas
posteriores a un mensaje del jugador. Todos los avisos de este sistema los inicia el
negocio, así que **sin plantillas aprobadas no llegan**. Cargarlas es parte del alta
en producción, no un detalle opcional.

Cada club necesita además, desde su panel, su access token de producción de
MercadoPago y la clave secreta de webhooks. La URL a configurar en MercadoPago es
`{APP_BASE_URL}/api/webhooks/mercadopago/{slug}`.
