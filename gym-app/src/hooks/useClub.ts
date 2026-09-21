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
        applyClubIcons(config.heroImageUrl);
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

/**
 * Icono de la pestana y de la instalacion: la portada del club que trae la API,
 * o las imagenes por defecto del index.html cuando el club no cargo portada.
 * Se reemplazan en runtime porque el slug del club no se conoce en el html.
 */
function applyClubIcons(heroImageUrl: string | null) {
  document.querySelectorAll('link[rel="icon"], link[rel="apple-touch-icon"]').forEach((link) => link.remove());

  // El navegador cachea el favicon por URL: la portada no cambia la URL entre
  // cargas, asi que con un query nuevo se fuerza a pedirla de vuelta.
  const hero = heroImageUrl
    ? `${heroImageUrl}${heroImageUrl.includes('?') ? '&' : '?'}v=${Date.now()}`
    : null;

  const links: Array<{ rel: string; type?: string; sizes?: string; href: string }> = hero
    ? [
        { rel: 'icon', type: 'image/png', href: hero },
        { rel: 'icon', sizes: '192x192', href: hero },
        { rel: 'icon', sizes: '512x512', href: hero },
        { rel: 'apple-touch-icon', href: hero },
      ]
    : [
        { rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' },
        { rel: 'apple-touch-icon', href: '/apple-touch-icon.png' },
      ];

  for (const { rel, type, sizes, href } of links) {
    const link = document.createElement('link');
    link.rel = rel;
    if (type) {
      link.type = type;
    }
    if (sizes) {
      link.sizes = sizes;
    }
    link.href = href;
    document.head.appendChild(link);
  }
}
