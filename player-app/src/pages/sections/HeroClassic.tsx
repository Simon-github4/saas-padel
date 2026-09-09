/** Texto del botón cuando el club no cargó uno propio. Compartido por los tres diseños de portada. */
export const DEFAULT_CTA = 'Ver horarios';

/** Oscurecido de la foto cuando el club no lo definio. Igual al del panel. */
const DEFAULT_OVERLAY = 55;

/**
 * Cuerpo del título según su largo.
 *
 * <p>Un cuerpo fijo funciona con "Pádel Necochea" y se rompe con "Club Atlético
 * Deportivo Necochea Padel Center": a 126px ese nombre ocupaba cuatro líneas,
 * estiraba la portada al 103% de la pantalla y dejaba el botón abajo del
 * pliegue. Los nombres largos entran con menos cuerpo; los cortos conservan el
 * tamaño grande, que es el que le da fuerza a la portada.
 */
export function titleSize(headline: string): string {
  if (headline.length > 34) {
    return 'text-[clamp(2rem,8vw,3.5rem)]';
  }
  if (headline.length > 20) {
    return 'text-[clamp(2.5rem,11vw,5rem)]';
  }
  return 'text-[clamp(3rem,16vw,7rem)]';
}

/**
 * Portada del club: ocupa casi toda la pantalla, porque es lo único que el
 * jugador ve al abrir el link de WhatsApp y de ahí decide si sigue.
 *
 * <p>El club la configura desde el panel: título, texto del botón y cuánto se
 * oscurece la foto. Ese último ajuste no es cosmético — sobre una foto clara el
 * título queda ilegible y es lo único que el club puede corregir sin cambiarla.
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
}: {
  name: string;
  tagline: string | null;
  heroImageUrl: string | null;
  heroHeadline: string | null;
  heroCtaLabel: string | null;
  heroOverlay: number;
  address: string | null;
  courtCount: number;
}) {
  const facts: { label: string; value: string }[] = [];
  if (address) {
    facts.push({ label: 'Dónde', value: address });
  }
  if (courtCount > 0) {
    facts.push({ label: 'Canchas', value: String(courtCount) });
  }

  const headline = heroHeadline ?? name;
  const cta = heroCtaLabel ?? DEFAULT_CTA;
  // El panel entrega 0-100, pero acá se acota igual: un valor fuera de rango, o
  // ausente porque la API todavía no lo manda, daba NaN y el velo tapaba la foto
  // entera en vez de degradarse a un valor razonable.
  const veil = Number.isFinite(heroOverlay)
    ? Math.min(100, Math.max(0, heroOverlay)) / 100
    : DEFAULT_OVERLAY / 100;

  return (
    // svh y no vh: en el navegador del teléfono, vh cuenta la barra de
    // direcciones y la portada quedaba cortada por abajo.
    //
    // mx-[calc(50%-50vw)] en vez de -mx-4: la portada tiene que sangrar hasta
    // el borde de la ventana, no solo hasta el borde de la columna de
    // contenido (que en desktop es angosta, max-w-2xl). Esa cuenta se
    // recalcula sola contra el ancho real de la columna en cada breakpoint.
    <section className="relative mx-[calc(50%-50vw)] flex min-h-[88svh] flex-col justify-end overflow-hidden">
      {heroImageUrl ? (
        <>
          {/* Cada club sube una foto distinta —de día, de noche, con su propia
              luz—, y una desaturación pareja las hace leer como una sola
              familia visual en vez de una foto de stock con un filtro
              cualquiera arriba. */}
          <img
            src={heroImageUrl}
            alt=""
            className="absolute inset-0 h-full w-full object-cover [filter:grayscale(35%)_contrast(1.05)_brightness(0.9)]"
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

      <div className="relative mx-auto w-full max-w-lg px-5 pb-10 pt-24 text-center md:max-w-2xl">
        <h1
          className={`hero-title ${titleSize(headline)} [text-shadow:0_2px_12px_rgba(0,0,0,0.45)]`}
        >
          {headline}
        </h1>
        {tagline && (
          <p className="mx-auto mt-4 flex max-w-md items-center justify-center gap-3">
            <span className="h-px w-6 shrink-0 bg-cal/25" aria-hidden />
            <span className="eyebrow text-cal/75">{tagline}</span>
            <span className="h-px w-6 shrink-0 bg-cal/25" aria-hidden />
          </p>
        )}

        {facts.length > 0 && (
          <dl
            className={`mx-auto mt-10 grid max-w-md ${
              facts.length === 1 ? 'grid-cols-1' : 'grid-cols-2'
            } divide-x divide-cal/20 text-center`}
          >
            {facts.map((fact) => (
              <div key={fact.label} className="px-2">
                <dt className="eyebrow text-cal/55">{fact.label}</dt>
                <dd className="mt-2 text-sm font-semibold leading-snug text-cal tabular-nums">
                  {fact.value}
                </dd>
              </div>
            ))}
          </dl>
        )}

        <a
          href="#reserva"
          className="mt-10 inline-flex items-center gap-2 rounded-full bg-ladrillo px-10 py-4 text-sm font-bold uppercase tracking-[0.12em] text-cal transition hover:bg-ladrillo/90 [box-shadow:var(--shadow-glow)]"
        >
          {cta}
          <span aria-hidden>→</span>
        </a>
        <p className="eyebrow mt-5 text-cal/50">Sin registro · Confirmación al instante</p>
      </div>
    </section>
  );
}
