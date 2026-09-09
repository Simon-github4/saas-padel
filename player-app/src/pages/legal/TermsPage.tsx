import { LegalLayout, LegalList, LegalSection } from './LegalLayout';
import { BRAND, LEGAL_EMAIL } from '../marketing/config';

const UPDATED = '8 de septiembre de 2026';

/** Condiciones de uso para quien reserva una cancha por acá. */
export function TermsPage() {
  return (
    <LegalLayout
      title="Términos de uso"
      updated={UPDATED}
      otherHref="/privacidad"
      otherLabel="Ver la Política de privacidad"
    >
      <LegalSection heading="Qué es esto">
        <p>
          {BRAND} es una plataforma que conecta jugadores con clubes de pádel para reservar
          canchas online. Cada club fija sus propios horarios, precios y condiciones de
          cancelación: {BRAND} pone la herramienta, pero no es dueño de las canchas ni presta el
          servicio de juego.
        </p>
      </LegalSection>

      <LegalSection heading="Cómo reservás">
        <LegalList
          items={[
            'Elegís una cancha y un horario libre.',
            'Según lo que acepte el club, reservás de palabra (queda confirmado al instante, pagás en el club) o con una seña por MercadoPago.',
            'Si pagás una seña, el dinero va directo a la cuenta de MercadoPago del club: nosotros no la cobramos ni la retenemos.',
          ]}
        />
      </LegalSection>

      <LegalSection heading="Cancelaciones">
        <p>
          Cada club define su propia ventana de cancelación, y la ves antes de reservar o en el
          detalle de tu turno. Fuera de esa ventana, tenés que resolverlo directamente con el
          club.
        </p>
      </LegalSection>

      <LegalSection heading="Turno sin confirmar">
        <p>
          Si el club pide confirmación y no la das a tiempo, la cancha se libera sola y queda
          disponible para otro jugador.
        </p>
      </LegalSection>

      <LegalSection heading="Ausencias e historial">
        <p>
          Si no te presentás a un turno reservado, el club puede marcarlo así, y eso queda en tu
          historial visible para ese club — puede influir en cómo te trata a futuro (por ejemplo,
          pedirte pagar por adelantado). No es un puntaje público ni se comparte entre clubes.
        </p>
      </LegalSection>

      <LegalSection heading="Tu cuenta">
        <p>
          Si creás una cuenta, sos responsable de mantener tu contraseña segura. Si sospechás que
          alguien más la usa, avisanos.
        </p>
      </LegalSection>

      <LegalSection heading="Qué no garantiza esta plataforma">
        <p>
          La disponibilidad real de la cancha, el estado de las instalaciones y el trato que te dé
          el club son responsabilidad de cada club, no de {BRAND}. Nuestro rol termina en
          facilitar la reserva y, si corresponde, el cobro de la seña.
        </p>
      </LegalSection>

      <LegalSection heading="Uso indebido">
        <p>
          No reserves canchas sin intención real de usarlas, no cargues datos de otra persona sin
          su autorización, y no intentes vulnerar el sistema.
        </p>
      </LegalSection>

      <LegalSection heading="Cambios a estos términos">
        <p>
          Podemos actualizarlos con el tiempo. Si el cambio es importante, lo vas a ver reflejado
          acá con una nueva fecha arriba.
        </p>
      </LegalSection>

      <LegalSection heading="Ley aplicable">
        <p>
          Estos términos se rigen por las leyes de la República Argentina, incluyendo la Ley de
          Defensa del Consumidor (24.240) en lo que te corresponda como consumidor.
        </p>
      </LegalSection>

      <LegalSection heading="Contacto">
        <p>
          Cualquier duda, escribinos a{' '}
          <a href={`mailto:${LEGAL_EMAIL}`} className="text-ladrillo-claro underline-offset-4 hover:underline">
            {LEGAL_EMAIL}
          </a>
          .
        </p>
      </LegalSection>
    </LegalLayout>
  );
}
