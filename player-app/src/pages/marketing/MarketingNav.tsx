import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { WhatsappGlyph } from '../../components/Ui';
import { BRAND, SALES_EMAIL, salesWhatsappHref } from './config';
import { CONTAINER } from './motion';

const ANCHORS = [
  { href: '#probalo', label: 'Probalo' },
  { href: '#funciones', label: 'Qué hace' },
  { href: '#empezar', label: 'Cómo empezar' },
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
 * Barra superior de la landing: marca, anclas a las secciones y CTA de venta.
 *
 * <p>Arriba de todo es transparente, para que la portada respire; al bajar se
 * vuelve sólida. La sección visible queda marcada.
 *
 * <p>Debajo de `lg` no entra todo, y lo que sobra no se puede esconder y ya: el
 * dueño que entra desde el celular se quedaba sin las anclas, sin la salida del
 * jugador y sin el acceso a su propio panel -- le quedaba la marca y "Hablemos".
 * Así que ahí lo que sobra pasa a un menú, y "Hablemos" se queda afuera porque
 * es a lo que vino esta página.
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
          className="display group flex shrink-0 items-center gap-2.5 text-xl tracking-[0.14em]"
        >
          <span
            aria-hidden
            className="size-2.5 rounded-full bg-ladrillo transition-transform duration-500 group-hover:scale-150"
          />
          {BRAND}
        </a>

        <nav aria-label="Secciones" className="hidden items-center gap-1 lg:flex">
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

        <div className="flex shrink-0 items-center gap-2 sm:gap-4">
          <Link
            to="/buscar"
            className="hidden text-xs font-semibold uppercase tracking-[0.12em] text-ink-soft transition hover:text-cal lg:inline"
          >
            Reservar cancha
          </Link>
          {/* Acceso rápido para el dueño que ya es cliente: sin este link tiene
              que bajar hasta el pie para entrar a su panel.
              /admin vive en el backend (panel Vaadin), no en esta SPA: tiene que
              ser una navegacion de pagina completa, no un Link de React Router
              (que caeria en el catch-all y te devolveria a esta misma landing). */}
          <a
            href="/admin"
            className="hidden text-xs font-semibold uppercase tracking-[0.12em] text-ink-soft transition hover:text-cal lg:inline"
          >
            Soy club
          </a>
          <a
            href={salesWhatsappHref()}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-2 rounded-full bg-ladrillo px-4 py-2.5 text-xs font-bold uppercase tracking-[0.12em] text-cal transition duration-300 hover:-translate-y-px hover:bg-ladrillo/90 sm:px-5 [box-shadow:var(--shadow-glow)]"
          >
            <WhatsappGlyph className="size-3.5" />
            Hablemos
          </a>

          <button
            type="button"
            onClick={() => setAbierto(!abierto)}
            aria-expanded={abierto}
            aria-controls="menu-landing"
            aria-label={abierto ? 'Cerrar menú' : 'Abrir menú'}
            className="relative grid size-10 shrink-0 place-items-center rounded-full border border-cal/10 text-ink-soft transition hover:border-cal/25 hover:text-cal lg:hidden"
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
        className={`grid transition-[grid-template-rows] duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] lg:hidden ${
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
            <div className="flex gap-6 py-4">
              <Link
                to="/buscar"
                onClick={() => setAbierto(false)}
                className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-soft transition hover:text-cal"
              >
                Reservar cancha
              </Link>
              {/* Pagina completa, no Link: /admin es el panel Vaadin del backend. */}
              <a
                href="/admin"
                className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-soft transition hover:text-cal"
              >
                Soy club
              </a>
            </div>
          </div>
        </nav>
      </div>
    </header>
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
          <p className="display flex items-center gap-2.5 text-2xl tracking-[0.14em]">
            <span aria-hidden className="size-2.5 rounded-full bg-ladrillo" />
            {BRAND}
          </p>
          <p className="mt-3 max-w-xs text-sm text-ink-mute">Reservas online para tu club de pádel.</p>
        </div>
        <FooterColumn title="Jugadores">
          <Link to="/buscar" className={`${linkClass} font-semibold text-cal`}>
            Reservar cancha
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
