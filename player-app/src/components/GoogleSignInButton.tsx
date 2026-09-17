import { useEffect, useRef } from 'react';
import { usePlayerAuth } from '../auth/AuthContext';
import { ApiError, playerApi } from '../api/client';

/** Ventana mínima del SDK de Google Identity Services -- no hay paquete de tipos instalado para esto. */
interface GoogleIdentityServices {
  accounts: {
    id: {
      initialize: (config: { client_id: string; callback: (response: { credential: string }) => void }) => void;
      renderButton: (parent: HTMLElement, options: { type: string; width: number; text: string }) => void;
    };
  };
}

declare global {
  interface Window {
    google?: GoogleIdentityServices;
  }
}

interface Props {
  /** A dónde seguir cuando la sesión quedó abierta. */
  onSignedIn: () => void;
  onError: (message: string) => void;
  /** Para que la pantalla bloquee su formulario mientras Google responde. */
  onWorkingChange?: (working: boolean) => void;
  /** Texto del botón: "Continuar con Google" o "Iniciar sesión con Google". */
  text?: 'continue_with' | 'signin_with';
  className?: string;
}

/**
 * El botón de Google, que lo dibuja el SDK de Google Identity Services.
 *
 * <p>Vive aparte porque lo usan el login y la pantalla de recuperar contraseña:
 * a esa última llega quien no tiene contraseña que recuperar justamente porque
 * su cuenta es de Google, y el botón es la salida.
 *
 * <p>No dibuja nada si la aplicación no tiene configurado el Client ID
 * ({@code GOOGLE_CLIENT_ID}): sin eso el login con Google no se ofrece.
 */
export function GoogleSignInButton({
  onSignedIn,
  onError,
  onWorkingChange,
  text = 'continue_with',
  className = 'flex justify-center',
}: Props) {
  const { loginWithGoogle } = usePlayerAuth();
  const buttonRef = useRef<HTMLDivElement>(null);

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
      if (cancelled || !config?.googleClientId || !scriptReady || !window.google || !buttonRef.current) {
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
      buttonRef.current.innerHTML = '';
      window.google.accounts.id.renderButton(buttonRef.current, {
        type: 'standard',
        width: 320,
        text,
      });
    }

    void renderGoogleButton();
    return () => {
      cancelled = true;
    };
    // Las tres funciones las rearma la pantalla en cada render; incluirlas
    // volveria a pedir el boton al SDK sin que haya cambiado nada.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loginWithGoogle, text]);

  return <div ref={buttonRef} className={className} />;
}
