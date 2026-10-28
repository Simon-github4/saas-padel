import { useEffect, useState, type CSSProperties } from 'react';
import type { Slot } from '../../api/client';
import { InstagramGlyph, type ClubInstagram } from '../../components/Ui';
import { loadLogoCutout, type Cutout } from '../../logoCutout';
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
 * <p>Acá el logo va suelto: se le borra el blanco de fondo (ver logoCutout) y
 * queda apoyado sobre la portada, sin placa ni marco, nunca más grande que lo
 * que la imagen aguanta. El fondo toma el color de marca del propio logo, en
 * una franja que baja desde arriba y se pierde antes del título.
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
  const logo = useLogoCutout(heroImageUrl);
  // Sin color propio (un logo en blanco y negro, o mientras se procesa), el del club.
  const tint = logo?.tint ? `rgb(${logo.tint.join(' ')})` : 'var(--color-ladrillo)';

  return (
    <section className={`relative mx-[calc(50%-50vw)] flex flex-col justify-center overflow-hidden py-[clamp(1rem,4svh,3rem)] ${HERO_SCREEN}`}>
      <div
        className="absolute inset-0 bg-[linear-gradient(to_bottom,color-mix(in_srgb,var(--tinte)_32%,transparent),transparent_55%)]"
        style={{ '--tinte': tint } as CSSProperties}
        aria-hidden
      />
      <div
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(65%_55%_at_50%_100%,var(--color-ladrillo),transparent_70%)] opacity-20"
        aria-hidden
      />

      {/* --logo: lado del logo. Hasta 13rem (208 px): en un celular, que pinta
          tres píxeles por punto, eso pide unos 620, lo que la imagen tiene. */}
      <div className="relative mx-auto w-full max-w-lg px-5 text-center [--logo:clamp(5.5rem,22svh,13rem)] md:max-w-2xl">
        <Logo imageUrl={heroImageUrl} logo={logo} name={name} />

        <h1
          className={`hero-title portada-sube mt-[clamp(0.5rem,2.5svh,1.5rem)] ${titleSize(headline)}`}
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

type LoadedLogo = Cutout & { src: string };

/**
 * El logo recortado, o null mientras se procesa. Si no se puede recortar (una
 * URL de otro sitio que no deja leer sus píxeles), devuelve la imagen tal cual,
 * marcada como no recortada.
 */
function useLogoCutout(url: string | null): LoadedLogo | null {
  const [logo, setLogo] = useState<{ url: string; value: LoadedLogo } | null>(null);

  useEffect(() => {
    if (!url) {
      return;
    }
    let cancelled = false;
    loadLogoCutout(url)
      .then((result) => {
        if (!cancelled) {
          setLogo({ url, value: result });
        }
      })
      .catch(() => {
        if (!cancelled) {
          setLogo({ url, value: { src: url, cut: false, tint: null } });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [url]);

  return logo && logo.url === url ? logo.value : null;
}

/**
 * El logo suelto sobre la portada. Recortado, lleva solo una sombra que sigue
 * su silueta (drop-shadow, no box-shadow) para despegarse del fondo. Si no se
 * pudo recortar —una foto, un logo sobre un color—, va con las esquinas
 * redondeadas, que es lo mínimo para que no corte en seco. Mientras se procesa
 * el lugar queda reservado, así nada salta cuando aparece. Si el recorte no
 * se puede mostrar, vuelve a la imagen original: un logo roto en la portada
 * es peor que uno sin recortar. Sin imagen, la inicial del club.
 */
function Logo({ imageUrl, logo, name }: { imageUrl: string | null; logo: LoadedLogo | null; name: string }) {
  const [brokenSrc, setBrokenSrc] = useState<string | null>(null);
  if (!imageUrl) {
    return (
      <div
        className="display portada-sube mx-auto grid size-[var(--logo)] place-items-center rounded-full bg-ladrillo text-[length:calc(var(--logo)*0.55)] text-white"
        style={delay(0)}
        aria-hidden
      >
        {name.trim().charAt(0).toUpperCase()}
      </div>
    );
  }
  return (
    <div className="mx-auto size-[var(--logo)]">
      {logo && (
        <img
          src={brokenSrc === logo.src ? imageUrl : logo.src}
          alt={`Logo de ${name}`}
          onError={() => setBrokenSrc(logo.src)}
          className={`logo-aparece h-full w-full object-contain ${
            logo.cut && brokenSrc !== logo.src
              ? '[filter:drop-shadow(0_10px_18px_rgba(0,0,0,0.28))]'
              : 'rounded-[18%] shadow-[0_18px_40px_rgba(0,0,0,0.3)]'
          }`}
        />
      )}
    </div>
  );
}
