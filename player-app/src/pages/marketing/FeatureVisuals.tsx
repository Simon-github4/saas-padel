import { useEffect, useState } from 'react';
import { money } from '../../format';
import { CheckGlyph, CourtLines, delay, useInView, usePrefersReducedMotion } from './motion';

/**
 * Las ilustraciones de cada tarjeta de "Qué hace por vos". Cada una muestra la
 * función andando en chico, en vez de un ícono que hay que interpretar. Son
 * decorativas para el lector de pantalla: el texto de la tarjeta ya lo dice.
 */

/** Dos jugadores van por el mismo turno: entra uno solo. Se repite al pasar el mouse. */
export function OverbookVisual({ replay }: { replay: number }) {
  const [ref, shown] = useInView<HTMLDivElement>();
  return (
    <div ref={ref} aria-hidden className="flex h-full flex-col justify-center gap-2.5">
      <p className="eyebrow text-ink-mute">Cancha 1 · Sábado 21:00</p>
      {shown && (
        <div key={replay} className="space-y-2.5">
          <div
            className="mk-fade-up flex items-center justify-between gap-3 rounded-xl border border-ladrillo/40 bg-ladrillo/10 px-4 py-3"
            style={delay(0)}
          >
            <span className="flex items-center gap-3 text-sm font-semibold">
              <Avatar letter="J" />
              Juan
            </span>
            <span className="flex items-center gap-1.5 text-xs font-semibold text-ladrillo-claro">
              <CheckGlyph className="size-3.5" />
              Reservado
            </span>
          </div>
          <div className="mk-fade-up" style={delay(450)}>
            <div
              className="mk-shake flex items-center justify-between gap-3 rounded-xl border border-cal/10 bg-pista px-4 py-3"
              style={delay(1000)}
            >
              <span className="flex items-center gap-3 text-sm font-semibold text-ink-soft">
                <Avatar letter="P" muted />
                Pedro
              </span>
              <span className="text-xs text-ink-mute">Ya lo tomaron</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function Avatar({ letter, muted = false }: { letter: string; muted?: boolean }) {
  return (
    <span
      className={`grid size-7 place-items-center rounded-full text-xs font-bold ${
        muted ? 'bg-cal/10 text-ink-soft' : 'bg-ladrillo text-cal'
      }`}
    >
      {letter}
    </span>
  );
}

/**
 * El horario completo con tres anotados en la lista de espera: alguien cancela
 * y el aviso sale solo. Se juega al entrar en pantalla.
 */
const WAITING = ['Nacho', 'Caro', 'Seba'];

export function WaitlistVisual() {
  const reduced = usePrefersReducedMotion();
  const [ref, shown] = useInView<HTMLDivElement>();
  const [freed, setFreed] = useState(false);

  useEffect(() => {
    if (!shown || reduced) {
      return;
    }
    const id = window.setTimeout(() => setFreed(true), 1600);
    return () => window.clearTimeout(id);
  }, [shown, reduced]);

  return (
    <div ref={ref} aria-hidden className="flex h-full flex-col justify-center gap-3">
      <div className="flex items-center justify-between gap-3">
        <p className="eyebrow text-ink-mute">Viernes 20:00</p>
        <span
          className={`eyebrow rounded-full px-2 py-0.5 transition-colors duration-500 ${
            freed ? 'bg-ladrillo/15 text-ladrillo-claro' : 'bg-cal/10 text-ink-soft'
          }`}
        >
          {freed ? 'Se liberó' : 'Completo'}
        </span>
      </div>

      <ul className="space-y-2">
        {shown &&
          WAITING.map((name, index) => (
            <li
              key={name}
              className="mk-fade-up flex items-center justify-between gap-3 rounded-xl border border-cal/10 bg-pista px-4 py-2.5"
              style={delay(index * 160)}
            >
              <span className="flex items-center gap-3 text-sm font-semibold">
                <Avatar letter={name[0]} muted={!freed} />
                {name}
              </span>
              <span
                className={`text-xs transition-colors duration-500 ${
                  freed ? 'text-ladrillo-claro' : 'text-ink-mute'
                }`}
              >
                {freed ? 'Avisado' : `En espera`}
              </span>
            </li>
          ))}
      </ul>
    </div>
  );
}

const HOLD_MINUTES = 10;
/** En décimas de segundo: 5 s descontando y 2 s mostrando la cancha libre. */
const EXPIRE_COUNTDOWN = 50;
const EXPIRE_CYCLE = 70;

/**
 * El turno sin confirmar se descuenta y vuelve a quedar libre. En loop
 * mientras está en pantalla; con movimiento reducido queda quieto a mitad.
 */
export function ExpireVisual() {
  const reduced = usePrefersReducedMotion();
  const [ref, inView] = useInView<HTMLDivElement>({ once: false, rootMargin: '0px' });
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    if (reduced || !inView) {
      return;
    }
    const id = window.setInterval(() => setElapsed((value) => (value + 1) % EXPIRE_CYCLE), 100);
    return () => window.clearInterval(id);
  }, [inView, reduced]);

  const progress = reduced ? 0.6 : Math.min(elapsed / EXPIRE_COUNTDOWN, 1);
  const released = progress >= 1;
  const minutesLeft = Math.ceil(HOLD_MINUTES * (1 - progress));
  const circumference = 2 * Math.PI * 26;

  return (
    <div ref={ref} aria-hidden className="flex h-full items-center gap-5">
      <div className="relative size-24 shrink-0">
        <svg viewBox="0 0 64 64" className="size-full -rotate-90">
          <circle cx="32" cy="32" r="26" fill="none" strokeWidth="5" className="stroke-cal/[0.08]" />
          <circle
            cx="32"
            cy="32"
            r="26"
            fill="none"
            strokeWidth="5"
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={circumference * progress}
            className={`transition-[stroke] duration-300 ${released ? 'stroke-transparent' : 'stroke-ladrillo'}`}
          />
        </svg>
        <div className="absolute inset-0 grid place-items-center text-center">
          {released ? (
            <CheckGlyph className="mk-fade-in size-7 text-ladrillo-claro" />
          ) : (
            <p className="display text-2xl tabular-nums leading-none">
              {minutesLeft}
              <span className="block text-[0.6rem] tracking-[0.12em] text-ink-mute">min</span>
            </p>
          )}
        </div>
      </div>
      <div className="min-w-0">
        <p className="text-sm font-semibold">Cancha 3 · 20:00</p>
        <p
          key={String(released)}
          className={`mk-fade-in mt-1 text-xs ${released ? 'text-ladrillo-claro' : 'text-ink-soft'}`}
        >
          {released ? 'Libre de nuevo para reservar' : 'Seña sin pagar, se libera sola'}
        </p>
      </div>
    </div>
  );
}

const WEEKDAYS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

/** Un mes con todos los martes tomados por el mismo turno fijo, que se van cargando solos. */
export function FixedVisual() {
  const [ref, shown] = useInView<HTMLDivElement>();
  return (
    <div ref={ref} aria-hidden data-shown={shown || undefined} className="flex h-full flex-col justify-center">
      <div className="grid w-full max-w-[17rem] grid-cols-7 gap-1.5 text-center">
        {WEEKDAYS.map((day, index) => (
          <span key={index} className={`eyebrow ${index === 1 ? 'text-ladrillo-claro' : 'text-ink-mute'}`}>
            {day}
          </span>
        ))}
        {Array.from({ length: 28 }, (_, cell) => {
          const tuesday = cell % 7 === 1;
          const week = Math.floor(cell / 7);
          return (
            <span key={cell} className="relative aspect-square rounded-md bg-cal/[0.04]">
              {tuesday && (
                <span
                  className="mk-grow-y absolute inset-0 rounded-md bg-ladrillo"
                  style={delay(300 + week * 220)}
                />
              )}
            </span>
          );
        })}
      </div>
      <p className="mt-3 text-xs text-ink-soft">Martes 21:00 · los de siempre</p>
    </div>
  );
}

const BANDS = [
  { label: 'Mañana', price: 5000, promo: false },
  { label: 'Siesta', price: 4000, promo: true },
  { label: 'Tarde', price: 6000, promo: false },
  { label: 'Noche', price: 7000, promo: false },
];

/** Tarifa por franja horaria, con la promo que llena el hueco de la siesta. */
export function PricesVisual() {
  const [ref, shown] = useInView<HTMLDivElement>();
  const max = Math.max(...BANDS.map((band) => band.price));
  return (
    <div ref={ref} aria-hidden data-shown={shown || undefined} className="flex h-full items-end gap-3 sm:gap-4">
      {BANDS.map((band, index) => (
        <div key={band.label} className="group flex h-full flex-1 flex-col items-center justify-end">
          <span
            className={`text-[0.7rem] font-bold tabular-nums transition-colors sm:text-xs ${
              band.promo ? 'text-ladrillo-claro' : 'text-ink-soft group-hover:text-cal'
            }`}
          >
            {money(band.price)}
          </span>
          <div className="mt-2 flex w-full flex-1 items-end">
            <div
              className={`mk-grow-y w-full rounded-t-lg ${
                band.promo
                  ? 'bg-ladrillo'
                  : 'bg-cal/[0.1] transition-colors duration-300 group-hover:bg-cal/25'
              }`}
              style={{ height: `${(band.price / max) * 100}%`, ...delay(index * 120) }}
            />
          </div>
          <span className="eyebrow mt-2 text-ink-mute">{band.label}</span>
        </div>
      ))}
    </div>
  );
}

const SWATCHES = [
  { name: 'ladrillo', value: '#ea580c' },
  { name: 'azul', value: '#2563eb' },
  { name: 'verde', value: '#16a34a' },
  { name: 'fucsia', value: '#db2777' },
];

/** Portada del club con el color y el tema que elija quien mira. Interactivo. */
export function BrandVisual() {
  const [color, setColor] = useState(SWATCHES[1].value);
  const [light, setLight] = useState(false);

  return (
    <div className="flex h-full flex-col gap-4 sm:flex-row sm:items-center sm:gap-6">
      <div
        aria-hidden
        data-shown
        className={`w-full overflow-hidden rounded-2xl border transition-colors duration-500 sm:w-60 sm:shrink-0 ${
          light ? 'border-black/10 bg-[#f6f3ee] text-[#17140f]' : 'border-cal/10 bg-pista text-cal'
        }`}
      >
        <div
          className="relative h-20 overflow-hidden transition-colors duration-500"
          style={{ backgroundColor: color }}
        >
          <div className="absolute inset-0 bg-gradient-to-br from-black/10 to-black/45" />
          <CourtLines className="absolute -left-4 top-1 w-[130%] rotate-[-8deg] text-white/35" />
        </div>
        <div className="px-4 pb-4 pt-3">
          <p className="display text-lg tracking-[0.12em]">Tu club</p>
          <p className={`text-[0.7rem] ${light ? 'text-[#57524a]' : 'text-ink-soft'}`}>Pádel · 3 canchas</p>
          <span
            className="mt-3 block rounded-full py-2 text-center text-[0.65rem] font-bold uppercase tracking-[0.12em] text-white transition-colors duration-500"
            style={{ backgroundColor: color }}
          >
            Reservar
          </span>
        </div>
      </div>

      <div className="space-y-3">
        <div className="flex gap-2.5">
          {SWATCHES.map((swatch) => (
            <button
              key={swatch.value}
              type="button"
              aria-label={`Color ${swatch.name}`}
              aria-pressed={color === swatch.value}
              onClick={() => setColor(swatch.value)}
              className={`size-9 rounded-full border-2 transition duration-300 hover:scale-110 ${
                color === swatch.value ? 'scale-110 border-cal' : 'border-transparent'
              }`}
              style={{ backgroundColor: swatch.value }}
            />
          ))}
        </div>
        <div role="group" aria-label="Tema" className="inline-grid grid-cols-2 rounded-full border border-cal/10 bg-pista p-1">
          {[
            { value: false, label: 'Oscuro' },
            { value: true, label: 'Claro' },
          ].map((option) => (
            <button
              key={option.label}
              type="button"
              aria-pressed={light === option.value}
              onClick={() => setLight(option.value)}
              className={`rounded-full px-4 text-xs font-bold uppercase tracking-[0.1em] transition duration-300 ${
                light === option.value ? 'bg-cal text-pista' : 'text-ink-soft hover:text-cal'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
