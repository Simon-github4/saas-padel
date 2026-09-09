import { DEFAULT_CTA } from './HeroClassic';

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
 */
export function HeroCourtSplit({
  name,
  heroImageUrl,
  heroHeadline,
  heroCtaLabel,
  address,
  courtCount,
}: {
  name: string;
  heroImageUrl: string | null;
  heroHeadline: string | null;
  heroCtaLabel: string | null;
  address: string | null;
  courtCount: number;
}) {
  const headline = heroHeadline ?? name;
  const cta = heroCtaLabel ?? DEFAULT_CTA;
  const availabilityLabel =
    courtCount > 0 ? `${courtCount} ${courtCount === 1 ? 'cancha disponible' : 'canchas disponibles'}` : null;

  return (
    <section className="relative mx-[calc(50%-50vw)] overflow-hidden bg-[#f6f3ee] text-[#17140f]">
      <div className="flex min-h-[88svh] flex-col md:min-h-[82svh] md:flex-row">
        <div className="relative h-72 shrink-0 overflow-hidden sm:h-96 md:h-auto md:w-[56%]">
          {heroImageUrl ? (
            <img
              src={heroImageUrl}
              alt=""
              className="absolute inset-0 h-full w-full object-cover"
              loading="eager"
              fetchPriority="high"
            />
          ) : (
            <div className="absolute inset-0 bg-[radial-gradient(75%_60%_at_50%_35%,var(--color-ladrillo),transparent_70%)] bg-[#17140f] opacity-90" />
          )}
          <div className="absolute inset-0 bg-[#0a0a0a] opacity-[0.18]" aria-hidden />

          {/* Red dibujada sobre la foto: horizontal al pie en mobile (el corte
              queda abajo), vertical al borde derecho en desktop (el corte
              pasa a la derecha). Mismo color que el fondo del panel de texto,
              para que se lea como una línea de cancha y no como un borde. */}
          <span
            className="absolute inset-x-0 bottom-0 h-[3px] bg-[#f6f3ee] md:hidden"
            aria-hidden
          />
          <span
            className="absolute bottom-0 left-1/2 h-[34px] w-[3px] -translate-x-1/2 bg-[#f6f3ee] md:hidden"
            aria-hidden
          />
          <span
            className="absolute bottom-0 left-1/2 size-5 -translate-x-1/2 translate-y-1/2 rounded-full border-2 border-[#f6f3ee] md:hidden"
            aria-hidden
          />
          <span
            className="absolute inset-y-0 right-0 hidden w-[3px] bg-[#f6f3ee] md:block"
            aria-hidden
          />
          <span
            className="absolute right-0 top-1/2 hidden size-5 -translate-y-1/2 translate-x-1/2 rounded-full border-2 border-[#f6f3ee] md:block"
            aria-hidden
          />
        </div>

        <div className="flex flex-1 flex-col justify-center gap-5 px-6 py-10 text-center md:px-16 md:py-0 md:text-left">
          {availabilityLabel && (
            <p className="eyebrow text-ladrillo">{availabilityLabel}</p>
          )}
          <h1 className="text-4xl font-extrabold leading-[0.95] tracking-tight md:text-7xl">
            {headline}
          </h1>
          {address && <p className="text-sm text-[#57524a] md:text-base">{address}</p>}

          <div className="mt-3 flex flex-col items-center gap-3 md:items-start">
            <a
              href="#reserva"
              className="inline-flex w-full items-center justify-center gap-2 rounded-full bg-[#17140f] px-8 py-4 text-sm font-bold uppercase tracking-[0.12em] text-[#f6f3ee] transition hover:bg-[#17140f]/90 md:w-auto"
            >
              {cta}
              <span aria-hidden>→</span>
            </a>
            <p className="eyebrow text-[#8a8377]">Sin registro · Confirmación al instante</p>
          </div>
        </div>
      </div>
    </section>
  );
}
