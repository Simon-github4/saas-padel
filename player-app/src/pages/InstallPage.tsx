import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { AccountButton } from '../components/AccountButton';
import { BrandIcon } from '../components/BrandLogo';
import { TemaSwitch } from '../components/TemaSwitch';
import { Alert, Button, Card, Screen, TopBar, WhatsappLink } from '../components/Ui';
import { promptInstall, useInstallState } from '../pwa';
import { setPageMeta } from '../seo';
import { BRAND } from './marketing/config';

type Telefono = 'android' | 'ios';

type Paso = {
  titulo: string;
  texto: ReactNode;
  /** Lo que el jugador tiene que buscar en la pantalla, dibujado como lo va a ver. */
  muestra?: ReactNode;
};

/**
 * Guía para poner la app en el inicio del celular.
 *
 * <p>Pensada para quien nunca instaló una app desde una página: pasos cortos, letra
 * grande y, en cada uno, el dibujo del botón que hay que tocar, porque "el botón de
 * compartir" no le dice nada a quien no sabe cuál es. Abre en los pasos del teléfono
 * que se está usando, y deja cambiar al otro para ayudar a alguien más.
 *
 * <p>En Android con Chrome, si el navegador ya ofrece instalar, arriba de todo va un
 * botón que lo hace en un toque y los pasos quedan como plan B.
 */
export function InstallPage() {
  const install = useInstallState();
  const [telefono, setTelefono] = useState<Telefono>(install.platform === 'ios' ? 'ios' : 'android');
  const [rechazado, setRechazado] = useState(false);

  useEffect(
    () =>
      setPageMeta(
        `Instalá la app · ${BRAND}`,
        'Tené TurnosPadel en el inicio del celular y buscá cancha de pádel con un toque. Paso a paso para Android y iPhone.',
      ),
    [],
  );

  const pasos = telefono === 'ios' ? pasosIphone(install.iosNotSafari) : pasosAndroid();
  const enCompu = install.platform === 'desktop';
  const link = `${window.location.origin}/instalar`;

  return (
    <Screen top={<TopBar name={BRAND} titleTo="/buscar" brandMark accountSlot={<AccountButton />} />}>
      <TemaSwitch recordar="tema-buscador" />

      <div className="pt-10 text-center">
        <div className="mx-auto grid size-24 place-items-center rounded-[1.6rem] border border-cal/10 bg-[#0a0a0a] [box-shadow:var(--shadow-card)]">
          <BrandIcon className="size-16" />
        </div>
        <h1 className="mt-6 text-[clamp(2.75rem,12vw,3.75rem)] tracking-[0.06em]">Instalá la app</h1>
        <p className="mx-auto mt-3 max-w-sm text-lg leading-relaxed text-ink-soft">
          Tené {BRAND} en el inicio del celular, como cualquier otra app. La abrís con un toque y vas
          directo a buscar cancha, sin pasar por Google.
        </p>
        <p className="eyebrow mt-5 text-ladrillo-claro">Gratis · No ocupa lugar · Sin tienda de apps</p>
      </div>

      {install.platform === 'installed' ? (
        <Card className="mt-10 space-y-4 text-center">
          <p className="text-2xl font-bold">¡Ya la tenés instalada!</p>
          <p className="text-ink-soft">Buscá el ícono de {BRAND} en el inicio de tu celular.</p>
          <Link
            to="/buscar"
            className="block w-full rounded-full bg-ladrillo px-5 py-4 text-base font-bold uppercase tracking-[0.12em] text-cal [box-shadow:var(--shadow-glow)]"
          >
            Buscar cancha
          </Link>
        </Card>
      ) : (
        <>
          {install.canPrompt && (
            <Card className="mt-10 space-y-3 text-center">
              <p className="text-lg font-bold">Se instala con un solo toque</p>
              <Button
                variant="accent"
                className="py-4 text-base"
                onClick={() => void promptInstall().then((aceptada) => setRechazado(!aceptada))}
              >
                Instalar ahora
              </Button>
              {rechazado && (
                <p className="text-sm text-ink-soft">
                  No se instaló. Si cambiás de idea, tocá el botón de nuevo o seguí los pasos de abajo.
                </p>
              )}
            </Card>
          )}

          {enCompu && (
            <div className="mt-10">
              <Alert tone="info">
                <p className="text-base text-cal">Estás en una computadora.</p>
                <p className="mt-1">
                  Abrí esta página desde el celular donde querés la app:{' '}
                  <span className="font-semibold text-cal">{link.replace(/^https?:\/\//, '')}</span>
                </p>
              </Alert>
            </div>
          )}

          {install.inAppBrowser && (
            <div className="mt-10">
              <Alert tone="info">
                <p className="text-base font-semibold text-cal">Primero abrila en el navegador</p>
                <p className="mt-1 text-base">
                  Abriste este link desde otra app (Instagram, Facebook…) y desde ahí no se puede instalar.
                  Tocá los tres puntitos de arriba y elegí <strong>Abrir en {telefono === 'ios' ? 'Safari' : 'Chrome'}</strong>{' '}
                  o <strong>Abrir en el navegador</strong>.
                </p>
              </Alert>
            </div>
          )}

          <section className="mt-12" aria-labelledby="pasos-titulo">
            <h2 id="pasos-titulo" className="text-center text-3xl tracking-[0.06em]">
              {install.canPrompt ? 'O hacelo a mano' : 'Seguí estos pasos'}
            </h2>

            <div
              role="tablist"
              aria-label="Tipo de celular"
              className="mx-auto mt-5 grid max-w-sm grid-cols-2 gap-1 rounded-full border border-cal/10 bg-vidrio p-1"
            >
              {(
                [
                  ['android', 'Android'],
                  ['ios', 'iPhone'],
                ] as const
              ).map(([valor, etiqueta]) => (
                <button
                  key={valor}
                  type="button"
                  role="tab"
                  aria-selected={telefono === valor}
                  onClick={() => setTelefono(valor)}
                  className={`rounded-full px-4 py-3 text-base font-bold transition ${
                    telefono === valor ? 'bg-cal text-pista' : 'text-ink-soft hover:text-cal'
                  }`}
                >
                  {etiqueta}
                </button>
              ))}
            </div>
            <p className="mt-3 text-center text-sm text-ink-soft">
              {telefono === 'ios' ? 'Desde Safari, el navegador de la brújula.' : 'Desde Chrome, el navegador de Google.'}
            </p>

            <ol className="mt-8 space-y-4">
              {pasos.map((paso, index) => (
                <li key={paso.titulo}>
                  <Card className="flex gap-4">
                    <span
                      aria-hidden
                      className="display grid size-11 shrink-0 place-items-center rounded-full bg-ladrillo text-2xl text-cal"
                    >
                      {index + 1}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="text-xl font-bold leading-snug">{paso.titulo}</p>
                      <div className="mt-1.5 text-base leading-relaxed text-ink-soft">{paso.texto}</div>
                      {paso.muestra && <div className="mt-4">{paso.muestra}</div>}
                    </div>
                  </Card>
                </li>
              ))}
            </ol>
          </section>
        </>
      )}

      <section className="mt-14 text-center">
        <h2 className="text-3xl tracking-[0.06em]">¿Juegan con vos?</h2>
        <p className="mx-auto mt-2 max-w-sm text-ink-soft">
          Pasales la app al grupo, así la instalan también.
        </p>
        <div className="mt-5">
          <WhatsappLink
            href={`https://wa.me/?text=${encodeURIComponent(
              `Para reservar cancha de pádel sin vueltas, instalá ${BRAND} en el celu: ${link}`,
            )}`}
          >
            Compartir por WhatsApp
          </WhatsappLink>
        </div>
      </section>

      <footer className="mt-16 border-t border-cal/10 pt-8 text-center">
        <Link
          to="/buscar"
          className="eyebrow text-ink-soft underline-offset-4 transition hover:text-cal hover:underline"
        >
          Seguir sin instalar: buscar cancha
        </Link>
      </footer>
    </Screen>
  );
}

function pasosIphone(otroNavegador: boolean): Paso[] {
  return [
    {
      titulo: 'Tocá el botón Compartir',
      texto: otroNavegador ? (
        <>
          Estás en otro navegador: el botón está arriba, al lado de la dirección. Si después no aparece{' '}
          <strong>Agregar a inicio</strong>, abrí esta página en Safari.
        </>
      ) : (
        <>
          Es el cuadradito con una flecha para arriba, en la barra de Safari. Si no lo ves, tocá primero los
          tres puntitos <strong>(···)</strong> de abajo a la derecha.
        </>
      ),
      muestra: (
        <div className="flex items-center gap-3">
          <Tecla>
            <CompartirIcono />
          </Tecla>
          <span className="text-sm text-ink-soft">o</span>
          <Tecla>
            <PuntosIcono horizontal />
          </Tecla>
        </div>
      ),
    },
    {
      titulo: 'Tocá "Ver más"',
      texto: 'Está abajo de las primeras opciones y abre la lista completa. Si ya ves Agregar a inicio, salteá este paso.',
      muestra: <Opcion icono={<VerMasIcono />}>Ver más</Opcion>,
    },
    {
      titulo: 'Tocá "Agregar a inicio"',
      texto: 'Bajá un poco en la lista hasta encontrarlo.',
      muestra: <Opcion icono={<AgregarIcono />}>Agregar a inicio</Opcion>,
    },
    {
      titulo: 'Tocá "Agregar"',
      texto: (
        <>
          Está arriba a la derecha. Si aparece <strong>Abrir como app web</strong>, dejalo prendido.
        </>
      ),
      muestra: <Tecla ancha>Agregar</Tecla>,
    },
    pasoFinal,
  ];
}

function pasosAndroid(): Paso[] {
  return [
    {
      titulo: 'Tocá los tres puntitos',
      texto: 'Están arriba a la derecha de Chrome.',
      muestra: (
        <Tecla>
          <PuntosIcono />
        </Tecla>
      ),
    },
    {
      titulo: 'Tocá "Instalar y crear acceso directo"',
      texto: (
        <>
          Bajá un poco en el menú hasta encontrarlo. En otros celulares dice <strong>Instalar app</strong> o{' '}
          <strong>Agregar a la pantalla principal</strong>.
        </>
      ),
      muestra: <Opcion icono={<InstalarIcono />}>Instalar y crear acceso directo</Opcion>,
    },
    {
      titulo: 'Tocá "Instalar"',
      texto: (
        <>
          Si te da a elegir, tocá <strong>Instalar</strong> y no <strong>Crear acceso directo</strong>: el acceso
          directo abre la página en Chrome, con la barra de arriba, y no queda como una app. ¿Usás{' '}
          <strong>Samsung Internet</strong>? Tocá las tres rayitas de abajo, después <strong>Agregar página a</strong>{' '}
          y <strong>Pantalla de inicio</strong>.
        </>
      ),
      muestra: (
        <div className="flex flex-wrap items-center gap-3">
          <Tecla ancha>Instalar</Tecla>
          <span className="text-sm text-ink-mute line-through">Crear acceso directo</span>
        </div>
      ),
    },
    pasoFinal,
  ];
}

const pasoFinal: Paso = {
  titulo: '¡Listo! Buscá este ícono',
  texto: 'Queda en la pantalla de inicio, junto a tus otras apps. Tocalo y vas directo a buscar cancha.',
  muestra: (
    <div className="inline-flex flex-col items-center gap-1.5">
      <img src="/icon-192.png" alt={`Ícono de ${BRAND}`} className="size-16 rounded-2xl ring-1 ring-cal/15" />
      <span className="text-xs font-semibold text-cal">{BRAND}</span>
    </div>
  ),
};

/* ------------------------------------------------ lo que se ve en la pantalla */

/** Un botón del teléfono, dibujado para reconocerlo: redondo con el ícono, o con texto. */
function Tecla({ children, ancha = false }: { children: ReactNode; ancha?: boolean }) {
  return (
    <span
      className={`inline-grid place-items-center rounded-2xl border border-cal/15 bg-vidrio-alto text-[#0a84ff] ${
        ancha ? 'h-12 px-6 text-lg font-bold' : 'size-14'
      }`}
    >
      {children}
    </span>
  );
}

/** Una opción del menú del teléfono, con su ícono a la derecha como la muestra el sistema. */
function Opcion({ children, icono }: { children: ReactNode; icono: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-xl border border-cal/15 bg-vidrio-alto px-4 py-3 text-base font-semibold text-cal">
      <span>{children}</span>
      <span className="text-ink-soft">{icono}</span>
    </div>
  );
}

function CompartirIcono() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-label="Compartir" className="size-7">
      <path d="M12 3v12" />
      <path d="M7.5 7.5 12 3l4.5 4.5" />
      <path d="M8 11H6a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-8a1 1 0 0 0-1-1h-2" />
    </svg>
  );
}

function PuntosIcono({ horizontal = false }: { horizontal?: boolean }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-label="Tres puntitos" className={`size-7 ${horizontal ? '' : 'text-cal'}`}>
      {horizontal ? (
        <>
          <circle cx="5" cy="12" r="2" />
          <circle cx="12" cy="12" r="2" />
          <circle cx="19" cy="12" r="2" />
        </>
      ) : (
        <>
          <circle cx="12" cy="5" r="2" />
          <circle cx="12" cy="12" r="2" />
          <circle cx="12" cy="19" r="2" />
        </>
      )}
    </svg>
  );
}

/** La flecha para abajo del "Ver más" de la hoja de compartir. */
function VerMasIcono() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden className="size-6">
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}

function AgregarIcono() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden className="size-6">
      <rect x="4" y="4" width="16" height="16" rx="4" />
      <path d="M12 8.5v7M8.5 12h7" />
    </svg>
  );
}

function InstalarIcono() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden className="size-6">
      <rect x="6" y="2.5" width="12" height="19" rx="2.5" />
      <path d="M12 7v7M9 11l3 3 3-3" />
    </svg>
  );
}
