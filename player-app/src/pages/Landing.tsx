import { useEffect } from 'react';
import { BrandSection } from './marketing/BrandSection';
import { ClosingSection } from './marketing/ClosingSection';
import { BRAND } from './marketing/config';
import { FaqSection } from './marketing/FaqSection';
import { FeaturesSection } from './marketing/FeaturesSection';
import { HeroSection } from './marketing/HeroSection';
import { MarketingFooter, MarketingNav } from './marketing/MarketingNav';
import { NetworkSection } from './marketing/NetworkSection';
import { PanelSection } from './marketing/PanelSection';
import { PricingSection } from './marketing/PricingSection';
import { StepsSection } from './marketing/StepsSection';

/**
 * Landing comercial: vende el sistema al dueño de club que entra a la raíz.
 *
 * <p>El jugador no pasa por acá: cada club reparte su propio link de
 * /club/{slug} por WhatsApp. Quien sí cae en "/" es, sobre todo, el dueño de
 * club evaluando el producto — por eso esta página tiene su propio título de
 * pestaña, distinto del de la SPA, y lo restaura al salir.
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
    <div className="min-h-dvh overflow-x-clip bg-pista">
      <MarketingNav />
      <HeroSection />
      <div className="mx-auto w-full max-w-5xl px-4">
        <FeaturesSection />
        <PanelSection />
        <BrandSection />
        <NetworkSection />
        <StepsSection />
        <PricingSection />
        <FaqSection />
      </div>
      <ClosingSection />
      <MarketingFooter />
    </div>
  );
}
