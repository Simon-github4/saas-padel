import { Link } from 'react-router-dom';
import { money } from '../../format';
import { ArrowGlyph, CONTAINER, Reveal, SectionHeading, delay, useInView } from './motion';

/** Resultados de ejemplo: los nombres son los clubes de prueba del sistema. */
const RESULTS = [
  { club: 'Costa Verde', courts: 2, price: 5000, yours: false },
  { club: 'Tu club', courts: 1, price: 6500, yours: true },
  { club: 'El Muelle', courts: 3, price: 5500, yours: false },
];

/** La búsqueda entre clubes: por qué sumarse hace más útil el sistema para todos. */
export function NetworkSection() {
  const [ref, shown] = useInView<HTMLDivElement>();

  return (
    <section className="py-24 md:py-36">
      <div className={`${CONTAINER} grid items-center gap-14 lg:grid-cols-2 lg:gap-20`}>
        <div>
          <SectionHeading
            eyebrow="Búsqueda entre clubes"
            title="El jugador que no eligió club, te encuentra"
            lead="Además de tu link, existe una búsqueda que compara horarios libres entre todos los clubes que ya están en el sistema. Cada club que se suma hace esa búsqueda más útil para todos, vos incluido."
          />
          <Reveal delay={150}>
            <Link
              to="/buscar"
              className="group mt-8 inline-flex items-center gap-2 text-sm font-semibold text-ladrillo-claro"
            >
              <span className="underline-offset-4 group-hover:underline">Ver la búsqueda</span>
              <ArrowGlyph className="size-4 transition-transform duration-300 group-hover:translate-x-1" />
            </Link>
          </Reveal>
        </div>

        <Reveal delay={100}>
          <div
            ref={ref}
            aria-hidden
            className="rounded-[1.75rem] border border-cal/10 bg-vidrio p-5 [box-shadow:var(--shadow-card)] sm:p-7"
          >
            <div className="flex items-center gap-3 rounded-full border border-cal/10 bg-pista px-5 py-3.5">
              <SearchGlyph className="size-4 text-ink-mute" />
              <span className="text-sm text-arena">Hoy · 21:00 · 4 jugadores</span>
              <span className="mk-breathe ml-[-0.4rem] h-4 w-px bg-ladrillo-claro" />
            </div>
            <p className="eyebrow mt-6 text-ink-mute">3 clubes con cancha libre</p>
            <ul className="mt-3 min-h-[13rem] space-y-2.5">
              {shown &&
                RESULTS.map((result, index) => (
                  <li
                    key={result.club}
                    className={`mk-fade-up flex items-center justify-between gap-4 rounded-2xl border px-4 py-3.5 ${
                      result.yours ? 'border-ladrillo/50 bg-ladrillo/[0.08]' : 'border-cal/10 bg-pista'
                    }`}
                    style={delay(250 + index * 180)}
                  >
                    <div className="min-w-0">
                      <p className="flex items-center gap-2 font-semibold">
                        {result.club}
                        {result.yours && (
                          <span className="eyebrow rounded-full bg-ladrillo px-2 py-0.5 text-cal">Vos</span>
                        )}
                      </p>
                      <p className="mt-0.5 text-xs text-ink-soft">
                        {result.courts} {result.courts === 1 ? 'cancha libre' : 'canchas libres'}
                      </p>
                    </div>
                    <p
                      className={`shrink-0 text-sm font-bold tabular-nums ${result.yours ? 'text-ladrillo-claro' : ''}`}
                    >
                      {money(result.price)} <span className="font-normal text-ink-soft">c/u</span>
                    </p>
                  </li>
                ))}
            </ul>
          </div>
        </Reveal>
      </div>
    </section>
  );
}

function SearchGlyph({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      strokeLinecap="round"
      aria-hidden
      className={className}
    >
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20l-3.5-3.5" />
    </svg>
  );
}
