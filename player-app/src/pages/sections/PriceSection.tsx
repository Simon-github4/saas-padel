import { useEffect, useRef, useState, type ReactNode } from 'react';
import type { Slot } from '../../api/client';
import { clockTime, money } from '../../format';
import { Badge } from '../../components/Ui';

type Mode = 'persona' | 'turno';

/**
 * Precio del día, con la cancha dibujada al lado.
 *
 * <p>La pregunta del jugador casi siempre es "¿cuánto pongo yo?", pero el club
 * cobra el turno entero. Antes los dos números convivían en un plato claro, uno
 * gigante y el otro en una fila más; ahora un selector alterna entre "Por
 * persona" y "El turno", el número cuenta hasta el nuevo valor y la cancha
 * muestra de dónde sale: en "Por persona" se enciende un jugador, en "El turno"
 * los cuatro, cada uno con lo que pone. Tocar un jugador vuelve a "Por
 * persona" desde ese lugar.
 *
 * <p>La cancha va pintada con el color del club, así que es el acento de la
 * pieza en los dos temas. Sus líneas son blancas fijas, como las de una cancha
 * de verdad, con un velo oscuro debajo para que se lean aunque el club elija
 * un color claro. Todo lo demás sale de los tokens del tema: un gris de
 * Tailwind queda ilegible cuando la página se da vuelta a claro.
 */
export function PriceSection({
  slots,
  playersPerCourt,
  timeZone,
  slotDurationMinutes,
}: {
  slots: Slot[];
  playersPerCourt: number;
  timeZone: string;
  slotDurationMinutes: number;
}) {
  const [mode, setMode] = useState<Mode>('persona');
  const [you, setYou] = useState(0);

  const withAvailability = slots.filter((slot) => slot.available.length > 0);

  const cheapestOf = (slots: Slot[]) =>
    Math.min(...slots.flatMap((slot) => slot.available.map((c) => c.price)));

  // El numero grande es el precio de un turno comun: si mostrara el de promo,
  // repetiria el que ya esta abajo y el descuento dejaria de leerse.
  const regular = withAvailability.filter((slot) => !slot.promo);
  const price = withAvailability.length > 0 ? cheapestOf(regular.length > 0 ? regular : withAvailability) : 0;
  const share = price / playersPerCourt;

  const promos = withAvailability.filter((slot) => slot.promo);
  const promoPrice = promos.length > 0 ? cheapestOf(promos) : null;

  const counted = useCountUp(mode === 'persona' ? share : price);

  if (withAvailability.length === 0) {
    return null;
  }

  function pickPlayer(index: number) {
    setYou(index);
    setMode('persona');
  }

  return (
    // Sin margen propio: la separacion la da el padding de la banda que la
    // envuelve en ClubPage.
    <section>
      <div className="overflow-hidden rounded-3xl border border-cal/10 bg-vidrio [box-shadow:var(--shadow-card)]">
        <div className="grid md:grid-cols-[1fr_1.15fr] md:items-center">
          <div className="p-6 md:p-8">
            <ModeSwitch mode={mode} onChange={setMode} />

            {/* aria-live: al cambiar de modo, el lector anuncia el precio
                nuevo ya asentado, no cada paso de la cuenta. */}
            <p className="mt-6 flex items-baseline gap-2" aria-live="polite">
              <span className="sr-only">{money(mode === 'persona' ? share : price)}</span>
              <span aria-hidden className="display text-6xl leading-none tabular-nums text-cal md:text-7xl">
                {money(counted)}
              </span>
              <span className="text-sm font-semibold text-ink-soft">{mode === 'persona' ? 'c/u' : 'total'}</span>
            </p>
            <p className="mt-3 text-sm text-ink-soft tabular-nums">
              {mode === 'persona' ? (
                <>
                  El turno sale <span className="font-semibold text-cal">{money(price)}</span>, entre{' '}
                  {playersPerCourt}.
                </>
              ) : (
                <>
                  {playersPerCourt} jugadores × <span className="font-semibold text-cal">{money(share)}</span> c/u.
                </>
              )}
            </p>
          </div>

          <Court
            players={playersPerCourt}
            mode={mode}
            you={you}
            share={share}
            onPick={pickPlayer}
          />
        </div>

        <dl className="grid grid-cols-2 border-t border-cal/10">
          <Spec label="Duración" value={`${slotDurationMinutes} min`} icon={<ClockGlyph />} />
          <Spec label="Jugadores" value={String(playersPerCourt)} icon={<PlayersGlyph />} bordered />
        </dl>

        {/* La promo sigue al selector: si el jugador está mirando el turno
            entero, compararlo contra un precio por persona no le sirve. */}
        {promoPrice != null && promoPrice < price && (
          <div className="border-t border-cal/10 bg-ladrillo/[0.06] px-6 py-5 md:px-8">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
              <Badge tone="promo">Promo</Badge>
              <span className="display text-3xl leading-none tabular-nums text-cal">
                {money(mode === 'persona' ? promoPrice / playersPerCourt : promoPrice)}
              </span>
              <span className="text-sm text-ink-soft">{mode === 'persona' ? 'c/u' : 'el turno'}</span>
            </div>
            {/* Los horarios van en su propio renglon y no al final del anterior:
                un dia con muchas franjas los hacia envolver y quedaban colgados
                contra el margen derecho, con el borde izquierdo dentado. */}
            <ul className="mt-3 flex flex-wrap gap-1.5">
              {promos.map((slot) => (
                <li
                  key={slot.startsAt}
                  className="rounded-full border border-ladrillo/30 px-2.5 py-1 text-xs font-semibold tabular-nums text-ladrillo-claro"
                >
                  {clockTime(slot.startsAt, timeZone)} hs
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </section>
  );
}

/** "Por persona" / "El turno", con la pastilla que se desliza al elegido. */
function ModeSwitch({ mode, onChange }: { mode: Mode; onChange: (mode: Mode) => void }) {
  const options: { value: Mode; label: string }[] = [
    { value: 'persona', label: 'Por persona' },
    { value: 'turno', label: 'El turno' },
  ];
  return (
    <div role="group" aria-label="Ver el precio" className="relative inline-grid grid-cols-[1fr_1fr] rounded-full bg-cal/[0.06] p-1">
      <span
        aria-hidden
        className={`absolute inset-y-1 left-1 w-[calc(50%-0.25rem)] rounded-full bg-cal transition-transform duration-300 ease-out ${
          mode === 'turno' ? 'translate-x-full' : 'translate-x-0'
        }`}
      />
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={mode === option.value}
          onClick={() => onChange(option.value)}
          className={`relative whitespace-nowrap rounded-full px-3.5 text-xs font-bold uppercase tracking-[0.08em] transition-colors duration-300 ${
            mode === option.value ? 'text-pista' : 'text-ink-soft hover:text-cal'
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

/**
 * Cancha vista desde arriba (20 × 10 m): paredes, red, líneas de saque a
 * 3,05 m del fondo y la central entre ellas. Los jugadores se reparten mitad
 * y mitad a cada lado de la red.
 *
 * <p>Es un espejo del selector, que es el control accesible: para el lector
 * de pantalla la cancha no agrega nada que el texto no diga.
 */
function Court({
  players,
  mode,
  you,
  share,
  onPick,
}: {
  players: number;
  mode: Mode;
  you: number;
  share: number;
  onPick: (index: number) => void;
}) {
  const left = Math.ceil(players / 2);
  const right = players - left;
  const spots = Array.from({ length: players }, (_, index) => {
    const onLeft = index % 2 === 0;
    const row = Math.floor(index / 2);
    const inSide = onLeft ? left : right;
    return { x: onLeft ? 26 : 74, y: ((row + 1) / (inSide + 1)) * 100 };
  });

  return (
    <div aria-hidden className="px-4 pb-4 md:py-4 md:pl-0 md:pr-4">
      <div className="relative aspect-[2/1] overflow-hidden rounded-2xl bg-ladrillo">
        <div className="absolute inset-0 bg-gradient-to-br from-white/10 via-black/10 to-black/35" />
        <svg viewBox="0 0 200 100" preserveAspectRatio="none" className="absolute inset-3 h-[calc(100%-1.5rem)] w-[calc(100%-1.5rem)]">
          <g fill="none" stroke="white" strokeOpacity="0.75">
            <rect x="0" y="0" width="200" height="100" strokeWidth="2" vectorEffect="non-scaling-stroke" />
            <line x1="30.5" y1="0" x2="30.5" y2="100" strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
            <line x1="169.5" y1="0" x2="169.5" y2="100" strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
            <line x1="30.5" y1="50" x2="169.5" y2="50" strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
            <line x1="100" y1="-4" x2="100" y2="104" strokeWidth="3" strokeOpacity="0.95" vectorEffect="non-scaling-stroke" />
          </g>
        </svg>

        {spots.map((spot, index) => {
          const lit = mode === 'turno' || index === you;
          // La etiqueta sale hacia afuera de la cancha: la fila de arriba la
          // lleva encima y la de abajo debajo, si no tapa al compañero.
          const above = spot.y < 50;
          return (
            <span
              key={index}
              onClick={() => onPick(index)}
              onMouseEnter={() => mode === 'persona' && onPick(index)}
              className="group absolute -translate-x-1/2 -translate-y-1/2 cursor-pointer p-2"
              style={{ left: `${spot.x}%`, top: `${spot.y}%` }}
            >
              <span
                className={`relative block size-6 rounded-full border-2 border-white transition duration-300 ease-out md:size-7 ${
                  lit ? 'scale-110 bg-white shadow-[0_0_0_6px_rgba(255,255,255,0.18)]' : 'scale-90 bg-white/15 group-hover:bg-white/40'
                }`}
                style={{ transitionDelay: mode === 'turno' ? `${index * 70}ms` : '0ms' }}
              >
                {mode === 'persona' && index === you && (
                  <span
                    className="absolute inset-0 animate-ping rounded-full bg-white/60"
                    style={{ animationIterationCount: 1 }}
                  />
                )}
              </span>
              <span
                className={`absolute left-1/2 -translate-x-1/2 whitespace-nowrap rounded-full bg-black/55 px-2 py-0.5 text-[0.65rem] font-bold tabular-nums text-white backdrop-blur-sm transition duration-300 ${
                  above ? 'bottom-full' : 'top-full'
                } ${lit ? 'translate-y-0 opacity-100' : `${above ? 'translate-y-1' : '-translate-y-1'} opacity-0`}`}
                style={{ transitionDelay: mode === 'turno' ? `${index * 70 + 80}ms` : '0ms' }}
              >
                {mode === 'persona' ? `Vos · ${money(share)}` : money(share)}
              </span>
            </span>
          );
        })}
      </div>
    </div>
  );
}

/** Dato fijo del turno, con su ícono: la etiqueta arriba y el valor abajo. */
function Spec({
  label,
  value,
  icon,
  bordered = false,
}: {
  label: string;
  value: string;
  icon: ReactNode;
  bordered?: boolean;
}) {
  return (
    <div className={`flex items-center gap-3 px-6 py-4 md:px-8 ${bordered ? 'border-l border-cal/10' : ''}`}>
      <span className="grid size-9 shrink-0 place-items-center rounded-full bg-cal/[0.06] text-ink-soft">{icon}</span>
      <div>
        <dt className="eyebrow text-ink-mute">{label}</dt>
        <dd className="mt-0.5 font-semibold tabular-nums">{value}</dd>
      </div>
    </div>
  );
}

/**
 * El número que se ve, contando hasta el valor nuevo cuando cambia. Con
 * movimiento reducido salta directo.
 */
function useCountUp(target: number, duration = 450): number {
  const [value, setValue] = useState(target);
  const current = useRef(target);

  useEffect(() => {
    const from = current.current;
    if (from === target || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      current.current = target;
      setValue(target);
      return;
    }
    const started = performance.now();
    let frame = 0;
    const step = (now: number) => {
      const progress = Math.min((now - started) / duration, 1);
      const eased = 1 - (1 - progress) ** 3;
      current.current = progress < 1 ? from + (target - from) * eased : target;
      // Mientras cuenta redondea a cientos: los pesos sueltos bailando se leen
      // como ruido, no como un número que sube.
      setValue(progress < 1 ? Math.round(current.current / 100) * 100 : target);
      if (progress < 1) {
        frame = requestAnimationFrame(step);
      }
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [target, duration]);

  return value;
}

function ClockGlyph() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="size-4">
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </svg>
  );
}

function PlayersGlyph() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="size-4">
      <circle cx="9" cy="8.5" r="3" />
      <path d="M3.5 19c.6-3 2.8-4.8 5.5-4.8s4.9 1.8 5.5 4.8" />
      <circle cx="17" cy="9.5" r="2.3" />
      <path d="M16.5 14.4c2.2.2 3.7 1.8 4.1 4.1" />
    </svg>
  );
}
