import { gymWhatsappHref } from './config';
import { WhatsappCta } from './Cta';
import { CONTAINER, CheckGlyph, Reveal, SectionHeading } from './motion';

const POINTS = [
  {
    title: 'Socios y cuotas',
    text: 'Cuota mensual con los días por semana que pagó cada uno, y de un vistazo quién está al día y quién la tiene vencida.',
  },
  {
    title: 'Ingreso con QR',
    text: 'El socio escanea el QR de la entrada con su celular y entra con su DNI. El sistema controla que la cuota esté vigente.',
  },
  {
    title: 'Una o varias sedes',
    text: 'Una misma cuota puede valer en más de una sede, cada una con su propio QR.',
  },
  {
    title: 'En el mismo panel',
    text: 'No es otro sistema ni otro usuario: el gimnasio aparece al lado de tus canchas.',
  },
];

const WEEK = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];
/** Lunes y miércoles ya fue; hoy, jueves, acaba de entrar. */
const ATTENDED = [0, 2, 3];

/**
 * El módulo de gimnasio, al final y en corto: le habla al club que además de
 * canchas tiene gimnasio, y deja claro que se contrata aparte del plan de pádel.
 * El precio no va acá: se arma con cada club.
 */
export function GymSection() {
  return (
    <section id="gimnasio" className="scroll-mt-32 lg:scroll-mt-16 border-t border-cal/10 py-24 md:py-32">
      <div className={`${CONTAINER} grid items-center gap-14 lg:grid-cols-[1.2fr_1fr] lg:gap-20`}>
        <div>
          <SectionHeading
            eyebrow="Módulo adicional"
            title="¿Tu club también tiene gimnasio?"
            lead="Sumá el módulo de gimnasio al de pádel y manejá los socios desde el mismo panel."
          />

          <ul className="mt-10 grid gap-6 sm:grid-cols-2">
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

          <Reveal delay={500}>
            <div className="mt-10 flex flex-col items-start gap-4 sm:flex-row sm:items-center">
              <WhatsappCta href={gymWhatsappHref()} size="md">
                Consultar por el gimnasio
              </WhatsappCta>
              <p className="max-w-xs text-sm text-ink-soft">
                Se contrata aparte, como módulo del plan de pádel.
              </p>
            </div>
          </Reveal>
        </div>

        <Reveal delay={150}>
          <CheckinCard />
        </Reveal>
      </div>
    </section>
  );
}

/** Lo que ve el socio en su celular al escanear el QR de la entrada. */
function CheckinCard() {
  return (
    <div
      aria-hidden
      className="mx-auto max-w-sm rounded-[1.75rem] border border-cal/10 bg-vidrio p-6 [box-shadow:var(--shadow-card)] sm:p-7"
    >
      <p className="eyebrow text-ink-mute">Lo que ve el socio</p>
      <div className="mt-5 flex items-center gap-4 rounded-2xl border border-ladrillo/40 bg-ladrillo/[0.08] p-4">
        <span className="grid size-11 shrink-0 place-items-center rounded-full bg-ladrillo text-cal">
          <CheckGlyph className="size-5" />
        </span>
        <div>
          <p className="font-semibold">Ingreso registrado</p>
          <p className="text-xs text-ink-soft">Sede Centro · 18:42</p>
        </div>
      </div>

      <dl className="mt-5 space-y-2.5 text-sm">
        <div className="flex items-baseline justify-between gap-4">
          <dt className="text-ink-soft">Cuota</dt>
          <dd className="font-semibold text-ladrillo-claro">Al día hasta el 15/10</dd>
        </div>
        <div className="flex items-baseline justify-between gap-4">
          <dt className="text-ink-soft">Esta semana</dt>
          <dd className="font-semibold tabular-nums">3 de 3 días</dd>
        </div>
      </dl>

      <ul className="mt-5 grid grid-cols-7 gap-1.5">
        {WEEK.map((day, index) => (
          <li
            key={index}
            className={`grid h-9 place-items-center rounded-lg text-xs font-semibold ${
              ATTENDED.includes(index) ? 'bg-ladrillo/20 text-ladrillo-claro' : 'bg-cal/[0.05] text-ink-mute'
            }`}
          >
            {day}
          </li>
        ))}
      </ul>
    </div>
  );
}
