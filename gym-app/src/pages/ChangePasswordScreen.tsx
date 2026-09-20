import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/gymClient';
import { useGymAuth } from '../auth/GymAuthContext';
import { Alert, Button, Card, Field } from '../components/ui';

const MIN_LENGTH = 8;

/**
 * Cambio obligatorio de la clave temporal.
 *
 * <p>La clave del mostrador la conoce otra persona, asi que hasta que el socio
 * la cambie no puede usar nada mas. Si acaba de entrar, la app se acuerda de la
 * que escribio (solo en memoria) y no se la pide de nuevo; si recargo la pagina,
 * hay que volver a escribirla.
 */
export function ChangePasswordScreen() {
  const { temporaryPassword, changePassword, logout } = useGymAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [repeat, setRepeat] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);

  const currentPassword = temporaryPassword ?? current;
  const invalid = currentPassword.length === 0 || next.length < MIN_LENGTH || next !== repeat;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setWorking(true);
    try {
      await changePassword(currentPassword, next);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Tuvimos un problema. Probá de nuevo.');
      setWorking(false);
    }
  }

  return (
    <>
      <h1 className="text-3xl">Elegí tu clave</h1>
      <p className="mt-2 text-sm text-ink-soft">
        La que te dieron en el mostrador es temporal. Elegí una que solo sepas vos.
      </p>

      <Card className="mt-6">
        <form className="space-y-5" onSubmit={submit}>
          {temporaryPassword === null && (
            <Field
              label="Clave del mostrador"
              type="password"
              value={current}
              onChange={setCurrent}
              autoComplete="current-password"
            />
          )}
          <Field
            label="Clave nueva"
            type="password"
            value={next}
            onChange={setNext}
            autoComplete="new-password"
            hint={`Al menos ${MIN_LENGTH} caracteres.`}
          />
          <Field
            label="Repetí la clave nueva"
            type="password"
            value={repeat}
            onChange={setRepeat}
            autoComplete="new-password"
          />
          {repeat.length > 0 && next !== repeat && <Alert tone="info">Las dos claves tienen que ser iguales.</Alert>}
          {error && <Alert>{error}</Alert>}
          <Button type="submit" disabled={working || invalid}>
            {working ? 'Guardando…' : 'Guardar clave'}
          </Button>
        </form>
      </Card>

      <button
        type="button"
        onClick={() => void logout()}
        className="mt-4 block w-full text-center text-xs text-ink-soft underline-offset-4 hover:underline"
      >
        Salir
      </button>
    </>
  );
}
