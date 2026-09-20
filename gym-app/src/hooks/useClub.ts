import { useEffect, useState } from 'react';
import { ApiError, gymApi, type PublicConfig } from '../api/gymClient';

export type ClubState =
  | { status: 'loading' }
  | { status: 'ready'; config: PublicConfig }
  | { status: 'unavailable' }
  | { status: 'error'; message: string };

/**
 * El gimnasio del club: su nombre, si pide clave y si va a pedir la ubicacion. Responde 404 si el
 * club no tiene el modulo prendido.
 *
 * <p>De paso registra el manifest de la PWA en la pagina: su link lleva el slug del club, asi que
 * no puede estar escrito en el index.html.
 */
export function useClub(slug: string): ClubState {
  const [state, setState] = useState<ClubState>({ status: 'loading' });

  useEffect(() => {
    let cancelled = false;
    setState({ status: 'loading' });

    gymApi
      .config(slug)
      .then((config) => {
        if (cancelled) {
          return;
        }
        document.title = `${config.clubName} · Gimnasio`;
        setState({ status: 'ready', config });
      })
      .catch((error: unknown) => {
        if (cancelled) {
          return;
        }
        if (error instanceof ApiError && error.unavailable) {
          setState({ status: 'unavailable' });
        } else {
          setState({
            status: 'error',
            message: error instanceof ApiError ? error.message : 'Tuvimos un problema. Probá de nuevo.',
          });
        }
      });

    const link = document.createElement('link');
    link.rel = 'manifest';
    link.href = `/gym/${encodeURIComponent(slug)}/manifest.webmanifest`;
    document.head.appendChild(link);

    return () => {
      cancelled = true;
      link.remove();
    };
  }, [slug]);

  return state;
}
