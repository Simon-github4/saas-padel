import { useRef, type CSSProperties, type PointerEvent } from 'react';
import type { Slot } from '../../api/client';
import { clockTime } from '../../format';
import { DEFAULT_CTA } from './HeroClassic';

function delay(ms: number): CSSProperties {
  return { '--portada-delay': `${ms}ms` } as CSSProperties;
}

/**
 * Portada "cancha partida": foto de cancha a un lado, texto al otro — vertical
 * en mobile (foto arriba, texto abajo), horizontal en desktop (foto a la
 * izquierda). Es un único componente responsive y no dos: el corte de "red"
 * dibujado sobre la foto cambia de orientación con el layout, así que no hay
 * forma de compartirlo entre dos componentes separados sin duplicar el resto.
 *
 * <p>Como en HeroScoreboard, la paleta es fija (fondo claro, texto casi negro)
 * y no sigue el modo claro/oscuro del club: es la identidad de este diseño en
 * particular, no una variación del tema del sitio.
 *
 * <p>Sobre la red, justo en la marca del centro, va una ficha con el resumen de
 * hoy: une las dos mitades y le dice al jugador si vale la pena seguir. Es
 * solo información, a propósito: en este diseño los horarios se eligen abajo,
 * en la grilla, y no desde la portada como en la clásica y el marcador.
 *
 * <p>Al abrir, la foto se destapa desde la red hacia afuera, la red se dibuja y
 * la ficha cae en su lugar. En una compu con mouse la foto acompaña apenas al
 * puntero; con movimiento reducido no pasa nada de eso.
 */
export function HeroCourtSplit({
  name,
  tagline,
  heroImageUrl,
  heroHeadline,
  heroCtaLabel,
  address,
  courtCount,
  todaySlots,
  timeZone,
}: {
  name: string;
  tagline: string | null;
  heroImageUrl: string | null;
  heroHeadline: string | null;
  heroCtaLabel: string | null;
  address: string | null;
  courtCount: number;
  /** Horarios con cancha libre de hoy; null si la página no está en hoy. */
  todaySlots: Slot[] | null;
  timeZone: string;
}) {
  const headline = heroHeadline ?? name;
  const cta = heroCtaLabel ?? DEFAULT_CTA;
  const availabilityLabel =
    courtCount > 0 ? `${courtCount} ${courtCount === 1 ? 'cancha disponible' : 'canchas disponibles'}` : null;

  const photo = useRef<HTMLImageElement>(null);

  // La foto se corre unos pixeles hacia donde está el mouse. Se escribe directo
  // en el estilo y no en estado: re-renderizar la portada en cada movimiento
  // del puntero es trabajo tirado.
  function follow(event: PointerEvent<HTMLDivElement>) {
    const image = photo.current;
    if (
      !image ||
      event.pointerType !== 'mouse' ||
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    ) {
      return;
    }
    const box = event.currentTarget.getBoundingClientRect();
    const x = ((event.clientX - box.left) / box.width - 0.5) * -18;
    const y = ((event.clientY - box.top) / box.height - 0.5) * -18;
    image.style.translate = `${x.toFixed(1)}px ${y.toFixed(1)}px`;
  }

  function settle() {
    if (photo.current) {
      photo.current.style.translate = '0px 0px';
    }
  }

  return (
    <section className="relative mx-[calc(50%-50vw)] overflow-hidden bg-[#f6f3ee] text-[#17140f]">
      <div className="flex min-h-[88svh] flex-col md:min-h-[82svh] md:flex-row">
        <div
          className="relative h-80 shrink-0 sm:h-[26rem] md:h-auto md:w-[56%]"
          onPointerMove={follow}
          onPointerLeave={settle}
        >
          <div className="split-foto absolute inset-0 overflow-hidden">
            {heroImageUrl ? (
              <img
                ref={photo}
                src={heroImageUrl}
                alt=""
                className="portada-foto absolute inset-0 h-full w-full scale-[1.06] object-cover transition-[translate] duration-700 ease-out"
                loading="eager"
                fetchPriority="high"
              />
            ) : (
              <div className="absolute inset-0 bg-[radial-gradient(75%_60%_at_50%_35%,var(--color-ladrillo),transparent_70%)] bg-[#17140f] opacity-90" />
            )}
            <div className="absolute inset-0 bg-[#0a0a0a] opacity-[0.18]" aria-hidden />
            {/* Sombra hacia la red: la foto se hunde un poco antes del corte y
                la ficha de arriba se despega mejor. */}
            <div
              className="absolute inset-0 bg-gradient-to-b from-transparent via-transparent to-black/35 md:bg-gradient-to-r"
              aria-hidden
            />
          </div>

          {/* Red dibujada sobre la foto: horizontal al pie en mobile (el corte
              queda abajo), vertical al borde derecho en desktop (el corte
              pasa a la derecha). Mismo color que el fondo del panel de texto,
              para que se lea como una línea de cancha y no como un borde. */}
          <span className="split-red-h absolute inset-x-0 bottom-0 h-[3px] bg-[#f6f3ee] md:hidden" aria-hidden />
          <span className="split-red-v absolute inset-y-0 right-0 hidden w-[3px] bg-[#f6f3ee] md:block" aria-hidden />

          <TodayCard todaySlots={todaySlots} timeZone={timeZone} courtCount={courtCount} />
        </div>

        <div className="flex flex-1 flex-col justify-center px-6 pb-12 pt-16 text-center md:py-12 md:pl-32 md:pr-16 md:text-left">
          {availabilityLabel && (
            <p className="portada-sube eyebrow flex items-center justify-center gap-3 text-ladrillo md:justify-start" style={delay(200)}>
              <span className="h-px w-6 bg-current opacity-50" aria-hidden />
              {availabilityLabel}
            </p>
          )}
          <h1
            className="portada-sube mt-5 text-[clamp(2.75rem,13vw,4rem)] leading-[0.95] md:text-[clamp(3.5rem,5.6vw,5.75rem)]"
            style={delay(300)}
          >
            {headline}
          </h1>
          {tagline && (
            <p
              className="portada-sube mx-auto mt-4 max-w-sm text-base leading-relaxed text-[#57524a] md:mx-0 md:text-lg"
              style={delay(400)}
            >
              {tagline}
            </p>
          )}

          <div
            className="portada-sube mt-8 flex flex-col items-center gap-4 md:items-start"
            style={delay(500)}
          >
            <a
              href="#reserva"
              className="group inline-flex w-full items-center justify-center gap-2 rounded-full bg-[#17140f] px-8 py-4 text-sm font-bold uppercase tracking-[0.12em] text-[#f6f3ee] transition duration-300 hover:-translate-y-0.5 hover:bg-[#17140f]/90 md:w-auto"
            >
              {cta}
              <span aria-hidden className="transition-transform duration-300 group-hover:translate-x-1">
                →
              </span>
            </a>
            <p className="eyebrow text-[#8a8377]">Sin registro · Confirmación al instante</p>
          </div>

          {address && (
            <a
              href="#como-llegar"
              className="portada-sube group mt-8 flex items-center justify-center gap-3 border-t border-[#17140f]/10 pt-5 text-left md:justify-start"
              style={delay(600)}
            >
              <span className="grid size-9 shrink-0 place-items-center rounded-full bg-[#17140f]/[0.06] transition-colors group-hover:bg-ladrillo group-hover:text-white">
                <PinGlyph />
              </span>
              <span className="min-w-0">
                <span className="block text-sm font-semibold">{address}</span>
                <span className="block text-xs text-[#8a8377] transition-colors group-hover:text-[#17140f]">
                  Cómo llegar ↓
                </span>
              </span>
            </a>
          )}
        </div>
      </div>
    </section>
  );
}

/**
 * La ficha que se apoya en la marca del centro de la red: abajo de la foto en
 * el teléfono, en el borde derecho en desktop. Resume hoy sin ser un botón.
 */
function TodayCard({
  todaySlots,
  timeZone,
  courtCount,
}: {
  todaySlots: Slot[] | null;
  timeZone: string;
  courtCount: number;
}) {
  let title: string;
  let detail: string;
  if (todaySlots && todaySlots.length > 0) {
    title = todaySlots.length === 1 ? '1 horario libre' : `${todaySlots.length} horarios libres`;
    detail = `desde las ${clockTime(todaySlots[0].startsAt, timeZone)}`;
  } else if (todaySlots) {
    title = 'Hoy completo';
    detail = 'Mirá otros días';
  } else {
    title = courtCount > 0 ? `${courtCount} ${courtCount === 1 ? 'cancha' : 'canchas'}` : 'Reservá online';
    detail = 'Elegí día y hora abajo';
  }

  return (
    <div
      className="absolute bottom-0 left-1/2 z-10 -translate-x-1/2 translate-y-1/2 md:bottom-auto md:left-auto md:right-0 md:top-1/2 md:-translate-y-1/2 md:translate-x-1/2"
    >
      <div className="split-ficha flex items-center gap-3 whitespace-nowrap rounded-2xl border-[3px] border-[#f6f3ee] bg-[#17140f] py-3 pl-3 pr-5 text-[#f6f3ee] shadow-[0_18px_40px_rgba(23,20,15,0.35)]">
        <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-ladrillo text-white">
          <span className="relative flex size-2">
            {todaySlots && todaySlots.length > 0 && (
              <span className="absolute inset-0 animate-ping rounded-full bg-white opacity-70" />
            )}
            <span className="relative size-2 rounded-full bg-white" />
          </span>
        </span>
        <span className="text-left">
          <span className="eyebrow block text-[#f6f3ee]/55">{todaySlots ? 'Hoy' : 'El club'}</span>
          <span className="block text-sm font-bold leading-tight">{title}</span>
          <span className="block text-xs text-[#f6f3ee]/70">{detail}</span>
        </span>
      </div>
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
      className="size-4"
    >
      <path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 1 1 13 0c0 5.4-6.5 11-6.5 11Z" />
      <circle cx="12" cy="10" r="2.3" />
    </svg>
  );
}
