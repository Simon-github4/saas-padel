import { useState, type FocusEvent, type ReactNode } from 'react';
import { DemoClubCta } from './Cta';
import { CONTAINER, CheckGlyph, Reveal, SectionHeading, useInView, usePrefersReducedMotion } from './motion';

/**
 * Capturas reales del sistema (ver player-app/public/capturas, que el backend
 * sirve sin sesion por SecurityConfig.playerAppChain): las del panel,
 * del club de ejemplo con nombres inventados; la del jugador, del club de
 * demostración en producción, al que lleva el botón de esa pestaña. Las
 * maquetas de arriba explican la idea; esto muestra que el producto existe y
 * que se usa sin capacitación.
 *
 * <p>Una pestaña por pantalla en vez de una galería suelta: cada captura va con
 * lo que se hace ahí, que es lo que vende la facilidad de uso.
 *
 * <p>Avanza sola cada 3 segundos, con una barra que se llena en la pestaña
 * activa: el que solo scrollea ve las cuatro pantallas sin tocar nada. Se frena
 * con el mouse o el foco encima, fuera de pantalla y con movimiento reducido.
 * El cambio lo dispara el fin de la animación de la barra y no un timer
 * aparte, así pausar la barra pausa también el avance.
 */
const SHOTS = [
  {
    id: 'agenda',
    tab: 'La agenda',
    title: 'Todo el día en una pantalla',
    points: [
      'Las reservas de la web, las del teléfono y los turnos fijos, en la misma grilla.',
      'El color te dice qué está cobrado y qué falta cobrar.',
      'Los turnos fijos del día, listados arriba de un vistazo.',
    ],
    src: '/capturas/agenda.webp',
    width: 2880,
    height: 2120,
    alt: 'Agenda del panel con las reservas del día por cancha, cobradas y por cobrar',
    phone: false,
  },
  {
    id: 'cargar',
    tab: 'Cargar un turno',
    title: 'Un turno de teléfono, en segundos',
    points: [
      'Tocás el horario libre, escribís nombre y teléfono, y listo.',
      'El precio sale solo de tus tarifas.',
      'Esa cancha deja de aparecer libre en tu link al instante.',
    ],
    src: '/capturas/cargar-turno.webp',
    width: 1008,
    height: 1232,
    alt: 'Ventana para cargar un turno que entró por teléfono',
    phone: false,
  },
  {
    id: 'cobrar',
    tab: 'Cobrar',
    title: 'Cobrás desde el mismo turno',
    points: [
      'Total, lo pagado y el saldo, sin hacer cuentas.',
      'Efectivo, transferencia o Mercado Pago, y los consumos del buffet en la misma ventana.',
      'Todo queda en la caja del día.',
    ],
    src: '/capturas/cobrar.webp',
    width: 1196,
    height: 1324,
    alt: 'Detalle de un turno con el saldo y el cobro en el mostrador',
    phone: false,
  },
  {
    id: 'jugador',
    tab: 'Lo que ve el jugador',
    title: 'Reserva solo, desde el celular',
    points: [
      'Elige el día y la hora, con el precio y las canchas libres a la vista.',
      'Sin crear cuenta ni bajar nada: nombre, teléfono y listo.',
      'La reserva cae sola en tu agenda.',
    ],
    src: '/capturas/jugador.webp',
    width: 780,
    height: 1688,
    alt: 'Página de reservas de un club en el celular, eligiendo la hora',
    phone: true,
    demo: true,
  },
] as const;

function Icon({ children }: { children: ReactNode }) {
  return (
    <svg
      aria-hidden
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-5"
    >
      {children}
    </svg>
  );
}

const ICONS: Record<(typeof SHOTS)[number]['id'], ReactNode> = {
  agenda: (
    <Icon>
      <rect x="3" y="4.5" width="18" height="16" rx="2.5" />
      <path d="M3 9.5h18M8 3v3M16 3v3M8 13.5h2M14 13.5h2M8 17h2" />
    </Icon>
  ),
  cargar: (
    <Icon>
      <path d="M5 4h3l1.5 4-2 1.3a11 11 0 0 0 7.2 7.2l1.3-2 4 1.5v3a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z" />
      <path d="M17 3v6M14 6h6" />
    </Icon>
  ),
  cobrar: (
    <Icon>
      <rect x="2.5" y="6" width="19" height="12" rx="2" />
      <circle cx="12" cy="12" r="2.5" />
      <path d="M6 9.5v5M18 9.5v5" />
    </Icon>
  ),
  jugador: (
    <Icon>
      <rect x="6.5" y="2.5" width="11" height="19" rx="2.5" />
      <path d="M10.5 18.5h3" />
    </Icon>
  ),
};

export function ShowcaseSection() {
  const [active, setActive] = useState(0);
  const [held, setHeld] = useState(false);
  const [ref, inView] = useInView<HTMLDivElement>({ once: false, rootMargin: '0px' });
  const reduced = usePrefersReducedMotion();
  const playing = inView && !held && !reduced;
  const shot = SHOTS[active];

  const releaseOnBlur = (event: FocusEvent<HTMLDivElement>) => {
    if (!event.currentTarget.contains(event.relatedTarget)) {
      setHeld(false);
    }
  };

  return (
    <section id="asi-se-ve" className="scroll-mt-32 lg:scroll-mt-16 border-y border-cal/10 bg-vidrio/40 py-24 md:py-36">
      <div className={CONTAINER}>
        <SectionHeading
          eyebrow="Así se ve"
          title="Fácil desde el primer día"
          lead="Sin capacitación ni manuales. Estas son las pantallas reales que usan el mostrador y tus jugadores."
        />

        <Reveal delay={100} className="mt-12 md:mt-16">
          <div
            ref={ref}
            onMouseEnter={() => setHeld(true)}
            onMouseLeave={() => setHeld(false)}
            onFocus={() => setHeld(true)}
            onBlur={releaseOnBlur}
          >
            <div
              role="tablist"
              aria-label="Pantallas del sistema"
              className="grid grid-cols-2 gap-2 rounded-[1.5rem] border border-cal/10 bg-pista/70 p-2 lg:grid-cols-4"
            >
              {SHOTS.map((item, index) => {
                const selected = index === active;
                return (
                  <button
                    key={item.id}
                    type="button"
                    role="tab"
                    id={`tab-${item.id}`}
                    aria-selected={selected}
                    aria-controls={`panel-${item.id}`}
                    onClick={() => setActive(index)}
                    className={`group relative flex items-center gap-3 overflow-hidden rounded-2xl border px-3 py-3 text-left transition duration-300 sm:px-4 ${
                      selected
                        ? 'border-ladrillo/40 bg-vidrio [box-shadow:var(--shadow-card)]'
                        : 'border-transparent hover:bg-cal/[0.04]'
                    }`}
                  >
                    <span
                      className={`grid size-10 shrink-0 place-items-center rounded-xl transition duration-300 ${
                        selected ? 'bg-ladrillo text-cal' : 'bg-cal/[0.06] text-ink-soft group-hover:text-cal'
                      }`}
                    >
                      {ICONS[item.id]}
                    </span>
                    <span className="min-w-0">
                      <span className="block text-[0.65rem] font-semibold tabular-nums tracking-[0.14em] text-ink-mute">
                        0{index + 1}
                      </span>
                      <span
                        className={`block text-sm font-semibold leading-tight transition-colors ${
                          selected ? 'text-cal' : 'text-ink-soft group-hover:text-cal'
                        }`}
                      >
                        {item.tab}
                      </span>
                    </span>
                    {selected && !reduced && (
                      <span
                        key={active}
                        aria-hidden
                        data-paused={!playing || undefined}
                        onAnimationEnd={() => setActive((current) => (current + 1) % SHOTS.length)}
                        className="mk-progress absolute inset-x-0 bottom-0 h-[3px] bg-ladrillo"
                      />
                    )}
                  </button>
                );
              })}
            </div>

            <div
              role="tabpanel"
              id={`panel-${shot.id}`}
              aria-labelledby={`tab-${shot.id}`}
              className="mt-8 grid items-center gap-10 lg:grid-cols-[1.6fr_1fr] lg:gap-14"
            >
              {/* Alto fijo en pantallas anchas: cambiar de pestaña no mueve el resto de la página. */}
              <a
                key={shot.id}
                href={shot.src}
                target="_blank"
                rel="noreferrer"
                title="Ver en tamaño completo"
                className="mk-fade-in flex items-center justify-center lg:h-[34rem]"
              >
                <img
                  src={shot.src}
                  width={shot.width}
                  height={shot.height}
                  alt={shot.alt}
                  loading="lazy"
                  className={`h-auto max-h-[34rem] w-auto max-w-full border border-cal/10 object-contain [box-shadow:var(--shadow-card)] ${
                    shot.phone ? 'rounded-[2rem] lg:max-h-full' : 'rounded-2xl'
                  }`}
                />
              </a>

              <div key={`text-${shot.id}`} className="mk-fade-in">
                <h3 className="text-3xl tracking-[0.04em] md:text-4xl">{shot.title}</h3>
                <ul className="mt-6 space-y-4">
                  {shot.points.map((point) => (
                    <li key={point} className="flex gap-3 text-sm leading-relaxed text-ink-soft md:text-base">
                      <CheckGlyph className="mt-1 size-4 shrink-0 text-ladrillo-claro" />
                      {point}
                    </li>
                  ))}
                </ul>
                {'demo' in shot && (
                  <DemoClubCta className="mt-8 w-full sm:w-auto">Ver el club de demostración</DemoClubCta>
                )}
                <p className="mt-6 text-xs text-ink-mute">Tocá la imagen para verla en tamaño completo.</p>
              </div>
            </div>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
