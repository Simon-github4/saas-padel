# Módulo gimnasio

Socios con DNI, cuotas por periodo con tope de días por semana, y check-in por
QR, en una o varias sedes. Vive en este paquete, en la app `gym-app/` y en las
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
2. **Socios**: dar de alta al socio (nombre y DNI) y cobrar su cuota (desde cuándo,
   meses, días por semana, sedes).

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

## Desarrollo

Con el perfil `dev`, `GymDevSeeder` prende el gimnasio en `club-necochea` (solo DNI) y
carga dos sedes (Necochea y Quequén, sin ubicación) y tres socios: `30111222` (cuota
vigente), `40222333` (vencida) y `50333444` (sin cuota). Si se prende el modo clave, la
de los tres es `gimnasio1234`.

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
- Pasado el tope semanal se rechaza; el mostrador puede dejar pasar (queda marcado
  como excepción). El mostrador solo registra ingresos de HOY.
- Un socio no puede tener dos cuotas que se pisen (constraint en la base).
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
