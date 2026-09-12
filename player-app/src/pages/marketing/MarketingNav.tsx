import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { BRAND, salesWhatsappHref } from './config';

const ANCHORS = [
  { href: '#funciones', label: 'Qué hace' },
  { href: '#panel', label: 'El panel' },
  { href: '#empezar', label: 'Cómo empezar' },
  { href: '#precio', label: 'Precio' },
];

/**
 * Barra superior de la landing: marca, anclas a las secciones y CTA de venta.
 *
 * <p>Debajo de `lg` no entra todo, y lo que sobra no se puede esconder y ya: el
 * dueño que entra desde el celular se quedaba sin las anclas, sin la salida del
 * jugador y sin el acceso a su propio panel -- le quedaba la marca y "Hablemos".
 * Así que ahí lo que sobra pasa a un menú, y "Hablemos" se queda afuera porque
 * es a lo que vino esta página.
 */
export function MarketingNav() {
  const [abierto, setAbierto] = useState(false);

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

  return (
    <header className="sticky top-0 z-30 border-b border-cal/10 bg-pista/90 backdrop-blur">
      <div className="mx-auto flex h-14 w-full max-w-5xl items-center justify-between gap-4 px-4 md:h-16">
        <a
          href="#top"
          onClick={() => setAbierto(false)}
          className="display shrink-0 text-xl tracking-[0.14em]"
        >
          {BRAND}
        </a>

        <nav className="hidden items-center gap-6 text-sm text-ink-soft lg:flex">
          {ANCHORS.map((anchor) => (
            <a key={anchor.href} href={anchor.href} className="transition hover:text-cal">
              {anchor.label}
            </a>
          ))}
        </nav>

        <div className="flex shrink-0 items-center gap-3">
          <Link
            to="/buscar"
            className="hidden text-xs font-semibold uppercase tracking-[0.12em] text-ink-soft transition hover:text-cal lg:inline"
          >
            Soy jugador
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
            Entrar al panel
          </a>
          <a
            href={salesWhatsappHref()}
            target="_blank"
            rel="noreferrer"
            className="rounded-full bg-ladrillo px-4 py-2.5 text-xs font-bold uppercase tracking-[0.12em] text-cal transition hover:bg-ladrillo/90 sm:px-5 [box-shadow:var(--shadow-glow)]"
          >
            Hablemos
          </a>

          <button
            type="button"
            onClick={() => setAbierto(!abierto)}
            aria-expanded={abierto}
            aria-controls="menu-landing"
            aria-label={abierto ? 'Cerrar menú' : 'Abrir menú'}
            className="grid size-9 shrink-0 place-items-center rounded-full border border-cal/10 text-ink-soft transition hover:border-cal/25 hover:text-cal lg:hidden"
          >
            {abierto ? <CloseGlyph className="size-4" /> : <MenuGlyph className="size-4" />}
          </button>
        </div>
      </div>

      {/*
        Se despliega debajo de la barra y dentro del mismo header, que es
        sticky: así queda pegado a la barra al abrirlo desde cualquier punto
        del scroll, en vez de aparecer arriba de todo.
      */}
      {abierto && (
        <nav
          id="menu-landing"
          className="border-t border-cal/10 bg-pista lg:hidden"
        >
          <div className="mx-auto flex w-full max-w-5xl flex-col px-4 py-2">
            {ANCHORS.map((anchor) => (
              <a
                key={anchor.href}
                href={anchor.href}
                onClick={() => setAbierto(false)}
                className="border-b border-cal/[0.06] py-3 text-sm text-ink-soft transition hover:text-cal"
              >
                {anchor.label}
              </a>
            ))}
            <Link
              to="/buscar"
              onClick={() => setAbierto(false)}
              className="border-b border-cal/[0.06] py-3 text-xs font-semibold uppercase tracking-[0.12em] text-ink-soft transition hover:text-cal"
            >
              Soy jugador
            </Link>
            {/* Pagina completa, no Link: /admin es el panel Vaadin del backend. */}
            <a
              href="/admin"
              className="py-3 text-xs font-semibold uppercase tracking-[0.12em] text-ink-soft transition hover:text-cal"
            >
              Entrar al panel
            </a>
          </div>
        </nav>
      )}
    </header>
  );
}

function MenuGlyph({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
      className={className}
    >
      <path d="M4 7h16M4 12h16M4 17h16" />
    </svg>
  );
}

function CloseGlyph({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
      className={className}
    >
      <path d="M6 6l12 12M18 6L6 18" />
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
  return (
    <footer className="border-t border-cal/10 py-10 text-center">
      <p className="display text-xl tracking-[0.14em]">{BRAND}</p>
      <div className="mt-5 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-sm">
        <Link to="/buscar" className="font-semibold text-ink-soft transition hover:text-cal">
          Soy jugador · Buscar cancha
        </Link>
        {/* Pagina completa, no Link: /admin es el panel Vaadin del backend. */}
        <a href="/admin" className="text-ink-mute transition hover:text-ink-soft">
          Entrar al panel
        </a>
        <Link to="/terminos" className="text-ink-mute transition hover:text-ink-soft">
          Términos de uso
        </Link>
        <Link to="/privacidad" className="text-ink-mute transition hover:text-ink-soft">
          Privacidad
        </Link>
      </div>
      <p className="eyebrow mt-6 text-ink-mute">Reservas online para tu club</p>
    </footer>
  );
}
