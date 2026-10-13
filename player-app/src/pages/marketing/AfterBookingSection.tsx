import { useEffect, useState } from 'react';
import { money } from '../../format';
import { CONTAINER, CheckGlyph, Reveal, SectionHeading, delay, useInView, usePrefersReducedMotion } from './motion';

/** El plazo de cancelación que trae cada club de fábrica (Club.cancellationLimitHours). */
const CANCEL_HOURS = 12;
/** Un turno de ejemplo: el club carga el precio por persona y el turno es de a cuatro. */
const TURN_PRICE = 6500 * 4;

const POINTS = [
  {
    title: 'Sus turnos, a mano',
    text: 'Los de hoy y los próximos, con cancha y hora. Con cuenta, también sus turnos fijos, desde cualquier celular.',
  },
  {
    title: 'Cancela solo, a tiempo',
    text: `Hasta ${CANCEL_HOURS} horas antes, o el plazo que elijas. Más tarde te tiene que escribir: la cancha no se te cae a último momento.`,
  },
  {
    title: 'Se lo pasa al resto',
    text: 'Manda el turno a los otros tres con un link, y nadie te pregunta a qué hora era.',
  },
  {
    title: 'Como una app, sin bajar nada',
    text: 'Lo instala en la pantalla de inicio del celular, sin pasar por la tienda de aplicaciones.',
  },
];

/**
 * Lo que pasa después de reservar, contado desde el lado del dueño: el jugador
 * ya no le escribe para preguntar a qué hora juega ni para que le liberen la
 * cancha. Es la otra mitad de "Lo que el link contesta por vos" (el "Al final
 * no vamos, ¿la liberás?" de la cinta de mensajes).
 *
 * <p>Al lado, Mis turnos como lo ve el jugador: el de hoy ya no se puede
 * cancelar (faltan menos de 12 horas) y el del sábado se cancela solo al
 * entrar en pantalla, o tocándolo.
 */
export function AfterBookingSection() {
  return (
    <section id="despues" className="scroll-mt-32 lg:scroll-mt-16 py-24 md:py-36">
      <div className={`${CONTAINER} grid items-center gap-14 lg:grid-cols-[1fr_1.1fr] lg:gap-20`}>
        <div className="lg:order-2">
          <SectionHeading
            eyebrow="Después de reservar"
            title="Y tampoco te escribe para cancelar"
            lead="Cada jugador tiene sus turnos en el celular: ve cuándo juega, se lo pasa a los otros tres y, si no puede ir, lo cancela él mismo. La cancha vuelve a tu link sin que hagas nada."
          />

          {/* En una sola columna entre lg y xl: al lado de la tarjeta, dos quedaban apretadas. */}
          <ul className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
            {POINTS.map((point, index) => (
              <Reveal key={point.title} delay={120 + index * 90}>
                <li className="flex gap-3">
                  <CheckGlyph className="mt-1 size-4 shrink-0 text-ladrillo-claro" />
                  <div>
                    <h3 className="font-semibold">{point.title}</h3>
                    <p className="mt-1 text-sm leading-relaxed text-ink-soft">{point.text}</p>
                  </div>
                </li>
              </Reveal>
            ))}
          </ul>
        </div>

        <Reveal delay={100} className="lg:order-1">
          <MyBookingsDemo />
        </Reveal>
      </div>
    </section>
  );
}

type Stage = 'reservado' | 'cancelando' | 'cancelado';

/** Mis turnos del jugador: el del sábado se cancela y la cancha vuelve al link del club. */
function MyBookingsDemo() {
  const reduced = usePrefersReducedMotion();
  const [ref, shown] = useInView<HTMLDivElement>();
  const [stage, setStage] = useState<Stage>('reservado');
  // Se juega sola una sola vez: después de "Volver a verlo" queda esperando el toque.
  const [played, setPlayed] = useState(false);

  useEffect(() => {
    if (!shown || reduced || played || stage !== 'reservado') {
      return;
    }
    const id = window.setTimeout(() => {
      setPlayed(true);
      setStage('cancelando');
    }, 1800);
    return () => window.clearTimeout(id);
  }, [shown, reduced, played, stage]);

  useEffect(() => {
    if (stage !== 'cancelando') {
      return;
    }
    const id = window.setTimeout(() => setStage('cancelado'), 1100);
    return () => window.clearTimeout(id);
  }, [stage]);

  const cancelled = stage === 'cancelado';

  return (
    <div
      ref={ref}
      className="mx-auto max-w-md rounded-[1.75rem] border border-cal/10 bg-vidrio p-5 [box-shadow:var(--shadow-card)] sm:p-7"
    >
      <div className="flex items-center justify-between gap-3">
        <p className="eyebrow text-ink-mute">Lo que ve el jugador</p>
        <p className="display text-lg tracking-[0.12em]">Mis turnos</p>
      </div>

      <p className="eyebrow mt-6 text-ink-soft">Hoy</p>
      <div className="mt-2.5 rounded-2xl border border-cal/10 bg-pista p-4">
        <BookingHead court="Cancha 2" when="Hoy · 21:00" />
        <div className="mt-3 flex items-center justify-between gap-3">
          <p className="text-sm font-semibold tabular-nums">{money(TURN_PRICE)}</p>
          <span className="shrink-0 whitespace-nowrap rounded-full border border-cal/15 px-4 py-2 text-xs font-bold uppercase tracking-[0.1em] text-cal">
            Ver turno
          </span>
        </div>
        <p className="mt-3 border-t border-cal/10 pt-3 text-xs text-ink-mute">
          Faltan menos de {CANCEL_HOURS} horas: para cancelar, te escribe a vos.
        </p>
      </div>

      <p className="eyebrow mt-6 text-ink-soft">Próximos</p>
      <ul className="mt-2.5 space-y-2.5">
        <li className="rounded-2xl border border-cal/10 bg-pista p-4">
          <BookingHead court="Cancha 1" when="Jueves · 19:30" />
          <p className="eyebrow mt-3 text-ink-soft">Turno fijo</p>
        </li>
        <li
          className={`rounded-2xl border p-4 transition-colors duration-500 ${
            cancelled ? 'border-cal/10 bg-pista/40' : 'border-cal/10 bg-pista'
          }`}
        >
          <BookingHead court="Cancha 3" when="Sábado · 10:30" cancelled={cancelled} />
          <div className="mt-3 flex items-center justify-between gap-3">
            <p className={`text-sm font-semibold tabular-nums ${cancelled ? 'text-ink-mute line-through' : ''}`}>
              {money(TURN_PRICE)}
            </p>
            {cancelled ? (
              <button
                type="button"
                onClick={() => setStage('reservado')}
                className="shrink-0 whitespace-nowrap rounded-full px-2 py-2 text-xs font-semibold text-ink-soft underline-offset-4 transition hover:text-cal hover:underline"
              >
                Volver a verlo
              </button>
            ) : (
              <button
                type="button"
                onClick={() => setStage('cancelando')}
                disabled={stage === 'cancelando'}
                aria-label="Cancelar el turno del sábado"
                className={`shrink-0 whitespace-nowrap rounded-full px-4 py-2 text-xs font-bold uppercase tracking-[0.1em] text-cal transition duration-300 ${
                  stage === 'cancelando' ? 'scale-95 bg-ladrillo/70' : 'bg-ladrillo hover:bg-ladrillo/90'
                }`}
              >
                {stage === 'cancelando' ? 'Cancelando…' : 'Ver o cancelar'}
              </button>
            )}
          </div>
        </li>
      </ul>

      {/* Alto fijo: el aviso aparece sin correr la tarjeta hacia abajo. */}
      <div className="mt-4 min-h-[4.5rem]" aria-live="polite">
        {cancelled && (
          <div
            className="mk-fade-up flex items-center gap-3 rounded-2xl border border-ladrillo/40 bg-ladrillo/[0.08] px-4 py-3.5"
            style={delay(0)}
          >
            <CheckGlyph className="size-5 shrink-0 text-ladrillo-claro" />
            <p className="text-sm">
              <span className="font-semibold">El sábado 10:30 volvió a estar libre en tu link</span>
              <span className="block text-xs text-ink-soft">
                Y a los anotados en la lista de espera les llega el aviso solo.
              </span>
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

function BookingHead({ court, when, cancelled = false }: { court: string; when: string; cancelled?: boolean }) {
  return (
    <div className="flex items-start justify-between gap-3">
      {/* La fecha arriba y corta: con el club y la cancha en el mismo renglón, en el celular se partía la hora. */}
      <div className={cancelled ? 'text-ink-mute' : ''}>
        <p className="font-semibold">{when}</p>
        <p className={`text-sm ${cancelled ? '' : 'text-ink-soft'}`}>Tu club · {court}</p>
      </div>
      <span
        className={`eyebrow shrink-0 rounded-full px-2.5 py-1 transition-colors duration-500 ${
          cancelled ? 'bg-cal/10 text-ink-mute' : 'bg-ladrillo/15 text-ladrillo-claro'
        }`}
      >
        {cancelled ? 'Cancelado' : 'Confirmado'}
      </span>
    </div>
  );
}
