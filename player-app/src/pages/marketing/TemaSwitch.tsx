import { useEffect, useState } from 'react';

/**
 * Botón flotante para ver la landing en claro u oscuro: un sol en el tema
 * oscuro (lleva al claro) y una luna en el claro (vuelve al oscuro).
 *
 * <p>La landing es oscura por defecto. Pone el mismo data-theme que ya usan las
 * páginas de un club que eligió tema claro, así lo que se ve es la paleta real
 * y no una maqueta aparte.
 */
export function TemaSwitch() {
  const [claro, setClaro] = useState(false);

  useEffect(() => {
    const html = document.documentElement;
    if (claro) {
      html.setAttribute('data-theme', 'light');
    } else {
      html.removeAttribute('data-theme');
    }
    return () => html.removeAttribute('data-theme');
  }, [claro]);

  const etiqueta = claro ? 'Ver en oscuro' : 'Ver en claro';

  return (
    <button
      type="button"
      onClick={() => setClaro(!claro)}
      aria-label={etiqueta}
      title={etiqueta}
      className="fixed bottom-5 right-5 z-50 grid size-12 place-items-center rounded-full border border-cal/20 bg-vidrio text-cal shadow-lg transition hover:border-cal/40"
    >
      {claro ? <Luna /> : <Sol />}
    </button>
  );
}

function Sol() {
  return (
    <svg aria-hidden viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor"
      strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
    </svg>
  );
}

function Luna() {
  return (
    <svg aria-hidden viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor"
      strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
    </svg>
  );
}
