import { useEffect, useLayoutEffect, useState } from 'react';

/**
 * Botón flotante para ver la página en claro u oscuro: un sol en el tema
 * oscuro (lleva al claro) y una luna en el claro (vuelve al oscuro).
 *
 * <p>Pone el mismo data-theme que ya usan las páginas de un club que eligió
 * tema claro, así lo que se ve es la paleta real y no una maqueta aparte.
 * Layout effect para que no se vea un instante oscura antes de pintarse clara.
 *
 * <p>Con {@code recordar}, arranca con lo que eligió la persona la última vez
 * en esta página; si nunca eligió, con el tema del teléfono o la compu, y si
 * el navegador no lo dice, claro. Sin {@code recordar}, siempre claro (la
 * landing).
 */
export function TemaSwitch({ recordar }: { recordar?: string }) {
  const [claro, setClaro] = useState(() => (recordar ? temaInicial(recordar) : true));
  const [elegido, setElegido] = useState(() => (recordar ? leer(recordar) !== null : true));

  // Mientras la persona no eligió, sigue al sistema si lo cambia con la página abierta.
  useEffect(() => {
    if (!recordar || elegido || !window.matchMedia) {
      return;
    }
    const oscuro = window.matchMedia('(prefers-color-scheme: dark)');
    const alCambiar = () => setClaro(!oscuro.matches);
    oscuro.addEventListener('change', alCambiar);
    return () => oscuro.removeEventListener('change', alCambiar);
  }, [recordar, elegido]);

  function cambiar() {
    const siguiente = !claro;
    setClaro(siguiente);
    setElegido(true);
    if (recordar) {
      guardar(recordar, siguiente ? 'claro' : 'oscuro');
    }
  }

  useLayoutEffect(() => {
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
      onClick={cambiar}
      aria-label={etiqueta}
      title={etiqueta}
      className="fixed bottom-5 right-5 z-50 grid size-12 place-items-center rounded-full border border-cal/20 bg-vidrio text-cal shadow-lg transition hover:border-cal/40"
    >
      {claro ? <Luna /> : <Sol />}
    </button>
  );
}

function temaInicial(clave: string): boolean {
  const guardado = leer(clave);
  if (guardado !== null) {
    return guardado === 'claro';
  }
  return !(window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false);
}

// El almacenamiento puede no estar (navegación privada, sitio bloqueado): sin él
// la página anda igual, solo no recuerda la elección.
function leer(clave: string): string | null {
  try {
    return window.localStorage.getItem(clave);
  } catch {
    return null;
  }
}

function guardar(clave: string, valor: string) {
  try {
    window.localStorage.setItem(clave, valor);
  } catch {
    // Ver leer().
  }
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
