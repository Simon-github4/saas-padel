import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  api,
  ApiError,
  playerApi,
  type BookingCreated,
  type Club,
  type CourtAvailability,
  type PaymentChoice,
  type Slot,
} from '../api/client';
import { track, trackNow } from '../analytics';
import { usePlayerAuth } from '../auth/AuthContext';
import { clockTime, durationMinutes, longDate, money, perPerson, shareBooking } from '../format';
import { rememberGuestBooking } from '../guestBookings';
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
  onBack,
  onSlotTaken,
}: {
  slug: string;
  club: Club;
  slot: Slot;
  onBack: () => void;
  onSlotTaken: () => void;
}) {
  const { session, updateLocalProfile } = usePlayerAuth();
  const [court, setCourt] = useState<CourtAvailability>(slot.available[0]);
  // Con sesión iniciada el teléfono ya es de quien reserva: no se vuelve a pedir,
  // y el nombre se precarga con el que quedó guardado en la cuenta.
  const [fullName, setFullName] = useState(session?.displayName ?? '');
  const [phone, setPhone] = useState(session?.phoneNumber ?? '');
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<BookingCreated | null>(null);

  const canPayAtClub = club.allowUnpaidBooking || !club.acceptsOnlinePayments;
  const deposit = Math.round((court.price * club.depositPercentage) / 100);

  async function submit(paymentChoice: PaymentChoice) {
    setError(null);
    setSending(true);
    track('checkout_submit', { slotAt: slot.startsAt, paymentChoice });
    try {
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
        detail: err instanceof ApiError ? err.code : 'UNKNOWN',
      });
      if (err instanceof ApiError && err.slotTaken) {
        // No hay nada que corregir: alguien llegó primero. Se refresca la grilla.
        onSlotTaken();
        return;
      }
      setError(err instanceof ApiError ? err.message : 'No pudimos tomar la reserva.');
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
      value: slot.available.length > 1 ? court.courtName : 'La que esté libre',
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
                  <span className="font-semibold">{option.courtName}</span>
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
          onChange={setFullName}
          placeholder="Nombre y apellido"
          autoComplete="name"
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
            onChange={setPhone}
            placeholder="2262 15-415000"
            inputMode="tel"
            autoComplete="tel"
            hint="Te avisamos por WhatsApp a este número"
          />
        )}
      </div>

      {error && <Alert>{error}</Alert>}

      <div className="space-y-2 pt-1">
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
                `Turno confirmado en ${club.name}, cancha ${court.courtName}, ` +
                  `${longDate(slot.startsAt, club.timeZone)} a las ` +
                  `${clockTime(slot.startsAt, club.timeZone)} hs.`,
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
        <WhatsappLink href={`https://wa.me/${club.whatsappNumber.replace(/[^0-9]/g, '')}`}>
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
