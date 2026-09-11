import { LegalLayout, LegalList, LegalSection } from './LegalLayout';
import { BRAND, LEGAL_EMAIL } from '../marketing/config';

const UPDATED = '8 de septiembre de 2026';

/**
 * Qué datos junta la plataforma y para qué, en criollo. Cubre tanto al
 * jugador (con o sin cuenta) como al personal del club que usa el panel,
 * porque ambos pasan por el mismo sistema.
 */
export function PrivacyPage() {
  return (
    <LegalLayout
      title="Política de privacidad"
      updated={UPDATED}
      otherHref="/terminos"
      otherLabel="Ver los Términos de uso"
    >
      <LegalSection heading="Quién es el responsable">
        <p>
          {BRAND} lo opera Simón Díaz, monotributista. Para cualquier consulta sobre tus datos
          personales, escribí a{' '}
          <a href={`mailto:${LEGAL_EMAIL}`} className="text-ladrillo-claro underline-offset-4 hover:underline">
            {LEGAL_EMAIL}
          </a>
          .
        </p>
      </LegalSection>

      <LegalSection heading="A quién aplica">
        <p>
          A cualquiera que use {BRAND}: el jugador que reserva una cancha (con o sin cuenta) y el
          personal del club (dueño o mostrador) que usa el panel de administración.
        </p>
      </LegalSection>

      <LegalSection heading="Qué datos juntamos">
        <LegalList
          items={[
            <>
              <strong>Si reservás sin crear cuenta:</strong> tu nombre y tu teléfono, para que el
              club te identifique y pueda contactarte por esa reserva.
            </>,
            <>
              <strong>Si creás una cuenta:</strong> tu email y una contraseña (la guardamos
              cifrada, nunca la vemos en texto plano), o tu cuenta de Google si entrás con
              "Continuar con Google" — en ese caso no vemos tu contraseña de Google, solo
              confirmamos quién sos. También tu nombre y teléfono, si los cargás.
            </>,
            <>
              <strong>Tu historial de reservas:</strong> qué cancha, cuándo, en qué club, si
              pagaste una seña y si te presentaste o cancelaste. Esto último queda como
              referencia para el club donde jugaste esa reserva.
            </>,
            <>
              <strong>Si el club usa el panel:</strong> el dueño o el mostrador de ese club pueden
              ver tus datos de contacto y tus reservas ahí — lo necesitan para atenderte.
            </>,
          ]}
        />
      </LegalSection>

      <LegalSection heading="Qué no guardamos">
        <p>
          Nunca vemos ni guardamos el número de tu tarjeta. Si pagás una seña, te llevamos al
          checkout de MercadoPago y el pago se acredita directo en la cuenta del club: nosotros
          solo recibimos la confirmación de que se pagó (monto y estado), nada más.
        </p>
      </LegalSection>

      <LegalSection heading="Para qué usamos tus datos">
        <LegalList
          items={[
            'Gestionar tu reserva y que el club pueda contactarte si hace falta.',
            'Mandarte los emails de tu cuenta (verificación, recuperar contraseña).',
            'Mostrarte tu historial de turnos si tenés cuenta.',
            'Mostrarle al club tu historial de reservas en ese club, como referencia.',
          ]}
        />
        <p>Hoy no mandamos WhatsApp de forma automática: si un club te escribe por ahí, lo hace directo con su propio teléfono, no a través de un envío nuestro.</p>
      </LegalSection>

      <LegalSection heading="Con quién compartimos datos">
        <LegalList
          items={[
            'Con el club donde reservás: es indispensable, es quien te atiende.',
            'Con MercadoPago, si elegís pagar una seña online (ellos procesan el pago).',
            'Con Google, si elegís entrar con tu cuenta de Google.',
          ]}
        />
        <p>No vendemos tus datos ni los compartimos con nadie más, ni los usamos para publicidad de terceros.</p>
      </LegalSection>

      <LegalSection heading="Cuánto tiempo los guardamos">
        <p>
          Mientras tu cuenta esté activa y mientras el club conserve el registro de sus reservas.
          Si querés que borremos tus datos, escribinos a{' '}
          <a href={`mailto:${LEGAL_EMAIL}`} className="text-ladrillo-claro underline-offset-4 hover:underline">
            {LEGAL_EMAIL}
          </a>{' '}
          — hoy es un pedido que resolvemos a mano, no hay todavía un botón de autoborrado en la
          cuenta.
        </p>
      </LegalSection>

      <LegalSection heading="Tus derechos">
        <p>
          Por la Ley 25.326 de Protección de Datos Personales, tenés derecho a acceder,
          rectificar, actualizar y pedir la supresión de tus datos. Podés ejercerlos escribiendo a{' '}
          <a href={`mailto:${LEGAL_EMAIL}`} className="text-ladrillo-claro underline-offset-4 hover:underline">
            {LEGAL_EMAIL}
          </a>
          . La Agencia de Acceso a la Información Pública (AAIP), autoridad de control de esa ley,
          también recibe reclamos.
        </p>
      </LegalSection>

      <LegalSection heading="Cookies y almacenamiento local">
        <p>
          Guardamos un token de sesión en tu navegador (localStorage) para mantenerte identificado
          entre visitas. No usamos cookies de rastreo ni de publicidad de terceros.
        </p>
        <p>
          Para saber qué partes del sitio se usan, anotamos las páginas que se visitan y los pasos
          de una reserva. Van atados a un identificador al azar que vive mientras tenés la pestaña
          abierta y se borra al cerrarla: no te reconoce entre visitas, no sale de nuestros
          servidores y no se cruza con tu nombre ni tu teléfono. Ese registro se borra a los doce
          meses.
        </p>
      </LegalSection>

      <LegalSection heading="Menores de edad">
        <p>
          {BRAND} está pensado para que reserve una persona mayor de edad. Si sos menor, pedile a
          un adulto responsable que reserve por vos.
        </p>
      </LegalSection>

      <LegalSection heading="Cambios a esta política">
        <p>
          Podemos actualizar esta página con el tiempo. Si el cambio es importante, lo vas a ver
          reflejado acá con una nueva fecha de actualización arriba.
        </p>
      </LegalSection>
    </LegalLayout>
  );
}
