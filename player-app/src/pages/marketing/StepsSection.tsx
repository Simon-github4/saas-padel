import { TRIAL_DAYS } from './config';
import { CONTAINER, SectionHeading, delay, useInView } from './motion';

const STEPS = [
  {
    title: 'Charlamos y cargamos tu club',
    text: 'Canchas, horarios y tarifas, listos en el sistema.',
  },
  {
    title: `Probás tu link ${TRIAL_DAYS} días`,
    text: 'Gratis y con tus canchas reales. Vos decidís cuándo pasar a compartirlo.',
  },
  {
    title: 'Lo compartís por WhatsApp e Instagram',
    text: 'El link es tuyo, para difundirlo como ya lo hacés.',
  },
];

/**
 * Sin promesas de plazo: los tres pasos, sin fecha porque no la controlamos.
 * Acá la numeración sí dice algo: es un orden real. La línea que los une se
 * completa al entrar en pantalla.
 */
export function StepsSection() {
  const [ref, shown] = useInView<HTMLOListElement>();

  return (
    <section id="empezar" className="scroll-mt-32 lg:scroll-mt-16 border-y border-cal/10 bg-vidrio/40 py-24 md:py-32">
      <div className={CONTAINER}>
        <SectionHeading eyebrow="Cómo empezamos" title="Sin vueltas, en tres pasos" />

        <ol ref={ref} data-shown={shown || undefined} className="relative mt-16 grid gap-12 md:mt-20 md:grid-cols-3 md:gap-10">
          {/* Riel de fondo y su tramo encendido: vertical en el teléfono, horizontal desde md. */}
          <span aria-hidden className="absolute bottom-6 left-6 top-6 w-px bg-cal/10 md:hidden" />
          <span aria-hidden className="mk-grow-y-line absolute bottom-6 left-6 top-6 w-px bg-ladrillo md:hidden" />
          <span aria-hidden className="absolute left-6 right-6 top-6 hidden h-px bg-cal/10 md:block" />
          <span aria-hidden className="mk-grow-x absolute left-6 right-6 top-6 hidden h-px bg-ladrillo md:block" />

          {STEPS.map((step, index) => (
            <li
              key={step.title}
              className="relative grid grid-cols-[3rem_1fr] gap-5 md:block"
            >
              <span
                className={`display relative grid size-12 place-items-center rounded-full border text-2xl transition-colors duration-700 ${
                  shown ? 'border-ladrillo bg-pista text-cal' : 'border-cal/15 bg-pista text-ink-mute'
                }`}
                style={{ transitionDelay: `${400 + index * 450}ms` }}
              >
                {index + 1}
              </span>
              <div className={shown ? 'mk-fade-up' : 'opacity-0'} style={delay(400 + index * 450)}>
                <h3 className="text-2xl tracking-[0.05em] md:mt-8 md:text-[1.75rem]">{step.title}</h3>
                <p className="mt-3 max-w-xs text-sm leading-relaxed text-ink-soft">{step.text}</p>
              </div>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
