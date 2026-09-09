import { Link } from 'react-router-dom';
import { BRAND, salesWhatsappHref } from './config';

const ANCHORS = [
  { href: '#funciones', label: 'Qué hace' },
  { href: '#panel', label: 'El panel' },
  { href: '#empezar', label: 'Cómo empezar' },
  { href: '#precio', label: 'Precio' },
];

/** Barra superior de la landing: marca, anclas a las secciones y CTA de venta. */
export function MarketingNav() {
  return (
    <header className="sticky top-0 z-30 border-b border-cal/10 bg-pista/90 backdrop-blur">
      <div className="mx-auto flex h-14 w-full max-w-5xl items-center justify-between gap-4 px-4 md:h-16">
        <a href="#top" className="display shrink-0 text-xl tracking-[0.14em]">
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
            className="hidden text-xs font-semibold uppercase tracking-[0.12em] text-ink-soft transition hover:text-cal sm:inline"
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
            className="hidden text-xs font-semibold uppercase tracking-[0.12em] text-ink-soft transition hover:text-cal sm:inline"
          >
            Entrar al panel
          </a>
          <a
            href={salesWhatsappHref()}
            target="_blank"
            rel="noreferrer"
            className="rounded-full bg-ladrillo px-5 py-2.5 text-xs font-bold uppercase tracking-[0.12em] text-cal transition hover:bg-ladrillo/90 [box-shadow:var(--shadow-glow)]"
          >
            Hablemos
          </a>
        </div>
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
