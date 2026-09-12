import { SectionTitle } from '../../components/Ui';

const FEATURES = [
  {
    title: 'Imposible sobrevender',
    text: 'Dos personas no pueden tomar la misma cancha al mismo horario: lo impide la base de datos, no una validación que se pueda pasar por alto.',
  },
  {
    title: 'Seña o de palabra',
    text: 'Cobrás una seña por MercadoPago a tu propia cuenta, o dejás reservar de palabra con confirmación al instante.',
  },
  {
    title: 'El olvido se cae solo',
    text: 'El turno sin confirmar se libera solo a los minutos, y la cancha vuelve a aparecer disponible.',
  },
  {
    title: 'Turnos fijos',
    text: 'Los turnos de siempre se generan solos, semana tras semana, sin que nadie tenga que volver a cargarlos.',
  },
  {
    title: 'Precios a medida',
    text: 'Tarifas distintas por franja horaria y por cancha, con promociones cuando quieras llenar un hueco.',
  },
  {
    title: 'Tu link, tu marca',
    text: 'Compartís un solo link por WhatsApp o Instagram: tu nombre, tu foto, tus colores.',
  },
];

/** Qué resuelve el sistema, en tarjetas cortas y sin jerga. */
export function FeaturesSection() {
  return (
    <section id="funciones" className="scroll-mt-20 py-16 md:py-20">
      <SectionTitle
        title="Qué hace por vos"
        subtitle="Lo que hoy resolvés a mano, resuelto solo."
      />
      <div className="mt-8 grid gap-4 sm:grid-cols-2 md:grid-cols-3">
        {FEATURES.map((feature) => (
          <div key={feature.title} className="rounded-2xl border border-cal/10 bg-vidrio p-5">
            <p className="display text-lg tracking-[0.06em] text-ladrillo-claro">
              {feature.title}
            </p>
            <p className="mt-2 text-sm leading-relaxed text-ink-soft">{feature.text}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
