import { TRIAL_DAYS, salesWhatsappHref } from './config';
import { ArrowGlyph, CONTAINER, Reveal, SectionHeading } from './motion';

const FAQS = [
  {
    q: '¿Tengo que confirmar cada reserva a mano?',
    a: 'No. Si cobrás seña, el turno se confirma solo cuando Mercado Pago acredita el pago; si dejás reservar de palabra, queda confirmado al instante. En los dos casos aparece en tu agenda sin que toques nada.',
  },
  {
    q: '¿Tengo que cambiar cómo trabajo?',
    a: 'No. Seguís atendiendo por WhatsApp y en el mostrador como siempre; el sistema suma un canal más, no reemplaza el que ya usás.',
  },
  {
    q: '¿Y los turnos que entran por teléfono?',
    a: 'Los cargás vos desde el panel, en la misma agenda: quedan igual de visibles que los que reservó el jugador solo, y esa cancha deja de aparecer libre en tu link.',
  },
  {
    q: '¿La plata pasa por ustedes?',
    a: 'No. La seña se cobra con tu propia cuenta de Mercado Pago: el dinero va directo a vos y no te cobramos comisión por reserva. Mercado Pago sí descuenta la suya por cobrar online, como en cualquier venta.',
  },
  {
    q: '¿Cómo conecto mi Mercado Pago?',
    a: 'Desde el panel, con un botón: entrás con tu usuario de Mercado Pago y autorizás la conexión. No hay claves que copiar, y podés desconectarla cuando quieras.',
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
    a: `Cargamos tu club y probás el sistema real, con tus canchas y tarifas, ${TRIAL_DAYS} días sin pagar nada. No te pedimos tarjeta ni datos de pago: si no seguís, no hacés nada y no se te cobra.`,
  },
  {
    q: '¿Cómo se paga la suscripción?',
    a: 'Por transferencia, un pago fijo mensual por club. Lo coordinamos directo por WhatsApp o mail.',
  },
];

/**
 * Objeciones frecuentes, con <details> nativo: sin JS de por medio. La
 * apertura animada sale de landing.css y sólo la tienen los navegadores que
 * soportan ::details-content; el resto la abre de golpe, que también sirve.
 */
export function FaqSection() {
  return (
    <section className="py-24 md:py-36">
      <div className={`${CONTAINER} grid gap-12 lg:grid-cols-[0.8fr_1.2fr] lg:gap-20`}>
        <div className="lg:sticky lg:top-28 lg:self-start">
          <SectionHeading
            eyebrow="Preguntas"
            title="Las de siempre"
            lead="Lo que más nos preguntan los dueños antes de arrancar."
          />
          <Reveal delay={150}>
            <a
              href={salesWhatsappHref()}
              target="_blank"
              rel="noreferrer"
              className="group mt-8 inline-flex items-center gap-2 text-sm font-semibold text-ladrillo-claro"
            >
              <span className="underline-offset-4 group-hover:underline">¿Otra duda? Preguntanos</span>
              <ArrowGlyph className="size-4 transition-transform duration-300 group-hover:translate-x-1" />
            </a>
          </Reveal>
        </div>

        <Reveal delay={100}>
          <div className="border-t border-cal/10">
            {FAQS.map((faq) => (
              <details key={faq.q} className="mk-faq group border-b border-cal/10">
                <summary className="flex cursor-pointer list-none items-center justify-between gap-6 py-6 text-base font-semibold transition-colors hover:text-ladrillo-claro md:text-lg [&::-webkit-details-marker]:hidden">
                  {faq.q}
                  <span
                    aria-hidden
                    className="relative grid size-8 shrink-0 place-items-center rounded-full border border-cal/15 transition duration-300 group-open:rotate-45 group-open:border-ladrillo group-open:bg-ladrillo"
                  >
                    <span className="absolute h-px w-3 bg-current" />
                    <span className="absolute h-3 w-px bg-current" />
                  </span>
                </summary>
                <p className="max-w-xl pb-6 pr-12 leading-relaxed text-ink-soft">{faq.a}</p>
              </details>
            ))}
          </div>
        </Reveal>
      </div>
    </section>
  );
}
