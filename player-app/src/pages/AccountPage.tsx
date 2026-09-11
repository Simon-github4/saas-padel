import { useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { usePlayerAuth } from '../auth/AuthContext';
import { ApiError, playerApi, type BookingHistoryItem } from '../api/client';
import { clockTime, formatHours, longDate, money } from '../format';
import { readGuestBookings, type GuestBooking } from '../guestBookings';
import { Alert, Button, Card, Chip, Loading, Screen, SectionTitle, StatusBadge, TopBar } from '../components/Ui';
import { ActivityChart } from '../components/ActivityChart';
import { summarize, type Period } from '../stats';

/**
 * Historial del jugador en todos los clubes de la plataforma.
 *
 * <p>Cada turno linkea a su propio {@code /manage/:token} de siempre en vez de
 * reimplementar el detalle acá: esta pantalla es, ni más ni menos, un índice de
 * los links de gestión que el jugador ya tendría desperdigados en WhatsApp.
 *
 * <p>Los turnos reservados como invitado (ver {@code guestBookings.ts}) van en
 * una lista aparte, y se muestran haya o no sesión. No pueden mezclarse con el
 * historial: ese sale de la cuenta, y estos son de este dispositivo -- nada
 * prueba que sean de quien está mirando. Pero esconderlos al iniciar sesión era
 * dejar al jugador sin forma de volver a abrirlos, que es justamente para lo que
 * existe esta lista.
 */
export function AccountPage() {
  const navigate = useNavigate();
  const { session, logout, clearExpiredSession } = usePlayerAuth();
  const [history, setHistory] = useState<BookingHistoryItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [period, setPeriod] = useState<Period>('month');
  const now = useMemo(() => new Date(), [history]);
  // Se leen siempre, con o sin sesión. Un turno reservado como invitado no
  // aparece en el historial de la cuenta -- nada prueba que sea de quien
  // pregunta, solo que alguien escribió su teléfono -- pero este dispositivo sí
  // sabe que lo reservó, y esconderlo al iniciar sesión era perder el único
  // camino que le quedaba al jugador para volver a abrirlo.
  const guestBookings = useMemo(() => readGuestBookings(), [session]);

  useEffect(() => {
    if (!session) {
      return;
    }
    (async () => {
      try {
        setHistory(await playerApi.bookingHistory(session.token));
      } catch (err) {
        if (err instanceof ApiError && err.code === 'SESSION_EXPIRED') {
          clearExpiredSession();
          return;
        }
        setError(err instanceof ApiError ? err.message : 'No pudimos cargar tus turnos.');
      } finally {
        setLoading(false);
      }
    })();
  }, [session, clearExpiredSession]);

  if (!session && guestBookings.length === 0) {
    return <Navigate to="/login" replace />;
  }

  if (!session) {
    return <GuestAccountView bookings={guestBookings} onBack={() => navigate(-1)} />;
  }

  return (
    <Screen
      className="pt-6"
      top={
        <TopBar
          name="Mis turnos"
          accountSlot={
            <button
              type="button"
              onClick={() => navigate(-1)}
              aria-label="Volver"
              className="grid size-9 shrink-0 place-items-center rounded-full border border-cal/10 text-ink-soft transition hover:border-cal/25 hover:text-cal"
            >
              <ArrowLeftGlyph className="size-4" />
            </button>
          }
          onTitleClick={() => navigate(history?.[0]?.clubSlug ? `/club/${history[0].clubSlug}` : '/')}
        />
      }
    >
      <div className="mb-5 mt-6 flex items-start justify-between gap-4">
        <SectionTitle
          title={session.displayName ?? 'Mis turnos'}
          subtitle={session.email}
        />
        <Button variant="secondary" className="w-auto px-4" onClick={logout}>
          Cerrar sesión
        </Button>
      </div>

      {loading && <Loading />}
      {error && <Alert>{error}</Alert>}

      {!loading && !error && history && history.length === 0 && guestBookings.length === 0 && (
        <Alert tone="info">Todavía no reservaste ningún turno.</Alert>
      )}

      {!loading && !error && history && history.length === 0 && guestBookings.length > 0 && (
        <Alert tone="info">
          Todavía no reservaste ningún turno con esta cuenta. Abajo están los que
          reservaste sin iniciar sesión.
        </Alert>
      )}

      {!loading && history && history.length > 0 && (
        <>
          <ActivitySummary history={history} period={period} onPeriodChange={setPeriod} now={now} />
          <ul className="space-y-3">
            {history.map((item) => (
              <li key={item.bookingId}>
                <Link to={`/manage/${item.managementToken}`}>
                  <Card className="transition hover:border-cal/25">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-semibold">{item.clubName}</p>
                        <p className="text-sm text-ink-soft">{item.courtName}</p>
                      </div>
                      <StatusBadge status={item.status} />
                    </div>
                    <p className="mt-3 text-sm text-ink-soft first-letter:uppercase">
                      {longDate(item.startTime)} ·{' '}
                      {clockTime(item.startTime, Intl.DateTimeFormat().resolvedOptions().timeZone)} hs
                    </p>
                    <p className="mt-1 text-sm font-semibold tabular-nums">{money(item.totalPrice)}</p>
                  </Card>
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}

      {guestBookings.length > 0 && (
        <section className="mt-8">
          <SectionTitle
            title="Reservados sin cuenta"
            subtitle="En este dispositivo. No están atados a tu cuenta."
          />
          <ul className="mt-4 space-y-3">
            {guestBookings.map((item) => (
              <li key={item.bookingId}>
                <GuestBookingCard booking={item} />
              </li>
            ))}
          </ul>
          <p className="mt-3 px-1 text-xs text-ink-soft">
            Los reservaste antes de iniciar sesión, así que no figuran en el historial de
            arriba. Se ven solo desde este navegador y desaparecen de acá una hora después
            de terminar el turno.
          </p>
        </section>
      )}
    </Screen>
  );
}

/**
 * Un turno de invitado. Mismo formato en las dos pantallas que los muestran: sin
 * estado ni precio, que son datos que esta lista no tiene -- solo lo necesario
 * para reconocerlo y abrirlo.
 */
function GuestBookingCard({ booking }: { booking: GuestBooking }) {
  return (
    <Link to={`/manage/${booking.managementToken}`}>
      <Card className="transition hover:border-cal/25">
        <p className="font-semibold">{booking.clubName}</p>
        <p className="text-sm text-ink-soft">{booking.courtName}</p>
        <p className="mt-3 text-sm text-ink-soft first-letter:uppercase">
          {longDate(booking.startTime)} ·{' '}
          {clockTime(booking.startTime, Intl.DateTimeFormat().resolvedOptions().timeZone)} hs
        </p>
      </Card>
    </Link>
  );
}

const PERIODS: { value: Period; label: string }[] = [
  { value: 'week', label: 'Esta semana' },
  { value: 'month', label: 'Este mes' },
  { value: 'year', label: 'Este año' },
  { value: 'all', label: 'Histórico' },
];

/** Cuánto jugaste en la ventana elegida: dos números, no una lista de turnos. */
function ActivitySummary({
  history,
  period,
  onPeriodChange,
  now,
}: {
  history: BookingHistoryItem[];
  period: Period;
  onPeriodChange: (period: Period) => void;
  now: Date;
}) {
  const { turnos, minutos, buckets } = summarize(history, period, now);
  const everPlayed = summarize(history, 'all', now).turnos > 0;

  return (
    <div className="mb-6">
      <div role="group" aria-label="Período" className="flex flex-wrap gap-2">
        {PERIODS.map(({ value, label }) => (
          <Chip key={value} active={period === value} onClick={() => onPeriodChange(value)}>
            {label}
          </Chip>
        ))}
      </div>

      <Card className="mt-3">
        {turnos === 0 ? (
          <p className="text-sm text-ink-soft">
            {everPlayed
              ? 'No jugaste turnos en este período.'
              : history.length > 0
                ? 'Tenés turnos reservados, pero todavía no jugaste ninguno.'
                : 'Todavía no jugaste ningún turno — al reservar y jugar, acá ves tus horas.'}
          </p>
        ) : (
          <>
            <div className="flex items-center gap-8">
              <div>
                <p className="eyebrow text-ink-soft">Turnos jugados</p>
                <p className="display mt-1 text-3xl">{turnos}</p>
              </div>
              <div>
                <p className="eyebrow text-ink-soft">Horas jugadas</p>
                <p className="display mt-1 text-3xl">{formatHours(minutos)}</p>
              </div>
            </div>
            <ActivityChart buckets={buckets} />
          </>
        )}
      </Card>
    </div>
  );
}

/** Vista sin cuenta: los turnos que este dispositivo recuerda, nada más. */
function GuestAccountView({
  bookings,
  onBack,
}: {
  bookings: ReturnType<typeof readGuestBookings>;
  onBack: () => void;
}) {
  return (
    <Screen
      className="pt-6"
      top={
        <TopBar
          name="Tus turnos"
          accountSlot={
            <button
              type="button"
              onClick={onBack}
              aria-label="Volver"
              className="grid size-9 shrink-0 place-items-center rounded-full border border-cal/10 text-ink-soft transition hover:border-cal/25 hover:text-cal"
            >
              <ArrowLeftGlyph className="size-4" />
            </button>
          }
        />
      }
    >
      <div className="mb-5 mt-6">
        <SectionTitle title="Tus turnos" subtitle="Guardados en este dispositivo, sin cuenta." />
      </div>

      <ul className="space-y-3">
        {bookings.map((item) => (
          <li key={item.bookingId}>
            <GuestBookingCard booking={item} />
          </li>
        ))}
      </ul>

      <div className="mt-6 space-y-3">
        <Alert tone="info">
          Estos turnos solo se ven desde este dispositivo. Creá una cuenta para verlos
          desde cualquier lado.
        </Alert>
        <Link
          to="/login?mode=register"
          className="block rounded-full border border-cal/10 bg-vidrio px-5 py-3.5 text-center text-sm font-bold uppercase tracking-[0.12em] text-cal transition hover:border-cal/25"
        >
          Crear cuenta
        </Link>
      </div>
    </Screen>
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
      <path d="M19 12H5" />
      <path d="m12 19-7-7 7-7" />
    </svg>
  );
}
