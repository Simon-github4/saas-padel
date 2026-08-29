import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api, ApiError, type BookingDetail, type Cancellation } from '../api/client';
import { clockTime, longDate, money, whatsappLink } from '../format';
import { Alert, Button, Card, Loading, Screen, WhatsappLink } from '../components/Ui';

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
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No pudimos cancelar el turno.');
      setConfirmingCancel(false);
    } finally {
      setWorking(false);
    }
  }

  if (loading) {
    return (
      <Screen>
        <Loading />
      </Screen>
    );
  }

  if (!booking) {
    return (
      <Screen>
        <Alert>{error ?? 'Este link no corresponde a ningún turno.'}</Alert>
      </Screen>
    );
  }

  if (cancelled) {
    return (
      <Screen>
        <Cancelled result={cancelled} />
      </Screen>
    );
  }

  const active = booking.status === 'CONFIRMED' || booking.status === 'AWAITING_CONFIRMATION';

  return (
    <Screen>
      <header className="mb-5">
        <h1 className="text-2xl font-bold tracking-tight">Tu turno</h1>
        <p className="text-sm text-slate-500">{booking.clubName}</p>
      </header>

      {mode === 'confirm' && booking.status === 'CONFIRMED' && (
        <div className="mb-4">
          <Alert tone="success">Listo, tu turno quedó confirmado.</Alert>
        </div>
      )}

      <Card>
        <dl className="space-y-3">
          <Row label="Cuándo">
            {/* Solo la inicial: "capitalize" pone en mayuscula cada palabra y deja
                "Sabado, 5 De Septiembre". */}
            <span className="first-letter:uppercase">{longDate(booking.startTime)}</span>,{' '}
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
                <p className="mb-3 text-sm text-slate-700">
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
    </Screen>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-sm text-slate-500">{label}</dt>
      <dd className="text-right font-medium">{children}</dd>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { text: string; className: string }> = {
    CONFIRMED: { text: 'Confirmado', className: 'bg-emerald-100 text-emerald-800' },
    AWAITING_CONFIRMATION: { text: 'Sin confirmar', className: 'bg-amber-100 text-amber-800' },
    DRAFT: { text: 'Esperando pago', className: 'bg-amber-100 text-amber-800' },
    COMPLETED: { text: 'Jugado', className: 'bg-slate-100 text-slate-700' },
    CANCELLED: { text: 'Cancelado', className: 'bg-red-100 text-red-800' },
    NO_SHOW: { text: 'No te presentaste', className: 'bg-red-100 text-red-800' },
  };
  const badge = map[status] ?? { text: status, className: 'bg-slate-100 text-slate-700' };

  return (
    <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${badge.className}`}>
      {badge.text}
    </span>
  );
}

/** El sistema no mueve plata para atras: la devolucion la coordina una persona. */
function Cancelled({ result }: { result: Cancellation }) {
  return (
    <div className="pt-10 text-center">
      <div className="mx-auto mb-4 flex size-14 items-center justify-center rounded-full bg-slate-200 text-3xl">
        ✓
      </div>
      <h1 className="text-xl font-bold">Turno cancelado</h1>
      <p className="mx-auto mt-2 max-w-sm text-slate-600">{result.message}</p>

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
