import { beforeEach, describe, expect, it } from 'vitest';
import { forgetPendingVerification, readPendingVerification, rememberPendingVerification } from './pendingVerification';

/**
 * El pedido de confirmación por WhatsApp que la página deja esperando, para
 * contarle al jugador cómo terminó si vuelve a abrir la página del club.
 */

const STORAGE_KEY = 'padel_pending_verification';
const AHORA = Date.parse('2026-10-30T20:00:00Z');
const MINUTO = 60 * 1000;

const PEDIDO = {
  verificationId: 'v-1',
  clubSlug: 'costa-verde',
  clubName: 'Costa Verde Pádel',
  courtName: 'Cancha Roja',
  startTime: '2026-10-31T22:00:00Z',
  endTime: '2026-10-31T23:00:00Z',
};

/** localStorage en memoria, como en guestContact.test.ts. */
function stubLocalStorage() {
  const data = new Map<string, string>();
  return {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => void data.set(key, value),
    removeItem: (key: string) => void data.delete(key),
    clear: () => data.clear(),
    key: (index: number) => [...data.keys()][index] ?? null,
    get length() {
      return data.size;
    },
  } as Storage;
}

beforeEach(() => {
  globalThis.localStorage = stubLocalStorage();
});

describe('pedido de confirmación pendiente', () => {
  it('lo devuelve en la página del mismo club', () => {
    rememberPendingVerification(PEDIDO, AHORA);

    expect(readPendingVerification('costa-verde', AHORA + 5 * MINUTO)).toMatchObject(PEDIDO);
  });

  it('en la página de otro club no aparece, y queda para el suyo', () => {
    rememberPendingVerification(PEDIDO, AHORA);

    expect(readPendingVerification('el-muelle', AHORA)).toBeNull();
    expect(readPendingVerification('costa-verde', AHORA)).not.toBeNull();
  });

  it('vence a la hora, y se borra', () => {
    rememberPendingVerification(PEDIDO, AHORA);

    expect(readPendingVerification('costa-verde', AHORA + 59 * MINUTO)).not.toBeNull();
    expect(readPendingVerification('costa-verde', AHORA + 60 * MINUTO)).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('se olvida cuando terminó', () => {
    rememberPendingVerification(PEDIDO, AHORA);
    forgetPendingVerification();

    expect(readPendingVerification('costa-verde', AHORA)).toBeNull();
  });

  it('lo ilegible se descarta', () => {
    localStorage.setItem(STORAGE_KEY, '{"nada":');

    expect(readPendingVerification('costa-verde', AHORA)).toBeNull();
  });
});
