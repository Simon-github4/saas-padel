/**
 * El logo oficial de Mercado Pago, servido desde public/marcas.
 *
 * <p>Son los archivos del pack oficial (versión RGB, para uso digital), tal
 * como vienen: su hoja de uso pide no editarles el contenido, así que van como
 * imagen y no como SVG copiado acá adentro. Por eso tampoco existe una versión
 * con el óvalo solo: el pack no la trae.
 *
 * <p>Dos variantes, según el fondo: la de color sobre claro y la blanca sobre
 * el negro de la landing, donde el azul marino del nombre no se leería.
 */

const COLOR = '/marcas/mercado-pago-color.svg';
const BLANCO = '/marcas/mercado-pago-blanco.svg';

/** Sobre fondo oscuro. */
export function MercadoPagoLogo({ className = '' }: { className?: string }) {
  return <img src={BLANCO} alt="Mercado Pago" className={className} />;
}

/** Sobre fondo claro: el logo con sus colores. */
export function MercadoPagoLogoColor({ className = '' }: { className?: string }) {
  return <img src={COLOR} alt="Mercado Pago" className={className} />;
}
