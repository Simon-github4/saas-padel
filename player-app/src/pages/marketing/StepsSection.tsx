import { SectionTitle } from '../../components/Ui';

const STEPS = [
  {
    title: 'Charlamos y cargamos tu club',
    text: 'Canchas, horarios y tarifas, listos en el sistema.',
  },
  {
    title: 'Probás tu link una semana',
    text: 'Vos decidís cuándo pasar a compartirlo.',
  },
  {
    title: 'Lo compartís por WhatsApp e Instagram',
    text: 'El link es tuyo, para difundirlo como ya lo hacés.',
  },
];

/** Sin promesas de plazo: los tres pasos, sin fecha porque no la controlamos. */
export function StepsSection() {
  return (
    <section id="empezar" className="scroll-mt-20 py-16 md:py-20">
      <SectionTitle title="Cómo empezamos" subtitle="Sin vueltas, en tres pasos." />
      <ol className="mt-8 grid gap-6 md:grid-cols-3">
        {STEPS.map((step, index) => (
          <li key={step.title} className="rounded-2xl border border-cal/10 bg-vidrio p-5">
            <span className="display grid size-9 place-items-center rounded-full bg-cal text-base text-pista">
              {index + 1}
            </span>
            <p className="display mt-4 text-lg tracking-[0.06em]">{step.title}</p>
            <p className="mt-2 text-sm leading-relaxed text-ink-soft">{step.text}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}
