import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, ApiError, type BookingShareInfo } from '../api/client';
import { clockTime, longDate } from '../format';
import { Alert, Card, Loading, Screen, SiteFooter, StatusBadge, TopBar } from '../components/Ui';
import { AccountButton } from '../components/AccountButton';

/**
 * Vista publica de solo lectura del turno, para el link que el jugador le
 * manda a los demas. A diferencia del portal de gestion no permite cancelar
 * ni muestra plata: quien la recibe no es el dueño de la reserva.
 */
export function SharePage() {
  const { token = '' } = useParams();
  const [booking, setBooking] = useState<BookingShareInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        setBooking(await api.share(token));
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'No pudimos encontrar el turno.');
      } finally {
        setLoading(false);
      }
    })();
  }, [token]);

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

  return (
    <Screen className="pt-6" top={<TopBar name={booking.clubName} accountSlot={<AccountButton />} titleTo={`/club/${booking.clubSlug}`} />}>
      <header className="mb-5 mt-6">
        <h1 className="text-3xl">Turno</h1>
        {booking.bookedByName && <p className="mt-1 text-ink-soft">Reservado por {booking.bookedByName}</p>}
      </header>

      {booking.status === 'CANCELLED' && (
        <div className="mb-4">
          <Alert>Este turno se canceló: no hace falta que vayan.</Alert>
        </div>
      )}

      <Card>
        <dl className="space-y-3">
          <Row label="Cuándo">
            <span className="first-letter:uppercase">{longDate(booking.startTime)}</span>
          </Row>
          <Row label="Hora">
            {clockTime(booking.startTime, Intl.DateTimeFormat().resolvedOptions().timeZone)} hs
          </Row>
          <Row label="Cancha">{booking.courtName}</Row>
          <Row label="Club">{booking.clubName}</Row>
          <Row label="Estado">
            <StatusBadge status={booking.status} />
          </Row>
        </dl>
      </Card>

      <div className="mt-5">
        <Link
          to={`/club/${booking.clubSlug}`}
          className="block rounded-full border border-cal/10 bg-vidrio px-5 py-3.5 text-center text-sm font-bold uppercase tracking-[0.12em] text-cal transition hover:border-cal/25"
        >
          Reservá tu cancha en {booking.clubName}
        </Link>
      </div>

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
