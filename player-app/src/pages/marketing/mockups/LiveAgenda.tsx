import { useEffect, useState } from 'react';
import { money } from '../../../format';
import { CheckGlyph, useInView, usePrefersReducedMotion } from '../motion';

/**
 * Agenda del club que se llena sola: la promesa del titular, en movimiento.
 *
 * <p>Todo es de ejemplo, nada viene de un club real. Las reservas entran de a
 * una, con el aviso que vería el dueño, hasta dejar la noche casi completa;
 * después la agenda se vacía y vuelve a empezar. Sólo corre mientras está en
 * pantalla, y con movimiento reducido se muestra directamente llena.
 */

const COURTS = ['Cancha 1', 'Cancha 2', 'Cancha 3'];
const HOURS = ['18:00', '19:30', '21:00', '22:30'];
const DEPOSIT = 12000;

type Booking = {
  court: number;
  hour: number;
  name: string;
  deposit: boolean;
};

/** Lo que ya estaba antes de que el link empiece a trabajar. */
const PRELOADED = [
  { court: 0, hour: 0, label: 'Fijo' },
  { court: 1, hour: 1, label: 'Tel.' },
];

const INCOMING: Booking[] = [
  { court: 1, hour: 2, name: 'Lucía', deposit: true },
  { court: 0, hour: 1, name: 'Martín', deposit: false },
  { court: 2, hour: 0, name: 'Nico', deposit: true },
  { court: 0, hour: 2, name: 'Sofi', deposit: true },
  { court: 2, hour: 1, name: 'Joaco', deposit: false },
  { court: 1, hour: 3, name: 'Caro', deposit: true },
  { court: 1, hour: 0, name: 'Fede', deposit: true },
  { court: 2, hour: 2, name: 'Agus', deposit: false },
];

export function LiveAgenda() {
  const reduced = usePrefersReducedMotion();
  const [ref, inView] = useInView<HTMLDivElement>({ once: false, rootMargin: '0px' });
  const [count, setCount] = useState(0);
  const [clearing, setClearing] = useState(false);

  const shown = reduced ? INCOMING.length : count;

  useEffect(() => {
    if (reduced || !inView) {
      return;
    }
    let next: number;
    if (clearing) {
      next = window.setTimeout(() => {
        setCount(0);
        setClearing(false);
      }, 600);
    } else if (count === INCOMING.length) {
      // Pausa con la noche llena, antes de apagarla y arrancar de nuevo.
      next = window.setTimeout(() => setClearing(true), 3800);
    } else {
      next = window.setTimeout(() => setCount(count + 1), count === 0 ? 1400 : 1700);
    }
    return () => window.clearTimeout(next);
  }, [count, clearing, inView, reduced]);

  const bookings = INCOMING.slice(0, shown);
  const latest = shown > 0 ? INCOMING[shown - 1] : null;

  return (
    <div ref={ref} aria-hidden className="relative mx-auto w-full max-w-[34rem]">
      <div className="overflow-hidden rounded-[1.75rem] border border-cal/10 bg-vidrio/90 backdrop-blur [box-shadow:var(--shadow-card)]">
        <div className="flex items-center justify-between gap-4 border-b border-cal/[0.07] px-5 py-4 sm:px-6">
          <div>
            <p className="eyebrow text-ink-mute">Panel del club</p>
            <p className="display mt-1 text-xl tracking-[0.06em]">Agenda de hoy</p>
          </div>
          <p className="eyebrow flex items-center gap-2 rounded-full border border-cal/10 px-3 py-1.5 text-ink-soft">
            <span className="mk-ping size-1.5 rounded-full bg-ladrillo-claro" />
            Reservando
          </p>
        </div>

        <div
          className={`grid grid-cols-[auto_repeat(4,minmax(0,1fr))] gap-1.5 px-4 py-5 transition-opacity duration-500 sm:gap-2 sm:px-6 ${
            clearing ? 'opacity-30' : 'opacity-100'
          }`}
        >
          <div />
          {HOURS.map((hour) => (
            <div key={hour} className="eyebrow pb-1 text-center tabular-nums text-ink-mute">
              {hour}
            </div>
          ))}
          {COURTS.map((court, c) => (
            <Row key={court} court={court} courtIndex={c} bookings={bookings} latest={latest} />
          ))}
        </div>

        <dl className="grid grid-cols-2 border-t border-cal/[0.07] text-center">
          <Stat label="Reservas" value={String(PRELOADED.length + shown)} tickKey={shown} />
          <Stat label="Mensajes contestados" value="0" bordered />
        </dl>
      </div>

      {latest && !reduced && (
        <div
          key={shown}
          className="mk-toast absolute -bottom-9 left-3 right-3 flex items-center gap-3 rounded-2xl border border-cal/10 bg-vidrio-alto/95 p-3 pr-4 backdrop-blur-md [box-shadow:0_18px_40px_rgba(0,0,0,0.6)] sm:-left-8 sm:right-auto sm:min-w-72"
        >
          <span className="grid size-9 shrink-0 place-items-center rounded-full bg-ladrillo text-cal">
            <CheckGlyph className="size-4" />
          </span>
          <div className="min-w-0 text-left">
            <p className="truncate text-sm font-semibold">
              {latest.name} reservó · {COURTS[latest.court]} · {HOURS[latest.hour]}
            </p>
            <p className="truncate text-xs text-ink-soft">
              {latest.deposit ? `Seña de ${money(DEPOSIT)} pagada` : 'Confirmada de palabra'}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

function Row({
  court,
  courtIndex,
  bookings,
  latest,
}: {
  court: string;
  courtIndex: number;
  bookings: Booking[];
  latest: Booking | null;
}) {
  return (
    <>
      <div className="eyebrow flex items-center pr-1 text-ink-soft sm:pr-2">
        <span className="sm:hidden">C{courtIndex + 1}</span>
        <span className="hidden sm:inline">{court}</span>
      </div>
      {HOURS.map((hour, h) => {
        const pre = PRELOADED.find((p) => p.court === courtIndex && p.hour === h);
        const booking = bookings.find((b) => b.court === courtIndex && b.hour === h);
        const base = 'grid h-12 place-items-center rounded-lg px-1 text-center sm:h-14';
        if (pre) {
          return (
            <div key={hour} className={`${base} bg-cal/[0.07]`}>
              <span className="eyebrow text-arena">{pre.label}</span>
            </div>
          );
        }
        if (booking) {
          return (
            <div
              key={hour}
              className={`${base} border border-ladrillo/40 bg-ladrillo/15 ${booking === latest ? 'mk-pop' : ''}`}
            >
              <span className="w-full truncate text-[0.7rem] font-semibold leading-tight text-ladrillo-claro sm:text-xs">
                {booking.name}
              </span>
            </div>
          );
        }
        return <div key={hour} className={`${base} border border-dashed border-cal/10`} />;
      })}
    </>
  );
}

function Stat({
  label,
  value,
  tickKey,
  bordered = false,
}: {
  label: string;
  value: string;
  tickKey?: number;
  bordered?: boolean;
}) {
  return (
    <div className={`px-2 py-4 ${bordered ? 'border-l border-cal/[0.07]' : ''}`}>
      {/* No usa .eyebrow: esa clase fija el tamaño y acá los rótulos tienen
          que entrar en la mitad del ancho de un teléfono. */}
      <dt className="text-[0.6rem] font-semibold uppercase leading-tight tracking-[0.14em] text-ink-mute [font-stretch:85%]">
        {label}
      </dt>
      <dd className="display mt-1.5 overflow-hidden text-2xl tabular-nums">
        <span key={tickKey} className={tickKey ? 'mk-tick' : ''}>
          {value}
        </span>
      </dd>
    </div>
  );
}
