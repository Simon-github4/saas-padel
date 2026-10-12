import { Link, useLocation } from 'react-router-dom';
import { usePlayerAuth } from '../auth/AuthContext';
import { whatsappLink } from '../format';
import { readGuestBookings } from '../guestBookings';

/**
 * "¿Ya reservaste?", debajo del título de la reserva en la página del club.
 *
 * <p>Es para quien vuelve a la página del club a cancelar y no tiene a la vista
 * la tarjeta de su próximo turno: esa solo aparece si este dispositivo sabe de
 * un turno, y quien reservó desde otro teléfono o llegó por el link de WhatsApp
 * no tiene nada guardado acá. Va donde mira ese jugador: arriba de los pasos
 * para reservar, que es adonde lleva "Ver horarios".
 *
 * <p>Con sesión, o con turnos guardados en este dispositivo, lleva a Mis turnos.
 * Sin nada no hay una pantalla que pueda mostrarle su turno: si reservó con
 * cuenta lo ve al entrar, y si reservó sin cuenta solo el club sabe de él. Por
 * eso ofrece las dos salidas.
 */
export function AlreadyBookedLink({ clubWhatsapp }: { clubWhatsapp: string }) {
  const { session } = usePlayerAuth();
  const location = useLocation();
  // Como el botón de la barra: "volver" desde Mis turnos o el login trae acá.
  const desde = { from: location.pathname + location.search };
  const link = 'font-semibold text-ladrillo-claro underline-offset-4 hover:underline claro:text-ladrillo';

  if (session || readGuestBookings().length > 0) {
    return (
      <p className="mt-3 text-sm text-ink-soft">
        ¿Ya reservaste?{' '}
        <Link to="/account" state={desde} className={link}>
          Ver o cancelar tu turno
        </Link>
      </p>
    );
  }

  const hasWhatsapp = clubWhatsapp.replace(/[^0-9]/g, '') !== '';
  return (
    <p className="mt-3 text-sm text-ink-soft">
      ¿Ya reservaste?{' '}
      <Link to="/login" state={desde} className={link}>
        Entrá con tu cuenta
      </Link>{' '}
      para verlo o cancelarlo.
      {hasWhatsapp && (
        <>
          {' '}
          Si reservaste sin cuenta,{' '}
          <a
            href={whatsappLink(clubWhatsapp, 'Hola, tengo un turno reservado y necesito verlo o cancelarlo.')}
            target="_blank"
            rel="noreferrer"
            className={link}
          >
            escribile al club
          </a>
          .
        </>
      )}
    </p>
  );
}
