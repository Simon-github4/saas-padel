import { DEFAULT_CTA } from './HeroClassic';

/**
 * Grilla de líneas de cancha: dos degradados repetidos, uno por eje, en vez de
 * una imagen de fondo — así escala sin pixelarse y no suma un asset más.
 */
const COURT_GRID = {
  backgroundImage:
    'repeating-linear-gradient(0deg, rgba(255,255,255,.05) 0px, rgba(255,255,255,.05) 1px, transparent 1px, transparent 34px), ' +
    'repeating-linear-gradient(90deg, rgba(255,255,255,.05) 0px, rgba(255,255,255,.05) 1px, transparent 1px, transparent 34px)',
};

/**
 * Portada "marcador de cancha": foto del club en un badge circular tipo
 * pelota, sobre un fondo casi negro fijo — a diferencia de HeroClassic, este
 * diseño no sigue la paleta clara/oscura del club porque su identidad (grilla
 * de cancha, resplandor naranja) depende de ese fondo oscuro. El acento sí
 * respeta el color de marca del club: usa --color-ladrillo, que el club puede
 * pisar con su primaryColor.
 */
export function HeroScoreboard({
  name,
  tagline,
  heroImageUrl,
  heroHeadline,
  heroCtaLabel,
  address,
  courtCount,
}: {
  name: string;
  tagline: string | null;
  heroImageUrl: string | null;
  heroHeadline: string | null;
  heroCtaLabel: string | null;
  address: string | null;
  courtCount: number;
}) {
  const headline = heroHeadline ?? name;
  const cta = heroCtaLabel ?? DEFAULT_CTA;
  const stats = [
    address,
    courtCount > 0 ? `${courtCount} ${courtCount === 1 ? 'cancha' : 'canchas'}` : null,
  ].filter((value): value is string => Boolean(value));

  return (
    <section className="relative mx-[calc(50%-50vw)] flex min-h-[88svh] flex-col justify-center overflow-hidden bg-[#0a0a0a] py-14 text-white">
      <div className="absolute inset-0" style={COURT_GRID} aria-hidden />
      <div
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(60%_50%_at_50%_40%,var(--color-ladrillo),transparent_70%)] opacity-20"
        aria-hidden
      />

      <div className="relative mx-auto w-full max-w-lg px-5 text-center md:max-w-2xl">
        <p className="eyebrow flex items-center justify-center gap-2 text-ladrillo-claro">
          <span className="size-1.5 rounded-full bg-ladrillo-claro" aria-hidden />
          Cancha disponible
        </p>

        <h1 className="hero-title mt-4 text-[clamp(2rem,9vw,3.25rem)] leading-[0.92] tracking-tight">
          {headline}
        </h1>
        {tagline && <p className="eyebrow mt-3 text-white/60">{tagline}</p>}

        <div className="mt-8 flex items-center gap-4">
          <span className="h-px flex-1 bg-white/20" aria-hidden />
          <div className="relative size-40 shrink-0 overflow-hidden rounded-full border-[3px] border-ladrillo shadow-[0_0_0_7px_color-mix(in_srgb,var(--color-ladrillo)_15%,transparent)]">
            {heroImageUrl ? (
              <img src={heroImageUrl} alt="" className="h-full w-full object-cover" loading="eager" />
            ) : (
              <div className="h-full w-full bg-[radial-gradient(75%_75%_at_50%_50%,var(--color-ladrillo),transparent_70%)] opacity-40" />
            )}
          </div>
          <span className="h-px flex-1 bg-white/20" aria-hidden />
        </div>

        {stats.length > 0 && (
          <p className="mt-6 text-sm font-bold uppercase tracking-wide tabular-nums text-white/90">
            {stats.join(' · ')}
          </p>
        )}

        <a
          href="#reserva"
          className="mt-10 inline-flex w-full items-center justify-center gap-2 rounded-full bg-ladrillo px-10 py-4 text-sm font-bold uppercase tracking-[0.12em] text-white transition hover:bg-ladrillo/90 [box-shadow:var(--shadow-glow)]"
        >
          {cta}
          <span aria-hidden>→</span>
        </a>
        <p className="eyebrow mt-5 text-white/40">Sin registro · Confirmación al instante</p>
      </div>
    </section>
  );
}
