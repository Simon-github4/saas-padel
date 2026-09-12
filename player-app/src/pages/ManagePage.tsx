import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api, ApiError, type BookingDetail, type Cancellation } from '../api/client';
import { clockTime, longDate, money, shareBooking, whatsappLink } from '../format';
import { forgetGuestBooking } from '../guestBookings';
import {
  Alert,
  Button,
  Card,
  Loading,
  Screen,
  SiteFooter,
  StatusBadge,
  TopBar,
  WhatsappLink,
} from '../components/Ui';
import { AccountButton } from '../components/AccountButton';

/**
 * Portal del turno, al que se llega con el link que viajo por WhatsApp.
 *
 * <p>El token es la unica credencial: no hay login, porque pedirle al jugador que
 * se registre para ver su propio turno lo devuelve directo a WhatsApp.
 */
export function ManagePage({ mode }: { mode: 'manage' | 'confirm' }) {
  const { token = '' } = useParams();
  const [booking, setBooking] = useState<BookingDetail | null>(null);
  const [cancelled, setCancelled] = useState<Cancellation | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [confirmingCancel, setConfirmingCancel] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        // En el flujo sin seña, abrir el link es la confirmacion: el jugador ya
        // toco el enlace del WhatsApp y no tiene sentido pedirle un paso mas.
        setBooking(mode === 'confirm' ? await api.confirm(token) : await api.booking(token));
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'No pudimos encontrar el turno.');
      } finally {
        setLoading(false);
      }
    })();
  }, [token, mode]);

  async function cancel() {
    setWorking(true);
    setError(null);
    try {
      setCancelled(await api.cancel(token));
      if (booking) {
        // Cancelado, ya no sirve tenerlo a mano en el dispositivo.
        forgetGuestBooking(booking.bookingId);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No pudimos cancelar el turno.');
      setConfirmingCancel(false);
    } finally {
      setWorking(false);
    }
  }

  if (loading) {
    return (
      <Screen className="pt-6">
        <Loading />
      </Screen>
    );
  }

  if (!booking) {
    return (
      <Screen className="pt-6">
        <Alert>{error ?? 'Este link no corresponde a ningún turno.'}</Alert>
      </Screen>
    );
  }

  if (cancelled) {
    return (
      <Screen className="pt-6">
        <Cancelled result={cancelled} />
      </Screen>
    );
  }

  const active = booking.status === 'CONFIRMED' || booking.status === 'AWAITING_CONFIRMATION';

  return (
    <Screen className="pt-6" top={<TopBar name={booking.clubName} accountSlot={<AccountButton />} titleTo={`/club/${booking.clubSlug}`} />}>
      <header className="mb-5 mt-6">
        <h1 className="text-3xl">Tu turno</h1>
      </header>

      {mode === 'confirm' && booking.status === 'CONFIRMED' && (
        <div className="mb-4">
          <Alert tone="success">Listo, tu turno quedó confirmado.</Alert>
        </div>
      )}

      <Card>
        <dl className="space-y-3">
          {/* Fecha y hora en filas separadas: juntas desbordaban el ancho y
              dejaban el "hs" colgando solo en la segunda linea. */}
          <Row label="Cuándo">
            {/* Solo la inicial: "capitalize" pone en mayuscula cada palabra y deja
                "Sabado, 5 De Septiembre". */}
            <span className="first-letter:uppercase">{longDate(booking.startTime)}</span>
          </Row>
          <Row label="Hora">
            {clockTime(booking.startTime, Intl.DateTimeFormat().resolvedOptions().timeZone)} hs
          </Row>
          <Row label="Cancha">{booking.courtName}</Row>
          <Row label="Estado">
            <StatusBadge status={booking.status} />
          </Row>
          <Row label="Total">{money(booking.totalPrice)}</Row>
          {booking.paidAmount > 0 && <Row label="Pagado">{money(booking.paidAmount)}</Row>}
          {booking.balanceDue > 0 && (
            <Row label="A pagar en el club">
              <strong>{money(booking.balanceDue)}</strong>
            </Row>
          )}
        </dl>
      </Card>

      {booking.status === 'CONFIRMED' && (
        <div className="mt-4">
          <Button
            variant="secondary"
            onClick={() =>
              shareBooking(
                `Turno confirmado en ${booking.clubName}, cancha ${booking.courtName}, ` +
                  `${longDate(booking.startTime)} a las ` +
                  `${clockTime(booking.startTime, Intl.DateTimeFormat().resolvedOptions().timeZone)} hs.`,
                booking.shareUrl,
              )
            }
          >
            Compartir turno
          </Button>
        </div>
      )}

      {error && (
        <div className="mt-4">
          <Alert>{error}</Alert>
        </div>
      )}

      {active && (
        <div className="mt-5 space-y-3">
          {booking.cancellableOnline ? (
            confirmingCancel ? (
              <Card>
                <p className="mb-4 text-sm text-ink-soft">
                  ¿Seguro que querés cancelar? La cancha vuelve a quedar disponible para
                  otros jugadores.
                </p>
                <div className="space-y-2">
                  <Button variant="danger" onClick={cancel} disabled={working}>
                    Sí, cancelar el turno
                  </Button>
                  <Button variant="secondary" onClick={() => setConfirmingCancel(false)}>
                    No, dejarlo
                  </Button>
                </div>
              </Card>
            ) : (
              <Button variant="danger" onClick={() => setConfirmingCancel(true)}>
                Cancelar turno
              </Button>
            )
          ) : (
            <>
              <Alert tone="info">{booking.cancellationHint}</Alert>
              <WhatsappLink
                href={whatsappLink(
                  booking.clubWhatsapp,
                  `Hola, necesito cancelar mi turno de ${booking.courtName}.`,
                )}
              >
                Escribirle al club
              </WhatsappLink>
            </>
          )}
        </div>
      )}

      <SiteFooter name={booking.clubName} address={null} />
    </Screen>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
      <div className="flex items-baseline justify-between gap-4">
        <dt className="eyebrow text-ink-soft">{label}</dt>
        <dd className="text-right font-semibold tabular-nums">{children}</dd>
      </div>
  );
}

/** El sistema no mueve plata para atras: la devolucion la coordina una persona. */
function Cancelled({ result }: { result: Cancellation }) {
  return (
    <div className="pt-10 text-center">
      <div className="mx-auto mb-4 flex size-14 items-center justify-center rounded-full bg-cal/[0.06] text-3xl text-ink-soft">
        ✓
      </div>
      <h1 className="text-2xl">Turno cancelado</h1>
      <p className="mx-auto mt-3 max-w-sm text-ink-soft">{result.message}</p>

      {result.refundNeeded && (
        <div className="mt-6">
          <WhatsappLink
            href={whatsappLink(
              result.clubWhatsapp,
              'Hola, cancelé mi turno y quería coordinar la devolución de la seña.',
            )}
          >
            Coordinar la devolución
          </WhatsappLink>
        </div>
      )}
    </div>
  );
}
