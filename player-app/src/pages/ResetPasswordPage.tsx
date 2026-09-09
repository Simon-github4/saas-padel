import { useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError, playerApi } from '../api/client';
import { Alert, Button, Card, Field, Screen, SectionTitle } from '../components/Ui';

/** Aplica la contraseña nueva a partir del token que llegó por email. */
export function ResetPasswordPage() {
  const navigate = useNavigate();
  const { token } = useParams<{ token: string }>();
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [working, setWorking] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!token) {
      return;
    }
    setError(null);
    setWorking(true);
    try {
      await playerApi.resetPassword(token, newPassword);
      setDone(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Tuvimos un problema. Probá de nuevo.');
    } finally {
      setWorking(false);
    }
  }

  return (
    <Screen className="pt-10">
      <SectionTitle title="Elegí una contraseña nueva" subtitle="Al menos 8 caracteres." />
      <div className="mt-6">
        <Card>
          {done ? (
            <div className="space-y-5 text-center">
              <p className="text-ink-soft">Listo, ya podés entrar con tu contraseña nueva.</p>
              <Button onClick={() => navigate('/login')}>Ir a entrar</Button>
            </div>
          ) : (
            <form className="space-y-5" onSubmit={submit}>
              <Field
                label="Contraseña nueva"
                value={newPassword}
                onChange={setNewPassword}
                type="password"
                autoComplete="new-password"
                placeholder="Al menos 8 caracteres"
              />
              {error && <Alert>{error}</Alert>}
              <Button type="submit" disabled={working || newPassword.trim().length < 8}>
                {working ? 'Guardando…' : 'Guardar contraseña'}
              </Button>
            </form>
          )}
        </Card>
      </div>
    </Screen>
  );
}
