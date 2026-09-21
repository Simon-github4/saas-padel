import { useEffect, useRef, useState } from 'react';
import { ApiError, gymApi, type CheckInResult } from '../api/gymClient';
import { useGymAuth } from '../auth/GymAuthContext';
import { Alert, Button, Card, Loading, WeekDots } from '../components/ui';
import { longDay, weekUsage } from '../format';
import { currentPosition, describeProblem, GeoError, type GeoProblem, type Position } from '../geolocation';

/**
 * Registra el ingreso con el codigo de la sede y muestra el resultado.
 *
 * <p>Se llega por el escaner o por el link del QR. En los dos casos el pedido es el mismo, y el
 * socio sale de la sesion: aca nunca se manda quien es.
 *
 * <p>Si alguna sede del club verifica la ubicacion, antes se la pide al celular. Si no la obtiene
 * (permiso negado, sin GPS) el pedido sale igual, sin ella: el servidor decide, porque la sede a la
 * que apunta el QR puede no verificarla. Si la rechaza por eso, se le explica al socio como
 * arreglarlo y puede reintentar sin volver a escanear.
 */
export function CheckInScreen({
  code,
  locationRequired,
  onDone,
}: {
  code: string;
  locationRequired: boolean;
  onDone: () => void;
}) {
  const { slug, session, expire, requirePasswordChange } = useGymAuth();
  const [result, setResult] = useState<CheckInResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [geoProblem, setGeoProblem] = useState<GeoProblem | null>(null);
  const [locating, setLocating] = useState(false);
  // Cada reintento vuelve a correr el efecto.
  const [attempt, setAttempt] = useState(0);
  // React en desarrollo monta los efectos dos veces: sin esta marca el pedido saldria dos veces y
  // la segunda respuesta ("ya registrado") taparia la primera.
  const sentFor = useRef<string | null>(null);

  useEffect(() => {
    if (!session) {
      return;
    }
    const key = `${code}#${attempt}`;
    if (sentFor.current === key) {
      return;
    }
    sentFor.current = key;

    async function run(token: string) {
      let position: Position | undefined;
      let problem: GeoProblem | null = null;
      if (locationRequired) {
        setLocating(true);
        try {
          position = await currentPosition();
        } catch (err) {
          problem = err instanceof GeoError ? err.problem : 'unavailable';
        }
        setLocating(false);
      }

      try {
        const checkedIn = await gymApi.checkIn(slug, token, code, position);
        setResult(checkedIn);
      } catch (err) {
        if (err instanceof ApiError && err.sessionExpired) {
          // La app vuelve al login; al entrar, el codigo pendiente se retoma solo.
          sentFor.current = null;
          expire();
        } else if (err instanceof ApiError && err.passwordChangeRequired) {
          sentFor.current = null;
          requirePasswordChange();
        } else {
          // Solo se le explica el permiso cuando el servidor lo rechazo por falta de ubicacion.
          setGeoProblem(err instanceof ApiError && err.locationRequired ? (problem ?? 'unavailable') : null);
          setError(err instanceof ApiError ? err.message : 'Tuvimos un problema. Probá de nuevo.');
        }
      }
    }

    // Sin cancelar al desmontar: el guardia de arriba impide repetirlo, asi que cancelar dejaria a
    // React en desarrollo (que monta dos veces) con un pedido que nadie retoma.
    void run(session.token);
  }, [slug, session, code, attempt, locationRequired, expire, requirePasswordChange]);

  function retry() {
    setError(null);
    setGeoProblem(null);
    setAttempt((n) => n + 1);
  }

  if (error) {
    return (
      <>
        <h1 className="text-3xl">No pudimos registrarlo</h1>
        <div className="mt-6 space-y-3">
          <Alert>{error}</Alert>
          {geoProblem && <Alert tone="info">{describeProblem(geoProblem)}</Alert>}
        </div>
        <Button className="mt-6" onClick={retry}>
          Reintentar
        </Button>
        <Button variant="secondary" className="mt-3" onClick={onDone}>
          Volver
        </Button>
      </>
    );
  }

  if (!result) {
    return <Loading label={locating ? 'Verificando tu ubicación…' : 'Registrando tu ingreso…'} />;
  }

  return (
    <>
      <div className="flex flex-col items-center pt-4 text-center">
        <span className="grid size-20 place-items-center rounded-full bg-emerald-500/15 text-emerald-700">
          <svg viewBox="0 0 24 24" className="size-10" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
            <path d="M5 12.5l4.5 4.5L19 7.5" />
          </svg>
        </span>
        <h1 className="mt-5 text-3xl">{result.alreadyRegistered ? 'Ya estabas registrado' : '¡Listo, bienvenido!'}</h1>
        <p className="mt-2 text-sm text-ink-soft">
          {result.alreadyRegistered ? 'Tu ingreso de hoy ya figuraba en' : 'Ingreso registrado en'} {result.sedeName}.
        </p>
      </div>

      <Card className="mt-8">
        <p className="eyebrow text-ink-soft">Esta semana</p>
        <div className="mt-2 flex items-center justify-between gap-3">
          <WeekDots used={result.weekUsed} limit={result.weekLimit} />
          <p className="text-sm font-semibold">{weekUsage(result.weekUsed, result.weekLimit)}</p>
        </div>
        <p className="mt-4 text-xs text-ink-soft">Tu cuota vale hasta el {longDay(result.validUntil)}.</p>
      </Card>

      <Button className="mt-6" onClick={onDone}>
        Listo
      </Button>
    </>
  );
}
