import { useState } from 'react';
import {
  api,
  ApiError,
  type BookingCreated,
  type Club,
  type CourtAvailability,
  type PaymentChoice,
  type Slot,
} from '../api/client';
import { clockTime, money } from '../format';
import { Alert, Button, Field, WhatsappLink } from '../components/Ui';

/**
 * Checkout, en una hoja que sube desde abajo.
 *
 * <p>Tres pasos cortos: cancha, datos y forma de pago. Se pide lo minimo, nombre y
 * telefono, porque cada campo de mas es un jugador que abandona y vuelve a
 * reservar por WhatsApp, que es justamente lo que este producto viene a evitar.
 */
export function Checkout({
  slug,
  club,
  slot,
  onClose,
  onSlotTaken,
}: {
  slug: string;
  club: Club;
  slot: Slot;
  onClose: () => void;
  onSlotTaken: () => void;
}) {
  const [court, setCourt] = useState<CourtAvailability>(slot.available[0]);
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<BookingCreated | null>(null);

  const canPayAtClub = club.allowUnpaidBooking || !club.acceptsOnlinePayments;
  const deposit = Math.round((court.price * club.depositPercentage) / 100);

  async function submit(paymentChoice: PaymentChoice) {
    setError(null);
    setSending(true);
    try {
      const booking = await api.book(slug, {
        courtId: court.courtId,
        startTime: slot.startsAt,
        fullName,
        phoneNumber: phone,
        paymentChoice,
      });

      // Con seña, el jugador sigue en MercadoPago; el turno queda reservado
      // mientras tanto y se cae solo si no paga.
      if (booking.checkoutUrl) {
        window.location.href = booking.checkoutUrl;
        return;
      }
      setResult(booking);
    } catch (err) {
      if (err instanceof ApiError && err.slotTaken) {
        // No hay nada que corregir: alguien llego primero. Se refresca la grilla.
        onSlotTaken();
        return;
      }
      setError(err instanceof ApiError ? err.message : 'No pudimos tomar la reserva.');
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/40">
      <div className="max-h-[92dvh] w-full max-w-lg overflow-y-auto rounded-t-3xl bg-white p-5 pb-8">
        {result ? (
          <Booked booking={result} club={club} />
        ) : (
          <>
            <div className="mb-4 flex items-start justify-between gap-4">
              <div>
                <h2 className="text-xl font-bold">
                  {clockTime(slot.startsAt, club.timeZone)} – {clockTime(slot.endsAt, club.timeZone)}
                </h2>
                <p className="text-sm text-slate-500">{club.name}</p>
              </div>
              <button onClick={onClose} className="px-2 text-2xl leading-none text-slate-400">
                ×
              </button>
            </div>

            {slot.available.length > 1 && (
              <div className="mb-4">
                <p className="mb-2 text-sm font-medium text-slate-700">Elegí la cancha</p>
                <div className="grid gap-2">
                  {slot.available.map((option) => (
                    <button
                      key={option.courtId}
                      onClick={() => setCourt(option)}
                      className={`flex items-center justify-between rounded-xl border px-4 py-3 text-left ${
                        option.courtId === court.courtId
                          ? 'border-brand-600 bg-brand-50'
                          : 'border-slate-200'
                      }`}
                    >
                      <span className="font-medium">{option.courtName}</span>
                      <span className="tabular-nums">{money(option.price)}</span>
                    </button>
                  ))}
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
              <Field
                label="Tu teléfono"
                value={phone}
                onChange={setPhone}
                placeholder="2262 15-415000"
                inputMode="tel"
                autoComplete="tel"
                hint="Te avisamos por WhatsApp a este número"
              />
            </div>

            {error && (
              <div className="mt-4">
                <Alert>{error}</Alert>
              </div>
            )}

            <div className="mt-5 space-y-2">
              {club.acceptsOnlinePayments && (
                <Button onClick={() => submit('DEPOSIT_ONLINE')} disabled={sending}>
                  Pagar seña de {money(deposit)}
                </Button>
              )}
              {canPayAtClub && (
                <Button
                  variant={club.acceptsOnlinePayments ? 'secondary' : 'primary'}
                  onClick={() => submit('PAY_AT_CLUB')}
                  disabled={sending}
                >
                  Pagar en el club
                </Button>
              )}
              <p className="pt-1 text-center text-xs text-slate-500">
                Total del turno: {money(court.price)}
              </p>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

/** Reserva tomada, esperando que el jugador toque el link del WhatsApp. */
function Booked({ booking, club }: { booking: BookingCreated; club: Club }) {
  return (
    <div className="py-2 text-center">
      <div className="mx-auto mb-4 flex size-14 items-center justify-center rounded-full bg-emerald-100 text-3xl">
        ✓
      </div>
      <h2 className="text-xl font-bold">Te reservamos el turno</h2>
      <p className="mt-2 text-slate-600">{booking.message}</p>

      <div className="mt-5 space-y-2 text-left">
        {/*
          El link de gestion se muestra siempre, no solo en el WhatsApp: si el
          mensaje no llega, el jugador no puede quedarse sin forma de ver ni
          cancelar su turno.
        */}
        <a
          href={booking.managementUrl}
          className="block rounded-xl border border-slate-200 px-4 py-3 text-center font-medium text-brand-600"
        >
          Ver mi turno
        </a>
        <WhatsappLink href={`https://wa.me/${club.whatsappNumber.replace(/[^0-9]/g, '')}`}>
          Escribirle al club
        </WhatsappLink>
      </div>
    </div>
  );
}
