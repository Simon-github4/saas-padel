import { useEffect } from 'react';
import { setPageMeta } from '../seo';
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
import { TemaSwitch } from '../components/TemaSwitch';
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
  // Mismos textos que SeoPageRenderer.landingMeta() en el backend, que es lo
  // que ven los buscadores y las vistas previas de link antes de que cargue React.
  useEffect(
    () =>
      setPageMeta(
        `Reservas online para tu club — ${BRAND}`,
        'Sistema de reservas online para clubes de pádel: tus jugadores reservan y pagan la seña solos, con Mercado Pago, y vos manejás la agenda desde un panel. Con 7 días de prueba.',
      ),
    [],
  );

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
