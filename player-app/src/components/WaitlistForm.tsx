import { useState } from 'react';
import { api, ApiError } from '../api/client';
import { usePlayerAuth } from '../auth/AuthContext';
import { Alert, Button, Field } from './Ui';

/**
 * Anotarse para que avisen si se libera una cancha en un horario lleno.
 *
 * <p>Se expande adentro de la ficha del horario, no como un paso de checkout
 * aparte: es un solo dato que pedir (nombre y teléfono), no una reserva.
 */
export function WaitlistForm({
  slug,
  startTime,
  onJoined,
}: {
  slug: string;
  startTime: string;
  onJoined: () => void;
}) {
  const { session } = usePlayerAuth();
  const [fullName, setFullName] = useState(session?.displayName ?? '');
  const [phone, setPhone] = useState(session?.phoneNumber ?? '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setLoading(true);
    setError(null);
    try {
      await api.joinWaitlist(slug, { startTime, fullName, phoneNumber: phone });
      onJoined();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo anotar. Probá de nuevo.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mt-3 space-y-2">
      <Field label="Nombre" value={fullName} onChange={setFullName} autoComplete="name" />
      <Field
        label="Teléfono"
        value={phone}
        onChange={setPhone}
        type="tel"
        inputMode="tel"
        autoComplete="tel"
      />
      {error && <Alert>{error}</Alert>}
      <Button onClick={() => void submit()} disabled={loading || !fullName || !phone}>
        {loading ? 'Anotando…' : 'Avisame'}
      </Button>
    </div>
  );
}
