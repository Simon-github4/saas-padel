import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ApiError, playerApi, type BookingHistoryItem, type RecurringItem } from '../api/client';
import { usePlayerAuth } from '../auth/AuthContext';
import { clockTime } from '../format';
import { dayLabel, fixedThisWeek, manageLabel, nextBooking } from '../upcoming';
import { useGuestBookings } from '../useGuestBookings';
import { StatusBadge } from './Ui';

/** El historial no trae la zona del club: las horas salen en la del dispositivo, como en /account. */
const localTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

/** Un turno por delante, venga de la cuenta, de un fijo o de este dispositivo. */
interface Candidate {
  clubName: string;
  clubSlug: string;
  courtName: string;
  startTime: string;
  endTime: string;
  /**
   * Para saber si se va a jugar. El de un turno de invitado es el que dio su
   * link al cargar la pantalla; si nunca se pudo consultar (sin conexión), se
   * lo da por vigente.
   */
  status: string;
  /** El estado para mostrar; null en un turno de invitado que nunca se pudo consultar. */
  badge: string | null;
  /** Su {@code /manage/:token}; null en la fecha de un fijo, que no trae token. */
  href: string | null;
  fixed: boolean;
}

interface AccountData {
  token: string;
  history: BookingHistoryItem[];
  recurring: RecurringItem[];
}

/**
 * El próximo turno del jugador, con la salida a verlo o cancelarlo. Va arriba
 * del buscador y, filtrado por club, debajo de la portada de cada club.
 *
 * <p>La entrada a "Mis turnos" es una pastilla chica arriba a la derecha, y
 * había jugadores que no la encontraban: abrían la app para cancelar o para
 * mirar a qué hora jugaban y se topaban con un buscador. Esto aparece justo en
 * ese caso -- tiene un turno por delante -- y si no hay ninguno no ocupa lugar.
 *
 * <p>Junta las mismas tres fuentes que lista {@code /account}: el historial de
 * la cuenta, la fecha de esta semana de los fijos y los turnos que este
 * dispositivo reservó sin cuenta.
 *
 * <p>Con {@code clubSlug} solo anuncia turnos de ese club. La página es del club,
 * que es quien paga: anunciar ahí un turno en otro club sería ponerle el nombre
 * de la competencia en su propia portada.
 */
export function NextBookingCard({ clubSlug }: { clubSlug?: string } = {}) {
  const { session, clearExpiredSession } = usePlayerAuth();
  const location = useLocation();
  const token = session?.token;
  const [account, setAccount] = useState<AccountData | null>(null);
  const guest = useGuestBookings();

  useEffect(() => {
    if (!token) {
      return;
    }
    let cancelled = false;
    // Los dos juntos: si los fijos llegaran después, la tarjeta podría cambiar de
    // turno bajo el dedo del jugador.
    void Promise.allSettled([playerApi.bookingHistory(token), playerApi.recurring(token)]).then(
      ([history, recurring]) => {
        if (cancelled) {
          return;
        }
        if (
          history.status === 'rejected' &&
          history.reason instanceof ApiError &&
          history.reason.code === 'SESSION_EXPIRED'
        ) {
          clearExpiredSession();
          return;
        }
        // Si falla otra cosa, la tarjeta se arma con lo que haya: es un atajo, y
        // el error de verdad lo muestra /account.
        setAccount({
          token,
          history: history.status === 'fulfilled' ? history.value : [],
          recurring: recurring.status === 'fulfilled' ? recurring.value : [],
        });
      },
    );
    return () => {
      cancelled = true;
    };
  }, [token, clearExpiredSession]);

  const fromAccount = token && account?.token === token ? account : null;

  const { next, pending, now } = useMemo(() => {
    const now = new Date();
    const candidates: Candidate[] = [];
    const seen = new Set<string>();
    for (const item of fromAccount?.history ?? []) {
      seen.add(item.bookingId);
      candidates.push({
        clubName: item.clubName,
        clubSlug: item.clubSlug,
        courtName: item.courtName,
        startTime: item.startTime,
        endTime: item.endTime,
        status: item.status,
        badge: item.status,
        href: `/manage/${item.managementToken}`,
        fixed: false,
      });
    }
    for (const item of fixedThisWeek(fromAccount?.recurring ?? [], now)) {
      candidates.push({ ...item, badge: item.status, href: null, fixed: true });
    }
    for (const booking of guest.bookings) {
      if (seen.has(booking.bookingId)) {
        continue;
      }
      candidates.push({
        clubName: booking.clubName,
        clubSlug: booking.clubSlug,
        courtName: booking.courtName,
        startTime: booking.startTime,
        // Las entradas viejas no guardan el fin: se toman por terminadas al empezar.
        endTime: booking.endTime ?? booking.startTime,
        status: booking.status ?? 'CONFIRMED',
        badge: booking.status ?? null,
        href: `/manage/${booking.managementToken}`,
        fixed: false,
      });
    }
    const all = nextBooking(candidates, now);
    const here = clubSlug
      ? nextBooking(candidates.filter((item) => item.clubSlug === clubSlug), now).next
      : all.next;
    // El botón lleva a Mis turnos, que lista los de todos los clubes: la cuenta
    // es la de esa pantalla, no solo la de este club.
    return { next: here, pending: all.next ? all.more + 1 : 0, now };
  }, [fromAccount, guest.bookings, clubSlug]);

  // Nada hasta saber cómo están todos: mostrar un turno y cambiarlo un segundo
  // después por otro sería peor que esperar, y anunciar uno que el club ya
  // canceló es justo lo que no puede pasar.
  if ((token && !fromAccount) || !guest.checked || !next) {
    return null;
  }

  // Igual que el botón de la barra: "volver" desde Mis turnos trae a esta misma
  // pantalla, y en /buscar con sus filtros.
  const desde = { from: location.pathname + location.search };
  const action =
    'flex-1 whitespace-nowrap rounded-full px-3 py-3 text-center text-xs font-bold uppercase tracking-[0.1em] transition sm:flex-none sm:px-6';

  return (
    <section
      aria-label="Tu próximo turno"
      className="mt-8 rounded-2xl border border-ladrillo/40 bg-ladrillo/[0.08] p-5 text-left"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="eyebrow text-ladrillo-claro claro:text-ladrillo">Tu próximo turno</p>
          <p className="display mt-1 text-4xl tabular-nums">
            {clockTime(next.startTime, localTimeZone)}
            <span className="ml-1.5 text-lg text-ink-soft">hs</span>
          </p>
          <p className="text-sm font-semibold first-letter:uppercase">{dayLabel(next, now)}</p>
          <p className="mt-2 text-sm text-ink-soft">
            {/* En la página del club, su nombre ya está en todos lados. */}
            {clubSlug ? next.courtName : `${next.clubName} · ${next.courtName}`}
            {next.fixed && ' · Turno fijo'}
          </p>
        </div>
        {next.badge && <StatusBadge status={next.badge} endTime={next.endTime} />}
      </div>
      <div className="mt-4 flex gap-2">
        {next.href && (
          <Link to={next.href} className={`${action} bg-ladrillo text-cal hover:bg-ladrillo/90`}>
            {/* Igual que en Mis turnos: el que ya empezó no se cancela, se mira. */}
            {manageLabel(next, now)}
          </Link>
        )}
        <Link
          to="/account"
          state={desde}
          className={`${action} border border-cal/15 bg-vidrio text-cal hover:border-cal/30`}
        >
          {pending > 1 ? `Mis turnos (${pending})` : 'Mis turnos'}
        </Link>
      </div>
    </section>
  );
}
