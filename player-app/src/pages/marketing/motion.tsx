import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';

/** Ancho y márgenes de la columna de la landing, iguales en todas las secciones. */
export const CONTAINER = 'mx-auto w-full max-w-6xl px-5 md:px-8';

/** Delay de una animación de landing.css, que lo lee de --mk-delay. */
export function delay(ms: number): CSSProperties {
  return { '--mk-delay': `${ms}ms` } as CSSProperties;
}

export function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(
    () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  );
  useEffect(() => {
    const query = window.matchMedia('(prefers-reduced-motion: reduce)');
    const onChange = () => setReduced(query.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);
  return reduced;
}

/**
 * Si el elemento está (o estuvo) en pantalla.
 *
 * <p>Con `once` se queda en true la primera vez: lo que ya apareció no vuelve
 * a esconderse al pasar de largo. Sin `once` sirve para pausar lo que se mueve
 * solo mientras nadie lo está mirando.
 */
export function useInView<T extends Element>({
  once = true,
  rootMargin = '0px 0px -10% 0px',
}: { once?: boolean; rootMargin?: string } = {}) {
  const ref = useRef<T>(null);
  const [inView, setInView] = useState(false);

  useEffect(() => {
    const element = ref.current;
    if (!element) {
      return;
    }
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        setInView(true);
        if (once) {
          observer.disconnect();
        }
      } else if (!once) {
        setInView(false);
      }
    }, { rootMargin });
    observer.observe(element);
    return () => observer.disconnect();
  }, [once, rootMargin]);

  return [ref, inView] as const;
}

/** Aparece subiendo cuando entra en pantalla. */
export function Reveal({
  children,
  delay: ms = 0,
  className = '',
}: {
  children: ReactNode;
  delay?: number;
  className?: string;
}) {
  const [ref, shown] = useInView<HTMLDivElement>();
  return (
    <div ref={ref} data-shown={shown || undefined} className={`mk-reveal ${className}`} style={delay(ms)}>
      {children}
    </div>
  );
}

/** Encabezado de sección: rótulo, título grande y bajada opcional. */
export function SectionHeading({
  eyebrow,
  title,
  lead,
  center = false,
}: {
  eyebrow: string;
  title: ReactNode;
  lead?: ReactNode;
  center?: boolean;
}) {
  return (
    <Reveal className={center ? 'mx-auto max-w-3xl text-center' : 'max-w-3xl'}>
      <p className={`eyebrow flex items-center gap-3 text-ladrillo-claro ${center ? 'justify-center' : ''}`}>
        <span aria-hidden className="h-px w-6 bg-ladrillo-claro/60" />
        {eyebrow}
      </p>
      <h2 className="mt-5 text-[clamp(2.5rem,6vw,4.25rem)] tracking-[0.02em] text-balance">{title}</h2>
      {lead && (
        <p
          className={`mt-5 max-w-xl text-base leading-relaxed text-ink-soft text-pretty md:text-lg ${center ? 'mx-auto' : ''}`}
        >
          {lead}
        </p>
      )}
    </Reveal>
  );
}

/**
 * Cancha de pádel vista desde arriba, en proporción real (20 × 10 m): paredes,
 * red al medio, líneas de saque a 6,95 m de la red y la central entre ellas.
 * Se dibuja trazo por trazo cuando el contenedor recibe data-shown.
 */
export function CourtLines({ className = '' }: { className?: string }) {
  const stroke = { strokeWidth: 0.35, pathLength: 1, className: 'mk-draw' } as const;
  return (
    <svg viewBox="-1 -1 202 102" fill="none" stroke="currentColor" aria-hidden className={className}>
      <rect x="0" y="0" width="200" height="100" rx="0.5" {...stroke} style={delay(0)} />
      <line x1="100" y1="-1" x2="100" y2="101" {...stroke} strokeWidth={0.7} style={delay(350)} />
      <line x1="30.5" y1="0" x2="30.5" y2="100" {...stroke} style={delay(600)} />
      <line x1="169.5" y1="0" x2="169.5" y2="100" {...stroke} style={delay(600)} />
      <line x1="30.5" y1="50" x2="169.5" y2="50" {...stroke} style={delay(850)} />
    </svg>
  );
}

export function ArrowGlyph({ className = '' }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      className={className}
    >
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  );
}

export function CheckGlyph({ className = '' }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      className={className}
    >
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </svg>
  );
}
