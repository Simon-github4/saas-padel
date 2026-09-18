import { useEffect, useRef, useState } from 'react';
import { usePlayerAuth } from '../auth/AuthContext';
import { ApiError, playerApi } from '../api/client';

/** Opciones de renderButton que usamos: https://developers.google.com/identity/gsi/web/reference/js-reference */
interface GoogleButtonOptions {
  type: 'standard';
  theme: 'outline';
  size: 'large';
  shape: 'pill';
  text: 'continue_with' | 'signin_with';
  width: number;
  logo_alignment: 'left' | 'center';
  locale: string;
}

/** Ventana mínima del SDK de Google Identity Services -- no hay paquete de tipos instalado para esto. */
interface GoogleIdentityServices {
  accounts: {
    id: {
      initialize: (config: { client_id: string; callback: (response: { credential: string }) => void }) => void;
      renderButton: (parent: HTMLElement, options: GoogleButtonOptions) => void;
    };
  };
}

declare global {
  interface Window {
    google?: GoogleIdentityServices;
  }
}

/** Lo más ancho que Google deja dibujar el botón. */
const GOOGLE_MAX_WIDTH = 400;

interface Props {
  /** A dónde seguir cuando la sesión quedó abierta. */
  onSignedIn: () => void;
  onError: (message: string) => void;
  /** Para que la pantalla bloquee su formulario mientras Google responde. */
  onWorkingChange?: (working: boolean) => void;
  /** Texto del botón: "Continuar con Google" o "Iniciar sesión con Google". */
  text?: 'continue_with' | 'signin_with';
  /**
   * Rótulo de la línea que separa el botón del formulario de abajo ("o con tu
   * email"). Aparece solo si el botón se dibujó: sin Google configurado no
   * queda una línea separando nada.
   */
  separator?: string;
  className?: string;
}

/**
 * El botón de Google, que lo dibuja el SDK de Google Identity Services.
 *
 * <p>Vive aparte porque lo usan el login y la pantalla de recuperar contraseña:
 * a esa última llega quien no tiene contraseña que recuperar justamente porque
 * su cuenta es de Google, y el botón es la salida.
 *
 * <p>Google lo dibuja adentro de un iframe, así que el CSS de la app no lo
 * alcanza: lo que se ajusta es lo que su SDK deja elegir. Píldora como el resto
 * de los botones, blanca (la versión "outline" de Google, en cualquier tema) y
 * del ancho de los campos, medido al dibujarlo.
 *
 * <p>No dibuja nada si la aplicación no tiene configurado el Client ID
 * ({@code GOOGLE_CLIENT_ID}): sin eso el login con Google no se ofrece.
 */
export function GoogleSignInButton({
  onSignedIn,
  onError,
  onWorkingChange,
  text = 'continue_with',
  separator,
  className = '',
}: Props) {
  const { loginWithGoogle } = usePlayerAuth();
  const buttonRef = useRef<HTMLDivElement>(null);
  const [rendered, setRendered] = useState(false);

  useEffect(() => {
    let cancelled = false;

    // El script de Google Identity Services carga con "async defer" (index.html):
    // no hay garantia de que window.google ya exista cuando este efecto corre.
    async function waitForGoogleScript(): Promise<boolean> {
      for (let attempt = 0; attempt < 50; attempt++) {
        if (window.google) {
          return true;
        }
        await new Promise((resolve) => setTimeout(resolve, 100));
      }
      return false;
    }

    async function renderGoogleButton() {
      const [config, scriptReady] = await Promise.all([
        playerApi.config().catch(() => null),
        waitForGoogleScript(),
      ]);
      const parent = buttonRef.current;
      if (cancelled || !config?.googleClientId || !scriptReady || !window.google || !parent) {
        return;
      }
      window.google.accounts.id.initialize({
        client_id: config.googleClientId,
        callback: async (response) => {
          onWorkingChange?.(true);
          try {
            await loginWithGoogle(response.credential);
            onSignedIn();
          } catch (err) {
            onError(err instanceof ApiError ? err.message : 'No pudimos verificar tu cuenta de Google.');
          } finally {
            onWorkingChange?.(false);
          }
        },
      });
      parent.innerHTML = '';
      window.google.accounts.id.renderButton(parent, {
        type: 'standard',
        theme: 'outline',
        size: 'large',
        shape: 'pill',
        text,
        width: Math.min(parent.clientWidth, GOOGLE_MAX_WIDTH),
        logo_alignment: 'center',
        locale: 'es-419',
      });
      setRendered(true);
    }

    void renderGoogleButton();
    return () => {
      cancelled = true;
    };
    // Las tres funciones las rearma la pantalla en cada render; incluirlas
    // volveria a pedir el boton al SDK sin que haya cambiado nada.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loginWithGoogle, text]);

  return (
    <div className={className}>
      {/*
        color-scheme claro solo para el iframe de Google: la app declara oscuro
        en <html>, y cuando el iframe y su documento no coinciden el navegador le
        pinta un fondo blanco opaco, que asomaba como un rectángulo alrededor de
        la píldora en el botón personalizado ("Continuar como ...").
      */}
      <div ref={buttonRef} className="flex w-full justify-center [color-scheme:light]" />
      {rendered && separator && (
        <div className="mt-5 flex items-center gap-3 text-xs text-ink-mute">
          <span aria-hidden className="h-px flex-1 bg-cal/10" />
          {separator}
          <span aria-hidden className="h-px flex-1 bg-cal/10" />
        </div>
      )}
    </div>
  );
}
