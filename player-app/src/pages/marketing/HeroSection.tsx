import { useEffect, useState } from 'react';
import { TRIAL_DAYS, salesWhatsappHref } from './config';
import { DemoClubCta, WhatsappCta } from './Cta';
import { MercadoPagoLogo } from './MercadoPagoLogo';
import { CONTAINER, CheckGlyph, CourtLines, delay } from './motion';
import { LiveAgenda } from './mockups/LiveAgenda';

/** La del medio termina en el logo de Mercado Pago: es la que el club busca con el ojo. */
const PROMISES = [
  { text: `${TRIAL_DAYS} días gratis` },
  { text: 'La seña va a tu', mercadoPago: true },
  { text: 'El jugador no crea cuenta' },
];

/**
 * Portada de la landing comercial: lo primero que ve un dueño de club.
 *
 * <p>El titular no vende "un sistema": vende dejar de resolver la reserva a
 * mano, que es el dolor real. Al lado, la agenda se llena sola mientras se lee
 * el titular: la promesa funcionando, en vez de pedir que se la imaginen.
 * Abajo, el piso es una cancha que se dibuja al cargar.
 */
export function HeroSection() {
  // Las líneas de la cancha arrancan a dibujarse recién montada la portada.
  const [drawn, setDrawn] = useState(false);
  useEffect(() => {
    const frame = requestAnimationFrame(() => setDrawn(true));
    return () => cancelAnimationFrame(frame);
  }, []);

  return (
    // Margen negativo y padding del mismo alto que la barra: la portada sube por
    // detrás de ella, que arriba de todo es transparente, así el resplandor y la
    // cancha llegan hasta el borde. Debajo de lg la barra suma la fila de
    // "Buscar turno" y "Soy club", y es más alta.
    <section
      id="top"
      className="relative isolate -mt-[7.625rem] overflow-hidden pt-[7.625rem] lg:-mt-16 lg:pt-16"
    >
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(55%_50%_at_70%_20%,rgba(234,88,12,0.16),transparent_70%),radial-gradient(40%_40%_at_10%_0%,rgba(255,255,255,0.05),transparent_70%)]"
      />
      {/* La cancha como piso, en perspectiva: la portada "está parada" sobre ella. */}
      <div
        aria-hidden
        data-shown={drawn || undefined}
        className="pointer-events-none absolute inset-x-0 bottom-0 -z-10 h-[70%] [mask-image:linear-gradient(to_bottom,transparent,black_35%,black_70%,transparent)] [perspective:900px]"
      >
        <div className="absolute left-1/2 top-[38%] w-[190%] -translate-x-1/2 origin-top [transform:rotateX(64deg)] md:w-[140%] lg:left-[72%] lg:w-[110%]">
          <CourtLines className="w-full text-cal/[0.1]" />
        </div>
      </div>

      <div
        className={`${CONTAINER} grid gap-16 pb-28 pt-14 md:pt-20 lg:min-h-[calc(100dvh-4rem)] lg:grid-cols-[1.1fr_1fr] lg:items-center lg:gap-14 lg:pb-24`}
      >
        <div className="text-center lg:text-left">
          <p
            className="eyebrow mk-fade-up flex items-center justify-center gap-3 text-ladrillo-claro lg:justify-start"
            style={delay(0)}
          >
            <span aria-hidden className="h-px w-6 bg-ladrillo-claro/60" />
            Para dueños de club
          </p>
          <h1 className="mt-6 text-[clamp(3.75rem,15vw,7rem)] leading-[0.9] tracking-[0.01em] lg:text-[clamp(4.5rem,6.8vw,7.25rem)]">
            <span className="mk-line">
              <span style={delay(100)}>Que tu club</span>
            </span>
            <span className="mk-line">
              <span style={delay(220)}>
                reserve <span className="text-ladrillo">solo</span>
              </span>
            </span>
          </h1>
          <p
            className="mk-fade-up mx-auto mt-7 max-w-md text-lg leading-relaxed text-ink-soft text-pretty lg:mx-0"
            style={delay(420)}
          >
            Tu WhatsApp deja de ser una fila de mensajes a la noche: el jugador abre
            tu link, ve las canchas libres y reserva. Vos mirás la agenda.
          </p>
          <div
            className="mk-fade-up mt-9 flex flex-col items-stretch gap-3 sm:flex-row sm:flex-wrap sm:justify-center lg:justify-start"
            style={delay(560)}
          >
            <WhatsappCta href={salesWhatsappHref()}>Hablemos</WhatsappCta>
            <DemoClubCta>Ver un club real</DemoClubCta>
          </div>
          <ul
            className="mk-fade-up mt-9 flex flex-wrap justify-center gap-x-6 gap-y-2 text-sm text-ink-soft lg:justify-start"
            style={delay(700)}
          >
            {PROMISES.map((promise) => (
              <li key={promise.text} className="flex items-center gap-2">
                <CheckGlyph className="size-3.5 text-ladrillo-claro" />
                {promise.text}
                {promise.mercadoPago && <MercadoPagoLogo className="h-6 w-auto" />}
              </li>
            ))}
          </ul>
        </div>

        <div className="mk-fade-up" style={delay(500)}>
          <LiveAgenda />
        </div>
      </div>
    </section>
  );
}
