import { Link } from 'react-router-dom';
import { SALES_EMAIL, salesWhatsappHref } from './config';
import { DEMO_CLUB_PATH, WhatsappCta } from './Cta';
import { CourtLines, Reveal, useInView } from './motion';

/**
 * Cierre de la landing: el mismo par de contactos que la barra, sin más rodeos.
 * Detrás, la cancha vista desde arriba se dibuja al llegar, con el llamado
 * parado sobre la red; las líneas se apagan hacia el centro para no cruzar
 * el texto.
 */
export function ClosingSection() {
  const [ref, shown] = useInView<HTMLElement>();

  return (
    <section
      ref={ref}
      data-shown={shown || undefined}
      className="relative isolate overflow-hidden border-t border-cal/10 py-28 text-center md:py-40"
    >
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(50%_60%_at_50%_50%,rgba(234,88,12,0.14),transparent_75%)]"
      />
      <CourtLines className="pointer-events-none absolute left-1/2 top-1/2 -z-10 w-[min(72rem,180%)] -translate-x-1/2 -translate-y-1/2 text-cal/[0.09] [mask-image:radial-gradient(ellipse_50%_55%_at_center,transparent_50%,black_95%)]" />

      <div className="mx-auto w-full max-w-3xl px-5">
        <Reveal>
          <p className="eyebrow text-ladrillo-claro">Sin compromiso</p>
          <h2 className="mt-5 text-[clamp(3.5rem,11vw,7rem)] leading-[0.9] tracking-[0.01em] text-balance">
            Hablemos de tu club
          </h2>
          <p className="mx-auto mt-6 max-w-md text-lg leading-relaxed text-ink-soft">
            Contanos cómo trabajás hoy y vemos juntos si el sistema te sirve.
          </p>
        </Reveal>
        <Reveal delay={150}>
          <div className="mt-10 flex flex-col items-center justify-center gap-5">
            <WhatsappCta href={salesWhatsappHref()}>Escribinos por WhatsApp</WhatsappCta>
            <a
              href={`mailto:${SALES_EMAIL}`}
              className="text-sm font-semibold uppercase tracking-[0.12em] text-ink-soft transition hover:text-cal"
            >
              {SALES_EMAIL}
            </a>
          </div>
          <p className="mt-10 text-sm text-ink-soft">
            ¿Preferís verlo antes?{' '}
            <Link
              to={DEMO_CLUB_PATH}
              target="_blank"
              className="font-semibold text-ladrillo-claro underline-offset-4 hover:underline"
            >
              Mirá un club real funcionando →
            </Link>
          </p>
        </Reveal>
      </div>
    </section>
  );
}
