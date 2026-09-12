import { Link } from 'react-router-dom';
import { money } from '../../format';
import { SectionTitle } from '../../components/Ui';
import {
  MONTHLY_PRICE_ARS,
  SALES_EMAIL,
  TRIAL_DAYS,
  subscribeMailHref,
  subscribeWhatsappHref,
} from './config';

const INCLUDED = [
  'Grilla online para tus canchas, con tu link y tu marca',
  'Panel para cargar turnos de teléfono y mostrador',
  'Seña por MercadoPago a tu propia cuenta, o reserva de palabra',
  'Turnos fijos, alertas y jugadores con marca de confianza',
];

/**
 * Un solo plan, sin letra chica: precio fijo por club y el alta la hace el
 * vendedor a mano, así que el CTA manda un mensaje en vez de abrir un
 * checkout. La prueba se agota sola, no hay que acordarse de cancelar nada.
 */
export function PricingSection() {
  return (
    <section id="precio" className="scroll-mt-20 py-16 md:py-20">
      <SectionTitle title="Precio" subtitle="Un plan, sin letra chica." />
      <div className="mx-auto mt-8 max-w-md rounded-2xl border border-cal/10 bg-vidrio p-6 sm:p-8">
        <p className="eyebrow text-ladrillo-claro">Plan único</p>
        <p className="display mt-2 text-4xl tracking-[0.04em]">
          {money(MONTHLY_PRICE_ARS)}
          <span className="text-base font-normal text-ink-soft"> /mes</span>
        </p>
        <p className="mt-1 text-sm text-ink-soft">Por club, un solo pago mensual.</p>

        <ul className="mt-6 space-y-3">
          {INCLUDED.map((item) => (
            <li key={item} className="flex gap-3 text-sm text-ink-soft">
              <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-ladrillo-claro" aria-hidden />
              {item}
            </li>
          ))}
        </ul>

        <div className="mt-6 rounded-xl border border-cal/10 bg-pista/60 p-4 text-center text-sm">
          <span className="font-semibold text-ladrillo-claro">{TRIAL_DAYS} días gratis</span>{' '}
          <span className="text-ink-soft">para probarlo en tu club antes de pagar.</span>
        </div>

        <div className="mt-6 flex flex-col gap-3">
          <a
            href={subscribeWhatsappHref()}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center justify-center gap-2 rounded-full bg-ladrillo px-6 py-3.5 text-sm font-bold uppercase tracking-[0.12em] text-cal transition hover:bg-ladrillo/90 [box-shadow:var(--shadow-glow)]"
          >
            Quiero mi prueba gratis
            <span aria-hidden>→</span>
          </a>
          <Link
            to="/club/club-necochea"
            target="_blank"
            className="inline-flex items-center justify-center gap-2 rounded-full border border-cal/10 bg-pista px-6 py-3.5 text-sm font-bold uppercase tracking-[0.12em] text-cal transition hover:border-cal/25"
          >
            Ver un club real funcionando
            <span aria-hidden>→</span>
          </Link>
          <a
            href={subscribeMailHref()}
            className="text-center text-xs font-semibold uppercase tracking-[0.12em] text-ink-soft transition hover:text-cal"
          >
            O escribinos a {SALES_EMAIL}
          </a>
        </div>
      </div>
    </section>
  );
}
