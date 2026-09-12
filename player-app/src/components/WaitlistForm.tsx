import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { usePlayerAuth } from '../auth/AuthContext';
import { Alert, Button, Field } from './Ui';

/**
 * Anotarse para que avisen si se libera una cancha en un horario lleno.
 *
 * <p>Se expande adentro de la ficha del horario, no como un paso de checkout
 * aparte: es un solo dato que pedir (nombre y teléfono), no una reserva.
 *
 * <p>Exige sesión iniciada -a diferencia de reservar, que admite invitado-: el
 * aviso necesita un mail al que caer cuando el WhatsApp del club está apagado o
 * el envío real falla, y un invitado sin cuenta no tiene uno confiable que
 * ofrecer. El botón "Avisame" sigue siempre visible; sin sesión, tocarlo
 * ofrece iniciar sesión o crear una cuenta en vez de mostrar el formulario.
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
  const navigate = useNavigate();
  const { session, clearExpiredSession } = usePlayerAuth();
  const [fullName, setFullName] = useState(session?.displayName ?? '');
  const [phone, setPhone] = useState(session?.phoneNumber ?? '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!session) {
    // Sin cuenta todavía: se vuelve acá mismo después de iniciar sesión o
    // registrarse, así no se pierde el club en el camino.
    const returnTo = encodeURIComponent(`/club/${slug}`);
    return (
      <div className="mt-3 space-y-2">
        <Alert tone="info">Para anotarte necesitás una cuenta, así te avisamos si se libera.</Alert>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => navigate(`/login?returnTo=${returnTo}`)}>
            Iniciar sesión
          </Button>
          <Button onClick={() => navigate(`/login?mode=register&returnTo=${returnTo}`)}>
            Crear cuenta
          </Button>
        </div>
      </div>
    );
  }

  const submit = async () => {
    setLoading(true);
    setError(null);
    try {
      await api.joinWaitlist(slug, { startTime, fullName, phoneNumber: phone }, session.token);
      onJoined();
    } catch (err) {
      if (err instanceof ApiError && err.requiresLogin) {
        // La sesión venció justo en el medio: se cae acá mismo, y el próximo
        // render ya muestra el cartel de "iniciar sesión" solo, sin que
        // vuelva a intentar con un token que el backend ya rechazó.
        clearExpiredSession();
        return;
      }
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
