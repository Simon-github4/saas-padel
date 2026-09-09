import { Link } from 'react-router-dom';
import { SALES_EMAIL, salesWhatsappHref } from './config';

/** Cierre de la landing: el mismo par de contactos que la barra, sin más rodeos. */
export function ClosingSection() {
  return (
    <section className="mx-[calc(50%-50vw)] border-y border-cal/10 bg-ladrillo/[0.04] py-16 text-center md:py-20">
      <div className="mx-auto w-full max-w-2xl px-4">
        <h2 className="text-3xl md:text-4xl">Hablemos de tu club</h2>
        <p className="mx-auto mt-4 max-w-md text-ink-soft">
          Contanos cómo trabajás hoy y vemos juntos si el sistema te sirve. Sin
          compromiso.
        </p>
        <p className="mt-3 text-sm text-ink-soft">
          ¿Preferís verlo antes?{' '}
          <Link
            to="/club/club-necochea"
            target="_blank"
            className="font-semibold text-ladrillo-claro underline-offset-4 hover:underline"
          >
            Mirá un club real funcionando →
          </Link>
        </p>
        <div className="mt-8 flex flex-col items-center justify-center gap-4 sm:flex-row">
          <a
            href={salesWhatsappHref()}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-2 rounded-full bg-ladrillo px-10 py-4 text-sm font-bold uppercase tracking-[0.12em] text-cal transition hover:bg-ladrillo/90 [box-shadow:var(--shadow-glow)]"
          >
            Escribinos por WhatsApp
            <span aria-hidden>→</span>
          </a>
          <a
            href={`mailto:${SALES_EMAIL}`}
            className="text-sm font-semibold uppercase tracking-[0.12em] text-ink-soft transition hover:text-cal"
          >
            {SALES_EMAIL}
          </a>
        </div>
      </div>
    </section>
  );
}
