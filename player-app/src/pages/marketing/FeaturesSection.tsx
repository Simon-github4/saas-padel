import { useState, type ReactNode } from 'react';
import { WhatsappGlyph } from '../../components/Ui';
import {
  BrandVisual,
  DepositVisual,
  ExpireVisual,
  FixedVisual,
  OverbookVisual,
  PricesVisual,
} from './FeatureVisuals';
import { CONTAINER, Reveal, SectionHeading } from './motion';

/**
 * Qué resuelve el sistema, en tarjetas cortas y sin jerga, cada una con la
 * función andando en chico. Las anchas son las que necesitan lugar para
 * mostrarse; el orden alterna anchas y angostas para que la grilla respire.
 */
export function FeaturesSection() {
  const [overbookReplay, setOverbookReplay] = useState(0);

  return (
    <section id="funciones" className="scroll-mt-32 lg:scroll-mt-16 py-24 md:py-36">
      <div className={CONTAINER}>
        <SectionHeading
          eyebrow="Qué hace por vos"
          title="Lo que hoy resolvés a mano, resuelto solo"
          lead="Seis cosas que dejan de depender de que alguien esté mirando el teléfono."
        />

        <div className="mt-16 grid gap-4 md:mt-20 md:grid-cols-2 lg:grid-cols-3 lg:gap-5">
          <Feature
            wide
            title="Imposible sobrevender"
            text="Dos personas no pueden tomar la misma cancha al mismo horario: lo impide la base de datos, no una validación que se pueda pasar por alto."
            onEnter={() => setOverbookReplay((value) => value + 1)}
          >
            <OverbookVisual replay={overbookReplay} />
          </Feature>
          <Feature
            delay={100}
            title="Seña o de palabra"
            text="Cobrás una seña por MercadoPago a tu propia cuenta, o dejás reservar de palabra con confirmación al instante."
          >
            <DepositVisual />
          </Feature>
          <Feature
            title="El olvido se cae solo"
            text="El turno sin confirmar se libera solo a los minutos, y la cancha vuelve a aparecer disponible."
            note="Solo en la versión con WhatsApp incluido"
          >
            <ExpireVisual />
          </Feature>
          <Feature
            wide
            delay={100}
            title="Precios a medida"
            text="Tarifas distintas por franja horaria y por cancha, con promociones cuando quieras llenar un hueco."
          >
            <PricesVisual />
          </Feature>
          <Feature
            wide
            title="Tu link, tu marca"
            text="Compartís un solo link por WhatsApp o Instagram, con tu foto de portada, tus colores y tema claro u oscuro. El jugador entra a tu club, no a un genérico."
          >
            <BrandVisual />
          </Feature>
          <Feature
            delay={100}
            className="md:col-span-2 lg:col-span-1"
            title="Turnos fijos"
            text="Los turnos de siempre se generan solos, semana tras semana, sin que nadie tenga que volver a cargarlos."
          >
            <FixedVisual />
          </Feature>
        </div>
      </div>
    </section>
  );
}

function Feature({
  title,
  text,
  children,
  wide = false,
  delay = 0,
  className = '',
  note,
  onEnter,
}: {
  title: string;
  text: string;
  children: ReactNode;
  wide?: boolean;
  delay?: number;
  /** Para la última angosta, que en dos columnas quedaría sola con un hueco al lado. */
  className?: string;
  /** Aclaración de alcance: la función no viene en todas las versiones. */
  note?: string;
  onEnter?: () => void;
}) {
  return (
    <Reveal delay={delay} className={wide ? 'md:col-span-2' : className}>
      <article
        onMouseEnter={onEnter}
        className="group flex h-full flex-col rounded-[1.75rem] border border-cal/10 bg-vidrio p-6 transition-colors duration-500 hover:border-cal/20 md:p-8"
      >
        <div className="min-h-44 flex-1">{children}</div>
        <h3 className="mt-8 text-2xl tracking-[0.05em] md:text-[1.75rem]">{title}</h3>
        <p className="mt-3 max-w-lg text-sm leading-relaxed text-ink-soft">{text}</p>
        {note && (
          <p className="mt-4 inline-flex items-center gap-2 self-start rounded-full border border-cal/10 bg-pista px-3 py-1.5 text-xs text-ink-soft">
            <WhatsappGlyph className="size-3.5 shrink-0" />
            {note}
          </p>
        )}
      </article>
    </Reveal>
  );
}
