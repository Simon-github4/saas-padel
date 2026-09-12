import { Link, useLocation } from 'react-router-dom';
import { usePlayerAuth } from '../auth/AuthContext';
import { readGuestBookings } from '../guestBookings';

/**
 * Entrada a la cuenta, para el {@code accountSlot} del TopBar.
 *
 * <p>Sin sesión muestra "Ver turnos", salvo que este dispositivo tenga guardado
 * algún turno reservado sin cuenta: ahí muestra "Tus turnos" y lleva a
 * {@code /account} igual, porque es la unica forma de volver a cancelarlo. Ya
 * logueado, la identidad del jugador con un punto verde delante para que se
 * vea de un vistazo el estado.
 */
export function AccountButton() {
  const { session } = usePlayerAuth();
  const location = useLocation();
  // De dónde sale el jugador, para que "volver" lo devuelva acá y no a un
  // destino supuesto. Incluye la query a propósito: en /buscar los filtros
  // viven ahí, y volver sin ellos es volver a otra búsqueda.
  const desde = { from: location.pathname + location.search };

  if (session) {
    return (
      <Link
        to="/account"
        state={desde}
        aria-label={`Ver mis turnos de ${session.displayName ?? session.email}`}
        className="flex h-9 shrink-0 items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-500/10 pl-3 pr-4 transition hover:border-emerald-500/50"
      >
        <span className="size-1.5 rounded-full bg-emerald-400" aria-hidden />
        <span className="max-w-28 truncate text-sm font-semibold text-emerald-300">
          {session.displayName ?? session.email}
        </span>
      </Link>
    );
  }

  const hasGuestBookings = readGuestBookings().length > 0;

  return (
    <Link
      to={hasGuestBookings ? '/account' : '/login'}
      state={desde}
      aria-label={hasGuestBookings ? 'Ver tus turnos de este dispositivo' : 'Iniciar sesión o ver tus turnos'}
      className="flex h-9 shrink-0 items-center gap-1.5 rounded-full border border-cal/10 px-3 text-ink-soft transition hover:border-cal/25 hover:text-cal"
    >
      <PersonGlyph className="size-4" />
      <span className="text-xs font-bold uppercase tracking-[0.1em]">
        {hasGuestBookings ? 'Tus turnos' : 'Ver turnos'}
      </span>
    </Link>
  );
}

function PersonGlyph({ className }: { className?: string }) {
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
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20c0-3.5 3-6 7-6s7 2.5 7 6" />
    </svg>
  );
}