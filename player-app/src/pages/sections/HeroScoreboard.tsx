import type { CSSProperties } from 'react';
import type { Slot } from '../../api/client';
import { clockTime, longDate, money, todayIso } from '../../format';
import { DEFAULT_CTA, HERO_SCREEN } from './HeroClassic';

/**
 * Grilla de líneas de cancha: dos degradados repetidos, uno por eje, en vez de
 * una imagen de fondo — así escala sin pixelarse y no suma un asset más.
 */
const COURT_GRID = {
  backgroundImage:
    'repeating-linear-gradient(0deg, rgba(255,255,255,.05) 0px, rgba(255,255,255,.05) 1px, transparent 1px, transparent 34px), ' +
    'repeating-linear-gradient(90deg, rgba(255,255,255,.05) 0px, rgba(255,255,255,.05) 1px, transparent 1px, transparent 34px)',
};

/** Puntitos de panel de LED, detrás de las cifras del tablero. */
const LED_DOTS = {
  backgroundImage: 'radial-gradient(rgba(255,255,255,.07) 1px, transparent 1px)',
  backgroundSize: '6px 6px',
};

/** Resplandor de LED en el color del club para las cifras. */
const LED_GLOW = {
  textShadow: '0 0 18px color-mix(in srgb, var(--color-ladrillo) 65%, transparent)',
};

/** Cuántos horarios de hoy entran en el tablero antes de mandar a la grilla. */
const QUICK_SLOTS = 4;

function delay(ms: number): CSSProperties {
  return { '--portada-delay': `${ms}ms` } as CSSProperties;
}

/**
 * Portada "marcador de cancha": foto del club en un badge circular tipo
 * pelota, sobre un fondo casi negro fijo — a diferencia de HeroClassic, este
 * diseño no sigue la paleta clara/oscura del club porque su identidad (grilla
 * de cancha, resplandor naranja) depende de ese fondo oscuro. El acento sí
 * respeta el color de marca del club: usa --color-ladrillo, que el club puede
 * pisar con su primaryColor.
 *
 * <p>Debajo de la pelota va un tablero de verdad, como el de un club: cuántas
 * canchas hay, cuántos horarios quedan libres hoy y cuál es el próximo, con
 * las cifras en LED. Abajo del tablero, los primeros horarios del día entran
 * dando vuelta como un cartel de paletas; tocar uno lleva directo a los datos
 * del jugador. Al cargar, la pelota cae y pica una sola vez; después solo
 * late el punto de la fecha.
 */
export function HeroScoreboard({
  name,
  tagline,
  heroImageUrl,
  heroHeadline,
  heroCtaLabel,
  address,
  courtCount,
  todaySlots,
  timeZone,
  playersPerCourt,
  onPickSlot,
  onSeeToday,
}: {
  name: string;
  tagline: string | null;
  heroImageUrl: string | null;
  heroHeadline: string | null;
  heroCtaLabel: string | null;
  address: string | null;
  courtCount: number;
  /** Horarios con cancha libre de hoy; null si la página no está en hoy. */
  todaySlots?: Slot[] | null;
  timeZone?: string;
  playersPerCourt?: number;
  onPickSlot?: (slot: Slot) => void;
  onSeeToday?: () => void;
}) {
  const headline = heroHeadline ?? name;
  const cta = heroCtaLabel ?? DEFAULT_CTA;
  const live = todaySlots && timeZone && playersPerCourt && onPickSlot ? todaySlots : null;
  const next = live && live.length > 0 ? clockTime(live[0].startsAt, timeZone!) : null;

  const stats: { label: string; value: string }[] = [];
  if (courtCount > 0) {
    stats.push({ label: courtCount === 1 ? 'Cancha' : 'Canchas', value: String(courtCount) });
  }
  if (live) {
    stats.push({ label: 'Libres hoy', value: String(live.length) });
    stats.push({ label: 'Próximo', value: next ?? '—' });
  }

  return (
    <section className={`relative mx-[calc(50%-50vw)] flex flex-col justify-center overflow-hidden bg-[#0a0a0a] py-[clamp(1rem,4svh,3rem)] text-white ${HERO_SCREEN}`}>
      <div className="absolute inset-0" style={COURT_GRID} aria-hidden />
      <div
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(60%_50%_at_50%_40%,var(--color-ladrillo),transparent_70%)] opacity-20"
        aria-hidden
      />

      <div className="relative mx-auto w-full max-w-lg px-5 text-center md:max-w-2xl">
        {/* La pelota va al costado del título y no apilada entre dos líneas,
            como antes: apilada, la portada medía más que la pantalla en
            cualquier teléfono y el botón quedaba bajo el pliegue. */}
        <div className="flex items-center gap-4 text-left md:gap-8">
          <div className="order-2 min-w-0 flex-1">
            <p
              className="portada-sube eyebrow flex items-center gap-2 text-ladrillo-claro"
              style={delay(0)}
            >
              <span className="relative flex size-1.5" aria-hidden>
                <span className="absolute inset-0 animate-ping rounded-full bg-ladrillo-claro opacity-70" />
                <span className="relative size-1.5 rounded-full bg-ladrillo-claro" />
              </span>
              {longDate(todayIso())}
            </p>

            <h1
              className="portada-sube hero-title mt-[clamp(0.5rem,1.5svh,1rem)] text-[clamp(1.75rem,8vw,3.25rem)] leading-[0.92] tracking-tight"
              style={delay(100)}
            >
              {headline}
            </h1>
            {tagline && (
              <p className="portada-sube eyebrow mt-3 text-white/60" style={delay(180)}>
                {tagline}
              </p>
            )}
          </div>

          <div className="order-1 shrink-0">
            <Ball imageUrl={heroImageUrl} />
          </div>
        </div>

        {stats.length > 0 && (
          <div
            className="portada-sube mt-[clamp(1rem,3.5svh,2rem)] overflow-hidden rounded-2xl border border-white/10 bg-black/60 text-left shadow-[inset_0_1px_0_rgba(255,255,255,0.06),0_20px_50px_rgba(0,0,0,0.5)] backdrop-blur"
            style={delay(700)}
          >
            <dl
              className="grid divide-x divide-white/10"
              style={{ ...LED_DOTS, gridTemplateColumns: `repeat(${stats.length}, minmax(0, 1fr))` }}
            >
              {stats.map((stat) => (
                <div key={stat.label} className="px-3 py-[clamp(0.5rem,1.6svh,1rem)] text-center">
                  <dt className="eyebrow text-white/50">{stat.label}</dt>
                  <dd
                    className="display mt-1 text-[length:clamp(1.75rem,5svh,3rem)] leading-none tabular-nums text-ladrillo-claro"
                    style={LED_GLOW}
                  >
                    {stat.value}
                  </dd>
                </div>
              ))}
            </dl>

            {live && (
              <BoardSlots
                slots={live}
                timeZone={timeZone!}
                playersPerCourt={playersPerCourt!}
                onPick={onPickSlot!}
                onSeeToday={onSeeToday}
              />
            )}
          </div>
        )}

        {address && (
          <p
            className="portada-sube mt-[clamp(0.5rem,2svh,1.25rem)] flex items-center justify-center gap-1.5 text-sm font-semibold text-white/80"
            style={delay(900)}
          >
            <PinGlyph />
            {address}
          </p>
        )}

        <div className="portada-sube" style={delay(1000)}>
          <a
            href="#reserva"
            className="group mt-[clamp(0.75rem,3svh,1.75rem)] inline-flex w-full items-center justify-center gap-2 rounded-full bg-ladrillo px-10 py-4 text-sm font-bold uppercase tracking-[0.12em] text-white transition duration-300 hover:-translate-y-0.5 hover:bg-ladrillo/90 [box-shadow:var(--shadow-glow)]"
          >
            {cta}
            <span aria-hidden className="transition-transform duration-300 group-hover:translate-x-1">
              →
            </span>
          </a>
          <p className="eyebrow mt-[clamp(0.5rem,2svh,1.25rem)] text-white/40 muy-bajo:hidden">Sin registro · Confirmación al instante</p>
        </div>
      </div>
    </section>
  );
}

/**
 * La foto del club en la pelota, con las dos costuras dibujadas encima. Cae y
 * pica una vez al cargar.
 */
function Ball({ imageUrl }: { imageUrl: string | null }) {
  return (
    <div className="portada-pica relative size-[clamp(4.5rem,14svh,10rem)] shrink-0" aria-hidden>
      <div className="size-full overflow-hidden rounded-full border-[3px] border-ladrillo shadow-[0_0_0_7px_color-mix(in_srgb,var(--color-ladrillo)_15%,transparent),0_18px_40px_rgba(0,0,0,0.6)]">
        {imageUrl ? (
          <img src={imageUrl} alt="" className="h-full w-full object-cover" loading="eager" />
        ) : (
          <div className="h-full w-full bg-[radial-gradient(75%_75%_at_50%_50%,var(--color-ladrillo),transparent_70%)] opacity-40" />
        )}
      </div>
      {/* Costuras de pelota: dos curvas enfrentadas, apenas marcadas para no
          tapar la foto. */}
      <svg viewBox="0 0 100 100" className="pointer-events-none absolute inset-0 size-full" fill="none">
        <path d="M20 12 C42 34 42 66 20 88" stroke="white" strokeOpacity="0.55" strokeWidth="2" strokeLinecap="round" />
        <path d="M80 12 C58 34 58 66 80 88" stroke="white" strokeOpacity="0.55" strokeWidth="2" strokeLinecap="round" />
      </svg>
    </div>
  );
}

/**
 * Renglón de horarios del tablero. Cada ficha entra dando vuelta, escalonada,
 * como las paletas de un cartel de salidas.
 */
function BoardSlots({
  slots,
  timeZone,
  playersPerCourt,
  onPick,
  onSeeToday,
}: {
  slots: Slot[];
  timeZone: string;
  playersPerCourt: number;
  onPick: (slot: Slot) => void;
  onSeeToday?: () => void;
}) {
  if (slots.length === 0) {
    return (
      <div className="border-t border-white/10 px-4 py-4 text-center">
        <p className="text-sm text-white/70">Hoy no quedan horarios libres.</p>
        <a
          href="#reserva"
          className="mt-1 inline-block text-sm font-semibold text-ladrillo-claro underline-offset-4 hover:underline"
        >
          Mirá otros días
        </a>
      </div>
    );
  }

  const shown = slots.slice(0, QUICK_SLOTS);

  return (
    <div className="border-t border-white/10 p-3">
      <ul className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {shown.map((slot, index) => {
          const cheapest = Math.min(...slot.available.map((court) => court.price));
          const time = clockTime(slot.startsAt, timeZone);
          return (
            <li key={slot.startsAt} className="portada-flip max-sm:bajo:nth-[n+3]:hidden" style={delay(850 + index * 90)}>
              <button
                type="button"
                onClick={() => onPick(slot)}
                aria-label={`Reservar hoy a las ${time}, ${money(cheapest / playersPerCourt)} por persona${slot.promo ? ', en promo' : ''}`}
                className="group relative flex w-full flex-col items-center rounded-xl border border-white/10 bg-white/[0.04] px-2 py-[clamp(0.375rem,1.2svh,0.625rem)] transition duration-300 hover:-translate-y-0.5 hover:border-ladrillo hover:bg-ladrillo"
              >
                {/* La raya del medio de una paleta de cartel. */}
                <span aria-hidden className="absolute inset-x-0 top-1/2 h-px bg-black/40" />
                <span className="display relative text-2xl leading-none tabular-nums">{time}</span>
                <span className="relative mt-1 text-[0.7rem] font-semibold tabular-nums text-white/60 transition-colors group-hover:text-white">
                  {money(cheapest / playersPerCourt)} c/u
                  {slot.promo && <span className="ml-1 uppercase text-ladrillo-claro group-hover:text-white">· promo</span>}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
      {/* Sin la cuenta de cuántos faltan: en un teléfono bajo se ven dos
          fichas y en el resto cuatro, y el número mentía en uno de los dos. */}
      {slots.length > 2 && onSeeToday && (
        <button
          type="button"
          onClick={onSeeToday}
          className={`-mb-2 mt-0.5 w-full text-xs font-semibold uppercase tracking-[0.12em] text-white/60 transition hover:text-white ${
            slots.length > QUICK_SLOTS ? '' : 'hidden max-sm:bajo:block'
          }`}
        >
          Ver todos los horarios →
        </button>
      )}
    </div>
  );
}

function PinGlyph() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      className="size-4 shrink-0 text-ladrillo-claro"
    >
      <path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 1 1 13 0c0 5.4-6.5 11-6.5 11Z" />
      <circle cx="12" cy="10" r="2.3" />
    </svg>
  );
}
