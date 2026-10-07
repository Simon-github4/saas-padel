import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { usePlayerAuth } from '../auth/AuthContext';
import { useClubTheme } from '../clubTheme';
import {
  ApiError,
  playerApi,
  type BookingHistoryItem,
  type RecurringItem,
  type WaitlistItem,
} from '../api/client';
import { clockTime, dateRange, formatHours, longDate, money } from '../format';
import { readGuestBookings, type GuestBooking } from '../guestBookings';
import { Alert, Button, Card, Chip, Loading, Screen, SectionTitle, StatusBadge, TopBar } from '../components/Ui';
import { ActivityChart } from '../components/ActivityChart';
import { summarize, type Period } from '../stats';
import { fixedThisWeek, isFixed, splitBookings, whenLabel, type AccountBooking } from '../upcoming';

/** El historial no trae la zona del club: las horas salen en la del dispositivo. */
const localTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

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
  useClubTheme();
  const { session, logout, clearExpiredSession } = usePlayerAuth();
  const [history, setHistory] = useState<BookingHistoryItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [period, setPeriod] = useState<Period>('month');
  // Turnos fijos por el teléfono de la cuenta. Vacío hasta que llegan, y si
  // fallan también: la sección no aparece y el resto de la pantalla sigue igual.
  const [recurring, setRecurring] = useState<RecurringItem[]>([]);
  // Para no mostrar "no encontramos turnos fijos" antes de haberlos buscado.
  const [recurringLoaded, setRecurringLoaded] = useState(false);
  const now = useMemo(() => new Date(), [history, recurring]);
  // La fecha de esta semana de cada fijo se lista con los demás turnos, en Hoy o
  // en Próximos según el día.
  const { today, upcoming, past } = useMemo(
    () => splitBookings<AccountBooking>([...(history ?? []), ...fixedThisWeek(recurring, now)], now),
    [history, recurring, now],
  );
  // Se leen siempre, con o sin sesión. Un turno reservado como invitado no
  // aparece en el historial de la cuenta -- nada prueba que sea de quien
  // pregunta, solo que alguien escribió su teléfono -- pero este dispositivo sí
  // sabe que lo reservó, y esconderlo al iniciar sesión era perder el único
  // camino que le quedaba al jugador para volver a abrirlo.
  const guestBookings = useMemo(() => readGuestBookings(), [session]);

  // A dónde vuelve "atrás", en dos escalones.
  //
  // Si el jugador entró desde adentro de la app, el botón de la barra dejó
  // anotado de dónde venía (AccountButton) y se vuelve exactamente ahí, con la
  // query incluida: desde /buscar tiene que volver a esa misma búsqueda, no a
  // una nueva ni al club de un turno cualquiera.
  //
  // Si no hay nada anotado -- se entró por un link de WhatsApp, por un marcador
  // o recargando la página -- se cae a un destino declarado y no a
  // navigate(-1), que en esos casos saca de la app o devuelve al login recién
  // completado. La raíz no sirve como destino: es la landing comercial para
  // dueños de club.
  const location = useLocation();
  const volverA = useMemo(() => {
    const desde = (location.state as { from?: string } | null)?.from;
    if (desde?.startsWith('/') && !desde.startsWith('/account')) {
      return desde;
    }
    const club = history?.[0]?.clubSlug ?? guestBookings[0]?.clubSlug;
    return club ? `/club/${club}` : '/buscar';
  }, [location.state, history, guestBookings]);

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

  useEffect(() => {
    if (!session) {
      return;
    }
    let cancelled = false;
    playerApi
      .recurring(session.token)
      .then((items) => {
        if (!cancelled) {
          setRecurring(items);
          setRecurringLoaded(true);
        }
      })
      .catch(() => {
        // El historial de arriba ya maneja la sesión vencida y sus errores; sin
        // turnos fijos, la sección simplemente no aparece (ni la nota que explica
        // cómo verlos: si falló, no sabemos si tiene o no).
      });
    return () => {
      cancelled = true;
    };
  }, [session]);

  if (!session && guestBookings.length === 0) {
    return <Navigate to="/login" replace />;
  }

  if (!session) {
    return <GuestAccountView bookings={guestBookings} backTo={volverA} />;
  }

  return (
    <Screen
      className="pt-6"
      top={
        <TopBar
          name="Mis turnos"
          accountSlot={
            <Link
              to={volverA}
              aria-label="Volver"
              className="grid size-9 shrink-0 place-items-center rounded-full border border-cal/10 text-ink-soft transition hover:border-cal/25 hover:text-cal"
            >
              <ArrowLeftGlyph className="size-4" />
            </Link>
          }
        />
      }
    >
      <div className="mb-5 mt-6 flex items-start justify-between gap-4">
        <div>
          <SectionTitle
            title={session.displayName ?? 'Mis turnos'}
            subtitle={session.email}
          />
          {session.phoneNumber && (
            <p className="mt-0.5 text-sm tabular-nums text-ink-soft">{session.phoneDisplay ?? session.phoneNumber}</p>
          )}
        </div>
        <Button variant="secondary" className="w-auto px-4" onClick={logout}>
          Cerrar sesión
        </Button>
      </div>

      {loading && <Loading />}
      {error && <Alert>{error}</Alert>}

      {!loading && !error && history && history.length === 0 && recurring.length === 0 && guestBookings.length === 0 && (
        <>
          <Alert tone="info">Todavía no reservaste ningún turno.</Alert>
          {recurringLoaded && <RecurringHint session={session} />}
        </>
      )}

      {!loading && !error && history && history.length === 0 && guestBookings.length > 0 && (
        <Alert tone="info">
          Todavía no reservaste ningún turno con esta cuenta. Abajo están los que
          reservaste sin iniciar sesión.
        </Alert>
      )}

      {!loading && history && (history.length > 0 || recurring.length > 0) && (
        <>
          {today.length > 0 && (
            <section className="mb-8">
              <SectionTitle title="Hoy" />
              <ul className="mt-4 space-y-3">
                {today.map((item, index) => (
                  <li key={bookingKey(item)}>
                    <TodayBookingCard item={item} now={now} next={index === 0} />
                  </li>
                ))}
              </ul>
            </section>
          )}
          {upcoming.length > 0 && (
            <section className="mb-8">
              <SectionTitle title="Próximos" />
              <BookingList items={upcoming} />
            </section>
          )}
          {recurring.length > 0 ? (
            <RecurringSection items={recurring} />
          ) : (
            recurringLoaded && <RecurringHint session={session} />
          )}
          {history.length > 0 && (
            <ActivitySummary history={history} period={period} onPeriodChange={setPeriod} now={now} />
          )}
          {past.length > 0 && (
            <section>
              <SectionTitle title="Anteriores" />
              <BookingList items={past} />
            </section>
          )}
        </>
      )}

      <WaitlistSection token={session.token} onSessionExpired={clearExpiredSession} />

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
 * Horarios llenos en los que el jugador está anotado, con la opción de bajarse.
 *
 * <p>Antes no había forma de salir de la lista: anotarse era para siempre, y el
 * aviso llegaba igual aunque ya no le sirviera el horario. Si no está anotado en
 * nada, la sección no aparece: no hay nada que hacer acá.
 */
function WaitlistSection({ token, onSessionExpired }: { token: string; onSessionExpired: () => void }) {
  const [entries, setEntries] = useState<WaitlistItem[]>([]);
  const [leaving, setLeaving] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    playerApi
      .waitlist(token)
      .then((items) => {
        if (!cancelled) {
          setEntries(items);
        }
      })
      .catch((err) => {
        if (err instanceof ApiError && err.requiresLogin) {
          onSessionExpired();
        }
        // Si falla por otra cosa, la sección simplemente no aparece: el
        // historial de turnos de arriba ya muestra su propio error.
      });
    return () => {
      cancelled = true;
    };
  }, [token, onSessionExpired]);

  async function leave(entryId: string) {
    setError(null);
    setLeaving(entryId);
    try {
      await playerApi.leaveWaitlist(token, entryId);
      setEntries((current) => current.filter((entry) => entry.entryId !== entryId));
    } catch (err) {
      if (err instanceof ApiError && err.requiresLogin) {
        onSessionExpired();
        return;
      }
      if (err instanceof ApiError && err.status === 404) {
        // Ya no estaba (reservó ese horario, o el turno ya pasó): lo mismo que se pedía.
        setEntries((current) => current.filter((entry) => entry.entryId !== entryId));
        return;
      }
      setError(err instanceof ApiError ? err.message : 'No pudimos sacarte de la lista. Probá de nuevo.');
    } finally {
      setLeaving(null);
    }
  }

  if (entries.length === 0) {
    return null;
  }

  return (
    <section className="mt-8">
      <SectionTitle
        title="Listas de espera"
        subtitle="Te avisamos si se libera una cancha en estos horarios."
      />
      {error && (
        <div className="mt-4">
          <Alert>{error}</Alert>
        </div>
      )}
      <ul className="mt-4 space-y-3">
        {entries.map((entry) => (
          <li key={entry.entryId}>
            <Card>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <Link
                    to={`/club/${entry.clubSlug}`}
                    className="font-semibold underline-offset-4 hover:underline"
                  >
                    {entry.clubName}
                  </Link>
                  <p className="mt-1 text-sm text-ink-soft first-letter:uppercase">
                    {longDate(entry.startsAt, entry.timeZone)} · {clockTime(entry.startsAt, entry.timeZone)} hs
                  </p>
                </div>
                <Button
                  variant="secondary"
                  className="w-auto shrink-0 px-4"
                  onClick={() => void leave(entry.entryId)}
                  disabled={leaving === entry.entryId}
                >
                  {leaving === entry.entryId ? 'Saliendo…' : 'Salir'}
                </Button>
              </div>
            </Card>
          </li>
        ))}
      </ul>
    </section>
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

/** Clave estable para la lista: un turno por su id, la fecha de un fijo por su regla. */
function bookingKey(item: AccountBooking): string {
  return isFixed(item) ? `fijo-${item.recurringId}` : item.bookingId;
}

/**
 * Un turno de la cuenta abre su gestión; la fecha de un turno fijo no, porque no
 * trae token (ver {@code RecurringItem}).
 */
function ManageLink({ item, children }: { item: AccountBooking; children: ReactNode }) {
  if (isFixed(item)) {
    return <>{children}</>;
  }
  return <Link to={`/manage/${item.managementToken}`}>{children}</Link>;
}

/** Turnos que no son de hoy: cada uno abre su gestión, salvo las fechas de los fijos. */
function BookingList({ items }: { items: AccountBooking[] }) {
  return (
    <ul className="mt-4 space-y-3">
      {items.map((item) => (
        <li key={bookingKey(item)}>
          <ManageLink item={item}>
            <Card className={isFixed(item) ? '' : 'transition hover:border-cal/25'}>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-semibold">{item.clubName}</p>
                  <p className="text-sm text-ink-soft">{item.courtName}</p>
                </div>
                <StatusBadge status={item.status} endTime={item.endTime} />
              </div>
              <p className="mt-3 text-sm text-ink-soft first-letter:uppercase">
                {longDate(item.startTime)} · {clockTime(item.startTime, localTimeZone)} hs
              </p>
              {isFixed(item) ? (
                <p className="eyebrow mt-2 text-ink-soft">Turno fijo</p>
              ) : (
                <p className="mt-1 text-sm font-semibold tabular-nums">{money(item.totalPrice)}</p>
              )}
            </Card>
          </ManageLink>
        </li>
      ))}
    </ul>
  );
}

/**
 * Un turno de hoy. Lo que manda es cuándo se juega -- cuánto falta y la hora en
 * grande --, con el color del club para que no se confunda con el resto. El más
 * cercano, además, brilla.
 */
function TodayBookingCard({ item, now, next }: { item: AccountBooking; now: Date; next: boolean }) {
  const fixed = isFixed(item);
  return (
    <ManageLink item={item}>
      <div
        className={`rounded-2xl border p-5 transition ${
          next
            ? `border-ladrillo/50 bg-ladrillo/[0.12] [box-shadow:var(--shadow-glow)] ${fixed ? '' : 'hover:border-ladrillo/70'}`
            : `border-ladrillo/25 bg-ladrillo/[0.05] ${fixed ? '' : 'hover:border-ladrillo/50'}`
        }`}
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="eyebrow text-ladrillo-claro claro:text-ladrillo">{whenLabel(item, now)}</p>
            <p className="display mt-1 text-4xl tabular-nums">
              {clockTime(item.startTime, localTimeZone)}
              <span className="ml-1.5 text-lg text-ink-soft">hs</span>
            </p>
          </div>
          <StatusBadge status={item.status} endTime={item.endTime} />
        </div>
        <p className="mt-3 font-semibold">{item.clubName}</p>
        <p className="text-sm text-ink-soft">
          {item.courtName}
          {fixed && ' · Turno fijo'}
        </p>
      </div>
    </ManageLink>
  );
}

/**
 * Cómo ver los turnos fijos, cuando no apareció ninguno.
 *
 * <p>Se buscan por el teléfono de la cuenta, y eso no es obvio: un jugador con
 * fijo que se registró sin teléfono, o con otro número que el que tiene el club,
 * no ve nada y no sabe por qué. Lo que dice depende de qué le falta a la cuenta.
 */
function RecurringHint({
  session,
}: {
  session: { phoneNumber: string | null; phoneDisplay: string | null; phoneLocked: boolean };
}) {
  let text: string;
  if (!session.phoneNumber) {
    text =
      '¿Tenés turno fijo en un club? Para verlo acá, tu cuenta necesita el mismo teléfono que le diste al club. Se guarda la próxima vez que reserves con tu cuenta.';
  } else if (!session.phoneLocked) {
    text =
      '¿Tenés turno fijo en un club? Aparece acá cuando tu teléfono esté verificado: se verifica la primera vez que reservás con él.';
  } else {
    text = `¿Tenés turno fijo en un club? Aparece acá si el club lo cargó con tu número, ${
      session.phoneDisplay ?? session.phoneNumber
    }. Si no lo ves, pedile al club que revise el teléfono.`;
  }
  return <p className="mb-8 mt-3 px-1 text-xs text-ink-soft">{text}</p>;
}

/** "Todos los martes": el plural de cada día, de lunes (1) a domingo (7). */
const EVERY_DAY = ['', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábados', 'domingos'];

/**
 * Los turnos fijos del jugador: la regla, no cada fecha. La fecha de esta
 * semana ya está arriba, en Hoy o en Próximos.
 *
 * <p>Sin link ni acciones: salen del teléfono de la cuenta, que nadie verifica,
 * así que se pueden mirar pero no tocar. Cambiarlos o suspender una semana
 * sigue siendo hablar con el club.
 */
function RecurringSection({ items }: { items: RecurringItem[] }) {
  return (
    <section className="mb-8">
      <SectionTitle title="Turnos fijos" subtitle="Los que tenés pactados con el club, todas las semanas." />
      <ul className="mt-4 space-y-3">
        {items.map((fixed) => (
          <li key={fixed.recurringId}>
            <Card>
              <p className="eyebrow text-ink-soft">Todos los {EVERY_DAY[fixed.dayOfWeek]}</p>
              <p className="display mt-1 text-3xl tabular-nums">
                {fixed.startTime}
                <span className="ml-1.5 text-base text-ink-soft">hs</span>
              </p>
              <p className="mt-3 font-semibold">{fixed.clubName}</p>
              <p className="text-sm text-ink-soft">
                {fixed.courtName} · {formatHours(fixed.durationMinutes)}
              </p>
              {(fixed.next || fixed.validUntil) && (
                <div className="mt-3 space-y-0.5 text-sm text-ink-soft">
                  {fixed.next && (
                    <p>
                      Próximo: <span className="text-cal">{longDate(fixed.next.startTime, fixed.timeZone)}</span>
                    </p>
                  )}
                  {fixed.validUntil && <p>Hasta el {longDate(fixed.validUntil)}</p>}
                </div>
              )}
            </Card>
          </li>
        ))}
      </ul>
    </section>
  );
}

const PERIODS: { value: Period; label: string }[] = [
  { value: 'week', label: 'Últimos 7 días' },
  { value: 'month', label: 'Último mes' },
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
  const { turnos, minutos, buckets, range } = summarize(history, period, now);
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
        {/* Qué días abarca la ventana: "último mes" o "este año" solos no dicen
            desde cuándo cuentan. */}
        {everPlayed && range && (
          <p className="eyebrow mb-4 text-ink-soft">{dateRange(range.from, range.to)}</p>
        )}
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
  backTo,
}: {
  bookings: ReturnType<typeof readGuestBookings>;
  backTo: string;
}) {
  return (
    <Screen
      className="pt-6"
      top={
        <TopBar
          name="Tus turnos"
          accountSlot={
            <Link
              to={backTo}
              aria-label="Volver"
              className="grid size-9 shrink-0 place-items-center rounded-full border border-cal/10 text-ink-soft transition hover:border-cal/25 hover:text-cal"
            >
              <ArrowLeftGlyph className="size-4" />
            </Link>
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
          desde cualquier lado y, con tu teléfono, ver también tus turnos fijos.
        </Alert>
        {/* Con el color primario del club, como "Pagar seña": es la acción de la
            pantalla y no se tiene que confundir con las tarjetas de los turnos. */}
        <Link
          to="/login?mode=register"
          className="block rounded-full bg-ladrillo px-5 py-3.5 text-center text-sm font-bold uppercase tracking-[0.12em] text-cal transition [box-shadow:var(--shadow-glow)] hover:bg-ladrillo/90"
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
