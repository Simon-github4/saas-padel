import { useLayoutEffect } from 'react';

/** Lo que el club elige de su apariencia en Configuración: paleta y los dos acentos. */
export type ClubTheme = {
  themeMode: 'DARK' | 'LIGHT';
  primaryColor: string | null;
  secondaryColor: string | null;
};

const KEY = 'tema-ultimo-club';

/**
 * Pone la paleta y los acentos del club como custom properties en <html>, así los
 * toma toda la hoja de estilos. Devuelve la limpieza, para no dejarle el tema de
 * un club pegado a otra ruta.
 */
export function applyClubTheme(theme: ClubTheme): () => void {
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

/** Lo anota la página del club, para que "Ver turnos" se vea como el club de donde se vino. */
export function rememberClubTheme({ themeMode, primaryColor, secondaryColor }: ClubTheme) {
  try {
    window.localStorage.setItem(KEY, JSON.stringify({ themeMode, primaryColor, secondaryColor }));
  } catch {
    // Sin almacenamiento (navegación privada) la página anda igual, con el tema del dispositivo.
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

/**
 * Las pantallas de "Ver turnos" (login y Mis turnos) no son de un club, pero el
 * jugador llega desde uno: se ven con el tema del último club que abrió en este
 * dispositivo. Si nunca abrió ninguno, con el del teléfono o la compu, y claro si
 * no se sabe. Layout effect para no pintar un instante con otro tema.
 */
export function useLastClubTheme() {
  useLayoutEffect(() => {
    const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
    return applyClubTheme(
      lastClubTheme() ?? { themeMode: prefersDark ? 'DARK' : 'LIGHT', primaryColor: null, secondaryColor: null },
    );
  }, []);
}
