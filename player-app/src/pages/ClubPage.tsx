import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { track } from '../analytics';
import { api, ApiError, type Availability, type Slot } from '../api/client';
import { applyClubTheme, rememberClubTheme } from '../clubTheme';
import { roofFromParam, surfaceFromParam, wallFromParam } from '../courtFeatures';
import { addDays, clockTime, longDate, perPerson, todayIso, whatsappLink } from '../format';
import { setPageMeta, setStructuredData } from '../seo';
import {
  Alert,
  Badge,
  Button,
  type ClubInstagram,
  FloatingWhatsapp,
  Loading,
  Screen,
  SearchGlyph,
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
  // La busqueda global manda el dia y el horario ya elegidos: ?fecha=&hora=
  const [search] = useSearchParams();
  const linkedDate = validDate(search.get('fecha'));
  const linkedTime = search.get('hora');
  // Paredes, piso y techo pedidos en la búsqueda: la reserva arranca en una cancha así.
  const linkedWall = wallFromParam(search.get('paredes'));
  const linkedSurface = surfaceFromParam(search.get('piso'));
  const linkedRoof = roofFromParam(search.get('techo'));
  // La vuelta del login desde "Avisame si se libera" manda ?fecha=&espera=:
  // el jugador cae en la grilla de ese día con el formulario de ese horario
  // abierto, en vez de tener que buscarlo de nuevo. Sin fecha no significa nada.
  const linkedWaitlist = linkedDate ? search.get('espera') : null;
  const [date, setDate] = useState(linkedDate ?? todayIso());
  const [data, setData] = useState<Availability | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Slot | null>(null);
  const [step, setStep] = useState<1 | 2 | 3>(linkedTime || linkedWaitlist ? 2 : 1);
  // Se consume una sola vez: la ficha lo avisa al abrirse, y así volver a este
  // día más tarde no reabre el formulario solo.
  const [waitlistToOpen, setWaitlistToOpen] = useState(linkedWaitlist);
  // Por qué el jugador volvió a la grilla sin el turno que había elegido. Sin
  // esto, perder el turno al confirmar se veía como una recarga sin motivo.
  const [slotNotice, setSlotNotice] = useState<SlotNotice | null>(null);
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

  // La paleta la elige el club (ver clubTheme.ts). Se anota además para que
  // "Ver turnos" se vea como este club.
  useEffect(() => {
    const club = data?.club;
    if (!club) {
      return;
    }
    rememberClubTheme(club);
    return applyClubTheme(club);
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

  // Datos estructurados: sin esto Google tiene que adivinar, leyendo el
  // texto de la página, que esto es un club de pádel en tal dirección. Con
  // esto se lo decimos directo, y habilita un resultado con más que el link
  // pelado (mapa, dirección, teléfono).
  //
  // SportsClub y no SportsActivityLocation: probado con la herramienta de
  // resultados enriquecidos de Google, que no reconoce nada por fuera de
  // LocalBusiness y sus subtipos -- SportsActivityLocation es de la rama
  // Place/CivicStructure de schema.org, no de LocalBusiness, así que
  // quedaba invisible para Google aunque el JSON-LD fuera válido.
  // SportsClub sí es subtipo de LocalBusiness, y su propia descripción en
  // schema.org nombra clubes de tenis/raqueta -- el caso más parecido a
  // un club de pádel que hay.
  useEffect(() => {
    const club = data?.club;
    if (!club) {
      return;
    }
    const jsonLd: Record<string, unknown> = {
      '@context': 'https://schema.org',
      '@type': 'SportsClub',
      name: club.name,
      url: `${window.location.origin}/club/${slug}`,
      telephone: club.whatsappNumber,
    };
    if (club.tagline) {
      jsonLd.description = club.tagline;
    }
    if (club.heroImageUrl) {
      jsonLd.image = new URL(club.heroImageUrl, window.location.origin).toString();
    }
    if (club.address || club.city) {
      jsonLd.address = {
        '@type': 'PostalAddress',
        ...(club.address ? { streetAddress: club.address } : {}),
        ...(club.city ? { addressLocality: club.city } : {}),
        addressCountry: 'AR',
      };
    }
    if (club.latitude != null && club.longitude != null) {
      jsonLd.geo = {
        '@type': 'GeoCoordinates',
        latitude: club.latitude,
        longitude: club.longitude,
      };
    }
    // sameAs le dice a Google que ese perfil es del mismo club, y puede
    // mostrarlo junto al resultado de búsqueda.
    if (club.instagramUrl) {
      jsonLd.sameAs = [club.instagramUrl];
    }
    return setStructuredData(jsonLd);
  }, [data?.club, slug]);

  // Elegir un día avanza a la grilla de horarios (paso 2). Cambiar de día
  // además descarta el turno que se hubiera elegido: ya no aplica.
  const handleDaySelect = useCallback((day: string) => {
    setSelected(null);
    setSlotNotice(null);
    setStep(2);
    setDate(day);
  }, []);

  useEffect(() => {
    setSelected(null);
    setSlotNotice(null);
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
      // Vino de la búsqueda y el turno ya no estaba. Se anota aparte del
      // abandono: no es que no quiso reservar, es que no pudo.
      track('link_expired', { date, detail: linkedTime });
      setSlotNotice({
        tone: 'info',
        message: 'Ese turno ya se ocupó. Estos son los que quedan libres.',
      });
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

  // Cada paso que alcanza del flujo de reserva: es el embudo de la ficha, el
  // que dice en qué pantalla se cae la gente. El paso inicial no se anota
  // porque ya lo cuenta la visita a la página.
  const trackedStep = useRef(step);
  useEffect(() => {
    if (trackedStep.current === step) {
      return;
    }
    trackedStep.current = step;
    track('club_step', { step, date });
  }, [step, date]);

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
  // Con saludo y el link de la página: del otro lado saben de dónde viene la
  // consulta, sin que el jugador tenga que escribirlo.
  const whatsapp = whatsappLink(
    club.whatsappNumber,
    `Hola, te escribo desde tu página de reservas:\n${window.location.origin}/club/${club.slug}`,
  );
  const lastBookable = addDays(todayIso(), club.bookingHorizonDays);
  const instagram: ClubInstagram | null =
    club.instagramHandle && club.instagramUrl
      ? { handle: club.instagramHandle, url: club.instagramUrl }
      : null;
  // Los horarios libres de hoy para la portada. Solo si la página está parada
  // en hoy: si el jugador eligió otro día, estos datos son de ese día.
  const todaySlots =
    data!.date === todayIso() ? data!.slots.filter((slot) => slot.available.length > 0) : null;

  // El efecto de arriba lleva la vista a la reserva cuando cambia el paso. Desde
  // la portada se puede llegar al mismo paso en el que ya está (otro horario
  // estando en "Tus datos"), y ahí hay que moverla a mano.
  const scrollToReserva = (nextStep: 1 | 2 | 3) => {
    if (nextStep === step) {
      reserva.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  return (
    <Screen
      top={
        <TopBar
          name={club.name}
          whatsappHref={whatsapp}
          accountSlot={<AccountButton />}
          searchTo={searchHref(date)}
        />
      }
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
        instagram={instagram}
        courtCount={data!.courts.length}
        todaySlots={todaySlots}
        timeZone={club.timeZone}
        playersPerCourt={club.playersPerCourt}
        onPickSlot={(slot) => {
          track('slot_click', { slotAt: slot.startsAt, detail: 'portada' });
          setSelected(slot);
          setSlotNotice(null);
          scrollToReserva(3);
          setStep(3);
        }}
        onSeeToday={() => {
          scrollToReserva(2);
          handleDaySelect(todayIso());
        }}
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
                {slotNotice && (
                  <div role="alert">
                    <Alert tone={slotNotice.tone}>{slotNotice.message}</Alert>
                  </div>
                )}
                <HourGrid
                  slug={slug}
                  data={data!}
                  loading={loading}
                  canGoNextDay={addDays(date, 1) <= lastBookable}
                  onNextDay={() => setDate(addDays(date, 1))}
                  // Solo en el día del link: "21:30" existe todos los días, y si
                  // ese horario ya no estaba lleno la marca queda sin consumir.
                  waitlistToOpen={data!.date === linkedDate ? waitlistToOpen : null}
                  onWaitlistOpened={() => setWaitlistToOpen(null)}
                  onSelect={(slot) => {
                    track('slot_click', { slotAt: slot.startsAt });
                    setSelected(slot);
                    setSlotNotice(null);
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
                  // Con key: elegir otro horario desde la portada estando ya en
                  // este paso tiene que rearmar la cancha elegida, no arrastrar
                  // la del horario anterior.
                  key={selected.startsAt}
                  slug={slug}
                  club={club}
                  slot={selected}
                  preferredWall={linkedWall}
                  preferredSurface={linkedSurface}
                  preferredRoof={linkedRoof}
                  onBack={() => setStep(2)}
                  onSlotTaken={() => {
                    // Rojo y no gris como el del link vencido: acá el jugador
                    // tocó "Reservar" y la reserva no salió.
                    setSlotNotice({
                      tone: 'error',
                      message: `No pudimos reservarte el turno de las ${clockTime(
                        selected.startsAt,
                        club.timeZone,
                      )} hs: otra persona lo tomó justo antes. Elegí otro horario.`,
                    });
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

          {/* La otra salida hacia la búsqueda, además de la lupa de la barra:
              al pie de la reserva, que es donde el jugador se da cuenta de que
              acá no hay lo que busca. Lleva el día que estaba mirando. */}
          <Link
            to={searchHref(date)}
            className="group mt-10 flex items-center gap-4 rounded-2xl border border-cal/10 bg-vidrio p-4 transition hover:border-cal/25 hover:bg-vidrio-alto sm:p-5"
          >
            <span className="grid size-11 shrink-0 place-items-center rounded-full bg-ladrillo/15 text-ladrillo-claro">
              <SearchGlyph className="size-5" />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block font-semibold">¿Buscás en otros clubes?</span>
              <span className="block text-sm text-ink-soft">
                Mirá los horarios libres {date === todayIso() ? 'de hoy' : `del ${longDate(date)}`} en
                todos los clubes.
              </span>
            </span>
            <span aria-hidden className="text-lg text-ink-soft transition-transform group-hover:translate-x-1 group-hover:text-cal">
              →
            </span>
          </Link>
        </div>
      </div>

      <ServicesSection amenities={club.amenities} />

      <HowToGetThereSection
        address={club.address}
        city={club.city}
        mapsUrl={club.mapsUrl}
        mapsEmbedQuery={club.mapsEmbedQuery}
        latitude={club.latitude}
        longitude={club.longitude}
      />

      <SiteFooter name={club.name} address={club.address} instagram={instagram} />

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
  waitlistToOpen,
  onWaitlistOpened,
  onSelect,
  onBack,
}: {
  slug: string;
  data: Availability;
  loading: boolean;
  canGoNextDay: boolean;
  onNextDay: () => void;
  /** Horario ("21:30") cuyo formulario de lista de espera arranca abierto. */
  waitlistToOpen: string | null;
  onWaitlistOpened: () => void;
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
          <Link
            to={searchHref(data.date)}
            className="flex w-full items-center justify-center gap-2 rounded-full border border-cal/10 bg-vidrio px-5 py-3.5 text-sm font-bold uppercase tracking-[0.12em] text-cal transition hover:border-cal/25 hover:bg-vidrio-alto"
          >
            <SearchGlyph className="size-4" />
            Buscar este día en otros clubes
          </Link>
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
                date={data.date}
                slot={slot}
                index={index}
                timeZone={data.club.timeZone}
                startsOpen={slot.startTime === waitlistToOpen}
                onOpenedFromLink={onWaitlistOpened}
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
  date,
  slot,
  index,
  timeZone,
  startsOpen,
  onOpenedFromLink,
}: {
  slug: string;
  /** Día de la grilla (no el de startsAt: un turno de la 00:30 es del día anterior). */
  date: string;
  slot: Slot;
  index: number;
  timeZone: string;
  /** Vuelve del login con este horario: el formulario ya abierto y a la vista. */
  startsOpen: boolean;
  onOpenedFromLink: () => void;
}) {
  const [open, setOpen] = useState(startsOpen);
  const [joined, setJoined] = useState(false);
  const card = useRef<HTMLDivElement>(null);

  // Solo al montar: la página arranca arriba de todo, en la portada, y el
  // formulario al que volvió el jugador queda varias pantallas más abajo.
  useEffect(() => {
    if (!startsOpen) {
      return;
    }
    card.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    onOpenedFromLink();
  }, []);

  return (
    <div
      ref={card}
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
        <WaitlistForm
          slug={slug}
          startTime={slot.startsAt}
          returnTo={`/club/${slug}?fecha=${date}&espera=${slot.startTime}`}
          onJoined={() => {
            // La otra forma de "quiso y no pudo": el horario estaba lleno.
            track('waitlist_joined', { slotAt: slot.startsAt });
            setJoined(true);
          }}
        />
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

type SlotNotice = { tone: 'info' | 'error'; message: string };

/** Una fecha del link solo se acepta si tiene la forma que produce la busqueda. */
function validDate(value: string | null): string | null {
  return value && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : null;
}

/** La búsqueda en todos los clubes, parada en el día que el jugador estaba mirando. */
function searchHref(date: string): string {
  return `/buscar?fecha=${date}`;
}
