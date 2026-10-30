# Módulo gimnasio

Socios con DNI, cuotas por periodo con tope de días por semana (contado por semana
o por mes, según el club), y check-in por QR, en una o varias sedes. Vive en este paquete, en la app `gym-app/` y en las
tablas `gym_*` (migración `V4__gimnasio.sql`), y está aislado del resto de
turnospadel a propósito: no se sabe si a futuro se integra o se separa.

## Cómo se prende para un club

El módulo está apagado por defecto. Se prende insertando una fila (una por club):

```sql
INSERT INTO gym_club_config (club_id)
SELECT id FROM tenant WHERE slug = 'los-troncos';
```

Para apagarlo: `UPDATE gym_club_config SET enabled = FALSE WHERE club_id = ...`
(o borrar la fila). Con el módulo apagado, la API del club responde 404 y el
grupo "Gimnasio" no aparece en el panel.

Después, desde el panel (`/admin`, entrando como dueño):

1. **Sedes y QR**: crear la sede, cargar su ubicación (ver abajo), imprimir su QR y
   pegarlo en la entrada.
2. **Socios**: dar de alta al socio (nombre y, si está a mano, DNI) y cobrar su
   cuota (desde cuándo, meses, días por semana, sedes).

El socio entra a `https://<host>/gym/<slug-del-club>` **con solo su DNI** y escanea
el QR de la entrada.

### Acceso con clave (opcional)

Por defecto alcanza el DNI: en un club de barrio el mostrador ve quién entra, y una
clave no frena el fraude real (prestar la cuenta a un conocido). Si un club prefiere
DNI + clave, se prende desde el panel (*Sedes y QR* → "Los socios entran con DNI y
clave") o con `UPDATE gym_club_config SET password_required = TRUE WHERE club_id = ...`.
Con eso, el alta muestra una clave temporal (una sola vez) que el socio cambia al
entrar, y aparece "Resetear clave". El código de las claves sigue entero; cambiar de
modo no toca las sesiones abiertas. Los socios dados de alta sin clave conocida tienen
que pasar por el mostrador ("Resetear clave") antes de entrar en modo clave.

### Ubicación de la sede

Si la sede tiene latitud y longitud, el socio tiene que estar a menos de `radius_meters`
(200 m por defecto, holgado porque el GPS falla adentro de un edificio) para registrar
el ingreso: es lo que impide hacerlo desde su casa con una foto del QR. La app pide la
ubicación del celular al escanear; el servidor rechaza con el código `LOCATION_REQUIRED`
si falta y con un mensaje con la distancia si está lejos. Sin coordenadas, la sede no
verifica nada. En el panel, *Editar sede* → "Usar la ubicación de este dispositivo"
(estando parado en el gimnasio), o pegar las coordenadas de Google Maps.

La ubicación la informa el celular: no frena a quien la falsifique a propósito, y el
mostrador puede registrar ingresos sin ella (está en la sede). Cada ingreso guarda a
cuántos metros estaba (`gym_checkin.distance_m`).

### Aviso en el panel

Cuando un socio entra escaneando el QR, el panel abierto muestra un cartel ("Ingreso:
Juan Pérez · Necochea", más "· Debe el mes" si entró por la gracia) con un toque corto,
distinto del de las reservas, e «Ingresos de hoy» se redibuja. Sale de `GymCheckinFeed`,
en memoria y después del commit, como el aviso de reservas web. Los ingresos que carga el
mostrador y los escaneos repetidos del día no suenan, y no van al celular: en hora pico
serían decenas. Cada navegador puede apagar el sonido desde el menú del usuario («Sonar
ingresos al gimnasio», guardado en `localStorage`); el cartel aparece igual.

## Desarrollo

Con el perfil `dev`, `GymDevSeeder` prende el gimnasio en `club-necochea` (solo DNI) y
carga dos sedes (Necochea y Quequén, sin ubicación) y tres socios: `30111222` (cuota
vigente), `40222333` (vencida) y `50333444` (sin cuota). Si se prende el modo clave, la
de los tres es `gimnasio1234`. Suma además los casos del mostrador, aunque la base ya
tuviera el gimnasio: socios sin DNI, el mismo socio cargado dos veces (Agustín Coupau),
uno que adeuda meses (Facundo Molina), uno adelantado, uno con ingresos y una cuota
anulada (Hernán Paz, `33444555`), uno deshabilitado, un cobro de ayer cargado hoy, una
cuota que empieza en unos días y uno en el tope semanal. Y los del pase de 15 días: uno
vigente (Tomás Paso, `39111222`), uno vencido, un mensual con el pase después, un pase con el
mensual después y un mensual que debía y pasó a pase (Ramiro Sosa, para anularlo). Y una socia
de 2 días que ya vino 9 veces en su mes (Valeria Sosa, `37999000`), con su cuota contada por mes:
queda en el tope, y la grilla muestra socios por semana y por mes mezclados.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd gym-app && npm run dev     # http://localhost:5175/gym/club-necochea
```

El link del QR sale de `app.base-url`: para que el QR del panel abra la app de
desarrollo, arrancar el backend con `--app.base-url=http://localhost:5175`.
Para probar sin cámara, pegar el link o el código en el campo del escáner.

## Reglas

- La sesión de la app dura un año desde el ÚLTIMO uso (se renueva sola): quien va al
  gimnasio no vuelve a entrar. El mostrador la corta al instante deshabilitando al socio.
- Un ingreso por socio por día calendario (en la zona horaria del club), sumando
  todas las sedes. La semana va de lunes a domingo.
- **Tope de ingresos** (`entry_limit` en `gym_club_config` y en cada `gym_membership`, migración
  `V19`): cada cuota guarda cómo se cuentan sus días por semana, y el tope de hoy sale de la cuota
  que rige hoy (`GymAllowance`). El dueño elige en «Cuotas» el modo de lo que se cobre desde ahora.
  - **Por semana** (lo normal): de lunes a domingo, tantos ingresos como días por semana.
  - **Por mes**: en el período de la cuota del socio (del 15 al 14 si arrancó el 15), no en el
    mes calendario. El tope es días por semana × días del período / 7, redondeado (nunca da
    ,5): 2 días son 8 en un período de 28 días y 9 en uno de 30 o 31; 7 días, todos los días.
    En la gracia (el mes impago) cuenta el período corriente; con un nuevo inicio de mes a
    futuro, la cuota de antes que cubre hoy; un pase de 15 días se cuenta en sus 15 días (2 días
    por semana son 4).
  - Cambiar de modo rige desde la próxima cuota de cada socio: lo toman las cuotas que se cobren
    desde ahora y las ya pagas que todavía no empezaron (un adelanto). La que corre hoy sigue con la
    regla con la que se pagó hasta que vence, y el que debe el mes sigue con el modo de su última
    cuota hasta que lo pague. Así nadie queda sin ingresos a mitad de lo que ya pagó. «Cobrar» avisa
    cuando esa cuota pasa al socio a contarse por mes, y «Cuotas cobradas» muestra el modo de cada una.
  - Mientras conviven los dos modos, la columna de «Socios» dice «Ingresos» y aclara en cada socio
    si es de la semana, del mes o del pase.
- Pasado el tope se rechaza; el mostrador puede dejar pasar (queda marcado
  como excepción). El mostrador solo registra ingresos de HOY.
- El DNI es opcional en el alta: los socios anotados sin DNI se cargan con el nombre y
  el DNI se completa después en "Cargar DNI y datos". Sin DNI no entra a la app, pero el
  mostrador le cobra y le registra ingresos. Si se cambia el DNI de un socio, se cierran
  sus sesiones abiertas.
- Un socio no puede tener dos cuotas que se pisen (constraint en la base).
- Una cuota cobrada se corrige desde el menú del socio, en "Cuotas cobradas": los días
  por semana y el monto cambian, las fechas no. Ahí también se anula la que se cobró de
  más (sale de la caja); si era la única, el socio vuelve a estar como el que nunca pagó.
- "Eliminar socio" es para el cargado por error o repetido: se borra con sus cuotas y
  sesiones. Si ya registró ingresos no se puede, para no perder su historia: se deshabilita.
- La fecha de cobro (`paid_on`) es aparte del período: un pago de otro día va a la caja
  de ese día, y pagar antes no adelanta el período.
- Al cobrar, "Nuevo inicio de mes" es opcional. Vacío, el ciclo sigue como venía. Con
  una fecha, el mes arranca de nuevo ese día: sirve para cargar a un socio que ya venía
  con su mes real, o para el que vuelve después de meses (los meses sin pagar de antes
  dejan de contar como deuda). No puede pisar lo que el socio ya tiene pago.
- **Pase de 15 días** (para el que viene de paso): se cobra en «Cobrar» → Plan "15 días", vale
  15 días calendario contando el primero y el último, y tiene su propia tarifa en «Cuotas».
  Arranca el día después de lo pago (o hoy), nunca para atrás. Cuando vence no genera deuda
  ni tiene gracia: queda "Venció el …". Solo se guarda el tipo de cada cuota
  (`gym_membership.plan_length`, migración `V18`); lo demás sale de las cuotas:
  - El **régimen** lo define la última cuota paga: si es un pase, el socio está "con pases" y
    solo cuenta la cobertura. Lo que vale hoy (tope, sedes, check-in) sale de la cuota que
    cubre hoy, que puede ser un mes que todavía corre.
  - **Mensual → pase**: el pase arranca después del mes pago y el ciclo mensual deja de
    proyectarse. Si el socio debía cuotas, el mostrador tiene que confirmar que dejan de
    cobrarse. Vender un pase no toca el ancla.
  - **Pase → mensual**: arranca un ciclo nuevo, por defecto el día después de lo pago (es un
    «Nuevo inicio de mes» precargado). Los días de pases no son deuda.
  - **Anular un pase** es "nunca se cobró": si había cerrado un plan mensual, el socio vuelve a
    ese plan con sus cuotas impagas (el diálogo lo avisa). Si después del pase se arrancó otro
    ciclo mensual que también se anuló, el pase ya no se puede anular: el ciclo de antes no se
    puede reconstruir.
  - Un socio sin pases sigue exactamente las reglas mensuales de siempre.
- En «Socios», "N activos" cuenta a los socios habilitados con la cuota de hoy paga (mensual o
  pase): el que debe el mes no cuenta aunque entre por la gracia, ni el que todavía no empezó.
  El filtro de al lado pone a cada socio en una sola situación (`GymOverviewService.Standing`):
  Activos, Adeudan (el mensual que debe el mes o más), Por empezar, Pase vencido (no debe nada),
  Sin cuota y Deshabilitados.
- El QR es un secreto estático por sede: quien lo fotografíe lo tiene. Se puede
  regenerar desde el panel, lo que invalida el cartel impreso. Con la ubicación de la
  sede cargada, tenerlo no alcanza: hay que estar cerca.
- El socio se deriva SIEMPRE de la sesión; el check-in nunca recibe un id de socio.

## Aislamiento

`GymArchitectureTest` (ArchUnit) hace cumplir dos reglas:

1. Nada fuera de `ar.com.padelnec.gym..` conoce este paquete, salvo `MainLayout`
   (agrega el grupo del menú).
2. Este paquete solo usa el **núcleo compartido**, y la lista es cerrada:
   `TenantContext`, `BaseEntity`, `TenantScopedEntity`, `Tenant`, `TenantService`,
   `ThemeMode`, `Tokens`, `TokenHash`, `BusinessRuleException`, `ResourceNotFoundException`,
   `UnauthorizedSessionException`, `MainLayout` y `ClubUserPrincipal`.

Además, ninguna tabla de padel apunta a `gym_*`, y desde `gym_*` solo hay FKs a
`tenant` y `club_user`. No toca `Tenant`, `SecurityConfig`,
`SpaForwardingController`, `ApiExceptionHandler` ni `player-app`: trae su propia
cadena de seguridad (`GymSecurityConfig`), sus rutas (`GymSpaController`) y su
manejo de errores (`GymExceptionHandler`).

## Cómo se extrae a otra aplicación

Copiar el paquete `gym`, la carpeta `gym-app/` y la migración, y reemplazar el
núcleo compartido de arriba por lo propio de la nueva app (multi-tenancy, tokens,
excepciones y el marco del panel). Como la lista es cerrada y la verifica el test,
ese es todo el trabajo.

## Pendiente (Fase 2 y 3)

- Liquidación entre sedes con socio externo (`gym_sede.partner_share_pct` ya
  existe): la cuota de cada membresía se atribuye a las sedes según las visitas.
  Confirmar la fórmula con la clienta antes de construirla.
- Pantalla de SUPER_ADMIN para prender el módulo (hoy es el `INSERT` de arriba).
- QR rotativo en una pantalla en la puerta (solo si la ubicación no alcanza) y
  empaquetar con Capacitor para las tiendas.
