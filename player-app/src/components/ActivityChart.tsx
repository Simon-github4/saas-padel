import { useState } from 'react';
import { formatHours } from '../format';
import type { Bucket } from '../stats';

/**
 * Las horas jugadas del período, en columnas.
 *
 * <p>Son divs y no un SVG a propósito: un viewBox estirado al ancho disponible
 * deforma las esquinas redondeadas de las barras, y una barra que además es un
 * botón de verdad se toca y se navega con teclado sin trabajo extra.
 *
 * <p>El componente no sabe nada de turnos ni de períodos: recibe las barras
 * hechas y las dibuja.
 */
export function ActivityChart({ buckets }: { buckets: Bucket[] }) {
  const [selected, setSelected] = useState<number | null>(null);

  // Una sola barra no es un gráfico: el número grande de arriba ya lo dice todo.
  if (buckets.length < 2) {
    return null;
  }
  const peakMinutes = Math.max(...buckets.map((bucket) => bucket.minutos));
  if (peakMinutes === 0) {
    return null;
  }

  // El piso de dos horas evita que un único turno suelto se dibuje como una
  // montaña que llena el alto entero.
  const scale = Math.max(peakMinutes, 120);
  const peak = buckets.findIndex((bucket) => bucket.minutos === peakMinutes);
  const detail = selected === null ? null : buckets[selected];

  return (
    <div className="mt-5">
      <div className="flex gap-0.5">
        {buckets.map((bucket, index) => (
          <div
            key={bucket.label + index}
            className={`flex min-w-0 flex-1 ${edgeAlignment(index, buckets.length)}`}
          >
            {index === peak && (
              <span className="eyebrow whitespace-nowrap text-ink-soft">
                {formatHours(bucket.minutos)}
              </span>
            )}
          </div>
        ))}
      </div>

      <div className="mt-1 flex h-24 items-end gap-0.5">
        {buckets.map((bucket, index) => (
          <button
            key={bucket.label + index}
            type="button"
            aria-label={barLabel(bucket)}
            aria-pressed={selected === index}
            onClick={() => setSelected(selected === index ? null : index)}
            className="flex h-full min-w-0 flex-1 items-end justify-center"
          >
            <span
              className={`w-full max-w-6 rounded-t transition ${
                bucket.minutos > 0 ? 'bg-ladrillo' : 'bg-ink-mute'
              } ${selected !== null && selected !== index ? 'opacity-40' : ''}`}
              style={{
                height: bucket.minutos > 0 ? `${(bucket.minutos / scale) * 100}%` : '2px',
              }}
            />
          </button>
        ))}
      </div>

      <div className="mt-1.5 flex gap-0.5">
        {buckets.map((bucket, index) => (
          <span
            key={bucket.label + index}
            aria-hidden
            className={`eyebrow min-w-0 flex-1 truncate text-center ${
              selected === index ? 'text-cal' : 'text-ink-mute'
            }`}
          >
            {bucket.label}
          </span>
        ))}
      </div>

      {detail && (
        <p className="mt-3 text-sm text-ink-soft">
          <span className="text-cal">{detail.fullLabel}</span>
          {detail.turnos === 0
            ? ' · no jugaste'
            : ` · ${turnosLabel(detail.turnos)} · ${formatHours(detail.minutos)}`}
        </p>
      )}
    </div>
  );
}

/** El valor de la barra más alta no se puede recortar: en los bordes se alinea para adentro. */
function edgeAlignment(index: number, total: number): string {
  if (index === 0) {
    return 'justify-start';
  }
  if (index === total - 1) {
    return 'justify-end';
  }
  return 'justify-center';
}

function barLabel(bucket: Bucket): string {
  if (bucket.turnos === 0) {
    return `${bucket.fullLabel}: no jugaste`;
  }
  return `${bucket.fullLabel}: ${turnosLabel(bucket.turnos)}, ${formatHours(bucket.minutos)}`;
}

function turnosLabel(turnos: number): string {
  return turnos === 1 ? '1 turno' : `${turnos} turnos`;
}
