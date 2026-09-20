import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/gymClient';
import { useGymAuth } from '../auth/GymAuthContext';
import { Alert, Button, Card, Field } from '../components/ui';
import { digitsOnly } from '../format';

/**
 * Entrar con el DNI. La clave se pide solo si el club la exige (lo normal es que no).
 */
export function LoginScreen({ passwordRequired }: { passwordRequired: boolean }) {
  const { login } = useGymAuth();
  const [dni, setDni] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setWorking(true);
    try {
      await login(digitsOnly(dni), passwordRequired ? password : undefined);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Tuvimos un problema. Probá de nuevo.');
      setWorking(false);
    }
  }

  return (
    <>
      <h1 className="text-3xl">Entrá a tu cuenta</h1>
      <p className="mt-2 text-sm text-ink-soft">
        {passwordRequired
          ? 'Con tu DNI y la clave que te dieron en el mostrador.'
          : 'Con tu DNI alcanza. Si todavía no sos socio, anotate en el mostrador.'}
      </p>

      <Card className="mt-6">
        <form className="space-y-5" onSubmit={submit}>
          <Field
            label="DNI"
            value={dni}
            onChange={setDni}
            inputMode="numeric"
            autoComplete="username"
            placeholder="Sin puntos"
          />
          {passwordRequired && (
            <Field
              label="Clave"
              type="password"
              value={password}
              onChange={setPassword}
              autoComplete="current-password"
            />
          )}
          {error && <Alert>{error}</Alert>}
          <Button type="submit" disabled={working || digitsOnly(dni).length < 6 || (passwordRequired && password.length === 0)}>
            {working ? 'Entrando…' : 'Entrar'}
          </Button>
        </form>
      </Card>

      {passwordRequired && (
        <p className="mt-4 text-center text-xs text-ink-soft">¿Todavía no tenés clave? Pedila en el mostrador.</p>
      )}
    </>
  );
}
