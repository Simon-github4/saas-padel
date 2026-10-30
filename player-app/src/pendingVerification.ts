/**
 * El pedido de confirmación por WhatsApp que esta página dejó esperando, recordado
 * en este navegador.
 *
 * <p>Desde el navegador de Instagram, la página muere cuando el jugador va a
 * WhatsApp a mandar el código. El turno sale igual en el servidor al llegar el
 * mensaje, pero si el jugador vuelve a abrir la página del club no ve nada que se
 * lo diga, y reservaba otro. Con esto, la página del club pregunta cómo terminó el
 * pedido y, si salió, se lo muestra y lo suma a "Mis turnos" (ver ClubPage).
 *
 * <p>Solo sirve en este mismo navegador: el de Chrome no sabe nada de lo que
 * pasó en el de Instagram. Para ese está el link que guarda el turno, en la
 * respuesta de WhatsApp.
 */

const STORAGE_KEY = 'padel_pending_verification';

/**
 * Cuánto se recuerda. El código vence a los 10 minutos; una hora alcanza para que
 * vuelva a abrir la página después de mandarlo, sin quedar con un aviso de un
 * turno de días atrás.
 */
const TTL_MS = 60 * 60 * 1000;

export interface PendingVerification {
  verificationId: string;
  clubSlug: string;
  clubName: string;
  courtName: string;
  startTime: string;
  endTime: string;
  savedAt: number;
}

/** El pedido guardado para este club, o null si no hay o venció (y en ese caso se borra). */
export function readPendingVerification(clubSlug: string, now: number = Date.now()): PendingVerification | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<PendingVerification>;
    if (typeof parsed.verificationId !== 'string' || typeof parsed.savedAt !== 'number' || now >= parsed.savedAt + TTL_MS) {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return parsed.clubSlug === clubSlug ? (parsed as PendingVerification) : null;
  } catch {
    return null;
  }
}

/** Al mandarlo a WhatsApp: vale el último pedido, uno solo por navegador. */
export function rememberPendingVerification(pending: Omit<PendingVerification, 'savedAt'>, now: number = Date.now()): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...pending, savedAt: now } satisfies PendingVerification));
  } catch {
    // Sin localStorage el turno sale igual; solo no se avisa al volver.
  }
}

/** Ya terminó: salió el turno, no salió, o el jugador cambió los datos. */
export function forgetPendingVerification(): void {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // no-op
  }
}
