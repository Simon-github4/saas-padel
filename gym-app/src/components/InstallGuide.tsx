import { useEffect, useMemo, useState } from 'react';
import { useGymAuth } from '../auth/GymAuthContext';
import { useInstallPrompt, type InstallPlatform } from '../hooks/useInstallPrompt';
import { Button, Card } from './ui';

type TutorialStep = {
  title: string;
  text: string;
  imageSrc: string;
  imageAlt: string;
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

function stepsFor(platform: InstallPlatform, canPrompt: boolean): TutorialStep[] {
  if (platform === 'ios') {
    return [
      {
        title: 'Abrí el menú de compartir',
        text: 'En iPhone se instala desde Safari: tocá el botón de compartir que está abajo.',
        imageSrc: '/gym-app/install/ios-compartir.png',
        imageAlt: 'Captura del botón Compartir de Safari en iPhone',
      },
      {
        title: 'Agregala a inicio',
        text: 'Elegí Agregar a inicio y después confirmá con Agregar.',
        imageSrc: '/gym-app/install/ios-agregar-a-inicio.png',
        imageAlt: 'Captura de la opción Agregar a inicio en iPhone',
      },
    ];
  }

  if (platform === 'android') {
    return [
      {
        title: canPrompt ? 'Usá el botón de instalación' : 'Abrí el menú del navegador',
        text: canPrompt
          ? 'Chrome puede mostrarte la instalación directa desde esta pantalla.'
          : 'Si no aparece el botón automático, abrí el menú de los tres puntos.',
        imageSrc: '/gym-app/install/android-menu.png',
        imageAlt: 'Captura del menú de Chrome en Android',
      },
      {
        title: 'Confirmá la app',
        text: 'Tocá Instalar y vas a tener el acceso junto a tus otras apps.',
        imageSrc: '/gym-app/install/android-instalar.png',
        imageAlt: 'Captura de confirmación para instalar la app en Android',
      },
    ];
  }

  return [];
}

export function InstallGuide() {
  const { slug } = useGymAuth();
  const install = useInstallPrompt();
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState(0);

  const steps = useMemo(() => stepsFor(install.platform, install.canPrompt), [install.platform, install.canPrompt]);
  const current = steps[Math.min(step, steps.length - 1)];

  useEffect(() => {
    if (steps.length > 0 && !readDismissed(slug)) {
      setOpen(true);
    }
  }, [slug, steps.length]);

  if (install.platform === 'installed' || steps.length === 0) {
    return null;
  }

  const close = () => {
    writeDismissed(slug);
    setOpen(false);
    setStep(0);
  };

  return (
    <>
      <Card className="mt-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="eyebrow text-ink-soft">Acceso rápido</p>
            <h2 className="mt-1 text-xl">Instalá esta app</h2>
          </div>
          <span className="rounded-full border border-ladrillo/30 bg-ladrillo/15 px-3 py-1 text-xs font-bold uppercase tracking-[0.1em] text-ladrillo-claro">
            PWA
          </span>
        </div>
        <p className="mt-3 text-sm text-ink-soft">
          Guardala en el inicio del celular para entrar directo al QR y tu cuota.
        </p>
        <div className="mt-5 grid gap-3">
          {install.canPrompt && (
            <Button variant="secondary" onClick={() => void install.prompt()}>
              Instalar app
            </Button>
          )}
          <Button variant="secondary" onClick={() => setOpen(true)}>
            Ver guía paso a paso
          </Button>
        </div>
      </Card>

      {open && (
        <div className="fixed inset-0 z-50 flex items-end bg-black/70 p-3 pt-10 backdrop-blur sm:items-center sm:justify-center">
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="install-title"
            className="max-h-full w-full max-w-lg overflow-y-auto rounded-2xl border border-cal/10 bg-pista p-5 [box-shadow:var(--shadow-card)]"
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="eyebrow text-ladrillo-claro">Primer ingreso</p>
                <h2 id="install-title" className="mt-1 text-2xl">
                  {current.title}
                </h2>
              </div>
              <button
                type="button"
                onClick={close}
                className="rounded-full border border-cal/10 px-3 py-1 text-sm text-ink-soft"
                aria-label="Cerrar guía"
              >
                Cerrar
              </button>
            </div>

            <InstallImage src={current.imageSrc} alt={current.imageAlt} />
            <p className="mt-4 text-sm leading-6 text-ink-soft">{current.text}</p>

            <div className="mt-5 flex items-center justify-center gap-2">
              {steps.map((item, index) => (
                <span
                  key={item.title}
                  className={`h-1.5 rounded-full transition-all ${index === step ? 'w-8 bg-ladrillo' : 'w-2 bg-cal/20'}`}
                />
              ))}
            </div>

            <div className="mt-5 grid gap-3">
              {install.canPrompt && step === steps.length - 1 && (
                <Button variant="accent" onClick={() => void install.prompt().then(close)}>
                  Instalar ahora
                </Button>
              )}
              <Button
                variant="secondary"
                onClick={() => {
                  if (step < steps.length - 1) {
                    setStep(step + 1);
                  } else {
                    close();
                  }
                }}
              >
                {step < steps.length - 1 ? 'Siguiente' : 'Entendido'}
              </Button>
            </div>
          </section>
        </div>
      )}
    </>
  );
}

function InstallImage({ src, alt }: { src: string; alt: string }) {
  const [failed, setFailed] = useState(false);

  return (
    <div className="mt-5 overflow-hidden rounded-2xl border border-cal/10 bg-vidrio-alto p-3">
      {failed ? (
        <div className="flex aspect-[9/16] w-full items-center justify-center rounded-xl border border-dashed border-cal/20 bg-pista px-6 text-center">
          <div>
            <p className="eyebrow text-ladrillo-claro">Imagen pendiente</p>
            <p className="mt-3 text-sm leading-6 text-ink-soft">
              Cargá esta captura en
              <span className="mt-2 block break-all rounded-lg bg-cal/[0.06] px-3 py-2 text-xs text-cal">{src}</span>
            </p>
          </div>
        </div>
      ) : (
        <img
          src={src}
          alt={alt}
          className="aspect-[9/16] w-full rounded-xl object-contain"
          onError={() => setFailed(true)}
        />
      )}
    </div>
  );
}
