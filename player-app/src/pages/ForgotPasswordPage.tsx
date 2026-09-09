import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError, playerApi } from '../api/client';
import { Alert, Button, Card, Field, Screen, SectionTitle } from '../components/Ui';

/**
 * Pide el link para resetear la contraseña. Responde siempre el mismo mensaje,
 * exista o no ese email -- así la pantalla misma no delata qué cuentas existen.
 */
export function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);
  const [working, setWorking] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setWorking(true);
    try {
      await playerApi.requestPasswordReset(email);
      setSent(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Tuvimos un problema. Probá de nuevo.');
    } finally {
      setWorking(false);
    }
  }

  return (
    <Screen className="pt-10">
      <SectionTitle title="Recuperar contraseña" subtitle="Te mandamos un link para elegir una nueva." />
      <div className="mt-6">
        <Card>
          {sent ? (
            <div className="space-y-5 text-center">
              <p className="text-ink-soft">
                Si <strong>{email}</strong> tiene una cuenta, te mandamos un link para resetear la contraseña.
              </p>
              <Button onClick={() => navigate('/login')}>Volver a entrar</Button>
            </div>
          ) : (
            <form className="space-y-5" onSubmit={submit}>
              <Field
                label="Email"
                value={email}
                onChange={setEmail}
                type="email"
                autoComplete="email"
                placeholder="vos@email.com"
              />
              {error && <Alert>{error}</Alert>}
              <Button type="submit" disabled={working || !email.trim()}>
                {working ? 'Enviando…' : 'Mandar link'}
              </Button>
            </form>
          )}
        </Card>
      </div>
    </Screen>
  );
}
