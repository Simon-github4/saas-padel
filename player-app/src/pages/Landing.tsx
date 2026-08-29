import { Card, Screen } from '../components/Ui';

/**
 * Portada.
 *
 * <p>Nadie entra por aca en la practica: cada club comparte su propio link y el
 * jugador llega directo a su grilla. Existe para que la raiz no quede en blanco.
 */
export function Landing() {
  return (
    <Screen>
      <div className="pt-16 text-center">
        <h1 className="text-3xl font-bold tracking-tight">Reservá tu cancha</h1>
        <p className="mt-3 text-slate-600">
          Cada club tiene su propio link. Pedíselo por WhatsApp y reservá en dos toques.
        </p>
      </div>

      <Card className="mt-8">
        <p className="text-sm text-slate-600">
          Si el club ya te pasó su link, va a verse parecido a esto:
        </p>
        <code className="mt-2 block rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-700">
          /club/nombre-del-club
        </code>
      </Card>
    </Screen>
  );
}
