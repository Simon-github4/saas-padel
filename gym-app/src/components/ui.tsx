import type { ReactNode } from 'react';

/**
 * Piezas visuales de la app. Mismo lenguaje que la app del jugador (fondo casi
 * negro, el ladrillo como unico acento), pero propias: esta app no importa nada
 * de aquella.
 */

export function Screen({ children, top }: { children: ReactNode; top?: ReactNode }) {
  return (
    <div className="min-h-dvh overflow-x-clip bg-pista">
      {top}
      <main className="mx-auto w-full max-w-lg px-4 pb-16 pt-6">{children}</main>
    </div>
  );
}

/** Barra fija con el nombre del club, que es lo unico que el socio necesita ver siempre. */
export function TopBar({ name, action }: { name: string; action?: ReactNode }) {
  return (
    <header className="sticky top-0 z-30 border-b border-borde-suave bg-pista/90 pt-[env(safe-area-inset-top)] backdrop-blur">
      <div className="mx-auto flex h-14 w-full max-w-lg items-center justify-between gap-3 px-4">
        <p className="display truncate text-xl tracking-[0.14em]">{name}</p>
        {action}
      </div>
    </header>
  );
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-2xl border border-borde bg-vidrio p-5 [box-shadow:var(--shadow-card)] ${className}`}>
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
  variant?: 'primary' | 'accent' | 'secondary';
  disabled?: boolean;
  type?: 'button' | 'submit';
  className?: string;
}) {
  const styles = {
    primary: 'bg-cal text-pista hover:bg-arena',
    accent: 'bg-ladrillo text-cal hover:bg-ladrillo/90 [box-shadow:var(--shadow-glow)]',
    secondary: 'border border-borde bg-vidrio text-cal hover:border-cal/30 hover:bg-vidrio-alto',
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
  type = 'text',
  hint,
  inputMode,
  autoComplete,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  hint?: string;
  inputMode?: 'text' | 'numeric';
  autoComplete?: string;
  placeholder?: string;
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
        className="w-full rounded-xl border border-borde-suave bg-pista px-4 py-3 text-base text-cal outline-none transition placeholder:text-ink-mute focus:border-ladrillo focus:ring-2 focus:ring-ladrillo/25"
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
    error: 'border-red-500/30 bg-red-500/10 text-red-700',
    info: 'border-cal/10 bg-cal/[0.04] text-ink-soft',
    success: 'border-emerald-600/30 bg-emerald-500/10 text-emerald-700',
  }[tone];

  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={`rounded-xl border px-4 py-3 text-sm ${styles}`}>
      {children}
    </div>
  );
}

export function Loading({ label = 'Cargando…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-ink-soft">
      <span className="size-5 animate-spin rounded-full border-2 border-cal/25 border-t-ladrillo" />
      {label}
    </div>
  );
}

export function Badge({
  children,
  tone = 'neutral',
}: {
  children: ReactNode;
  tone?: 'neutral' | 'success' | 'warning' | 'danger';
}) {
  const styles = {
    neutral: 'border-cal/10 bg-cal/[0.06] text-ink-soft',
    success: 'border-emerald-600/30 bg-emerald-500/10 text-emerald-700',
    warning: 'border-ladrillo/40 bg-ladrillo/15 text-[#a13d06]',
    danger: 'border-rose-500/30 bg-rose-500/10 text-rose-700',
  }[tone];

  return <span className={`eyebrow inline-flex items-center rounded-full border px-2.5 py-1 ${styles}`}>{children}</span>;
}

/** Los dias de la semana como circulos: llenos los usados, vacios los que quedan. */
export function WeekDots({ used, limit }: { used: number; limit: number }) {
  return (
    <div className="flex gap-2" role="img" aria-label={`${used} de ${limit} días usados esta semana`}>
      {Array.from({ length: limit }, (_, index) => (
        <span
          key={index}
          className={`size-4 rounded-full border-2 ${
            index < used ? 'border-ladrillo bg-ladrillo' : 'border-cal/25'
          }`}
        />
      ))}
    </div>
  );
}
