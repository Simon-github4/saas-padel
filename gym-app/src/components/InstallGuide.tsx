import { useEffect, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { useGymAuth } from '../auth/GymAuthContext';
import { promptInstall, useInstallState } from '../pwa';
import { Alert, Button, Card } from './ui';

type Telefono = 'android' | 'ios';

type Paso = {
  titulo: string;
  texto: ReactNode;
  /** Lo que el socio tiene que buscar en la pantalla, dibujado como lo va a ver. */
  muestra?: ReactNode;
};

const dismissedKey = (slug: string) => `gym_install_tutorial_seen_${slug}`;

function readDismissed(slug: string) {
  try {
    return window.localStorage.getItem(dismissedKey(slug)) === '1';
  } catch {
    return false;
  }
}

function writeDismissed(slug: string) {
  try {
    window.localStorage.setItem(dismissedKey(slug), '1');
  } catch {
    // Si el navegador bloquea localStorage, solo se mostrara durante esta visita.
  }
}

/**
 * Botón "Instalar app" de la barra de arriba, con la guía paso a paso.
 *
 * <p>Va en la barra porque es lo único que se ve siempre, en todas las pantallas y
 * con cualquier scroll, sin tapar la cámara al escanear. Si Chrome ya ofrece
 * instalar, lo hace en un toque; si no, abre la guía. Con la app ya instalada no
 * aparece.
 *
 * <p>La guía es la misma que la app del jugador (/instalar): pasos cortos, letra grande
 * y, en cada uno, el dibujo del botón que hay que tocar, porque "el botón de
 * compartir" no le dice nada a quien no sabe cuál es. Abre en los pasos del teléfono
 * que se está usando y deja cambiar al otro.
 *
 * <p>Con {@code autoOpen} (el socio ya entró) la guía se abre sola la primera vez en
 * el celular.
 */
export function InstallButton({ autoOpen }: { autoOpen: boolean }) {
  const { slug } = useGymAuth();
  const install = useInstallState();
  const movil = install.platform === 'android' || install.platform === 'ios';
  const [open, setOpen] = useState(false);
  const [telefono, setTelefono] = useState<Telefono>(install.platform === 'ios' ? 'ios' : 'android');
  const [rechazado, setRechazado] = useState(false);

  useEffect(() => {
    if (autoOpen && movil && !readDismissed(slug)) {
      setOpen(true);
    }
  }, [autoOpen, slug, movil]);

  if (install.platform === 'installed') {
    return null;
  }

  const close = () => {
    writeDismissed(slug);
    setOpen(false);
  };

  const instalar = () =>
    void promptInstall().then((aceptada) => {
      setRechazado(!aceptada);
      if (aceptada) {
        close();
      }
    });

  const pasos = telefono === 'ios' ? pasosIphone(install.iosNotSafari) : pasosAndroid();

  return (
    <>
      <button
        type="button"
        onClick={() => (install.canPrompt ? instalar() : setOpen(true))}
        className="flex shrink-0 items-center gap-1.5 rounded-full bg-ladrillo py-2 pl-3 pr-3.5 text-xs font-bold uppercase tracking-[0.08em] text-cal [box-shadow:var(--shadow-glow)]"
      >
        <InstalarIcono className="size-4" />
        Instalar app
      </button>

      {/* En el body y no en su lugar: la barra de arriba tiene backdrop-blur, que encierra
          a los hijos "fixed" en la barra en vez de la pantalla. */}
      {open &&
        createPortal(
          <div className="fixed inset-0 z-50 flex items-end bg-black/60 p-3 pt-10 backdrop-blur sm:items-center sm:justify-center">
            <section
              role="dialog"
              aria-modal="true"
              aria-labelledby="install-title"
              className="max-h-full w-full max-w-lg overflow-y-auto rounded-2xl border border-borde bg-pista p-5 [box-shadow:var(--shadow-card)]"
            >
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="eyebrow text-ladrillo">Acceso rápido</p>
                  <h2 id="install-title" className="mt-1 text-3xl">
                    Instalá la app
                  </h2>
                </div>
                <button
                  type="button"
                  onClick={close}
                  className="rounded-full border border-borde px-3 py-1 text-sm text-ink-soft"
                  aria-label="Cerrar guía"
                >
                  Cerrar
                </button>
              </div>
              <p className="mt-2 text-base leading-relaxed text-ink-soft">
                La tenés en el inicio del celular, como cualquier otra app, y entrás con un toque al QR y tu cuota.
              </p>

              {install.canPrompt && (
                <div className="mt-5 space-y-2">
                  <Button variant="accent" onClick={instalar}>
                    Instalar ahora
                  </Button>
                  {rechazado && (
                    <p className="text-center text-sm text-ink-soft">
                      No se instaló. Tocá el botón de nuevo o seguí los pasos de abajo.
                    </p>
                  )}
                </div>
              )}

              {install.inAppBrowser && (
                <div className="mt-5">
                  <Alert tone="info">
                    <p className="text-base font-semibold text-cal">Primero abrila en el navegador</p>
                    <p className="mt-1 text-base">
                      Abriste este link desde otra app (Instagram, Facebook…) y desde ahí no se puede instalar.
                      Tocá los tres puntitos de arriba y elegí{' '}
                      <strong>Abrir en {telefono === 'ios' ? 'Safari' : 'Chrome'}</strong> o{' '}
                      <strong>Abrir en el navegador</strong>.
                    </p>
                  </Alert>
                </div>
              )}

              <div
                role="tablist"
                aria-label="Tipo de celular"
                className="mt-6 grid grid-cols-2 gap-1 rounded-full border border-borde bg-vidrio p-1"
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

              <ol className="mt-5 space-y-3">
                {pasos.map((paso, index) => (
                  <li key={paso.titulo}>
                    <Card className="flex gap-4">
                      <span
                        aria-hidden
                        className="display grid size-11 shrink-0 place-items-center rounded-full bg-ladrillo text-2xl text-white"
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

              <Button variant="secondary" className="mt-5" onClick={close}>
                Entendido
              </Button>
            </section>
          </div>,
          document.body,
        )}
    </>
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
  titulo: '¡Listo! Buscá el ícono',
  texto:
    'Queda en la pantalla de inicio, con el nombre de tu gimnasio, junto a tus otras apps. Tocalo y entrás directo a tu QR y tu cuota.',
};

/* ------------------------------------------------ lo que se ve en la pantalla */

/** Un botón del teléfono, dibujado para reconocerlo: redondo con el ícono, o con texto. */
function Tecla({ children, ancha = false }: { children: ReactNode; ancha?: boolean }) {
  return (
    <span
      className={`inline-grid place-items-center rounded-2xl border border-borde bg-vidrio-alto text-[#0a84ff] ${
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
    <div className="flex items-center justify-between gap-3 rounded-xl border border-borde bg-vidrio-alto px-4 py-3 text-base font-semibold text-cal">
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

function AgregarIcono() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden className="size-6">
      <rect x="4" y="4" width="16" height="16" rx="4" />
      <path d="M12 8.5v7M8.5 12h7" />
    </svg>
  );
}

function InstalarIcono({ className = 'size-6' }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden className={className}>
      <rect x="6" y="2.5" width="12" height="19" rx="2.5" />
      <path d="M12 7v7M9 11l3 3 3-3" />
    </svg>
  );
}
