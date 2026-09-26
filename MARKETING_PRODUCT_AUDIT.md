# Auditoría de producto, onboarding y conversión comercial

Fecha: 25 de septiembre de 2026  
Producto auditado: TurnosPadel  
Alcance: aplicación pública del jugador, landing comercial, panel del club, módulo de gimnasio, backend, modelo de datos, analytics y notificaciones.

## 1. Resumen ejecutivo

TurnosPadel ya tiene un núcleo de producto considerablemente más sólido que el de una simple agenda: resuelve disponibilidad, reservas, cobros de señas, operación de mostrador, caja, lista de espera, turnos fijos, personalización por club y búsqueda entre clubes. La propuesta de valor es clara y el flujo del jugador está pensado para convertir con poca fricción: se puede reservar sin crear una cuenta y el dinero de la seña va directamente al Mercado Pago del club.

La principal debilidad no está en el motor de reservas, sino en la capa comercial del SaaS. Hoy el producto puede venderse de manera artesanal, con el fundador atendiendo WhatsApp, creando el club y coordinando el cobro por fuera. No existe todavía dentro del sistema un embudo B2B completo y medible:

`visitante → lead → prueba → club activado → cliente pago`

En concreto:

- La landing envía al interesado a WhatsApp o email, pero no captura el lead ni registra el clic comercial.
- No aparece en el repositorio un alta productiva de clubes. Los tenants y dueños de ejemplo se crean únicamente desde `DevDataSeeder`.
- La prueba de 7 días se promete en marketing, pero no existe en el dominio una fecha de inicio, vencimiento ni estado de trial.
- El precio mensual se publica, pero no existe una suscripción, estado de pago o historial de cobros del SaaS.
- El panel abre directamente en la agenda. Para un club nuevo no hay checklist, recorrido guiado ni una definición visible de “listo para recibir reservas”.
- El sistema registra muy bien parte del embudo del jugador, pero no el embudo comercial del club y no ofrece una interfaz para consultar los datos existentes.
- El acceso del club no tiene recuperación de contraseña autoservicio.
- Hay emails transaccionales para cuentas de jugadores y lista de espera, y una buena base de plantillas de WhatsApp, pero no hay comunicaciones de bienvenida, activación, trial o facturación para el dueño del club.

Conclusión: el producto está cerca de ser vendible mediante venta asistida, pero todavía no está preparado para que esa venta sea repetible, observable y operable sin intervención técnica. Los P0 de este documento no proponen convertirlo inmediatamente en un SaaS completamente autoservicio; proponen construir la mínima columna vertebral comercial para vender y acompañar clubes sin usar la base de datos como backoffice.

## 2. Cómo se entiende el producto hoy

### 2.1 Problema que resuelve

Para el club:

- Evita administrar disponibilidad y reservas exclusivamente por WhatsApp, teléfono o planillas.
- Centraliza en una sola agenda las reservas web, las cargadas por mostrador/teléfono y los turnos fijos.
- Reduce sobreventas, trabajo manual y huecos causados por señas impagas.
- Permite cobrar una seña online directamente en la cuenta de Mercado Pago del club.
- Ordena la operación posterior: cobros, caja, buffet, ausentes, cancelaciones, alertas y clientes.
- Da al club una página de reservas propia, personalizable y compartible.

Para el jugador:

- Permite ver disponibilidad y precios reales sin esperar una respuesta por WhatsApp.
- Permite reservar sin crear una cuenta.
- Permite buscar horarios entre distintos clubes si todavía no eligió dónde jugar.
- Permite pagar una seña, gestionar/cancelar el turno y entrar en lista de espera.

### 2.2 Usuario objetivo aparente

**Comprador principal:** dueño o administrador de un club de pádel pequeño o mediano, especialmente uno que todavía toma turnos por WhatsApp y atiende operación de mostrador.

**Usuario operativo:** personal de recepción/mostrador. Tiene acceso a agenda, caja, buffet, jugadores, turnos fijos, lista de espera, alertas y suspensiones, con restricciones sobre estadísticas y cobros online.

**Usuario final:** jugador de pádel que llega desde el link del club, la búsqueda global o un enlace compartido.

**Segmento adicional:** clubes que también operan un gimnasio. El módulo suma socios, cuotas, sedes y check-in por QR/DNI, pero se presenta como adicional y no como parte del plan base.

### 2.3 Flujo principal de uso

#### Flujo del club

1. El dueño conoce el producto en la landing.
2. Inicia una conversación por WhatsApp o email.
3. El vendedor carga el club, canchas, horarios y tarifas de forma asistida.
4. El dueño recibe credenciales e ingresa en `/admin`.
5. Personaliza su web, configura cobros y copia el link público.
6. Comparte el link por WhatsApp/Instagram.
7. Recibe reservas en la agenda y opera cobros, caja, cancelaciones y alertas.
8. Si continúa, coordina por fuera una transferencia mensual.

Los pasos 2, 3 y 8 están descritos en la landing, pero no están modelados como procesos dentro del producto.

#### Flujo del jugador

1. Entra directamente a `/club/{slug}` o busca en `/buscar`.
2. Elige día.
3. Elige horario y, si corresponde, cancha.
4. Ingresa nombre y teléfono; la cuenta es opcional.
5. Reserva pagando seña online o eligiendo pagar en el club, según la configuración y la confianza del jugador.
6. Ve la confirmación y puede gestionar/cancelar el turno mediante token.
7. Puede crear una cuenta luego de reservar para conservar el historial.
8. Si el horario está completo, puede ingresar a lista de espera; para eso sí necesita una cuenta.

### 2.4 Funcionalidades principales verificadas

- Multi-tenant con aislamiento por club.
- Agenda de canchas y carga manual de reservas.
- Reserva pública sin registro obligatorio.
- Disponibilidad y precios por fecha, franja y cancha.
- Restricción de doble reserva a nivel de base de datos.
- Pago de seña mediante Mercado Pago OAuth y webhook.
- Reserva de palabra/pago en club.
- Liberación automática de borradores impagos.
- Gestión y cancelación mediante links con token.
- Turnos fijos con generación futura y excepciones.
- Lista de espera y aviso cuando se libera un turno.
- Búsqueda de disponibilidad entre clubes.
- Personalización de página pública: portada, colores, contenido y servicios.
- Caja, cobros manuales, devoluciones, buffet y consumos.
- Jugadores, historial, bloqueo y marca de confianza.
- Alertas operativas.
- Estadísticas de reservas, ocupación, facturación, cobros, horarios y clientes.
- Roles de dueño y mostrador.
- Cuenta opcional del jugador con email/contraseña o Google.
- PWA para jugadores y aplicación de gimnasio.
- SEO server-side para landing, clubes y búsqueda; sitemap y datos estructurados.
- Analytics propios, sin terceros, para el recorrido del jugador.

### 2.5 Diferenciadores reales

1. **Agenda verdaderamente unificada.** Web, teléfono, mostrador y turnos fijos comparten disponibilidad y reglas.
2. **Protección fuerte contra doble booking.** La garantía vive en PostgreSQL, no solamente en validaciones del frontend.
3. **La seña va directo al club.** TurnosPadel no intermedia ni retiene el dinero y no suma comisión por reserva.
4. **Reserva sin cuenta.** Reduce una fricción importante en un caso de uso de alta intención y baja paciencia.
5. **Página propia por club.** El jugador percibe la marca del club y no solamente la del marketplace.
6. **Búsqueda entre clubes.** Introduce un efecto de red y puede generar demanda incremental, no solo digitalizar demanda existente.
7. **Profundidad operativa.** Caja, buffet, confianza del jugador, suspensiones, alertas y turnos fijos lo acercan al trabajo real del mostrador.
8. **Modelo híbrido pádel + gimnasio.** Es un ángulo comercial distintivo para clubes mixtos.
9. **Medición first-party y respetuosa de privacidad.** El embudo del jugador se guarda en la misma base donde vive la reserva y puede cruzarse con el resultado real.

### 2.6 Fricciones para un usuario nuevo

#### Dueño del club

- No puede registrarse ni iniciar una prueba desde la web.
- Debe abandonar el sitio hacia WhatsApp o email antes de quedar registrado como lead.
- No sabe cuánto tarda el alta ni qué información debe preparar.
- Recibe un panel amplio sin checklist ni recorrido de primera configuración.
- `Configuración` concentra siete pestañas y muchas decisiones antes del primer valor.
- No existe un indicador de que la página pública esté lista para compartirse.
- No puede restablecer su contraseña por sí mismo.
- No ve cuánto tiempo queda de prueba, estado de plan ni próximo pago.
- La promesa de prueba y suscripción depende de procesos externos no visibles.

#### Personal de mostrador

- El panel está optimizado para operación real, pero no explica el modelo mental durante el primer uso.
- La agenda es un buen inicio para un club configurado; para uno vacío, no enseña qué falta hacer.
- Algunos estados vacíos informan correctamente, pero no siempre llevan a la siguiente acción.

#### Jugador

- La reserva es corta y no obliga a registrarse, lo cual es una fortaleza.
- La lista de espera sí exige cuenta; el motivo se explica, pero agrega un desvío en un momento de frustración.
- La cuenta vacía informa que todavía no hay turnos, pero no ofrece una CTA directa para buscar o volver al club.
- La confirmación duradera depende de que el jugador conserve el link, el navegador o cree una cuenta. No hay email de reserva para el invitado porque el checkout no solicita email.

## 3. Auditoría de superficies

### 3.1 Landing page

**Fortalezas**

- El titular “Que tu club reserve solo” expresa resultado, no tecnología.
- El texto parte de un dolor reconocible: mensajes nocturnos y trabajo manual por WhatsApp.
- Se aclara rápido que la seña entra al Mercado Pago del club.
- La demostración interactiva conecta la acción del jugador con la agenda del club.
- Hay precio visible, prueba sin tarjeta, FAQ y explicación de implementación.
- Las objeciones importantes están cubiertas: dinero, teléfono, doble reserva, registro y trabajo actual.
- Hay SEO y previews sociales generadas desde backend; esto es especialmente relevante porque el link del club se comparte por WhatsApp.
- La landing distingue “Buscar turno”, “Soy club” y “Hablemos”, evitando mezclar los dos públicos principales.

**Problemas**

- Todos los CTA comerciales salen a WhatsApp o email sin registrar intención ni datos.
- No hay testimonios, logos, caso real cuantificado ni otra prueba social verificable.
- El contacto comercial y legal usa un Gmail personal, lo que reduce percepción de empresa y continuidad.
- “Ver un club real” depende de `/club/simon`, un tenant reservado que no se crea en el seeder de desarrollo. La producción depende de que ese tenant exista y se mantenga saludable.
- La demo permite experimentar el flujo del jugador y una simulación del panel, pero no permite que el dueño pruebe el panel real.
- La página es extensa antes del cierre y no adapta el CTA al nivel de intención: hablar, pedir demo y empezar prueba terminan esencialmente en el mismo canal.
- No se registra qué CTA, sección o propuesta generó el contacto.
- El mensaje inicial de WhatsApp es genérico y no incluye fuente, campaña, cantidad de canchas ni intención concreta.
- No se informa qué incluye el acompañamiento, cuánto tarda el alta ni qué sucede al terminar la prueba.

**Evaluación:** propuesta de valor 8/10; capacidad de capturar y medir demanda 3/10.

### 3.2 Registro y login

#### Jugador

**Bien resuelto**

- La cuenta no bloquea la reserva.
- Hay login con Google o email/contraseña.
- El alta valida email con código/link.
- Existe recuperación de contraseña.
- Los turnos reservados como invitado pueden reclamarse al iniciar sesión.
- Se preserva el retorno al flujo que originó el login.

**Fricciones/mejoras**

- No hay eventos de analytics para inicio/completitud/fallo de registro o login.
- La cuenta vacía no ofrece inmediatamente “Buscar turno”.
- No hay reenvío explícito del código; “Volvé a intentar” vuelve al formulario completo.

#### Club

**Problemas críticos**

- Solo existe login; no existe registro público o alta interna visible en el producto.
- `DevDataSeeder` es el único lugar encontrado que crea `Tenant` y usuario `OWNER` de punta a punta.
- El rol `SUPER_ADMIN` existe, pero no hay una consola de plataforma para crear o administrar tenants.
- “Olvidé mi clave” no dispara recuperación: el texto indica que se contacte al equipo.
- No hay invitación de primer acceso ni obligación de elegir una contraseña personal.
- No se registra primer login, último login ni avance de onboarding.

### 3.3 Onboarding

No hay onboarding in-product para el club. El onboarding actual es concierge: “charlamos y cargamos tu club”. Puede ser una buena decisión comercial temprana, pero necesita soporte de producto para ser repetible.

No existe:

- checklist de preparación;
- estado de publicación;
- asistente de canchas/horarios/tarifas;
- prueba de reserva guiada;
- validación de configuración mínima;
- progreso de activación;
- responsable comercial o notas del onboarding;
- fecha objetivo de salida;
- evento de activación.

La pantalla `SettingsView` contiene toda la capacidad necesaria, pero es una pantalla de administración, no un recorrido de primeros pasos.

### 3.4 Dashboard inicial

La ruta raíz del panel abre `AgendaView`, no `DashboardView`. Para un club operativo es una buena decisión: lleva al trabajo del día. La vista llamada `DashboardView` es en realidad la sección de estadísticas y está correctamente restringida al dueño.

Para un club nuevo, la agenda no funciona como home de activación:

- no indica si faltan canchas, horarios o tarifas;
- no propone una reserva de prueba;
- no muestra el estado del trial;
- no explica el próximo paso para publicar;
- el botón “Copiar link” está siempre disponible, incluso si la configuración todavía no produce una experiencia útil.

Recomendación: conservar Agenda como inicio una vez activado el club, pero mostrar un panel de “Prepará tu club” mientras no se cumpla la definición de activación.

### 3.5 Estados vacíos

**Buenos ejemplos**

- Búsqueda sin resultados: ofrece ampliar localidad, quitar filtros, ver todo el día o probar el día siguiente.
- Día sin disponibilidad en un club: ofrece día siguiente y búsqueda en otros clubes.
- Jugadores: explica que aparecen automáticamente con la primera reserva.
- Turnos fijos: indica que se crean con el botón superior.
- Alertas, suspensiones, caja y buffet tienen mensajes específicos.

**Problemas**

- Estadísticas muestra “No hay turnos/cobros” sin conducir a compartir el link, crear un turno de prueba o configurar la agenda.
- La cuenta vacía del jugador no lleva a `/buscar` ni al último club visitado.
- La agenda de un tenant sin canchas/horarios no se diferencia de un día naturalmente vacío.
- No hay estado vacío comercial: un trial recién creado no recibe una misión concreta.

### 3.6 Llamadas a la acción

**Fortalezas**

- Los CTA principales son claros, visibles y repetidos en puntos razonables.
- “Hablemos”, “Quiero mi prueba gratis” y “Ver un club real” reducen ambigüedad.
- En móvil se mantienen visibles los accesos para jugador y club.

**Problemas**

- “Hablemos” y “Quiero mi prueba gratis” terminan en el mismo mecanismo, pero expresan intenciones diferentes que no quedan registradas.
- No hay CTA intermedio para dejar datos sin abrir WhatsApp.
- No existe estado de éxito dentro del sitio.
- Los clics en WhatsApp, email, demo, precio o gimnasio no se instrumentan.
- Los CTA no conservan UTM ni página/sección de origen en el mensaje.

### 3.7 Demo y trial

Hay tres conceptos distintos que hoy se mezclan:

1. **Demo interactiva en landing:** simulación local, útil y sin riesgo.
2. **“Club real”:** página pública del tenant `/club/simon`.
3. **Prueba de 7 días:** club real del prospecto, preparado manualmente.

La demo interactiva es una fortaleza. Las otras dos necesitan formalización:

- El tenant demo no está provisionado por código fuera de la convención del slug.
- No hay monitoreo específico de que el link demo responda y tenga disponibilidad futura.
- El prospecto no puede acceder a un panel demo seguro o de solo lectura.
- El trial no existe como entidad o estado; no comienza, vence ni convierte dentro del sistema.
- No hay recordatorios de trial ni aviso interno para seguimiento comercial.

### 3.8 Pricing

**Estado actual**

- Plan único: ARS 35.000 por mes por club.
- 7 días gratis, sin tarjeta.
- Alta y cobro manuales.
- Pago de suscripción por transferencia.
- El módulo de gimnasio se cobra aparte, sin precio publicado.

**Fortalezas**

- El precio es transparente.
- Un solo plan evita parálisis de elección.
- La calculadora traduce el abono a turnos necesarios para cubrirlo.
- Se aclara que no hay comisión propia por reserva.

**Problemas**

- El valor, trial y precio solo existen como constantes de frontend.
- No se aclara IVA/impuestos, actualización del precio, fecha de pago, mora, baja o soporte incluido.
- No existe contrato/condiciones SaaS para el club; los términos actuales están orientados al jugador.
- El módulo de gimnasio obliga a abrir una conversación sin siquiera un rango de precio o criterio.
- La calculadora puede producir un argumento demasiado simplificado si no contempla el margen real del club; debe presentarse como equivalencia de facturación, no recuperación neta.

### 3.9 Formularios de contacto/demo

No existe un formulario de lead o solicitud de demo. Los únicos canales son:

- WhatsApp de ventas con mensaje precargado.
- Email mediante `mailto:`.

Consecuencias:

- un visitante puede hacer clic y no enviar el mensaje sin que quede señal;
- no se capturan ciudad, cantidad de canchas, modo actual de trabajo o urgencia;
- no existe consentimiento comercial registrado;
- no hay deduplicación ni estado de seguimiento;
- no se puede atribuir una venta a campaña o CTA;
- no se puede automatizar seguimiento de leads sin respuesta.

### 3.10 Analytics y tracking de conversión

**Lo que ya existe**

El sistema registra eventos propios en `page_event`, con sesión anónima, UTM, referrer, dispositivo y retención de 12 meses. El recorrido del jugador incluye vistas, búsquedas, clics de resultado, pasos, selección de horario, envío de checkout, reserva creada/fallida, link vencido y lista de espera.

Es una base técnicamente buena y evita depender de terceros.

**Brechas**

- La landing solo genera `VIEW`; no hay eventos para CTA, interacción con demo, pricing, FAQ, email o WhatsApp.
- `PageEventName` está diseñado alrededor de la reserva del jugador, no de la adquisición B2B.
- No existe entidad `Lead` ni relación lead → tenant.
- No hay eventos de alta/login de jugador ni club.
- No existe definición persistida de activación.
- No existe entidad/estado de suscripción o cliente pago.
- Los datos de `page_event` solo son consultables por SQL; no hay dashboard de conversión.
- No hay alertas de caída de conversión o errores comerciales.

Por eso el sistema puede calcular “visita de jugador → reserva”, pero no “visita de dueño → cliente pago”.

### 3.11 Emails y notificaciones

**Implementado**

- Email de confirmación de cuenta del jugador.
- Email de recuperación de contraseña del jugador.
- Email de lista de espera como fallback.
- Plantillas de WhatsApp para confirmación, pago, cancelación, vencimiento, recordatorio y lista de espera.
- Registro de intentos de WhatsApp y alertas de fallos.

**Brechas**

- WhatsApp está apagado por defecto y necesita número/plantillas aprobadas para operar en producción.
- Existe el tipo de recordatorio, pero no se encontró un productor/job que publique el evento `REMINDER`.
- No hay email de confirmación de reserva para invitado porque el checkout no captura email.
- No hay bienvenida al dueño, invitación segura de primer acceso ni recuperación de contraseña del panel.
- No hay mensajes de onboarding, trial por vencer, trial vencido, activación lograda, pago recibido o pago atrasado.
- No hay resumen periódico de desempeño para el dueño.

## 4. Embudo SaaS propuesto

### 4.1 Estado actual y objetivo

| Etapa | Señal actual | Problema | Señal objetivo |
|---|---|---|---|
| Visitante | `VIEW` sobre `/` | Se conoce la visita, no la intención | Sesión, campaña, CTA y contenido consumido |
| Lead | No existe | El contacto sale del producto | Lead persistido con canal, club, ciudad, canchas y fuente |
| Usuario | `club_user`, creado manualmente | No se conoce relación con lead ni primer acceso | Lead convertido a tenant + invitación aceptada |
| Usuario activado | No existe definición | No se sabe quién llegó al primer valor | Checklist completo + primera reserva de prueba/real |
| Cliente pago | No existe | El pago mensual vive fuera del sistema | Suscripción/trial y cobros manuales registrados |

### 4.2 Definición recomendada de activación

Un club debería considerarse **configurado** cuando:

1. tiene al menos una cancha activa;
2. tiene horarios publicables;
3. tiene una tarifa general o regla aplicable;
4. completó nombre, WhatsApp y ubicación mínima;
5. el dueño aceptó la invitación e inició sesión.

Debería considerarse **activado** cuando, además:

6. abrió/previsualizó su página pública;
7. copió o compartió el link;
8. completó una reserva de prueba o recibió su primera reserva real.

Mercado Pago no debe ser obligatorio para activar: el producto soporta legítimamente reservas de palabra. Sí debe registrarse como una mejora posterior del nivel de adopción.

### 4.3 Métricas recomendadas

- Conversión landing → lead.
- Conversión por CTA y fuente/UTM.
- Lead → trial creado.
- Trial creado → primer login.
- Primer login → club configurado.
- Club configurado → primera reserva.
- Tiempo mediano a primera reserva.
- Trial → activado.
- Activado → pago.
- Días hasta pago.
- Retención de clubes a 30/60/90 días.
- Clubes activos mensuales: al menos una reserva confirmada o carga operativa en el período.
- Reservas confirmadas por club y proporción web/manual.
- Conversión del jugador por club: visita → horario → checkout → reserva.

Métrica norte sugerida para esta etapa: **clubes activos con al menos una reserva confirmada en los últimos 30 días**. Obliga a medir adquisición y valor real, no solo cuentas creadas.

## 5. Plan priorizado

Los esfuerzos asumen una persona desarrollando, incluyen pruebas automatizadas y QA básico, y no incluyen tiempos externos de revisión legal, aprobación de plantillas de Meta o credenciales de proveedores.

### P0 — Necesarios para vender de forma repetible

#### P0.1 Capturar leads dentro del producto

**Problema encontrado:** los CTA comerciales abandonan el sitio hacia WhatsApp/email y no existe un lead persistido.

**Impacto comercial:** no se puede medir conversión, recuperar un contacto que no envió el mensaje, priorizar oportunidades ni atribuir ventas.

**Solución propuesta:** agregar un formulario corto de “Pedí tu prueba” con nombre, club, WhatsApp, ciudad, cantidad de canchas y comentario opcional. Guardar fuente, UTM, referrer y CTA. Después del submit, ofrecer abrir WhatsApp con un mensaje contextual ya armado. Incorporar estado inicial `NEW` y deduplicación básica por teléfono/email.

**Archivos/componentes afectados:**

- `player-app/src/pages/marketing/ClosingSection.tsx`
- `player-app/src/pages/marketing/PricingSection.tsx`
- `player-app/src/pages/marketing/Cta.tsx`
- `player-app/src/pages/marketing/config.ts`
- nuevo componente `LeadForm.tsx`
- nueva entidad/repositorio/servicio/controlador/DTO de lead
- nueva migración Flyway
- `SecurityConfig.java`

**Esfuerzo estimado:** 4–6 días.

#### P0.2 Crear un backoffice mínimo para provisionar clubes y enviar invitaciones

**Problema encontrado:** no hay en el repositorio un flujo productivo para crear tenant + dueño; solo existe el seeder de desarrollo.

**Impacto comercial:** cada venta requiere intervención técnica o SQL, con riesgo de errores de seguridad, configuración y seguimiento.

**Solución propuesta:** crear una consola restringida a plataforma que convierta un lead en club, reserve slug, cree owner deshabilitado hasta aceptar invitación, defina fechas de trial y envíe un link de primer acceso. Debe permitir ver estado de onboarding y desactivar el tenant sin borrar datos.

**Archivos/componentes afectados:**

- `Tenant.java`, `ClubUser.java`, `UserRole.java`
- `TenantService.java`, `ClubUserService.java`
- `ClubUserDetailsService.java`, `AdminTenantFilter.java`
- nuevas vistas/servicios de plataforma
- `EmailTemplates.java`
- nuevas tablas/campos y migración Flyway

**Esfuerzo estimado:** 7–10 días.

#### P0.3 Modelar trial, suscripción y cobro manual

**Problema encontrado:** los 7 días gratis, el plan mensual y el pago por transferencia solo existen en copy de frontend.

**Impacto comercial:** el equipo no puede saber quién está en prueba, quién venció, quién debe pagar o qué precio se acordó. La promesa comercial no tiene soporte operativo.

**Solución propuesta:** incorporar estados `TRIAL`, `ACTIVE`, `PAST_DUE`, `CANCELLED`, fechas de inicio/fin, precio acordado y un registro simple de cobros manuales. No hace falta automatizar pagos en P0: alcanza con registrar transferencia, próximo vencimiento y responsable. Definir explícitamente qué acceso conserva un trial vencido.

**Archivos/componentes afectados:**

- nueva entidad `Subscription` o campos comerciales separados de `Tenant`
- repositorio/servicio de suscripción
- migración Flyway
- consola de plataforma
- `MainLayout.java` para estado visible al dueño
- landing/pricing para leer una configuración consistente o mantener copy sincronizado mediante tests

**Esfuerzo estimado:** 5–8 días.

#### P0.4 Onboarding guiado y control de “listo para publicar”

**Problema encontrado:** un club nuevo aterriza en una agenda operativa y debe descubrir una configuración extensa.

**Impacto comercial:** aumenta el tiempo a valor, la dependencia del vendedor y la probabilidad de que una prueba nunca reciba una reserva.

**Solución propuesta:** crear una home/checklist para tenants no activados: datos básicos, cancha, horario, tarifa, método de reserva, preview, reserva de prueba y compartir link. Calcular readiness desde datos reales, no solo desde checkboxes guardados. Ocultar el checklist después de activar, con opción de reabrirlo.

**Archivos/componentes afectados:**

- nueva `OnboardingView.java`
- `MainLayout.java`
- `AgendaView.java`
- `SettingsView.java` o extracción de formularios reutilizables
- `TenantService.java`, repositorios de canchas/horarios/tarifas
- nuevos campos/eventos de onboarding y tests

**Esfuerzo estimado:** 8–12 días.

#### P0.5 Recuperación de acceso e invitación segura para dueños

**Problema encontrado:** el panel muestra “escribinos” cuando se olvida la clave y no existe primer acceso seguro.

**Impacto comercial:** genera soporte evitable y riesgo de compartir contraseñas iniciales conocidas por el vendedor.

**Solución propuesta:** reutilizar la infraestructura de tokens/email con flujos separados para club: aceptar invitación, definir contraseña y recuperar contraseña. Invalidar tokens al usar y registrar fecha del primer login.

**Archivos/componentes afectados:**

- `LoginView.java`
- `ClubUser.java`, `ClubUserService.java`
- nuevas vistas/endpoints de invitación y reset
- `EmailTemplates.java`, `EmailSender.java`
- `SecurityConfig.java`
- migración Flyway

**Esfuerzo estimado:** 4–6 días.

#### P0.6 Hacer confiable la demo comercial

**Problema encontrado:** los CTA dependen de `/club/simon`; el código reserva ese slug, pero no provisiona ni verifica sus datos fuera de la base productiva.

**Impacto comercial:** un CTA de alta intención puede terminar en 404, sin horarios o con datos obsoletos.

**Solución propuesta:** definir un mecanismo explícito de provisioning/reset del tenant demo, disponibilidad futura controlada, marca visible de demo y un smoke test/health check del recorrido. Evitar que sus reservas contaminen métricas reales y búsqueda pública.

**Archivos/componentes afectados:**

- `player-app/src/pages/marketing/Cta.tsx`
- `CourtSearchService.java`
- nuevo seeder bajo perfil demo o herramienta administrativa idempotente
- `SeoPageRenderer.java`
- tests de integración de demo

**Esfuerzo estimado:** 2–4 días.

#### P0.7 Cerrar dependencias operativas y contractuales de producción

**Problema encontrado:** WhatsApp automático depende de configuración externa y está apagado por defecto; los términos actuales cubren principalmente al jugador, no la relación SaaS con el club.

**Impacto comercial:** se puede vender una promesa que el entorno productivo no está listo para cumplir y no quedan claras suscripción, soporte, datos, baja y responsabilidades B2B.

**Solución propuesta:** checklist de readiness de producción para SMTP, Mercado Pago, WhatsApp y dominio/remitente; prueba de entrega de cada plantilla; decidir explícitamente si la primera versión comercial opera con WhatsApp manual o automático y alinear el copy. Crear condiciones comerciales para clubes y política de tratamiento de datos, con revisión profesional. Migrar email comercial/legal a dominio propio.

**Archivos/componentes afectados:**

- `README.md` y configuración de despliegue
- `AppProperties.java`, `application.yml`
- `PrivacyPage.tsx`, `TermsPage.tsx` y nueva página de condiciones para clubes
- `config.ts`
- `NotificationService.java`, plantillas y smoke tests

**Esfuerzo estimado:** 2–4 días técnicos más revisión legal/operativa externa.

### P1 — Alto impacto en conversión y activación

#### P1.1 Instrumentar el embudo comercial completo

**Problema encontrado:** `page_event` no registra acciones comerciales y no se enlaza con lead, tenant o pago.

**Impacto comercial:** no se sabe qué mensaje o canal vende, ni dónde se pierde cada prospecto.

**Solución propuesta:** sumar eventos tipados `MARKETING_CTA_CLICK`, `DEMO_START`, `DEMO_COMPLETE`, `PRICING_INTERACTION`, `LEAD_SUBMITTED`, `TRIAL_STARTED`, `FIRST_LOGIN`, `CLUB_CONFIGURED`, `LINK_COPIED`, `FIRST_BOOKING` y `SUBSCRIPTION_ACTIVATED`. Mantener datos sensibles en entidades comerciales, no en propiedades libres de analytics.

**Archivos/componentes afectados:**

- `player-app/src/analytics.ts`
- componentes de marketing y demo
- `PageEventName.java`, `PageEventDtos.java`, `PageEventService.java`
- `MainLayout.java` y servicios de onboarding/suscripción
- migración y tests

**Esfuerzo estimado:** 4–6 días después de P0.1–P0.3.

#### P1.2 Dashboard de conversión para plataforma

**Problema encontrado:** la información existente requiere consultas SQL manuales.

**Impacto comercial:** el equipo no puede gestionar adquisición y onboarding en la rutina diaria.

**Solución propuesta:** vista de plataforma con embudo por período/fuente, leads pendientes, trials por vencer, tiempo a activación, errores de checkout y clientes pagos. Separarla de las estadísticas de cada club.

**Archivos/componentes afectados:**

- `PageEventRepository.java`
- nuevos repositorios/servicios de métricas comerciales
- nueva vista de plataforma
- navegación y permisos de `SUPER_ADMIN`

**Esfuerzo estimado:** 5–8 días.

#### P1.3 Mejorar prueba social y confianza de la landing

**Problema encontrado:** hay una demostración fuerte, pero no evidencia externa de adopción o resultados.

**Impacto comercial:** un dueño debe confiar en promesas propias antes de entregar agenda, clientes y cobros.

**Solución propuesta:** agregar uno o dos casos reales con permiso, resultado concreto, captura identificable y cita; mostrar identidad comercial, soporte y ubicación. No inventar métricas: comenzar con evidencia cualitativa o datos verificables.

**Archivos/componentes afectados:**

- `Landing.tsx`
- nuevo `ProofSection.tsx`
- assets autorizados
- `landing.css`
- metadatos SEO si se crea una página de caso

**Esfuerzo estimado:** 2–4 días, sujeto a conseguir evidencia y permisos.

#### P1.4 Panel demo guiado para compradores

**Problema encontrado:** “Ver un club real” muestra la experiencia del jugador, pero el comprador evalúa principalmente el panel.

**Impacto comercial:** la propuesta de operación diaria sigue dependiendo de capturas y simulación.

**Solución propuesta:** crear una demo de panel de solo lectura o una visita guiada con dataset reseteable. Mostrar agenda, carga manual, cobro, caja y estadísticas sin permitir mutaciones peligrosas.

**Archivos/componentes afectados:**

- seguridad/roles demo del panel
- `MainLayout.java`, `AgendaView.java`, `CajaView.java`, `DashboardView.java`
- dataset demo y reseteo
- CTA de landing

**Esfuerzo estimado:** 6–10 días.

#### P1.5 Estados vacíos orientados a siguiente acción

**Problema encontrado:** varios vacíos administrativos informan, pero no activan.

**Impacto comercial:** el usuario nuevo entiende que no hay datos, pero no cómo generar valor.

**Solución propuesta:** distinguir vacío de configuración de vacío natural. Incorporar CTA contextual: configurar cancha, crear reserva de prueba, copiar link, buscar turno o volver al club.

**Archivos/componentes afectados:**

- `AgendaView.java`
- `DashboardView.java`
- `CajaView.java`
- `CustomersView.java`
- `AccountPage.tsx`
- `SearchPage.tsx`

**Esfuerzo estimado:** 3–5 días.

#### P1.6 Comunicaciones de activación y trial

**Problema encontrado:** no hay secuencia de acompañamiento al dueño ni alertas comerciales.

**Impacto comercial:** el trial puede vencer sin que el club llegue al primer valor y el equipo se entera tarde.

**Solución propuesta:** emails/eventos para invitación, bienvenida, configuración incompleta, primera reserva, trial a 3/1 días, trial vencido y pago registrado. Notificar también al responsable comercial cuando un trial se estanca.

**Archivos/componentes afectados:**

- `EmailTemplates.java`
- nuevos jobs y servicio de lifecycle
- entidades de onboarding/suscripción
- configuración de remitente
- tests de templates

**Esfuerzo estimado:** 4–6 días.

#### P1.7 Afinar pricing y expectativas comerciales

**Problema encontrado:** faltan condiciones del abono y del módulo de gimnasio; el precio vive hardcodeado en frontend.

**Impacto comercial:** genera preguntas evitables y riesgo de desalineación entre lo publicado y lo cobrado.

**Solución propuesta:** aclarar impuestos, alta, soporte, permanencia, baja, actualización y qué ocurre al finalizar trial. Definir precio o criterio explícito del gimnasio. Centralizar la configuración comercial o proteger la consistencia con tests.

**Archivos/componentes afectados:**

- `config.ts`
- `PricingSection.tsx`
- `GymSection.tsx`
- `FaqSection.tsx`
- condiciones para clubes

**Esfuerzo estimado:** 1–3 días más decisión comercial.

#### P1.8 Completar medición y recuperación del jugador

**Problema encontrado:** no se mide alta/login y el invitado no tiene canal de confirmación persistente fuera del link/navegador.

**Impacto comercial:** se pierde visibilidad sobre retención de jugadores y puede aumentar soporte por turnos “perdidos”.

**Solución propuesta:** instrumentar registro/login/claim de turnos; mejorar reenvío de código; probar captura opcional de email después de reservar, sin agregarlo al checkout principal; activar recordatorios solo cuando exista un canal confiable.

**Archivos/componentes afectados:**

- `LoginPage.tsx`, `AuthContext.tsx`, `Checkout.tsx`, `AccountPage.tsx`
- analytics y catálogo de eventos
- `EmailTemplates.java`, `NotificationService.java`
- job real de recordatorios

**Esfuerzo estimado:** 4–7 días.

### P2 — Mejoras posteriores

#### P2.1 Alta y checkout SaaS autoservicio

**Problema encontrado:** toda venta requiere intervención humana.

**Impacto comercial:** limita escala y atención fuera de horario.

**Solución propuesta:** permitir que el dueño cree el club, configure lo mínimo y contrate online. Hacerlo después de aprender con onboarding concierge y medir dónde se traban los primeros clientes.

**Archivos/componentes afectados:** landing, nuevo flujo de alta, provisión, billing, seguridad y emails.

**Esfuerzo estimado:** 15–25 días.

#### P2.2 Segmentación y experimentación

**Problema encontrado:** la landing usa un único mensaje para clubes de tamaños y necesidades distintas.

**Impacto comercial:** puede dejar valor sin comunicar a clubes con gimnasio, múltiples canchas o mayor volumen.

**Solución propuesta:** segmentar mensajes/casos y agregar experimentos first-party sobre titular, CTA y formulario, con asignación persistente y métricas de lead/pago.

**Archivos/componentes afectados:** landing, analytics, modelo de experimento y dashboard.

**Esfuerzo estimado:** 5–8 días.

#### P2.3 Señales de product-qualified lead y seguimiento

**Problema encontrado:** todos los leads se tratan igual.

**Impacto comercial:** el tiempo de ventas no se concentra en quienes muestran intención o progreso.

**Solución propuesta:** puntuar señales como demo completada, precio visto, respuesta, primer login, configuración parcial y link copiado. Crear tareas de seguimiento, sin convertir el producto en un CRM completo.

**Archivos/componentes afectados:** leads, analytics, onboarding y vista de plataforma.

**Esfuerzo estimado:** 4–6 días.

#### P2.4 Programa de referidos y crecimiento de la red

**Problema encontrado:** la búsqueda entre clubes tiene valor de red, pero no se usa como mecanismo de adquisición.

**Impacto comercial:** se desaprovecha el incentivo de sumar oferta local y capturar demanda no atendida.

**Solución propuesta:** detectar búsquedas sin resultados por zona/franja, usar ese dato en ventas y habilitar referidos de clubes. Mostrar al club cuántas búsquedas potenciales existen en su zona, con cuidado de no prometer demanda garantizada.

**Archivos/componentes afectados:** analytics de búsqueda, dashboard de plataforma, landing y modelo de referidos.

**Esfuerzo estimado:** 6–10 días.

#### P2.5 Resumen periódico de negocio para el dueño

**Problema encontrado:** las estadísticas existen, pero requieren que el dueño entre a buscarlas.

**Impacto comercial:** se reduce percepción recurrente de valor y oportunidad de retención.

**Solución propuesta:** email semanal/mensual con reservas, ocupación, facturación, web vs manual, cancelaciones y recomendación accionable.

**Archivos/componentes afectados:** `BookingStatsService.java`, templates de email, scheduler y preferencias de notificación.

**Esfuerzo estimado:** 3–5 días.

## 6. Secuencia recomendada de implementación

### Ola 1 — Base comercial mínima

1. P0.1 Captura de leads.
2. P0.2 Provisioning e invitación.
3. P0.3 Trial/suscripción manual.
4. P0.5 Recuperación de acceso.
5. P0.6 Demo confiable.
6. P0.7 Readiness operativo/contractual.

Resultado esperado: se puede tomar una oportunidad, convertirla en trial, darle acceso seguro, conocer su estado y registrar que pagó sin tocar SQL.

### Ola 2 — Activación

1. P0.4 Checklist y readiness.
2. P1.5 Estados vacíos accionables.
3. P1.6 Comunicaciones de trial.
4. P1.8 Medición del jugador.

Resultado esperado: baja el tiempo a primera reserva y el trial deja de depender exclusivamente del seguimiento manual.

### Ola 3 — Optimización comercial

1. P1.1 Instrumentación completa.
2. P1.2 Dashboard de conversión.
3. P1.3 Prueba social.
4. P1.4 Panel demo.
5. P1.7 Pricing.

Resultado esperado: el equipo puede explicar qué canal convierte, dónde se caen los prospects y qué acciones mejoran activación/pago.

## 7. Criterios para aprobar la salida comercial

Antes de invertir en adquisición paga, deberían poder responderse desde el sistema estas preguntas:

- ¿Cuántos visitantes dueños llegaron esta semana?
- ¿Cuántos pidieron una demo o prueba y desde qué CTA/campaña?
- ¿Qué leads todavía no recibieron respuesta?
- ¿Qué clubs están en trial y cuándo vencen?
- ¿Quién nunca inició sesión?
- ¿Quién todavía no configuró canchas, horarios o tarifas?
- ¿Quién compartió el link?
- ¿Quién recibió su primera reserva y cuánto tardó?
- ¿Quién se convirtió en cliente pago?
- ¿Qué cliente está atrasado o canceló?

Además, el recorrido mínimo de venta debe poder completarse sin edición directa de base de datos:

`crear lead → provisionar club → invitar dueño → configurar → probar → compartir → recibir primera reserva → registrar pago`

## 8. Veredicto

TurnosPadel no necesita rehacer su propuesta ni su motor de reservas. El valor central existe, se entiende y tiene diferenciales defendibles. La prioridad es construir alrededor de ese producto la infraestructura mínima de comercialización y activación.

La decisión correcta para esta etapa no es saltar directamente a signup y billing totalmente autoservicio. Primero conviene formalizar la venta asistida que ya plantea la landing: capturar el lead, provisionar sin SQL, guiar el trial, medir la primera reserva y registrar el pago. Con esa base se podrá aprender de clientes reales y recién después decidir cuánto autoservicio conviene construir.

