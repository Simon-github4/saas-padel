import { useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { money } from '../../format';
import { DEMO_CLUB_PATH } from './Cta';
import { CONTAINER, CheckGlyph, Reveal, SectionHeading } from './motion';

/**
 * Demo de los dos lados del sistema a la vez: el jugador reserva en el
 * celular y el turno aparece en la agenda del panel, sin nadie en el medio.
 *
 * <p>Todo pasa en el navegador, con datos de ejemplo: no toca la API ni crea
 * nada. Reemplaza a las dos capturas quietas que había antes, porque "el
 * jugador reserva solo" se entiende mucho mejor haciéndolo que leyéndolo.
 */

const COURTS = ['Cancha 1', 'Cancha 2', 'Cancha 3'];
const HOURS = [
  { time: '18:00', perPerson: 6000, promo: false },
  { time: '19:30', perPerson: 6000, promo: false },
  { time: '21:00', perPerson: 6500, promo: false },
  { time: '22:30', perPerson: 5000, promo: true },
];
const PLAYERS = 4;

type Cell = { label: string; kind: 'web' | 'fijo' | 'tel'; mine?: boolean };
type Agenda = Record<string, Cell>;
type Step = 'hora' | 'datos' | 'listo';

const cellKey = (court: number, hour: number) => `${court}-${hour}`;

const INITIAL: Agenda = {
  '0-0': { label: 'Fijo', kind: 'fijo' },
  '1-0': { label: 'Pablo', kind: 'web' },
  '2-0': { label: 'Tel.', kind: 'tel' },
  '0-1': { label: 'Maru', kind: 'web' },
  '1-1': { label: 'Fijo', kind: 'fijo' },
  '2-2': { label: 'Leo', kind: 'web' },
};

function freeCourts(agenda: Agenda, hour: number): number[] {
  return COURTS.map((_, court) => court).filter((court) => !agenda[cellKey(court, hour)]);
}

const POINTS = [
  'Cargás a mano el turno que entró por teléfono, en la misma agenda.',
  'Cobrás en el mostrador y marcás el ausente sin salir de la agenda.',
  'Los jugadores quedan con su historial y una marca de confianza.',
  'Alertas de lo que necesita tu atención, sin revisar todo.',
];

export function DemoSection() {
  const [agenda, setAgenda] = useState<Agenda>(INITIAL);
  const [step, setStep] = useState<Step>('hora');
  const [hour, setHour] = useState<number | null>(null);
  const [hovered, setHovered] = useState<number | null>(null);
  const [name, setName] = useState('');
  // Sólo para que el formulario se parezca al real: no se usa al reservar.
  const [phone, setPhone] = useState('');
  const [last, setLast] = useState<{ court: number; hour: number; name: string } | null>(null);

  const pendingCourt = step === 'datos' && hour !== null ? (freeCourts(agenda, hour)[0] ?? null) : null;
  const focusHour = step === 'listo' ? null : (hovered ?? hour);
  const changed = agenda !== INITIAL;

  function pick(index: number) {
    setHour(index);
    setHovered(null);
    setStep('datos');
  }

  function book(event: FormEvent) {
    event.preventDefault();
    if (hour === null || pendingCourt === null) {
      return;
    }
    const label = name.trim() || 'Vos';
    setAgenda({ ...agenda, [cellKey(pendingCourt, hour)]: { label, kind: 'web', mine: true } });
    setLast({ court: pendingCourt, hour, name: label });
    setStep('listo');
  }

  function reset() {
    setAgenda(INITIAL);
    setStep('hora');
    setHour(null);
    setLast(null);
  }

  return (
    <section id="probalo" className="scroll-mt-32 lg:scroll-mt-16 py-24 md:py-36">
      <div className={CONTAINER}>
        <SectionHeading
          eyebrow="Probalo"
          title="Reservá como un jugador"
          lead="Tocá un horario en el celular y mirá cómo entra en la agenda del club, sin que nadie conteste un mensaje. Es una simulación con datos de ejemplo: acá no se reserva ninguna cancha de verdad."
        />

        <div className="mt-16 grid items-start gap-14 md:mt-20 lg:grid-cols-[20rem_minmax(0,1fr)] lg:gap-16 xl:gap-24">
          <Reveal className="mx-auto w-full max-w-[20rem]">
            <p className="eyebrow mb-4 text-center text-ink-mute">El jugador, en su celular</p>
            <div className="relative">
              <Phone>
                {step === 'hora' && (
                  <HourStep
                    agenda={agenda}
                    onPick={pick}
                    onHover={setHovered}
                    onReset={changed ? reset : undefined}
                  />
                )}
                {step === 'datos' && hour !== null && pendingCourt !== null && (
                  <DetailsStep
                    hour={hour}
                    court={pendingCourt}
                    name={name}
                    onName={setName}
                    phone={phone}
                    onPhone={setPhone}
                    onBack={() => {
                      setStep('hora');
                      setHour(null);
                    }}
                    onSubmit={book}
                  />
                )}
                {step === 'listo' && last && (
                  <DoneStep
                    court={last.court}
                    hour={last.hour}
                    onAgain={() => {
                      setStep('hora');
                      setHour(null);
                    }}
                  />
                )}
              </Phone>
              {/* El único link a un club de verdad dentro de la demo: quien
                  quiere ver el sistema andando con datos reales, puede. Va
                  como estampa, no como pie de foto, para que se lea como
                  invitación. Pestaña nueva para no perder la landing. */}
              <Link
                to={DEMO_CLUB_PATH}
                target="_blank"
                className="absolute -right-3 -top-3 inline-flex -rotate-6 items-center gap-1.5 rounded-full bg-ladrillo px-4 py-2 text-xs font-bold uppercase tracking-[0.1em] text-cal transition duration-300 [box-shadow:var(--shadow-glow)] hover:-rotate-2 hover:scale-105"
              >
                Club real
                <span aria-hidden>↗</span>
              </Link>
            </div>
          </Reveal>

          <Reveal delay={120} className="min-w-0">
            <p className="eyebrow mb-4 text-center text-ink-mute lg:text-left">Vos, en el panel</p>
            <Panel
              agenda={agenda}
              focusHour={focusHour}
              pendingCourt={pendingCourt}
              pendingHour={step === 'datos' ? hour : null}
              last={last}
            />

            <h3 className="mt-14 text-3xl tracking-[0.04em] md:text-4xl">
              Vos manejás la cancha, no la planilla
            </h3>
            <ul className="mt-6 grid gap-x-8 gap-y-4 sm:grid-cols-2">
              {POINTS.map((point) => (
                <li key={point} className="flex gap-3 text-sm leading-relaxed text-ink-soft">
                  <CheckGlyph className="mt-0.5 size-4 shrink-0 text-ladrillo-claro" />
                  {point}
                </li>
              ))}
            </ul>
          </Reveal>
        </div>
      </div>
    </section>
  );
}

function Phone({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-[3rem] border border-cal/15 bg-[#050505] p-2.5 [box-shadow:0_40px_80px_-20px_rgba(0,0,0,0.9),inset_0_1px_0_rgba(255,255,255,0.06)]">
      <div className="relative flex min-h-[39rem] flex-col overflow-hidden rounded-[2.4rem] bg-pista">
        <div className="flex items-center justify-between px-7 pb-2 pt-3.5 text-[0.7rem] font-semibold tabular-nums text-arena">
          <span>21:04</span>
          <span aria-hidden className="absolute left-1/2 top-2.5 h-6 w-24 -translate-x-1/2 rounded-full bg-black" />
          <span aria-hidden className="flex items-end gap-0.5">
            <span className="h-1.5 w-0.5 rounded-full bg-arena" />
            <span className="h-2 w-0.5 rounded-full bg-arena" />
            <span className="h-2.5 w-0.5 rounded-full bg-arena" />
            <span className="ml-1 h-2.5 w-5 rounded-[3px] border border-arena/70 p-px">
              <span className="block h-full w-3/4 rounded-[1px] bg-arena" />
            </span>
          </span>
        </div>
        <div className="flex items-center justify-between border-b border-cal/10 px-5 pb-3 pt-2">
          <p className="display text-lg tracking-[0.14em]">Tu club</p>
          <span className="eyebrow rounded-full border border-cal/15 px-2.5 py-1 text-[0.6rem] text-ink-soft">
            Demo
          </span>
        </div>
        <div className="flex flex-1 flex-col p-5">{children}</div>
      </div>
    </div>
  );
}

function HourStep({
  agenda,
  onPick,
  onHover,
  onReset,
}: {
  agenda: Agenda;
  onPick: (hour: number) => void;
  onHover: (hour: number | null) => void;
  onReset?: () => void;
}) {
  const allFull = HOURS.every((_, index) => freeCourts(agenda, index).length === 0);
  return (
    <div className="mk-fade-in flex flex-1 flex-col">
      <p className="eyebrow text-ink-mute">Hoy, martes</p>
      <p className="display mt-1 text-2xl tracking-[0.06em]">Elegí la hora</p>
      <div className="mt-5 grid grid-cols-2 gap-2.5" onMouseLeave={() => onHover(null)}>
        {HOURS.map((slot, index) => {
          const free = freeCourts(agenda, index).length;
          const full = free === 0;
          return (
            <button
              key={slot.time}
              type="button"
              disabled={full}
              onClick={() => onPick(index)}
              onMouseEnter={() => onHover(index)}
              onFocus={() => onHover(index)}
              onBlur={() => onHover(null)}
              className={`rounded-2xl border p-3 text-left transition duration-200 ${
                full
                  ? 'cursor-not-allowed border-cal/[0.06] opacity-40'
                  : slot.promo
                    ? 'border-ladrillo/40 bg-ladrillo/[0.06] hover:-translate-y-0.5 hover:border-ladrillo/80'
                    : 'border-cal/10 bg-vidrio hover:-translate-y-0.5 hover:border-cal/30'
              }`}
            >
              <span className="display block text-2xl tabular-nums">{slot.time}</span>
              <span
                className={`mt-1 block text-xs font-bold tabular-nums ${slot.promo ? 'text-ladrillo-claro' : 'text-cal'}`}
              >
                {money(slot.perPerson)} <span className="font-normal text-ink-soft">c/u</span>
              </span>
              <span className="mt-2 block text-[0.7rem] text-ink-mute">
                {slot.promo && !full && <span className="font-bold uppercase text-ladrillo-claro">Promo · </span>}
                {full ? 'Completo' : `${free} ${free === 1 ? 'libre' : 'libres'}`}
              </span>
            </button>
          );
        })}
      </div>
      <div className="mt-auto pt-6 text-center">
        {allFull ? (
          <p className="text-xs text-ink-soft">Llenaste la noche.</p>
        ) : (
          <p className="text-xs text-ink-mute">Tocá un horario para reservarlo.</p>
        )}
        {onReset && (
          <button
            type="button"
            onClick={onReset}
            className="eyebrow mt-2 text-ink-soft underline-offset-4 transition hover:text-cal hover:underline"
          >
            Reiniciar la demo
          </button>
        )}
      </div>
    </div>
  );
}

const INPUT =
  'mt-1.5 w-full rounded-xl border border-cal/10 bg-vidrio px-4 py-2.5 text-sm text-cal placeholder:text-ink-mute focus:border-ladrillo/60 focus:outline-none';

function DetailsStep({
  hour,
  court,
  name,
  onName,
  phone,
  onPhone,
  onBack,
  onSubmit,
}: {
  hour: number;
  court: number;
  name: string;
  onName: (name: string) => void;
  phone: string;
  onPhone: (phone: string) => void;
  onBack: () => void;
  onSubmit: (event: FormEvent) => void;
}) {
  const slot = HOURS[hour];
  const rows = [
    { label: 'Cuándo', value: `Hoy ${slot.time} · 90 min` },
    { label: 'Cancha', value: COURTS[court] },
    { label: 'Total', value: money(slot.perPerson * PLAYERS) },
  ];
  return (
    <form onSubmit={onSubmit} className="mk-fade-in flex flex-1 flex-col">
      <button
        type="button"
        onClick={onBack}
        className="eyebrow -ml-1 self-start px-1 text-ink-soft transition hover:text-cal"
      >
        ← Cambiar hora
      </button>
      <p className="display mt-1 text-2xl tracking-[0.06em]">Tus datos</p>
      <dl className="mt-4 divide-y divide-cal/[0.07] rounded-2xl border border-cal/10 bg-vidrio px-4">
        {rows.map((row) => (
          <div key={row.label} className="flex items-center justify-between py-2 text-sm">
            <dt className="text-ink-soft">{row.label}</dt>
            <dd className="font-semibold tabular-nums">{row.value}</dd>
          </div>
        ))}
      </dl>
      <label className="mt-3 block">
        <span className="eyebrow text-ink-mute">Tu nombre</span>
        <input
          value={name}
          onChange={(event) => onName(event.target.value)}
          maxLength={14}
          placeholder="Ej: Lucía"
          className={INPUT}
        />
      </label>
      <label className="mt-3 block">
        <span className="eyebrow text-ink-mute">Teléfono</span>
        <input
          type="tel"
          inputMode="tel"
          autoComplete="off"
          value={phone}
          onChange={(event) => onPhone(event.target.value)}
          maxLength={20}
          placeholder="Ej: 2262 123456"
          className={INPUT}
        />
      </label>
      <div className="mt-auto pt-5">
        <button
          type="submit"
          className="w-full rounded-full bg-ladrillo px-5 py-3.5 text-sm font-bold uppercase tracking-[0.12em] text-cal transition hover:bg-ladrillo/90 active:scale-[0.98] [box-shadow:var(--shadow-glow)]"
        >
          Reservar
        </button>
      </div>
    </form>
  );
}

function DoneStep({ court, hour, onAgain }: { court: number; hour: number; onAgain: () => void }) {
  return (
    <div className="mk-fade-in flex flex-1 flex-col items-center justify-center text-center">
      <svg viewBox="0 0 64 64" className="size-20 text-ladrillo-claro" fill="none" aria-hidden>
        <circle cx="32" cy="32" r="30" stroke="currentColor" strokeOpacity="0.25" strokeWidth="2" />
        <path
          d="M20 33l8 8 16-17"
          stroke="currentColor"
          strokeWidth="3.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          pathLength={1}
          className="mk-check"
        />
      </svg>
      <p className="display mt-5 text-3xl tracking-[0.06em]">Reservado</p>
      <p className="mt-2 text-sm font-semibold tabular-nums">
        {COURTS[court]} · Hoy {HOURS[hour].time}
      </p>
      <p className="mt-3 max-w-[14rem] text-sm text-ink-soft">
        Ya está en la agenda del club. Nadie tuvo que contestar un mensaje.
      </p>
      <p className="mt-2 text-[0.7rem] text-ink-mute">Es una demo: no se reservó nada de verdad.</p>
      <a
        href="#demo-panel"
        className="eyebrow mt-5 text-ladrillo-claro underline-offset-4 hover:underline lg:hidden"
      >
        Ver cómo le llegó al club ↓
      </a>
      <button
        type="button"
        onClick={onAgain}
        className="mt-auto w-full whitespace-nowrap rounded-full border border-cal/15 px-4 py-3.5 text-xs font-bold uppercase tracking-[0.1em] text-cal transition hover:border-cal/40"
      >
        Reservar otro horario
      </button>
    </div>
  );
}

function Panel({
  agenda,
  focusHour,
  pendingCourt,
  pendingHour,
  last,
}: {
  agenda: Agenda;
  focusHour: number | null;
  pendingCourt: number | null;
  pendingHour: number | null;
  last: { court: number; hour: number; name: string } | null;
}) {
  const lastKey = last ? cellKey(last.court, last.hour) : null;
  return (
    <div
      id="demo-panel"
      className="scroll-mt-24 overflow-hidden rounded-[1.75rem] border border-cal/10 bg-vidrio [box-shadow:var(--shadow-card)]"
    >
      <div className="flex items-center gap-2 border-b border-cal/[0.07] px-5 py-3.5">
        <span aria-hidden className="size-2.5 rounded-full bg-cal/10" />
        <span aria-hidden className="size-2.5 rounded-full bg-cal/10" />
        <span aria-hidden className="size-2.5 rounded-full bg-cal/10" />
        <p className="eyebrow ml-3 text-ink-mute">Panel del club · Agenda de hoy</p>
      </div>

      {/* aria-live: quien usa lector de pantalla también se entera de que
          la reserva llegó al panel, que es el punto de toda la demo. */}
      <div aria-live="polite" className="min-h-[4.5rem] px-5 pt-5 sm:px-6">
        {last ? (
          <div
            key={lastKey}
            className="mk-toast flex items-center gap-3 rounded-2xl border border-ladrillo/30 bg-ladrillo/[0.08] px-4 py-3"
          >
            <span className="grid size-8 shrink-0 place-items-center rounded-full bg-ladrillo text-cal">
              <CheckGlyph className="size-3.5" />
            </span>
            <p className="min-w-0 text-sm">
              <span className="font-semibold">Nueva reserva web</span>
              <span className="text-ink-soft">
                {' '}
                · {last.name} · {COURTS[last.court]} · {HOURS[last.hour].time}
              </span>
            </p>
          </div>
        ) : (
          <p className="flex items-center gap-2.5 rounded-2xl border border-dashed border-cal/10 px-4 py-3.5 text-sm text-ink-mute">
            <span className="mk-ping size-1.5 rounded-full bg-ink-mute" />
            Esperando reservas…
          </p>
        )}
      </div>

      <div aria-hidden className="grid grid-cols-[auto_repeat(4,minmax(0,1fr))] gap-1.5 p-5 sm:gap-2 sm:p-6">
        <div />
        {HOURS.map((slot, h) => (
          <div
            key={slot.time}
            className={`eyebrow pb-1 text-center tabular-nums transition-colors ${
              focusHour === h ? 'text-ladrillo-claro' : 'text-ink-mute'
            }`}
          >
            {slot.time}
          </div>
        ))}
        {COURTS.map((court, c) => (
          <PanelRow
            key={court}
            court={court}
            courtIndex={c}
            agenda={agenda}
            focusHour={focusHour}
            pending={pendingHour !== null && pendingCourt === c ? pendingHour : null}
            lastKey={lastKey}
          />
        ))}
      </div>

      <div className="flex flex-wrap gap-x-5 gap-y-2 border-t border-cal/[0.07] px-5 py-3.5 text-[0.7rem] text-ink-mute sm:px-6">
        <Legend className="border border-ladrillo/40 bg-ladrillo/15">Reserva web</Legend>
        <Legend className="bg-cal/[0.07]">Fijo o teléfono</Legend>
        <Legend className="border border-dashed border-cal/15">Libre</Legend>
      </div>
    </div>
  );
}

function PanelRow({
  court,
  courtIndex,
  agenda,
  focusHour,
  pending,
  lastKey,
}: {
  court: string;
  courtIndex: number;
  agenda: Agenda;
  focusHour: number | null;
  pending: number | null;
  lastKey: string | null;
}) {
  return (
    <>
      <div className="eyebrow flex items-center pr-1 text-ink-soft sm:pr-3">
        <span className="sm:hidden">C{courtIndex + 1}</span>
        <span className="hidden sm:inline">{court}</span>
      </div>
      {HOURS.map((slot, h) => {
        const key = cellKey(courtIndex, h);
        const cell = agenda[key];
        const focused = focusHour === h;
        const base = 'grid h-14 place-items-center rounded-lg px-1 text-center transition-colors duration-300 sm:h-16';
        if (pending === h) {
          return (
            <div key={slot.time} className={`${base} border border-dashed border-ladrillo/70 bg-ladrillo/[0.05]`}>
              <span className="mk-breathe text-[0.65rem] font-semibold text-ladrillo-claro">Eligiendo…</span>
            </div>
          );
        }
        if (!cell) {
          return (
            <div
              key={slot.time}
              className={`${base} border border-dashed ${focused ? 'border-cal/35 bg-cal/[0.03]' : 'border-cal/10'}`}
            />
          );
        }
        const web = cell.kind === 'web';
        return (
          <div
            key={slot.time}
            className={`${base} ${
              cell.mine
                ? 'border border-ladrillo/70 bg-ladrillo/25'
                : web
                  ? 'border border-ladrillo/40 bg-ladrillo/15'
                  : 'bg-cal/[0.07]'
            } ${key === lastKey ? 'mk-pop' : ''}`}
          >
            <span
              className={`w-full truncate text-[0.7rem] font-semibold sm:text-xs ${
                web ? 'text-ladrillo-claro' : 'text-arena'
              }`}
            >
              {cell.label}
            </span>
          </div>
        );
      })}
    </>
  );
}

function Legend({ className, children }: { className: string; children: ReactNode }) {
  return (
    <span className="flex items-center gap-2">
      <span aria-hidden className={`size-3 rounded ${className}`} />
      {children}
    </span>
  );
}
