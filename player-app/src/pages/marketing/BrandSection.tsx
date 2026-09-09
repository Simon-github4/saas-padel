import { SectionTitle } from '../../components/Ui';

const TRAITS = ['Tu foto de portada', 'Tus colores', 'Tema claro u oscuro'];

/** El link es del club, no del sistema: nada de esto se ve genérico. */
export function BrandSection() {
  return (
    <section className="py-16 md:py-20">
      <SectionTitle title="Tu marca, tu link" subtitle="El jugador entra a un club, no a un genérico." />
      <div className="mt-6 grid gap-4 sm:grid-cols-3">
        {TRAITS.map((trait) => (
          <div
            key={trait}
            className="rounded-2xl border border-cal/10 bg-vidrio p-5 text-center"
          >
            <p className="display text-base tracking-[0.06em]">{trait}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
