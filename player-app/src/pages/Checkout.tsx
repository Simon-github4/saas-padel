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
  const { session, updateLocalProfile } = usePlayerAuth();
  // Arranca en una cancha como la que se buscó: si pidió techada o pared, que no
  // le toque otra por ser la primera de la lista. Sin preferencia, la primera libre.
  const [court, setCourt] = useState<CourtAvailability>(
    () =>
      slot.available.find((option) => courtMatches(option, preferredWall, preferredSurface, preferredRoof)) ??
      slot.available[0],
  );
  // Con sesión iniciada el teléfono ya es de quien reserva: no se vuelve a pedir,
  // y el nombre se precarga con el que quedó guardado en la cuenta.
  const [fullName, setFullName] = useState(session?.displayName ?? '');
  const [phone, setPhone] = useState(session?.phoneNumber ?? '');
  const [error, setError] = useState<string | null>(null);
  // Lo que está mal en cada campo, abajo del campo y no en el cartel general: así
  // el jugador ve qué corregir sin buscarlo.
  const [nameError, setNameError] = useState<string | null>(null);
  const [phoneError, setPhoneError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<BookingCreated | null>(null);

  // Primera reserva de un número: el servidor le mandó un código por WhatsApp y
  // sin él no hay turno. Queda abierto para la forma de pago que eligió, así el
  // código confirma exactamente lo que pidió.
  const [verification, setVerification] = useState<{ paymentChoice: PaymentChoice } | null>(null);
  const [code, setCode] = useState('');
  const [codeError, setCodeError] = useState<string | null>(null);
  // Reenviar recién después de un minuto: antes, el primero puede estar llegando.
  const [canResend, setCanResend] = useState(false);
  useEffect(() => {
    if (!verification || canResend) {
      return;
    }
    const timer = setTimeout(() => setCanResend(true), 60_000);
    return () => clearTimeout(timer);
  }, [verification, canResend]);

  /** Otro número, otro código: el que se estaba esperando ya no sirve. */
  function closeVerification() {
    setVerification(null);
    setCode('');
    setCodeError(null);
    setCanResend(false);
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
    const badPhone = session?.phoneNumber ? null : phoneProblem(phone);
    setNameError(missingName);
    setPhoneError(badPhone);
    return !missingName && !badPhone;
  }

  async function submit(paymentChoice: PaymentChoice) {
    setError(null);
    setCodeError(null);
    if (!validate()) {
      return;
    }
    setSending(true);
    try {
      if (!verification) {
        // Confirmar el código no es mandar el formulario otra vez: se anota una sola.
        track('checkout_submit', { slotAt: slot.startsAt, paymentChoice });
        // Si el número nunca reservó, acá sale el WhatsApp con el código y se
        // frena hasta que lo escriba. Los que ya reservaron pasan directo.
        const { verificationRequired } = await api.requestPhoneCode(slug, phone);
        if (verificationRequired) {
          setVerification({ paymentChoice });
          track('phone_code_sent', { slotAt: slot.startsAt, paymentChoice });
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
          verificationCode: verification ? code.trim() : undefined,
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
        if (name) {
          void playerApi.updateProfile(session.token, name, phone).catch(() => {});
          updateLocalProfile(name, phone);
        }
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
      if (err instanceof ApiError && isCodeError(err)) {
        setCodeError(err.message);
        // Vencido o agotado, pedir otro es lo único que le queda: sin esperar el minuto.
        if (err.reason === 'VERIFICATION_CODE_EXPIRED') {
          setCanResend(true);
        }
        return;
      }
      if (err instanceof ApiError && err.reason === 'VERIFICATION_REQUIRED') {
        // El servidor pide el código y el checkout no lo tenía abierto (por
        // ejemplo, se prendió la verificación mientras llenaba el formulario).
        setVerification({ paymentChoice });
        void resendCode();
        return;
      }
      // Con el teléfono de la cuenta el campo no se muestra: ahí el error va al cartel general.
      if (err instanceof ApiError && isPhoneError(err) && !session?.phoneNumber) {
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
    }
  }

  async function resendCode() {
    setCodeError(null);
    setCanResend(false);
    try {
      await api.requestPhoneCode(slug, phone);
    } catch (err) {
      setCodeError(err instanceof ApiError ? err.message : 'No pudimos mandarte el código.');
      setCanResend(true);
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
        <Field
          label="Tu nombre"
          value={fullName}
          onChange={(value) => {
            setFullName(value);
            setNameError(null);
          }}
          placeholder="Nombre y apellido"
          autoComplete="name"
          error={nameError}
        />
        {session?.phoneNumber ? (
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
            placeholder="2262 15-415000"
            inputMode="tel"
            autoComplete="tel"
            hint="Con código de área. Te avisamos por WhatsApp a este número."
            error={phoneError}
          />
        )}
      </div>

      {error && <Alert>{error}</Alert>}

      {verification ? (
        <div className="space-y-3 rounded-xl border border-cal/10 bg-vidrio p-4">
          <p className="text-sm text-ink-soft">
            Te mandamos un código por WhatsApp al{' '}
            <span className="font-semibold text-cal tabular-nums">{phone}</span>. Lo pedimos una sola vez, la
            primera vez que reservás con este número.
          </p>
          <Field
            label="Código"
            value={code}
            onChange={(value) => {
              setCode(value.replace(/\D/g, '').slice(0, 6));
              setCodeError(null);
            }}
            placeholder="123456"
            inputMode="numeric"
            autoComplete="one-time-code"
            error={codeError}
          />
          <Button
            variant={verification.paymentChoice === 'DEPOSIT_ONLINE' ? 'accent' : 'primary'}
            onClick={() => submit(verification.paymentChoice)}
            disabled={sending || code.length < 6}
          >
            {verification.paymentChoice === 'DEPOSIT_ONLINE' ? 'Confirmar y pagar seña' : 'Confirmar reserva'}
          </Button>
          <div className="flex items-center justify-between text-xs">
            <button
              type="button"
              onClick={() => void resendCode()}
              disabled={!canResend || sending}
              className="font-semibold text-ladrillo-claro underline-offset-4 hover:underline disabled:cursor-not-allowed disabled:text-ink-mute disabled:no-underline"
            >
              {canResend ? 'Reenviar código' : 'Reenviar en un minuto'}
            </button>
            {!session?.phoneNumber && (
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
    // El WhatsApp no salió o se pidieron demasiados: lo que hay que revisar es el número.
    err.reason === 'VERIFICATION_CODE_NOT_SENT' ||
    err.reason === 'VERIFICATION_LIMIT' ||
    err.field === 'phoneNumber'
  );
}

/** Lo que falló es el código de WhatsApp: el mensaje va abajo del campo del código. */
function isCodeError(err: ApiError): boolean {
  return err.reason === 'VERIFICATION_CODE_INVALID' || err.reason === 'VERIFICATION_CODE_EXPIRED';
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
