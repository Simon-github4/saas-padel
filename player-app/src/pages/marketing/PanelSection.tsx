import { SectionTitle } from '../../components/Ui';
import { PanelMockup } from './mockups/PanelMockup';

const POINTS = [
  'La agenda del día, con el turno que entró por teléfono cargado a mano.',
  'Cobrás en el mostrador y marcás el ausente sin salir de la agenda.',
  'Los jugadores quedan con su historial y una marca de confianza.',
  'Alertas de lo que necesita tu atención, sin tener que revisar todo.',
];

/** El otro lado del sistema: lo que pasa fuera de internet, en el panel. */
export function PanelSection() {
  return (
    <section id="panel" className="scroll-mt-20 py-16 md:py-20">
      <div className="grid gap-10 md:grid-cols-2 md:items-center md:gap-12">
        <div>
          <SectionTitle
            title="Vos manejás la cancha, no la planilla"
            subtitle="El panel del club, para lo que pasa fuera de internet."
          />
          <ul className="mt-6 space-y-3">
            {POINTS.map((point) => (
              <li key={point} className="flex gap-3 text-sm text-ink-soft">
                <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-ladrillo-claro" aria-hidden />
                {point}
              </li>
            ))}
          </ul>
        </div>
        <PanelMockup />
      </div>
    </section>
  );
}
