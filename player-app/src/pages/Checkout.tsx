import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  api,
  ApiError,
  playerApi,
  type BookingCreated,
  type Club,
  type CourtAvailability,
  type CourtRoof,
  type CourtSurface,
  type CourtWall,
  type PaymentChoice,
  type Slot,
} from '../api/client';
import { track, trackNow } from '../analytics';
import { usePlayerAuth } from '../auth/AuthContext';
import { ROOF_LABEL, SURFACE_LABEL, WALL_LABEL, courtMatches } from '../courtFeatures';
import { clockTime, durationMinutes, longDate, money, perPerson, shareBooking, slotLine, whatsappLink } from '../format';
import { rememberGuestBooking } from '../guestBookings';
import { forgetGuestContact, readGuestContact, rememberGuestContact, type GuestContact } from '../guestContact';
import { phoneProblem } from '../phone';
import { Alert, Button, Card, Field, SummaryCard, WhatsappLink } from '../components/Ui';

/**
 * Paso 3 de la reserva: datos del jugador y forma de pago.
 *
 * <p>Ya no es un modal que tapa todo: es la última sección del flujo en la
 * misma página, con el resumen del turno arriba y los botones de pago según lo
 * que acepte el club.
 */
export function Checkout({
  slug,
  club,
  slot,
  preferredWall = null,
  preferredSurface = null,
  preferredRoof = null,
  onBack,
  onSlotTaken,
}: {
  slug: string;
  club: Club;
  slot: Slot;
  /** Paredes, piso y techo que el jugador pidió en la búsqueda, si vino de ahí. */
  preferredWall?: CourtWall | null;
  preferredSurface?: CourtSurface | null;
  preferredRoof?: CourtRoof | null;
  onBack: () => void;
  onSlotTaken: () => void;
}) {
  const { session, updateLocalProfile, refreshProfile } = usePlayerAuth();
  // Arranca en una cancha como la que se buscó: si pidió techada o pared, que no
  // le toque otra por ser la primera de la lista. Sin preferencia, la primera libre.
  const [court, setCourt] = useState<CourtAvailability>(
    () =>
      slot.available.find((option) => courtMatches(option, preferredWall, preferredSurface, preferredRoof)) ??
      slot.available[0],
  );
  // Con sesión iniciada y el teléfono de la cuenta verificado, se reserva con ese:
  // no se vuelve a pedir ni se puede cambiar. Sin verificar se muestra escrito y
  // editable, porque si se registró con un número mal escrito nunca podría
  // confirmarlo por WhatsApp. El nombre se precarga con el de la cuenta.
  const phoneFixed = Boolean(session?.phoneLocked && session.phoneNumber);
  const [fullName, setFullName] = useState(session?.displayName ?? '');
  // Se escribe como lo escribiría él ("2262 21-2345"), no en el formato con +549
  // que guarda el servidor: el servidor lo entiende igual.
  const [phone, setPhone] = useState(session?.phoneDisplay ?? session?.phoneNumber ?? '');
  // Sin cuenta, los datos de la última reserva en este navegador. Se ofrecen y
  // no se precargan: el dispositivo puede ser de otro (ver guestContact).
  const [suggested, setSuggested] = useState<GuestContact | null>(() => (session ? null : readGuestContact()));
  const showSuggestion = Boolean(suggested && !session && !fullName && !phone);

  // La sesión guardada es de cuando inició sesión: el teléfono pudo verificarse
  // después, en otra reserva o en otro dispositivo.
  useEffect(() => {
    void refreshProfile();
  }, [refreshProfile]);
  useEffect(() => {
    if (phoneFixed && session?.phoneNumber) {
      setPhone(session.phoneDisplay ?? session.phoneNumber);
    }
  }, [phoneFixed, session?.phoneNumber, session?.phoneDisplay]);
  const [error, setError] = useState<string | null>(null);
  // Lo que está mal en cada campo, abajo del campo y no en el cartel general: así
  // el jugador ve qué corregir sin buscarlo.
  const [nameError, setNameError] = useState<string | null>(null);
  const [phoneError, setPhoneError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<BookingCreated | null>(null);

  // Primera reserva sin seña de un número que nunca se verificó: el jugador nos
  // tiene que mandar un WhatsApp desde ese número, con el mensaje que le armó el
  // servidor. Mientras tanto se pregunta si llegó, y cuando llega la reserva sale
  // sola: volver de WhatsApp y encontrar el turno tomado es todo lo que tiene que hacer.
  const [verification, setVerification] = useState<Verification | null>(null);
  // En la compu el link no sirve de mucho: WhatsApp está en el celular. Ahí va el QR.
  const [onDesktop] = useState(isDesktop);
  // La reserva que sale sola tiene que ver lo último que escribió (el nombre se
  // puede corregir mientras espera), no lo que había cuando empezó a esperar.
  const submitRef = useRef<((paymentChoice: PaymentChoice, afterVerification?: boolean) => Promise<void>) | null>(
    null,
  );
  useEffect(() => {
    if (!verification || verification.status !== 'PENDING') {
      return;
    }
    let cancelled = false;
    let done = false;
    async function check(id: string) {
      try {
        const { status } = await api.phoneVerificationStatus(slug, id);
        if (cancelled || done) {
          return;
        }
        if (status === 'VERIFIED') {
          done = true;
          track('phone_verified', { slotAt: slot.startsAt, paymentChoice: 'PAY_AT_CLUB' });
          setVerification((current) => current && { ...current, status: 'VERIFIED' });
          void submitRef.current?.('PAY_AT_CLUB', true);
        } else if (status === 'EXPIRED') {
          setVerification((current) => current && { ...current, status: 'EXPIRED' });
        }
      } catch {
        // Sin conexión un momento: la próxima vuelta pregunta de nuevo.
      }
    }
    const id = verification.id;
    const timer = setInterval(() => void check(id), 2000);
    // Vuelve de WhatsApp: se pregunta ya, sin esperar la próxima vuelta.
    const onVisible = () => {
      if (document.visibilityState === 'visible') {
        void check(id);
      }
    };
    document.addEventListener('visibilitychange', onVisible);
    window.addEventListener('focus', onVisible);
    return () => {
      cancelled = true;
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
      window.removeEventListener('focus', onVisible);
    };
  }, [verification?.id, verification?.status, slug, slot.startsAt]);

  /** Otro número: el mensaje que se estaba esperando era para el anterior. */
  function closeVerification() {
    setVerification(null);
  }

  /**
   * Le pide al servidor el mensaje para confirmar el número y lo deja a la vista.
   *
   * @return si hace falta confirmarlo; si no, se reserva directo
   */
  async function startVerification(): Promise<boolean> {
    const started = await api.startPhoneVerification(slug, phone);
    if (!started.verificationRequired || !started.verificationId || !started.whatsappLink) {
      return false;
    }
    setVerification({
      id: started.verificationId,
      link: started.whatsappLink,
      qr: started.whatsappQr,
      status: 'PENDING',
    });
    track('phone_code_sent', { slotAt: slot.startsAt, paymentChoice: 'PAY_AT_CLUB' });
    return true;
  }

  // Un club que exige seña igual deja reservar sin ella a sus jugadores de
  // confianza, y eso depende del teléfono: se le pregunta al servidor cuando el
  // número está completo, y si no contesta (o el número no es de confianza) queda
  // el comportamiento de siempre.
  const [trusted, setTrusted] = useState(false);
  useEffect(() => {
    if (club.allowUnpaidBooking || phone.replace(/\D/g, '').length < 10) {
      setTrusted(false);
      return;
    }
    let cancelled = false;
    const timer = setTimeout(() => {
      api
        .paymentOptions(slug, phone)
        .then((options) => {
          if (!cancelled) {
            setTrusted(options.canPayAtClub);
          }
        })
        .catch(() => {
          if (!cancelled) {
            setTrusted(false);
          }
        });
    }, 500);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [slug, phone, club.allowUnpaidBooking]);

  const canPayAtClub = club.allowUnpaidBooking || !club.acceptsOnlinePayments || trusted;
  const deposit = Math.round((court.price * club.depositPercentage) / 100);

  /**
   * Ataja antes de mandar lo que el servidor rechazaría seguro: sin nombre, o un
   * teléfono sin código de área. Antes esto viajaba igual y volvía como error, y
   * era la mitad de las reservas fallidas.
   */
  function validate(): boolean {
    const missingName = fullName.trim() ? null : 'Necesitamos tu nombre para reservar el turno.';
    // Con sesión el teléfono es el de la cuenta y no se muestra: no hay nada que corregir.
    const badPhone = phoneFixed ? null : phoneProblem(phone);
    setNameError(missingName);
    setPhoneError(badPhone);
    return !missingName && !badPhone;
  }

  /**
   * @param afterVerification la reserva que sale sola cuando llega el WhatsApp: el
   *     formulario ya se mandó una vez y el número ya está confirmado
   */
  async function submit(paymentChoice: PaymentChoice, afterVerification = false) {
    setError(null);
    if (!validate()) {
      // Borró el nombre mientras esperaba: el número ya quedó confirmado, así que
      // alcanza con que lo corrija y vuelva a tocar reservar.
      if (afterVerification) {
        setVerification(null);
      }
      return;
    }
    setSending(true);
    try {
      if (!afterVerification) {
        // La que sale sola después del WhatsApp no es mandar el formulario otra vez:
        // se anota una sola.
        track('checkout_submit', { slotAt: slot.startsAt, paymentChoice });
        // Sin seña, un número que nunca se verificó frena acá hasta que nos mande
        // el WhatsApp. Con seña, o ya verificado, pasa directo.
        if (paymentChoice === 'PAY_AT_CLUB' && (await startVerification())) {
          return;
        }
      }

      const booking = await api.book(
        slug,
        {
          courtId: court.courtId,
          startTime: slot.startsAt,
          fullName,
          phoneNumber: phone,
          paymentChoice,
        },
        session?.token,
      );

      // El final del embudo, con el id de la reserva real: es lo que permite
      // preguntarle después a la base si el que entró por la búsqueda global
      // terminó pagando. Sale ya, sin esperar el lote: con seña, la línea de
      // abajo se lleva al jugador a MercadoPago y la cola se pierde ahí.
      trackNow('booking_created', {
        slotAt: slot.startsAt,
        paymentChoice,
        bookingId: booking.bookingId,
      });

      // Está logueado: el nombre (y el teléfono, si todavía no tenía) que
      // escribió le quedan guardados a la cuenta, para no volver a pedírselos.
      if (session) {
        const name = fullName.trim();
        const saved = name ? playerApi.updateProfile(session.token, name, phone).catch(() => {}) : Promise.resolve();
        if (name) {
          updateLocalProfile(name, phone);
        }
        // Si el teléfono se verificó en esta reserva, el servidor ya lo hizo el de
        // la cuenta y quedó fijo: se trae para que la próxima lo muestre así.
        void saved.then(() => refreshProfile());
      } else {
        // Sin cuenta, este dispositivo es la unica forma de volver a
        // /manage/:token despues de salir de esta pantalla.
        rememberGuestBooking({
          bookingId: booking.bookingId,
          managementToken: booking.managementToken,
          clubName: club.name,
          clubSlug: slug,
          courtName: court.courtName,
          startTime: slot.startsAt,
          // El fin es lo que decide cuándo se olvida: una hora después de
          // terminar, el link ya no sirve para nada y se borra solo.
          endTime: slot.endsAt,
        });
        // Para ofrecérselos la próxima vez, hasta que se haga una cuenta.
        rememberGuestContact({ fullName, phone });
      }

      // Con seña, el jugador sigue en MercadoPago; el turno queda reservado
      // mientras tanto y se cae solo si no paga.
      if (booking.checkoutUrl) {
        window.location.href = booking.checkoutUrl;
        return;
      }
      setResult(booking);
    } catch (err) {
      // Con el código del error: un checkout que falla no es un abandono, y
      // mezclarlos da una conversión pesimista y sin diagnóstico.
      track('booking_failed', {
        slotAt: slot.startsAt,
        paymentChoice,
        detail: err instanceof ApiError ? err.analyticsCode : 'UNKNOWN',
      });
      if (err instanceof ApiError && err.slotTaken) {
        // No hay nada que corregir: alguien llegó primero. Se refresca la grilla.
        onSlotTaken();
        return;
      }
      if (err instanceof ApiError && err.reason === 'VERIFICATION_REQUIRED') {
        // El servidor pide confirmar el número y el checkout no lo sabía (por
        // ejemplo, se prendió la verificación mientras llenaba el formulario).
        try {
          if (await startVerification()) {
            return;
          }
        } catch {
          // Cae al cartel general con el mensaje del servidor.
        }
      }
      if (err instanceof ApiError && err.reason === 'ACCOUNT_PHONE_LOCKED') {
        // La cuenta ya tenía el número verificado y esta sesión no lo sabía.
        void refreshProfile();
      }
      // Con el teléfono de la cuenta el campo no se muestra: ahí el error va al cartel general.
      if (err instanceof ApiError && isPhoneError(err) && !phoneFixed) {
        setPhoneError(err.message);
        return;
      }
      if (err instanceof ApiError && isNameError(err)) {
        setNameError(err.message);
        return;
      }
      setError(err instanceof ApiError ? err.message : 'No pudimos tomar la reserva.');
    } finally {
      setSending(false);
      // La que sale sola ya no espera nada: si salió, se muestra el turno; si no,
      // el error queda arriba y vuelven los botones para intentar de nuevo.
      if (afterVerification) {
        setVerification(null);
      }
    }
  }

  submitRef.current = submit;

  /** Pasó el plazo sin que llegara el mensaje: uno nuevo, con otro código. */
  async function restartVerification() {
    setError(null);
    setSending(true);
    try {
      if (!(await startVerification())) {
        // Mientras tanto quedó verificado por otro lado: se reserva directo.
        setVerification(null);
        setSending(false);
        await submit('PAY_AT_CLUB', true);
        return;
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No pudimos preparar el mensaje. Probá de nuevo.');
    } finally {
      setSending(false);
    }
  }

  if (result) {
    return <Booked booking={result} club={club} court={court} slot={slot} phone={phone} fullName={fullName} />;
  }

  const rows = [
    { label: 'Cuándo', value: longDate(slot.startsAt, club.timeZone) },
    { label: 'Hora', value: `${clockTime(slot.startsAt, club.timeZone)} hs` },
    {
      label: 'Cancha',
      value: `${slot.available.length > 1 ? court.courtName : 'La que esté libre'} · ${courtFeatures(court)}`,
    },
    { label: 'Duración', value: `${durationMinutes(slot.startsAt, slot.endsAt)} min` },
    {
      label: 'Precio por persona',
      value: perPerson(court.price, club.playersPerCourt),
    },
    { label: 'Total del turno', value: money(court.price) },
  ];

  return (
    <div className="space-y-4">
      <button
        type="button"
        onClick={onBack}
        className="text-xs font-semibold text-ladrillo-claro underline-offset-4 hover:underline"
      >
        ‹ Volver a elegir horario
      </button>

      <SummaryCard rows={rows} />

      {slot.available.length > 1 && (
        <div>
          <p className="eyebrow mb-2 text-ink-soft">Elegí la cancha</p>
          <div className="grid gap-2">
            {slot.available.map((option) => {
              const isChosen = option.courtId === court.courtId;
              return (
                <button
                  key={option.courtId}
                  type="button"
                  aria-pressed={isChosen}
                  onClick={() => setCourt(option)}
                  className={`flex items-center justify-between rounded-xl border px-4 py-3 text-left transition ${
                    isChosen
                      ? 'border-cal bg-cal text-pista'
                      : 'border-cal/10 bg-vidrio hover:border-cal/25'
                  }`}
                >
                  <span className="min-w-0">
                    <span className="block font-semibold">{option.courtName}</span>
                    <span className={`block text-xs ${isChosen ? 'text-pista/60' : 'text-ink-soft'}`}>
                      {ROOF_LABEL[option.roof]} · {WALL_LABEL[option.wall]} · {SURFACE_LABEL[option.surface]}
                    </span>
                  </span>
                  <span className="tabular-nums">
                    {perPerson(option.price, club.playersPerCourt)}
                    <span className={`text-xs ${isChosen ? 'text-neutral-500' : 'text-ink-soft'}`}>
                      {' '}
                      c/u
                    </span>
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      )}

      <div className="space-y-3">
        {showSuggestion && suggested && (
          // El nombre ocupa todo el ancho, sin recortar: es lo que tiene que leer
          // para saber si es él. El teléfono comparte renglón con los botones.
          <div className="rounded-xl border border-cal/10 bg-vidrio px-4 py-3">
            <span className="eyebrow block text-ink-soft">¿Reservás como?</span>
            <p className="break-words text-sm font-semibold">{suggested.fullName}</p>
            <div className="mt-1 flex items-center justify-between gap-3">
              <p className="min-w-0 text-sm tabular-nums text-ink-soft">{suggested.phone}</p>
              <div className="flex shrink-0 items-center gap-3">
                <button
                  type="button"
                  onClick={() => {
                    forgetGuestContact();
                    setSuggested(null);
                  }}
                  className="text-xs font-medium text-ink-soft underline underline-offset-4 hover:text-cal"
                >
                  No soy yo
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setFullName(suggested.fullName);
                    setPhone(suggested.phone);
                    setNameError(null);
                    setPhoneError(null);
                    setSuggested(null);
                  }}
                  className="rounded-full bg-cal px-4 py-2 text-xs font-bold uppercase tracking-[0.12em] text-pista transition hover:bg-arena"
                >
                  Sí
                </button>
              </div>
            </div>
          </div>
        )}
        <Field
          label="Tu nombre completo"
          value={fullName}
          onChange={(value) => {
            setFullName(value);
            setNameError(null);
          }}
          placeholder="Nombre y apellido"
          autoComplete="name"
          error={nameError}
        />
        {phoneFixed ? (
          <div className="rounded-xl border border-cal/10 bg-vidrio px-4 py-3">
            <span className="eyebrow block text-ink-soft">Reservás con</span>
            <p className="text-sm font-semibold tabular-nums">{phone}</p>
          </div>
        ) : (
          <Field
            label="Tu teléfono"
            value={phone}
            onChange={(value) => {
              setPhone(value);
              setPhoneError(null);
              closeVerification();
            }}
            placeholder="2262 ######"
            inputMode="tel"
            autoComplete="tel"
            hint="Con código de área."
            error={phoneError}
          />
        )}
      </div>

      {error && <Alert>{error}</Alert>}

      {verification ? (
        <div className="space-y-4 rounded-xl border border-cal/10 bg-vidrio p-4">
          {verification.status === 'VERIFIED' ? (
            <p className="text-sm font-semibold text-cal">¡Listo, número confirmado! Estamos reservando tu turno…</p>
          ) : (
            <p className="text-sm text-ink-soft">
              Es tu primera reserva sin seña con el{' '}
              <span className="font-semibold text-cal tabular-nums">{phone}</span>: confirmalo mandándonos un
              WhatsApp desde ese número. El mensaje ya está escrito, solo tenés que enviarlo. Se pide una sola vez.
            </p>
          )}

          {verification.status === 'EXPIRED' && (
            <>
              <Alert>Pasaron más de 10 minutos y no nos llegó el mensaje.</Alert>
              <Button onClick={() => void restartVerification()} disabled={sending}>
                Preparar otro mensaje
              </Button>
            </>
          )}

          {verification.status === 'PENDING' &&
            (onDesktop && verification.qr ? (
              <div className="flex flex-col items-center gap-3">
                <img
                  src={verification.qr}
                  alt="QR para mandarnos el WhatsApp desde el celular"
                  className="size-52 rounded-xl bg-white p-2"
                />
                <p className="text-center text-xs text-ink-soft">
                  Escanealo con la cámara del celular que tiene WhatsApp con ese número.
                </p>
                <a
                  href={verification.link}
                  target="_blank"
                  rel="noreferrer"
                  className="text-xs font-semibold text-ladrillo-claro underline-offset-4 hover:underline"
                >
                  O abrí WhatsApp en esta compu
                </a>
              </div>
            ) : (
              <WhatsappLink href={verification.link}>Confirmar por WhatsApp</WhatsappLink>
            ))}

          {verification.status === 'PENDING' && (
            <p className="flex items-center justify-center gap-2 text-xs text-ink-soft">
              <span className="size-2 animate-pulse rounded-full bg-wapp" aria-hidden="true" />
              Esperando tu mensaje. Cuando llegue, la reserva se confirma sola.
            </p>
          )}

          <div className="flex items-center justify-between gap-3 text-xs">
            <a
              href={whatsappLink(
                club.whatsappNumber,
                `Hola, quiero reservar el ${longDate(slot.startsAt, club.timeZone)} a las ${clockTime(slot.startsAt, club.timeZone)} hs.`,
              )}
              target="_blank"
              rel="noreferrer"
              className="text-ink-mute underline-offset-4 hover:underline"
            >
              ¿No tenés WhatsApp? Escribile al club
            </a>
            {!phoneFixed && verification.status !== 'VERIFIED' && (
              <button
                type="button"
                onClick={closeVerification}
                className="text-ink-mute underline-offset-4 hover:underline"
              >
                Cambiar número
              </button>
            )}
          </div>
        </div>
      ) : (
        <div className="space-y-2 pt-1">
          {trusted && !club.allowUnpaidBooking && (
            <p className="text-center text-xs text-ink-soft">
              {club.name} te tiene como jugador de confianza: podés reservar sin pagar seña.
            </p>
          )}
          {club.acceptsOnlinePayments && (
            <Button variant="accent" onClick={() => submit('DEPOSIT_ONLINE')} disabled={sending}>
              Pagar seña de {money(deposit)}
            </Button>
          )}
          {canPayAtClub && (
            <Button
              variant={club.acceptsOnlinePayments ? 'secondary' : 'primary'}
              onClick={() => submit('PAY_AT_CLUB')}
              disabled={sending}
            >
              Reservar y pagar en el club
            </Button>
          )}
        </div>
      )}

      <p className="text-center text-xs text-ink-mute">
        Al reservar aceptás los{' '}
        <Link to="/terminos" className="underline-offset-4 hover:underline">
          Términos de uso
        </Link>{' '}
        y la{' '}
        <Link to="/privacidad" className="underline-offset-4 hover:underline">
          Política de privacidad
        </Link>
        .
      </p>
    </div>
  );
}

/** Reserva tomada, esperando que el jugador toque el link del WhatsApp. */
function Booked({
  booking,
  club,
  court,
  slot,
  phone,
  fullName,
}: {
  booking: BookingCreated;
  club: Club;
  court: CourtAvailability;
  slot: Slot;
  phone: string;
  /** Nombre que el jugador escribió en el checkout, para guardarlo en su cuenta. */
  fullName: string;
}) {
  const { session } = usePlayerAuth();
  const [offering, setOffering] = useState(true);

  // La confirmacion reemplaza el checkout en el mismo lugar y no cambia el paso,
  // asi que sin esto la vista quedaba donde estaba el boton de pago y el bloque
  // "Turno reservado" quedaba fuera de pantalla. Este efecto lo trae a la vista.
  const doneRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    doneRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, []);

  const registerParams = new URLSearchParams({ mode: 'register' });
  if (fullName.trim()) {
    registerParams.set('name', fullName.trim());
  }
  if (phone.trim()) {
    registerParams.set('phone', phone.trim());
  }

  return (
    <div ref={doneRef} className="scroll-mt-20 py-2 text-center">
      <div className="mx-auto mb-4 flex size-14 items-center justify-center rounded-full bg-ladrillo/15 text-3xl text-ladrillo-claro">
        ✓
      </div>
      <h2 className="text-2xl">Turno reservado</h2>
      <p className="mt-3 text-ink-soft">{booking.message}</p>

      {booking.status === 'CONFIRMED' && (
        <div className="mt-5">
          <Button
            variant="secondary"
            onClick={() =>
              shareBooking(
                `✅ Turno confirmado en *${club.name}*\n${slotLine(court.courtName, slot.startsAt, club.timeZone)}`,
                booking.shareUrl,
              )
            }
          >
            Compartir turno
          </Button>
        </div>
      )}

      <div className="mt-5 space-y-2 text-left">
        {/*
          Logueado, el turno ya quedó guardado en la cuenta: el botón va al
          historial de "Mis turnos", donde este turno aparece solo.
        */}
        {session ? (
          <Link
            to="/account"
            className="block rounded-full border border-cal/10 bg-vidrio px-5 py-3.5 text-center text-sm font-bold uppercase tracking-[0.12em] text-cal transition hover:border-cal/25"
          >
            Ver mis turnos
          </Link>
        ) : (
          <Link
            to={`/manage/${booking.managementToken}`}
            className="block rounded-full border border-cal/10 bg-vidrio px-5 py-3.5 text-center text-sm font-bold uppercase tracking-[0.12em] text-cal transition hover:border-cal/25"
          >
            Ver mi turno
          </Link>
        )}
        <WhatsappLink
          href={whatsappLink(
            club.whatsappNumber,
            `Hola, te escribo por el turno que reservé:\n` +
              `${slotLine(court.courtName, slot.startsAt, club.timeZone)}\n\nMi turno:\n${booking.shareUrl}`,
          )}
        >
          Escribirle al club
        </WhatsappLink>
      </div>

      {/* Con sesión ya no hace falta el ofrecimiento: el turno ya le va a
          aparecer solo en "Mis turnos". */}
      {session && (
        <div className="mt-5">
          <Alert tone="success">Listo, ya vas a ver este turno en "Mis turnos".</Alert>
        </div>
      )}

      {!session && offering && (
        <Card className="mt-5 !p-4 text-left">
          <p className="text-sm text-ink-soft">¿Querés guardar este turno en una cuenta?</p>
          <div className="mt-3 flex items-center gap-4">
            <Link
              to={`/login?${registerParams.toString()}`}
              className="rounded-full bg-vidrio px-4 py-2 text-center text-xs font-bold uppercase tracking-[0.12em] text-cal transition hover:bg-vidrio-alto"
            >
              Crear cuenta
            </Link>
            <button
              type="button"
              className="text-xs text-ink-mute underline-offset-4 hover:underline"
              onClick={() => setOffering(false)}
            >
              No, gracias
            </button>
          </div>
        </Card>
      )}
    </div>
  );
}

/** El servidor rechazó el teléfono: el mensaje va abajo del campo, que es lo que hay que corregir. */
function isPhoneError(err: ApiError): boolean {
  return (
    err.reason?.startsWith('PHONE_') === true ||
    // Pidió confirmar este número demasiadas veces: lo que hay que revisar es el número.
    err.reason === 'VERIFICATION_LIMIT' ||
    err.field === 'phoneNumber'
  );
}

/** El WhatsApp que esperamos para confirmar el número. */
interface Verification {
  id: string;
  /** Abre WhatsApp con el mensaje ya escrito. */
  link: string;
  /** El mismo link como QR, para la compu. */
  qr: string | null;
  /** VERIFIED dura lo que tarda en salir la reserva. */
  status: 'PENDING' | 'EXPIRED' | 'VERIFIED';
}

/**
 * Sin pantalla táctil es una compu: el WhatsApp del jugador está en el celular y
 * conviene el QR. Un celular o una tablet abren el link directo.
 */
function isDesktop(): boolean {
  try {
    return !window.matchMedia('(pointer: coarse)').matches;
  } catch {
    return false;
  }
}

function isNameError(err: ApiError): boolean {
  return err.reason === 'NAME_MISSING' || err.field === 'fullName';
}

/** "Techada · Blindex", o "Al aire libre · Pared · Cemento": el piso solo cuando no es el de siempre. */
function courtFeatures(court: CourtAvailability): string {
  const parts = [ROOF_LABEL[court.roof], WALL_LABEL[court.wall]];
  if (court.surface === 'NO_CARPET') {
    parts.push(SURFACE_LABEL[court.surface]);
  }
  return parts.join(' · ');
}
