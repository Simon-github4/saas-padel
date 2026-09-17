import { useEffect, useState } from 'react';

/**
 * Botón para mirar la landing en claro, solo en la máquina de desarrollo.
 *
 * <p>La landing es oscura y no tiene modo claro: esto existe para decidir si
 * vale la pena hacerlo. Pone el mismo data-theme que ya usan las páginas de un
 * club que eligió tema claro, así lo que se ve es la paleta real y no una
 * maqueta aparte.
 *
 * <p>Se muestra solo en localhost. No se filtra a producción, y no hace falta
 * acordarse de sacarlo antes de un deploy.
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

  if (!esLocal()) {
    return null;
  }

  return (
    <button
      type="button"
      onClick={() => setClaro(!claro)}
      className="fixed bottom-5 right-5 z-50 rounded-full border border-cal/20 bg-vidrio px-5 py-3 text-xs font-bold uppercase tracking-[0.12em] text-cal shadow-lg transition hover:border-cal/40"
    >
      {claro ? 'Ver en oscuro' : 'Ver en claro'}
    </button>
  );
}

function esLocal(): boolean {
  return location.hostname === 'localhost' || location.hostname === '127.0.0.1';
}
