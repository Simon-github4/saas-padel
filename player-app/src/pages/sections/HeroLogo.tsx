import type { CSSProperties } from 'react';
import type { Slot } from '../../api/client';
import { InstagramGlyph, type ClubInstagram } from '../../components/Ui';
import { DEFAULT_CTA, HERO_SCREEN, PinGlyph, TodaySlots } from './HeroClassic';

function delay(ms: number): CSSProperties {
  return { '--portada-delay': `${ms}ms` } as CSSProperties;
}

/**
 * Cuerpo del título. Más chico que en la portada Base: acá el logo ya dice el
 * nombre del club, y el alto que no se lleva el título es el que gana la placa.
 */
function titleSize(headline: string): string {
  if (headline.length > 20) {
    return 'text-[length:min(clamp(2rem,8vw,3.5rem),6svh)]';
  }
  return 'text-[length:min(clamp(2.5rem,12vw,4.75rem),8.5svh)]';
}

/**
 * Portada "logo del club", para el club que lo único que tiene es el logo de
 * su perfil de Instagram: un cuadrado de unos 640 px, muchas veces ya
 * recomprimido. Base y cancha partida lo estiran a pantalla completa —en un
 * celular, dos o tres veces su tamaño— y se ve pixelado.
 *
 * <p>Acá el logo va en una placa que mide como mucho 15rem (240 px): aun en un
 * celular, que pinta tres píxeles por punto, eso pide unos 720, casi lo que la
 * imagen tiene. El fondo es el mismo logo, enorme y desenfocado: toma los
 * colores del club sin pedirle otra foto, y con ese desenfoque la resolución
 * deja de importar.
 *
 * <p>Sigue la paleta clara u oscura del club, como la Base, y ofrece los mismos
 * horarios de hoy para reservar directo.
 */
export function HeroLogo({
  name,
  tagline,
  heroImageUrl,
  heroHeadline,
  heroCtaLabel,
  address,
  instagram,
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
  instagram?: ClubInstagram | null;
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
  const courtsLabel = courtCount > 0 ? `${courtCount} ${courtCount === 1 ? 'cancha' : 'canchas'}` : null;

  return (
    <section className={`relative mx-[calc(50%-50vw)] flex flex-col justify-center overflow-hidden py-[clamp(1rem,4svh,3rem)] ${HERO_SCREEN}`}>
      {heroImageUrl && (
        // scale-150 para que el borde del desenfoque, que se aclara, quede
        // afuera de la portada.
        <img
          src={heroImageUrl}
          alt=""
          aria-hidden
          className="absolute inset-0 h-full w-full scale-150 object-cover opacity-45 blur-3xl saturate-150"
        />
      )}
      <div className="absolute inset-0 bg-gradient-to-b from-pista/30 via-pista/70 to-pista" aria-hidden />
      <div
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(65%_55%_at_50%_100%,var(--color-ladrillo),transparent_70%)] opacity-30"
        aria-hidden
      />

      <div className="relative mx-auto w-full max-w-lg px-5 text-center md:max-w-2xl">
        <div className="portada-sube mx-auto w-fit" style={delay(0)}>
          <Plate imageUrl={heroImageUrl} name={name} />
        </div>

        <h1
          className={`hero-title portada-sube mt-[clamp(0.75rem,3svh,1.75rem)] ${titleSize(headline)}`}
          style={delay(120)}
        >
          {headline}
        </h1>
        {tagline && (
          <p
            className="portada-sube mx-auto mt-3 flex max-w-md items-center justify-center gap-3 muy-bajo:hidden"
            style={delay(200)}
          >
            <span className="h-px w-6 shrink-0 bg-cal/25" aria-hidden />
            <span className="eyebrow text-cal/75">{tagline}</span>
            <span className="h-px w-6 shrink-0 bg-cal/25" aria-hidden />
          </p>
        )}

        {(address || courtsLabel || instagram) && (
          <div
            className="portada-sube mt-[clamp(0.5rem,2svh,1.25rem)] flex flex-wrap items-center justify-center gap-x-5 gap-y-1 text-sm font-semibold text-cal/80"
            style={delay(280)}
          >
            {(address || courtsLabel) && (
              <p className="flex min-w-0 items-center gap-1.5">
                {address && <PinGlyph />}
                {address && <span className="truncate">{address}</span>}
                {address && courtsLabel && <span aria-hidden className="mx-1 h-3 w-px shrink-0 bg-cal/25" />}
                {courtsLabel && <span className="shrink-0 tabular-nums">{courtsLabel}</span>}
              </p>
            )}
            {instagram && (
              <a
                href={instagram.url}
                target="_blank"
                rel="noreferrer"
                aria-label={`Instagram de ${name}: @${instagram.handle}`}
                className="flex min-w-0 items-center gap-1.5 transition hover:text-cal"
              >
                <InstagramGlyph className="size-4 shrink-0 text-ladrillo-claro" />
                <span className="truncate">@{instagram.handle}</span>
              </a>
            )}
          </div>
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
 * La placa del logo: fondo blanco porque casi todos los logos de perfil están
 * hechos sobre blanco, y object-contain para que uno que no es cuadrado quede
 * entero en vez de recortado. Sin logo, la inicial del club.
 */
function Plate({ imageUrl, name }: { imageUrl: string | null; name: string }) {
  return (
    <div className="size-[clamp(6rem,24svh,15rem)] overflow-hidden rounded-[22%] bg-white shadow-[0_0_0_1px_rgba(0,0,0,0.06),0_0_0_8px_color-mix(in_srgb,var(--color-cal)_8%,transparent),0_24px_60px_rgba(0,0,0,0.35)]">
      {imageUrl ? (
        <img src={imageUrl} alt={`Logo de ${name}`} className="h-full w-full object-contain" loading="eager" fetchPriority="high" />
      ) : (
        <div className="display grid h-full w-full place-items-center bg-ladrillo text-[length:clamp(3rem,12svh,7rem)] text-white" aria-hidden>
          {name.trim().charAt(0).toUpperCase()}
        </div>
      )}
    </div>
  );
}
