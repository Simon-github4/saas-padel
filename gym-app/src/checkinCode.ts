/**
 * Lo que dice el QR de la puerta.
 *
 * <p>El QR lleva la URL que abre la app ({@code https://host/gym/<club>/in/<token>}),
 * asi sirve tanto para el escaner de la app como para la camara del celular. El
 * escaner y el campo "pegar codigo" pasan por aca: aceptan la URL completa o el
 * token pelado, y rechazan el QR de otro gimnasio antes de molestar al servidor.
 */

export type ParsedCode = { ok: true; token: string } | { ok: false; error: string };

const URL_PATH = /^\/gym\/([a-z0-9-]+)\/in\/([A-Za-z0-9_-]+)\/?$/;
const RAW_TOKEN = /^[A-Za-z0-9_-]{20,64}$/;

export function parseCheckInCode(input: string, slug: string): ParsedCode {
  const text = input.trim();
  if (!text) {
    return { ok: false, error: 'Pegá el código del QR.' };
  }

  if (RAW_TOKEN.test(text)) {
    return { ok: true, token: text };
  }

  let path: string;
  try {
    // El segundo argumento permite que "/gym/..." sin host también se lea como ruta.
    path = new URL(text, 'https://gimnasio.invalid').pathname;
  } catch {
    return { ok: false, error: NOT_A_GYM_CODE };
  }

  const match = URL_PATH.exec(path);
  if (!match) {
    return { ok: false, error: NOT_A_GYM_CODE };
  }
  if (match[1] !== slug) {
    return { ok: false, error: 'Ese QR es de otro gimnasio.' };
  }
  return { ok: true, token: match[2] };
}

const NOT_A_GYM_CODE = 'Ese código no parece el QR del gimnasio.';
