import { useLayoutEffect } from 'react';

/** Lo que el club elige de su apariencia en Configuración: paleta y los dos acentos. */
export type ClubTheme = {
  themeMode: 'DARK' | 'LIGHT';
  primaryColor: string | null;
  secondaryColor: string | null;
};

const KEY = 'tema-ultimo-club';

/**
 * Pinta la pantalla con la apariencia de un club: la paleta y los acentos van
 * como custom properties en <html>, así los toma toda la hoja de estilos, y se
 * limpian al salir para no dejarle el tema de un club pegado a otra ruta.
 *
 * <p>Con el tema del club a mano (su página, el portal de un turno suyo) usa ese
 * y lo anota. Sin él (todavía cargando, o "Ver turnos", que no es de ningún
 * club) usa el del último club que el jugador abrió en este dispositivo: es de
 * donde viene. Si nunca abrió ninguno, el tema del teléfono o la compu, y claro
 * si no se sabe. Layout effect para no pintar un instante con otro tema.
 */
export function useClubTheme(theme?: ClubTheme | null) {
  const mode = theme?.themeMode;
  const primary = theme?.primaryColor ?? null;
  const secondary = theme?.secondaryColor ?? null;

  useLayoutEffect(() => {
    if (mode) {
      const current: ClubTheme = { themeMode: mode, primaryColor: primary, secondaryColor: secondary };
      remember(current);
      return apply(current);
    }
    return apply(lastClubTheme() ?? deviceTheme());
  }, [mode, primary, secondary]);
}

function apply(theme: ClubTheme): () => void {
  const root = document.documentElement;
  root.dataset.theme = theme.themeMode === 'LIGHT' ? 'light' : 'dark';
  if (theme.primaryColor) {
    root.style.setProperty('--color-ladrillo', theme.primaryColor);
  }
  if (theme.secondaryColor) {
    root.style.setProperty('--color-ladrillo-claro', theme.secondaryColor);
  }
  return () => {
    delete root.dataset.theme;
    root.style.removeProperty('--color-ladrillo');
    root.style.removeProperty('--color-ladrillo-claro');
  };
}

function remember(theme: ClubTheme) {
  try {
    window.localStorage.setItem(KEY, JSON.stringify(theme));
  } catch {
    // Sin almacenamiento (navegación privada) todo anda igual, sin recordar el club.
  }
}

function lastClubTheme(): ClubTheme | null {
  try {
    const raw = window.localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as ClubTheme) : null;
  } catch {
    return null;
  }
}

function deviceTheme(): ClubTheme {
  const dark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
  return { themeMode: dark ? 'DARK' : 'LIGHT', primaryColor: null, secondaryColor: null };
}
