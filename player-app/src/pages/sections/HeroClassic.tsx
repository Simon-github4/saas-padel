import type { CSSProperties } from 'react';
import type { Slot } from '../../api/client';
import { clockTime, money } from '../../format';

/** Texto del botón cuando el club no cargó uno propio. Compartido por los tres diseños de portada. */
export const DEFAULT_CTA = 'Ver horarios';

/** Oscurecido de la foto cuando el club no lo definio. Igual al del panel. */
const DEFAULT_OVERLAY = 55;

/** Cuántos horarios de hoy entran en la portada antes de mandar a la grilla. */
const QUICK_SLOTS = 4;

/**
 * Alto de las tres portadas: justo la pantalla menos la barra de arriba (h-14,
 * md:h-16, y su borde de 1px). Con svh y no vh: en el teléfono, vh cuenta la
 * barra de direcciones y la portada quedaba cortada por abajo.
 *
 * <p>Antes medían un 88% de la pantalla como mínimo y crecían con el
 * contenido, así que en un teléfono chico o una notebook el botón quedaba bajo
 * el pliegue. Ahora el contenido se acomoda al alto (espacios y cuerpos en svh,
 * variantes bajo: y muy-bajo:) y min-h-fit queda solo de red: en un teléfono
 * acostado, donde de verdad no entra, la portada crece en vez de recortar.
 */
export const HERO_SCREEN = 'h-[calc(100svh-3.5rem-1px)] min-h-fit md:h-[calc(100svh-4rem-1px)]';

/**
 * Cuerpo del título según su largo.
 *
 * <p>Un cuerpo fijo funciona con "Pádel Necochea" y se rompe con "Club Atlético
 * Deportivo Necochea Padel Center": a 126px ese nombre ocupaba cuatro líneas,
 * estiraba la portada al 103% de la pantalla y dejaba el botón abajo del
 * pliegue. Los nombres largos entran con menos cuerpo; los cortos conservan el
 * tamaño grande, que es el que le da fuerza a la portada.
 *
 * <p>Cada cuerpo tiene además un tope por alto de pantalla: en una notebook el
 * título a 126px se comía la mitad de la portada.
 */
export function titleSize(headline: string): string {
  if (headline.length > 34) {
    return 'text-[length:min(clamp(2rem,8vw,3.5rem),6svh)]';
  }
  if (headline.length > 20) {
    return 'text-[length:min(clamp(2.5rem,11vw,5rem),8svh)]';
  }
  return 'text-[length:min(clamp(3rem,16vw,7rem),11svh)]';
}

function delay(ms: number): CSSProperties {
  return { '--portada-delay': `${ms}ms` } as CSSProperties;
}

/**
 * Portada del club: ocupa casi toda la pantalla, porque es lo único que el
 * jugador ve al abrir el link de WhatsApp y de ahí decide si sigue.
 *
 * <p>El club la configura desde el panel: título, texto del botón y cuánto se
 * oscurece la foto. Ese último ajuste no es cosmético — sobre una foto clara el
 * título queda ilegible y es lo único que el club puede corregir sin cambiarla.
 *
 * <p>Abajo del título van los primeros horarios libres de hoy. Quien abre el
 * link a la tarde casi siempre quiere jugar esa noche: tocar uno lleva directo
 * a sus datos, sin pasar por el calendario ni por la grilla. Si el jugador ya
 * está mirando otro día, el bloque no aparece, porque no hay "hoy" que ofrecer.
 */
export function HeroClassic({
  name,
  tagline,
  heroImageUrl,
  heroHeadline,
  heroCtaLabel,
  heroOverlay,
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
  heroOverlay: number;
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
  // El panel entrega 0-100, pero acá se acota igual: un valor fuera de rango, o
  // ausente porque la API todavía no lo manda, daba NaN y el velo tapaba la foto
  // entera en vez de degradarse a un valor razonable.
  const veil = Number.isFinite(heroOverlay)
    ? Math.min(100, Math.max(0, heroOverlay)) / 100
    : DEFAULT_OVERLAY / 100;

  const courtsLabel = courtCount > 0 ? `${courtCount} ${courtCount === 1 ? 'cancha' : 'canchas'}` : null;

  return (
    // mx-[calc(50%-50vw)] en vez de -mx-4: la portada tiene que sangrar hasta
    // el borde de la ventana, no solo hasta el borde de la columna de
    // contenido (que en desktop es angosta, max-w-2xl). Esa cuenta se
    // recalcula sola contra el ancho real de la columna en cada breakpoint.
    <section className={`relative mx-[calc(50%-50vw)] flex flex-col justify-end overflow-hidden ${HERO_SCREEN}`}>
      {heroImageUrl ? (
        <>
          {/* Cada club sube una foto distinta —de día, de noche, con su propia
              luz—, y una desaturación pareja las hace leer como una sola
              familia visual en vez de una foto de stock con un filtro
              cualquiera arriba. Al abrir se acerca despacio, una sola vez. */}
          <img
            src={heroImageUrl}
            alt=""
            className="portada-foto absolute inset-0 h-full w-full object-cover [filter:grayscale(35%)_contrast(1.05)_brightness(0.9)]"
            loading="eager"
            fetchPriority="high"
          />
          {/* Tres capas: el velo que gradúa el club, el degradado fijo que
              funde la foto con el fondo de la página, y un resplandor del
              color del club en la base —el mismo foco que ilumina el botón
              de abajo, subiendo hacia la foto. Sin mix-blend: en tema claro
              "aclarar" contra un fondo ya claro casi no se nota, y el
              resplandor tiene que verse igual de presente en los dos temas. */}
          <div className="absolute inset-0 bg-pista" style={{ opacity: veil }} />
          <div className="absolute inset-0 bg-gradient-to-t from-pista via-pista/60 to-transparent" />
          <div className="absolute inset-0 bg-[radial-gradient(65%_55%_at_50%_100%,var(--color-ladrillo),transparent_70%)] opacity-45" />
        </>
      ) : (
        // Sin foto la portada no se achica: el título se sostiene solo sobre un
        // resplandor del color del club.
        <div className="absolute inset-0 bg-[radial-gradient(75%_60%_at_50%_35%,var(--color-ladrillo),transparent_70%)] opacity-25" />
      )}

      <div className="relative mx-auto w-full max-w-lg px-5 pb-[clamp(1.25rem,4svh,2.5rem)] pt-8 text-center md:max-w-2xl">
        {(address || courtsLabel) && (
          <p
            className="portada-sube mx-auto inline-flex max-w-full items-center gap-2.5 rounded-full border border-cal/15 bg-pista/40 px-4 py-2 text-xs font-semibold text-cal backdrop-blur-md"
            style={delay(0)}
          >
            {address && (
              <span className="flex min-w-0 items-center gap-1.5">
                <PinGlyph />
                <span className="truncate">{address}</span>
              </span>
            )}
            {address && courtsLabel && <span aria-hidden className="h-3 w-px shrink-0 bg-cal/25" />}
            {courtsLabel && <span className="shrink-0 tabular-nums">{courtsLabel}</span>}
          </p>
        )}

        <h1
          className={`hero-title portada-sube mt-[clamp(0.75rem,2.5svh,1.5rem)] ${titleSize(headline)} [text-shadow:0_2px_12px_rgba(0,0,0,0.45)]`}
          style={delay(120)}
        >
          {headline}
        </h1>
        {tagline && (
          <p
            className="portada-sube mx-auto mt-4 flex max-w-md items-center justify-center gap-3"
            style={delay(240)}
          >
            <span className="h-px w-6 shrink-0 bg-cal/25" aria-hidden />
            <span className="eyebrow text-cal/75">{tagline}</span>
            <span className="h-px w-6 shrink-0 bg-cal/25" aria-hidden />
          </p>
        )}

        {todaySlots && onPickSlot && timeZone && playersPerCourt ? (
          <TodaySlots
            slots={todaySlots}
            timeZone={timeZone}
            playersPerCourt={playersPerCourt}
            onPick={onPickSlot}
            onSeeToday={onSeeToday}
          />
        ) : (
          <div className="mt-[clamp(1rem,4svh,2rem)]" />
        )}

        <div className="portada-sube" style={delay(520)}>
          <a
            href="#reserva"
            className="group mt-[clamp(0.75rem,3svh,1.5rem)] inline-flex items-center gap-2 rounded-full bg-ladrillo px-10 py-4 text-sm font-bold uppercase tracking-[0.12em] text-cal transition duration-300 hover:-translate-y-0.5 hover:bg-ladrillo/90 [box-shadow:var(--shadow-glow)]"
          >
            {cta}
            <span aria-hidden className="transition-transform duration-300 group-hover:translate-x-1">
              →
            </span>
          </a>
          <p className="eyebrow mt-[clamp(0.5rem,2svh,1.25rem)] text-cal/50 muy-bajo:hidden">Sin registro · Confirmación al instante</p>
        </div>
      </div>
    </section>
  );
}

/**
 * Los primeros horarios libres de hoy, como fichas que se tocan. Cada una
 * lleva lo que pone cada jugador, que es lo que se pregunta antes de decidir.
 */
function TodaySlots({
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
  const shown = slots.slice(0, QUICK_SLOTS);

  if (slots.length === 0) {
    return (
      <div className="portada-sube mt-[clamp(1rem,4svh,2rem)]" style={delay(360)}>
        <p className="text-sm text-cal/75">Hoy no quedan horarios libres.</p>
        <a
          href="#reserva"
          className="mt-1 inline-block text-sm font-semibold text-ladrillo-claro underline-offset-4 hover:underline"
        >
          Mirá otros días
        </a>
      </div>
    );
  }

  return (
    <div className="mt-[clamp(1rem,4svh,2rem)]">
      {/* El resto de los horarios va en el mismo renglón que la cuenta y no
          debajo de las fichas: ahí empujaba el botón fuera de la pantalla en
          una notebook. */}
      <div
        className="portada-sube flex flex-wrap items-center justify-center gap-x-3 gap-y-1"
        style={delay(360)}
      >
        <p className="eyebrow flex items-center gap-2 text-cal/75">
          <span className="relative flex size-2">
            <span className="absolute inset-0 animate-ping rounded-full bg-ladrillo-claro opacity-70" />
            <span className="relative size-2 rounded-full bg-ladrillo-claro" />
          </span>
          {slots.length === 1 ? 'Queda 1 horario libre hoy' : `Quedan ${slots.length} horarios libres hoy`}
        </p>
        {/* En un teléfono bajo entran dos fichas y no cuatro, así que "Ver
            todos" aparece desde el tercer horario. El -my-3 le deja al pulgar
            el área de siempre sin sumarle alto al renglón. */}
        {slots.length > 2 && onSeeToday && (
          <button
            type="button"
            onClick={onSeeToday}
            className={`-my-3 text-xs font-semibold text-ladrillo-claro underline-offset-4 hover:underline ${
              slots.length > QUICK_SLOTS ? '' : 'hidden max-sm:bajo:inline'
            }`}
          >
            Ver todos →
          </button>
        )}
      </div>
      <ul className="mx-auto mt-[clamp(0.5rem,2svh,1rem)] grid max-w-md grid-cols-2 gap-2 sm:flex sm:max-w-none sm:flex-wrap sm:justify-center">
        {shown.map((slot, index) => {
          const cheapest = Math.min(...slot.available.map((court) => court.price));
          const time = clockTime(slot.startsAt, timeZone);
          return (
            <li key={slot.startsAt} className="portada-sube max-sm:bajo:nth-[n+3]:hidden" style={delay(420 + index * 60)}>
              <button
                type="button"
                onClick={() => onPick(slot)}
                aria-label={`Reservar hoy a las ${time}, ${money(cheapest / playersPerCourt)} por persona${slot.promo ? ', en promo' : ''}`}
                className="group relative flex w-full flex-col items-start rounded-2xl border border-cal/15 bg-pista/45 px-4 py-[clamp(0.5rem,1.4svh,0.75rem)] text-left backdrop-blur-md transition duration-300 hover:-translate-y-0.5 hover:border-ladrillo hover:bg-ladrillo sm:w-32"
              >
                {slot.promo && (
                  <span className="absolute right-3 top-3 text-[0.6rem] font-bold uppercase tracking-[0.12em] text-ladrillo-claro transition-colors group-hover:text-cal">
                    Promo
                  </span>
                )}
                <span className="display text-2xl leading-none tabular-nums text-cal">{time}</span>
                <span className="mt-1 text-xs font-semibold tabular-nums text-cal/70 transition-colors group-hover:text-cal">
                  {money(cheapest / playersPerCourt)} c/u
                </span>
              </button>
            </li>
          );
        })}
      </ul>
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
      className="size-3.5 shrink-0 text-ladrillo-claro"
    >
      <path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 1 1 13 0c0 5.4-6.5 11-6.5 11Z" />
      <circle cx="12" cy="10" r="2.3" />
    </svg>
  );
}
