import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { BrandLogo } from '../../components/BrandLogo';
import { SearchGlyph, WhatsappGlyph } from '../../components/Ui';
import { BRAND, SALES_EMAIL, salesWhatsappHref } from './config';
import { CONTAINER } from './motion';

const ANCHORS = [
  { href: '#probalo', label: 'Probalo' },
  { href: '#cobros', label: 'Cobros' },
  { href: '#funciones', label: 'Qué hace' },
  { href: '#empezar', label: 'Empezar' },
  { href: '#precio', label: 'Precio' },
];

/** La sección que ocupa el centro de la pantalla, para marcarla en la barra. */
function useActiveAnchor(): string | null {
  const [active, setActive] = useState<string | null>(null);
  useEffect(() => {
    const sections = ANCHORS.map((anchor) => document.querySelector(anchor.href)).filter(
      (section): section is Element => section !== null,
    );
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setActive(`#${entry.target.id}`);
          }
        }
      },
      // Una franja fina en el medio de la pantalla: a lo sumo una sección la
      // cruza a la vez.
      { rootMargin: '-45% 0px -54% 0px' },
    );
    sections.forEach((section) => observer.observe(section));
    return () => observer.disconnect();
  }, []);
  return active;
}

/**
 * Barra superior de la landing: marca, anclas a las secciones, las dos puertas
 * de entrada ("Buscar turno" para el jugador, "Soy club" para el dueño que ya es
 * cliente) y el CTA de venta.
 *
 * <p>Arriba de todo es transparente, para que la portada respire; al bajar se
 * vuelve sólida. La sección visible queda marcada.
 *
 * <p>"Buscar turno" y "Soy club" están siempre a la vista, en cualquier ancho:
 * antes eran dos links chicos en gris en desktop y en el teléfono quedaban
 * adentro del menú, y quien venía a reservar o a entrar a su panel no los
 * encontraba. Debajo de lg no entran en la fila de la marca, así que van en una
 * segunda fila de la barra, a lo ancho. Lo que sí pasa al menú son las anclas.
 */
export function MarketingNav() {
  const [abierto, setAbierto] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const active = useActiveAnchor();

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  // Escape cierra, como cualquier menú. Y el listener se suelta cuando se
  // cierra: dejarlo puesto haría que esta página escuche cada tecla de más.
  useEffect(() => {
    if (!abierto) {
      return;
    }
    const alPresionar = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') {
        setAbierto(false);
      }
    };
    document.addEventListener('keydown', alPresionar);
    return () => document.removeEventListener('keydown', alPresionar);
  }, [abierto]);

  const solid = scrolled || abierto;

  return (
    <header
      className={`sticky top-0 z-30 border-b transition-[background-color,border-color,backdrop-filter] duration-500 ${
        solid ? 'border-cal/10 bg-pista/85 backdrop-blur-xl' : 'border-transparent bg-transparent'
      }`}
    >
      <div className={`${CONTAINER} flex h-16 items-center justify-between gap-4`}>
        <a
          href="#top"
          onClick={() => setAbierto(false)}
          className="flex shrink-0 items-center text-xl transition-opacity hover:opacity-85"
        >
          <BrandLogo />
        </a>

        <nav aria-label="Secciones" className="hidden items-center gap-1 xl:flex">
          {ANCHORS.map((anchor) => {
            const current = active === anchor.href;
            return (
              <a
                key={anchor.href}
                href={anchor.href}
                aria-current={current ? 'true' : undefined}
                className={`relative rounded-full px-4 py-2 text-sm transition-colors duration-300 ${
                  current ? 'text-cal' : 'text-ink-soft hover:text-cal'
                }`}
              >
                {anchor.label}
                <span
                  aria-hidden
                  className={`absolute bottom-0.5 left-1/2 h-1 w-1 -translate-x-1/2 rounded-full bg-ladrillo-claro transition duration-300 ${
                    current ? 'scale-100 opacity-100' : 'scale-0 opacity-0'
                  }`}
                />
              </a>
            );
          })}
        </nav>

        <div className="flex shrink-0 items-center gap-2 sm:gap-3">
          <SearchCta className="hidden px-5 py-2.5 text-xs lg:inline-flex" />
          <ClubCta className="hidden px-5 py-2.5 text-xs lg:inline-flex" />
          <a
            href={salesWhatsappHref()}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-2 rounded-full bg-ladrillo px-4 py-2.5 text-xs font-bold uppercase tracking-[0.12em] text-cal transition duration-300 hover:-translate-y-px hover:bg-ladrillo/90 sm:px-5 [box-shadow:var(--shadow-glow)]"
          >
            <WhatsappGlyph className="size-3.5" />
            {/* En los teléfonos más angostos no entra al lado del logo: queda el ícono. */}
            <span className="max-[399px]:sr-only">Hablemos</span>
          </a>

          <button
            type="button"
            onClick={() => setAbierto(!abierto)}
            aria-expanded={abierto}
            aria-controls="menu-landing"
            aria-label={abierto ? 'Cerrar menú' : 'Abrir menú'}
            className="relative grid size-10 shrink-0 place-items-center rounded-full border border-cal/10 text-ink-soft transition hover:border-cal/25 hover:text-cal xl:hidden"
          >
            {/* Dos trazos que se cruzan al abrir: la hamburguesa se vuelve cruz. */}
            <span
              aria-hidden
              className={`absolute h-[1.5px] w-4 rounded-full bg-current transition duration-300 ${
                abierto ? 'rotate-45' : '-translate-y-[3.5px]'
              }`}
            />
            <span
              aria-hidden
              className={`absolute h-[1.5px] w-4 rounded-full bg-current transition duration-300 ${
                abierto ? '-rotate-45' : 'translate-y-[3.5px]'
              }`}
            />
          </button>
        </div>
      </div>

      {/* Las dos puertas en el teléfono y la tablet: una fila propia, a lo ancho. */}
      <div className={`${CONTAINER} grid grid-cols-2 gap-2 pb-3 lg:hidden`}>
        <SearchCta className="flex py-3 text-[0.8rem]" onClick={() => setAbierto(false)} />
        <ClubCta className="flex py-3 text-[0.8rem]" />
      </div>

      {/*
        Se despliega debajo de la barra y dentro del mismo header, que es
        sticky: así queda pegado a la barra al abrirlo desde cualquier punto
        del scroll, en vez de aparecer arriba de todo. Queda montado y cerrado
        con altura cero para poder animar la apertura; `inert` lo saca del
        foco y del lector de pantalla mientras está cerrado.
      */}
      <div
        id="menu-landing"
        inert={!abierto}
        className={`grid transition-[grid-template-rows] duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] xl:hidden ${
          abierto ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'
        }`}
      >
        <nav aria-label="Menú" className="overflow-hidden">
          <div className={`${CONTAINER} flex flex-col border-t border-cal/10 py-3`}>
            {ANCHORS.map((anchor, index) => (
              <a
                key={anchor.href}
                href={anchor.href}
                onClick={() => setAbierto(false)}
                className={`display border-b border-cal/[0.06] py-4 text-3xl tracking-[0.06em] transition duration-500 hover:text-ladrillo-claro ${
                  abierto ? 'translate-y-0 opacity-100' : '-translate-y-2 opacity-0'
                }`}
                style={{ transitionDelay: abierto ? `${80 + index * 50}ms` : '0ms' }}
              >
                {anchor.label}
              </a>
            ))}
          </div>
        </nav>
      </div>
    </header>
  );
}

/** "Buscar turno": la pastilla clara, la que más resalta de la barra junto con la de venta. */
function SearchCta({ className = '', onClick }: { className?: string; onClick?: () => void }) {
  return (
    <Link
      to="/buscar"
      onClick={onClick}
      className={`items-center justify-center gap-2 whitespace-nowrap rounded-full bg-cal font-bold uppercase tracking-[0.1em] text-pista transition duration-300 hover:-translate-y-px hover:bg-arena ${className}`}
    >
      <SearchGlyph className="size-3.5 shrink-0" />
      Buscar turno
    </Link>
  );
}

/**
 * "Soy club": el acceso del dueño que ya es cliente a su panel.
 *
 * <p>/admin vive en el backend (panel Vaadin), no en esta SPA: tiene que ser una
 * navegación de página completa, no un Link de React Router (que caería en el
 * catch-all y te devolvería a esta misma landing).
 */
function ClubCta({ className = '' }: { className?: string }) {
  return (
    <a
      href="/admin"
      className={`items-center justify-center gap-2 whitespace-nowrap rounded-full border border-cal/25 bg-pista/40 font-bold uppercase tracking-[0.1em] text-cal backdrop-blur transition duration-300 hover:-translate-y-px hover:border-cal/50 hover:bg-cal/10 ${className}`}
    >
      <CourtGlyph className="size-3.5 shrink-0" />
      Soy club
    </a>
  );
}

/** Cancha vista desde arriba, chiquita: el ícono de "club". */
function CourtGlyph({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinejoin="round"
      aria-hidden
      className={className}
    >
      <rect x="3" y="5" width="18" height="14" rx="1.5" />
      <path d="M12 5v14M7 9v6M17 9v6M7 12h10" />
    </svg>
  );
}

/**
 * Pie de la landing. El acceso a `/buscar` es la salida del jugador que cae
 * acá por error: el catch-all de main.tsx manda cualquier link roto a esta
 * página, y sin este link ese jugador quedaría varado en una venta que no le
 * sirve.
 */
export function MarketingFooter() {
  const linkClass = 'text-sm text-ink-soft transition hover:text-cal';
  return (
    <footer className="border-t border-cal/10 bg-vidrio/30">
      <div className={`${CONTAINER} grid gap-10 py-14 sm:grid-cols-2 lg:grid-cols-[1.6fr_1fr_1fr_1fr]`}>
        <div>
          <p className="text-2xl">
            <BrandLogo />
          </p>
          <p className="mt-3 max-w-xs text-sm text-ink-mute">Reservas online para tu club de pádel.</p>
        </div>
        <FooterColumn title="Jugadores">
          <Link to="/buscar" className={`${linkClass} font-semibold text-cal`}>
            Buscar turno
          </Link>
        </FooterColumn>
        <FooterColumn title="Clubes">
          {/* Pagina completa, no Link: /admin es el panel Vaadin del backend. */}
          <a href="/admin" className={linkClass}>
            Soy club
          </a>
          <a href={salesWhatsappHref()} target="_blank" rel="noreferrer" className={linkClass}>
            WhatsApp de ventas
          </a>
          <a href={`mailto:${SALES_EMAIL}`} className={`${linkClass} break-all`}>
            {SALES_EMAIL}
          </a>
        </FooterColumn>
        <FooterColumn title="Legal">
          <Link to="/terminos" className={linkClass}>
            Términos de uso
          </Link>
          <Link to="/privacidad" className={linkClass}>
            Privacidad
          </Link>
        </FooterColumn>
      </div>
      <div className={`${CONTAINER} border-t border-cal/[0.06] py-6`}>
        <p className="eyebrow text-ink-mute">
          © {new Date().getFullYear()} {BRAND}
        </p>
      </div>
    </footer>
  );
}

function FooterColumn({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <p className="eyebrow text-ink-mute">{title}</p>
      <div className="mt-4 flex flex-col items-start gap-3">{children}</div>
    </div>
  );
}
