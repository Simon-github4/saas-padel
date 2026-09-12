import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

/**
 * Piezas visuales compartidas, para que las pantallas se ocupen del flujo.
 *
 * <p>La página es casi toda negra: una sola pieza clara (el precio). El ladrillo
 * es el acento del club — CTA de la portada, pagar la seña, promociones,
 * precios y foco — y el verde solo aparece en WhatsApp.
 */

export function Screen({
  children,
  top,
  className = '',
}: {
  children: ReactNode;
  /** Barra superior a sangre, fuera de la columna de contenido. */
  top?: ReactNode;
  className?: string;
}) {
  return (
    // overflow-x clip y no hidden: los bloques a sangre (la portada, la zona de
    // reserva) se estiran con 50vw, que incluye el ancho de la barra de scroll y
    // deja unos pixeles de sobra a la derecha. "clip" los recorta sin crear un
    // contenedor de scroll, que es lo que romperia el sticky de la barra.
    <div className="min-h-dvh overflow-x-clip bg-pista">
      {top}
      <div className={`mx-auto w-full max-w-lg px-4 pb-28 md:max-w-2xl ${className}`}>{children}</div>
    </div>
  );
}

/**
 * Barra superior fija: el nombre del club y el WhatsApp siempre a mano, que es
 * lo único que el jugador puede necesitar en cualquier punto del scroll.
 */
export function TopBar({
  name,
  whatsappHref,
  accountSlot,
  titleTo,
}: {
  name: string;
  whatsappHref?: string;
  /** Botón de "Mis turnos" / entrar, armado afuera: este componente no sabe de sesiones. */
  accountSlot?: ReactNode;
  /**
   * A dónde lleva tocar el título. Sin esto el título es texto, no un botón.
   *
   * <p>Se pasa sólo cuando lleva a otra pantalla. Estando ya en el destino hay
   * que omitirlo: un título que parece botón y navega a la página donde ya
   * estás no hace nada, y lo que el jugador aprende de eso es a no tocarlo.
   */
  titleTo?: string;
}) {
  return (
    <header className="sticky top-0 z-30 border-b border-cal/10 bg-pista/90 backdrop-blur">
      <div className="mx-auto flex h-14 w-full max-w-lg items-center justify-between gap-3 px-4 md:h-16 md:max-w-2xl">
        {titleTo ? (
          <Link
            to={titleTo}
            className="display cursor-pointer truncate text-left text-xl tracking-[0.14em] transition hover:text-ink-soft"
          >
            {name}
          </Link>
        ) : (
          <p className="display truncate text-xl tracking-[0.14em]">{name}</p>
        )}
        <div className="flex shrink-0 items-center gap-2">
          {accountSlot}
          {whatsappHref && (
            <a
              href={whatsappHref}
              target="_blank"
              rel="noreferrer"
              aria-label="Escribirle al club por WhatsApp"
              className="grid size-9 shrink-0 place-items-center rounded-full border border-cal/10 text-ink-soft transition hover:border-cal/25 hover:text-cal"
            >
              <WhatsappGlyph className="size-4" />
            </a>
          )}
        </div>
      </div>
    </header>
  );
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={`rounded-2xl border border-cal/10 bg-vidrio p-5 [box-shadow:var(--shadow-card)] ${className}`}
    >
      {children}
    </div>
  );
}

export function Button({
  children,
  onClick,
  variant = 'primary',
  disabled,
  type = 'button',
  className = '',
}: {
  children: ReactNode;
  onClick?: () => void;
  /** primary es la pastilla blanca; accent, el ladrillo de pagar. */
  variant?: 'primary' | 'accent' | 'secondary' | 'danger';
  disabled?: boolean;
  type?: 'button' | 'submit';
  className?: string;
}) {
  const styles = {
    primary: 'bg-cal text-pista hover:bg-arena',
    accent: 'bg-ladrillo text-cal hover:bg-ladrillo/90 [box-shadow:var(--shadow-glow)]',
    secondary: 'border border-cal/10 bg-vidrio text-cal hover:border-cal/25 hover:bg-vidrio-alto',
    danger: 'border border-red-500/25 bg-red-500/10 text-red-300 hover:bg-red-500/15',
  }[variant];

  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={`w-full rounded-full px-5 py-3.5 text-sm font-bold uppercase tracking-[0.12em] transition disabled:cursor-not-allowed disabled:border-transparent disabled:bg-cal/[0.06] disabled:text-ink-mute disabled:[box-shadow:none] ${styles} ${className}`}
    >
      {children}
    </button>
  );
}

export function Field({
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
  hint,
  inputMode,
  autoComplete,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  type?: string;
  hint?: string;
  inputMode?: 'text' | 'tel' | 'numeric';
  autoComplete?: string;
}) {
  return (
    <label className="block">
      <span className="eyebrow mb-2 block text-ink-soft">{label}</span>
      <input
        type={type}
        value={value}
        inputMode={inputMode}
        autoComplete={autoComplete}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        className="w-full rounded-xl border border-cal/10 bg-pista px-4 py-3 text-base text-cal outline-none transition placeholder:text-ink-mute focus:border-ladrillo focus:ring-2 focus:ring-ladrillo/25"
      />
      {hint && <span className="mt-1.5 block text-xs text-ink-soft">{hint}</span>}
    </label>
  );
}

export function Alert({
  children,
  tone = 'error',
}: {
  children: ReactNode;
  tone?: 'error' | 'info' | 'success';
}) {
  const styles = {
    error: 'border-red-500/25 bg-red-500/10 text-red-300',
    info: 'border-cal/10 bg-cal/[0.04] text-ink-soft',
    success: 'border-emerald-500/25 bg-emerald-500/10 text-emerald-300',
  }[tone];

  return <div className={`rounded-xl border px-4 py-3 text-sm ${styles}`}>{children}</div>;
}

export function Loading({ label = 'Cargando…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-ink-soft">
      <span className="size-5 animate-spin rounded-full border-2 border-cal/15 border-t-ladrillo" />
      {label}
    </div>
  );
}

export function WhatsappLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noreferrer"
      className="inline-flex w-full items-center justify-center gap-2 rounded-full bg-wapp px-5 py-3.5 text-sm font-bold uppercase tracking-[0.12em] text-pista transition hover:bg-wapp/90"
    >
      <WhatsappGlyph className="size-4" />
      {children}
    </a>
  );
}

/** Etiqueta corta: estado, promoción o tipo. */
export function Badge({
  children,
  tone = 'neutral',
}: {
  children: ReactNode;
  tone?: 'promo' | 'neutral' | 'success';
}) {
  const styles = {
    promo: 'border-ladrillo/30 bg-ladrillo/15 text-ladrillo-claro',
    neutral: 'border-cal/10 bg-cal/[0.06] text-ink-soft',
    success: 'border-emerald-500/25 bg-emerald-500/10 text-emerald-300',
  }[tone];

  return (
    <span className={`eyebrow inline-flex items-center rounded-full border px-2 py-0.5 ${styles}`}>
      {children}
    </span>
  );
}

/**
 * Título de sección con número de paso.
 *
 * <p>La numeración no es decorativa: la reserva es una secuencia real y el
 * número le dice al jugador cuánto le falta.
 */
export function SectionTitle({
  step,
  title,
  subtitle,
}: {
  step?: number;
  title: string;
  subtitle?: string;
}) {
  return (
    <div>
      <h2 className="flex items-center gap-3 text-2xl">
        {step && (
          <span className="grid size-7 shrink-0 place-items-center rounded-full bg-cal pt-0.5 text-sm text-pista">
            {step}
          </span>
        )}
        {title}
      </h2>
      {subtitle && <p className="mt-2 text-sm text-ink-soft">{subtitle}</p>}
    </div>
  );
}

const STEPS = ['Día', 'Hora', 'Datos'];

/**
 * Avance del flujo de reserva.
 *
 * <p>Los pasos ya cumplidos son botones: volver atrás no debería depender de
 * encontrar un link escondido dentro de la grilla.
 */
export function StepIndicator({
  current,
  onGo,
}: {
  current: number;
  onGo?: (step: number) => void;
}) {
  return (
    <ol className="flex items-start">
      {STEPS.map((label, index) => {
        const step = index + 1;
        const done = step < current;
        const active = step === current;
        const circle = `grid size-8 shrink-0 place-items-center rounded-full text-xs font-bold transition ${
          active
            ? 'bg-cal text-pista'
            : done
              ? 'bg-cal/10 text-cal hover:bg-cal/20'
              : 'border border-cal/10 text-ink-mute'
        }`;

        return (
          <li key={label} className={`flex items-start ${step < STEPS.length ? 'flex-1' : ''}`}>
            <div className="flex w-16 shrink-0 flex-col items-center gap-2">
              {done && onGo ? (
                <button
                  type="button"
                  onClick={() => onGo(step)}
                  aria-label={`Volver al paso ${step}: ${label}`}
                  className={circle}
                >
                  ✓
                </button>
              ) : (
                <span className={circle} aria-current={active ? 'step' : undefined}>
                  {done ? '✓' : step}
                </span>
              )}
              <span
                className={`eyebrow text-center ${
                  active ? 'text-cal' : done ? 'text-ink-soft' : 'text-ink-mute'
                }`}
              >
                {label}
              </span>
            </div>
            {step < STEPS.length && <span className="mt-4 h-px flex-1 bg-cal/15" />}
          </li>
        );
      })}
    </ol>
  );
}

/** Resumen del turno antes de confirmar, para el paso 3. */
export function SummaryCard({ rows }: { rows: { label: string; value: ReactNode }[] }) {
  return (
    <Card className="!p-4">
      <dl className="divide-y divide-cal/[0.08]">
        {rows.map((row) => (
          <div
            key={row.label}
            className="flex items-baseline justify-between gap-4 py-2.5 first:pt-0 last:pb-0"
          >
            <dt className="text-sm text-ink-soft">{row.label}</dt>
            <dd className="text-right font-semibold tabular-nums">{row.value}</dd>
          </div>
        ))}
      </dl>
    </Card>
  );
}

/** Estado de un turno, con el mismo mapeo de color en el detalle y en el historial. */
export function StatusBadge({ status }: { status: string }) {
  const neutral = 'border-cal/10 bg-cal/[0.06] text-ink-soft';
  const map: Record<string, { text: string; className: string }> = {
    CONFIRMED: {
      text: 'Confirmado',
      className: 'border-emerald-500/25 bg-emerald-500/10 text-emerald-300',
    },
    AWAITING_CONFIRMATION: {
      text: 'Sin confirmar',
      className: 'border-amber-500/25 bg-amber-500/10 text-amber-300',
    },
    DRAFT: {
      text: 'Esperando pago',
      className: 'border-amber-500/25 bg-amber-500/10 text-amber-300',
    },
    COMPLETED: { text: 'Jugado', className: neutral },
    CANCELLED: { text: 'Cancelado', className: 'border-red-500/25 bg-red-500/10 text-red-300' },
    NO_SHOW: {
      text: 'No te presentaste',
      className: 'border-red-500/25 bg-red-500/10 text-red-300',
    },
  };
  const badge = map[status] ?? { text: status, className: neutral };

  return (
    <span className={`eyebrow inline-flex rounded-full border px-2.5 py-1 ${badge.className}`}>
      {badge.text}
    </span>
  );
}

/** Pastilla de filtro. Es un toggle, no un link: no navega, acota lo que se ve. */
export function Chip({
  children,
  active,
  onClick,
}: {
  children: ReactNode;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`rounded-full border px-3.5 py-2 text-sm transition ${
        active
          ? 'border-ladrillo/50 bg-ladrillo/15 font-bold text-ladrillo-claro'
          : 'border-cal/10 bg-vidrio text-ink-soft hover:border-cal/25 hover:text-cal'
      }`}
    >
      {children}
    </button>
  );
}

/** Botón fijo de WhatsApp, presente en toda la portada. */
export function FloatingWhatsapp({ href }: { href: string }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noreferrer"
      aria-label="Escribirle al club por WhatsApp"
      className="fixed bottom-5 right-5 z-40 flex size-14 items-center justify-center rounded-full bg-wapp text-pista ring-1 ring-cal/10 transition hover:bg-wapp/90"
    >
      <WhatsappGlyph className="size-7" />
    </a>
  );
}

/**
 * Marca de WhatsApp.
 *
 * <p>Es el trazo oficial (Simple Icons, CC0) y no un dibujo aproximado: el
 * jugador reconoce este ícono antes de leer nada, y una silueta parecida pero
 * distinta lo hace dudar justo en el botón que más se toca.
 */
function WhatsappGlyph({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="currentColor" aria-hidden>
      <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z" />
    </svg>
  );
}

/**
 * Cierre de la página.
 *
 * <p>Antes terminaba de golpe después de "Cómo llegar". Es un remate callado a
 * propósito: sin botón de WhatsApp, porque el flotante está fijo justo encima y
 * la barra de arriba ya tiene otro. Tres accesos al mismo chat en la misma
 * esquina no ayudan a nadie.
 */
/**
 * Cierre de las pantallas que pertenecen a un club.
 *
 * <p>Lleva el link a la búsqueda porque es la única salida hacia el resto de la
 * app: a `/manage/:token` se entra por WhatsApp, sin haber pasado por ninguna
 * otra pantalla, y sin esto el jugador que quiere buscar otro turno no tiene
 * por dónde. La raíz no sirve para eso -- es la landing comercial para dueños
 * de club, no el inicio del jugador.
 */
export function SiteFooter({ name, address }: { name: string; address: string | null }) {
  return (
    <footer className="mt-14 border-t border-cal/10 pt-8 text-center">
      <p className="display text-2xl tracking-[0.14em]">{name}</p>
      {address && <p className="mt-2 text-sm text-ink-soft">{address}</p>}
      <Link
        to="/buscar"
        className="eyebrow mt-6 inline-block text-ink-soft underline-offset-4 transition hover:text-cal hover:underline"
      >
        Buscar cancha en otro club
      </Link>
    </footer>
  );
}
