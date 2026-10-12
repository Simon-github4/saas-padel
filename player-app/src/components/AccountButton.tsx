import { Link, useLocation } from 'react-router-dom';
import { usePlayerAuth } from '../auth/AuthContext';
import { readGuestBookings } from '../guestBookings';

/**
 * Entrada a la cuenta, para el {@code accountSlot} del TopBar.
 *
 * <p>Dice siempre "Mis turnos", con o sin sesión: es lo que el jugador viene a
 * buscar, y el mismo nombre que tiene el atajo del ícono de la app instalada.
 * Antes, ya logueado, mostraba solo el nombre del jugador, y no había nada que
 * dijera que ahí estaban sus turnos; sin sesión decía "Ver turnos" chiquito y en
 * gris. Había jugadores que no lo encontraban.
 *
 * <p>Con sesión, un punto verde sobre el calendario dice de un vistazo que ya
 * entró; el nombre queda en la etiqueta y en /account. Sin sesión lleva a
 * {@code /account} igual si este dispositivo tiene guardado algún turno
 * reservado sin cuenta, porque es la única forma de volver a cancelarlo; si no,
 * al login.
 */
export function AccountButton() {
  const { session } = usePlayerAuth();
  const location = useLocation();
  // De dónde sale el jugador, para que "volver" lo devuelva acá y no a un
  // destino supuesto. Incluye la query a propósito: en /buscar los filtros
  // viven ahí, y volver sin ellos es volver a otra búsqueda.
  const desde = { from: location.pathname + location.search };
  const hasGuestBookings = !session && readGuestBookings().length > 0;

  const label = session
    ? `Mis turnos de ${session.displayName ?? session.email}`
    : hasGuestBookings
      ? 'Mis turnos, los de este dispositivo'
      : 'Mis turnos: iniciá sesión para verlos';

  return (
    <Link
      to={session || hasGuestBookings ? '/account' : '/login'}
      state={desde}
      aria-label={label}
      title={label}
      className="flex h-9 shrink-0 items-center gap-2 rounded-full border border-cal/20 bg-vidrio pl-3 pr-3.5 text-cal transition hover:border-cal/40"
    >
      <span className="relative" aria-hidden>
        <CalendarGlyph className="size-4" />
        {session && (
          <span className="absolute -right-1 -top-1 size-2 rounded-full bg-emerald-400 ring-2 ring-pista" />
        )}
      </span>
      <span className="whitespace-nowrap text-xs font-bold uppercase tracking-[0.1em]">Mis turnos</span>
    </Link>
  );
}

function CalendarGlyph({ className }: { className?: string }) {
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
      <rect x="3.5" y="5" width="17" height="15.5" rx="2.5" />
      <path d="M3.5 10h17" />
      <path d="M8 3v4" />
      <path d="M16 3v4" />
    </svg>
  );
}
