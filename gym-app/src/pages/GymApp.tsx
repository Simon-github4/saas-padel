import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { GymAuthProvider, useGymAuth } from '../auth/GymAuthContext';
import { InstallButton } from '../components/InstallGuide';
import { Alert, Button, Card, Loading, Screen, TopBar } from '../components/ui';
import { useClub } from '../hooks/useClub';
import { ChangePasswordScreen } from './ChangePasswordScreen';
import { CheckInScreen } from './CheckInScreen';
import { HomeScreen } from './HomeScreen';
import { LoginScreen } from './LoginScreen';
import { ScanScreen } from './ScanScreen';

/**
 * La app entera vive en una sola ruta ({@code /gym/:slug/*}): el backend solo sirve
 * {@code /gym/<club>} y el link del QR ({@code /gym/<club>/in/<token>}), asi que
 * login, escaner y resultado son estados de esta pantalla y no rutas propias. Y
 * como es UNA ruta, pasar de un link a otro no la desmonta ni pierde el estado.
 */
export function GymApp() {
  const { slug = '', '*': rest = '' } = useParams();
  // El token del link del QR: viaja en la URL mientras el socio entra o cambia la clave.
  const deepLinkToken = /^in\/([^/]+)\/?$/.exec(rest)?.[1] ?? null;

  return (
    <GymAuthProvider slug={slug}>
      <Shell slug={slug} deepLinkToken={deepLinkToken} />
    </GymAuthProvider>
  );
}

function Shell({ slug, deepLinkToken }: { slug: string; deepLinkToken: string | null }) {
  const club = useClub(slug);
  const { session, logout } = useGymAuth();
  const navigate = useNavigate();
  const [scanning, setScanning] = useState(false);
  // El codigo de la sede que hay que usar para registrar el ingreso. Arranca con el
  // del link del QR y se retoma solo despues de entrar o de cambiar la clave.
  const [pendingCode, setPendingCode] = useState<string | null>(deepLinkToken);

  useEffect(() => {
    if (deepLinkToken) {
      setPendingCode(deepLinkToken);
    }
  }, [deepLinkToken]);

  const ready = session !== null && !session.mustChangePassword;
  useEffect(() => {
    // El token se saca de la barra de direcciones apenas se puede usar, para que no
    // quede en el historial. El codigo pendiente sigue en el estado.
    if (ready && deepLinkToken) {
      navigate(`/gym/${slug}`, { replace: true });
    }
  }, [ready, deepLinkToken, slug, navigate]);

  if (club.status === 'loading') {
    return (
      <Screen>
        <Loading />
      </Screen>
    );
  }
  if (club.status === 'unavailable') {
    return (
      <Screen>
        <h1 className="text-3xl">Gimnasio no disponible</h1>
        <div className="mt-6">
          <Card>
            <p className="text-sm text-ink-soft">
              Este link no corresponde a ningún gimnasio activo. Revisá que sea el del cartel de la entrada.
            </p>
          </Card>
        </div>
      </Screen>
    );
  }
  if (club.status === 'error') {
    return (
      <Screen>
        <Alert>{club.message}</Alert>
        <Button className="mt-4" variant="secondary" onClick={() => window.location.reload()}>
          Reintentar
        </Button>
      </Screen>
    );
  }

  const { clubName, passwordRequired, locationRequired } = club.config;

  const top = (
    <TopBar
      name={`${clubName} · Gimnasio`}
      action={
        <div className="flex items-center gap-3">
          <InstallButton autoOpen={ready} />
          {ready && (
            <button
              type="button"
              onClick={() => void logout()}
              className="text-xs text-ink-soft underline-offset-4 hover:underline"
            >
              Salir
            </button>
          )}
        </div>
      }
    />
  );

  return <Screen top={top}>{body()}</Screen>;

  function body() {
    if (!session) {
      return <LoginScreen passwordRequired={passwordRequired} />;
    }
    if (session.mustChangePassword) {
      return <ChangePasswordScreen />;
    }
    if (pendingCode) {
      return (
        <CheckInScreen
          key={pendingCode}
          code={pendingCode}
          locationRequired={locationRequired}
          onDone={() => setPendingCode(null)}
        />
      );
    }
    if (scanning) {
      return (
        <ScanScreen
          onCode={(token) => {
            setScanning(false);
            setPendingCode(token);
          }}
          onCancel={() => setScanning(false)}
        />
      );
    }
    return <HomeScreen onScan={() => setScanning(true)} />;
  }
}
