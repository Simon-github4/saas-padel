import { useEffect, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Screen, SectionTitle } from '../../components/Ui';
import { BRAND } from '../marketing/config';

/**
 * Chrome compartido de las páginas legales: mismo título, misma fecha, mismo
 * link cruzado al otro documento. El contenido de cada uno es lo único que
 * cambia entre Términos y Privacidad.
 */
export function LegalLayout({
  title,
  updated,
  otherHref,
  otherLabel,
  children,
}: {
  title: string;
  updated: string;
  otherHref: string;
  otherLabel: string;
  children: ReactNode;
}) {
  const navigate = useNavigate();

  useEffect(() => {
    const previous = document.title;
    document.title = `${title} — ${BRAND}`;
    return () => {
      document.title = previous;
    };
  }, [title]);

  return (
    <Screen className="pt-10 pb-16">
      <button
        type="button"
        onClick={() => navigate(-1)}
        className="mb-6 text-sm font-semibold text-ink-soft transition hover:text-cal"
      >
        ‹ Volver
      </button>

      <SectionTitle title={title} subtitle={`Última actualización: ${updated}`} />

      <div className="mt-8 space-y-8">{children}</div>

      <div className="mt-10 border-t border-cal/10 pt-6 text-sm">
        <Link
          to={otherHref}
          className="font-semibold text-ladrillo-claro underline-offset-4 hover:underline"
        >
          {otherLabel} →
        </Link>
      </div>
    </Screen>
  );
}

/** Un bloque con título corto y su texto. Repetir esto es el documento entero. */
export function LegalSection({ heading, children }: { heading: string; children: ReactNode }) {
  return (
    <section>
      <h2 className="display text-lg tracking-[0.04em]">{heading}</h2>
      <div className="mt-2 space-y-3 text-sm leading-relaxed text-ink-soft">{children}</div>
    </section>
  );
}

/** Lista de puntos con el mismo tilde que ya usa la landing. */
export function LegalList({ items }: { items: ReactNode[] }) {
  return (
    <ul className="space-y-2">
      {items.map((item, index) => (
        <li key={index} className="flex gap-3">
          <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-ladrillo-claro" aria-hidden />
          <span>{item}</span>
        </li>
      ))}
    </ul>
  );
}
