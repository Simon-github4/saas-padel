import { useId, useState, type CSSProperties } from 'react';
import { money } from '../../format';
import { DemoClubCta, WhatsappCta } from './Cta';
import {
  MONTHLY_PRICE_ARS,
  SALES_EMAIL,
  TRIAL_DAYS,
  subscribeMailHref,
  subscribeWhatsappHref,
} from './config';
import { CONTAINER, CheckGlyph, Reveal, SectionHeading } from './motion';

const INCLUDED = [
  'Grilla online para tus canchas, con tu link y tu marca',
  'Panel para cargar turnos de teléfono y mostrador',
  'Seña por MercadoPago a tu propia cuenta, o reserva de palabra',
  'Turnos fijos, alertas y jugadores con marca de confianza',
];

/** El dueño piensa el precio por jugador; el turno es de a cuatro. */
const PLAYERS_PER_TURN = 4;
const PLAYER_MIN = 6000;
const PLAYER_MAX = 22000;
const PLAYER_STEP = 500;
const PLAYER_DEFAULT = 9000;

/**
 * Un solo plan, sin letra chica: precio fijo por club y el alta la hace el
 * vendedor a mano, así que el CTA manda un mensaje en vez de abrir un
 * checkout. La prueba se agota sola, no hay que acordarse de cancelar nada.
 *
 * <p>La cuenta de al lado traduce el precio a la unidad con la que piensa el
 * dueño —turnos— a partir de lo que cobra por jugador, multiplicado por los
 * cuatro de cada turno. No promete cuántos turnos trae el sistema: sólo
 * cuántos hacen falta para cubrirlo.
 */
export function PricingSection() {
  const [playerPrice, setPlayerPrice] = useState(PLAYER_DEFAULT);
  const sliderId = useId();
  const turnPrice = playerPrice * PLAYERS_PER_TURN;
  const turns = Math.ceil(MONTHLY_PRICE_ARS / turnPrice);
  const fill = ((playerPrice - PLAYER_MIN) / (PLAYER_MAX - PLAYER_MIN)) * 100;

  return (
    <section id="precio" className="scroll-mt-16 py-24 md:py-36">
      <div className={CONTAINER}>
        <SectionHeading center eyebrow="Precio" title="Un plan, sin letra chica" />

        <Reveal delay={100} className="mt-16 md:mt-20">
          <div className="grid overflow-hidden rounded-[2rem] border border-cal/10 bg-vidrio [box-shadow:var(--shadow-card)] lg:grid-cols-2">
            <div className="relative p-5 sm:p-10 lg:p-12">
              <div
                aria-hidden
                className="pointer-events-none absolute inset-0 bg-[radial-gradient(70%_60%_at_0%_0%,rgba(234,88,12,0.14),transparent_70%)]"
              />
              <div className="relative">
                <p className="eyebrow text-ladrillo-claro">Plan único</p>
                <p className="display mt-4 flex items-baseline gap-3 text-[clamp(3.75rem,9vw,5.5rem)] leading-none tracking-[0.02em]">
                  {money(MONTHLY_PRICE_ARS)}
                  <span className="font-sans text-lg font-normal tracking-normal text-ink-soft">/mes</span>
                </p>
                <p className="mt-3 text-ink-soft">Por club, un solo pago mensual.</p>

                <div className="mt-8 flex items-center gap-4 rounded-2xl border border-ladrillo/30 bg-ladrillo/[0.07] p-4">
                  <span className="display grid size-14 shrink-0 place-items-center rounded-xl bg-ladrillo text-3xl text-cal">
                    {TRIAL_DAYS}
                  </span>
                  <p className="text-sm">
                    <span className="font-semibold">Días gratis</span>
                    <span className="block text-ink-soft">para probarlo en tu club antes de pagar.</span>
                  </p>
                </div>

                <div className="mt-8 flex flex-col gap-3">
                  <WhatsappCta href={subscribeWhatsappHref()}>Quiero mi prueba gratis</WhatsappCta>
                  <DemoClubCta>Ver un club real</DemoClubCta>
                  <a
                    href={subscribeMailHref()}
                    className="mt-1 text-center text-xs font-semibold uppercase tracking-[0.12em] text-ink-soft transition hover:text-cal"
                  >
                    O escribinos a {SALES_EMAIL}
                  </a>
                </div>
              </div>
            </div>

            <div className="flex flex-col border-t border-cal/10 bg-pista/50 p-5 sm:p-10 lg:border-l lg:border-t-0 lg:p-12">
              <p className="eyebrow text-ink-mute">Incluye todo</p>
              <ul className="mt-5 space-y-4">
                {INCLUDED.map((item) => (
                  <li key={item} className="flex gap-3 text-[0.95rem] leading-relaxed text-arena">
                    <span className="mt-0.5 grid size-5 shrink-0 place-items-center rounded-full bg-ladrillo/15 text-ladrillo-claro">
                      <CheckGlyph className="size-3" />
                    </span>
                    {item}
                  </li>
                ))}
              </ul>

              <div className="mt-10 rounded-2xl border border-cal/10 bg-vidrio p-5 sm:p-6 lg:mt-auto">
                <div className="flex items-baseline justify-between gap-4">
                  <label htmlFor={sliderId} className="text-sm text-ink-soft">
                    ¿Cuánto cobrás por jugador?
                  </label>
                  <output htmlFor={sliderId} className="display text-2xl tabular-nums">
                    {money(playerPrice)}
                  </output>
                </div>
                <input
                  id={sliderId}
                  type="range"
                  min={PLAYER_MIN}
                  max={PLAYER_MAX}
                  step={PLAYER_STEP}
                  value={playerPrice}
                  onChange={(event) => setPlayerPrice(Number(event.target.value))}
                  className="mk-range mt-4 w-full"
                  style={{ '--mk-fill': `${fill}%` } as CSSProperties}
                />
                <p className="mt-4 text-sm text-ink-soft" aria-live="polite">
                  El turno de {PLAYERS_PER_TURN} sale{' '}
                  <span className="font-semibold tabular-nums text-cal">{money(turnPrice)}</span>. Lo cubrís con{' '}
                  <span className="font-semibold text-cal">
                    {turns} {turns === 1 ? 'turno' : 'turnos'}
                  </span>{' '}
                  de tu club por mes.
                </p>
              </div>
            </div>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
