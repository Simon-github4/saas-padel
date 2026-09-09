import { Link } from 'react-router-dom';
import { salesWhatsappHref } from './config';
import { PhoneMockup } from './mockups/PhoneMockup';

/**
 * Portada de la landing comercial: lo primero que ve un dueño de club.
 *
 * <p>El titular no vende "un sistema": vende dejar de resolver la reserva a
 * mano, que es el dolor real. El mockup de al lado muestra de entrada cómo se
 * ve, en vez de pedirle al lector que se lo imagine.
 */
export function HeroSection() {
  return (
    <section
      id="top"
      className="relative mx-[calc(50%-50vw)] overflow-hidden pb-16 pt-14 md:pb-24 md:pt-20"
    >
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(60%_55%_at_50%_0%,rgba(234,88,12,0.18),transparent_70%)]"
      />
      <div className="relative mx-auto grid w-full max-w-5xl gap-12 px-4 md:grid-cols-[1.1fr_0.9fr] md:items-center md:gap-8">
        <div className="text-center md:text-left">
          <p className="eyebrow text-ladrillo-claro">Para dueños de club</p>
          <h1 className="mt-4 text-[clamp(2.75rem,9vw,4.5rem)] leading-[0.95] tracking-[0.02em]">
            Que tu club reserve solo
          </h1>
          <p className="mx-auto mt-6 max-w-md text-lg text-ink-soft md:mx-0">
            Tu link de WhatsApp deja de ser una fila de mensajes a la noche: el
            jugador ve las canchas libres, elige un horario y reserva. Vos mirás la
            agenda.
          </p>
          <div className="mt-8 flex flex-col items-center gap-3 md:items-start">
            <a
              href={salesWhatsappHref()}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-2 rounded-full bg-ladrillo px-10 py-4 text-sm font-bold uppercase tracking-[0.12em] text-cal transition hover:bg-ladrillo/90 [box-shadow:var(--shadow-glow)]"
            >
              Hablemos por WhatsApp
              <span aria-hidden>→</span>
            </a>
            {/* Mismo destino que la estampa sobre el mockup, pero como texto: en
                mobile la estampa puede pasar desapercibida, y quien todavía no
                se decide a escribir necesita poder tocar algo ya. */}
            <Link
              to="/club/club-necochea"
              target="_blank"
              className="text-sm font-semibold text-ink-soft underline-offset-4 transition hover:text-cal hover:underline"
            >
              Ver un club real funcionando →
            </Link>
          </div>
        </div>
        <div className="relative">
          <PhoneMockup />
          {/* El único link a un club real de toda la landing: mientras el
              resto vende con mockups, acá el que quiere ver el sistema andando
              de verdad puede. Va como estampa sobre la esquina de la ficha, no
              como pie de foto, para que no se lea como una aclaración legal
              sino como una invitación. Nueva pestaña para no perder la landing. */}
          <Link
            to="/club/club-necochea"
            target="_blank"
            className="absolute -top-3 right-3 inline-flex -rotate-3 items-center gap-1.5 rounded-full bg-ladrillo px-4 py-2 text-xs font-bold uppercase tracking-[0.1em] text-cal transition [box-shadow:var(--shadow-glow)] hover:-rotate-1 hover:bg-ladrillo/90"
          >
            Club real
            <span aria-hidden>→</span>
          </Link>
        </div>
      </div>
    </section>
  );
}
