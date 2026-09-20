import { describe, expect, it } from 'vitest';
import { parseCheckInCode } from './checkinCode';

const TOKEN = 'aB3_dE6-gH9jK2mN5pQ8sT1vW4yZ7bC0dE3fG6hI9jK';

describe('parseCheckInCode', () => {
  it('lee el token de la URL completa del QR', () => {
    expect(parseCheckInCode(`https://turnospadel.com.ar/gym/los-troncos/in/${TOKEN}`, 'los-troncos')).toEqual({
      ok: true,
      token: TOKEN,
    });
  });

  it('acepta la ruta sin host, y con o sin barra final', () => {
    expect(parseCheckInCode(`/gym/los-troncos/in/${TOKEN}`, 'los-troncos')).toEqual({ ok: true, token: TOKEN });
    expect(parseCheckInCode(`/gym/los-troncos/in/${TOKEN}/`, 'los-troncos')).toEqual({ ok: true, token: TOKEN });
  });

  it('acepta el token pelado, con espacios alrededor', () => {
    expect(parseCheckInCode(`  ${TOKEN}\n`, 'los-troncos')).toEqual({ ok: true, token: TOKEN });
  });

  it('rechaza el QR de otro gimnasio antes de llegar al servidor', () => {
    const result = parseCheckInCode(`https://turnospadel.com.ar/gym/otro-club/in/${TOKEN}`, 'los-troncos');

    expect(result).toEqual({ ok: false, error: 'Ese QR es de otro gimnasio.' });
  });

  it('rechaza un texto vacío con un mensaje que dice qué hacer', () => {
    expect(parseCheckInCode('   ', 'los-troncos')).toEqual({ ok: false, error: 'Pegá el código del QR.' });
  });

  it('rechaza cualquier otro QR: un link cualquiera, un texto corto', () => {
    const notGym = { ok: false, error: 'Ese código no parece el QR del gimnasio.' };

    expect(parseCheckInCode('https://ejemplo.com/promo/123', 'los-troncos')).toEqual(notGym);
    expect(parseCheckInCode('hola', 'los-troncos')).toEqual(notGym);
    expect(parseCheckInCode('/gym/los-troncos', 'los-troncos')).toEqual(notGym);
  });
});
