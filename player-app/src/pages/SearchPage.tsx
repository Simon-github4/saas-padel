import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { track } from '../analytics';
import { ApiError, api } from '../api/client';
import type { ClubOption, CourtRoof, CourtSurface, CourtWall, SearchMatch, SearchResult } from '../api/client';
import { AccountButton } from '../components/AccountButton';
import { InstallBanner } from '../components/InstallBanner';
import { MonthCalendar } from '../components/MonthCalendar';
import { TemaSwitch } from '../components/TemaSwitch';
import { Alert, Badge, Button, Card, Screen, TopBar } from '../components/Ui';
import {
  ROOF_LABEL,
  SURFACE_LABEL,
  WALL_LABEL,
  featuresSummary,
  roofFromParam,
  roofParam,
  surfaceFromParam,
  surfaceParam,
  wallFromParam,
  wallParam,
} from '../courtFeatures';
import { formatDistance } from '../distance';
import { addDays, longDate, perPerson, todayIso } from '../format';
import { GeoError, type Position, currentPosition, describeProblem, rememberedPosition } from '../geolocation';
import {
  type ClubByDistance,
  type ClubResults,
  clubFeatures,
  clubInitials,
  groupByClub,
  slotMinutes,
  sortByDistance,
} from '../searchResults';
import { setPageMeta } from '../seo';
import { BRAND } from './marketing/config';

/** Primera y última hora que ofrece el selector, en pasos de media hora. */
const FIRST_HOUR = 0;
const LAST_HALF = '23:30';

const FRANJAS: { label: string; hint: string; from: string; to: string }[] = [
  { label: 'Todo el día', hint: 'Cualquier hora', from: '00:00', to: LAST_HALF },
  { label: 'Mañana', hint: '06 a 12', from: '06:00', to: '12:00' },
  { label: 'Tarde', hint: '12 a 18', from: '12:00', to: '18:00' },
  { label: 'Noche', hint: '18 en adelante', from: '18:00', to: LAST_HALF },
];

const TIMES = buildTimes();

/** Cuántos días muestra la tira antes de tener que abrir el calendario. */
const STRIP_DAYS = 14;

/**
 * Dónde se recuerda si el jugador activó el orden por cercanía. No va en la URL a
 * propósito: un link compartido con cercanía le pediría la ubicación a quien lo
 * abre sin que la haya pedido. Y es sessionStorage: dura lo que la pestaña.
 */
const CERCANIA_KEY = 'orden-buscador';

/**
 * Si se ofrece el botón de ordenar por cercanía. Está listo pero apagado hasta
 * decidir mostrarlo: mientras tanto la búsqueda siempre ordena por defecto (más
 * horarios primero) y nunca pide la ubicación. Para activarlo alcanza con ponerlo
 * en true.
 */
const CERCANIA_VISIBLE = false;

/**
 * Búsqueda de canchas en varios clubes a la vez.
 *
 * <p>Responde la pregunta con la que llega el jugador que todavía no eligió dónde:
 * "hoy a la noche, donde sea". La grilla por club no sirve para eso, porque exige
 * saber de antemano a cuál entrar.
 *
 * <p>Los resultados van seccionados por localidad y, adentro de cada una, en una
 * tarjeta por club con su foto y todos sus horarios libres del rango. La
 * localidad es un corte real para quien juega: nadie cruza de ciudad por un
 * turno. Por eso las ciudades pegadas cuentan como una sola zona (Necochea y
 * Quequén, ver ZONAS). Arriba va el club con más horarios distintos libres, que
 * es el que más chances da de encontrar uno que sirva (ver groupByClub).
 *
 * <p>Con el botón de cercanía se ordena por distancia: la ubicación del jugador se
 * pide recién ahí, y la distancia se calcula acá sin mandarla al servidor.
 *
 * <p>Los filtros viven en la URL para que una búsqueda se pueda compartir por
 * WhatsApp y para que volver desde un club no la pierda. La localidad se filtra
 * acá y no en el backend: la búsqueda ya trae todos los clubes, y así las
 * cuentas de cada localidad salen de la misma respuesta.
 */
export function SearchPage() {
  const [params, setParams] = useSearchParams();

  const date = params.get('fecha') || todayIso();
  const from = params.get('desde') || '00:00';
  const to = params.get('hasta') || LAST_HALF;
  const localidad = params.get('localidad') || '';
  const wall = wallFromParam(params.get('paredes'));
  const surface = surfaceFromParam(params.get('piso'));
  const roof = roofFromParam(params.get('techo'));
  const selectedClubs = useMemo(
    () => (params.get('clubes') || '').split(',').filter(Boolean),
    [params],
  );

  const [data, setData] = useState<SearchResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pickingDay, setPickingDay] = useState(false);

  // Se arranca con cercanía solo si la ubicación ya está en memoria (volviendo de un
  // club, sin recargar). Si no, el efecto de abajo la recupera sin preguntar, o queda
  // el orden por defecto.
  const [nearby, setNearby] = useState(
    () => CERCANIA_VISIBLE && readNearby() && rememberedPosition() !== null,
  );
  const [origin, setOrigin] = useState<Position | null>(() => rememberedPosition());
  const [locating, setLocating] = useState(false);
  const [geoError, setGeoError] = useState<string | null>(null);

  // Después de recargar la página la ubicación en memoria se pierde. Si el jugador
  // había elegido cercanía y el permiso ya está dado, se vuelve a pedir: el navegador
  // no muestra ningún cartel. Si el permiso no está dado, no se pregunta nada sin que
  // toque el botón.
  useEffect(() => {
    if (!CERCANIA_VISIBLE || !readNearby() || rememberedPosition() || !navigator.permissions) {
      return;
    }
    let cancelled = false;
    navigator.permissions
      .query({ name: 'geolocation' })
      .then((status) => (status.state === 'granted' ? currentPosition() : null))
      .then((position) => {
        if (!cancelled && position) {
          setOrigin(position);
          setNearby(true);
        }
      })
      .catch(() => {
        // Sin ubicación queda el orden por defecto: no hay nada que avisar, nadie la pidió ahora.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const toggleNearby = async () => {
    if (locating) {
      return;
    }
    setGeoError(null);
    if (nearby) {
      setNearby(false);
      saveNearby(false);
      track('search_sort', { detail: 'cercania:off' });
      return;
    }
    setLocating(true);
    try {
      const position = await currentPosition();
      setOrigin(position);
      setNearby(true);
      saveNearby(true);
      track('search_sort', { detail: 'cercania' });
    } catch (err) {
      // Sin ubicación no hay cercanía: queda el orden por defecto y se dice por qué.
      const problem = err instanceof GeoError ? err.problem : 'unavailable';
      setGeoError(describeProblem(problem));
      track('search_sort', { detail: `cercania:${problem}` });
    } finally {
      setLocating(false);
    }
  };

  // Con ?localidad= de una zona reconocida (ver ZONAS), el título nombra la zona en vez del
  // genérico "todos los clubes": mismos textos que SeoPageRenderer.searchMeta(zoneKey) en el
  // backend, que es lo que ya ve un buscador antes de que cargue React.
  useEffect(() => {
    const zoneName = zoneNameByKey(localidad);
    return zoneName
      ? setPageMeta(
          `Canchas de pádel en ${zoneName} — turnos online`,
          `Buscá y reservá una cancha de pádel libre en ${zoneName} hoy, por día y horario, en todos los clubes a la vez.`,
        )
      : setPageMeta(
          'Buscar cancha de pádel — todos los clubes',
          'Buscá canchas de pádel libres hoy en todos los clubes a la vez, por día y horario, sin elegir club primero.',
        );
  }, [localidad]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await api.search({ date, from, to, clubs: selectedClubs, wall, surface, roof });
      setData(result);
      // Con los filtros y el total: una búsqueda que vuelve con cero turnos es
      // demanda que hoy se pierde, y es el dato que dice qué club falta sumar.
      track('search', {
        date,
        from,
        to,
        clubs: selectedClubs.join(','),
        results: result.matches.length,
        // Qué cancha se pidió: dice cuánta gente busca techada, blindex, pared o cemento.
        detail: [roof, wall, surface].filter(Boolean).join(',') || undefined,
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo buscar. Probá de nuevo.');
    } finally {
      setLoading(false);
    }
  }, [date, from, to, selectedClubs, wall, surface, roof]);

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

  // Estable entre renders: el rango exacto la usa en un efecto con espera, y una
  // función nueva en cada render reiniciaría esa espera.
  const setRange = useCallback((desde: string, hasta: string) => update({ desde, hasta }), [update]);

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

  const localities = useMemo(() => buildLocalities(clubs, data?.matches ?? []), [clubs, data]);
  const activeLocality = localities.find((item) => item.key === localidad) ?? null;
  const visibleClubs = activeLocality
    ? clubs.filter((club) => localityOf(club.city).key === activeLocality.key)
    : clubs;
  const sections = useMemo(
    () => buildSections(data?.matches ?? [], activeLocality?.key ?? null),
    [data, activeLocality],
  );
  const visibleMatches = sections.reduce((sum, section) => sum + section.matches.length, 0);

  // Con cercanía, una sola lista sin secciones: ordenada por distancia, los
  // encabezados de localidad cortarían el orden. Se agrupa desde los turnos tal
  // como vienen del backend -y no desde las secciones- para que, a igual distancia y
  // entre clubes sin ubicación, siga mandando el orden por defecto.
  const byDistance = useMemo(() => {
    if (!nearby || !origin || !data) {
      return null;
    }
    const matches = activeLocality
      ? data.matches.filter((match) => localityOf(match.city).key === activeLocality.key)
      : data.matches;
    return sortByDistance(groupByClub(matches, data.clubs), origin);
  }, [nearby, origin, data, activeLocality]);

  // Cambiar de localidad descarta los clubes elegidos de otra: dejarlos
  // filtrando daría una lista vacía sin motivo a la vista.
  const pickLocality = (key: string) => {
    const keep = key
      ? selectedClubs.filter((slug) => {
          const club = clubs.find((item) => item.slug === slug);
          return club && localityOf(club.city).key === key;
        })
      : selectedClubs;
    update({ localidad: key, clubes: keep.join(',') });
  };

  return (
    <Screen
      top={
        // La marca arriba a la izquierda, con link a la landing: quien llega a
        // /buscar por un link compartido tiene que poder ver qué es TurnosPadel
        // sin bajar hasta el pie.
        <TopBar name={BRAND} titleTo="/" brandMark accountSlot={<AccountButton />} />
      }
    >
      <TemaSwitch recordar="tema-buscador" />
      <div className="pt-10 text-center">
        <p className="eyebrow flex items-center justify-center gap-3 text-ladrillo-claro">
          <span aria-hidden className="h-px w-6 bg-ladrillo-claro/60" />
          Todos los clubes
          <span aria-hidden className="h-px w-6 bg-ladrillo-claro/60" />
        </p>
        <h1 className="mt-4 text-[clamp(2.5rem,11vw,3.5rem)] tracking-[0.06em]">¿Cuándo querés jugar?</h1>
        <p className="mx-auto mt-3 max-w-sm text-ink-soft">
          Buscá en todos los clubes a la vez y quedate con el horario que te sirva.
        </p>
      </div>

      <InstallBanner />

      {/* ------------------------------------------------------- filtros */}
      <Card className="mt-8 space-y-7">
        <div className="space-y-3">
          <div className="flex items-baseline justify-between gap-3">
            <p className="eyebrow text-ink-soft">Día</p>
            <button
              type="button"
              onClick={() => setPickingDay(!pickingDay)}
              aria-expanded={pickingDay}
              className="-my-3 text-xs font-semibold text-ladrillo-claro underline-offset-4 hover:underline"
            >
              {pickingDay ? 'Cerrar calendario' : 'Ver calendario'}
            </button>
          </div>
          <DayStrip
            selected={date}
            days={Math.min(STRIP_DAYS, horizon + 1)}
            onSelect={(day) => {
              update({ fecha: day });
              setPickingDay(false);
            }}
          />
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

        <TimeFilter
          franjaActiva={franjaActiva?.label ?? null}
          from={from}
          to={to}
          onFranja={(franja) => update({ desde: franja.from, hasta: franja.to })}
          onRange={setRange}
        />

        <CourtFilter
          wall={wall}
          surface={surface}
          roof={roof}
          onWall={(value) => update({ paredes: value ? wallParam(value) : '' })}
          onSurface={(value) => update({ piso: value ? surfaceParam(value) : '' })}
          onRoof={(value) => update({ techo: value ? roofParam(value) : '' })}
        />

        {clubs.length > 0 && (
          <div className="space-y-4">
            <p className="eyebrow text-ink-soft">Dónde</p>
            {localities.length > 1 && (
              <div className="flex flex-wrap gap-2">
                <LocalityChip
                  active={!activeLocality}
                  label="Todas"
                  count={data?.matches.length ?? 0}
                  onClick={() => pickLocality('')}
                />
                {localities.map((item) => (
                  <LocalityChip
                    key={item.key}
                    active={activeLocality?.key === item.key}
                    label={item.name}
                    count={item.matches}
                    onClick={() => pickLocality(item.key)}
                  />
                ))}
              </div>
            )}
            <div className="flex flex-wrap gap-2">
              <ClubChip active={selectedClubs.length === 0} onClick={() => update({ clubes: '' })}>
                {activeLocality ? `Todos en ${activeLocality.name}` : 'Todos los clubes'}
              </ClubChip>
              {visibleClubs.map((club) => (
                <ClubChip
                  key={club.slug}
                  active={selectedClubs.includes(club.slug)}
                  onClick={() => toggleClub(club.slug)}
                >
                  {club.name}
                </ClubChip>
              ))}
            </div>
          </div>
        )}
      </Card>

      {/* ---------------------------------------------------- resultados */}
      <section className="mt-12" aria-busy={loading}>
        {error && (
          <div className="space-y-3">
            <Alert>{error}</Alert>
            <Button variant="secondary" onClick={() => void load()}>
              Reintentar
            </Button>
          </div>
        )}

        {!error && !data && loading && <ResultsSkeleton />}

        {!error && data && (
          // Al cambiar un filtro, la lista vieja queda atenuada mientras llega la
          // nueva, en vez de desaparecer y dejar la página saltando.
          <div className={`transition-opacity duration-300 ${loading ? 'opacity-50' : 'opacity-100'}`}>
            {visibleMatches === 0 ? (
              <EmptyResults
                date={date}
                todoElDia={todoElDia}
                otherLocalities={activeLocality ? (data.matches.length > 0) : false}
                courtFiltered={wall !== null || surface !== null || roof !== null}
                onAllDay={() => update({ desde: '00:00', hasta: LAST_HALF })}
                onNextDay={() => update({ fecha: addDays(date, 1) })}
                onAllLocalities={() => pickLocality('')}
                onAnyCourt={() => update({ paredes: '', piso: '', techo: '' })}
              />
            ) : (
              <>
                {/* Con una sola zona, la cuenta total repetía la de su encabezado. Por
                    cercanía no hay encabezados de zona, así que va siempre. */}
                {sections.length > 1 || byDistance || CERCANIA_VISIBLE ? (
                  <div className="mb-6 flex flex-wrap items-end justify-between gap-x-3 gap-y-4">
                    <div className="min-w-0">
                      {(sections.length > 1 || byDistance) && (
                        <h2 className="text-2xl">{resultsTitle(sections.flatMap((section) => section.matches))}</h2>
                      )}
                      <p className="mt-1 text-xs text-ink-soft">Tocá un horario para reservarlo</p>
                    </div>
                    {CERCANIA_VISIBLE && (
                      <NearbyToggle active={nearby} locating={locating} onToggle={() => void toggleNearby()} />
                    )}
                  </div>
                ) : (
                  <p className="mb-3 text-right text-xs text-ink-soft">Tocá un horario para reservarlo</p>
                )}

                {geoError && (
                  <div className="mb-6">
                    <Alert>{geoError}</Alert>
                  </div>
                )}

                {byDistance ? (
                  <ul className="space-y-4">
                    {byDistance.map((group, index) => (
                      <li key={group.slug}>
                        <ClubCard
                          club={group}
                          distanceKm={group.distanceKm}
                          date={data.date}
                          position={index + 1}
                          courtQuery={courtQuery(wall, surface, roof)}
                        />
                      </li>
                    ))}
                  </ul>
                ) : (
                  <div className="space-y-10">
                    {sections.map((section) => (
                      <LocalitySection
                        key={section.key}
                        section={section}
                        clubs={clubs}
                        date={data.date}
                        courtQuery={courtQuery(wall, surface, roof)}
                      />
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </section>

      {/*
        La otra salida hacia la landing, además de la marca de la barra. A
        /buscar se entra por un link compartido o por un marcador, sin pasar por
        la portada. Acá abajo va con el texto de a quién le sirve: esa página le
        vende sobre todo al dueño del club.
      */}
      <footer className="mt-16 border-t border-cal/10 pt-8 text-center">
        <Link
          to="/"
          className="eyebrow text-ink-soft underline-offset-4 transition hover:text-cal hover:underline"
        >
          ¿Tenés un club? Conocé {BRAND}
        </Link>
      </footer>
    </Screen>
  );
}

/**
 * Los próximos días como pastillas que se deslizan de costado: hoy y mañana
 * con nombre, el resto con el día de la semana. Para ir más lejos está el
 * calendario. Si la elegida queda fuera de la vista, la tira se corre hasta
 * mostrarla.
 */
function DayStrip({
  selected,
  days,
  onSelect,
}: {
  selected: string;
  days: number;
  onSelect: (day: string) => void;
}) {
  const strip = useRef<HTMLDivElement>(null);
  const today = todayIso();
  const list = Array.from({ length: Math.max(days, 1) }, (_, index) => addDays(today, index));

  // La tira se mueve solo si el día elegido no se ve entero (vino de la URL o
  // del calendario), y lo justo para mostrarlo. Antes lo centraba siempre: tocar
  // un día de la mitad para la derecha corría la tira y "Hoy" quedaba afuera.
  useEffect(() => {
    const container = strip.current;
    const pill = container?.querySelector<HTMLElement>('[aria-pressed="true"]');
    if (!container || !pill) {
      return;
    }
    const edge = parseFloat(getComputedStyle(container).paddingLeft);
    const box = container.getBoundingClientRect();
    const pillBox = pill.getBoundingClientRect();
    const hiddenLeft = box.left + edge - pillBox.left;
    const hiddenRight = pillBox.right - (box.right - edge);
    // scrollBy sobre la tira y no scrollIntoView: ese mueve también la página. El
    // pixel de tolerancia es por los decimales: un scroll de 0,000002 px no mueve
    // nada a la vista, pero despierta el encastre y la tira salta de lugar.
    if (hiddenLeft > 1) {
      container.scrollBy({ left: -hiddenLeft, behavior: 'smooth' });
    } else if (hiddenRight > 1) {
      container.scrollBy({ left: hiddenRight, behavior: 'smooth' });
    }
  }, [selected]);

  // scroll-px-5: el encastre respeta el margen. Sin esto, al cargar la tira
  // saltaba 20px para pegar "Hoy" al borde de la pantalla.
  return (
    <div
      ref={strip}
      className="-mx-5 flex snap-x scroll-px-5 gap-2 overflow-x-auto px-5 pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
    >
      {list.map((day, index) => {
        const active = day === selected;
        const label = index === 0 ? 'Hoy' : index === 1 ? 'Mañana' : weekday(day);
        return (
          <button
            key={day}
            type="button"
            aria-pressed={active}
            aria-label={longDate(day)}
            onClick={() => onSelect(day)}
            className={`flex w-16 shrink-0 snap-start flex-col items-center rounded-2xl border py-2.5 transition ${
              active
                ? 'border-cal bg-cal text-pista'
                : 'border-cal/10 bg-pista text-cal hover:border-cal/30'
            }`}
          >
            <span className={`text-[0.65rem] font-semibold uppercase tracking-[0.1em] ${active ? 'text-pista/70' : 'text-ink-soft'}`}>
              {label}
            </span>
            <span className="display mt-0.5 text-2xl leading-none tabular-nums">{Number(day.slice(8))}</span>
          </button>
        );
      })}
      {/* Si el día elegido quedó fuera de la tira (vino del calendario), igual
          se ve cuál es. */}
      {!list.includes(selected) && (
        <span className="flex shrink-0 items-center rounded-2xl border border-cal bg-cal px-4 text-sm font-bold text-pista first-letter:uppercase">
          {longDate(selected)}
        </span>
      )}
    </div>
  );
}

/**
 * Franjas en un selector de cuatro, y el rango exacto plegado debajo: casi todos
 * buscan "a la noche", y los dos desplegables a la vista eran ruido para ellos.
 * Si el rango de la URL no es una franja, arranca abierto.
 */
function TimeFilter({
  franjaActiva,
  from,
  to,
  onFranja,
  onRange,
}: {
  franjaActiva: string | null;
  from: string;
  to: string;
  onFranja: (franja: (typeof FRANJAS)[number]) => void;
  onRange: (from: string, to: string) => void;
}) {
  const [exact, setExact] = useState(franjaActiva === null);

  return (
    <div className="space-y-3">
      <div className="flex items-baseline justify-between gap-3">
        <p className="eyebrow text-ink-soft">Horario</p>
        <button
          type="button"
          onClick={() => setExact(!exact)}
          aria-expanded={exact}
          className="-my-3 text-xs font-semibold text-ladrillo-claro underline-offset-4 hover:underline"
        >
          {exact ? 'Ocultar horario exacto' : 'Elegir horario exacto'}
        </button>
      </div>
      <div role="group" aria-label="Franja horaria" className="grid grid-cols-4 gap-1 rounded-2xl bg-cal/[0.05] p-1">
        {FRANJAS.map((franja) => {
          const active = franjaActiva === franja.label;
          return (
            <button
              key={franja.label}
              type="button"
              aria-pressed={active}
              onClick={() => onFranja(franja)}
              className={`flex flex-col items-center justify-center rounded-xl px-1 py-2 text-center transition ${
                active ? 'bg-cal text-pista' : 'text-ink-soft hover:bg-cal/[0.06] hover:text-cal'
              }`}
            >
              <span className="text-xs font-bold leading-tight">{franja.label}</span>
              <span className={`mt-0.5 text-[0.65rem] leading-tight ${active ? 'text-pista/70' : 'text-ink-mute'}`}>
                {franja.hint}
              </span>
            </button>
          );
        })}
      </div>
      {exact && <ExactRange from={from} to={to} onRange={onRange} />}
    </div>
  );
}

/** Espera antes de volcar el rango a la URL: arrastrar no dispara una búsqueda por paso. */
const RANGE_COMMIT_MS = 350;

/** Marcas debajo del riel: cada seis horas y el último turno posible. */
const RANGE_TICKS = [0, 12, 24, 36, TIMES.length - 1];

function timeIndex(time: string): number {
  return Math.max(0, TIMES.indexOf(time));
}

/**
 * El rango exacto: dos manijas sobre un riel del día, para acercarse rápido, y
 * botones de media hora a cada lado de la hora, para afinar con el pulgar sin
 * pelearse con la manija. Reemplaza a los dos desplegables de 48 horas, que en
 * el teléfono obligaban a bajar una lista larga dos veces.
 *
 * <p>Las manijas no se cruzan: "desde" nunca pasa a "hasta". El rango va a la
 * URL recién cuando se deja de mover, así la búsqueda no corre en cada paso.
 */
function ExactRange({
  from,
  to,
  onRange,
}: {
  from: string;
  to: string;
  onRange: (from: string, to: string) => void;
}) {
  const [start, setStart] = useState(timeIndex(from));
  const [end, setEnd] = useState(timeIndex(to));
  const last = TIMES.length - 1;

  // Si el rango cambia desde afuera (una franja, volver atrás), las manijas lo siguen.
  useEffect(() => {
    setStart(timeIndex(from));
    setEnd(timeIndex(to));
  }, [from, to]);

  useEffect(() => {
    if (TIMES[start] === from && TIMES[end] === to) {
      return;
    }
    const timer = window.setTimeout(() => onRange(TIMES[start], TIMES[end]), RANGE_COMMIT_MS);
    return () => window.clearTimeout(timer);
  }, [start, end, from, to, onRange]);

  const moveStart = (value: number) => setStart(Math.min(Math.max(0, value), end));
  const moveEnd = (value: number) => setEnd(Math.max(Math.min(last, value), start));
  // Los botones suman sobre el valor del momento y no sobre el del render: dos
  // toques seguidos antes de que se redibuje tienen que mover dos pasos.
  const nudgeStart = (delta: number) => setStart((value) => Math.min(Math.max(0, value + delta), end));
  const nudgeEnd = (delta: number) => setEnd((value) => Math.max(Math.min(last, value + delta), start));
  const percent = (index: number) => (index / last) * 100;

  return (
    <div className="rounded-2xl border border-cal/10 bg-pista p-4">
      <div className="grid grid-cols-2 divide-x divide-cal/10">
        <TimeStepper
          label="Desde"
          value={TIMES[start]}
          onMinus={() => nudgeStart(-1)}
          onPlus={() => nudgeStart(1)}
          canMinus={start > 0}
          canPlus={start < end}
        />
        <TimeStepper
          label="Hasta"
          value={TIMES[end]}
          onMinus={() => nudgeEnd(-1)}
          onPlus={() => nudgeEnd(1)}
          canMinus={end > start}
          canPlus={end < last}
        />
      </div>

      <div className="relative mx-3 mt-5 h-7">
        <div className="absolute inset-x-0 top-1/2 h-1.5 -translate-y-1/2 rounded-full bg-cal/10" />
        <div
          className="absolute top-1/2 h-1.5 -translate-y-1/2 rounded-full bg-ladrillo"
          style={{ left: `${percent(start)}%`, right: `${100 - percent(end)}%` }}
        />
        <input
          type="range"
          min={0}
          max={last}
          step={1}
          value={start}
          onChange={(event) => moveStart(Number(event.target.value))}
          aria-label="Desde"
          aria-valuetext={TIMES[start]}
          className="rango-doble"
          // Con las dos manijas juntas al final, la de "desde" tiene que quedar
          // arriba: si no, la tapa la de "hasta" y no hay forma de agarrarla.
          style={{ zIndex: start > last - 2 ? 3 : 2 }}
        />
        <input
          type="range"
          min={0}
          max={last}
          step={1}
          value={end}
          onChange={(event) => moveEnd(Number(event.target.value))}
          aria-label="Hasta"
          aria-valuetext={TIMES[end]}
          className="rango-doble"
        />
      </div>
      <div aria-hidden className="relative mx-3 mt-1 h-4 text-[0.65rem] tabular-nums text-ink-mute">
        {RANGE_TICKS.map((index) => (
          <span key={index} className="absolute -translate-x-1/2" style={{ left: `${percent(index)}%` }}>
            {TIMES[index]}
          </span>
        ))}
      </div>

      <p className="mt-4 text-center text-xs text-ink-soft">
        Turnos que <span className="font-semibold text-cal">empiezan</span> entre las{' '}
        <span className="font-semibold tabular-nums text-cal">{TIMES[start]}</span> y las{' '}
        <span className="font-semibold tabular-nums text-cal">{TIMES[end]}</span>
      </p>
    </div>
  );
}

function TimeStepper({
  label,
  value,
  onMinus,
  onPlus,
  canMinus,
  canPlus,
}: {
  label: string;
  value: string;
  onMinus: () => void;
  onPlus: () => void;
  canMinus: boolean;
  canPlus: boolean;
}) {
  const stepButton =
    'grid size-8 shrink-0 place-items-center rounded-full border border-cal/10 text-lg leading-none text-ink-soft transition hover:border-cal/30 hover:text-cal disabled:opacity-30 disabled:hover:border-cal/10 disabled:hover:text-ink-soft sm:size-9';
  return (
    <div className="flex flex-col items-center px-2">
      <span className="eyebrow text-ink-mute">{label}</span>
      <div className="mt-1.5 flex items-center gap-1.5 sm:gap-3">
        <button
          type="button"
          onClick={onMinus}
          disabled={!canMinus}
          aria-label={`${label}: media hora antes`}
          className={stepButton}
        >
          −
        </button>
        <span className="display w-[4.5ch] text-center text-[1.75rem] leading-none tabular-nums sm:text-3xl">{value}</span>
        <button
          type="button"
          onClick={onPlus}
          disabled={!canPlus}
          aria-label={`${label}: media hora después`}
          className={stepButton}
        >
          +
        </button>
      </div>
    </div>
  );
}

/**
 * Cómo es la cancha: techo, paredes y piso. En Necochea conviven techadas y al
 * aire libre, blindex y pared, y algunas de las antiguas no tienen alfombra; para
 * quien juega no es lo mismo. Sin elegir nada, da igual y entran todas.
 *
 * <p>Tres desplegables en una fila, con "Todas" a la vista cuando no hay filtro:
 * ocupan una sola fila en vez de tres. El que tiene algo elegido se pinta lleno,
 * para que se note de un vistazo que está filtrando.
 */
function CourtFilter({
  wall,
  surface,
  roof,
  onWall,
  onSurface,
  onRoof,
}: {
  wall: CourtWall | null;
  surface: CourtSurface | null;
  roof: CourtRoof | null;
  onWall: (wall: CourtWall | null) => void;
  onSurface: (surface: CourtSurface | null) => void;
  onRoof: (roof: CourtRoof | null) => void;
}) {
  return (
    <div className="space-y-3">
      <p className="eyebrow text-ink-soft">Cancha</p>
      {/* Cada uno del ancho de su opción más larga y no tres columnas iguales: en
          360px, "Al aire libre" no entraba en un tercio. */}
      <div className="flex gap-1">
        <FeatureDropdown
          label="Techo"
          align="left"
          options={[
            { value: null, label: 'Todas', hint: 'Techadas y al aire libre' },
            { value: 'COVERED', label: ROOF_LABEL.COVERED, hint: 'Bajo techo' },
            { value: 'OUTDOOR', label: ROOF_LABEL.OUTDOOR, hint: 'Sin techo' },
          ]}
          value={roof}
          onChange={onRoof}
        />
        <FeatureDropdown
          label="Paredes"
          align="center"
          options={[
            { value: null, label: 'Todas', hint: 'Blindex y pared' },
            { value: 'GLASS', label: WALL_LABEL.GLASS, hint: 'Paredes de vidrio' },
            { value: 'WALL', label: WALL_LABEL.WALL, hint: 'Paredes de material' },
          ]}
          value={wall}
          onChange={onWall}
        />
        <FeatureDropdown
          label="Piso"
          align="right"
          options={[
            { value: null, label: 'Todos', hint: 'Alfombra y cemento' },
            { value: 'CARPET', label: SURFACE_LABEL.CARPET, hint: 'Césped sintético' },
            { value: 'NO_CARPET', label: SURFACE_LABEL.NO_CARPET, hint: 'Sin alfombra' },
          ]}
          value={surface}
          onChange={onSurface}
        />
      </div>
    </div>
  );
}

/**
 * Un desplegable propio, con el estilo de la página: el nativo abre una lista
 * que dibuja el sistema (en Windows, un recuadro gris de otra época) y no se le
 * puede cambiar el aspecto.
 *
 * <p>Cada opción lleva una línea que la explica ("Paredes de vidrio"): quien no
 * sabe qué es blindex lo entiende sin salir de la búsqueda. Se maneja con el
 * teclado como un desplegable común (flechas, Enter, Escape) y se cierra tocando
 * afuera.
 *
 * @param align de qué lado se abre la lista, para que la de la punta derecha no
 *              se salga de la pantalla en el teléfono.
 */
function FeatureDropdown<T extends string>({
  label,
  options,
  value,
  onChange,
  align,
}: {
  label: string;
  /** La primera es la de "me da igual", con valor nulo. */
  options: { value: T | null; label: string; hint: string }[];
  value: T | null;
  onChange: (value: T | null) => void;
  align: 'left' | 'center' | 'right';
}) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const items = useRef<(HTMLButtonElement | null)[]>([]);
  const labelId = useId();
  const listId = useId();

  const selectedIndex = Math.max(0, options.findIndex((option) => option.value === value));
  const selected = options[selectedIndex];
  const filtering = value !== null;

  // Tocar afuera cierra, como cualquier menú.
  useEffect(() => {
    if (!open) {
      return;
    }
    const onPointerDown = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [open]);

  // Al abrir, el foco va a la opción elegida: las flechas arrancan desde ahí.
  useEffect(() => {
    if (open) {
      items.current[selectedIndex]?.focus();
    }
  }, [open, selectedIndex]);

  const close = () => {
    setOpen(false);
    trigger.current?.focus();
  };

  const choose = (next: T | null) => {
    onChange(next);
    close();
  };

  const onItemKeyDown = (event: React.KeyboardEvent, index: number) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      close();
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      items.current[(index + 1) % options.length]?.focus();
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      items.current[(index - 1 + options.length) % options.length]?.focus();
    } else if (event.key === 'Tab') {
      setOpen(false);
    }
  };

  const position = align === 'left' ? 'left-0' : align === 'right' ? 'right-0' : 'left-1/2 -translate-x-1/2';

  return (
    <div ref={root} className="relative min-w-0 flex-auto">
      <p id={labelId} className="eyebrow mb-1.5 text-ink-mute">
        {label}
      </p>
      <button
        ref={trigger}
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        onClick={() => setOpen(!open)}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' && !open) {
            event.preventDefault();
            setOpen(true);
          }
        }}
        className={`flex w-full items-center justify-between gap-1 rounded-xl border py-3 pl-2 pr-1.5 text-xs font-bold transition ${
          filtering
            ? 'border-cal bg-cal text-pista'
            : `border-cal/10 bg-pista text-cal hover:border-cal/30 ${open ? 'border-cal/30' : ''}`
        }`}
      >
        <span className="truncate">{selected.label}</span>
        <svg
          aria-hidden
          viewBox="0 0 12 12"
          className={`size-2.5 shrink-0 transition-transform duration-200 ${open ? 'rotate-180' : ''} ${
            filtering ? 'text-pista' : 'text-ink-soft'
          }`}
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M3 4.5 6 7.5 9 4.5" />
        </svg>
      </button>

      {open && (
        <div
          id={listId}
          role="listbox"
          aria-labelledby={labelId}
          className={`absolute top-full z-30 mt-2 w-56 max-w-[calc(100vw-2.5rem)] rounded-2xl border border-cal/10 bg-vidrio-alto p-1.5 [box-shadow:0_18px_40px_rgba(0,0,0,0.55)] ${position}`}
        >
          {options.map((option, index) => {
            const isSelected = index === selectedIndex;
            return (
              <button
                key={option.label}
                ref={(element) => {
                  items.current[index] = element;
                }}
                type="button"
                role="option"
                aria-selected={isSelected}
                onClick={() => choose(option.value)}
                onKeyDown={(event) => onItemKeyDown(event, index)}
                className={`flex w-full items-center gap-3 rounded-xl px-3 py-2 text-left transition hover:bg-cal/[0.06] focus:bg-cal/[0.06] focus:outline-none ${
                  isSelected ? 'text-cal' : 'text-ink-soft hover:text-cal'
                }`}
              >
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-semibold">{option.label}</span>
                  <span className="mt-0.5 block text-xs text-ink-mute">{option.hint}</span>
                </span>
                {isSelected && (
                  <svg
                    aria-hidden
                    viewBox="0 0 16 16"
                    className="size-4 shrink-0 text-ladrillo-claro"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="M3.5 8.5 6.5 11.5 12.5 4.5" />
                  </svg>
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

function LocalityChip({
  active,
  label,
  count,
  onClick,
}: {
  active: boolean;
  label: string;
  count: number;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={`flex items-center gap-2 rounded-full border py-2 pl-3.5 pr-2 text-sm font-semibold transition ${
        active ? 'border-cal bg-cal text-pista' : 'border-cal/10 bg-pista text-cal hover:border-cal/30'
      }`}
    >
      <PinGlyph className={`size-3.5 ${active ? 'text-pista/70' : 'text-ladrillo-claro'}`} />
      {label}
      <span
        className={`grid min-w-6 place-items-center rounded-full px-1.5 py-0.5 text-[0.7rem] tabular-nums ${
          active ? 'bg-pista/15 text-pista' : 'bg-cal/[0.08] text-ink-soft'
        }`}
      >
        {count}
      </span>
    </button>
  );
}

function ClubChip({ active, onClick, children }: { active: boolean; onClick: () => void; children: string }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
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

function LocalitySection({
  section,
  clubs,
  date,
  courtQuery,
}: {
  section: Section;
  clubs: ClubOption[];
  date: string;
  courtQuery: string;
}) {
  const groups = groupByClub(section.matches, clubs);
  return (
    <section aria-label={section.name}>
      <header className="mb-4 flex flex-col gap-1 border-b border-cal/10 pb-3 sm:flex-row sm:items-end sm:justify-between sm:gap-3">
        <div className="min-w-0">
          {section.region && <p className="eyebrow text-ink-mute">{section.region}</p>}
          <h3 className="mt-1 flex items-center gap-2 text-3xl">
            <PinGlyph className="size-5 shrink-0 text-ladrillo-claro" />
            <span>{section.name}</span>
          </h3>
        </div>
        <p className="shrink-0 text-sm text-ink-soft tabular-nums sm:pb-1">
          {groups.length === 1 ? '1 club' : `${groups.length} clubes`} · {resultsTitle(section.matches)}
        </p>
      </header>
      <ul className="space-y-4">
        {groups.map((group, index) => (
          <li key={group.slug}>
            <ClubCard club={group} date={date} position={index + 1} courtQuery={courtQuery} />
          </li>
        ))}
      </ul>
    </section>
  );
}

/**
 * Un club con todos sus horarios libres del rango.
 *
 * <p>La cabecera lleva a la página del club sin horario elegido, para quien quiere
 * mirar antes; cada horario de abajo lleva directo a reservar ese turno. Son links
 * separados y no uno alrededor de toda la tarjeta, porque un link no puede tener
 * otros adentro.
 *
 * <p>Van todos los horarios a la vista, sin "ver más": en un rango de noche son
 * cuatro o cinco, y con "todo el día" siguen entrando en pocas filas de fichas.
 */
function ClubCard({
  club,
  distanceKm,
  date,
  position,
  courtQuery,
}: {
  club: ClubResults;
  /**
   * Solo en el orden por cercanía: la distancia al jugador, o null si el club no
   * cargó su ubicación. Sin la prop, la tarjeta está en el orden por defecto.
   */
  distanceKm?: ClubByDistance['distanceKm'];
  date: string;
  /** Lugar de la tarjeta en su localidad (o en la lista por cercanía), desde 1: lo registra la analítica. */
  position: number;
  /** Paredes y piso pedidos, para que el club preelija una cancha que los cumpla. */
  courtQuery: string;
}) {
  // El lugar en la lista para la analítica. Por cercanía se marca aparte: el primero
  // por distancia no es el primero del orden por defecto, y mezclarlos ensuciaría la métrica.
  const detail = distanceKm === undefined ? String(position) : `cercania:${position}`;
  const first = club.matches[0];
  const { walls, surfaces, roofs } = clubFeatures(club.matches);
  const minutes = slotMinutes(club.matches);
  // La ciudad va porque una zona puede tener más de una; después la duración, que
  // es lo que permite comparar precios entre clubes de 60 y de 90 minutos, y al
  // final cómo son las canchas. En ese orden: en el teléfono la línea se corta por
  // el final, y lo que se pierde es lo menos necesario para elegir.
  const place = [
    club.city ? townOf(club.city) : null,
    minutes !== null ? `Turnos de ${minutes} min` : null,
    ...featuresSummary(walls, surfaces, roofs),
  ]
    .filter(Boolean)
    .join(' · ');
  const cheapest = Math.min(...club.matches.map((match) => match.cheapestPrice));
  const hasPromo = club.matches.some((match) => match.promo);

  return (
    <article
      style={{ animationDelay: `${Math.min(position - 1, 8) * 60}ms` }}
      className="ficha-in overflow-hidden rounded-3xl border border-cal/10 bg-vidrio"
    >
      <Link
        to={`/club/${club.slug}?fecha=${date}${courtQuery}`}
        onClick={() => track('search_result_click', { clubSlug: club.slug, detail })}
        className="group flex items-center gap-4 p-3 pr-4 transition hover:bg-vidrio-alto focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ladrillo"
      >
        <ClubPhoto name={club.name} url={club.heroImageUrl} />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h4 className="display truncate text-[1.7rem] leading-none tracking-[0.04em]">{club.name}</h4>
            {hasPromo && <Badge tone="promo">Promo</Badge>}
          </div>
          <p className="mt-1.5 text-xs text-ink-soft">
            Desde{' '}
            <span className="font-semibold tabular-nums text-cal">{perPerson(cheapest, first.playersPerCourt)}</span>{' '}
            c/u
          </p>
          {(place || typeof distanceKm === 'number') && (
            <p className="mt-0.5 truncate text-xs text-ink-mute">
              {/* Primero, porque es por lo que se ordenó: si la línea se corta, se corta lo otro. */}
              {typeof distanceKm === 'number' && (
                <span className="font-semibold text-ink-soft">{formatDistance(distanceKm)}</span>
              )}
              {typeof distanceKm === 'number' && place && ' · '}
              {place}
            </p>
          )}
        </div>
        <span
          aria-hidden
          className="grid size-9 shrink-0 place-items-center rounded-full border border-cal/10 text-ink-soft transition group-hover:border-ladrillo group-hover:bg-ladrillo group-hover:text-cal"
        >
          →
        </span>
      </Link>

      <ul className="grid grid-cols-4 gap-1.5 border-t border-cal/10 p-3 sm:grid-cols-6 md:grid-cols-7">
        {club.matches.map((match) => (
          <li key={match.startsAt}>
            <SlotChip match={match} date={date} detail={detail} courtQuery={courtQuery} />
          </li>
        ))}
      </ul>
    </article>
  );
}

/**
 * Un horario libre, listo para tocar: la hora grande, el precio por persona y una
 * canchita por cada cancha que queda. El precio va en cada ficha y no solo el
 * "desde" de la cabecera, porque de noche suele ser otro. Al pasar por encima se
 * da vuelta a blanco, como el día elegido en la tira; las de promo van teñidas.
 */
function SlotChip({
  match,
  date,
  detail,
  courtQuery,
}: {
  match: SearchMatch;
  date: string;
  /** El lugar de la tarjeta del club en la lista, tal como lo registra la analítica. */
  detail: string;
  courtQuery: string;
}) {
  const price = perPerson(match.cheapestPrice, match.playersPerCourt);
  const free = match.freeCourts === 1 ? '1 cancha libre' : `${match.freeCourts} canchas libres`;
  return (
    <Link
      to={`/club/${match.clubSlug}?fecha=${date}&hora=${match.startTime}${courtQuery}`}
      onClick={() =>
        track('search_result_click', {
          clubSlug: match.clubSlug,
          slotAt: match.startsAt,
          // En qué lugar de su localidad (o de la lista por cercanía) estaba la
          // tarjeta del club que eligió.
          detail,
        })
      }
      aria-label={`${match.startTime} a ${match.endTime}, ${price} por persona${match.promo ? ', en promo' : ''}, ${free}`}
      className={`group flex flex-col items-center rounded-2xl border px-1 pb-2 pt-2.5 transition duration-200 hover:-translate-y-0.5 hover:border-cal hover:bg-cal hover:text-pista focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ladrillo ${
        match.promo ? 'border-ladrillo/45 bg-ladrillo/[0.08]' : 'border-cal/10 bg-pista'
      }`}
    >
      <span className="display text-[1.6rem] leading-none tabular-nums">{match.startTime}</span>
      <span
        className={`mt-1 text-[0.68rem] font-semibold tabular-nums group-hover:text-pista/70 ${
          match.promo ? 'text-ladrillo-claro' : 'text-ink-soft'
        }`}
      >
        {price}
      </span>
      <CourtMarks count={match.freeCourts} size="sm" className="mt-1.5" />
    </Link>
  );
}

/**
 * La foto de portada del club, recortada al cuadrado, encima de sus iniciales.
 * Las iniciales se ven mientras la foto carga y se quedan si no cargó ninguna o
 * la URL externa dejó de responder: una tarjeta con un cuadrado vacío o el ícono
 * de imagen rota se lee como un club abandonado.
 */
function ClubPhoto({ name, url }: { name: string; url: string | null }) {
  const [state, setState] = useState<'loading' | 'loaded' | 'broken'>('loading');
  return (
    <span
      aria-hidden
      className="display relative grid size-16 shrink-0 place-items-center overflow-hidden rounded-2xl bg-ladrillo/15 text-[1.75rem] text-ladrillo-claro sm:size-[4.5rem]"
    >
      {clubInitials(name)}
      {url && state !== 'broken' && (
        <img
          src={url}
          alt=""
          width={72}
          height={72}
          loading="lazy"
          decoding="async"
          onLoad={() => setState('loaded')}
          onError={() => setState('broken')}
          className={`absolute inset-0 size-full object-cover transition-opacity duration-300 ${
            state === 'loaded' ? 'opacity-100' : 'opacity-0'
          }`}
        />
      )}
    </span>
  );
}

/** Una canchita por cada cancha libre (hasta cuatro), para leer la cantidad de un vistazo. */
function CourtMarks({
  count,
  size = 'md',
  className = '',
}: {
  count: number;
  size?: 'sm' | 'md';
  className?: string;
}) {
  const shown = Math.min(count, 4);
  const mark = size === 'sm' ? 'h-2 w-3 rounded-[2px]' : 'h-2.5 w-4 rounded-[3px]';
  return (
    <span aria-hidden className={`flex gap-0.5 ${className}`}>
      {Array.from({ length: shown }, (_, index) => (
        <span key={index} className={`relative border border-ladrillo-claro/70 ${mark}`}>
          <span className="absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-ladrillo-claro/70" />
        </span>
      ))}
    </span>
  );
}

function EmptyResults({
  date,
  todoElDia,
  otherLocalities,
  courtFiltered,
  onAllDay,
  onNextDay,
  onAllLocalities,
  onAnyCourt,
}: {
  date: string;
  todoElDia: boolean;
  otherLocalities: boolean;
  courtFiltered: boolean;
  onAllDay: () => void;
  onNextDay: () => void;
  onAllLocalities: () => void;
  onAnyCourt: () => void;
}) {
  return (
    <div className="rounded-3xl border border-dashed border-cal/15 px-6 py-10 text-center">
      <span className="mx-auto grid size-14 place-items-center rounded-full bg-cal/[0.06] text-ink-soft">
        <CourtMarks count={1} />
      </span>
      <h2 className="mt-5 text-2xl">No quedan canchas libres</h2>
      <p className="mx-auto mt-2 max-w-xs text-sm text-ink-soft first-letter:uppercase">
        {longDate(date)}, con esos filtros.
      </p>
      <div className="mx-auto mt-6 max-w-xs space-y-2.5">
        {otherLocalities && (
          <Button variant="primary" onClick={onAllLocalities}>
            Ver en todas las localidades
          </Button>
        )}
        {courtFiltered && (
          <Button variant={otherLocalities ? 'secondary' : 'primary'} onClick={onAnyCourt}>
            Cualquier cancha
          </Button>
        )}
        {!todoElDia && (
          <Button variant="secondary" onClick={onAllDay}>
            Buscar en todo el día
          </Button>
        )}
        <Button variant="secondary" onClick={onNextDay}>
          Probar el día siguiente
        </Button>
      </div>
    </div>
  );
}

/** Con la forma de las tarjetas de club, para que la página no salte cuando llegan. */
function ResultsSkeleton() {
  return (
    <div className="space-y-4" aria-label="Buscando canchas…">
      {[0, 1].map((index) => (
        <div
          key={index}
          className="animate-pulse overflow-hidden rounded-3xl border border-cal/10 bg-vidrio"
          style={{ animationDelay: `${index * 120}ms` }}
        >
          <div className="flex items-center gap-4 p-3">
            <div className="size-16 shrink-0 rounded-2xl bg-cal/[0.06] sm:size-[4.5rem]" />
            <div className="flex-1 space-y-2">
              <div className="h-5 w-2/5 rounded bg-cal/[0.08]" />
              <div className="h-3 w-3/5 rounded bg-cal/[0.05]" />
            </div>
          </div>
          <div className="grid grid-cols-4 gap-1.5 border-t border-cal/10 p-3 sm:grid-cols-6 md:grid-cols-7">
            {[0, 1, 2, 3].map((chip) => (
              <div key={chip} className="h-[4.4rem] rounded-2xl bg-cal/[0.05]" />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

function PinGlyph({ className }: { className?: string }) {
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
      <path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 1 1 13 0c0 5.4-6.5 11-6.5 11Z" />
      <circle cx="12" cy="10" r="2.3" />
    </svg>
  );
}

type Locality = { key: string; name: string; region: string | null };
type LocalityCount = Locality & { matches: number };
type Section = Locality & { matches: SearchMatch[] };

const NO_LOCALITY: Locality = { key: 'otras', name: 'Otras localidades', region: null };

/**
 * Ciudades distintas que para el jugador son un mismo lugar, por el nombre de la
 * ciudad sin tildes. Necochea y Quequén están pegadas, separadas solo por el río:
 * partirlas en dos secciones escondía la mitad de las canchas a quien busca "en
 * Necochea". Una ciudad nueva que se sume a una zona va acá.
 */
const ZONAS: Record<string, { key: string; name: string }> = {
  necochea: { key: 'necochea-quequen', name: 'Necochea y Quequén' },
  quequen: { key: 'necochea-quequen', name: 'Necochea y Quequén' },
};

/** El nombre de la zona a partir de su clave en la URL, para el título del SEO (ver el `useEffect` de arriba). */
function zoneNameByKey(key: string): string | null {
  return Object.values(ZONAS).find((zona) => zona.key === key)?.name ?? null;
}

/**
 * La localidad de un club, a partir de la ciudad que carga en el panel: "Necochea,
 * Buenos Aires" se muestra "Necochea" con la provincia aparte. La clave va sin
 * tildes ni espacios porque viaja en la URL (?localidad=necochea-buenos-aires).
 */
function localityOf(city: string | null): Locality {
  const clean = city?.trim();
  if (!clean) {
    return NO_LOCALITY;
  }
  const [name, ...rest] = clean.split(',').map((part) => part.trim());
  const region = rest.filter(Boolean).join(', ') || null;
  const zona = ZONAS[slugify(name)];
  return zona ? { ...zona, region } : { key: slugify(clean), name, region };
}

/** Techo, paredes y piso pedidos, listos para colgar del link al club: "&paredes=blindex". */
function courtQuery(wall: CourtWall | null, surface: CourtSurface | null, roof: CourtRoof | null): string {
  return (
    (wall ? `&paredes=${wallParam(wall)}` : '') +
    (surface ? `&piso=${surfaceParam(surface)}` : '') +
    (roof ? `&techo=${roofParam(roof)}` : '')
  );
}

/** "Quequén, Buenos Aires" → "Quequén". */
function townOf(city: string): string {
  return city.split(',')[0].trim();
}

/** Las localidades con al menos un club, en orden alfabético y "otras" al final. */
function buildLocalities(clubs: ClubOption[], matches: SearchMatch[]): LocalityCount[] {
  const byKey = new Map<string, LocalityCount>();
  for (const club of clubs) {
    const locality = localityOf(club.city);
    if (!byKey.has(locality.key)) {
      byKey.set(locality.key, { ...locality, matches: 0 });
    }
  }
  for (const match of matches) {
    const entry = byKey.get(localityOf(match.city).key);
    if (entry) {
      entry.matches += 1;
    }
  }
  return [...byKey.values()].sort(compareLocalities);
}

/**
 * Un solo botón que prende y apaga el orden por cercanía; apagado, queda el orden
 * por defecto (más horarios primero), que no necesita botón. Mientras se busca la
 * ubicación el botón lo dice y no se puede volver a tocar: el navegador ya está
 * mostrando (o resolviendo) el pedido de permiso.
 */
function NearbyToggle({
  active,
  locating,
  onToggle,
}: {
  active: boolean;
  locating: boolean;
  onToggle: () => void;
}) {
  return (
    <>
      <button
        type="button"
        aria-pressed={active}
        aria-busy={locating}
        disabled={locating}
        onClick={onToggle}
        className={`inline-flex items-center gap-1.5 rounded-full border px-3.5 py-2 text-xs font-bold transition disabled:cursor-wait ${
          active
            ? 'border-cal bg-cal text-pista'
            : 'border-cal/15 text-ink-soft hover:border-cal/40 hover:text-cal'
        }`}
      >
        <PinGlyph className="size-3.5 shrink-0" />
        {locating ? 'Buscando tu ubicación…' : 'Ordenar por cercanía'}
      </button>
      <span className="sr-only" aria-live="polite">
        {locating ? 'Buscando tu ubicación…' : ''}
      </span>
    </>
  );
}

/** Si activó la cercanía en esta pestaña. Sin sessionStorage (modo privado, bloqueado) queda apagada. */
function readNearby(): boolean {
  try {
    return sessionStorage.getItem(CERCANIA_KEY) === 'cercania';
  } catch {
    return false;
  }
}

function saveNearby(on: boolean) {
  try {
    if (on) {
      sessionStorage.setItem(CERCANIA_KEY, 'cercania');
    } else {
      sessionStorage.removeItem(CERCANIA_KEY);
    }
  } catch {
    // Sin almacenamiento, la elección dura hasta recargar: no vale un error.
  }
}

/** Los resultados agrupados por localidad, cada grupo en el orden por horario que manda el backend. */
function buildSections(matches: SearchMatch[], onlyKey: string | null): Section[] {
  const byKey = new Map<string, Section>();
  for (const match of matches) {
    const locality = localityOf(match.city);
    if (onlyKey && locality.key !== onlyKey) {
      continue;
    }
    const section = byKey.get(locality.key) ?? { ...locality, matches: [] };
    section.matches.push(match);
    byKey.set(locality.key, section);
  }
  return [...byKey.values()].sort(compareLocalities);
}

function compareLocalities(a: Locality, b: Locality): number {
  if (a.key === NO_LOCALITY.key) {
    return 1;
  }
  if (b.key === NO_LOCALITY.key) {
    return -1;
  }
  return a.name.localeCompare(b.name, 'es');
}

function slugify(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}

/** "sáb", "dom": el día de la semana corto, sin el punto de la abreviatura. */
function weekday(isoDate: string): string {
  return new Intl.DateTimeFormat('es-AR', { weekday: 'short' })
    .format(new Date(`${isoDate}T12:00:00`))
    .replace('.', '');
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
