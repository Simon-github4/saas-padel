import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { usePlayerAuth } from '../auth/AuthContext';
import { ApiError, playerApi } from '../api/client';
import { Alert, Button, Card, Field, Screen, SectionTitle } from '../components/Ui';

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

/**
 * Login del jugador: email y contraseña, o Google. Reemplaza el viejo flujo de
 * teléfono + código por WhatsApp -- ya no hace falta WhatsApp para entrar.
 */
export function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { login, register, confirmSignup, loginWithGoogle } = usePlayerAuth();

  const [mode, setMode] = useState<'login' | 'register'>(
    searchParams.get('mode') === 'register' ? 'register' : 'login',
  );
  // Solo aplica en modo "register": 'form' pide los datos, 'code' pide el
  // código de 6 dígitos que se mandó por mail. La cuenta no existe hasta que
  // ese código (o el link del mismo mail) se confirma.
  const [step, setStep] = useState<'form' | 'code'>('form');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState(searchParams.get('name') ?? '');
  const [phoneNumber, setPhoneNumber] = useState(searchParams.get('phone') ?? '');
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);

  const googleButtonRef = useRef<HTMLDivElement>(null);

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
      if (cancelled || !config?.googleClientId || !scriptReady || !window.google || !googleButtonRef.current) {
        return;
      }
      window.google.accounts.id.initialize({
        client_id: config.googleClientId,
        callback: async (response) => {
          setError(null);
          setWorking(true);
          try {
            await loginWithGoogle(response.credential);
            navigate('/account');
          } catch (err) {
            setError(err instanceof ApiError ? err.message : 'No pudimos verificar tu cuenta de Google.');
          } finally {
            setWorking(false);
          }
        },
      });
      googleButtonRef.current.innerHTML = '';
      window.google.accounts.id.renderButton(googleButtonRef.current, {
        type: 'standard',
        width: 320,
        text: 'continue_with',
      });
    }

    void renderGoogleButton();
    return () => {
      cancelled = true;
    };
  }, [loginWithGoogle, navigate]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setWorking(true);
    try {
      if (mode === 'login') {
        await login(email, password);
        navigate('/account');
      } else {
        await register(email, password, displayName || undefined, phoneNumber || undefined);
        setCode('');
        setStep('code');
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Tuvimos un problema. Probá de nuevo.');
    } finally {
      setWorking(false);
    }
  }

  async function submitCode(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setWorking(true);
    try {
      await confirmSignup(email, code);
      navigate('/account');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Tuvimos un problema. Probá de nuevo.');
    } finally {
      setWorking(false);
    }
  }

  const showCodeStep = mode === 'register' && step === 'code';

  return (
    <Screen className="pt-10">
      <BackLink onClick={() => navigate(-1)} />
      <SectionTitle
        title="Tus turnos"
        subtitle={
          showCodeStep
            ? `Te mandamos un código a ${email}.`
            : mode === 'login'
              ? 'Entrá con tu email y contraseña.'
              : 'Creá tu cuenta con email y contraseña.'
        }
      />

      <div className="mt-6 space-y-4">
        <Card>
          {showCodeStep ? (
            <form className="space-y-5" onSubmit={submitCode}>
              <Field
                label="Código de 6 dígitos"
                value={code}
                onChange={(value) => setCode(value.replace(/\D/g, '').slice(0, 6))}
                inputMode="numeric"
                autoComplete="one-time-code"
                placeholder="123456"
                hint="También podés tocar el link que te mandamos, desde este mismo mail."
              />
              {error && <Alert>{error}</Alert>}
              <Button type="submit" disabled={working || code.trim().length !== 6}>
                {working ? 'Confirmando…' : 'Confirmar cuenta'}
              </Button>
              <button
                type="button"
                onClick={() => {
                  setStep('form');
                  setError(null);
                }}
                className="block w-full text-center text-xs text-ink-soft underline-offset-4 hover:underline"
              >
                ¿No llegó? Volvé a intentar
              </button>
            </form>
          ) : (
            <>
              <div ref={googleButtonRef} className="mb-5 flex justify-center" />

              <form className="space-y-5" onSubmit={submit}>
                <Field
                  label="Email"
                  value={email}
                  onChange={setEmail}
                  type="email"
                  inputMode="text"
                  autoComplete="email"
                  placeholder="vos@email.com"
                />
                <Field
                  label="Contraseña"
                  value={password}
                  onChange={setPassword}
                  type="password"
                  autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                  placeholder="Al menos 8 caracteres"
                />
                {mode === 'register' && (
                  <>
                    <Field
                      label="Tu nombre (opcional)"
                      value={displayName}
                      onChange={setDisplayName}
                      placeholder="Nombre y apellido"
                    />
                    <Field
                      label="Tu teléfono (opcional)"
                      value={phoneNumber}
                      onChange={setPhoneNumber}
                      type="tel"
                      inputMode="tel"
                      autoComplete="tel"
                      placeholder="2262 415000"
                      hint="Para que el club te ubique cuando reservás."
                    />
                  </>
                )}
                {error && <Alert>{error}</Alert>}
                <Button type="submit" disabled={working || !email.trim() || !password.trim()}>
                  {working ? 'Un momento…' : mode === 'login' ? 'Entrar' : 'Crear cuenta'}
                </Button>
                {mode === 'register' && (
                  <p className="text-center text-xs text-ink-mute">
                    Al crear tu cuenta aceptás los{' '}
                    <Link to="/terminos" className="underline-offset-4 hover:underline">
                      Términos de uso
                    </Link>{' '}
                    y la{' '}
                    <Link to="/privacidad" className="underline-offset-4 hover:underline">
                      Política de privacidad
                    </Link>
                    .
                  </p>
                )}
              </form>
            </>
          )}
        </Card>

        {!showCodeStep && (
          <div className="flex items-center justify-between text-sm">
            <button
              type="button"
              onClick={() => {
                setMode(mode === 'login' ? 'register' : 'login');
                setStep('form');
                setError(null);
              }}
              className="font-semibold text-ladrillo-claro underline-offset-4 hover:underline"
            >
              {mode === 'login' ? 'Crear una cuenta' : 'Ya tengo cuenta'}
            </button>
            {mode === 'login' && (
              <button
                type="button"
                onClick={() => navigate('/forgot-password')}
                className="text-ink-soft underline-offset-4 hover:underline"
              >
                ¿Olvidaste tu contraseña?
              </button>
            )}
          </div>
        )}
      </div>
    </Screen>
  );
}

/** Volver a la página anterior (al club del que venías), con la flecha + texto. */
function BackLink({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="mb-6 flex cursor-pointer items-center gap-1.5 text-sm font-semibold text-ink-soft transition hover:text-cal"
    >
      <ArrowLeftGlyph className="size-4" />
      Volver
    </button>
  );
}

function ArrowLeftGlyph({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden
    >
      <path d="M19 12H5M12 19l-7-7 7-7" />
    </svg>
  );
}
