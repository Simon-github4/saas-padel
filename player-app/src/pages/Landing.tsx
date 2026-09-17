import { useEffect } from 'react';
import { ClosingSection } from './marketing/ClosingSection';
import { BRAND } from './marketing/config';
import { DemoSection } from './marketing/DemoSection';
import { FaqSection } from './marketing/FaqSection';
import { FeaturesSection } from './marketing/FeaturesSection';
import { HeroSection } from './marketing/HeroSection';
import { MarketingFooter, MarketingNav } from './marketing/MarketingNav';
import { MessagesMarquee } from './marketing/MessagesMarquee';
import { NetworkSection } from './marketing/NetworkSection';
import { PaymentsSection } from './marketing/PaymentsSection';
import { PricingSection } from './marketing/PricingSection';
import { StepsSection } from './marketing/StepsSection';
import { TemaSwitch } from './marketing/TemaSwitch';
import './marketing/landing.css';

/**
 * Landing comercial: vende el sistema al dueño de club que entra a la raíz.
 *
 * <p>El jugador no pasa por acá: cada club reparte su propio link de
 * /club/{slug} por WhatsApp. Quien sí cae en "/" es, sobre todo, el dueño de
 * club evaluando el producto — por eso esta página tiene su propio título de
 * pestaña, distinto del de la SPA, y lo restaura al salir.
 *
 * <p>El orden cuenta una historia: la promesa andando (portada), el dolor de
 * hoy (los mensajes), probarlo con las propias manos (demo), el cobro de la seña, el detalle
 * (funciones), cómo se empieza, cuánto sale y las dudas.
 */
export function Landing() {
  useEffect(() => {
    const previous = document.title;
    document.title = `Reservas online para tu club — ${BRAND}`;
    return () => {
      document.title = previous;
    };
  }, []);

  return (
    <div className="mk min-h-dvh overflow-x-clip bg-pista">
      <MarketingNav />
      <main>
        <HeroSection />
        <MessagesMarquee />
        <DemoSection />
        <PaymentsSection />
        <FeaturesSection />
        <NetworkSection />
        <StepsSection />
        <PricingSection />
        <FaqSection />
        <ClosingSection />
      </main>
      <MarketingFooter />
      <TemaSwitch />
    </div>
  );
}
