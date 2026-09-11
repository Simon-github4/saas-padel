import { describe, expect, it } from 'vitest';
import { normalizePath } from './analytics';

/**
 * La ruta con la que se anota una visita.
 *
 * <p>Esto no es cosmética: `/manage/:token` y sus hermanos llevan en la URL el
 * token que autoriza el turno, que es la credencial con la que se cancela.
 * Normalizar es lo que hace que ese valor no salga siquiera del navegador.
 *
 * <p>El servidor vuelve a normalizar lo que le llegue (lo cubre
 * `PageEventApiIntegrationTest`), así que esto es la primera de dos barreras.
 * Se prueba igual porque una barrera que se rompe en silencio deja a la otra
 * sosteniendo sola algo que nadie diseñó para ser sostenido solo.
 */
describe('normalizePath', () => {
  it('deja la portada como está', () => {
    expect(normalizePath('/')).toBe('/');
    expect(normalizePath('')).toBe('/');
  });

  it('deja las rutas sin parámetros tal cual', () => {
    expect(normalizePath('/buscar')).toBe('/buscar');
    expect(normalizePath('/login')).toBe('/login');
    expect(normalizePath('/account')).toBe('/account');
    expect(normalizePath('/privacidad')).toBe('/privacidad');
    expect(normalizePath('/terminos')).toBe('/terminos');
    expect(normalizePath('/forgot-password')).toBe('/forgot-password');
  });

  it('reemplaza el slug del club por su forma', () => {
    expect(normalizePath('/club/club-necochea')).toBe('/club/:slug');
    expect(normalizePath('/club/el-muelle')).toBe('/club/:slug');
  });

  it('no deja pasar ningún token de turno', () => {
    const token = 'fZQBKrDMa7_snxQtL1FkPUjaUoKs9XvnKVt86Z3tPlA';

    expect(normalizePath(`/manage/${token}`)).toBe('/manage/:token');
    expect(normalizePath(`/confirm/${token}`)).toBe('/confirm/:token');
    expect(normalizePath(`/turno/${token}`)).toBe('/turno/:token');
    expect(normalizePath(`/reset-password/${token}`)).toBe('/reset-password/:token');
  });

  it('tampoco lo deja pasar escondido en la query o en el fragmento', () => {
    expect(normalizePath('/manage/abc123?ref=whatsapp')).toBe('/manage/:token');
    expect(normalizePath('/manage/abc123#detalle')).toBe('/manage/:token');
    expect(normalizePath('/club/necochea?fecha=2026-09-12&hora=20:00')).toBe('/club/:slug');
  });

  it('manda a /otro lo que no reconoce, en vez de guardarlo entero', () => {
    // Si mañana aparece una ruta nueva sin instrumentar, el peor caso tiene que
    // ser una fila que no dice de dónde, nunca un secreto en la bitácora.
    expect(normalizePath('/una-ruta-que-todavia-no-existe')).toBe('/otro');
    expect(normalizePath('/admin/algo')).toBe('/otro');
  });

  it('trata /club sin slug como ruta desconocida', () => {
    // No es la ficha de ningún club: sin segundo segmento no hay club que anotar.
    expect(normalizePath('/club')).toBe('/otro');
  });
});
