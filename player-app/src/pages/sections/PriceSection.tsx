import type { Slot } from '../../api/client';
import { clockTime, money, perPerson } from '../../format';

/**
 * Precio del día, armado como el marcador de una cancha.
 *
 * <p>El plato se lee de izquierda a derecha: lo que pone cada uno, y al lado
 * las condiciones del turno como filas de un marcador. Antes era una etiqueta
 * chica, un número gigante centrado y una frase corrida ("El turno completo,
 * $18.000 · 90 min · 4 jugadores") que obligaba a leerla entera para sacar tres
 * datos sueltos; separados en filas se barren de un vistazo y ademas usan el
 * ancho, en vez de apilarse en una columna angosta al medio.
 *
 * <p>Es la única pieza clara de la página. Ese contraste es deliberado: lo
 * primero que el jugador quiere saber es cuánto pone cada uno.
 */
export function PriceSection({
  slots,
  playersPerCourt,
  timeZone,
  slotDurationMinutes,
}: {
  slots: Slot[];
  playersPerCourt: number;
  timeZone: string;
  slotDurationMinutes: number;
}) {
  const withAvailability = slots.filter((slot) => slot.available.length > 0);
  if (withAvailability.length === 0) {
    return null;
  }

  const cheapestOf = (slots: Slot[]) =>
    Math.min(...slots.flatMap((slot) => slot.available.map((c) => c.price)));

  // El numero grande es el precio de un turno comun: si mostrara el de promo,
  // repetiria el que ya esta abajo y el descuento dejaria de leerse.
  const regular = withAvailability.filter((slot) => !slot.promo);
  const price = cheapestOf(regular.length > 0 ? regular : withAvailability);

  const promos = withAvailability.filter((slot) => slot.promo);
  const promoPrice = promos.length > 0 ? cheapestOf(promos) : null;

  return (
    // Sin margen propio: la separacion la da el padding de la banda que la
    // envuelve en ClubPage.
    <section>
      {/*
        Se invierte intercambiando tokens (se pinta con los de primer plano y
        escribe con el del fondo), asi acompana sola el tema del club. Por eso
        todo lo de adentro tiene que salir de esos mismos tokens: un gris de
        Tailwind queda ilegible cuando la tarjeta se da vuelta.
      */}
      <div className="rounded-3xl bg-gradient-to-br from-cal to-arena p-6 text-pista md:p-8">
        <div className="flex flex-col gap-6 md:flex-row md:items-center md:gap-8">
          {/* Lo que pone cada uno: el dato que el jugador vino a buscar, y el
              unico que lleva el acento de marca. A este cuerpo cuenta como
              texto grande, asi que el contraste alcanza en los dos temas; a
              11px, en cambio, no llegaba. */}
          <div className="md:flex-1">
            <p className="eyebrow text-pista/60">Por persona</p>
            <p className="display mt-1 text-6xl leading-none text-ladrillo tabular-nums">
              {perPerson(price, playersPerCourt)}
            </p>
          </div>

          {/* Las condiciones del turno, como filas de un marcador. Separadas en
              etiqueta y valor se barren de un vistazo; en la frase corrida que
              habia antes habia que leerla entera para sacar cada dato. */}
          <dl className="divide-y divide-pista/10 border-t border-pista/15 pt-1 md:w-64 md:border-l md:border-t-0 md:pl-8 md:pt-0">
            <Spec label="El turno" value={money(price)} />
            <Spec label="Duración" value={`${slotDurationMinutes} min`} />
            <Spec label="Jugadores" value={String(playersPerCourt)} />
          </dl>
        </div>

        {/* La promo cuelga del mismo plato, como el renglon de proximos turnos
            de un marcador, en vez de ser otra tarjeta adentro de la tarjeta.
            Repite el acento porque es el mismo dato -un precio por persona- y
            la jerarquia ya la da el cuerpo: 24px contra 60px. */}
        {promoPrice != null && promoPrice < price && (
          <div className="mt-6 border-t border-pista/15 pt-4">
            <div className="flex flex-wrap items-baseline gap-x-3">
              <span className="eyebrow text-pista/60">Promo</span>
              <span className="display text-2xl leading-none text-ladrillo tabular-nums">
                {perPerson(promoPrice, playersPerCourt)}
              </span>
              <span className="text-sm text-pista/70">c/u</span>
            </div>
            {/* Los horarios van en su propio renglon y no al final del anterior:
                un dia con muchas franjas los hacia envolver y quedaban colgados
                contra el margen derecho, con el borde izquierdo dentado. */}
            <p className="mt-1.5 text-sm text-pista/70 tabular-nums">
              {promos.map((slot) => clockTime(slot.startsAt, timeZone)).join(' · ')} hs
            </p>
          </div>
        )}
      </div>
    </section>
  );
}

/** Fila del marcador: la condicion a la izquierda, su valor a la derecha. */
function Spec({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4 py-2">
      <dt className="eyebrow text-pista/60">{label}</dt>
      <dd className="text-sm font-semibold tabular-nums">{value}</dd>
    </div>
  );
}
