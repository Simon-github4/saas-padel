import { useId } from 'react';
import { BRAND, BRAND_PARTS } from '../pages/marketing/config';

/**
 * Logo de TurnosPadel: la lupa con una pelota adentro (se busca turno) y el
 * nombre en dos tonos.
 *
 * <p>Va en SVG y con la Archivo que ya carga la página, no como imagen: se ve
 * nítido en cualquier tamaño y el texto no se desdibuja. Todo se mide en em,
 * así que el tamaño lo pone el text-* de quien lo usa.
 */
export function BrandLogo({ className = '' }: { className?: string }) {
  const [first, second] = BRAND_PARTS;
  return (
    <span
      aria-label={BRAND}
      role="img"
      className={`inline-flex items-center gap-[0.04em] font-sans leading-none font-extrabold tracking-[-0.02em] normal-case ${className}`}
    >
      <BrandIcon className="size-[1.65em] shrink-0 translate-y-[0.08em]" />
      <span aria-hidden>
        <span className="text-cal">{first}</span>
        <span className="text-ladrillo">{second}</span>
      </span>
    </span>
  );
}

/**
 * La lupa sola: aro y mango en durazno, la pelota en ladrillo con sus dos
 * costuras. Las costuras son círculos recortados por la pelota, así terminan
 * justo en el borde y no asoman sobre el hueco que la separa del aro.
 */
export function BrandIcon({ className = '' }: { className?: string }) {
  const clip = `pelota-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  return (
    <svg viewBox="0 0 92 92" aria-hidden className={className}>
      <defs>
        <clipPath id={clip}>
          <circle cx="40" cy="40" r="24" />
        </clipPath>
      </defs>
      <g fill={PEACH} transform="rotate(45 40 40)">
        <rect x="72" y="37.25" width="12" height="5.5" />
        <rect x="82" y="35.2" width="25.5" height="9.6" rx="3.2" />
      </g>
      <circle cx="40" cy="40" r="31.85" fill="none" stroke={PEACH} strokeWidth="8.3" />
      <circle cx="40" cy="40" r="24" fill="#ea580c" />
      <g clipPath={`url(#${clip})`} fill="none" stroke="#fff4ec" strokeWidth="2.4">
        <circle cx="64" cy="16" r="22.8" />
        <circle cx="16" cy="64" r="22.8" />
      </g>
    </svg>
  );
}

/** El aro de la lupa: más claro que el ladrillo para que la pelota se despegue. */
const PEACH = '#f5a67b';
