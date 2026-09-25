import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { promptInstall, useInstallState } from '../pwa';
import { BrandIcon } from './BrandLogo';

const CERRADO_KEY = 'aviso-instalar-cerrado';

function leerCerrado() {
  try {
    return window.localStorage.getItem(CERRADO_KEY) === '1';
  } catch {
    return false;
  }
}

function guardarCerrado() {
  try {
    window.localStorage.setItem(CERRADO_KEY, '1');
  } catch {
    // Sin localStorage el aviso vuelve en la próxima visita; no pasa nada más.
  }
}

/**
 * Aviso de "Instalá la app" arriba del buscador.
 *
 * <p>Solo en el celular y solo si todavía no la instaló: en la compu no hay nada
 * que instalar, y abierta desde el ícono ya no hace falta. Si el jugador lo cierra
 * no vuelve a aparecer; queda el botón fijo de abajo (InstallFloatingButton).
 *
 * <p>En Android con Chrome, "Instalar" abre directo el cartel del navegador. En el
 * resto lleva a /instalar, con los pasos de ese teléfono.
 */
export function InstallBanner() {
  const install = useInstallState();
  const [cerrado, setCerrado] = useState(leerCerrado);

  if (cerrado || (install.platform !== 'android' && install.platform !== 'ios')) {
    return null;
  }

  const cerrar = () => {
    guardarCerrado();
    setCerrado(true);
  };

  const boton = 'shrink-0 rounded-full bg-ladrillo px-4 py-2.5 text-sm font-bold text-cal';

  return (
    <div className="mt-6 flex items-center gap-3 rounded-2xl border border-ladrillo/30 bg-ladrillo/10 p-3 pr-2">
      <span className="grid size-12 shrink-0 place-items-center rounded-xl bg-[#0a0a0a]">
        <BrandIcon className="size-9" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="font-bold leading-tight">Instalá la app</p>
        <p className="mt-0.5 text-sm leading-snug text-ink-soft">Entrá con un toque, sin Google.</p>
      </div>
      {install.canPrompt ? (
        <button type="button" onClick={() => void promptInstall()} className={boton}>
          Instalar
        </button>
      ) : (
        <Link to="/instalar" className={boton}>
          Ver cómo
        </Link>
      )}
      <button
        type="button"
        onClick={cerrar}
        aria-label="Cerrar el aviso de instalar la app"
        className="grid size-9 shrink-0 place-items-center rounded-full text-xl leading-none text-ink-soft hover:text-cal"
      >
        ×
      </button>
    </div>
  );
}

/**
 * Botón fijo "Instalar app", abajo a la izquierda, en todas las pantallas del jugador.
 *
 * <p>Está siempre, no solo en el buscador: al jugador se lo encuentra sobre todo en el
 * link del club o del turno que le llegó por WhatsApp. Abajo a la derecha ya están el
 * tema y el WhatsApp del club, por eso va del otro lado.
 *
 * <p>Si Chrome ya ofrece instalar, lo hace en un toque; si no, lleva a la guía. No
 * aparece con la app ya instalada, en la guía misma ni en la portada, que es la
 * página comercial para los clubes.
 */
export function InstallFloatingButton() {
  const install = useInstallState();
  const { pathname } = useLocation();

  if (install.platform === 'installed' || pathname === '/' || pathname === '/instalar') {
    return null;
  }

  const className =
    'fixed bottom-5 left-5 z-40 flex h-12 items-center gap-2 rounded-full bg-ladrillo pl-4 pr-5 text-sm font-bold text-cal ring-1 ring-white/20 [box-shadow:var(--shadow-glow)] transition hover:bg-ladrillo/90';
  const contenido = (
    <>
      <InstalarGlyph />
      Instalar app
    </>
  );

  return install.canPrompt ? (
    <button type="button" onClick={() => void promptInstall()} className={className}>
      {contenido}
    </button>
  ) : (
    <Link to="/instalar" className={className}>
      {contenido}
    </Link>
  );
}

/** Un celular con la flecha de bajar: "ponerla en el teléfono". */
function InstalarGlyph() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden className="size-5">
      <rect x="6" y="2.5" width="12" height="19" rx="2.5" />
      <path d="M12 7v7M9 11l3 3 3-3" />
    </svg>
  );
}
