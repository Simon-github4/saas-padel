import { describe, expect, it } from 'vitest';
import type { BookingHistoryItem } from './api/client';
import { isPlayed, periodBuckets, periodStart, summarize } from './stats';

/**
 * Las barras del gráfico de actividad.
 *
 * <p>Es el módulo con aritmética de fechas de verdad de la app, y esa aritmética
 * se equivoca en silencio: un turno mal ubicado no rompe nada, sólo aparece en
 * el día de al lado.
 *
 * <p>Sobre el horario de verano: los límites de cada barra se arman con los
 * componentes de la fecha local y no sumando milisegundos, justamente para que
 * el día en que el reloj se corre no desplace las barras. Eso se prueba acá
 * como invariante -- toda barra de día empieza a la medianoche local -- y no
 * simulando un cambio de hora, que exigiría fijar la zona horaria del proceso:
 * Argentina hoy no tiene horario de verano, así que el runner no lo ejercita
 * solo. Si la construcción volviera a ser "sumar 24 horas", en un cambio de
 * hora alguna barra empezaría a las 23 o a la 1, y ese invariante cae.
 */

/** Un jueves, para que la semana de referencia tenga días antes y después. */
const AHORA = new Date(2026, 8, 10, 15, 0); // 10 de septiembre de 2026, 15:00 local

function turno(empieza: Date, minutos = 90, status = 'COMPLETED'): BookingHistoryItem {
  const termina = new Date(empieza.getTime() + minutos * 60 * 1000);
  return {
    bookingId: `t-${empieza.toISOString()}`,
    clubName: 'Pádel Necochea',
    clubSlug: 'club-necochea',
    courtName: 'Cancha 1',
    startTime: empieza.toISOString(),
    endTime: termina.toISOString(),
    status,
    totalPrice: 28000,
    paidAmount: 0,
    managementToken: 'tok',
  };
}

describe('isPlayed', () => {
  it('cuenta el turno terminado', () => {
    expect(isPlayed(turno(new Date(2026, 8, 9, 20, 0), 90, 'COMPLETED'), AHORA)).toBe(true);
  });

  it('cuenta el confirmado que ya pasó, aunque nadie lo haya cerrado', () => {
    expect(isPlayed(turno(new Date(2026, 8, 9, 20, 0), 90, 'CONFIRMED'), AHORA)).toBe(true);
  });

  it('no cuenta el confirmado que todavía no se jugó', () => {
    expect(isPlayed(turno(new Date(2026, 8, 12, 20, 0), 90, 'CONFIRMED'), AHORA)).toBe(false);
  });

  it('no cuenta el cancelado ni el que quedó pendiente', () => {
    expect(isPlayed(turno(new Date(2026, 8, 9, 20, 0), 90, 'CANCELLED'), AHORA)).toBe(false);
    expect(isPlayed(turno(new Date(2026, 8, 9, 20, 0), 90, 'AWAITING_CONFIRMATION'), AHORA))
      .toBe(false);
  });
});

describe('periodStart', () => {
  it('arranca la semana el lunes', () => {
    // El 10 de septiembre de 2026 es jueves; su lunes es el 7.
    const lunes = periodStart('week', AHORA)!;
    expect(lunes.getDay()).toBe(1);
    expect(lunes.getDate()).toBe(7);
  });

  it('toma el lunes anterior cuando hoy es domingo', () => {
    // El domingo cierra la semana, no la abre: su lunes está seis días atrás.
    const domingo = new Date(2026, 8, 13, 12, 0);
    const lunes = periodStart('week', domingo)!;
    expect(lunes.getDate()).toBe(7);
  });

  it('arranca el mes el día uno y el año el primero de enero', () => {
    expect(periodStart('month', AHORA)!.getDate()).toBe(1);
    expect(periodStart('year', AHORA)!.getMonth()).toBe(0);
    expect(periodStart('year', AHORA)!.getDate()).toBe(1);
  });

  it('no tiene inicio para el histórico', () => {
    expect(periodStart('all', AHORA)).toBeNull();
  });
});

describe('barras', () => {
  it('la semana tiene siete días, de lunes a domingo', () => {
    const buckets = periodBuckets([], 'week', AHORA);
    expect(buckets.map((bucket) => bucket.label)).toEqual(['L', 'M', 'M', 'J', 'V', 'S', 'D']);
  });

  it('el año tiene doce meses', () => {
    expect(periodBuckets([], 'year', AHORA)).toHaveLength(12);
  });

  it('toda barra de día empieza a la medianoche local', () => {
    // El invariante del horario de verano: si los límites se armaran sumando
    // milisegundos, el día del cambio de hora empezaría a las 23 o a la 1.
    for (const bucket of periodBuckets([], 'week', AHORA)) {
      expect(bucket.start.getHours()).toBe(0);
      expect(bucket.start.getMinutes()).toBe(0);
    }
  });

  it('las barras son contiguas: donde termina una empieza la siguiente', () => {
    // Sin esto, un turno podría caer en un hueco entre dos barras y desaparecer
    // del gráfico sin que nada avise.
    const buckets = periodBuckets([], 'month', AHORA);
    for (let i = 1; i < buckets.length; i++) {
      expect(buckets[i].start.getTime()).toBe(buckets[i - 1].end.getTime());
    }
  });

  it('ubica cada turno en su día', () => {
    const buckets = periodBuckets(
      [
        turno(new Date(2026, 8, 7, 20, 0)), // lunes
        turno(new Date(2026, 8, 9, 21, 30)), // miércoles
        turno(new Date(2026, 8, 9, 8, 0)), // miércoles, otra vez
      ],
      'week',
      AHORA,
    );

    expect(buckets.map((bucket) => bucket.turnos)).toEqual([1, 0, 2, 0, 0, 0, 0]);
  });

  it('el turno que arranca justo en el límite cae en el día que empieza', () => {
    // start es inclusivo y end exclusivo, así que dos barras vecinas no se
    // pelean por el turno de la medianoche.
    const buckets = periodBuckets([turno(new Date(2026, 8, 9, 0, 0))], 'week', AHORA);
    expect(buckets[2].turnos).toBe(1);
    expect(buckets[1].turnos).toBe(0);
  });

  it('no cuenta los turnos que no se jugaron', () => {
    const buckets = periodBuckets(
      [
        turno(new Date(2026, 8, 7, 20, 0), 90, 'CANCELLED'),
        turno(new Date(2026, 8, 12, 20, 0), 90, 'CONFIRMED'),
      ],
      'week',
      AHORA,
    );
    expect(buckets.every((bucket) => bucket.turnos === 0)).toBe(true);
  });

  it('el histórico abarca desde el primer año jugado hasta hoy', () => {
    const buckets = periodBuckets([turno(new Date(2024, 2, 5, 20, 0))], 'all', AHORA);
    expect(buckets.map((bucket) => bucket.label)).toEqual(['2024', '2025', '2026']);
    expect(buckets[0].turnos).toBe(1);
  });

  it('el histórico sin nada jugado no dibuja barras', () => {
    expect(periodBuckets([], 'all', AHORA)).toEqual([]);
  });
});

describe('totales', () => {
  it('salen de sumar las mismas barras que dibuja el gráfico', () => {
    // Con dos caminos de cálculo, tarde o temprano el número grande y las
    // barras terminan diciendo cosas distintas.
    const history = [
      turno(new Date(2026, 8, 7, 20, 0), 90),
      turno(new Date(2026, 8, 9, 20, 0), 60),
    ];

    const { turnos, minutos, buckets } = summarize(history, 'week', AHORA);

    expect(turnos).toBe(2);
    expect(minutos).toBe(150);
    expect(buckets.reduce((total, bucket) => total + bucket.turnos, 0)).toBe(turnos);
    expect(buckets.reduce((total, bucket) => total + bucket.minutos, 0)).toBe(minutos);
  });

  it('deja fuera lo que cae afuera de la ventana', () => {
    const laSemanaPasada = turno(new Date(2026, 8, 2, 20, 0));
    expect(summarize([laSemanaPasada], 'week', AHORA).turnos).toBe(0);
    expect(summarize([laSemanaPasada], 'month', AHORA).turnos).toBe(1);
  });
});
