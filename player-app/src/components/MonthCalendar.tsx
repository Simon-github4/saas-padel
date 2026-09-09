import { useMemo, useState } from 'react';
import { addDays, longDate, todayIso } from '../format';

const WEEKDAYS = ['Lu', 'Ma', 'Mi', 'Ju', 'Vi', 'Sá', 'Do'];

/**
 * Calendario mensual para elegir el día de la reserva.
 *
 * <p>Reemplaza las pastillas horizontales: permite ver todo el mes de un saque.
 * Los días pasados y los posteriores al horizonte de reserva del club quedan
 * deshabilitados.
 */
export function MonthCalendar({
  selected,
  onSelect,
  bookingHorizonDays,
}: {
  selected: string;
  onSelect: (date: string) => void;
  bookingHorizonDays: number;
}) {
  const today = todayIso();
  const firstAvailable = today;
  const lastAvailable = addDays(today, bookingHorizonDays);

  const [viewMonth, setViewMonth] = useState(() => {
    const d = new Date(`${selected}T12:00:00`);
    return { year: d.getFullYear(), month: d.getMonth() };
  });

  const cells = useMemo(
    () => buildMonth(new Date(viewMonth.year, viewMonth.month, 1)),
    [viewMonth],
  );

  const canGoBack = monthIndex(viewMonth) > monthIndexAt(firstAvailable);
  const canGoForward = monthIndex(viewMonth) < monthIndexAt(lastAvailable);

  return (
    // Con tope de ancho: las celdas son cuadradas y salen de dividir el ancho
    // en siete, asi que en una columna de escritorio cada dia terminaba siendo
    // un circulo de 88px para un numero de dos digitos, y el calendario solo
    // medi­a mas de medio alto de pantalla. En px y no en rem porque la raiz
    // crece a 17 y 18px en las pantallas grandes, que es justo donde molesta.
    //
    // Sin centrar: pegado a la izquierda queda alineado con el titulo del paso
    // y con la aclaracion de abajo, que son de ancho completo. Centrado, el
    // calendario quedaba corrido de los dos.
    <div className="max-w-[550px] rounded-2xl border border-cal/10 bg-vidrio p-4 [box-shadow:var(--shadow-card)]">
      <div className="mb-4 flex items-center justify-between">
        <button
          type="button"
          onClick={() => setViewMonth((m) => previousMonth(m))}
          disabled={!canGoBack}
          aria-label="Mes anterior"
          className="grid size-9 place-items-center rounded-full border border-cal/10 text-ink-soft transition hover:border-cal/25 hover:text-cal disabled:border-cal/[0.06] disabled:text-ink-mute"
        >
          ‹
        </button>
        <p className="display text-lg tracking-[0.12em]">
          {monthName(viewMonth.year, viewMonth.month)}
        </p>
        <button
          type="button"
          onClick={() => setViewMonth((m) => nextMonth(m))}
          disabled={!canGoForward}
          aria-label="Mes siguiente"
          className="grid size-9 place-items-center rounded-full border border-cal/10 text-ink-soft transition hover:border-cal/25 hover:text-cal disabled:border-cal/[0.06] disabled:text-ink-mute"
        >
          ›
        </button>
      </div>

      <div className="grid grid-cols-7 gap-1 text-center">
        {WEEKDAYS.map((day) => (
          <span key={day} className="eyebrow py-1 text-ink-mute">
            {day}
          </span>
        ))}

        {cells.map((cell, index) => {
          if (cell === null) {
            return <span key={`blank-${index}`} />;
          }
          const iso = cell;
          const enabled = iso >= firstAvailable && iso <= lastAvailable;
          const isSelected = iso === selected;
          const isToday = iso === today;
          return (
            <button
              key={iso}
              type="button"
              disabled={!enabled}
              onClick={() => onSelect(iso)}
              aria-label={longDate(iso)}
              aria-current={isSelected ? 'date' : undefined}
              // El dia disponible lleva fondo en reposo, no solo al pasar el
              // mouse: en el telefono -que es por donde entra el jugador- no
              // hay hover, y sin eso un dia libre y uno deshabilitado se
              // diferenciaban unicamente por el color del numero.
              className={`grid aspect-square place-items-center rounded-full text-sm font-semibold tabular-nums transition ${
                isSelected
                  ? 'bg-cal text-pista'
                  : enabled
                    ? 'bg-cal/[0.06] text-cal hover:bg-cal/15'
                    : 'text-ink-mute'
              } ${isToday && !isSelected ? 'ring-1 ring-inset ring-ladrillo/60' : ''}`}
            >
              {parseInt(iso.slice(8), 10)}
            </button>
          );
        })}
      </div>
    </div>
  );
}

type Month = { year: number; month: number };

/** Devuelve los días del mes visible; los huecos previos al día 1 van en null. */
function buildMonth(firstOfMonth: Date): (string | null)[] {
  const cells: (string | null)[] = [];
  const firstWeekday = (firstOfMonth.getDay() + 6) % 7; // lunes = 0
  for (let i = 0; i < firstWeekday; i += 1) {
    cells.push(null);
  }
  const daysInMonth = new Date(
    firstOfMonth.getFullYear(),
    firstOfMonth.getMonth() + 1,
    0,
  ).getDate();
  for (let day = 1; day <= daysInMonth; day += 1) {
    const d = new Date(firstOfMonth.getFullYear(), firstOfMonth.getMonth(), day);
    cells.push(isoOf(d));
  }
  return cells;
}

function previousMonth(m: Month): Month {
  return m.month === 0 ? { year: m.year - 1, month: 11 } : { year: m.year, month: m.month - 1 };
}

function nextMonth(m: Month): Month {
  return m.month === 11 ? { year: m.year + 1, month: 0 } : { year: m.year, month: m.month + 1 };
}

/** Indice comparable del mes, para saber si un mes está antes o después de otro. */
function monthIndex(m: Month): number {
  return m.year * 12 + m.month;
}

function monthIndexAt(isoDate: string): number {
  const d = new Date(`${isoDate}T12:00:00`);
  return monthIndex({ year: d.getFullYear(), month: d.getMonth() });
}

function monthName(year: number, month: number): string {
  return new Intl.DateTimeFormat('es-AR', { month: 'long', year: 'numeric' })
    .format(new Date(year, month, 1))
    .replace(/^./, (c) => c.toUpperCase());
}

function isoOf(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}
