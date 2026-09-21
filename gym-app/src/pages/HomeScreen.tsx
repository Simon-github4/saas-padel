import { useEffect, useState } from 'react';
import { ApiError, gymApi, type Me } from '../api/gymClient';
import { useGymAuth } from '../auth/GymAuthContext';
import { InstallGuide } from '../components/InstallGuide';
import { Alert, Badge, Button, Card, Loading, WeekDots } from '../components/ui';
import { firstName, longDay, money, shortDay, weekUsage } from '../format';

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
        <BillingCard me={me} />
      </Card>

      {me.checkedInToday && (
        <div className="mt-4">
          <Alert tone="success">Hoy ya registraste tu ingreso.</Alert>
        </div>
      )}

      <Button variant="accent" className="mt-6" onClick={onScan} disabled={!me.canEnter}>
        Escanear QR de la entrada
      </Button>

      <InstallGuide />

      {me.recent.length > 0 && (
        <section className="mt-10">
          <h2 className="text-xl">Tus últimos ingresos</h2>
          <ul className="mt-3 divide-y divide-borde-suave rounded-2xl border border-borde bg-vidrio">
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

/** La tarjeta de la cuota: al día, deber la corriente (con gracia) o estar bloqueado. */
function BillingCard({ me }: { me: Me }) {
  if (me.membership && me.monthsLate >= 2) {
    return (
      <div className="space-y-2">
        <Badge tone="danger">Adeudás {me.monthsLate} cuotas</Badge>
        <p className="text-sm text-ink-soft">
          {me.owedTotal > 0
            ? `Son ${money(me.owedTotal)} en total. Para volver a entrar, pasá por el mostrador a regularizar las cuotas impagas.`
            : 'Para volver a entrar, pasá por el mostrador a regularizar las cuotas impagas.'}
        </p>
      </div>
    );
  }

  if (!me.membership) {
    return (
      <div className="space-y-2">
        <Badge tone="warning">Sin cuota vigente</Badge>
        <p className="text-sm text-ink-soft">
          {me.cycleStart
            ? 'Tu cuota no está vigente. Consultá en el mostrador.'
            : 'Todavía no tenés una cuota cargada. Consultá en el mostrador.'}
        </p>
      </div>
    );
  }

  if (!me.canEnter) {
    return (
      <div className="space-y-2">
        <Badge tone="warning">Tu cuota todavía no arranca</Badge>
        <p className="text-sm text-ink-soft">
          Tu mes va del {longDay(me.periodStart)} al {longDay(me.periodEnd)}. Cuando arranque, vas a poder
          registrar tus ingresos.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        {me.paidCurrent ? <Badge tone="success">Al día</Badge> : <Badge tone="warning">Te falta el mes</Badge>}
        {me.paidCurrent ? (
          <p className="text-sm text-ink-soft">tu cuota está paga hasta el {longDay(me.paidUntil ?? me.periodEnd)}</p>
        ) : (
          <p className="text-sm text-ink-soft">
            tu mes va del {longDay(me.periodStart)} al {longDay(me.periodEnd)}
          </p>
        )}
      </div>
      {!me.paidCurrent && (
        <p className="text-sm text-ink-soft">
          Podés entrar igual, pero pasá por el mostrador a pagar la cuota de este mes.
        </p>
      )}
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
  );
}
