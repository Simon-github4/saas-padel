import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { WhatsappGlyph } from '../../components/Ui';
import { ArrowGlyph } from './motion';

/** El club de ejemplo con datos reales cargados: la prueba de que el sistema anda. */
export const DEMO_CLUB_PATH = '/club/simon';

/** Ícono de WhatsApp en su verde, latiendo (ver .wapp-late en index.css). */
export function WhatsappPulse({ className = '' }: { className?: string }) {
  return (
    <span
      aria-hidden
      className={`wapp-late relative grid shrink-0 place-items-center rounded-full bg-wapp text-white ${className}`}
    >
      <WhatsappGlyph className="size-[62%]" />
    </span>
  );
}

/** Botón ladrillo que abre el WhatsApp de ventas. */
export function WhatsappCta({
  href,
  children,
  size = 'lg',
  className = '',
}: {
  href: string;
  children: ReactNode;
  size?: 'md' | 'lg';
  className?: string;
}) {
  const sizing =
    size === 'lg' ? 'py-4 pl-4 pr-6 text-[0.8125rem] sm:pr-7 sm:text-sm' : 'py-3.5 pl-3.5 pr-5 text-xs';
  return (
    <a
      href={href}
      target="_blank"
      rel="noreferrer"
      className={`group inline-flex items-center justify-center gap-2.5 text-center sm:whitespace-nowrap rounded-full bg-ladrillo font-bold uppercase tracking-[0.08em] text-cal sm:tracking-[0.12em] transition duration-300 hover:-translate-y-0.5 hover:bg-ladrillo/90 active:translate-y-0 [box-shadow:var(--shadow-glow)] ${sizing} ${className}`}
    >
      <WhatsappPulse className={size === 'lg' ? '-my-1.5 size-7' : '-my-1 size-6'} />
      {children}
      <ArrowGlyph className="hidden size-4 shrink-0 transition-transform duration-300 group-hover:translate-x-1 sm:block" />
    </a>
  );
}

/**
 * Link con borde al club de ejemplo, en pestaña nueva para no perder la landing.
 */
export function DemoClubCta({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <Link
      to={DEMO_CLUB_PATH}
      target="_blank"
      className={`group inline-flex items-center justify-center gap-2.5 text-center sm:whitespace-nowrap rounded-full border border-cal/15 px-6 py-4 text-[0.8125rem] font-bold uppercase tracking-[0.08em] text-cal sm:px-7 sm:text-sm sm:tracking-[0.12em] transition duration-300 hover:border-cal/40 hover:bg-cal/[0.04] ${className}`}
    >
      {children}
      <ArrowGlyph className="size-4 shrink-0 -rotate-45 transition-transform duration-300 group-hover:rotate-0" />
    </Link>
  );
}
