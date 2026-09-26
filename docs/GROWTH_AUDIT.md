# Growth Audit de TurnosPadel

**Fecha:** 25 de septiembre de 2026  
**Alcance:** producto, UX, onboarding y conversión del SaaS B2B para clubes de pádel.  
**Objetivo de negocio:** maximizar clubes que publican disponibilidad y reciben reservas reales; no aumentar el inventario de funciones sin una relación clara con activación, conversión o retención.

## Resumen ejecutivo

TurnosPadel ya tiene un producto operativo considerablemente más maduro que su proceso comercial. El núcleo que un club necesita para dejar de administrar turnos por WhatsApp está resuelto: agenda multiusuario, reservas públicas sin cuenta obligatoria, prevención robusta de dobles reservas, tarifas, caja, señas con Mercado Pago, lista de espera, notificaciones, páginas públicas por club, buscador y una experiencia de jugador pensada para mobile.

La principal fuga no está hoy en el motor de reservas. Está antes y después:

- no existe un registro B2B autoservicio para el dueño del club;
- el alta de un tenant y su usuario depende de intervención manual;
- no hay un onboarding guiado que lleve de “entré por primera vez” a “mi club está listo y publicado”;
- trial, suscripción y estado comercial no existen como conceptos de dominio: aparecen principalmente como promesas de marketing;
- el producto no registra de forma autoritativa cuándo un club recibió su primera reserva web real;
- los contactos de la landing salen a WhatsApp o email sin convertirse en leads medibles y recuperables;
- faltan comunicaciones de ciclo de vida para rescatar altas, configuraciones y trials abandonados.

Esto produce un embudo con un buen producto al final, pero con pasos manuales e invisibles al principio. La prioridad debería ser construir un camino mínimo y medible desde el CTA de la landing hasta la primera reserva, y recién después optimizar adquisición o sumar mecanismos virales.

La landing y la experiencia pública mobile fueron contrastadas también con producción. La propuesta se entiende, el demo `/club/simon` funciona, el buscador devuelve clubes y disponibilidad reales, el precio es visible y la interfaz mobile pública es sólida. La revisión del panel autenticado mobile es principalmente sobre el código, porque no se utilizaron credenciales de un club productivo.

## Evento principal de activación

El evento de activación recomendado es:

> **`FIRST_REAL_WEB_BOOKING_CONFIRMED`: primera reserva real, originada por el canal web, que alcanza un estado confirmado para un club que no es demo.**

Debe cumplirse todo lo siguiente:

- `booking.source = WEB`;
- el tenant no es demo, interno ni de prueba;
- la reserva no fue creada desde administración ni por una recurrencia;
- no es un borrador pendiente de Mercado Pago;
- llega a un estado confirmado, ya sea sin pago online o con seña acreditada;
- se emite una sola vez por tenant y se persiste `activated_at`.

Es el mejor indicador porque demuestra, en un solo hecho, que el club:

1. configuró al menos una cancha, disponibilidad y precio utilizables;
2. publicó o compartió su link;
3. recibió tráfico de un jugador;
4. permitió completar el flujo público;
5. obtuvo el valor central prometido: una reserva sin gestionarla manualmente por WhatsApp.

`club_published` es un hito de setup, no activación. `mercado_pago_connected` es adopción de una capacidad valiosa, pero no debe bloquear la activación porque TurnosPadel admite legítimamente reservas sin seña online. `BOOKING_CREATED` tampoco alcanza: actualmente puede emitirse en el navegador antes de que un pago transforme el borrador en una reserva confirmada.

Métricas norte sugeridas:

- **métrica primaria:** porcentaje de clubes registrados que alcanzan `FIRST_REAL_WEB_BOOKING_CONFIRMED` dentro de los primeros 7 días;
- **velocidad:** mediana de horas desde `club_signup_completed` hasta activación;
- **calidad:** porcentaje de clubes activados que recibe una segunda reserva web dentro de 14 días;
- **resultado comercial:** conversión de club activado a cliente pago.

---

## 1. Funnel actual

### 1.1 Embudo B2B observado

| Etapa objetivo | Experiencia actual | Medición actual | Fuga principal |
|---|---|---|---|
| Visitante | Landing pública clara, demo, explicación de Mercado Pago, precio y prueba de 7 días | `VIEW` genérico en `page_event` | No se distinguen secciones, CTAs, demo, pricing ni intención comercial |
| Club interesado | CTAs hacia WhatsApp y `mailto:` | No hay entidad lead ni evento comercial específico | El visitante abandona el sitio; su atribución y seguimiento quedan fuera del producto |
| Registro | No hay alta B2B pública | No existe | El equipo debe crear club y credenciales manualmente |
| Configura club | El usuario entra a Configuración y recorre pestañas de Club, Web Reservas, Canchas, Tarifas, Productos, Cobros online y Usuarios | No hay hitos de onboarding | El producto presupone que el usuario sabe qué configurar y en qué orden |
| Publica canchas | Un tenant activo es accesible públicamente; el encabezado permite copiar el link | No hay estado `ready`/`published` ni evento de publicación | No existe validación de preparación ni una acción explícita de publicar |
| Recibe primera reserva | El flujo público es fuerte; admite sin cuenta, con o sin seña, y evita solapamientos | Hay eventos de navegador y eventos internos de booking, pero no activación persistida | `BOOKING_CREATED` puede representar un borrador previo al pago y no el valor real obtenido |
| Termina trial | La landing promete 7 días gratis, sin tarjeta | No hay fechas ni estados de trial en `Tenant` | No se puede automatizar vencimiento, avisos, extensión, conversión ni cohortes |
| Cliente pago | El precio está publicado y el pago mensual parece administrarse por fuera | No hay suscripción, plan, factura ni registro de pago SaaS | Conversión y churn quedan fuera del sistema y no se pueden correlacionar con uso |

### 1.2 Embudo del jugador

El flujo público está mejor resuelto:

`landing/buscador/link del club → fecha y horario → selección de cancha → datos mínimos → confirmación o Mercado Pago → reserva`

Fortalezas observadas:

- no obliga a crear cuenta para reservar;
- muestra disponibilidad y precio antes de pedir datos;
- ofrece buscador global, páginas públicas por club y URLs compartibles;
- dispone de acciones útiles cuando no hay disponibilidad;
- soporta PWA, cuenta opcional, Google login y recuperación de contraseña;
- el demo público funciona con canchas, horarios y precio visibles;
- existe lista de espera y mecanismos de confirmación/notificación;
- las reglas de base de datos protegen contra dobles reservas.

La principal limitación de este subfunnel no es UX básica, sino la falta de enlace con el embudo B2B: no se atribuye de manera completa qué acción del club originó el tráfico ni se usa la primera reserva confirmada para activar y acompañar al club.

### 1.3 Estado real del producto

El producto resuelve hoy:

- agenda de reservas web, manuales y recurrentes;
- configuración de canchas, horarios y tarifas;
- página pública personalizable por club;
- pagos de señas con Mercado Pago y webhooks;
- caja, productos/buffet, estadísticas y clientes;
- bloqueos, suspensiones, alertas, lista de espera y roles;
- recordatorios y mensajes de estado mediante una capa de notificaciones;
- experiencia pública mobile y buscador de clubes.

El proceso comercial resuelve hoy de forma manual:

- captación y calificación del lead;
- creación del club;
- entrega o recuperación de credenciales;
- acompañamiento durante el trial;
- cobro de la suscripción;
- detección de abandono y reactivación.

---

## 2. Problemas encontrados

### 2.1 Adquisición, landing y CTAs

**Lo que funciona**

- La propuesta “reservas online para tu club” está alineada con el problema real.
- Se explican agenda, Mercado Pago, link público, trial y precio.
- El demo es real y navegable; no es una captura estática.
- La landing responde bien en viewport mobile.
- El precio único de ARS 35.000/mes reduce ambigüedad inicial.

**Problemas**

1. **Los CTAs principales no comienzan un alta medible.** “Hablemos” y “Quiero mi prueba gratis” derivan a WhatsApp. Eso puede convertir en venta asistida, pero no deja un lead propio ni permite calcular conversión por fuente.
2. **No hay formulario de contacto/demo dentro del sitio.** Si WhatsApp no abre, el usuario no envía el mensaje o el equipo responde tarde, la intención se pierde.
3. **No hay prueba social suficiente.** Producción ya tiene clubes reales y reservas disponibles, pero la landing no usa logos, testimonios, volumen de reservas ni casos concretos.
4. **La confianza B2B es mejorable.** El contacto usa una cuenta Gmail personal y faltan datos comerciales claros, alcance del soporte, tratamiento de IVA, cancelación y condiciones del servicio B2B.
5. **La landing no instrumenta intención.** Visitar pricing, abrir el demo, tocar WhatsApp, copiar un dato o iniciar contacto quedan indistinguibles de una simple vista.
6. **El módulo de gimnasio amplía la propuesta sin precio ni recorrido comercial claro.** Puede distraer del trabajo principal de vender reservas de pádel si no se segmenta.

**Impacto:** no se conoce qué canal trae clubes, qué argumento genera intención ni dónde se pierden. El equipo depende de memoria y conversaciones manuales para vender.

### 2.2 Registro, login y recuperación

1. **No existe registro para clubes.** El alta pública existente corresponde a jugadores, no a dueños o administradores.
2. **El aprovisionamiento de tenant/owner no es autoservicio.** En el repositorio no se encontró un flujo productivo de creación de club equivalente al alta de jugador; el seeding disponible es de desarrollo.
3. **“Olvidé mi clave” del administrador no restablece la contraseña.** La pantalla indica contactar soporte. Esto genera una incidencia humana exactamente cuando el usuario quiere operar el negocio.
4. **No hay invitación segura para nuevos operadores.** Existe administración de usuarios, pero falta un ciclo completo de invitación, aceptación, expiración y definición de contraseña.
5. **No hay términos B2B explícitos asociados al alta.** Los términos visibles están más orientados a la relación con jugadores.

**Impacto:** cada club nuevo consume tiempo de soporte, el embudo no puede escalar y el acceso se convierte en un punto de abandono evitable.

### 2.3 Onboarding y configuración inicial

1. **La primera pantalla del administrador es la agenda.** Es correcta para un club activo, pero para un tenant vacío no explica el próximo paso.
2. **Configuración es un panel completo, no un onboarding.** El usuario debe deducir el orden entre datos del club, web de reservas, canchas, horarios, tarifas y cobros online.
3. **No hay checklist, progreso, guardado de avance ni “continuar después”.** Tampoco hay una definición visible de “listo para publicar”.
4. **No hay validación integral de configuración.** El sistema no resume problemas como “cancha sin horario”, “horario sin tarifa aplicable” o “club sin ubicación pública”.
5. **No hay preview/test guiado.** El link se puede copiar, pero no existe un paso explícito para ver el club como jugador y generar una reserva de prueba separada de las reales.
6. **No hay instrumentación por paso.** Se desconoce si la mayor fuga está al crear la cancha, definir horarios, cargar tarifas o compartir el link.

**Impacto:** tiempo a valor alto, soporte repetitivo y tenants técnicamente creados que nunca llegan a recibir demanda.

### 2.4 Canchas, horarios y tarifas

La capacidad está implementada, incluso con horarios por cancha. La fricción es de composición y validación:

- demasiadas decisiones aparecen dentro de una vista de configuración extensa;
- no se ofrece un preset de inicio, por ejemplo “lunes a domingo, 08:00 a 00:00, turnos de 90 minutos”;
- no hay un resumen visual de qué slots públicos producirá la configuración;
- no se detecta como hito de negocio la primera cancha publicable;
- no se advierte claramente cuando una regla deja días o franjas sin precio/disponibilidad.

**Impacto:** un usuario puede “haber configurado” el club sin que el jugador encuentre un solo turno reservable.

### 2.5 Mercado Pago

**Fortalezas**

- OAuth por club;
- webhook de pago;
- la seña se dirige al club sin comisión de plataforma;
- existe alternativa de reserva sin cobro online.

**Fricciones y riesgos de conversión**

1. La conexión está dentro de Configuración y no forma parte de un recorrido guiado.
2. No se separa con suficiente claridad “requisito para publicar” de “mejora para reducir ausencias”. Mercado Pago no debería bloquear la primera publicación.
3. Falta una prueba de salud visible: cuenta conectada, nombre de cuenta, último webhook correcto y una reserva/pago de prueba.
4. El evento de navegador `BOOKING_CREATED` puede ocurrir antes del pago confirmado; usarlo como conversión inflaría activación.
5. No hay mensajes de ciclo de vida del club frente a desconexión, credencial inválida o fallos repetidos.

**Impacto:** la integración más sensible del producto puede frenar onboarding o producir falsa confianza en medición y operación.

### 2.6 Trial, pricing y conversión a pago

1. **El trial no existe en el modelo de datos.** `Tenant` tiene activación técnica, pero no `trial_started_at`, `trial_ends_at`, estado de suscripción ni `activated_at` comercial.
2. **No hay experiencia in-product del trial.** Falta contador, estado, extensión, bloqueo gradual, CTA a pagar y confirmación de conversión.
3. **No hay registro de la suscripción SaaS.** No se puede distinguir trial, pago, vencido, pausado o cancelado.
4. **El precio publicado necesita contexto.** Faltan IVA, actualización, cancelación, medios de pago, qué incluye la implementación y nivel de soporte.
5. **No existe una ruta diferenciada para clubes de mayor complejidad.** El precio único es útil, pero debería coexistir con un CTA de venta asistida cuando el club tiene múltiples sedes o necesita migración.

**Impacto:** la promesa comercial no puede ejecutarse ni medirse desde el producto, y el cierre depende de seguimiento manual sin señales confiables.

### 2.7 Estados vacíos y llamadas a la acción

**Buenos ejemplos**

- buscador sin resultados con opciones para ampliar ubicación, cancha, horario o fecha;
- día lleno en la página del club con acciones para avanzar de fecha o buscar otros clubes;
- clientes y reservas recurrentes con contexto inicial;
- botón visible para copiar el link público.

**Oportunidades**

- estadísticas sin turnos/cobros informan el vacío, pero no conducen a configurar o compartir;
- cuenta de jugador sin reservas debería llevar al buscador;
- agenda vacía no distingue club nuevo, día sin configuración y día simplemente sin reservas;
- no hay estado inicial del dashboard con checklist de activación;
- después de copiar/compartir el link no se propone verificar visitas o completar la primera reserva.

**Impacto:** el producto describe estados, pero no siempre convierte esos estados en la siguiente acción del funnel.

### 2.8 Emails, WhatsApp y recuperación de abandonos

El repositorio contiene emails de verificación de jugador, reset de jugador y lista de espera; además, plantillas de WhatsApp para confirmación, reserva confirmada, vencimiento, cancelación y lista de espera.

Problemas:

- no hay bienvenida, verificación, invitación o recuperación de contraseña para el dueño del club;
- no hay secuencia por onboarding incompleto;
- no hay mensajes de trial por vencer, trial vencido o conversión a pago;
- no hay “primera reserva recibida” celebrada y acompañada;
- existe una plantilla de recordatorio de reserva, pero no se encontró el job/publicador que la dispare;
- el proveedor de WhatsApp puede operar en modo deshabilitado/no-op según configuración; debe tratarse como dependencia de lanzamiento y monitorearse;
- no existe recuperación de un visitante que tocó el CTA pero no terminó registro, porque no queda como lead.

**Impacto:** TurnosPadel no puede rescatar usuarios con intención ni construir hábito después del alta.

### 2.9 Analytics y eventos de conversión

**Lo existente**

- tracking first-party con identificador anónimo en `sessionStorage`;
- UTM, referrer y dispositivo;
- retención programada;
- eventos de búsqueda, click de resultados, pasos del club, slots, checkout, booking, fallos, link vencido y lista de espera.

**Brechas**

- no hay eventos de CTAs, demo, pricing, contacto o leads;
- no hay eventos del funnel del club;
- la atribución anónima no se enlaza con lead, tenant y suscripción;
- los datos no tienen una interfaz operativa para marketing/producto;
- `BOOKING_CREATED` del cliente no es una fuente autoritativa de reserva confirmada;
- falta idempotencia y persistencia explícita del primer evento de activación;
- no hay cohortes de trial, tiempo a activación, conversión a pago ni churn.

**Impacto:** es posible observar uso del jugador, pero no gestionar el negocio SaaS con el sistema actual.

### 2.10 SEO

**Fortalezas verificadas**

- canonical, description, Open Graph y JSON-LD;
- render server-side de metadata para páginas relevantes;
- `robots.txt` evita indexar rutas sensibles;
- sitemap productivo con landing, buscador y clubes reales;
- páginas públicas indexables por club;
- verificación para Google Search Console;
- rutas por localidad ya contempladas.

**Brechas**

- la expansión por localidades parece limitada y parcialmente curada a mano;
- faltan casos de éxito, páginas de solución y contenido B2B con intención de compra;
- falta reforzar datos estructurados/locales y consistencia de perfiles por club;
- no hay estrategia visible para capturar búsquedas “reservar pádel en [localidad]” a escala;
- una búsqueda externa `site:` no devolvió resultados durante la auditoría; esto no prueba falta de indexación, pero justifica revisar cobertura e impresiones directamente en Search Console.

**Impacto:** la base técnica es buena, pero todavía no hay un motor repetible de adquisición orgánica B2B/B2C.

### 2.11 Experiencia mobile del jugador

La experiencia pública es uno de los puntos fuertes:

- flujo breve y sin registro obligatorio;
- controles táctiles y jerarquía clara;
- filtros del buscador adaptados a mobile;
- acciones útiles ante disponibilidad vacía;
- PWA y tratamiento de navegadores embebidos;
- compartir mediante API nativa con fallback a WhatsApp;
- URLs que preservan filtros y se pueden compartir.

Mejoras recomendadas:

- medir performance real por dispositivo y red;
- mantener el precio total/seña y política de cancelación visibles antes del submit;
- instrumentar abandono por paso, especialmente datos y redirección a Mercado Pago;
- evitar que la cuenta opcional compita con reservar;
- hacer más evidente la recuperación de una operación que vuelve de Mercado Pago o se interrumpe.

### 2.12 Experiencia mobile del administrador

El código contempla drawer responsive, encabezado compacto y agenda con scroll horizontal/sticky. Eso hace viable la operación diaria desde teléfono. La principal deuda está en configuración: `SettingsView` concentra muchas áreas y decisiones, por lo que el alta completa desde mobile puede resultar densa.

Prioridad mobile admin:

1. agenda del día y alta rápida de reserva;
2. ver/cancelar/confirmar una reserva;
3. compartir el link;
4. ver cobros y alertas;
5. completar un onboarding paso a paso.

No se recomienda rehacer todo el panel antes de medir. Primero debe simplificarse el onboarding mobile y probarse con dueños reales.

### 2.13 Compartir, referidos y crecimiento orgánico

**Disponible hoy**

- copiar link público desde el panel;
- compartir una reserva por share sheet o WhatsApp;
- links de Instagram y WhatsApp en la experiencia pública;
- buscador global que puede distribuir demanda entre clubes.

**Faltante**

- botón específico “Compartir mi club por WhatsApp” con texto listo;
- QR descargable para mostrador/cartelería;
- pieza vertical para historia de Instagram;
- enlaces con campaña para atribuir visitas y reservas;
- programa de referidos entre clubes;
- invitación de jugadores/contactos sin subir agendas privadas;
- mecanismo para convertir búsquedas sin oferta en leads de clubes de esa localidad.

**Impacto:** existe la superficie viral —cada club necesita compartir su link—, pero no se aprovecha ni se mide como canal de adquisición.

---

## 3. Funnel propuesto

### 3.1 Embudo y estados

`VISITANTE → LEAD → REGISTRO VERIFICADO → TRIAL EN CONFIGURACIÓN → CLUB LISTO → CLUB PUBLICADO → PRIMERA RESERVA WEB CONFIRMADA → TRIAL ACTIVADO → CLIENTE PAGO → CLUB RETENIDO`

| Estado | Criterio de entrada | Próxima acción principal | KPI |
|---|---|---|---|
| Visitante | sesión en landing o página comercial | ver demo, pricing o comenzar alta | CTA rate |
| Lead | email/teléfono válido capturado | completar registro o agendar ayuda | lead → signup |
| Registro verificado | owner verifica email y tenant se crea | completar datos del club | signup completion |
| Trial en configuración | trial iniciado, setup incompleto | seguir checklist | setup completion |
| Club listo | tiene perfil mínimo, cancha, disponibilidad y tarifa válida | previsualizar/publicar | ready → publish |
| Club publicado | página pública accesible y con al menos un slot futuro | compartir link | publish → first visit/booking |
| Activado | primera reserva real web confirmada | repetir reservas y, opcionalmente, conectar MP | activation rate y time-to-value |
| Trial activado | activado dentro del trial | convertir a pago | activated → paid |
| Cliente pago | suscripción vigente | consolidar hábito y volumen | retention/churn |

### 3.2 Reglas del funnel

- Capturar el lead antes de sacar al usuario a WhatsApp, pero conservar venta asistida como alternativa.
- Crear el tenant y owner de forma transaccional e idempotente.
- No exigir Mercado Pago para publicar; ofrecerlo como acelerador de compromiso y reducción de ausencias.
- No permitir “publicar” si no existe al menos un slot futuro reservable con precio.
- Separar reservas de prueba de reservas reales.
- Definir el trial por fecha en backend; la UI sólo representa ese estado.
- Persistir cada transición importante, no inferirla retroactivamente desde pantallas vistas.
- Atribuir `lead_id → tenant_id → subscription_id` sin guardar PII innecesaria en analytics.

### 3.3 Checklist mínimo de activación

1. Nombre, localidad, dirección, contacto y slug.
2. Primera cancha.
3. Duración y horario de funcionamiento.
4. Primera tarifa aplicable.
5. Revisión de slots futuros generados.
6. Método de confirmación: sin seña o Mercado Pago.
7. Preview como jugador.
8. Publicación.
9. Compartir link.
10. Primera reserva web real confirmada.

---

## 4. P0 — Necesario para vender

### P0.1 Alta B2B autoservicio y aprovisionamiento seguro

- **Problema:** no existe registro de club; el equipo debe crear tenant y credenciales.
- **Impacto comercial:** el CTA de prueba gratuita no puede transformarse directamente en producto usable; crece el costo de adquisición y soporte.
- **Solución:** formulario corto con nombre, email, teléfono, club y localidad; verificación de email; creación transaccional de tenant + owner; slug sugerido; aceptación de términos B2B; inicio de trial; deduplicación por email/teléfono.
- **Archivos/componentes:** nuevas entidades/repositorios/servicios de `ClubSignup` o `Lead`; `Tenant`, usuarios de club, `SecurityConfig`; nuevas rutas/API; nueva pantalla React pública; migración Flyway; emails.
- **Esfuerzo:** **L**, 7–10 días de desarrollo y QA.

### P0.2 Captura de lead y medición de CTAs

- **Problema:** toda la intención comercial se deriva a canales externos sin quedar registrada.
- **Impacto comercial:** no se recuperan abandonos ni se atribuyen ventas.
- **Solución:** hacer que el CTA primario abra el alta propia; añadir una ruta secundaria “Prefiero que me contacten” con formulario mínimo y consentimiento; mantener WhatsApp como contacto inmediato, pero disparar evento y asociar atribución cuando sea posible.
- **Archivos/componentes:** `player-app/src/pages/Landing.tsx`, componentes de marketing, `Cta.tsx`, `config.ts`, nuevo endpoint/modelo de leads, `PageEventName`, `PageEventService`.
- **Esfuerzo:** **M**, 3–5 días.

### P0.3 Onboarding guiado con validación de readiness y publicación

- **Problema:** configuración completa sin orden, progreso ni criterio de finalización.
- **Impacto comercial:** clubes registrados quedan inactivos; el equipo no sabe dónde intervenir.
- **Solución:** wizard reanudable y responsive con presets; checklist persistido; diagnóstico de readiness; preview; reserva de prueba marcada; acción explícita Publicar. Mostrar siempre la próxima acción.
- **Archivos/componentes:** `SettingsView`, nueva `OnboardingView` o frontend dedicado, `MainLayout`, servicios de canchas/horarios/tarifas, `Tenant`, nueva migración, `SeoController`/servicios públicos para respetar publicación.
- **Esfuerzo:** **XL**, 10–15 días.

### P0.4 Modelo de trial y suscripción mínima

- **Problema:** los 7 días gratis y la conversión mensual no existen en dominio.
- **Impacto comercial:** no se puede ejecutar la oferta, segmentar cohortes ni saber quién debe pagar.
- **Solución:** agregar estado y fechas (`TRIALING`, `ACTIVE`, `PAST_DUE`, `CANCELED`), plan, comienzo/fin, fecha de conversión y registro de pago manual. En esta fase no hace falta automatizar una pasarela para cobrar el SaaS; sí hacer visible y administrable el estado.
- **Archivos/componentes:** `Tenant` o nueva `Subscription`, repositorio/servicio, migración, `MainLayout`, nueva vista interna/superadmin o proceso operativo, filtros de acceso.
- **Esfuerzo:** **L**, 6–9 días.

### P0.5 Activación server-side e idempotente

- **Problema:** no existe una señal confiable de primera reserva real confirmada.
- **Impacto comercial:** la métrica central puede incluir borradores, demos o reservas administrativas.
- **Solución:** escuchar los eventos de dominio que confirman reservas con y sin pago; validar `source=WEB`; excluir demo/test; persistir `activated_at`; emitir una única transición `FIRST_REAL_WEB_BOOKING_CONFIRMED`; registrar propiedades de atribución sin PII.
- **Archivos/componentes:** `BookingService`, `PaymentService`, `BookingEvent`, `Booking`, `Tenant`, listener nuevo, migración, `PageEventName` o una tabla separada de lifecycle events.
- **Esfuerzo:** **M**, 3–5 días incluyendo pruebas.

### P0.6 Invitación y recuperación de acceso del club

- **Problema:** “Olvidé mi clave” deriva a soporte y no hay un ciclo claro de invitación.
- **Impacto comercial:** fricción crítica en primer acceso y operación diaria.
- **Solución:** token de invitación con expiración, definición de contraseña, reset autoservicio, revocación de sesiones y auditoría básica. No revelar si un email existe.
- **Archivos/componentes:** `LoginView`, seguridad/autenticación de club, entidad de tokens, `EmailTemplates`, servicio de email, `SettingsView` para usuarios.
- **Esfuerzo:** **M/L**, 5–8 días.

### P0.7 Confiabilidad comercial y operativa mínima

- **Problema:** comunicaciones del club, proveedor WhatsApp, estado de Mercado Pago y términos B2B no están cerrados como experiencia de venta.
- **Impacto comercial:** un club puede comprar una promesa que falla silenciosamente o no comprende qué incluye.
- **Solución:** correo de dominio; términos/privacidad B2B; página de soporte; health checks y alertas de email/WhatsApp/webhooks; estado visible de MP; prueba de notificación; implementar o retirar el recordatorio no disparado; runbook de soporte.
- **Archivos/componentes:** `application.yml`, `NotificationService`, `BookingNotificationListener`, `EmailTemplates`, proveedores WhatsApp, OAuth/webhooks de MP, páginas legales/marketing y observabilidad.
- **Esfuerzo:** **L**, 5–8 días más configuración de proveedores.

**Resultado de salida P0:** un dueño puede llegar desde la landing, registrarse, configurar, publicar, recibir una reserva real, terminar un trial y quedar registrado como cliente pago sin depender de datos informales.

---

## 5. P1 — Mejoras de conversión

### P1.1 Recuperación por ciclo de vida

- **Problema:** no hay recuperación de leads, registros o setups abandonados.
- **Solución:** secuencias basadas en estado: lead sin signup (24 h), signup sin cancha (24 h), cancha sin publicación (48 h), publicado sin visitas/reservas (72 h), trial por vencer (3/1 días), primera reserva y trial vencido. Detener mensajes cuando el objetivo se cumple.
- **Impacto:** más activaciones sin incrementar tráfico.
- **Archivos:** scheduler/jobs, servicio de lifecycle, plantillas email/WhatsApp, preferencias y logs.
- **Esfuerzo:** **L**, 6–9 días.

### P1.2 Dashboard de conversión y lista de intervención

- **Problema:** analytics existe como datos técnicos, no como herramienta comercial.
- **Solución:** tablero con cohortes y lista accionable de clubes: etapa, días restantes, último hito, bloqueo, reservas y próxima acción.
- **Impacto:** venta asistida enfocada en PQLs y bloqueos reales.
- **Archivos:** nueva vista interna/superadmin, servicios de agregación, repositorios de lifecycle/subscription.
- **Esfuerzo:** **L**, 5–8 días.

### P1.3 Prueba social verificable

- **Problema:** la landing no capitaliza clubes reales.
- **Solución:** 2–3 testimonios breves, logos autorizados, caso con antes/después y métricas honestas; CTA directo al club público cuando corresponda.
- **Impacto:** reduce riesgo percibido del propietario.
- **Archivos:** componentes marketing, assets, configuración de contenido/SEO.
- **Esfuerzo:** **S/M**, 2–4 días más obtención de permisos.

### P1.4 Claridad de pricing y compra

- **Problema:** el precio no explica completamente IVA, actualización, cancelación, soporte y alta.
- **Solución:** bloque “qué incluye/no incluye”, preguntas comerciales, política de cancelación y dos recorridos: empezar trial o hablar por una necesidad compleja.
- **Impacto:** menos objeciones y conversaciones improductivas.
- **Archivos:** landing/configuración de marketing, términos B2B.
- **Esfuerzo:** **S**, 1–2 días.

### P1.5 Estados vacíos orientados a activación

- **Problema:** varios vacíos informan, pero no avanzan el funnel.
- **Solución:** agenda nueva con checklist; estadísticas sin datos con “Compartir link”; cuenta de jugador vacía con “Buscar turno”; club publicado sin visitas con kit de difusión.
- **Impacto:** más usuarios encuentran la próxima acción sin soporte.
- **Archivos:** `AgendaView`, `DashboardView`, `AccountPage.tsx`, `MainLayout`, onboarding.
- **Esfuerzo:** **M**, 3–5 días.

### P1.6 Kit de publicación y atribución

- **Problema:** copiar link es útil, pero no suficiente para que el club genere demanda.
- **Solución:** WhatsApp con texto sugerido, QR descargable, pieza vertical para Instagram, enlace corto/campaña y contador de visitas/reservas por canal.
- **Impacto:** acelera la primera reserva y convierte a cada club en canal de distribución.
- **Archivos:** `MainLayout`, nueva vista/componente de compartir, API de atribución, render de assets/QR, analytics.
- **Esfuerzo:** **M/L**, 5–8 días.

### P1.7 Onboarding mobile y operaciones críticas

- **Problema:** el panel es responsive, pero la configuración extensa puede ser difícil en teléfono.
- **Solución:** pasos de una decisión por pantalla, barra fija para continuar, presets y pruebas con 5 dueños; priorizar agenda, reserva rápida, cobros, alertas y compartir.
- **Impacto:** activación de propietarios que administran el club desde el celular.
- **Archivos:** `SettingsView`/nuevo onboarding, `AgendaView`, `MainLayout`, estilos Vaadin.
- **Esfuerzo:** **L**, 5–10 días según resultados de test.

### P1.8 Estado de Mercado Pago y recuperación de errores

- **Problema:** una conexión técnica no garantiza cobros sanos.
- **Solución:** mostrar cuenta conectada, última verificación, último webhook, fallos accionables y reconexión; comunicar claramente reserva pendiente/confirmada al volver de MP.
- **Impacto:** más señas cobradas y menos soporte/confusión.
- **Archivos:** `SettingsView`, `MercadoPagoOAuthController`, `MercadoPagoOAuthService`, `PaymentService`, retorno del player app.
- **Esfuerzo:** **M/L**, 4–7 días.

---

## 6. P2 — Crecimiento

### P2.1 Referidos entre clubes

- **Hipótesis:** los propietarios conocen a otros propietarios y pueden reducir CAC.
- **Solución:** código/link de referido, recompensa sólo cuando el referido paga, límites antifraude y atribución persistente.
- **Métrica:** clientes pagos referidos / clientes activos; CAC referido; retención del referido.
- **Esfuerzo:** **L**.

### P2.2 SEO local escalable

- **Hipótesis:** captar demanda de jugadores mejora la propuesta para clubes de cada localidad.
- **Solución:** páginas indexables “reservar pádel en [localidad]” sólo donde haya oferta útil; metadata y datos estructurados dinámicos; contenido no duplicado; Search Console como fuente de validación.
- **Métrica:** sesiones orgánicas locales, búsquedas con resultado, reservas orgánicas.
- **Esfuerzo:** **L**.

### P2.3 Demanda sin oferta como pipeline comercial

- **Hipótesis:** búsquedas sin clubes revelan ciudades donde vender.
- **Solución:** agregar “Avisame cuando haya clubes” para jugadores y un panel agregado sin PII para priorizar prospección de clubes.
- **Métrica:** localidades con demanda, leads contactados, clubes incorporados, demanda convertida.
- **Esfuerzo:** **M/L**.

### P2.4 Product-qualified leads y scoring

- **Hipótesis:** visitas al demo, alta, slots publicados y tráfico público permiten priorizar ventas.
- **Solución:** score transparente por hitos, sin cajas negras; alertar al equipo cuando un club está listo pero no activado o activado y cerca de vencer.
- **Métrica:** tasa y tiempo de cierre por nivel de score.
- **Esfuerzo:** **M** luego de estabilizar eventos.

### P2.5 Resumen semanal al dueño

- **Hipótesis:** visibilizar valor reduce churn.
- **Solución:** email semanal con reservas web, señas, ocupación, clientes nuevos, horas ahorradas estimadas y acciones pendientes.
- **Métrica:** apertura, retorno al panel, retención y reservas por club.
- **Esfuerzo:** **M**.

### P2.6 Cobro SaaS automatizado

- **Hipótesis:** al crecer el número de clientes, cobrar manualmente aumenta mora y operación.
- **Solución:** automatizar suscripción/facturación sólo después de validar proceso, estados y política comercial con pagos manuales.
- **Métrica:** conversión, mora, recupero, churn involuntario.
- **Esfuerzo:** **XL**.

---

## 7. Eventos de analytics recomendados

### 7.1 Principios

- Eventos de intención/UI pueden originarse en cliente.
- Estados de negocio deben emitirse en backend después del commit correspondiente.
- La primera activación y los pagos deben ser idempotentes.
- Analytics no debe contener nombre, email, teléfono ni texto libre.
- Propiedades comunes: `anonymous_id`, `lead_id`, `tenant_id`, `session_id`, timestamp, UTM, referrer, landing path, dispositivo, versión y experimento.
- Al registrarse, vincular la atribución original con el tenant sin reescribir el primer contacto.
- Separar eventos de producto de logs técnicos y notificaciones.

### 7.2 Taxonomía mínima

| Etapa | Evento | Fuente | Propiedades relevantes |
|---|---|---|---|
| Adquisición | `landing_viewed` | cliente | campaign, landing_variant |
| Intención | `demo_opened` | cliente | placement, demo_club |
| Intención | `pricing_viewed` | cliente | scroll/source |
| Intención | `club_cta_clicked` | cliente | cta_name, placement, destination |
| Lead | `lead_submitted` | servidor | channel, locality, attribution_id |
| Registro | `club_signup_started` | cliente/servidor | source |
| Registro | `club_signup_completed` | servidor | tenant_id, assisted |
| Registro | `club_email_verified` | servidor | elapsed_minutes |
| Acceso | `club_first_login` | servidor | invite/signup |
| Setup | `club_profile_completed` | servidor | completeness |
| Setup | `first_court_created` | servidor | court_type |
| Setup | `first_schedule_created` | servidor | weekly_slots_estimate |
| Setup | `first_pricing_created` | servidor | pricing_mode |
| Setup | `mercado_pago_connect_started` | servidor | onboarding_step |
| Setup | `mercado_pago_connected` | servidor | success, account_type |
| Readiness | `club_became_ready` | servidor | missing_items_before |
| Preview | `club_preview_opened` | cliente | device |
| Publicación | `club_published` | servidor | days_since_signup, slots_next_7d |
| Distribución | `booking_link_copied` | cliente | placement, campaign_id |
| Distribución | `booking_link_shared` | cliente | channel, campaign_id |
| Demanda | `first_public_club_view` | servidor | attribution_channel |
| Reserva | `slot_selected` | cliente | lead_time, court_id, price_band |
| Reserva | `checkout_started` | cliente | payment_mode |
| Reserva | `booking_draft_created` | servidor | payment_mode |
| Reserva | `booking_confirmed` | servidor | source, payment_mode, paid |
| **Activación** | **`first_real_web_booking_confirmed`** | **servidor** | **days_since_signup, days_since_publish, attribution_channel** |
| Adopción | `first_deposit_paid` | servidor | amount_band, days_since_signup |
| Retención | `second_web_booking_confirmed` | servidor | days_since_activation |
| Trial | `trial_expiring` | servidor | days_remaining, activated |
| Monetización | `subscription_started` | servidor | plan, assisted, activated |
| Monetización | `subscription_payment_recorded` | servidor | plan, period |
| Riesgo | `subscription_past_due` | servidor | attempt/count |
| Churn | `subscription_canceled` | servidor | reason_code, tenure_days |

### 7.3 Tablero mínimo

- sesiones → CTAs → leads → registros;
- registro → club listo → publicado → activado;
- conversión por fuente/campaña/localidad;
- tiempo mediano por transición;
- activación en 1, 3 y 7 días;
- activado → pago y no activado → pago;
- segunda reserva a 14 días;
- trials por vencer agrupados por etapa y bloqueo;
- abandono por paso de onboarding y checkout.

---

## 8. Experimentos de growth recomendados

No conviene hacer A/B tests sofisticados antes de instrumentar el funnel y acumular volumen. Con tráfico bajo, usar lanzamientos secuenciales, entrevistas y comparación de cohortes. Definir por anticipado métrica primaria, guardrail y plazo.

| Prioridad | Experimento | Hipótesis | Métrica primaria | Guardrail |
|---|---|---|---|---|
| 1 | “Crear mi club gratis” vs. “Hablemos” como CTA primario | Un resultado concreto reduce ambigüedad | signup started / visitante | leads calificados y spam |
| 2 | Alta embebida vs. salida directa a WhatsApp | Capturar datos propios permite recuperar más intención | lead/signup / CTA | tiempo a respuesta y calidad |
| 3 | Caso real y logos cerca del CTA | Evidencia local reduce riesgo percibido | CTA rate | rebote y consentimiento de clubes |
| 4 | Checklist asistido vs. configuración libre | Un orden prescriptivo acelera publicación | publish en 3 días | errores de configuración |
| 5 | Preset de horarios/tarifas vs. carga desde cero | Un default editable reduce trabajo inicial | club ready / signup | correcciones posteriores |
| 6 | Ofrecer MP después de publicar vs. durante setup obligatorio | Diferir la integración reduce tiempo a primera reserva | activación en 7 días | adopción de señas y ausencias |
| 7 | Prompt de compartir tras preview vs. sólo en header | El momento contextual genera tráfico antes | first public view / published | shares accidentales |
| 8 | Secuencia de recuperación por etapa | Mensajes específicos recuperan más que un recordatorio genérico | reanudación y activación | bajas/quejas |
| 9 | Trial de 7 vs. 14 días, sólo en cohortes comparables | Más tiempo puede ayudar a clubes lentos | activado → pago | dilación y soporte |
| 10 | “Te lo configuramos” para leads asistidos | Reducir esfuerzo percibido mejora cierre de clubes valiosos | lead → activated | costo de implementación |

El experimento de duración del trial debe ejecutarse después de conocer la distribución real del tiempo a activación. Si la mayoría se activa en 48 horas, extenderlo sólo demora el cierre; si la operación requiere coordinación interna del club, puede ser útil.

---

## 9. Archivos y componentes a modificar

Esta es una guía de impacto, no una instrucción de implementación inmediata.

| Área | Archivos/componentes existentes | Cambios o componentes nuevos probables |
|---|---|---|
| Landing y CTAs | `player-app/src/pages/Landing.tsx`, `player-app/src/pages/marketing/Cta.tsx`, `config.ts` | formulario de alta/lead, variantes de CTA, prueba social, pricing detallado |
| Alta de club | `SecurityConfig`, dominio `Tenant`, usuarios de club | `ClubSignupController/Service`, pantalla React, tokens de verificación, creación transaccional |
| Leads | `PageEvent` y servicios de analytics como referencia | `Lead`, repositorio, API, consentimientos, estado y atribución |
| Onboarding | `SettingsView`, `MainLayout`, servicios de configuración | `OnboardingView`, checklist/readiness service, presets, preview, estado publish |
| Canchas/horarios/tarifas | secciones dentro de `SettingsView` y servicios asociados | validación integral, plantillas/presets, resumen de slots futuros |
| Agenda/dashboard | `AgendaView`, `DashboardView`, `KpiCard` | home condicional por madurez, CTA contextual y progreso de activación |
| Auth de club | `LoginView`, security/auth provider | reset, invitaciones, tokens, emails, auditoría de accesos |
| Trial/suscripción | `Tenant` | `Subscription`, `SubscriptionStatus`, servicio, vista operativa y migraciones |
| Activación | `BookingService`, `PaymentService`, `BookingEvent`, `Booking` | listener idempotente, `activated_at`, lifecycle event store |
| Mercado Pago | `MercadoPagoOAuthController`, `MercadoPagoOAuthService`, `PaymentService`, `SettingsView` | estado de salud, prueba, reconexión y mensajes de recuperación |
| Notificaciones | `NotificationService`, `BookingNotificationListener`, `EmailTemplates`, paquete WhatsApp | plantillas del club, preferencias, jobs de lifecycle, observabilidad y recordatorios |
| Analytics | `PageEventController`, `PageEventService`, `PageEvent`, `PageEventName`, repositorio | taxonomía B2B, vinculación de atribución, eventos server-side y dashboard |
| Player booking | `ClubPage.tsx`, flujo de booking, `SearchPage.tsx`, `AccountPage.tsx` | instrumentación por paso, recuperación MP, CTA en vacíos, campaña de share |
| Compartir | `MainLayout`, `ClubPage.tsx` | share kit, QR, piezas sociales, campaign links |
| SEO | `SeoController`, `SeoPageRenderer`, sitemap/robots | páginas locales dinámicas, casos de éxito, schema y control de indexación |
| Persistencia | `src/main/resources/db/migration` | nuevas migraciones para leads, onboarding, lifecycle, trial y suscripción |
| Configuración/operación | `src/main/resources/application.yml` | email de dominio, proveedores, feature flags, alertas y secretos fuera del repo |
| Pruebas | tests de booking, pago, eventos, auth y SEO existentes | signup e2e, readiness, activación idempotente, trial, recuperación y atribución |

### Componentes que no deberían reescribirse sin evidencia

- motor de disponibilidad y prevención de solapamientos;
- reserva pública sin cuenta obligatoria;
- integración base de Mercado Pago;
- buscador y páginas públicas de clubes;
- agenda operacional;
- analytics first-party como fundamento técnico.

La estrategia debe envolver estas capacidades con onboarding, lifecycle y medición, no reemplazar un núcleo que ya funciona.

---

## 10. Plan de implementación

### Fase 0 — Definiciones y línea base (2–3 días)

1. Acordar estados de tenant, trial, publicación y suscripción.
2. Definir exactamente qué es demo/test y cómo se excluye.
3. Congelar taxonomía inicial de eventos y propiedades.
4. Medir manualmente la cohorte actual: clubes creados, publicados, con primera y segunda reserva.
5. Documentar el proceso comercial real y responsable de cada transición.

**Criterio de salida:** funnel con definiciones compartidas y consultas reproducibles, aunque parte de los datos todavía sea manual.

### Fase 1 — Captura y alta B2B (1–2 semanas)

1. Lead first-party y tracking de CTAs.
2. Registro/verificación de owner.
3. Aprovisionamiento transaccional de tenant.
4. Invitación y recuperación de contraseña.
5. Inicio de trial y términos B2B.
6. Emails con dominio propio.

**Criterio de salida:** un visitante puede convertirse en owner autenticado con tenant y trial sin intervención técnica.

### Fase 2 — De configuración a publicación (2–3 semanas)

1. Checklist persistido y onboarding responsive.
2. Presets de cancha, horarios y tarifa.
3. Readiness validator.
4. Preview y reserva de prueba identificada.
5. Publicación explícita.
6. Share por WhatsApp y primer kit básico.

**Criterio de salida:** al menos 80% de los usuarios de prueba entiende el siguiente paso sin ayuda y puede producir un slot público válido.

### Fase 3 — Activación y monetización mínima (1–2 semanas)

1. Evento server-side de primera reserva real confirmada.
2. `activated_at` y eventos de lifecycle.
3. Estados de suscripción y registro de pago manual.
4. Avisos de trial y CTA a convertir.
5. Panel interno de cohortes y próximos contactos.

**Criterio de salida:** se puede responder con datos cuántos clubes se registraron, publicaron, activaron y pagaron, por cohorte y fuente.

### Fase 4 — Recuperación y confiabilidad (1–2 semanas)

1. Secuencias de abandono por etapa.
2. Salud de Mercado Pago y notificaciones.
3. Confirmar/implementar recordatorios pendientes.
4. Estados vacíos accionables.
5. Runbooks y alertas productivas.

**Criterio de salida:** los principales bloqueos generan una acción automática o una tarea comercial visible; no dependen de que alguien revise la base.

### Fase 5 — Optimización y growth continuo

1. Ejecutar primero los experimentos de CTA, onboarding y sharing.
2. Incorporar prueba social real.
3. Expandir SEO local sólo donde haya oferta suficiente.
4. Lanzar referidos después de validar activación y retención.
5. Automatizar cobro SaaS cuando la operación manual sea el cuello de botella.

**Criterio de salida:** backlog de growth priorizado por impacto observado, no por cantidad de features.

### Orden recomendado de entrega

`medición confiable → alta B2B → onboarding/readiness → publicación → activación real → trial/pago → recuperación → adquisición escalable`

### Estimación global

- **P0 secuencial:** aproximadamente 5–8 semanas de desarrollo, según cuánto se reutilice de auth, email y UI existentes.
- **P1 inicial:** 3–5 semanas adicionales, entregable por partes.
- **P2:** sólo después de contar con una línea base de activación y retención.

Las estimaciones son rangos de producto e ingeniería, no compromisos de calendario. Deben ajustarse al equipo, QA, despliegue, configuración de proveedores y necesidad de migrar clubes existentes.

## Decisión de producto recomendada

Durante la próxima etapa, TurnosPadel debería rechazar cualquier iniciativa que no mejore al menos una de estas cuatro métricas:

1. club registrado → club publicado;
2. club publicado → primera reserva web confirmada;
3. tiempo hasta primera reserva;
4. club activado → cliente pago/retención.

El producto no necesita demostrar que puede hacer más cosas. Necesita convertir de forma repetible su capacidad actual en clubes activos que reciben reservas reales.
