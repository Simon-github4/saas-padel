import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api, ApiError, type Availability, type Slot } from '../api/client';
import { addDays, clockTime, longDate, perPerson, todayIso, whatsappLink } from '../format';
import { setPageMeta } from '../seo';
import {
  Alert,
  Badge,
  Button,
  FloatingWhatsapp,
  Loading,
  Screen,
  SectionTitle,
  SiteFooter,
  StepIndicator,
  TopBar,
} from '../components/Ui';
import { MonthCalendar } from '../components/MonthCalendar';
import { AccountButton } from '../components/AccountButton';
import { WaitlistForm } from '../components/WaitlistForm';
import { Checkout } from './Checkout';
import { HeroSection } from './sections/HeroSection';
import { PriceSection } from './sections/PriceSection';
import { ServicesSection } from './sections/ServicesSection';
import { HowToGetThereSection } from './sections/HowToGetThereSection';

/**
 * Portada del club, en una sola página con scroll.
 *
 * <p>Portada, precio y reserva en tres pasos conviven en la misma página, para
 * que el jugador no tenga que abrir modales ni cambiar de pantalla. El
 * checkout —hoy un modal— es el paso 3.
 */
export function ClubPage() {
  const { slug = '' } = useParams();
  const navigate = useNavigate();
  // La busqueda global manda el dia y el horario ya elegidos: ?fecha=&hora=
  const [search] = useSearchParams();
  const linkedDate = validDate(search.get('fecha'));
  const linkedTime = search.get('hora');
  const [date, setDate] = useState(linkedDate ?? todayIso());
  const [data, setData] = useState<Availability | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Slot | null>(null);
  const [step, setStep] = useState<1 | 2 | 3>(linkedTime ? 2 : 1);
  const [linkExpired, setLinkExpired] = useState(false);
  const reserva = useRef<HTMLElement>(null);
  const mounted = useRef(false);
  const linkApplied = useRef(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await api.availability(slug, date));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo cargar la disponibilidad.');
    } finally {
      setLoading(false);
    }
  }, [slug, date]);

  useEffect(() => {
    void load();
  }, [load]);

  // La paleta la elige el club: paleta clara/oscura y los dos acentos de
  // marca se aplican como custom properties en <html>, asi las toma toda la
  // hoja de estilos sin repetir la logica en cada seccion. Se limpia al salir
  // de la pagina para no dejarle el tema de un club pegado a otra ruta.
  useEffect(() => {
    const club = data?.club;
    if (!club) {
      return;
    }
    const root = document.documentElement;
    root.dataset.theme = club.themeMode === 'LIGHT' ? 'light' : 'dark';
    if (club.primaryColor) {
      root.style.setProperty('--color-ladrillo', club.primaryColor);
    }
    if (club.secondaryColor) {
      root.style.setProperty('--color-ladrillo-claro', club.secondaryColor);
    }
    return () => {
      delete root.dataset.theme;
      root.style.removeProperty('--color-ladrillo');
      root.style.removeProperty('--color-ladrillo-claro');
    };
  }, [data?.club]);

  // Sin esto, Google ve el mismo título y descripción genéricos de
  // index.html en la página de cada club, y un resultado de búsqueda no
  // puede distinguir "Simon Padel" de cualquier otro.
  useEffect(() => {
    const club = data?.club;
    if (!club) {
      return;
    }
    const where = club.city ? ` en ${club.city}` : '';
    const pitch = club.tagline ? `${club.tagline}. ` : '';
    return setPageMeta(
      `${club.name} — Reservá tu cancha de pádel`,
      `${pitch}Reservá tu cancha de pádel online${where} con ${club.name}, sin llamar ni escribir por WhatsApp.`,
    );
  }, [data?.club]);

  // Elegir un día avanza a la grilla de horarios (paso 2). Cambiar de día
  // además descarta el turno que se hubiera elegido: ya no aplica.
  const handleDaySelect = useCallback((day: string) => {
    setSelected(null);
    setStep(2);
    setDate(day);
  }, []);

  useEffect(() => {
    setSelected(null);
  }, [date]);

  // Turno preseleccionado desde la busqueda global: se salta directo a los datos.
  //
  // Corre una sola vez. Sin el ref, cambiar de dia despues de entrar por el link
  // volveria a saltar al horario viejo, que en el dia nuevo no significa nada.
  //
  // Que el turno ya no este es el caso normal, no el raro: entre que el jugador vio
  // el resultado y toco la tarjeta pudo tomarlo cualquiera. Ahi se lo deja en la
  // grilla del dia, que es lo que necesita para elegir otro.
  useEffect(() => {
    if (!data || !linkedTime || linkApplied.current) {
      return;
    }
    linkApplied.current = true;
    const match = data.slots.find(
      (slot) => slot.startTime === linkedTime && slot.available.length > 0,
    );
    if (match) {
      setSelected(match);
      setStep(3);
    } else {
      setLinkExpired(true);
      setStep(2);
    }
  }, [data, linkedTime]);

  // Cambiar de paso mueve la vista al bloque de reserva: si el jugador venía
  // leyendo los servicios, el paso nuevo aparecía fuera de pantalla.
  useEffect(() => {
    if (!mounted.current) {
      mounted.current = true;
      return;
    }
    reserva.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [step]);

  if (loading && !data) {
    return (
      <Screen className="pt-6">
        <Loading />
      </Screen>
    );
  }

  if (error && !data) {
    return (
      <Screen className="pt-6">
        <div className="space-y-4">
          <Alert>{error}</Alert>
          <Button variant="secondary" onClick={() => void load()}>
            Reintentar
          </Button>
        </div>
      </Screen>
    );
  }

  const club = data!.club;
  const whatsapp = whatsappLink(club.whatsappNumber);
  const lastBookable = addDays(todayIso(), club.bookingHorizonDays);

  return (
    <Screen
      top={<TopBar name={club.name} whatsappHref={whatsapp} accountSlot={<AccountButton />} onTitleClick={() => navigate(`/club/${slug}`)} />}
    >
      <HeroSection
        name={club.name}
        tagline={club.tagline}
        heroImageUrl={club.heroImageUrl}
        heroHeadline={club.heroHeadline}
        heroCtaLabel={club.heroCtaLabel}
        heroOverlay={club.heroOverlay}
        heroVariant={club.heroVariant}
        address={club.address}
        courtCount={data!.courts.length}
      />

      {/*
        La zona de acción: el precio y la reserva sobre un piso propio, a
        sangre como la portada. Es lo único de la página que cambia de piso, y
        por eso se lee como la máquina y no como más folleto: el precio es el
        motivo por el que se reserva, así que van juntos.

        El piso se tiñe con el acento del club y no con un gris más claro
        porque sobre un fondo casi negro no queda margen para aclarar: el único
        gris que se notaría es justo el de las tarjetas, y entonces las
        tarjetas de adentro se perderían contra su propio piso. Al 4% el color
        separa por matiz en vez de por luminosidad y las deja despegadas.

        El id y el ref quedan en la sección de adentro a propósito: el botón de
        la portada apunta a #reserva y cada cambio de paso hace scrollIntoView.
        Los dos tienen que caer en el calendario, no en el precio.
      */}
      <div className="mx-[calc(50%-50vw)] mt-12 border-y border-cal/10 bg-ladrillo/[0.04] py-12 md:py-16">
        <div className="mx-auto w-full max-w-lg px-4 md:max-w-2xl">
          <PriceSection
            slots={data!.slots}
            playersPerCourt={club.playersPerCourt}
            timeZone={club.timeZone}
            slotDurationMinutes={data!.slotDurationMinutes}
          />

          {/* ----------------------------------------------- reserva */}
          <section id="reserva" ref={reserva} className="mt-10 scroll-mt-24">
            <SectionTitle title="Reservá tu cancha" subtitle="En tres pasos, sin registrarte." />

            <div className="mb-6 mt-5">
              <StepIndicator current={step} onGo={(target) => setStep(target as 1 | 2 | 3)} />
            </div>

            {/* Los títulos de cada paso van sin numerar: el indicador de arriba
                ya lleva la cuenta, y antes se veían dos círculos "1" seguidos. */}
            {step === 1 && (
              <div className="space-y-4">
                <SectionTitle title="Elegí el día" />
                <MonthCalendar
                  selected={date}
                  onSelect={handleDaySelect}
                  bookingHorizonDays={club.bookingHorizonDays}
                />
                <p className="px-1 text-xs text-ink-soft">
                  Se reserva hasta {club.bookingHorizonDays} días antes. Los días que superan ese
                  horizonte están deshabilitados.
                </p>
              </div>
            )}

            {step === 2 && (
              <div className="space-y-4">
                <SectionTitle title="Elegí la hora" />
                {linkExpired && (
                  <Alert tone="info">
                    Ese turno ya se ocupó. Estos son los que quedan libres.
                  </Alert>
                )}
                <HourGrid
                  slug={slug}
                  data={data!}
                  loading={loading}
                  canGoNextDay={addDays(date, 1) <= lastBookable}
                  onNextDay={() => setDate(addDays(date, 1))}
                  onSelect={(slot) => {
                    setSelected(slot);
                    setStep(3);
                  }}
                  onBack={() => setStep(1)}
                />
              </div>
            )}

            {step === 3 && selected && (
              <div className="space-y-4">
                <SectionTitle title="Tus datos" />
                <Checkout
                  slug={slug}
                  club={club}
                  slot={selected}
                  onBack={() => setStep(2)}
                  onSlotTaken={() => {
                    setSelected(null);
                    setStep(2);
                    void load();
                  }}
                />
              </div>
            )}

            {error && (
              <div className="mt-4 space-y-3">
                <Alert>{error}</Alert>
                <Button variant="secondary" onClick={() => void load()}>
                  Reintentar
                </Button>
              </div>
            )}
          </section>
        </div>
      </div>

      <ServicesSection amenities={club.amenities} />

      <HowToGetThereSection
        address={club.address}
        city={club.city}
        mapsUrl={club.mapsUrl}
        latitude={club.latitude}
        longitude={club.longitude}
      />

      <SiteFooter name={club.name} address={club.address} />

      <FloatingWhatsapp href={whatsapp} />
    </Screen>
  );
}

/**
 * Grilla de horarios del día, leída como un marcador de cancha.
 *
 * <p>Los horarios llenos ya no se ocultan: se muestran atenuados con la opción de
 * anotarse en la lista de espera. Antes desaparecían de la grilla en cuanto se
 * agotaban las canchas, y el jugador no tenía forma de saber que existían.
 */
function HourGrid({
  slug,
  data,
  loading,
  canGoNextDay,
  onNextDay,
  onSelect,
  onBack,
}: {
  slug: string;
  data: Availability;
  loading: boolean;
  canGoNextDay: boolean;
  onNextDay: () => void;
  onSelect: (slot: Slot) => void;
  onBack: () => void;
}) {
  const slots = data.slots;

  return (
    <div>
      <div className="mb-3 flex items-baseline justify-between gap-2">
        <p className="text-sm font-semibold first-letter:uppercase">{longDate(data.date)}</p>
        <button
          type="button"
          onClick={onBack}
          className="text-xs font-semibold text-ladrillo-claro underline-offset-4 hover:underline"
        >
          Cambiar día
        </button>
      </div>

      {loading && <GridSkeleton />}

      {/* El dia lleno es el punto mas frustrante del recorrido, asi que la
          salida va en primario: antes el aviso era la superficie mas tenue de
          la app y el unico camino hacia adelante, un boton de contorno. */}
      {!loading && slots.length === 0 && (
        <div className="space-y-3">
          <Alert tone="info">No quedan turnos libres para este día.</Alert>
          {canGoNextDay && <Button onClick={onNextDay}>Ver el día siguiente</Button>}
        </div>
      )}

      {!loading && slots.length > 0 && (
        <div className="grid grid-cols-2 gap-2 md:grid-cols-3 md:gap-3">
          {slots.map((slot, index) =>
            slot.available.length > 0 ? (
              <HourCard
                key={slot.startsAt}
                slot={slot}
                index={index}
                timeZone={data.club.timeZone}
                playersPerCourt={data.club.playersPerCourt}
                onSelect={() => onSelect(slot)}
              />
            ) : (
              <FullSlotCard
                key={slot.startsAt}
                slug={slug}
                slot={slot}
                index={index}
                timeZone={data.club.timeZone}
              />
            ),
          )}
        </div>
      )}
    </div>
  );
}

/** Mientras se recarga el día, la grilla mantiene su altura en vez de colapsar. */
function GridSkeleton() {
  return (
    <div className="grid grid-cols-2 gap-2 md:grid-cols-3 md:gap-3" aria-hidden>
      {Array.from({ length: 6 }, (_, index) => (
        <div
          key={index}
          className="h-28 animate-pulse rounded-2xl border border-cal/10 bg-vidrio"
        />
      ))}
    </div>
  );
}

function HourCard({
  slot,
  index,
  timeZone,
  playersPerCourt,
  onSelect,
}: {
  slot: Slot;
  index: number;
  timeZone: string;
  playersPerCourt: number;
  onSelect: () => void;
}) {
  const cheapest = Math.min(...slot.available.map((court) => court.price));
  const free = slot.available.length;

  return (
    <button
      type="button"
      onClick={onSelect}
      style={{ animationDelay: `${index * 40}ms` }}
      className={`ficha-in rounded-2xl border p-4 text-left transition ${
        slot.promo
          ? 'border-ladrillo/40 bg-ladrillo/[0.06] hover:border-ladrillo/70'
          : 'border-cal/10 bg-vidrio hover:border-cal/25 hover:bg-vidrio-alto'
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <span className="display text-3xl tabular-nums">{clockTime(slot.startsAt, timeZone)}</span>
        {slot.promo && <Badge tone="promo">Promo</Badge>}
      </div>
      <p
        className={`mt-2 text-sm font-bold tabular-nums ${
          slot.promo ? 'text-ladrillo-claro' : 'text-cal'
        }`}
      >
        {perPerson(cheapest, playersPerCourt)}
        <span className="font-normal text-ink-soft"> c/u</span>
      </p>
      {/* La frase se lleva la linea entera: al lado del total del turno se
          partia en dos y la ficha perdia el orden. El total ya esta arriba, en
          la tarjeta de precio, y otra vez en el resumen antes de confirmar. */}
      <p className="mt-3 text-[0.6875rem] font-semibold uppercase tracking-wide text-ink-soft max-[360px]:text-[0.625rem] max-[360px]:tracking-normal">
        {free === 1 ? '1 cancha libre' : `${free} canchas libres`}
      </p>
    </button>
  );
}

/** Horario sin canchas libres: se ofrece anotarse en vez de reservar. */
function FullSlotCard({
  slug,
  slot,
  index,
  timeZone,
}: {
  slug: string;
  slot: Slot;
  index: number;
  timeZone: string;
}) {
  const [open, setOpen] = useState(false);
  const [joined, setJoined] = useState(false);

  return (
    <div
      style={{ animationDelay: `${index * 40}ms` }}
      className="ficha-in rounded-2xl border border-cal/10 bg-vidrio/50 p-4 text-left"
    >
      <span className="display text-3xl tabular-nums text-ink-mute">
        {clockTime(slot.startsAt, timeZone)}
      </span>
      <p className="mt-2 text-sm font-semibold text-ink-mute">Sin cupo</p>

      {joined ? (
        <p className="mt-3 text-[0.6875rem] font-semibold uppercase tracking-wide text-ladrillo-claro">
          Anotado ✓
        </p>
      ) : open ? (
        <WaitlistForm slug={slug} startTime={slot.startsAt} onJoined={() => setJoined(true)} />
      ) : (
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="mt-3 text-[0.6875rem] font-semibold uppercase tracking-wide text-ladrillo-claro underline-offset-4 hover:underline"
        >
          Avisame si se libera
        </button>
      )}
    </div>
  );
}

/** Una fecha del link solo se acepta si tiene la forma que produce la busqueda. */
function validDate(value: string | null): string | null {
  return value && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : null;
}
