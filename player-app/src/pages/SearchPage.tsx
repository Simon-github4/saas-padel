import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError, api } from '../api/client';
import type { SearchMatch, SearchResult } from '../api/client';
import { AccountButton } from '../components/AccountButton';
import { MonthCalendar } from '../components/MonthCalendar';
import { Alert, Badge, Button, Card, Chip, Loading, Screen, SectionTitle, TopBar } from '../components/Ui';
import { addDays, longDate, perPerson, todayIso } from '../format';

/** Primera y última hora que ofrece el selector, en pasos de media hora. */
const FIRST_HOUR = 0;
const LAST_HALF = '23:30';

const FRANJAS: { label: string; from: string; to: string }[] = [
  { label: 'Todo el día', from: '00:00', to: LAST_HALF },
  { label: 'Mañana', from: '06:00', to: '12:00' },
  { label: 'Tarde', from: '12:00', to: '18:00' },
  { label: 'Noche', from: '18:00', to: LAST_HALF },
];

const TIMES = buildTimes();

/**
 * Búsqueda de canchas en varios clubes a la vez.
 *
 * <p>Responde la pregunta con la que llega el jugador que todavía no eligió dónde:
 * "hoy a la noche, donde sea". La grilla por club no sirve para eso, porque exige
 * saber de antemano a cuál entrar.
 *
 * <p>Los resultados van en una sola lista por horario y no agrupados por club: a
 * quien le da igual el lugar, el club es un dato más del turno, no el índice por el
 * que busca.
 *
 * <p>Los filtros viven en la URL para que una búsqueda se pueda compartir por
 * WhatsApp y para que volver desde un club no la pierda.
 */
export function SearchPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();

  const date = params.get('fecha') || todayIso();
  const from = params.get('desde') || '00:00';
  const to = params.get('hasta') || LAST_HALF;
  const selectedClubs = useMemo(
    () => (params.get('clubes') || '').split(',').filter(Boolean),
    [params],
  );

  const [data, setData] = useState<SearchResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pickingDay, setPickingDay] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await api.search({ date, from, to, clubs: selectedClubs }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo buscar. Probá de nuevo.');
    } finally {
      setLoading(false);
    }
  }, [date, from, to, selectedClubs]);

  useEffect(() => {
    void load();
  }, [load]);

  const update = useCallback(
    (patch: Record<string, string>) => {
      const next = new URLSearchParams(params);
      for (const [key, value] of Object.entries(patch)) {
        if (value) {
          next.set(key, value);
        } else {
          next.delete(key);
        }
      }
      setParams(next, { replace: true });
    },
    [params, setParams],
  );

  // Mover un extremo del rango arrastra al otro si lo cruzó: un "desde" posterior
  // al "hasta" no describe ninguna franja, y el backend lo rechazaría.
  const setFrom = (value: string) =>
    update({ desde: value, hasta: value > to ? value : to });
  const setTo = (value: string) => update({ hasta: value, desde: value < from ? value : from });

  const toggleClub = (slug: string) => {
    const next = selectedClubs.includes(slug)
      ? selectedClubs.filter((item) => item !== slug)
      : [...selectedClubs, slug];
    update({ clubes: next.join(',') });
  };

  const clubs = data?.clubs ?? [];
  // El horizonte más largo entre los clubes: el calendario es uno solo para todos.
  const horizon = clubs.length > 0
    ? Math.max(...clubs.map((club) => club.bookingHorizonDays))
    : 21;
  const franjaActiva = FRANJAS.find((f) => f.from === from && f.to === to);
  const todoElDia = from === '00:00' && to === LAST_HALF;

  return (
    <Screen
      top={
        <TopBar
          name="Reservá tu cancha"
          accountSlot={<AccountButton />}
          onTitleClick={() => navigate('/buscar')}
        />
      }
    >
      <div className="pt-8 text-center">
        <h1 className="text-4xl tracking-[0.1em]">¿Cuándo querés jugar?</h1>
        <p className="mx-auto mt-3 max-w-sm text-ink-soft">
          Buscá en todos los clubes a la vez y quedate con el horario que te sirva.
        </p>
      </div>

      {/* ------------------------------------------------------- filtros */}
      <Card className="mt-7 space-y-6">
        <div className="space-y-3">
          <p className="eyebrow text-ink-soft">Día</p>
          <div className="flex flex-wrap items-center gap-2">
            <Chip active={date === todayIso()} onClick={() => update({ fecha: todayIso() })}>
              Hoy
            </Chip>
            <Chip
              active={date === addDays(todayIso(), 1)}
              onClick={() => update({ fecha: addDays(todayIso(), 1) })}
            >
              Mañana
            </Chip>
            <Chip active={pickingDay} onClick={() => setPickingDay(!pickingDay)}>
              {pickingDay ? 'Cerrar calendario' : 'Otro día'}
            </Chip>
          </div>
          <p className="text-sm text-ink-soft">{longDate(date)}</p>
          {pickingDay && (
            <MonthCalendar
              selected={date}
              onSelect={(day) => {
                update({ fecha: day });
                setPickingDay(false);
              }}
              bookingHorizonDays={horizon}
            />
          )}
        </div>

        <div className="space-y-3">
          <p className="eyebrow text-ink-soft">Horario</p>
          <div className="flex flex-wrap gap-2">
            {FRANJAS.map((franja) => (
              <Chip
                key={franja.label}
                active={franjaActiva?.label === franja.label}
                onClick={() => update({ desde: franja.from, hasta: franja.to })}
              >
                {franja.label}
              </Chip>
            ))}
          </div>
          <div className="grid grid-cols-2 gap-3">
            <TimeSelect label="Desde" value={from} onChange={setFrom} />
            <TimeSelect label="Hasta" value={to} onChange={setTo} />
          </div>
          <p className="text-xs text-ink-soft">
            Es la hora a la que empieza el turno, no a la que termina.
          </p>
        </div>

        {clubs.length > 0 && (
          <div className="space-y-3">
            <p className="eyebrow text-ink-soft">Clubes</p>
            <div className="flex flex-wrap gap-2">
              <Chip active={selectedClubs.length === 0} onClick={() => update({ clubes: '' })}>
                Todos
              </Chip>
              {clubs.map((club) => (
                <Chip
                  key={club.slug}
                  active={selectedClubs.includes(club.slug)}
                  onClick={() => toggleClub(club.slug)}
                >
                  {club.name}
                </Chip>
              ))}
            </div>
          </div>
        )}
      </Card>

      {/* ---------------------------------------------------- resultados */}
      <section className="mt-9">
        {error && (
          <div className="space-y-3">
            <Alert>{error}</Alert>
            <Button variant="secondary" onClick={() => void load()}>
              Reintentar
            </Button>
          </div>
        )}

        {!error && loading && <Loading label="Buscando canchas…" />}

        {!error && !loading && data && (
          <>
            {/* Sin resultados el titulo sobra: el cartel de abajo ya lo dice, y
                "0 horarios libres" arriba lo repite con peor cara. */}
            {data.matches.length > 0 && (
              <SectionTitle title={resultsTitle(data.matches)} subtitle="Tocá uno para reservarlo" />
            )}

            {data.matches.length === 0 ? (
              <div className="space-y-3">
                <Alert tone="info">No quedan canchas libres con esos filtros.</Alert>
                {!todoElDia && (
                  <Button
                    variant="secondary"
                    onClick={() => update({ desde: '00:00', hasta: LAST_HALF })}
                  >
                    Buscar en todo el día
                  </Button>
                )}
                <Button variant="secondary" onClick={() => update({ fecha: addDays(date, 1) })}>
                  Probar el día siguiente
                </Button>
              </div>
            ) : (
              <ul className="mt-5 space-y-3">
                {data.matches.map((match, index) => (
                  <li key={`${match.clubSlug}-${match.startsAt}`}>
                    <Link
                      to={`/club/${match.clubSlug}?fecha=${data.date}&hora=${match.startTime}`}
                      style={{ animationDelay: `${Math.min(index, 12) * 40}ms` }}
                      className={`ficha-in flex items-center gap-4 rounded-2xl border p-4 transition ${
                        match.promo
                          ? 'border-ladrillo/40 bg-ladrillo/[0.06] hover:border-ladrillo/70'
                          : 'border-cal/10 bg-vidrio hover:border-cal/25 hover:bg-vidrio-alto'
                      }`}
                    >
                      <div className="shrink-0 text-center">
                        <span className="display block text-3xl tabular-nums">
                          {match.startTime}
                        </span>
                        <span className="text-xs text-ink-soft tabular-nums">{match.endTime}</span>
                      </div>

                      <div className="min-w-0 flex-1">
                        <p className="truncate font-bold">{match.clubName}</p>
                        {match.city && (
                          <p className="truncate text-sm text-ink-soft">{match.city}</p>
                        )}
                        <p className="eyebrow mt-1.5 text-ink-soft">
                          {match.freeCourts === 1
                            ? '1 cancha libre'
                            : `${match.freeCourts} canchas libres`}
                        </p>
                      </div>

                      <div className="shrink-0 text-right">
                        {match.promo && <Badge tone="promo">Promo</Badge>}
                        <p
                          className={`mt-1 text-sm font-bold tabular-nums ${
                            match.promo ? 'text-ladrillo-claro' : 'text-cal'
                          }`}
                        >
                          {perPerson(match.cheapestPrice, match.playersPerCourt)}
                        </p>
                        <p className="text-xs text-ink-soft">c/u</p>
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </section>
    </Screen>
  );
}

function TimeSelect({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="block">
      <span className="eyebrow mb-1.5 block text-ink-soft">{label}</span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="w-full appearance-none rounded-xl border border-cal/10 bg-vidrio px-3.5 py-3 text-base tabular-nums text-cal transition hover:border-cal/25"
      >
        {TIMES.map((time) => (
          <option key={time} value={time}>
            {time}
          </option>
        ))}
      </select>
    </label>
  );
}

/**
 * Título de la lista de resultados.
 *
 * <p>Cada match es un horario en un club, no una cancha: un mismo horario puede
 * tener varias canchas libres. Contar solo los matches ("34 turnos libres") suena
 * a 34 canchas y en realidad pueden ser muchas más, así que el título separa las
 * dos cuentas en vez de mezclarlas en una sola que termina siendo ambigua.
 */
function resultsTitle(matches: SearchMatch[]): string {
  const totalCourts = matches.reduce((sum, match) => sum + match.freeCourts, 0);
  const horarios = matches.length === 1 ? '1 horario' : `${matches.length} horarios`;
  const canchas = totalCourts === 1 ? '1 cancha libre' : `${totalCourts} canchas libres`;
  return `${horarios} · ${canchas}`;
}

/** 00:00, 00:30, … 23:30. Media hora alcanza: ningún club arranca turnos al cuarto. */
function buildTimes(): string[] {
  const times: string[] = [];
  for (let hour = FIRST_HOUR; hour < 24; hour += 1) {
    for (const minute of ['00', '30']) {
      times.push(`${String(hour).padStart(2, '0')}:${minute}`);
    }
  }
  return times;
}
