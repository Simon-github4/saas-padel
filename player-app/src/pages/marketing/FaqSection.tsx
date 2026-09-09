import { SectionTitle } from '../../components/Ui';
import { TRIAL_DAYS } from './config';

const FAQS = [
  {
    q: '¿Tengo que cambiar cómo trabajo?',
    a: 'No. Seguís atendiendo por WhatsApp y en el mostrador como siempre; el sistema suma un canal más, no reemplaza el que ya usás.',
  },
  {
    q: '¿Y los turnos que entran por teléfono?',
    a: 'Los cargás vos desde el panel, en la misma agenda: quedan igual de visibles que los que reservó el jugador solo.',
  },
  {
    q: '¿La plata pasa por ustedes?',
    a: 'No. La seña se cobra con tu propia cuenta de MercadoPago: el dinero va directo a vos.',
  },
  {
    q: '¿Y si no quiero cobrar seña?',
    a: 'Podés dejar que se reserve de palabra: queda confirmado al instante y no hay cobro online de por medio.',
  },
  {
    q: '¿Qué pasa si dos personas reservan lo mismo?',
    a: 'No puede pasar: la base de datos rechaza el segundo intento apenas el primero se confirma.',
  },
  {
    q: '¿El jugador tiene que registrarse?',
    a: 'No. Reserva con su nombre y teléfono, en dos campos, sin crear cuenta.',
  },
  {
    q: '¿Cómo es la prueba gratis?',
    a: `Cargamos tu club y probás el sistema real, con tus canchas y tarifas, ${TRIAL_DAYS} días sin pagar nada. Recién después decidís si seguís.`,
  },
  {
    q: '¿Cómo se paga la suscripción?',
    a: 'Por transferencia, un pago fijo mensual por club. Lo coordinamos directo por WhatsApp o mail.',
  },
];

/** Objeciones frecuentes, con <details> nativo: sin JS de por medio. */
export function FaqSection() {
  return (
    <section className="py-16 md:py-20">
      <SectionTitle title="Preguntas de siempre" />
      <div className="mt-8 space-y-3">
        {FAQS.map((faq) => (
          <details key={faq.q} className="group rounded-2xl border border-cal/10 bg-vidrio p-5">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-semibold marker:content-none">
              {faq.q}
              <span
                className="shrink-0 text-lg text-ink-soft transition group-open:rotate-45"
                aria-hidden
              >
                +
              </span>
            </summary>
            <p className="mt-3 text-sm leading-relaxed text-ink-soft">{faq.a}</p>
          </details>
        ))}
      </div>
    </section>
  );
}
