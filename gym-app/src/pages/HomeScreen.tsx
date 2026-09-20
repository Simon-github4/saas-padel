import { useEffect, useState } from 'react';
import { ApiError, gymApi, type Me } from '../api/gymClient';
import { useGymAuth } from '../auth/GymAuthContext';
import { InstallGuide } from '../components/InstallGuide';
import { Alert, Badge, Button, Card, Loading, WeekDots } from '../components/ui';
import { firstName, longDay, shortDay, weekUsage } from '../format';

/** "Mi estado": si la cuota vale, hasta cuando, cuantos dias de la semana usó, y el boton de escanear. */
export function HomeScreen({ onScan }: { onScan: () => void }) {
  const { slug, session, expire, requirePasswordChange } = useGymAuth();
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!session) {
      return;
    }
    let cancelled = false;
    gymApi
      .me(slug, session.token)
      .then((result) => !cancelled && setMe(result))
      .catch((err: unknown) => {
        if (cancelled) {
          return;
        }
        if (err instanceof ApiError && err.sessionExpired) {
          expire();
        } else if (err instanceof ApiError && err.passwordChangeRequired) {
          requirePasswordChange();
        } else {
          setError(err instanceof ApiError ? err.message : 'Tuvimos un problema. Probá de nuevo.');
        }
      });
    return () => {
      cancelled = true;
    };
    // Solo se vuelve a pedir si cambia la sesion: las funciones del contexto son estables.
  }, [slug, session, expire, requirePasswordChange]);

  if (error) {
    return <Alert>{error}</Alert>;
  }
  if (!me) {
    return <Loading />;
  }

  return (
    <>
      <h1 className="text-3xl">Hola, {firstName(me.fullName)}</h1>

      <Card className="mt-6">
        {me.valid && me.membership ? (
          <div className="space-y-4">
            <div className="flex items-center justify-between gap-3">
              <Badge tone="success">Cuota vigente</Badge>
              <p className="text-sm text-ink-soft">hasta el {longDay(me.membership.endsOn)}</p>
            </div>
            <div>
              <p className="eyebrow text-ink-soft">Esta semana</p>
              <div className="mt-2 flex items-center justify-between gap-3">
                <WeekDots used={me.weekUsed} limit={me.weekLimit} />
                <p className="text-sm font-semibold">{weekUsage(me.weekUsed, me.weekLimit)}</p>
              </div>
            </div>
            {me.membership.sedes.length > 0 && (
              <p className="text-xs text-ink-soft">Vale en: {me.membership.sedes.join(', ')}</p>
            )}
          </div>
        ) : (
          <div className="space-y-2">
            <Badge tone="warning">Sin cuota vigente</Badge>
            <p className="text-sm text-ink-soft">
              {me.membership
                ? `Tu cuota venció el ${longDay(me.membership.endsOn)}. Renovala en el mostrador.`
                : 'Todavía no tenés una cuota cargada. Consultá en el mostrador.'}
            </p>
          </div>
        )}
      </Card>

      {me.checkedInToday && (
        <div className="mt-4">
          <Alert tone="success">Hoy ya registraste tu ingreso.</Alert>
        </div>
      )}

      <Button variant="accent" className="mt-6" onClick={onScan} disabled={!me.valid}>
        Escanear QR de la entrada
      </Button>

      <InstallGuide />

      {me.recent.length > 0 && (
        <section className="mt-10">
          <h2 className="text-xl">Tus últimos ingresos</h2>
          <ul className="mt-3 divide-y divide-cal/10 rounded-2xl border border-cal/10 bg-vidrio">
            {me.recent.map((checkin) => (
              <li key={checkin.date} className="flex items-center justify-between px-4 py-3 text-sm">
                <span>{shortDay(checkin.date)}</span>
                <span className="text-ink-soft">{checkin.sedeName}</span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}
