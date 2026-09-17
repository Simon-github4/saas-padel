import { useEffect, useState } from 'react';
import { money } from '../../format';
import { MercadoPagoLogo, MercadoPagoLogoColor } from './MercadoPagoLogo';
import { CONTAINER, CheckGlyph, Reveal, SectionHeading, delay, useInView, usePrefersReducedMotion } from './motion';

/** Un turno de ejemplo, con la seña que el club elige (acá, la mitad). */
const TURN_PRICE = 18000;
const DEPOSIT_RATE = 0.5;
const DEPOSIT = TURN_PRICE * DEPOSIT_RATE;

const POINTS = [
  {
    title: 'Conectás tu cuenta en un clic',
    text: 'Entrás con tu usuario de Mercado Pago desde el panel y listo. No hay claves que copiar ni trámites.',
  },
  {
    title: 'La plata es tuya, desde el primer peso',
    text: 'La seña se acredita en tu cuenta, no en la nuestra. No cobramos comisión por reserva: solo el abono mensual.',
  },
  {
    title: 'La reserva se confirma sola',
    text: 'Cuando Mercado Pago acredita el pago, el turno queda confirmado y la cancha deja de estar libre, sin que mires el teléfono.',
  },
];

/**
 * El cobro de la seña, que es lo primero que pregunta un club: de quién es la
 * plata y qué tiene que hacer para cobrarla. Tiene sección propia y no una
 * tarjeta más porque es la función que decide la venta.
 *
 * <p>Al lado, lo que ve el jugador: la seña, el pago y el turno confirmado. Se
 * juega solo al entrar en pantalla y se puede volver a jugar tocando el botón.
 */
export function PaymentsSection() {
  return (
    <section id="cobros" className="scroll-mt-32 lg:scroll-mt-16 border-y border-cal/10 bg-vidrio/40 py-24 md:py-36">
      <div className={`${CONTAINER} grid items-center gap-14 lg:grid-cols-2 lg:gap-20`}>
        <div>
          <SectionHeading
            eyebrow="Cobros"
            title={
              <>
                La seña entra a tu <span className="text-ladrillo">Mercado Pago</span>
              </>
            }
            lead="El jugador paga desde tu link con tarjeta, dinero en cuenta o transferencia, como ya paga todo. Vos la ves acreditada en tu cuenta de siempre."
          />

          <Reveal delay={120}>
            <MercadoPagoLogo className="mt-9 h-10 w-auto" />
          </Reveal>

          <ul className="mt-9 space-y-6">
            {POINTS.map((point, index) => (
              <Reveal key={point.title} delay={180 + index * 110}>
                <li className="flex gap-4">
                  <CheckGlyph className="mt-1 size-5 shrink-0 text-ladrillo-claro" />
                  <div>
                    <h3 className="font-semibold">{point.title}</h3>
                    <p className="mt-1.5 max-w-md text-sm leading-relaxed text-ink-soft">{point.text}</p>
                  </div>
                </li>
              </Reveal>
            ))}
          </ul>

          <Reveal delay={520}>
            <p className="mt-9 max-w-md rounded-2xl border border-cal/10 bg-pista px-5 py-4 text-sm text-ink-soft">
              ¿No querés cobrar online? Dejás reservar de palabra y el turno queda confirmado al instante. Vos
              elegís, y también cuánto es la seña: el 30%, la mitad o el turno entero.
            </p>
          </Reveal>
        </div>

        <Reveal delay={100}>
          <CheckoutDemo />
        </Reveal>
      </div>
    </section>
  );
}

type Stage = 'pendiente' | 'pagando' | 'acreditada';

/** Lo que ve el jugador al reservar: la seña, el pago y el turno confirmado. */
function CheckoutDemo() {
  const reduced = usePrefersReducedMotion();
  const [ref, shown] = useInView<HTMLDivElement>();
  const [stage, setStage] = useState<Stage>('pendiente');

  // Sola al entrar en pantalla: el que solo mira ve la promesa completa igual.
  useEffect(() => {
    if (!shown || reduced || stage !== 'pendiente') {
      return;
    }
    const id = window.setTimeout(() => setStage('pagando'), 1200);
    return () => window.clearTimeout(id);
  }, [shown, reduced, stage]);

  useEffect(() => {
    if (stage !== 'pagando') {
      return;
    }
    const id = window.setTimeout(() => setStage('acreditada'), 1400);
    return () => window.clearTimeout(id);
  }, [stage]);

  const paid = stage === 'acreditada';

  return (
    <div
      ref={ref}
      className="rounded-[1.75rem] border border-cal/10 bg-vidrio p-5 [box-shadow:var(--shadow-card)] sm:p-7"
    >
      <p className="eyebrow text-ink-mute">Lo que ve el jugador</p>

      <div className="mt-5 rounded-2xl border border-cal/10 bg-pista p-5">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="font-semibold">Cancha 2 · Sábado 21:00</p>
            <p className="mt-0.5 text-xs text-ink-soft">90 minutos · 4 jugadores</p>
          </div>
          <span
            className={`eyebrow shrink-0 rounded-full px-2.5 py-1 transition-colors duration-500 ${
              paid ? 'bg-ladrillo/15 text-ladrillo-claro' : 'bg-cal/10 text-ink-soft'
            }`}
          >
            {paid ? 'Confirmada' : 'A confirmar'}
          </span>
        </div>

        <dl className="mt-5 space-y-2.5 text-sm">
          <Row label={`Seña (${Math.round(DEPOSIT_RATE * 100)}%)`} value={money(DEPOSIT)} strong />
          <Row label="Resto, en el club" value={money(TURN_PRICE - DEPOSIT)} />
        </dl>
      </div>

      <button
        type="button"
        onClick={() => setStage(paid ? 'pagando' : 'acreditada')}
        aria-label={paid ? 'Ver el pago otra vez' : 'Pagar la seña con Mercado Pago'}
        className={`mt-4 flex w-full items-center justify-center gap-3 rounded-2xl px-5 py-4 font-semibold transition duration-300 hover:brightness-105 ${
          paid ? 'border border-cal/10 bg-cal/[0.06] text-cal' : 'bg-cal text-pista'
        }`}
      >
        {paid ? (
          <span className="text-sm text-ink-soft">Volver a verlo</span>
        ) : (
          <>
            <MercadoPagoLogoColor className="h-6 w-auto shrink-0" />
            <span>{stage === 'pagando' ? 'Procesando el pago…' : `Pagar ${money(DEPOSIT)}`}</span>
          </>
        )}
      </button>

      {/* Alto fijo: el aviso aparece sin correr la tarjeta hacia abajo. */}
      <div className="mt-4 min-h-[4.5rem]">
        {paid && (
          <div className="mk-fade-up flex items-center gap-3 rounded-2xl border border-ladrillo/40 bg-ladrillo/[0.08] px-4 py-3.5" style={delay(0)}>
            <CheckGlyph className="size-5 shrink-0 text-ladrillo-claro" />
            <p className="text-sm">
              <span className="font-semibold">Seña acreditada en tu cuenta</span>
              <span className="block text-xs text-ink-soft">La cancha ya figura reservada en tu agenda.</span>
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

function Row({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className={strong ? 'text-arena' : 'text-ink-soft'}>{label}</dt>
      <dd className={`tabular-nums ${strong ? 'text-lg font-bold' : 'text-ink-soft'}`}>{value}</dd>
    </div>
  );
}
