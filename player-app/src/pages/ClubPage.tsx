import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api, ApiError, type Availability, type Slot } from '../api/client';
import { addDays, clockTime, longDate, money, shortDate, todayIso } from '../format';
import { Alert, Button, Card, Loading, Screen } from '../components/Ui';
import { Checkout } from './Checkout';

/**
 * Grilla del club.
 *
 * <p>Se organiza por horario y no por cancha porque asi decide el jugador: primero
 * a que hora puede jugar y recien despues en cual de las canchas libres. Recien
 * cuando elige un horario aparecen las canchas de esa franja.
 */
export function ClubPage() {
  const { slug = '' } = useParams();
  const [date, setDate] = useState(todayIso());
  const [data, setData] = useState<Availability | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Slot | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await api.availability(slug, date));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No pudimos cargar la grilla.');
    } finally {
      setLoading(false);
    }
  }, [slug, date]);

  useEffect(() => {
    void load();
  }, [load]);

  // Cambiar de dia cierra el checkout: el turno elegido ya no aplica.
  useEffect(() => setSelected(null), [date]);

  if (loading && !data) {
    return (
      <Screen>
        <Loading />
      </Screen>
    );
  }

  if (error && !data) {
    return (
      <Screen>
        <Alert>{error}</Alert>
      </Screen>
    );
  }

  const club = data!.club;
  const withAvailability = data!.slots.filter((slot) => slot.available.length > 0);

  return (
    <Screen>
      <header className="mb-5">
        <h1 className="text-2xl font-bold tracking-tight">{club.name}</h1>
        <p className="text-sm text-slate-500">
          Turnos de {data!.slotDurationMinutes} minutos · {longDate(date)}
        </p>
      </header>

      <DayPicker date={date} onChange={setDate} />

      {loading && <Loading label="Actualizando…" />}

      {!loading && withAvailability.length === 0 && (
        <Alert tone="info">
          No quedan turnos libres para este día. Probá con otra fecha.
        </Alert>
      )}

      {!loading && (
        <ul className="mt-4 space-y-2">
          {withAvailability.map((slot) => (
            <li key={slot.startsAt}>
              <SlotRow
                slot={slot}
                timeZone={club.timeZone}
                onSelect={() => setSelected(slot)}
              />
            </li>
          ))}
        </ul>
      )}

      {selected && (
        <Checkout
          slug={slug}
          club={club}
          slot={selected}
          onClose={() => setSelected(null)}
          onSlotTaken={() => {
            setSelected(null);
            void load();
          }}
        />
      )}
    </Screen>
  );
}

/** Pastillas de los proximos dias. Mas rapido que abrir un calendario en el celular. */
function DayPicker({ date, onChange }: { date: string; onChange: (date: string) => void }) {
  const today = todayIso();
  const days = Array.from({ length: 14 }, (_, index) => addDays(today, index));

  return (
    <div className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-2">
      {days.map((day) => (
        <button
          key={day}
          onClick={() => onChange(day)}
          className={`shrink-0 rounded-xl border px-3 py-2 text-sm font-medium capitalize ${
            day === date
              ? 'border-brand-600 bg-brand-600 text-white'
              : 'border-slate-200 bg-white text-slate-700'
          }`}
        >
          {day === today ? 'Hoy' : shortDate(day)}
        </button>
      ))}
    </div>
  );
}

function SlotRow({
  slot,
  timeZone,
  onSelect,
}: {
  slot: Slot;
  timeZone: string;
  onSelect: () => void;
}) {
  const cheapest = Math.min(...slot.available.map((court) => court.price));
  const courts = slot.available.length;

  return (
    <Card className="flex items-center gap-4 !p-4">
      <div className="flex-1">
        <p className="text-lg font-semibold tabular-nums">
          {clockTime(slot.startsAt, timeZone)} – {clockTime(slot.endsAt, timeZone)}
        </p>
        <p className="text-sm text-slate-500">
          {courts === 1 ? '1 cancha libre' : `${courts} canchas libres`} · desde {money(cheapest)}
        </p>
      </div>
      <div className="w-28">
        <Button onClick={onSelect}>Reservar</Button>
      </div>
    </Card>
  );
}
